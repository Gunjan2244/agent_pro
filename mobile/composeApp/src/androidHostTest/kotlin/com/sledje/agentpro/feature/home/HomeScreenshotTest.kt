package com.sledje.agentpro.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.github.takahirom.roborazzi.captureRoboImage
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.gallery.GalleryFrame
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.setup_title
import com.sledje.agentpro.resources.whats_new_title
import org.jetbrains.compose.resources.stringResource
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi baselines of Home's two sheets (docs/TESTING.md; DECISIONS #103): the setup checklist (done rows, the next
 * step marked, later steps locked), the one-time "What's new" sheet and its full-screen walkthrough, in light/dark x EN/HI.
 * Images: composeApp/src/androidHostTest/screenshots/<variant>/home/<Name>.png.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class HomeScreenshotTest {
    private fun sheet(title: @Composable () -> String, content: @Composable () -> Unit): @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(vertical = Spacing.m)) {
            Text(title(), style = AgentProTheme.text.title, modifier = Modifier.padding(horizontal = Spacing.m).padding(bottom = Spacing.s).semantics { heading() })
            content()
        }
    }

    private val screens: List<Pair<String, @Composable () -> Unit>> = listOf(
        "SetupChecklist" to sheet({ stringResource(Res.string.setup_title) }) {
            Column {
                SetupChecklistContent(HomeUiState(done = setOf(SetupItem.Profile, SetupItem.Share)), onItem = {})
            }
        },
        "SetupChecklistStart" to sheet({ stringResource(Res.string.setup_title) }) {
            Column { SetupChecklistContent(HomeUiState(), onItem = {}) }
        },
        "WhatsNew" to sheet({ stringResource(Res.string.whats_new_title) }) {
            Column { WhatsNewContent(listOf(WhatsNewItem.Companies, WhatsNewItem.CustomerLogin, WhatsNewItem.ForMyCustomers), onItem = {}, onGotIt = {}) }
        },
        "WhatsNewCompaniesOnly" to sheet({ stringResource(Res.string.whats_new_title) }) {
            Column { WhatsNewContent(listOf(WhatsNewItem.Companies), onItem = {}, onGotIt = {}) }
        },
        // The full-screen walkthrough (DECISIONS #121): one important page, and the first of several with the counter.
        "WhatsNewWalkthrough" to { WhatsNewScreen(listOf(WhatsNewItem.NewTemplates), onShowMe = {}, onDone = {}) },
        "WhatsNewWalkthroughMultiPage" to { WhatsNewScreen(listOf(WhatsNewItem.MultiPageWebsite), onShowMe = {}, onDone = {}) },
        "WhatsNewWalkthroughPages" to { WhatsNewScreen(listOf(WhatsNewItem.NewTemplates, WhatsNewItem.Companies), onShowMe = {}, onDone = {}) },
    )

    private fun capture(variant: String, dark: Boolean, hindi: Boolean) = runComposeUiTest {
        var index by mutableStateOf(0)
        setContent {
            GalleryFrame(dark = dark, hindi = hindi, largeText = false) {
                key(index) { screens[index].second() }
            }
        }
        screens.forEachIndexed { i, (name, _) ->
            index = i
            waitForIdle()
            onRoot().captureRoboImage("src/androidHostTest/screenshots/$variant/home/$name.png")
        }
    }

    @Test fun lightEnglish() = capture("light_en", dark = false, hindi = false)

    @Test fun darkEnglish() = capture("dark_en", dark = true, hindi = false)

    @Test fun lightHindi() = capture("light_hi", dark = false, hindi = true)

    @Test fun darkHindi() = capture("dark_hi", dark = true, hindi = true)
}
