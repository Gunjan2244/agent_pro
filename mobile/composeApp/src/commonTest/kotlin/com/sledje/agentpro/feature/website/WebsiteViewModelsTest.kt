package com.sledje.agentpro.feature.website

import app.cash.turbine.test
import com.sledje.agentpro.core.catalog.AccentSwatch
import com.sledje.agentpro.core.catalog.SiteSchema
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.forms.ValidationError
import com.sledje.agentpro.core.platform.ImageAspect
import com.sledje.agentpro.core.platform.PickedImage
import com.sledje.agentpro.feature.onboarding.ui.SlugStatus
import com.sledje.agentpro.feature.website.data.ContentTooLarge
import com.sledje.agentpro.feature.website.data.ImageUnreadable
import com.sledje.agentpro.feature.website.data.PublishError
import com.sledje.agentpro.feature.website.data.PublishIssues
import com.sledje.agentpro.feature.website.data.PublishOutcome
import com.sledje.agentpro.feature.website.data.SiteAppearance
import com.sledje.agentpro.feature.website.data.SiteContent
import com.sledje.agentpro.feature.website.data.StatsPeriod
import com.sledje.agentpro.feature.website.ui.AppearanceViewModel
import com.sledje.agentpro.feature.website.ui.ContentViewModel
import com.sledje.agentpro.feature.website.ui.LinkViewModel
import com.sledje.agentpro.feature.website.ui.PublishState
import com.sledje.agentpro.feature.website.ui.SectionEditorViewModel
import com.sledje.agentpro.feature.website.ui.StatsViewModel
import com.sledje.agentpro.feature.website.ui.WebsiteHubViewModel
import com.sledje.agentpro.testing.FakeProfileRepository
import com.sledje.agentpro.testing.FakeSiteRepository
import com.sledje.agentpro.testing.FakeWebsite
import com.sledje.agentpro.testing.testProfile
import com.sledje.agentpro.testing.testSite
import com.sledje.agentpro.testing.viewRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Website hub view models on fakes (docs/TESTING.md). No backend, no images, no WebView. */
@OptIn(ExperimentalCoroutinesApi::class)
class WebsiteViewModelsTest {
    private val starter = buildJsonObject {
        put("hero", buildJsonObject { put("headline", "Ramesh Kumar") })
        put("contact", buildJsonObject { put("phone", "+919876543210") })
    }
    private val sites = FakeSiteRepository(testSite().copy(draftContent = starter, accent = AccentSwatch.Indigo))
    private val website = FakeWebsite()

    @BeforeTest fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun section(key: String) = SiteSchema.section(key)!!

    private fun editor(key: String, issues: List<PublishError> = emptyList(), onSaved: (String) -> Unit = {}) =
        SectionEditorViewModel(section(key), sites, website.services.imageUploader, PublishIssues.resolve(issues), onSaved)

    // ---- Section editor -----------------------------------------------------------------------

    @Test
    fun editorLoadsTheDraftAndSavesOnlyTheDraft() = runTest {
        val vm = editor("hero")
        runCurrent()
        val form = assertNotNull(vm.form)
        assertEquals("Ramesh Kumar", form.text("headline"))
        form.set("subheadline", "Life insurance for your family")
        assertTrue(vm.isDirty)
        vm.save()
        runCurrent()
        val saved = sites.drafts.single()
        assertEquals("Life insurance for your family", saved["hero"]!!.jsonObject["subheadline"]!!.jsonPrimitive.content)
        assertEquals(starter["contact"], saved["contact"], "other sections are untouched")
        assertTrue(vm.state.value.saved)
        assertEquals(0, sites.publishCalls, "saving never publishes")
    }

    @Test
    fun requiredAndMaxLengthBlockSaving() = runTest {
        val vm = editor("hero")
        runCurrent()
        val form = vm.form!!
        form.set("headline", "")
        vm.save()
        runCurrent()
        assertEquals(ValidationError.Required, form.visibleError("headline"))
        form.set("headline", "x".repeat(71))
        vm.save()
        runCurrent()
        assertEquals(ValidationError.MaxLength(70), form.visibleError("headline"))
        assertTrue(sites.drafts.isEmpty())
    }

