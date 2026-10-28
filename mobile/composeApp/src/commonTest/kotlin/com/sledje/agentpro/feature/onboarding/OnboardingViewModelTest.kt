package com.sledje.agentpro.feature.onboarding

import app.cash.turbine.test
import com.sledje.agentpro.app.AppEvents
import com.sledje.agentpro.app.AppMessage
import com.sledje.agentpro.app.OnboardingSession
import com.sledje.agentpro.core.catalog.AppLocale
import com.sledje.agentpro.core.catalog.Pricing
import com.sledje.agentpro.core.catalog.PrivacyNotice
import com.sledje.agentpro.core.catalog.ReservedSlugs
import com.sledje.agentpro.core.catalog.Templates
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.forms.ValidationError
import com.sledje.agentpro.feature.onboarding.ui.LaunchStep
import com.sledje.agentpro.feature.onboarding.ui.OnboardingStep
import com.sledje.agentpro.feature.onboarding.ui.SiteLaunch
import com.sledje.agentpro.feature.onboarding.ui.OnboardingViewModel
import com.sledje.agentpro.feature.onboarding.ui.OnboardingViewModel.Companion.MOBILE
import com.sledje.agentpro.feature.onboarding.ui.OnboardingViewModel.Companion.NAME
import com.sledje.agentpro.feature.onboarding.ui.SlugStatus
import com.sledje.agentpro.feature.onboarding.ui.SlugSuggestion
import com.sledje.agentpro.feature.website.data.PublishError
import com.sledje.agentpro.feature.website.data.PublishOutcome
import com.sledje.agentpro.feature.website.data.StarterContent
import com.sledje.agentpro.testing.FakeCompanyRepository
import com.sledje.agentpro.testing.FakeConsentRepository
import com.sledje.agentpro.testing.FakeProfileRepository
import com.sledje.agentpro.testing.testMembership
import com.sledje.agentpro.testing.TEST_USER
import com.sledje.agentpro.testing.FakeSiteRepository
import com.sledje.agentpro.testing.testProfile
import com.sledje.agentpro.testing.testSite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.jsonArray
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
    private val consents = FakeConsentRepository()
    private val sites = FakeSiteRepository()
    private val session = OnboardingSession().apply { start() }
    private val events = AppEvents()
    /** Step 4 needs one company link (DECISIONS #89); most tests start with it already added. */
    private val companies = FakeCompanyRepository(listOf(testMembership()))

    @BeforeTest fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun vm() = OnboardingViewModel(profiles, sites, session, events, companies, consents = consents)

    @Test
    fun mobileStepSavesAndMovesToTheStyleStep() = runTest {
        val vm = vm()
        runCurrent()
        assertFalse(vm.profileForm.isValid, "name and mobile are required")
        vm.profileForm.set(NAME, "Ramesh Kumar")
        vm.profileForm.set(MOBILE, "98765 43210")
        vm.state.test {
            awaitItem()
            vm.saveMobile()
            assertTrue(awaitItem().saving)
            var saved = awaitItem()
            while (saved.navigateTo == null) saved = awaitItem()
            assertEquals(OnboardingStep.Intro, saved.navigateTo)
            assertEquals("ramesh-kumar", saved.slug, "the link is suggested from the name")
            cancelAndIgnoreRemainingEvents()
        }
        val input = profiles.updates.single()
        assertEquals("+919876543210", input.phone, "mobile normalised to +91")
        assertEquals(null, input.city, "blank optional fields are null")
    }

    @Test
    fun theNameFromGoogleShowsAsASummaryWithTheEmailAndOnlyTheMobileIsAsked() = runTest {
        // A Google sign-up: the trigger saved Google's name and email; there is no mobile yet.
        profiles.profile.value = testProfile(fullName = "Ramesh Kumar", phone = null, designation = null, city = null)
        val vm = vm()
        runCurrent()
        assertTrue(vm.state.value.contactSummary, "the name isn't asked again")
        assertEquals("ramesh@example.com", vm.state.value.email)
        assertFalse(vm.profileForm.isValid, "the mobile is still needed")
        vm.profileForm.set(MOBILE, "91234 56789")
        vm.saveMobile()
        runCurrent()
        assertEquals("Ramesh Kumar", profiles.updates.single().fullName)
        assertEquals("+919123456789", profiles.updates.single().phone)
        assertEquals(OnboardingStep.Intro, vm.state.value.navigateTo, "no company is needed yet")
    }

    @Test
    fun editOnTheSummaryShowsTheNameField() = runTest {
        profiles.profile.value = testProfile(fullName = "Ramesh Kumar", phone = "+919876543210", designation = null, city = null)
        val vm = vm()
        runCurrent()
        assertTrue(vm.profileForm.isValid, "Continue works straight away")
        vm.editContact()
        assertFalse(vm.state.value.contactSummary)
        vm.profileForm.set(NAME, "Ramesh K. Kumar")
        vm.saveMobile()
        runCurrent()
        assertEquals("Ramesh K. Kumar", profiles.updates.single().fullName, "the edit is saved")
    }

    @Test
    fun noSummaryWithoutAName() = runTest {
        profiles.profile.value = testProfile(fullName = null, phone = null)
        val vm = vm()
        runCurrent()
        assertFalse(vm.state.value.contactSummary)
    }

    @Test
    fun aPendingGoogleSignUpConsentIsRecordedBeforeTheMobileIsSaved() = runTest {
        consents.pendingSignup = PrivacyNotice.AgentSignup.consent(AppLocale.En)
        consents.error = AppError.Offline
        val vm = vm()
        runCurrent()
        vm.profileForm.set(NAME, "Ramesh Kumar")
        vm.profileForm.set(MOBILE, "98765 43210")
        vm.saveMobile()
        runCurrent()
        assertEquals(AppError.Offline, vm.state.value.error)
        assertTrue(profiles.updates.isEmpty(), "nothing is saved before the consent is recorded")

        consents.error = null
        vm.saveMobile()
        runCurrent()
        assertEquals(null, consents.pendingSignup)
        assertEquals(2, consents.calls.count { it.startsWith("signup:agent_signup:") }, "the failed try, then the recorded one")
        assertEquals(1, profiles.updates.size)
        assertEquals(OnboardingStep.Intro, vm.state.value.navigateTo)
    }

    @Test
    fun aTappedTileIsPreviewedThenTaken() = runTest {
        val vm = vm()
        vm.previewTemplate("guide")
        assertEquals(OnboardingStep.Template, vm.state.value.navigateTo)
        assertEquals("guide", vm.state.value.templateKey, "step 3 previews the tapped template")
        vm.onNavigated()
        vm.previewTemplate("no-such-template")
        assertEquals(null, vm.state.value.navigateTo, "an unknown key goes nowhere")
        assertEquals("guide", vm.state.value.templateKey)
        vm.selectTemplate("guide")
        assertEquals(OnboardingStep.Details, vm.state.value.navigateTo)
        assertEquals("guide", vm.state.value.templateKey)
    }

    @Test
    fun theDesignsArePreviewedBeforeAnyCompanyIsAdded() = runTest {
        profiles.profile.value = testProfile()
        val vm = OnboardingViewModel(profiles, sites, session, events, FakeCompanyRepository())
        runCurrent()
        assertEquals(emptyList(), vm.insuranceTypes())
        val content = vm.starterContent(Templates.fromKey("classic")!!)!!
        val offered = content["services"]!!.jsonObject["offered"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("term", "endowment", "child", "pension"), offered, "the seed's default (life) until step 4")
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
        vm.saveMobile()
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
        assertEquals(OnboardingStep.Details, vm.state.value.navigateTo, "the companies come after the design")
        vm.selectTemplate("no-such-template")
        assertEquals(last.key, vm.state.value.templateKey)
    }

    @Test
    fun theSiteIsCreatedWithSeededContentForTheChosenTemplateInTheAppLanguage() = runTest {
        profiles.profile.value = testProfile()
        val vm = vm()
        runCurrent()
        vm.setLocale("hi")
        vm.setLocale("zz")
        vm.selectTemplate("spotlight")
        vm.onSlugChange("ramesh-k")
        advanceTimeBy(OnboardingViewModel.SLUG_DEBOUNCE_MILLIS + 1)
        vm.later()
        runCurrent()
        val created = sites.created.single()
        assertEquals("hi", created.locale)
        assertEquals(vm.starterContent(Templates.fromKey("spotlight")!!), created.draftContent, "the preview is what gets created")
        assertEquals(TEST_USER, testProfile().id)
        val spotlight = Templates.fromKey("spotlight")!!
        assertEquals(StarterContent.from(testProfile(), spotlight, "hi", TEST_USER), created.draftContent)
        assertTrue(created.draftContent["about"]!!.jsonObject["bio"]!!.jsonPrimitive.content.isNotBlank())
        assertEquals("offers", created.draftContent["sectionOrder"]!!.jsonArray[1].jsonPrimitive.content)
        assertFalse("offers" in created.draftContent["hidden"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun theSiteIsSeededFromTheInsuranceTypesOfStep4sCompanies() = runTest {
        profiles.profile.value = testProfile()
        companies.request("c2", null, "AG9", listOf("motor", "health"))
        val vm = vm()
        runCurrent()
        assertEquals(listOf("life", "health", "motor"), vm.insuranceTypes(), "the union over the pending links, in catalog order")
        vm.selectTemplate("classic")
        vm.onSlugChange("ramesh-k")
        advanceTimeBy(OnboardingViewModel.SLUG_DEBOUNCE_MILLIS + 1)
        vm.later()
        runCurrent()
        val created = sites.created.single().draftContent
        val offered = created["services"]!!.jsonObject["offered"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("term", "health", "motor", "endowment", "child", "pension"), offered)
        assertEquals(StarterContent.from(testProfile(), Templates.fromKey("classic")!!, "en", TEST_USER, listOf("life", "health", "motor")), created)
    }

    @Test
    fun theStarterPreviewDiffersPerFamily() = runTest {
        profiles.profile.value = testProfile()
        val vm = vm()
        runCurrent()
        val personal = vm.starterContent(Templates.fromKey("classic")!!)!!
        val offers = vm.starterContent(Templates.fromKey("guide")!!)!!
        assertTrue(personal["hidden"] != offers["hidden"])
        assertTrue(personal["sectionOrder"] != offers["sectionOrder"])
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
    fun publishNowCreatesTheSitePublishesAndStartsTheLaunchScreens() = runTest {
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
        assertTrue("Ramesh Kumar" in created.draftContent["hero"]!!.jsonObject["headline"]!!.jsonPrimitive.content, "a greeting with the name")
        assertEquals("en", created.locale)
        assertEquals("+919876543210", created.draftContent["contact"]!!.jsonObject["phone"]!!.jsonPrimitive.content)
        assertEquals(1, sites.publishCalls)
        assertEquals(SiteLaunch("ramesh-k", LaunchStep.AddToPhone), vm.state.value.launch)
        assertFalse(vm.state.value.finished, "Add to phone and Share come before Home")
        assertTrue(session.active.value)
        assertEquals(null, events.message.value)
    }

    @Test
    fun theLaunchScreensGoAddToPhoneShareThenHome() = runTest {
        profiles.profile.value = testProfile()
        val vm = vm()
        vm.onSlugChange("ramesh-k")
        advanceTimeBy(OnboardingViewModel.SLUG_DEBOUNCE_MILLIS + 1)
        vm.publishNow()
        runCurrent()
        vm.launchBack()
        assertEquals(LaunchStep.AddToPhone, vm.state.value.launch?.step, "no screen before the first")
        vm.launchNext()
        assertEquals(LaunchStep.Share, vm.state.value.launch?.step)
        vm.launchBack()
        assertEquals(LaunchStep.AddToPhone, vm.state.value.launch?.step)
        vm.launchNext()
        assertTrue(session.active.value)
        vm.finishLaunch()
        assertTrue(vm.state.value.finished)
        assertFalse(session.active.value, "the gate may now open Main")
        assertEquals(AppMessage.TrialStarted(Pricing.Service.trialDays), events.message.value)
    }

    @Test
    fun skippingThroughTheLastScreenAlsoGoesHome() = runTest {
        profiles.profile.value = testProfile()
        val vm = vm()
        vm.onSlugChange("ramesh-k")
        advanceTimeBy(OnboardingViewModel.SLUG_DEBOUNCE_MILLIS + 1)
        vm.publishNow()
        runCurrent()
        repeat(LaunchStep.entries.size) { vm.launchNext() }
        assertTrue(vm.state.value.finished)
        assertFalse(session.active.value)
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
        assertTrue(vm.state.value.finished, "a draft isn't live: nothing to add or share, straight to Home")
        assertEquals(null, vm.state.value.launch)
        assertFalse(session.active.value)
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
        assertEquals(LaunchStep.AddToPhone, vm.state.value.launch?.step)
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

    @Test
    fun anExistingSiteClosesOnboardingWithoutANewLink() = runTest {
        profiles.profile.value = testProfile()
        val vm = vm()
        runCurrent()
        assertFalse(vm.state.value.finished)
        // The agent's site syncs in while onboarding is open (e.g. after an app update emptied the cache).
        sites.site.value = testSite("gunjan23")
        runCurrent()
        assertTrue(vm.state.value.finished)
        assertFalse(session.active.value, "the gate opens Main")
        assertTrue(sites.created.isEmpty(), "no new site, so the link stays as it is")
        assertEquals(null, events.message.value, "no trial message for an agent who already set up")
    }

    @Test
    fun mobileStepChecksTheServerForASiteBeforeTheSiteSteps() = runTest {
        // The profile was incomplete, so onboarding opened; the agent already has a site on the server.
        sites.serverSite = testSite("gunjan23")
        val vm = vm()
        runCurrent()
        vm.profileForm.set(NAME, "Gunjan")
        vm.profileForm.set(MOBILE, "98765 43210")
        vm.saveMobile()
        runCurrent()
        assertEquals(1, sites.fetches)
        assertTrue(vm.state.value.finished, "straight to Main with the existing site")
        assertEquals(null, vm.state.value.navigateTo, "no template intro, no Your link")
        assertFalse(session.active.value)
        assertTrue(sites.created.isEmpty())
    }

    @Test
    fun mobileStepStaysPutWhenTheServerCantBeAsked() = runTest {
        sites.fetchError = AppError.Offline
        val vm = vm()
        runCurrent()
        vm.profileForm.set(NAME, "Gunjan")
        vm.profileForm.set(MOBILE, "98765 43210")
        vm.saveMobile()
        runCurrent()
        assertEquals(AppError.Offline, vm.state.value.error)
        assertEquals(null, vm.state.value.navigateTo, "never on to the site steps without knowing")
        assertFalse(vm.state.value.saving)
    }

    @Test
    fun detailsStepSavesTheCityAndMovesToThePhoto() = runTest {
        profiles.profile.value = testProfile(city = null)
        val vm = vm()
        runCurrent()
        vm.profileForm.set(OnboardingViewModel.CITY, "Pune")
        vm.saveDetails()
        runCurrent()
        assertEquals("Pune", profiles.updates.single().city)
        assertEquals(OnboardingStep.Photo, vm.state.value.navigateTo)
        assertEquals(0, sites.fetches, "the site check was step 1's")
    }

    @Test
    fun noAgencyCodeOrBranchIsAskedAndSavedValuesAreKept() = runTest {
        assertEquals(listOf(NAME, MOBILE, OnboardingViewModel.CITY, OnboardingViewModel.PHOTO), OnboardingViewModel.PROFILE_FIELDS.map { it.key })
        profiles.profile.value = testProfile().copy(agencyCode = "AG9", branch = "Kothrud")
        val vm = vm()
        runCurrent()
        vm.saveDetails()
        runCurrent()
        assertEquals("AG9", profiles.updates.single().agencyCode)
        assertEquals("Kothrud", profiles.updates.single().branch)
    }

    @Test
    fun skippingThePhotoWithoutOneSavedGoesStraightToTheWebsite() = runTest {
        profiles.profile.value = testProfile().copy(photoUrl = null)
        val vm = vm()
        runCurrent()
        vm.skipPhoto()
        runCurrent()
        assertTrue(profiles.updates.isEmpty(), "nothing to save")
        assertEquals(OnboardingStep.Preview, vm.state.value.navigateTo)
    }

    @Test
    fun skippingRemovesAPhotoSavedEarlier() = runTest {
        profiles.profile.value = testProfile().copy(photoUrl = "https://example.com/me.webp")
        val vm = vm()
        runCurrent()
        vm.skipPhoto()
        runCurrent()
        assertEquals(null, profiles.updates.single().photoUrl)
        assertEquals(OnboardingStep.Preview, vm.state.value.navigateTo)
    }

    @Test
    fun theWebsiteStepContinuesToTheLink() = runTest {
        val vm = vm()
        runCurrent()
        vm.openLink()
        assertEquals(OnboardingStep.Link, vm.state.value.navigateTo)
    }

    @Test
    fun detailsStepWaitsForAtLeastOneCompany() = runTest {
        val noCompanies = FakeCompanyRepository()
        val vm = OnboardingViewModel(profiles, sites, session, events, noCompanies)
        runCurrent()
        vm.profileForm.set(NAME, "Ramesh Kumar")
        vm.profileForm.set(MOBILE, "98765 43210")
        vm.saveDetails()
        runCurrent()
        assertTrue(profiles.updates.isEmpty(), "nothing is saved without a company")
        assertEquals(AppError.Validation("companies", "required"), vm.state.value.error)
        assertEquals(null, vm.state.value.navigateTo)
    }

    @Test
    fun aRejectedCompanyAloneDoesNotCount() = runTest {
        val rejected = FakeCompanyRepository(listOf(testMembership(status = "rejected", rejectReason = "not_found")))
        val vm = OnboardingViewModel(profiles, sites, session, events, rejected)
        runCurrent()
        vm.profileForm.set(NAME, "Ramesh Kumar")
        vm.profileForm.set(MOBILE, "98765 43210")
        vm.saveDetails()
        runCurrent()
        assertTrue(profiles.updates.isEmpty())
    }
}
