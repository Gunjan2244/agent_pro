package com.sledje.agentpro.core.platform

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.max
import kotlin.math.roundToInt

/** A crop aspect ratio from the site schema's `aspect` (e.g. "1:1" for the hero photo). */
data class ImageAspect(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) { "aspect must be positive" }
    }

    companion object {
        val Square = ImageAspect(1, 1)

        /** "W:H" → [ImageAspect]; null for a missing or malformed value (no crop then). */
        fun parse(value: String?): ImageAspect? {
            val parts = value?.split(':')?.map { it.trim().toIntOrNull() } ?: return null
            if (parts.size != 2) return null
            val (w, h) = parts
            return if (w != null && h != null && w > 0 && h > 0) ImageAspect(w, h) else null
        }
    }
}

/** A prepared image, ready to upload. */
class ProcessedImage(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    /** e.g. `image/webp` (iOS may fall back to `image/jpeg`). */
    val mimeType: String,
    /** File extension without the dot, e.g. `webp`. */
    val extension: String,
)

/**
 * The agent's crop, as fractions of the upright image: [zoom] ≥ 1 (1 = the largest crop of the aspect that fits) and
 * the crop's centre ([centerX], [centerY], 0..1). [ImageGeometry.crop] clamps it so the crop stays inside the image.
 */
data class ImageCrop(val zoom: Float = 1f, val centerX: Float = 0.5f, val centerY: Float = 0.5f) {
    companion object {
        /** The centred crop at zoom 1 (what an image gets without the editor). */
        val Centered = ImageCrop()
        const val MAX_ZOOM = 4f
    }
}

/**
 * Crops a picked image to the schema's aspect (the agent's [ImageCrop], else a centred crop), scales it so the longer side is at most
 * [MAX_SIDE] px and encodes it as WebP under the Storage limit (docs/ARCHITECTURE.md, Platform seams;
 * docs/BACKEND.md, Storage: 2 MB, images only). Android: Bitmap + WebP; iOS: UIImage (JPEG fallback).
 */
interface ImageProcessor {
    /** Null when the file isn't a readable image. [crop] is applied in the upright image (null: centred). */
    suspend fun prepare(image: PickedImage, aspect: ImageAspect?, crop: ImageCrop? = null, maxSide: Int = MAX_SIDE): ProcessedImage?

    /** The upright image, scaled so its longer side is at most [maxSide], for the crop editor; null when unreadable. */
    suspend fun decodePreview(image: PickedImage, maxSide: Int = PREVIEW_SIDE): ImageBitmap?

    companion object {
        const val MAX_SIDE = 1024

        /** The crop editor's working copy; big enough to judge a face, small enough for any phone's memory. */
        const val PREVIEW_SIDE = 1600

        /** The most pixels a decode may hold (about 48 MB as ARGB). */
        const val MAX_DECODE_PIXELS = 12_000_000L

        /** `site-media` bucket `file_size_limit` (2 MB). */
        const val MAX_BYTES = 2 * 1024 * 1024
    }
}

/** A pixel rectangle inside the source image. */
data class CropRect(val left: Int, val top: Int, val width: Int, val height: Int)

/** The pure geometry behind [ImageProcessor] (shared by every platform, unit-tested in commonTest). */
object ImageGeometry {
    /** The largest centred rectangle of [aspect] inside a [width]×[height] image; the whole image when [aspect] is null. */
    fun centerCrop(width: Int, height: Int, aspect: ImageAspect?): CropRect {
        if (aspect == null || width <= 0 || height <= 0) return CropRect(0, 0, width, height)
        // Compare width/height with aspect.width/aspect.height without floating point.
        val cropW: Int
        val cropH: Int
        if (width.toLong() * aspect.height > height.toLong() * aspect.width) {
            cropH = height
            cropW = (height.toLong() * aspect.width / aspect.height).toInt().coerceIn(1, width)
        } else {
            cropW = width
            cropH = (width.toLong() * aspect.height / aspect.width).toInt().coerceIn(1, height)
        }
        return CropRect((width - cropW) / 2, (height - cropH) / 2, cropW, cropH)
    }

    /**
     * The agent's [crop] of [aspect] inside a [width]×[height] image: the [centerCrop] size divided by the zoom, centred
     * on the crop's centre but moved back inside the image. The whole image when [aspect] is null.
     */
    fun crop(width: Int, height: Int, aspect: ImageAspect?, crop: ImageCrop): CropRect {
        val full = centerCrop(width, height, aspect)
        if (aspect == null) return full
        val zoom = crop.zoom.coerceIn(1f, ImageCrop.MAX_ZOOM)
        val cropW = (full.width / zoom).roundToInt().coerceIn(1, width)
        val cropH = (full.height / zoom).roundToInt().coerceIn(1, height)
        val left = (crop.centerX * width - cropW / 2f).roundToInt().coerceIn(0, width - cropW)
        val top = (crop.centerY * height - cropH / 2f).roundToInt().coerceIn(0, height - cropH)
        return CropRect(left, top, cropW, cropH)
    }

    /** [crop] with its zoom in 1..[ImageCrop.MAX_ZOOM] and its centre moved so the crop stays inside the image. */
    fun clampCrop(width: Int, height: Int, aspect: ImageAspect?, crop: ImageCrop): ImageCrop {
        if (aspect == null || width <= 0 || height <= 0) return ImageCrop.Centered
        val full = centerCrop(width, height, aspect)
        val zoom = crop.zoom.coerceIn(1f, ImageCrop.MAX_ZOOM)
        val halfX = (full.width / zoom / width / 2f).coerceAtMost(0.5f)
        val halfY = (full.height / zoom / height / 2f).coerceAtMost(0.5f)
        return ImageCrop(zoom, crop.centerX.coerceIn(halfX, 1f - halfX), crop.centerY.coerceIn(halfY, 1f - halfY))
    }

    /**
     * The part of a crop of [aspect] that the website shows as [shape] (CSS `object-fit: cover`,
     * `object-position: 50% focusY%`), as fractions of the crop: left, top, width, height.
     */
    fun shownPart(aspect: ImageAspect, shape: ImageAspect, focusY: Int): FloatArray {
        val a = aspect.width.toFloat() / aspect.height
        val s = shape.width.toFloat() / shape.height
        return if (s > a) {
            val h = a / s
            floatArrayOf(0f, (1f - h) * focusY.coerceIn(0, 100) / 100f, 1f, h)
        } else {
            val w = s / a
            floatArrayOf((1f - w) / 2f, 0f, w, 1f)
        }
    }

    /** [width]×[height] scaled down (never up) so the longer side is at most [maxSide]. */
    fun fitWithin(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
        val longer = max(width, height)
        if (longer <= maxSide || longer <= 0) return width to height
        val scale = maxSide.toDouble() / longer
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }

    /**
     * The power-of-two decode sample size that keeps the decoded image at least [minSide] on its shorter side, but
     * never more than [maxPixels] in all (so a zoomed crop of a 50 MP photo can't run out of memory).
     */
    fun sampleSize(width: Int, height: Int, minSide: Int, maxPixels: Long = ImageProcessor.MAX_DECODE_PIXELS): Int {
        var sample = 1
        val shorter = minOf(width, height)
        while (shorter / (sample * 2) >= minSide) sample *= 2
        while ((width / sample).toLong() * (height / sample) > maxPixels) sample *= 2
        return sample
    }
}
