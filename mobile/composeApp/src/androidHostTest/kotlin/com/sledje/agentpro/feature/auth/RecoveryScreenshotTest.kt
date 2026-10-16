package com.sledje.agentpro.feature.auth

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
import com.sledje.agentpro.core.designsystem.gallery.GalleryFrame
import com.sledje.agentpro.core.forms.FormState
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.feature.auth.ui.LinkExpiredScreen
import com.sledje.agentpro.feature.auth.ui.SetNewPasswordScreen
import com.sledje.agentpro.feature.auth.ui.SetNewPasswordUiState
import com.sledje.agentpro.feature.auth.ui.SetNewPasswordViewModel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi baselines of the password-reset screens (docs/TESTING.md; DECISIONS #46): Set new password
 * and "This link has expired", in light/dark × EN/HI.
 * Images: composeApp/src/androidHostTest/screenshots/<variant>/screens/<Screen>.png.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class RecoveryScreenshotTest {
    private val screens: List<Pair<String, @Composable () -> Unit>> = listOf(
        "SetNewPassword" to {
            val form = remember { FormState(SetNewPasswordViewModel.FIELDS, mapOf(SetNewPasswordViewModel.PASSWORD to "new-secret-1")) }
            SetNewPasswordScreen(form, SetNewPasswordUiState(), onSave = {}, onBack = {})
        },
        "LinkExpired" to { LinkExpiredScreen(onSendNewLink = {}, onBack = {}) },
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
            onRoot().captureRoboImage("src/androidHostTest/screenshots/$variant/screens/$name.png")
        }
    }

    @Test fun lightEnglish() = capture("light_en", dark = false, hindi = false)

    @Test fun darkEnglish() = capture("dark_en", dark = true, hindi = false)

    @Test fun lightHindi() = capture("light_hi", dark = false, hindi = true)

    @Test fun darkHindi() = capture("dark_hi", dark = true, hindi = true)
}
