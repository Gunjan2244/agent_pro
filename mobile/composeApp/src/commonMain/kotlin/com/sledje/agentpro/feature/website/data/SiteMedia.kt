package com.sledje.agentpro.feature.website.data

import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.AppResult
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.data.SupabaseProvider
import com.sledje.agentpro.core.data.remoteCall
import androidx.compose.ui.graphics.ImageBitmap
import com.sledje.agentpro.core.platform.ImageAspect
import com.sledje.agentpro.core.platform.ImageCrop
import com.sledje.agentpro.core.platform.ImageProcessor
import com.sledje.agentpro.core.platform.PickedImage
import com.sledje.agentpro.core.platform.ProcessedImage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlin.random.Random
import kotlin.time.Clock

/** Uploads agent images (hero photo, profile photo) to Storage (docs/BACKEND.md, Storage). */
interface SiteMediaRepository {
    /**
     * Uploads an already prepared image as `site-media/<uid>/<name>-<time>.<ext>` and returns its public
     * URL. A new name every time, so the live site keeps showing the old photo until the next publish and
     * caches never serve a stale image.
     */
    suspend fun upload(image: ProcessedImage, name: String): AppResult<String>
}

/** Why a picked file couldn't be used (not an image, or unreadable). */
val ImageUnreadable = AppError.Validation("image", "unreadable")

/**
 * The MediaPicker → photo editor → ImageProcessor → upload pipeline shared by the section editor and onboarding.
 * [crop] is the agent's crop from the photo editor (null: centred).
 */
class ImageUploader(private val processor: ImageProcessor, private val media: SiteMediaRepository) {
    suspend fun upload(image: PickedImage, aspect: ImageAspect?, name: String, crop: ImageCrop? = null): AppResult<String> {
        val processed = processor.prepare(image, aspect, crop) ?: return Result.Err(ImageUnreadable)
        if (processed.bytes.size > ImageProcessor.MAX_BYTES) return Result.Err(ImageUnreadable)
        return media.upload(processed, name)
    }

    /** The photo editor's upright working copy; null when the file isn't a readable image. */
    suspend fun preview(image: PickedImage): ImageBitmap? = processor.decodePreview(image)
}

class SupabaseSiteMediaRepository(private val supabase: SupabaseProvider) : SiteMediaRepository {
    override suspend fun upload(image: ProcessedImage, name: String): AppResult<String> = remoteCall {
        val client = supabase.require()
        val uid = client.auth.currentUserOrNull()?.id ?: error("no session")
        val path = MediaPaths.objectPath(uid, name, image.extension, Clock.System.now().toEpochMilliseconds(), Random.nextInt(0, 1_000_000))
        val bucket = client.storage.from(MediaPaths.BUCKET)
        bucket.upload(path, image.bytes) {
            upsert = false
            contentType = ContentType.parse(image.mimeType)
        }
        bucket.publicUrl(path)
    }
}

/** Object names inside the `site-media` bucket; the first folder must be the agent's uid (Storage policies). */
object MediaPaths {
    const val BUCKET = "site-media"
    const val HERO_PHOTO = "hero-photo"
    const val PROFILE_PHOTO = "profile-photo"

    fun objectPath(uid: String, name: String, extension: String, millis: Long, nonce: Int): String {
        val safe = name.lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-').ifEmpty { "image" }
        return "$uid/$safe-$millis-$nonce.$extension"
    }
}
