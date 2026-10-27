package com.sledje.agentpro.feature.privacy

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.forms.FormState
import com.sledje.agentpro.core.forms.fieldTestTag
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.feature.auth.ui.AuthFormMode
import com.sledje.agentpro.feature.auth.ui.AuthFormScreen
import com.sledje.agentpro.feature.auth.ui.AuthFormUiState
import com.sledje.agentpro.feature.auth.ui.AuthFormViewModel
import com.sledje.agentpro.core.catalog.PrivacyNotice
import com.sledje.agentpro.feature.privacy.ui.CustomerDutySheetContent
import com.sledje.agentpro.feature.privacy.ui.NoticeScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/** P1-4 UI checks (docs/TESTING.md): Create account stays disabled until the consent box is ticked. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class PrivacyUiTest {
    @Test
    fun createAccountIsDisabledUntilTheConsentBoxIsTicked() = runComposeUiTest {
        val form = FormState(AuthFormViewModel.fieldsFor(AuthFormMode.SignUp))
        val links = mutableListOf<String>()
        setContent {
            AgentProTheme(darkTheme = false) {
                CompositionLocalProvider(LocalIsOnline provides true) {
                    AuthFormScreen(AuthFormMode.SignUp, form, AuthFormUiState(), {}, {}, {}, {}, onLink = { links += it })
                }
            }
        }
        val create = hasText("Create account") and hasClickAction()
        onNode(create).assertIsNotEnabled()

        onNodeWithTag(fieldTestTag(AuthFormViewModel.NAME)).performTextInput("Ramesh Kumar")
        onNodeWithTag(fieldTestTag(AuthFormViewModel.MOBILE)).performTextInput("9876543210")
        onNodeWithTag(fieldTestTag(AuthFormViewModel.EMAIL)).performTextInput("new@example.com")
        onNodeWithTag(fieldTestTag(AuthFormViewModel.PASSWORD)).performTextInput("long enough")
        onNodeWithTag(fieldTestTag(AuthFormViewModel.CONFIRM_PASSWORD)).performTextInput("long enough")
        onNode(create).assertIsNotEnabled() // every field valid, but not consented

        onNode(isToggleable()).performScrollTo().performClick()
        assertEquals(true, form.value(AuthFormViewModel.CONSENT))
        onNode(create).assertIsEnabled()

        onNode(isToggleable()).performClick() // unticking disables it again
        onNode(create).assertIsNotEnabled()
    }

    @Test
    fun theConsentLineIsTheGeneratedTextAndIsNeverPreTicked() = runComposeUiTest {
        val form = FormState(AuthFormViewModel.fieldsFor(AuthFormMode.SignUp))
        setContent {
            AgentProTheme(darkTheme = false) {
                CompositionLocalProvider(LocalIsOnline provides true) {
                    AuthFormScreen(AuthFormMode.SignUp, form, AuthFormUiState(), {}, {}, {}, {})
                }
            }
        }
        onNodeWithText("I'm 18 or older, and I agree to the Terms and the Privacy notice.").assertExists()
        assertEquals(false, form.value(AuthFormViewModel.CONSENT))
    }

    @Test
    fun theNoticeScreenListsTheItemisedData() = runComposeUiTest {
        setContent {
            AgentProTheme(darkTheme = false) {
                CompositionLocalProvider(LocalIsOnline provides true) { NoticeScreen(PrivacyNotice.AgentSignup, onBack = {}) }
            }
        }
        onNodeWithText("Email, and either a password (stored hashed by Supabase Auth) or, if you continue with Google, your Google account's name and email").assertExists()
    }

    @Test
    fun theAttestationSheetBlocksAcceptingOffline() = runComposeUiTest {
        var accepted = 0
        setContent {
            AgentProTheme(darkTheme = false) {
                CustomerDutySheetContent(recording = false, canShare = true, onAccept = { accepted++ }, onShare = {}, online = false)
            }
        }
        onNode(hasText("I understand") and hasClickAction()).assertIsNotEnabled()
        onNodeWithText("Needs internet").assertExists()
        onNodeWithText("Share privacy note").assertExists()
        assertEquals(0, accepted)
    }
}