    @Test
    fun listsStopAtMaxItemsAndItemFieldsAreValidated() = runTest {
        val vm = editor("highlights")
        runCurrent()
        val form = vm.form!!
        repeat(6) { form.addItem("items") }
        assertEquals(4, form.items("items").size, "highlights allow 4 items")
        vm.save()
        runCurrent()
        assertTrue(sites.drafts.isEmpty(), "each item needs an icon and a title")
        form.items("items").forEach { it.set("icon", "shield"); it.set("title", "Claims help") }
        vm.save()
        runCurrent()
        assertEquals(4, (sites.drafts.single()["highlights"]!!.jsonObject["items"] as JsonArray).size)
    }

    @Test
    fun heroPhotoIsCroppedSquareUploadedAndStoredAsItsUrl() = runTest {
        val vm = editor("hero")
        runCurrent()
        vm.onImagePicked("photo", PickedImage { byteArrayOf(9) })
        assertEquals("photo", vm.state.value.uploadingField)
        runCurrent()
        assertEquals(listOf<ImageAspect?>(ImageAspect.Square), website.processor.aspects, "aspect 1:1 from the schema")
        assertEquals(listOf("hero-photo"), website.media.uploads)
        assertTrue(vm.form!!.text("photo").startsWith("https://"))
        assertNull(vm.state.value.uploadingField)
    }

    @Test
    fun anUnreadableImageShowsAnError() = runTest {
        website.processor.fail = true
        val vm = editor("hero")
        runCurrent()
        vm.onImagePicked("photo", PickedImage { byteArrayOf() })
        runCurrent()
        assertEquals(ImageUnreadable, vm.state.value.error)
        assertTrue(vm.form!!.text("photo").isEmpty())
    }

    @Test
    fun publishIssuesShowEveryErrorAndClearOnSave() = runTest {
        sites.site.value = sites.site.value!!.copy(draftContent = buildJsonObject { put("hero", buildJsonObject { }) })
        var cleared: String? = null
        val vm = editor("hero", listOf(PublishError("hero", "headline", null, "required"))) { cleared = it }
        runCurrent()
        assertEquals(1, vm.state.value.issues.size)
        assertEquals(ValidationError.Required, vm.form!!.visibleError("headline"), "shown without touching the field")
        vm.form!!.set("headline", "Ramesh")
        vm.save()
        runCurrent()
        assertEquals("hero", cleared)
        assertTrue(vm.state.value.issues.isEmpty())
    }

    @Test
    fun contentOverTheCapIsRefused() = runTest {
        val vm = editor("about")
        runCurrent()
        vm.form!!.set("bio", "x".repeat(600))
        sites.site.value = sites.site.value!!.copy(
            draftContent = buildJsonObject { put("faq", buildJsonObject { put("blob", "y".repeat(SiteContent.MAX_BYTES)) }) },
        )
        vm.save()
        runCurrent()
        assertEquals(ContentTooLarge, vm.state.value.error)
    }

    // ---- Content: show/hide and reorder --------------------------------------------------------

