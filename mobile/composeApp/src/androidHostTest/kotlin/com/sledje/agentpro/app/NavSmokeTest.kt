package com.sledje.agentpro.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.forms.fieldTestTag
import com.sledje.agentpro.feature.auth.data.AuthState
import com.sledje.agentpro.feature.auth.data.SignUpOutcome
import com.sledje.agentpro.feature.auth.ui.AuthFormViewModel
import com.sledje.agentpro.feature.offers.ui.CompaniesViewModel
import com.sledje.agentpro.feature.onboarding.ui.OnboardingViewModel
import com.sledje.agentpro.feature.onboarding.ui.SLUG_FIELD
import com.sledje.agentpro.feature.website.ui.templateTileTag
import com.sledje.agentpro.testing.FakeAppDependencies
import com.sledje.agentpro.testing.whatsNewSeen
import com.sledje.agentpro.testing.FakeAuthRepository
import com.sledje.agentpro.testing.FakeCompanyRepository
import com.sledje.agentpro.testing.FakeDataSync
import com.sledje.agentpro.testing.FakeGoogleSignIn
import com.sledje.agentpro.testing.FakeProfileRepository
import com.sledje.agentpro.testing.TEST_USER
import com.sledje.agentpro.testing.testMembership
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
        val deps = FakeAppDependencies().whatsNewSeen()
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

        // Step 1: the name (no account name here) and the mobile; nothing else.
        awaitText("Your mobile number")
        onNodeWithTag(fieldTestTag(OnboardingViewModel.NAME)).performTextInput("Sunita Rao")
        onNodeWithTag(fieldTestTag(OnboardingViewModel.MOBILE)).performTextInput("9876543210")
        onNodeWithTag(fieldTestTag(OnboardingViewModel.CITY)).assertDoesNotExist()
        onNode(hasText("Add company")).assertDoesNotExist()
        button("Continue").performClick()

        // Step 2: the grid of the five designs (from the generated Templates); step 3: the tapped one, before any company.
        awaitText("Choose your template")
        onNodeWithTag(templateTileTag("guide")).performClick()
        awaitText("Make website from this")
        button("See other templates").performClick()
        awaitText("Choose your template")
        onNodeWithTag(templateTileTag("classic")).performClick()
        button("Make website from this").performClick()

        // Step 4: your work. Continue waits for one company (DECISIONS #89). Add Alpha Insurance with its code and a type.
        awaitText("Your work")
        onNode(hasText("Continue") and hasClickAction()).assertIsNotEnabled()
        awaitText("Add at least one company to continue")
        button("Add company").performScrollTo().performClick()
        awaitText("Choose your company")
        onNode(hasText("Alpha Insurance") and hasClickAction()).performClick()
        onNodeWithTag(fieldTestTag(CompaniesViewModel.AGENCY_CODE)).performTextInput("AG123")
        onNode(hasText("Life") and hasClickAction()).performClick()
        button("Save").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasContentDescription("Waiting for approval")).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasText("Add at least one company to continue")).assertDoesNotExist()
        assertEquals("AG123", deps.companies.requests.single().agencyCode)
        onNode(hasText("More details")).assertDoesNotExist()
        onNode(hasText("Branch")).assertDoesNotExist()
        button("Continue").performClick()

        // Step 5: the photo, skipped; step 6: the finished website, with the pending company explained.
        awaitText("Your photo")
        button("Skip").performClick()
        awaitText("This is your website with your photo and companies. Scroll to look around; you can change anything later.")
        awaitText("Your companies show on your live website once we confirm you're their agent.")
        button("Continue").performClick()

        // Step 7: the link is suggested from the name and checked after the pause.
        awaitText("Your link")
        onNodeWithTag(fieldTestTag(SLUG_FIELD)).assertExists()
        ShadowLooper.idleMainLooper(OnboardingViewModel.SLUG_DEBOUNCE_MILLIS + 50, TimeUnit.MILLISECONDS)
        awaitText("Available")
        assertTrue("sunita-rao" in deps.sites.availabilityChecks)
        button("Publish now").performClick()

        // Live: Add to phone → Share, then Done.
        awaitText("Add it to your phone")
        button("Not now").performClick()
        awaitText("Share it with customers")
        button("Done").performClick()

        // Done: Main, with the trial snackbar.
        awaitText("Namaste, Sunita")
        awaitText("Your 14-day free trial has started")
        assertEquals("sunita-rao", deps.sites.created.single().slug)
        assertEquals(1, deps.sites.publishCalls)
        assertEquals("+919876543210", deps.profiles.profile.value?.phone)
    }

    @Test
    fun continueWithGoogleAsksOnlyForTheMobileThenTheTemplates() = runComposeUiTest {
        val auth = FakeAuthRepository()
        val profiles = FakeProfileRepository(null)
        // The signup trigger creates the profile from Google's name and email; there is no mobile yet.
        val sync = FakeDataSync().apply {
            onRefresh = { profiles.profile.value = testProfile(fullName = "Sunita Rao", phone = null, designation = null, city = null) }
        }
        val google = FakeGoogleSignIn()
        setContent { App(FakeAppDependencies(auth = auth, profiles = profiles, dataSync = sync, googleSignIn = google)) }

        awaitText("Continue with Google")
        onNode(hasText("Continue with Google") and hasClickAction()).assertIsNotEnabled()
        onNode(isToggleable()).performClick()
        button("Continue with Google").performClick()

        awaitText("Your mobile number")
        awaitText("Sunita Rao")
        onNodeWithTag(fieldTestTag(OnboardingViewModel.NAME)).assertDoesNotExist()
        onNodeWithTag(fieldTestTag(OnboardingViewModel.MOBILE)).performTextInput("9876543210")
        button("Continue").performClick()
        awaitText("Choose your template")
        assertEquals(1, google.opened)
        assertEquals(listOf("google:id-token:raw-nonce"), auth.calls)
        assertEquals("+919876543210", profiles.updates.single().phone)
    }

    @Test
    fun createAccountSignsInAndStep1DoesNotAskForTheNameAgain() = runComposeUiTest {
        val auth = FakeAuthRepository().apply { signUpResult = Result.Ok(SignUpOutcome.SignedIn) }
        val profiles = FakeProfileRepository(null)
        // The signup trigger creates the profile from the sign-up metadata; the first sync brings it.
        val sync = FakeDataSync().apply {
            onRefresh = {
                val account = auth.signUpAccounts.single()
                profiles.profile.value = testProfile(fullName = account.fullName, phone = account.phone, designation = null, city = null)
            }
        }
        val companies = FakeCompanyRepository(listOf(testMembership()))
        setContent { App(FakeAppDependencies(auth = auth, profiles = profiles, dataSync = sync, companies = companies)) }

        awaitText("Create account")
        button("Create account").performClick()
        onNodeWithTag(fieldTestTag(AuthFormViewModel.NAME)).performTextInput("Sunita Rao")
        onNodeWithTag(fieldTestTag(AuthFormViewModel.MOBILE)).performTextInput("9876543210")
        onNodeWithTag(fieldTestTag(AuthFormViewModel.EMAIL)).performTextInput("sunita@example.com")
        onNodeWithTag(fieldTestTag(AuthFormViewModel.PASSWORD)).performTextInput("long enough")
        onNodeWithTag(fieldTestTag(AuthFormViewModel.CONFIRM_PASSWORD)).performTextInput("long enough")
        onNode(isToggleable()).performScrollTo().performClick()
        button("Create account").performClick()

        // Signed in straight away: step 1 shows the name with Edit, not its field; the mobile is prefilled.
        awaitText("Your mobile number")
        awaitText("Sunita Rao")
        onNodeWithTag(fieldTestTag(OnboardingViewModel.NAME)).assertDoesNotExist()
        button("Edit").performClick()
        onNodeWithTag(fieldTestTag(OnboardingViewModel.NAME)).assertExists()
        button("Continue").performClick()
        awaitText("Choose your template")
        assertEquals("+919876543210", profiles.updates.single().phone)
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
        ).whatsNewSeen()
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
