package com.sledje.agentpro.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.github.takahirom.roborazzi.captureRoboImage
import com.sledje.agentpro.core.catalog.AppLocale
import com.sledje.agentpro.core.catalog.TemplateFamily
import com.sledje.agentpro.core.catalog.Templates
import com.sledje.agentpro.core.designsystem.gallery.GalleryFrame
import com.sledje.agentpro.core.forms.FormState
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.feature.auth.ui.AuthFormMode
import com.sledje.agentpro.feature.auth.ui.AuthFormScreen
import com.sledje.agentpro.feature.auth.ui.AuthFormUiState
import com.sledje.agentpro.feature.auth.ui.AuthFormViewModel
import com.sledje.agentpro.feature.auth.ui.WelcomeScreen
import com.sledje.agentpro.feature.onboarding.ui.OnboardingLinkScreen
import com.sledje.agentpro.feature.onboarding.ui.LaunchStep
import com.sledje.agentpro.feature.onboarding.ui.OnboardingProfileScreen
import com.sledje.agentpro.feature.onboarding.ui.SiteLaunchScreen
import com.sledje.agentpro.feature.onboarding.ui.OnboardingTemplateScreen
import com.sledje.agentpro.feature.onboarding.ui.OnboardingUiState
import com.sledje.agentpro.feature.onboarding.ui.OnboardingViewModel
import com.sledje.agentpro.feature.onboarding.ui.SlugStatus
import com.sledje.agentpro.feature.website.data.SiteLinks
import com.sledje.agentpro.testing.FakeAppDependencies
import com.sledje.agentpro.core.data.StoreKeys
import com.sledje.agentpro.feature.home.WhatsNew
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi baselines of the Phase 5 screens (docs/TESTING.md): Welcome, Sign in, the three onboarding
 * steps and the empty tab shell (Home in Main), in light/dark × EN/HI.
 * Images: composeApp/src/androidHostTest/screenshots/<variant>/screens/<Screen>.png.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class FoundationScreenshotTest {
    @Composable
    private fun LaunchScreenFor(step: LaunchStep) = SiteLaunchScreen(
        step, "Ramesh Kumar", "https://agentpro.site/a/ramesh-kumar", "agentpro.site/a/ramesh-kumar",
        onAddToHome = {}, onNext = {}, onOpen = {}, onShare = {}, onDone = {},
        // The WebView isn't screenshotted; a surface stands in for the render.
        preview = { Box(it.background(MaterialTheme.colorScheme.surfaceContainer)) },
    )

    private fun screens(hindi: Boolean): List<Pair<String, @Composable () -> Unit>> = listOf(
        "Welcome" to {
            WelcomeScreen(if (hindi) AppLocale.Hi else AppLocale.En, onSwitchLocale = {}, onCreateAccount = {}, onSignIn = {})
        },
        "SignIn" to {
            val form = remember { FormState(AuthFormViewModel.fieldsFor(AuthFormMode.SignIn), mapOf("email" to "ramesh@example.com", "password" to "secret123")) }
            AuthFormScreen(AuthFormMode.SignIn, form, AuthFormUiState(), {}, {}, {}, {})
        },
        "OnboardingProfile" to {
            val form = remember {
                FormState(
                    OnboardingViewModel.PROFILE_FIELDS,
                    mapOf(OnboardingViewModel.NAME to "Ramesh Kumar", OnboardingViewModel.MOBILE to "+919876543210", OnboardingViewModel.CITY to "Pune"),
                )
            }
            OnboardingProfileScreen(form, OnboardingUiState(), onContinue = {})
        },
        "LaunchAddToPhone" to { LaunchScreenFor(LaunchStep.AddToPhone) },
        "LaunchPreview" to { LaunchScreenFor(LaunchStep.Preview) },
        "LaunchShare" to { LaunchScreenFor(LaunchStep.Share) },
        "OnboardingProfileFromSignUp" to {
            val form = remember {
                FormState(
                    OnboardingViewModel.PROFILE_FIELDS,
                    mapOf(OnboardingViewModel.NAME to "Ramesh Kumar", OnboardingViewModel.MOBILE to "+919876543210"),
                )
            }
            OnboardingProfileScreen(form, OnboardingUiState(contactSummary = true), onContinue = {})
        },
        // Step 2 asks for the family first (the two cards), then pages through that family's templates.
        "OnboardingTemplateFamily" to {
            OnboardingTemplateScreen(Templates.all, Templates.all.first().key, "Ramesh Kumar", "LIC Agent · Pune", onUse = {}, onBack = {})
        },
        "OnboardingTemplate" to {
            OnboardingTemplateScreen(
                Templates.all, Templates.all.first().key, "Ramesh Kumar", "LIC Agent · Pune", onUse = {}, onBack = {},
                initialFamily = TemplateFamily.Personal,
            )
        },
        "OnboardingTemplateOffers" to {
            OnboardingTemplateScreen(
                Templates.all, "spotlight", "Ramesh Kumar", "LIC Agent · Pune", onUse = {}, onBack = {},
                initialFamily = TemplateFamily.Offers,
            )
        },
        "OnboardingLink" to {
            OnboardingLinkScreen(
                OnboardingUiState(slug = "ramesh-kumar", slugStatus = SlugStatus.Available),
                onSlugChange = {}, onPublishNow = {}, onLater = {}, onBack = {},
                linkDisplay = { SiteLinks.display(it, base = "https://agentpro.site") },
            )
        },
        // The "What's new" sheet is marked seen, so this stays the plain Home baseline (the sheet has its own in feature/home).
        "TabShell" to {
            MainShell(remember { FakeAppDependencies.ready().also { runBlocking { it.store.put(StoreKeys.WHATS_NEW_SEEN, WhatsNew.VERSION.toString()) } } })
        },
    )

    private fun capture(variant: String, dark: Boolean, hindi: Boolean) = runComposeUiTest {
        val screens = screens(hindi)
        var index by mutableStateOf(0)
        setContent {
            CompositionLocalProvider(LocalIsOnline provides true) {
                GalleryFrame(dark = dark, hindi = hindi, largeText = false) {
                    key(index) { screens[index].second() }
                }
            }
        }
        screens.forEachIndexed { i, (name, _) ->
            index = i
            waitForIdle()
            onRoot().captureRoboImage("src/androidHostTest/screenshots/$variant/screens/$name.png")
        }
    }

    @Test fun lightEnglish() = capture("light_en", dark = false, hindi = false)

    @Test fun darkEnglish() = capture("dark_en", dark = true, hindi = false)

    @Test fun lightHindi() = capture("light_hi", dark = false, hindi = true)

    @Test fun darkHindi() = capture("dark_hi", dark = true, hindi = true)
}
