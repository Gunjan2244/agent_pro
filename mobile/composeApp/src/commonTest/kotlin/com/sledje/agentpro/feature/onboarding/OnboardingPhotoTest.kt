package com.sledje.agentpro.feature.onboarding

import com.sledje.agentpro.app.AppEvents
import com.sledje.agentpro.app.OnboardingSession
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.forms.FieldType
import com.sledje.agentpro.core.forms.splitVisibleFields
import com.sledje.agentpro.core.platform.ImageAspect
import com.sledje.agentpro.core.platform.PickedImage
import com.sledje.agentpro.feature.onboarding.ui.OnboardingViewModel
import com.sledje.agentpro.feature.onboarding.ui.OnboardingViewModel.Companion.MOBILE
import com.sledje.agentpro.feature.onboarding.ui.OnboardingViewModel.Companion.NAME
import com.sledje.agentpro.feature.onboarding.ui.OnboardingViewModel.Companion.PHOTO
import com.sledje.agentpro.feature.website.data.MediaPaths
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

/** Onboarding step 1's photo (docs/UX.md: under More details), through the Website media pipeline. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingPhotoTest {
    private val profiles = FakeProfileRepository(testProfile(fullName = null, phone = null, designation = null, city = null))
    private val website = FakeWebsite()

    @BeforeTest fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun vm() = OnboardingViewModel(profiles, FakeSiteRepository(), OnboardingSession(), AppEvents(), photoUploader = website.services.imageUploader)

    @Test
    fun photoIsAnOptionalSquareImageUnderMoreDetails() {
        val spec = OnboardingViewModel.PROFILE_FIELDS.first { it.key == PHOTO }
        assertEquals(FieldType.Image, spec.type)
        assertEquals("1:1", spec.aspect)
        val (visible, more) = splitVisibleFields(OnboardingViewModel.PROFILE_FIELDS, OnboardingViewModel.VISIBLE_PROFILE_KEYS)
        assertTrue(more.any { it.key == PHOTO })
        assertFalse(visible.any { it.key == PHOTO })
    }

    @Test
    fun pickedPhotoIsUploadedAndSavedWithTheProfile() = runTest {
        val vm = vm()
        runCurrent()
        vm.uploadPhoto(PickedImage { byteArrayOf(1) })
        assertTrue(vm.state.value.uploadingPhoto)
        runCurrent()
        assertEquals(listOf<ImageAspect?>(ImageAspect.Square), website.processor.aspects)
        assertEquals(listOf(MediaPaths.PROFILE_PHOTO), website.media.uploads)
        vm.profileForm.set(NAME, "Ramesh Kumar")
        vm.profileForm.set(MOBILE, "9876543210")
        vm.saveProfile()
        runCurrent()
        assertTrue(profiles.updates.single().photoUrl!!.startsWith("https://"))
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