    @Test
    fun hidingASectionSavesTheDraftPinnedOnesCannotBeHidden() = runTest {
        val vm = ContentViewModel(sites)
        runCurrent()
        assertEquals(SiteContent.sections.size, vm.state.value.rows.size)
        vm.setHidden(section("testimonials"), true)
        runCurrent()
        val hidden = sites.drafts.single()[SiteContent.HIDDEN]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("testimonials"), hidden)
        assertTrue(vm.state.value.rows.first { it.section.key == "testimonials" }.hidden)
        vm.setHidden(section("hero"), true)
        runCurrent()
        assertEquals(1, sites.drafts.size, "pinned sections never hide")
    }

    @Test
    fun reorderIsLocalUntilDone() = runTest {
        val vm = ContentViewModel(sites)
        runCurrent()
        vm.startReorder()
        vm.move(0, 2) // about → after highlights
        assertTrue(sites.drafts.isEmpty(), "nothing saved while dragging")
        assertEquals(listOf("hero", "services", "highlights", "about"), vm.state.value.rows.take(4).map { it.section.key })
        vm.finishReorder()
        runCurrent()
        val order = sites.drafts.single()[SiteContent.SECTION_ORDER]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals("hero", order.first())
        assertEquals("contact", order.last())
        assertEquals("about", order[3])
        assertFalse(vm.state.value.reordering)
    }

    @Test
    fun aFailedSaveRestoresTheRows() = runTest {
        sites.writeError = AppError.Offline
        val vm = ContentViewModel(sites)
        runCurrent()
        vm.setHidden(section("faq"), true)
        runCurrent()
        assertEquals(AppError.Offline, vm.state.value.error)
        assertFalse(vm.state.value.rows.first { it.section.key == "faq" }.hidden)
    }

    // ---- Appearance ------------------------------------------------------------------------------

    @Test
    fun accentLanguageAndTemplateAreSavedAtOnce() = runTest {
        val vm = AppearanceViewModel(sites)
        runCurrent()
        assertEquals(AccentSwatch.Indigo, vm.state.value.accent)
        vm.selectAccent("teal")
        runCurrent()
        assertEquals(SiteAppearance("classic", "teal", null), sites.appearances.last())
        vm.selectLocale("hi")
        runCurrent()
        assertEquals(SiteAppearance("classic", "teal", "hi"), sites.appearances.last())
        vm.state.test {
            awaitItem()
            vm.selectTemplate("modern")
            var s = awaitItem()
            while (!s.templateSaved) s = awaitItem()
            assertEquals("modern", s.site?.templateKey)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(SiteAppearance("modern", "teal", "hi"), sites.appearances.last(), "the accent is kept")
        vm.selectAccent("not-a-swatch")
        runCurrent()
        assertEquals(3, sites.appearances.size, "only catalog swatches")
    }

    @Test
    fun switchingToAnOffersTemplateUnhidesOffersAfterHeroOnce() = runTest {
        val draft = buildJsonObject {
            put("hero", buildJsonObject { put("headline", "Ramesh") })
            put("sectionOrder", buildJsonArray { listOf("hero", "about", "services", "offers", "faq", "contact").forEach { add(it) } })
            put("hidden", buildJsonArray { add("offers"); add("testimonials") })
        }
        sites.site.value = sites.site.value!!.copy(draftContent = draft)
        val vm = AppearanceViewModel(sites)
        runCurrent()
        vm.selectTemplate("spotlight")
        runCurrent()
        val saved = sites.drafts.single()
        assertEquals(listOf("hero", "offers", "about", "services", "faq", "contact"), saved["sectionOrder"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("testimonials"), saved["hidden"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("spotlight", sites.appearances.last().templateKey)
        // Offers -> offers and back to personal change nothing in the draft.
        vm.selectTemplate("guide")
        runCurrent()
        vm.selectTemplate("classic")
        runCurrent()
        assertEquals(1, sites.drafts.size)
        assertEquals("classic", sites.appearances.last().templateKey)
    }

    @Test
    fun aFailedDraftWriteKeepsTheOldTemplate() = runTest {
        val vm = AppearanceViewModel(sites)
        runCurrent()
        sites.writeError = AppError.Offline
        vm.selectTemplate("spotlight")
        runCurrent()
        assertEquals(AppError.Offline, vm.state.value.error)
        assertEquals("classic", vm.state.value.site?.templateKey)
    }

    // ---- Link ----------------------------------------------------------------------------------

    @Test
    fun changeLinkChecksFormatReservedAndAvailability() = runTest {
        val vm = LinkViewModel(sites)
        runCurrent()
        vm.openChange()
        assertEquals("ramesh-kumar", vm.state.value.change?.slug)
        vm.onSlugInput("ab")
        assertEquals(SlugStatus.Invalid(ValidationError.Slug), vm.state.value.change?.status)
        vm.onSlugInput("admin")
        assertEquals(SlugStatus.Invalid(ValidationError.SlugReserved), vm.state.value.change?.status)
        vm.onSlugInput("taken-slug")
        assertEquals(SlugStatus.Checking, vm.state.value.change?.status)
        advanceTimeBy(LinkViewModel.SLUG_DEBOUNCE_MILLIS + 1)
        assertEquals(SlugStatus.Taken, vm.state.value.change?.status)
        vm.onSlugInput("Ramesh-Pune")
        advanceTimeBy(LinkViewModel.SLUG_DEBOUNCE_MILLIS + 1)
        assertEquals(SlugStatus.Available, vm.state.value.change?.status)
        assertEquals(listOf("taken-slug", "ramesh-pune"), sites.availabilityChecks, "only valid slugs reach the server")
        vm.confirmChange()
        runCurrent()
        assertEquals(listOf("ramesh-pune"), sites.slugChanges)
        assertNull(vm.state.value.change)
        assertTrue(vm.state.value.changed)
    }

    @Test
    fun aSlugTakenMeanwhileComesBackAsTaken() = runTest {
        val vm = LinkViewModel(sites)
        runCurrent()
        vm.openChange()
        vm.onSlugInput("new-link")
        advanceTimeBy(LinkViewModel.SLUG_DEBOUNCE_MILLIS + 1)
        sites.taken += "new-link"
        vm.confirmChange()
        runCurrent()
        assertEquals(SlugStatus.Taken, vm.state.value.change?.status)
        assertTrue(sites.slugChanges.isEmpty())
    }

    @Test
    fun theCurrentSlugCannotBeSavedAgain() = runTest {
        val vm = LinkViewModel(sites)
        runCurrent()
        vm.openChange()
        assertFalse(vm.state.value.change!!.canSave("ramesh-kumar"))
    }

    // ---- Publish -------------------------------------------------------------------------------

    private fun hub() = WebsiteHubViewModel(sites, FakeProfileRepository(testProfile()), website.services.stats)

    @Test
    fun publishErrorsBecomeSectionAndFieldIssues() = runTest {
        sites.publishResult = Result.Ok(
            PublishOutcome.Invalid(
                listOf(PublishError("hero", "headline", null, "required"), PublishError("faq", "answer", 0, "maxLength")),
            ),
        )
        val vm = hub()
        runCurrent()
        vm.publish()
        assertEquals(PublishState.Publishing, vm.publish.value)
        runCurrent()
        assertEquals(PublishState.NeedsFixes(2), vm.publish.value)
        assertEquals(listOf("hero", "faq"), vm.issues.value.map { it.section.key })
        assertEquals(ValidationError.MaxLength(400), vm.issuesFor("faq").single().error)
        vm.clearIssues("hero")
        assertEquals(listOf("faq"), vm.issues.value.map { it.section.key })
        vm.consumePublishResult()
        assertEquals(PublishState.Idle, vm.publish.value)
    }

    @Test
    fun aSuccessfulPublishIsLiveAndClearsIssues() = runTest {
        val vm = hub()
        runCurrent()
        vm.publish()
        runCurrent()
        assertEquals(PublishState.Live, vm.publish.value)
        assertTrue(vm.issues.value.isEmpty())
        assertTrue(sites.site.value!!.isPublished)
        assertFalse(sites.site.value!!.hasUnpublishedChanges)
    }

    @Test
    fun aFailedPublishReportsTheError() = runTest {
        sites.publishResult = Result.Err(AppError.ReadOnly)
        val vm = hub()
        runCurrent()
        vm.publish()
        runCurrent()
        assertEquals(PublishState.Failed(AppError.ReadOnly), vm.publish.value)
    }

    // ---- Stats ---------------------------------------------------------------------------------

    @Test
    fun statsFollowThePeriodAndTheHubShowsThisWeek() = runTest {
        website.views.rows.value = listOf(viewRow("2026-10-16", 20), viewRow("2026-10-01", 100))
        website.enquiryDays.value = listOf(kotlinx.datetime.LocalDate(2026, 10, 16))
        val vm = StatsViewModel(website.services.stats)
        runCurrent()
        assertEquals(20, vm.stats.value?.views)
        assertEquals(5.0, vm.stats.value?.conversionPercent)
        vm.selectPeriod(StatsPeriod.Month)
        runCurrent()
        assertEquals(120, vm.stats.value?.views)
        assertEquals(30, vm.stats.value?.days?.size)
        val hub = hub()
        runCurrent()
        assertEquals(20, hub.weekViews.value)
    }
}
