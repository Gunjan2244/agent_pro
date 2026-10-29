package com.sledje.agentpro.feature.home

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.sledje.agentpro.app.App
import com.sledje.agentpro.core.data.StoreKeys
import com.sledje.agentpro.feature.auth.data.AuthState
import com.sledje.agentpro.testing.FakeAppDependencies
import com.sledje.agentpro.testing.FakeAuthRepository
import com.sledje.agentpro.testing.TEST_USER
import com.sledje.agentpro.testing.testProfile
import com.sledje.agentpro.testing.testSite
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/** An existing agent is taken through important news full screen before Home (docs/UX.md, What's new; DECISIONS #121). */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class WhatsNewWalkthroughUiTest {
    private fun existingAgent(seen: String?) = FakeAppDependencies(auth = FakeAuthRepository(AuthState.SignedIn(TEST_USER, "ramesh@example.com"))).apply {
        profiles.profile.value = testProfile()
        sites.site.value = testSite()
        runBlocking { store.put(StoreKeys.WHATS_NEW_SEEN, seen) }
    }

    private fun ComposeUiTest.awaitText(text: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun showMeOpensTheTemplateGridAndTheWalkthroughIsSeen() = runComposeUiTest {
        val deps = existingAgent(seen = "1")
        setContent { App(deps) }

        awaitText("5 new website designs")
        onNodeWithText("Your website has a fresh new look. See all five designs and pick the one you like.").assertExists()
        onNode(hasText("Show me") and hasClickAction()).performClick()

        awaitText("Choose a template")
        awaitText("Tap a design to see your website in it.")
        assertEquals(WhatsNew.VERSION.toString(), runBlocking { deps.store.get(StoreKeys.WHATS_NEW_SEEN) })
    }

    @Test
    fun aFreshAgentPagesThroughEverythingNewThenNotNowLeavesItForHome() = runComposeUiTest {
        val deps = existingAgent(seen = null)
        setContent { App(deps) }

        awaitText("5 new website designs")
        // Every item is pending for a fresh agent (each one's feature is on).
        val count = WhatsNewItem.entries.size
        onNodeWithText("1 of $count").assertExists()
        repeat(count - 1) { onNode(hasText("Next") and hasClickAction()).performClick() }
        awaitText("$count of $count")
        onNode(hasText("Not now") and hasClickAction()).performClick()

        awaitText("Nothing due today")
        onNode(hasText("5 new website designs")).assertDoesNotExist()
        assertEquals(WhatsNew.VERSION.toString(), runBlocking { deps.store.get(StoreKeys.WHATS_NEW_SEEN) })
    }
}
