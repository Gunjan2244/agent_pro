package com.sledje.agentpro.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.forms.fieldTestTag
import com.sledje.agentpro.feature.auth.data.AuthState
import com.sledje.agentpro.feature.onboarding.ui.OnboardingViewModel
import com.sledje.agentpro.feature.onboarding.ui.SLUG_FIELD
import com.sledje.agentpro.testing.FakeAppDependencies
import com.sledje.agentpro.testing.FakeAuthRepository
import com.sledje.agentpro.testing.FakeDataSync
import com.sledje.agentpro.testing.FakeProfileRepository
import com.sledje.agentpro.testing.TEST_USER
import com.sledje.agentpro.testing.testProfile
import com.sledje.agentpro.testing.testSite
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The auth gate → onboarding → tabs, end to end on fakes (docs/TESTING.md, Compose UI tests). */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class NavSmokeTest {
    private fun SemanticsNodeInteractionsProvider.button(text: String) = onNode(hasText(text) and hasClickAction())

    private fun androidx.compose.ui.test.ComposeUiTest.awaitText(text: String) =
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun signInOpensTheTabsAndSignOutReturnsToWelcome() = runComposeUiTest {
        val deps = FakeAppDependencies()
        setContent { App(deps) }

        // Signed out → Welcome.
        awaitText("Create account")
        button("Sign in").performClick()
        onNodeWithTag(fieldTestTag("email")).performTextInput("ramesh@example.com")
        onNodeWithTag(fieldTestTag("password")).performTextInput("secret123")
        // The account already has a profile and a site, as the first sync would bring them.
        deps.profiles.profile.value = testProfile()
        deps.sites.site.value = testSite()
        button("Sign in").performClick()

        // Gate → Main with 4 tabs from the FeatureRegistry.
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Namaste, Ramesh")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(MAIN_NAV_BAR_TAG).assertExists()
        assertEquals(listOf("signIn:ramesh@example.com"), deps.auth.calls)
        assertEquals(listOf(TEST_USER), deps.dataSync.signedIn)
        assertTrue(deps.dataSync.refreshes >= 1, "initial sync after sign-in")

        button("Enquiries").performClick()
        awaitText("Share your website to get enquiries")

        // Customers tab: the [ Customers | Dues ] segment shell.
        button("Customers").performClick()
        awaitText("Add your first customer")
        button("Dues").performClick()
        awaitText("All caught up")

        button("Website").performClick()
        // A substring: the address starts with SITE_BASE_URL's host when local.properties sets one.
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("/a/ramesh-kumar", substring = true)).fetchSemanticsNodes().isNotEmpty() }

        // Home avatar → Settings → Sign out → Welcome.
        button("Home").performClick()
        onNodeWithText("Settings").assertDoesNotExist()
        onNode(hasClickAction() and androidx.compose.ui.test.hasContentDescription("Settings")).performClick()
        button("Sign out").performClick()
        awaitText("Create account")
        assertEquals(AuthState.SignedOut, deps.auth.state.value)
    }

    @Test
    fun aNewAccountGoesThroughOnboardingThenMain() = runComposeUiTest {
        val deps = FakeAppDependencies(
            auth = FakeAuthRepository(AuthState.SignedIn(TEST_USER, "new@example.com")),
            profiles = FakeProfileRepository(testProfile(fullName = null, phone = null, designation = null, city = null)),
            dataSync = FakeDataSync(),
        )
        setContent { App(deps) }

        // Step 1: profile.
        awaitText("Your profile")
        onNodeWithTag(fieldTestTag(OnboardingViewModel.NAME)).performTextInput("Sunita Rao")
        onNodeWithTag(fieldTestTag(OnboardingViewModel.MOBILE)).performTextInput("9876543210")
        button("Continue").performClick()

        // Step 2: template (from the generated Templates).
        awaitText("Pick a design")
        button("Use this template").performClick()

        // Step 3: the link is suggested from the name and checked after the pause.
        awaitText("Your link")
        onNodeWithTag(fieldTestTag(SLUG_FIELD)).assertExists()
        ShadowLooper.idleMainLooper(OnboardingViewModel.SLUG_DEBOUNCE_MILLIS + 50, TimeUnit.MILLISECONDS)
        awaitText("Available")
        assertTrue("sunita-rao" in deps.sites.availabilityChecks)
        button("Publish now").performClick()

        // Done: Main, with the trial snackbar.
        awaitText("Namaste, Sunita")
        awaitText("Your 14-day free trial has started")
        assertEquals("sunita-rao", deps.sites.created.single().slug)
        assertEquals(1, deps.sites.publishCalls)
        assertEquals("+919876543210", deps.profiles.profile.value?.phone)
    }

    @Test
    fun withoutKeysTheAppSaysItIsNotConfigured() = runComposeUiTest {
        setContent { App(FakeAppDependencies(isConfigured = false)) }
        awaitText("This app isn't connected to a server yet")
    }

    @Test
    fun anEmptyCacheThatCannotSyncShowsRetry() = runComposeUiTest {
        val deps = FakeAppDependencies(
            auth = FakeAuthRepository(AuthState.SignedIn(TEST_USER, null)),
            dataSync = FakeDataSync(com.sledje.agentpro.core.data.Result.Err(AppError.Offline)),
        )
        setContent { App(deps) }
        awaitText("Couldn't load your data")
        deps.dataSync.result = com.sledje.agentpro.core.data.Result.Ok(Unit)
        deps.dataSync.onRefresh = {
            deps.profiles.profile.value = testProfile()
            deps.sites.site.value = testSite()
        }
        button("Retry").performClick()
        awaitText("Namaste, Ramesh")
    }

    @Test
    fun offlineShowsTheBannerAndDisablesSignIn() = runComposeUiTest {
        val deps = FakeAppDependencies()
        deps.network.isOnline.value = false
        setContent { App(deps) }
        awaitText("Offline — showing saved data")
        button("Sign in").performClick()
        onNodeWithTag(fieldTestTag("email")).performTextInput("ramesh@example.com")
        onNodeWithTag(fieldTestTag("password")).performTextInput("secret123")
        awaitText("Needs internet")
        button("Sign in").assertIsNotEnabled()
    }
}
