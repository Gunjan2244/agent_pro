package com.sledje.agentpro.feature.onboarding

import app.cash.turbine.test
import com.sledje.agentpro.app.AppEvents
import com.sledje.agentpro.app.AppMessage
import com.sledje.agentpro.app.OnboardingSession
import com.sledje.agentpro.core.catalog.Pricing
import com.sledje.agentpro.core.catalog.ReservedSlugs
import com.sledje.agentpro.core.catalog.Templates
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.forms.ValidationError
import com.sledje.agentpro.feature.onboarding.ui.OnboardingStep
import com.sledje.agentpro.feature.onboarding.ui.OnboardingViewModel
import com.sledje.agentpro.feature.onboarding.ui.OnboardingViewModel.Companion.MOBILE
import com.sledje.agentpro.feature.onboarding.ui.OnboardingViewModel.Companion.NAME
import com.sledje.agentpro.feature.onboarding.ui.SlugStatus
import com.sledje.agentpro.feature.onboarding.ui.SlugSuggestion
import com.sledje.agentpro.feature.website.data.PublishError
import com.sledje.agentpro.feature.website.data.PublishOutcome
import com.sledje.agentpro.testing.FakeProfileRepository
import com.sledje.agentpro.testing.FakeSiteRepository
import com.sledje.agentpro.testing.testProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val profiles = FakeProfileRepository(testProfile(fullName = null, phone = null, designation = null, city = null))
    private val sites = FakeSiteRepository()
    private val session = OnboardingSession().apply { start() }
    private val events = AppEvents()

    @BeforeTest fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun vm() = OnboardingViewModel(profiles, sites, session, events)

    @Test
    fun profileStepSavesAndMovesToTheTemplateStep() = runTest {
        val vm = vm()
        runCurrent()
        assertFalse(vm.profileForm.isValid, "name and mobile are required")
        vm.profileForm.set(NAME, "Ramesh Kumar")
        vm.profileForm.set(MOBILE, "98765 43210")
        vm.state.test {
            awaitItem()
            vm.saveProfile()
            assertTrue(awaitItem().saving)
            var saved = awaitItem()
            while (saved.navigateTo == null) saved = awaitItem()
            assertEquals(OnboardingStep.Template, saved.navigateTo)
            assertEquals("ramesh-kumar", saved.slug, "the link is suggested from the name")
            cancelAndIgnoreRemainingEvents()
        }
        val input = profiles.updates.single()
        assertEquals("+919876543210", input.phone, "mobile normalised to +91")
        assertEquals(null, input.designation, "blank optional fields are null")
    }

    @Test
    fun nameAndMobileFromSignUpShowAsASummaryWithEdit() = runTest {
        profiles.profile.value = testProfile(fullName = "Ramesh Kumar", phone = "+919876543210", designation = null, city = null)
        val vm = vm()
        runCurrent()
        assertTrue(vm.state.value.contactSummary, "not asked again")
        assertTrue(vm.profileForm.isValid, "Continue works straight away; the rest is optional")
        vm.editContact()
        assertFalse(vm.state.value.contactSummary)
        vm.profileForm.set(MOBILE, "91234 56789")
        vm.saveProfile()
        runCurrent()
        assertEquals("Ramesh Kumar", profiles.updates.single().fullName)
        assertEquals("+919123456789", profiles.updates.single().phone, "the edit is saved")
    }

    @Test
    fun noSummaryWithoutAValidMobile() = runTest {
        profiles.profile.value = testProfile(fullName = "Ramesh Kumar", phone = null)
        val vm = vm()
        runCurrent()
        assertFalse(vm.state.value.contactSummary)
    }

    @Test
    fun profileStepIsPrefilledFromTheSavedProfile() = runTest {
        profiles.profile.value = testProfile()
        val vm = vm()
        runCurrent()
        assertEquals("Ramesh Kumar", vm.profileForm.text(NAME))
        assertEquals("+919876543210", vm.profileForm.text(MOBILE))
    }

    @Test
    fun profileSaveErrorIsShown() = runTest {
        profiles.updateError = AppError.Offline
        val vm = vm()
        vm.profileForm.set(NAME, "Ramesh")
        vm.profileForm.set(MOBILE, "9876543210")
        vm.saveProfile()
        runCurrent()
        assertEquals(AppError.Offline, vm.state.value.error)
        assertEquals(null, vm.state.value.navigateTo)
    }

    @Test
    fun templateComesFromTheGeneratedRegistry() = runTest {
        val vm = vm()
        assertEquals(Templates.all.first().key, vm.state.value.templateKey)
        val last = Templates.all.last()
        vm.selectTemplate(last.key)
        assertEquals(last.key, vm.state.value.templateKey)
        assertEquals(OnboardingStep.Link, vm.state.value.navigateTo)
        vm.selectTemplate("no-such-template")
        assertEquals(last.key, vm.state.value.templateKey)
    }

    @Test
    fun slugIsValidatedLocallyThenCheckedAfterThePause() = runTest {
        val vm = vm()
        vm.onSlugChange("Ab")
        assertEquals("ab", vm.state.value.slug)
        assertEquals(SlugStatus.Invalid(ValidationError.Slug), vm.state.value.slugStatus)
        vm.onSlugChange(ReservedSlugs.all.first { it.length >= 3 })
        assertEquals(SlugStatus.Invalid(ValidationError.SlugReserved), vm.state.value.slugStatus)
        assertTrue(sites.availabilityChecks.isEmpty(), "invalid slugs never reach the server")

        vm.onSlugChange("ramesh")
        vm.onSlugChange("ramesh-k")
        assertEquals(SlugStatus.Checking, vm.state.value.slugStatus)
        advanceTimeBy(OnboardingViewModel.SLUG_DEBOUNCE_MILLIS + 1)
        assertEquals(listOf("ramesh-k"), sites.availabilityChecks, "debounced: only the last value is checked")
        assertEquals(SlugStatus.Available, vm.state.value.slugStatus)

        vm.onSlugChange("taken-slug")
        advanceTimeBy(OnboardingViewModel.SLUG_DEBOUNCE_MILLIS + 1)
        assertEquals(SlugStatus.Taken, vm.state.value.slugStatus)
        assertFalse(vm.state.value.canSubmitLink)
    }

    @Test
    fun publishNowCreatesTheSitePublishesAndFinishes() = runTest {
        profiles.profile.value = testProfile()
        val vm = vm()
        runCurrent()
        vm.selectTemplate("modern")
        vm.onSlugChange("ramesh-k")
        advanceTimeBy(OnboardingViewModel.SLUG_DEBOUNCE_MILLIS + 1)
        vm.publishNow()
        runCurrent()
        val created = sites.created.single()
        assertEquals("ramesh-k", created.slug)
        assertEquals("modern", created.templateKey)
        assertEquals(Templates.fromKey("modern")!!.defaultAccent.key, created.accent)
        assertEquals("Ramesh Kumar", created.draftContent["hero"]!!.jsonObject["headline"]!!.jsonPrimitive.content)
        assertEquals("+919876543210", created.draftContent["contact"]!!.jsonObject["phone"]!!.jsonPrimitive.content)
        assertEquals(1, sites.publishCalls)
        assertTrue(vm.state.value.finished)
        assertFalse(session.active.value, "the gate may now open Main")
        assertEquals(AppMessage.TrialStarted(Pricing.Service.trialDays), events.message.value)
    }

    @Test
    fun laterCreatesADraftWithoutPublishing() = runTest {
        profiles.profile.value = testProfile()
        val vm = vm()
        runCurrent()
        advanceTimeBy(OnboardingViewModel.SLUG_DEBOUNCE_MILLIS + 1) // the suggested slug gets checked
        assertEquals(SlugStatus.Available, vm.state.value.slugStatus)
        vm.later()
        runCurrent()
        assertEquals(1, sites.created.size)
        assertEquals(0, sites.publishCalls)
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun aSlugTakenMeanwhileShowsTakenAndCreatesNothing() = runTest {
        profiles.profile.value = testProfile()
        val vm = vm()
        vm.onSlugChange("ramesh-k")
        advanceTimeBy(OnboardingViewModel.SLUG_DEBOUNCE_MILLIS + 1)
        sites.taken += "ramesh-k" // another agent took it after the check
        vm.publishNow()
        runCurrent()
        assertEquals(SlugStatus.Taken, vm.state.value.slugStatus)
        assertTrue(sites.created.isEmpty())
        assertFalse(vm.state.value.finished)
        assertTrue(session.active.value)
    }

    @Test
    fun aRejectedPublishKeepsTheSiteAndRetriesOnlyThePublish() = runTest {
        profiles.profile.value = testProfile()
        sites.publishResult = Result.Ok(PublishOutcome.Invalid(listOf(PublishError("hero", "headline", null, "required"))))
        val vm = vm()
        vm.onSlugChange("ramesh-k")
        advanceTimeBy(OnboardingViewModel.SLUG_DEBOUNCE_MILLIS + 1)
        vm.publishNow()
        runCurrent()
        assertEquals(AppError.Validation("site"), vm.state.value.error)
        assertFalse(vm.state.value.finished)

        sites.publishResult = Result.Ok(PublishOutcome.Published(null))
        vm.publishNow()
        runCurrent()
        assertEquals(1, sites.created.size, "the site isn't created twice")
        assertEquals(2, sites.publishCalls)
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun anUncheckedSlugCanStillBeSaved() = runTest {
        sites.availabilityError = AppError.Offline
        val vm = vm()
        vm.onSlugChange("ramesh-k")
        advanceTimeBy(OnboardingViewModel.SLUG_DEBOUNCE_MILLIS + 1)
        assertEquals(SlugStatus.Unchecked(AppError.Offline), vm.state.value.slugStatus)
        assertTrue(vm.state.value.canSubmitLink)
    }

    @Test
    fun slugSuggestions() {
        assertEquals("ramesh-kumar", SlugSuggestion.fromName("Ramesh Kumar"))
        assertEquals("r-k-sharma-2", SlugSuggestion.fromName("  R. K. Sharma (2) "))
        assertEquals("", SlugSuggestion.fromName("रमेश"), "no ASCII letters: no suggestion")
        assertEquals("", SlugSuggestion.fromName("Al"), "too short")
        assertEquals("", SlugSuggestion.fromName("Admin"), "reserved slugs are never suggested")
        assertEquals(30, SlugSuggestion.fromName("a".repeat(40)).length)
        assertEquals("abcdefghij-abcdefghij-abcdefgh", SlugSuggestion.fromName("abcdefghij abcdefghij abcdefgh-ij"))
        assertEquals(null, SlugSuggestion.fromName(null).ifEmpty { null })
    }
}
