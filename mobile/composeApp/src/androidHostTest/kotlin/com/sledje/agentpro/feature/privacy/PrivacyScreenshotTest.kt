package com.sledje.agentpro.feature.privacy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.github.takahirom.roborazzi.captureRoboImage
import com.sledje.agentpro.core.catalog.PrivacyNotice
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.gallery.GalleryFrame
import com.sledje.agentpro.core.forms.FormState
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.feature.auth.ui.AuthFormMode
import com.sledje.agentpro.feature.auth.ui.AuthFormScreen
import com.sledje.agentpro.feature.auth.ui.AuthFormUiState
import com.sledje.agentpro.feature.auth.ui.AuthFormViewModel
import com.sledje.agentpro.feature.customers.ui.CrmFormScreen
import com.sledje.agentpro.feature.customers.ui.CustomerFormViewModel
import com.sledje.agentpro.feature.customers.ui.GuardianConsentToggle
import com.sledje.agentpro.feature.privacy.ui.CustomerDutySheetContent
import com.sledje.agentpro.feature.privacy.ui.NoticeScreen
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.cat_privacy_agent_customer_duty_title
import com.sledje.agentpro.resources.customer_form_add
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi baselines of the privacy screens (docs/TESTING.md; DECISIONS #46): Create account with the consent
 * box (unticked and ticked), the Notice screen, the customer-data attestation sheet and the guardian toggle on
 * the customer form, in light/dark x EN/HI. Images: composeApp/src/androidHostTest/screenshots/<variant>/privacy/<Name>.png.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class PrivacyScreenshotTest {
    private fun signUp(consented: Boolean): @Composable () -> Unit = {
        val form = remember {
            FormState(
                AuthFormViewModel.fieldsFor(AuthFormMode.SignUp),
                mapOf("email" to "ramesh@example.com", "password" to "secret123", AuthFormViewModel.CONSENT to consented),
            )
        }
        AuthFormScreen(AuthFormMode.SignUp, form, AuthFormUiState(), {}, {}, {}, {})
    }

    private val screens: List<Pair<String, @Composable () -> Unit>> = listOf(
        "SignUpConsentUnticked" to signUp(false),
        "SignUpConsentTicked" to signUp(true),
        "Notice" to { NoticeScreen(PrivacyNotice.AgentSignup, onBack = {}) },
        "AttestationSheet" to {
            Column(Modifier.fillMaxWidth().padding(vertical = Spacing.m)) {
                Text(
                    stringResource(Res.string.cat_privacy_agent_customer_duty_title),
                    style = AgentProTheme.text.title,
                    modifier = Modifier.padding(horizontal = Spacing.m).padding(bottom = Spacing.s).semantics { heading() },
                )
                CustomerDutySheetContent(recording = false, canShare = true, onAccept = {}, onShare = {}, online = true)
            }
        },
        "GuardianToggle" to {
            val form = remember {
                FormState(
                    CustomerFormViewModel.FIELDS,
                    mapOf(
                        CustomerFormViewModel.NAME to "Aarav Sharma",
                        CustomerFormViewModel.PHONE to "+919876543210",
                        CustomerFormViewModel.DOB to LocalDate(2015, 3, 1),
                    ),
                )
            }
            CrmFormScreen(
                title = stringResource(Res.string.customer_form_add),
                form = form,
                saving = false,
                onSave = {},
                onBack = {},
                snackbar = remember { SnackbarHostState() },
                visibleKeys = CustomerFormViewModel.VISIBLE,
                aboveSave = { GuardianConsentToggle(agreed = true, missing = false, onChange = {}) },
            )
        },
    )

    private fun capture(variant: String, dark: Boolean, hindi: Boolean) = runComposeUiTest {
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
            onRoot().captureRoboImage("src/androidHostTest/screenshots/$variant/privacy/$name.png")
        }
    }

    @Test fun lightEnglish() = capture("light_en", dark = false, hindi = false)

    @Test fun darkEnglish() = capture("dark_en", dark = true, hindi = false)

    @Test fun lightHindi() = capture("light_hi", dark = false, hindi = true)

    @Test fun darkHindi() = capture("dark_hi", dark = true, hindi = true)
}
