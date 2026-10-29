package com.sledje.agentpro.feature.onboarding

import com.sledje.agentpro.app.AppEvents
import com.sledje.agentpro.app.OnboardingSession
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.forms.FieldType
import com.sledje.agentpro.core.forms.splitVisibleFields
import com.sledje.agentpro.core.platform.ImageAspect
import com.sledje.agentpro.core.platform.ImageCrop
import com.sledje.agentpro.core.platform.PickedImage
import com.sledje.agentpro.feature.onboarding.ui.OnboardingStep
import com.sledje.agentpro.feature.onboarding.ui.OnboardingViewModel
import com.sledje.agentpro.feature.onboarding.ui.OnboardingViewModel.Companion.MOBILE
import com.sledje.agentpro.feature.onboarding.ui.OnboardingViewModel.Companion.NAME
import com.sledje.agentpro.feature.onboarding.ui.OnboardingViewModel.Companion.PHOTO
import com.sledje.agentpro.feature.website.data.MediaPaths
import com.sledje.agentpro.testing.FakeCompanyRepository
import com.sledje.agentpro.testing.testMembership
import com.sledje.agentpro.testing.FakeProfileRepository
import com.sledje.agentpro.testing.FakeSiteRepository
import com.sledje.agentpro.testing.FakeWebsite
import com.sledje.agentpro.testing.testProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Onboarding step 5's photo (docs/UX.md: a screen of its own, skippable), through the Website media pipeline. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingPhotoTest {
    private val profiles = FakeProfileRepository(testProfile(fullName = null, phone = null, designation = null, city = null))
    private val website = FakeWebsite()

    @BeforeTest fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun vm() = OnboardingViewModel(profiles, FakeSiteRepository(), OnboardingSession(), AppEvents(), FakeCompanyRepository(listOf(testMembership())), photoUploader = website.services.imageUploader)

    @Test
    fun photoIsAnOptionalSquareImageOnItsOwnStep() {
        val spec = OnboardingViewModel.PROFILE_FIELDS.first { it.key == PHOTO }
        assertEquals(FieldType.Image, spec.type)
        assertEquals("1:1", spec.aspect)
        assertFalse(spec.required)
        assertEquals(setOf(PHOTO), OnboardingViewModel.PHOTO_STEP_KEYS)
        // Step 4 shows only the city, with nothing left over for a More details group.
        val step4 = OnboardingViewModel.PROFILE_FIELDS.filterNot { it.key in OnboardingViewModel.MOBILE_STEP_KEYS + OnboardingViewModel.PHOTO_STEP_KEYS }
        val (visible, more) = splitVisibleFields(step4, OnboardingViewModel.DETAILS_VISIBLE_KEYS)
        assertEquals(listOf(OnboardingViewModel.CITY), visible.map { it.key })
        assertTrue(more.isEmpty())
    }

    @Test
    fun pickedPhotoIsUploadedAndSavedWithTheProfile() = runTest {
        val vm = vm()
        runCurrent()
        val crop = ImageCrop(zoom = 1.5f, centerX = 0.5f, centerY = 0.35f)
        vm.uploadPhoto(PickedImage { byteArrayOf(1) }, crop)
        assertTrue(vm.state.value.uploadingPhoto)
        runCurrent()
        assertEquals(listOf<ImageAspect?>(ImageAspect.Square), website.processor.aspects)
        assertEquals(listOf<ImageCrop?>(crop), website.processor.crops, "the agent's crop from the photo editor")
        assertEquals(3, vm.profileForm.spec(OnboardingViewModel.PHOTO).shownAs.size, "the hero photo's shapes")
        assertEquals(listOf(MediaPaths.PROFILE_PHOTO), website.media.uploads)
        vm.profileForm.set(NAME, "Ramesh Kumar")
        vm.profileForm.set(MOBILE, "9876543210")
        vm.savePhoto()
        runCurrent()
        assertTrue(profiles.updates.single().photoUrl!!.startsWith("https://"))
        assertEquals(OnboardingStep.Preview, vm.state.value.navigateTo)
    }

    @Test
    fun aFailedUploadKeepsTheFormAndShowsTheError() = runTest {
        website.media.error = AppError.Offline
        val vm = vm()
        runCurrent()
        vm.uploadPhoto(PickedImage { byteArrayOf(1) })
        runCurrent()
        assertEquals(AppError.Offline, vm.state.value.error)
        assertFalse(vm.state.value.uploadingPhoto)
        assertTrue(vm.profileForm.text(PHOTO).isEmpty())
    }
}
