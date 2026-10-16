package com.sledje.agentpro.testing

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.AppResult
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.platform.ImageAspect
import com.sledje.agentpro.core.platform.ImageProcessor
import com.sledje.agentpro.core.platform.MediaPicker
import com.sledje.agentpro.core.platform.PickedImage
import com.sledje.agentpro.core.platform.ProcessedImage
import com.sledje.agentpro.core.platform.SitePreview
import com.sledje.agentpro.feature.website.WebsiteServices
import com.sledje.agentpro.feature.website.data.ImageUploader
import com.sledje.agentpro.feature.website.data.SiteDailyViewDao
import com.sledje.agentpro.feature.website.data.SiteDailyViewEntity
import com.sledje.agentpro.feature.website.data.SiteMediaRepository
import com.sledje.agentpro.feature.website.data.SiteStatsRepository
import com.sledje.agentpro.feature.website.data.WebsiteEnquirySource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate

/** Fakes of the Website hub seams (Phase 8). No backend, no WebView, no real images. */

/** Returns [next] each time the picker opens (null = the agent cancelled). */
class FakeMediaPicker(var next: PickedImage? = PickedImage { byteArrayOf(1, 2, 3) }) : MediaPicker {
    var opened = 0

    @Composable
    override fun rememberImagePicker(onResult: (PickedImage?) -> Unit): () -> Unit = { opened++; onResult(next) }
}

/** "Processes" by recording the aspect; [fail] makes the image unreadable. */
class FakeImageProcessor(var fail: Boolean = false) : ImageProcessor {
    val aspects = mutableListOf<ImageAspect?>()

    override suspend fun prepare(image: PickedImage, aspect: ImageAspect?, maxSide: Int): ProcessedImage? {
        aspects += aspect
        if (fail) return null
        return ProcessedImage(image.read(), maxSide, maxSide, "image/webp", "webp")
    }
}

class FakeSiteMediaRepository(var error: AppError? = null) : SiteMediaRepository {
    val uploads = mutableListOf<String>()

    override suspend fun upload(image: ProcessedImage, name: String): AppResult<String> {
        error?.let { return Result.Err(it) }
        uploads += name
        return Result.Ok("https://example.com/storage/v1/object/public/site-media/$TEST_USER/$name-${uploads.size}.webp")
    }
}

/** Records the JSON it would hand to `window.renderSite` (a WebView can't render in tests). */
class FakeSitePreview : SitePreview {
    val rendered = mutableListOf<String>()

    @Composable
    override fun Render(siteJson: String, modifier: Modifier) {
        rendered += siteJson
        Box(modifier)
    }
}

class FakeSiteDailyViewDao(initial: List<SiteDailyViewEntity> = emptyList()) : SiteDailyViewDao {
    val rows = MutableStateFlow(initial)
    override fun observeAll(): Flow<List<SiteDailyViewEntity>> = rows
    override suspend fun upsert(rows: List<SiteDailyViewEntity>) { this.rows.value = (this.rows.value.filterNot { r -> rows.any { it.id == r.id } } + rows).sortedBy { it.day } }
    override suspend fun deleteByIds(ids: List<String>) { rows.value = rows.value.filterNot { it.id in ids } }
    override suspend fun deleteAll() { rows.value = emptyList() }
}

fun viewRow(day: String, views: Int, id: String = "v-$day") = SiteDailyViewEntity(id, "site-1", day, views, "2026-10-16T00:00:00Z", null)

/** The fakes bundled as the app's [WebsiteServices]. */
class FakeWebsite(
    val picker: FakeMediaPicker = FakeMediaPicker(),
    val processor: FakeImageProcessor = FakeImageProcessor(),
    val media: FakeSiteMediaRepository = FakeSiteMediaRepository(),
    val preview: FakeSitePreview = FakeSitePreview(),
    val views: FakeSiteDailyViewDao = FakeSiteDailyViewDao(),
    val enquiryDays: MutableStateFlow<List<LocalDate>> = MutableStateFlow(emptyList()),
    today: LocalDate = LocalDate(2026, 10, 16),
) {
    val services = WebsiteServices(
        mediaPicker = picker,
        imageUploader = ImageUploader(processor, media),
        sitePreview = preview,
        stats = SiteStatsRepository(views, WebsiteEnquirySource { enquiryDays }, today = { today }),
    )
}
