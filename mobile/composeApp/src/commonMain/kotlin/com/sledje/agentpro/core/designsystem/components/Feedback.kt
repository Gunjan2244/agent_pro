package com.sledje.agentpro.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.AppShapes
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.action_cancel
import com.sledje.agentpro.resources.action_discard
import com.sledje.agentpro.resources.action_keep_editing
import com.sledje.agentpro.resources.discard_message
import com.sledje.agentpro.resources.discard_title
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource

/** Reversible actions show Undo for 5 seconds (docs/UX.md, Global UX rule 2). */
const val UNDO_SNACKBAR_MILLIS: Long = 5_000L

/** The snackbar host: one-line message, optional action (Undo) in the inverse primary colour. */
@Composable
fun AppSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = AppShapes.sm,
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = MaterialTheme.colorScheme.inversePrimary,
        )
    }
}

/** A stateless snackbar with the host's styling (used by the component gallery). */
@Composable
fun AppSnackbar(message: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Snackbar(
        modifier = modifier,
        action = actionLabel?.let {
            {
                TextButton(onClick = onAction, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary)) {
                    Text(it, style = AgentProTheme.text.label)
                }
            }
        },
        shape = AppShapes.sm,
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        actionContentColor = MaterialTheme.colorScheme.inversePrimary,
    ) { Text(message, style = AgentProTheme.text.body) }
}

/**
 * Shows [message] with an Undo action for [UNDO_SNACKBAR_MILLIS]. Returns true when Undo was tapped.
 * Callers apply the change first, then revert it if this returns true.
 */
suspend fun SnackbarHostState.showUndo(message: String, undoLabel: String): Boolean = coroutineScope {
    val timeout = launch {
        delay(UNDO_SNACKBAR_MILLIS)
        currentSnackbarData?.takeIf { it.visuals.message == message }?.dismiss()
    }
    val result = showSnackbar(message = message, actionLabel = undoLabel, duration = SnackbarDuration.Indefinite)
    timeout.cancel()
    result == SnackbarResult.ActionPerformed
}

/**
 * Confirms an irreversible action only (deleting a customer, deleting the account). Everything else
 * uses Undo instead.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
    dismissLabel: String = stringResource(Res.string.action_cancel),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = AgentProTheme.text.title) },
        text = { Text(message, style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = {
            if (destructive) DestructiveButton(confirmLabel, onConfirm) else TertiaryButton(confirmLabel, onConfirm)
        },
        dismissButton = { TertiaryButton(dismissLabel, onDismiss) },
        shape = AppShapes.lg,
        containerColor = AgentProTheme.colors.elevatedSurface,
    )
}

/** "Discard changes?" when leaving a form with unsaved changes (docs/UX.md, rule 3). */
@Composable
fun DiscardChangesDialog(onDiscard: () -> Unit, onKeepEditing: () -> Unit) {
    ConfirmDialog(
        title = stringResource(Res.string.discard_title),
        message = stringResource(Res.string.discard_message),
        confirmLabel = stringResource(Res.string.action_discard),
        onConfirm = onDiscard,
        onDismiss = onKeepEditing,
        dismissLabel = stringResource(Res.string.action_keep_editing),
    )
}

/**
 * The sticky bottom bar holding the screen's primary action (and optionally one text-button alternative). While [helperText] is set (e.g.
 * "Needs internet" when offline) it shows under the button as one muted line.
 */
@Composable
fun BottomActionBar(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    helperText: String? = null,
    /** An optional text-button alternative under the primary, e.g. "Later" next to "Publish now". */
    secondaryText: String? = null,
    onSecondary: () -> Unit = {},
    secondaryEnabled: Boolean = true,
    /** An icon after the label (PrimaryButton's trailingIcon), optionally nudging back and forth. */
    trailingIcon: DrawableResource? = null,
    nudgeTrailingIcon: Boolean = false,
) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding()) {
            HairlineDivider()
            Column(Modifier.padding(Spacing.m)) {
                PrimaryButton(text, onClick, Modifier.fillMaxWidth(), enabled = enabled, trailingIcon = trailingIcon, nudgeTrailingIcon = nudgeTrailingIcon)
                if (secondaryText != null) {
                    TertiaryButton(secondaryText, onSecondary, Modifier.fillMaxWidth(), enabled = secondaryEnabled)
                }
                if (helperText != null) {
                    Text(
                        helperText,
                        style = AgentProTheme.text.bodyMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
                    )
                }
            }
        }
    }
}
