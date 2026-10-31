package com.sledje.agentpro.feature.settings

import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.platform.ImageCrop
import com.sledje.agentpro.core.platform.PickedImage
import com.sledje.agentpro.feature.website.data.ImageUnreadable
import com.sledje.agentpro.testing.FakeProfileRepository
import com.sledje.agentpro.testing.FakeSiteRepository
import com.sledje.agentpro.testing.FakeWebsite
import com.sledje.agentpro.testing.testProfile
import com.sledje.agentpro.testing.testSite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Settings → Profile → the agent's photo, on fakes (docs/TESTING.md). */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfilePhotoTest {
    private val profiles = FakeProfileRepository(testProfile())
    private val sites = FakeSiteRepository(testSite().copy(draftContent = buildJsonObject { put("hero", buildJsonObject { put("headline", "Ramesh Kumar") }) }))
    private val website = FakeWebsite()

    @BeforeTest fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun vm() = ProfilePhotoViewModel(profiles, sites, website.services.imageUploader)

    @Test
    fun aNewPhotoIsSavedOnTheProfileAndTheDraftHeroKeepingTheRestOfTheHero() = runTest {
        val vm = vm()
        runCurrent()
        val crop = ImageCrop(zoom = 2f, centerX = 0.4f, centerY = 0.3f)
        vm.onPicked(PickedImage { byteArrayOf(1) }, crop)
        runCurrent()
        val url = profiles.profile.value?.photoUrl
        assertTrue(url!!.startsWith("https://"))
        assertEquals(listOf<ImageCrop?>(crop), website.processor.crops)
        assertEquals(listOf("profile-photo"), website.media.uploads)
        val hero = sites.site.value!!.draftContent["hero"]!!.jsonObject
        assertEquals(url, hero["photo"]!!.jsonPrimitive.content, "the website shows the same photo")
        assertEquals("Ramesh Kumar", hero["headline"]!!.jsonPrimitive.content, "other hero fields are untouched")
        assertTrue(vm.state.value.saved && !vm.state.value.uploading)
        assertEquals(url, vm.state.value.photoUrl)
    }

    @Test
    fun anUnreadablePhotoChangesNothing() = runTest {
        website.processor.fail = true
        val vm = vm()
        runCurrent()
        vm.onPicked(PickedImage { byteArrayOf() }, null)
        runCurrent()
        assertEquals(ImageUnreadable, vm.state.value.error)
        assertTrue(profiles.updates.isEmpty())
        assertNull(vm.state.value.photoUrl)
    }

    @Test
    fun aFailedProfileSaveShowsTheErrorAndLeavesTheSiteAlone() = runTest {
        profiles.updateError = AppError.Offline
        val vm = vm()
        runCurrent()
        vm.onPicked(PickedImage { byteArrayOf(1) }, null)
        runCurrent()
        assertEquals(AppError.Offline, vm.state.value.error)
        assertTrue(!vm.state.value.saved)
        assertNull(sites.site.value!!.draftContent["hero"]!!.jsonObject["photo"])
    }
}
