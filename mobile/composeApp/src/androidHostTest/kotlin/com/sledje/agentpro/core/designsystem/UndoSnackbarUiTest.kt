package com.sledje.agentpro.core.designsystem

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.sledje.agentpro.core.designsystem.components.AppSnackbarHost
import com.sledje.agentpro.core.designsystem.components.PrimaryButton
import com.sledje.agentpro.core.designsystem.components.UNDO_SNACKBAR_MILLIS
import com.sledje.agentpro.core.designsystem.components.showUndo
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/** The 5-second Undo snackbar (docs/UX.md, Global UX rule 2; docs/TESTING.md). */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class UndoSnackbarUiTest {
    private fun undoTest(tapUndo: Boolean) = runComposeUiTest {
        var undone: Boolean? = null
        setContent {
            AgentProTheme(darkTheme = false) {
                val host = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                PrimaryButton("delete", onClick = { scope.launch { undone = host.showUndo("Deleted", "Undo") } })
                AppSnackbarHost(host)
            }
        }
        onNodeWithText("delete").performClick()
        onNodeWithText("Deleted").assertExists()
        if (tapUndo) {
            onNodeWithText("Undo").performClick()
        } else {
            mainClock.advanceTimeBy(UNDO_SNACKBAR_MILLIS + 1_000)
        }
        waitForIdle()
        onNodeWithText("Deleted").assertDoesNotExist()
        assertEquals(tapUndo, undone)
    }

    @Test
    fun undoRevertsTheAction() = undoTest(tapUndo = true)

    @Test
    fun snackbarDismissesItselfAfterFiveSeconds() = undoTest(tapUndo = false)
}
