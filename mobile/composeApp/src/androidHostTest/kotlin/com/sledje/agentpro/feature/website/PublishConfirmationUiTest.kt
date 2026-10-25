package com.sledje.agentpro.feature.website

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.sledje.agentpro.app.App
import com.sledje.agentpro.feature.auth.data.AuthState
import com.sledje.agentpro.testing.FakeAppDependencies
import com.sledje.agentpro.testing.FakeAuthRepository
import com.sledje.agentpro.testing.TEST_USER
import com.sledje.agentpro.testing.testProfile
import com.sledje.agentpro.testing.testSite
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * "Your website is live" after Publish stays on screen (user report 2026-10-25: no confirmation after publishing).
 * Consuming the publish result used to cancel the snackbar it had just shown, so it flashed for one frame.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class PublishConfirmationUiTest {
    @Test
    fun publishingFromTheWebsiteTabShowsYourWebsiteIsLive() = runComposeUiTest {
        val deps = FakeAppDependencies(auth = FakeAuthRepository(AuthState.SignedIn(TEST_USER, "ramesh@example.com")))
        deps.profiles.profile.value = testProfile()
        deps.sites.site.value = testSite()
        setContent { App(deps) }

        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Website") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasText("Website") and hasClickAction()).performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Publish") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasText("Publish") and hasClickAction()).performClick()

        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Your website is live")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, deps.sites.publishCalls)
        // Well inside the snackbar's 4 s: it must still be showing.
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(1_000)
        onNodeWithText("Your website is live").assertExists()
    }
}
