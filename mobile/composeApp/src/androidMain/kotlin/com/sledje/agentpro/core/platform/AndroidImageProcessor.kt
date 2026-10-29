package com.sledje.agentpro.core.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * [ImageProcessor] on Android: decodes with a power-of-two sample size (bounded memory), applies the
 * EXIF orientation, crops to the aspect (the agent's [ImageCrop], else centred), scales to ≤ [ImageProcessor.MAX_SIDE] px and encodes
 * WebP (lossy), lowering the quality until it fits under the 2 MB bucket limit.
 */
class AndroidImageProcessor : ImageProcessor {
    override suspend fun prepare(image: PickedImage, aspect: ImageAspect?, crop: ImageCrop?, maxSide: Int): ProcessedImage? = try {
        val bytes = image.read()
        withContext(Dispatchers.Default) { process(bytes, aspect, crop ?: ImageCrop.Centered, maxSide) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    override suspend fun decodePreview(image: PickedImage, maxSide: Int): ImageBitmap? = try {
        val bytes = image.read()
        withContext(Dispatchers.Default) {
            // Halve while the longer side stays at least maxSide, then scale the rest of the way.
            decodeUpright(bytes) { w, h -> ImageGeometry.sampleSize(w, h, minSide = maxSide * minOf(w, h) / maxOf(w, h)) }?.let { upright ->
                val (w, h) = ImageGeometry.fitWithin(upright.width, upright.height, maxSide)
                (if (w == upright.width && h == upright.height) upright else Bitmap.createScaledBitmap(upright, w, h, true)).asImageBitmap()
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** Decodes with the power-of-two [sample] size for the stored width×height (bounded memory), upright (EXIF). */
    private fun decodeUpright(bytes: ByteArray, sample: (Int, Int) -> Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sample(bounds.outWidth, bounds.outHeight) }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        return rotate(decoded, orientation(bytes))
    }

    private fun process(bytes: ByteArray, aspect: ImageAspect?, agentCrop: ImageCrop, maxSide: Int): ProcessedImage? {
        // A zoomed crop is a smaller part of the photo, so decode with more pixels to keep it near maxSide.
        val minSide = (maxSide * agentCrop.zoom.coerceIn(1f, ImageCrop.MAX_ZOOM)).toInt()
        val upright = decodeUpright(bytes) { w, h -> ImageGeometry.sampleSize(w, h, minSide) } ?: return null
        val crop = ImageGeometry.crop(upright.width, upright.height, aspect, agentCrop)
        val (w, h) = ImageGeometry.fitWithin(crop.width, crop.height, maxSide)
        val cropped = Bitmap.createBitmap(upright, crop.left, crop.top, crop.width, crop.height)
        val scaled = if (w == cropped.width && h == cropped.height) cropped else Bitmap.createScaledBitmap(cropped, w, h, true)
        val encoded = encode(scaled)
        return ProcessedImage(encoded, scaled.width, scaled.height, mimeType = "image/webp", extension = "webp")
    }

    private fun encode(bitmap: Bitmap): ByteArray {
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        var quality = START_QUALITY
        while (true) {
            val out = ByteArrayOutputStream()
            bitmap.compress(format, quality, out)
            if (out.size() <= ImageProcessor.MAX_BYTES || quality <= MIN_QUALITY) return out.toByteArray()
            quality -= QUALITY_STEP
        }
    }

    private fun orientation(bytes: ByteArray): Int = try {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        const val START_QUALITY = 82
        const val MIN_QUALITY = 40
        const val QUALITY_STEP = 10
    }
}
