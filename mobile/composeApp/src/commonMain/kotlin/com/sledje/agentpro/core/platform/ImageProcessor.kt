package com.sledje.agentpro.core.platform

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
 * Crops a picked image to the schema's aspect (a centred crop), scales it so the longer side is at most
 * [MAX_SIDE] px and encodes it as WebP under the Storage limit (docs/ARCHITECTURE.md, Platform seams;
 * docs/BACKEND.md, Storage: 2 MB, images only). Android: Bitmap + WebP; iOS: UIImage (JPEG fallback).
 */
interface ImageProcessor {
    /** Null when the file isn't a readable image. */
    suspend fun prepare(image: PickedImage, aspect: ImageAspect?, maxSide: Int = MAX_SIDE): ProcessedImage?

    companion object {
        const val MAX_SIDE = 1024

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

    /** [width]×[height] scaled down (never up) so the longer side is at most [maxSide]. */
    fun fitWithin(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
        val longer = max(width, height)
        if (longer <= maxSide || longer <= 0) return width to height
        val scale = maxSide.toDouble() / longer
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }

    /** The power-of-two decode sample size that keeps the decoded image at least [minSide] on its shorter side. */
    fun sampleSize(width: Int, height: Int, minSide: Int): Int {
        var sample = 1
        val shorter = minOf(width, height)
        while (shorter / (sample * 2) >= minSide) sample *= 2
        return sample
    }
}
