package com.sledje.agentpro.feature.customers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.designsystem.components.BottomActionBar
import com.sledje.agentpro.core.designsystem.components.DiscardChangesDialog
import com.sledje.agentpro.core.designsystem.components.HairlineDivider
import com.sledje.agentpro.core.forms.FormRenderer
import com.sledje.agentpro.core.forms.FormState
import com.sledje.agentpro.resources.crm_save
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.message
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.SecondaryButton
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.needs_internet
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/*
 * Small pieces shared by the enquiry, customer, policy and dues screens (Phases 6–7). They only arrange
 * design-system components; colours, sizes and copy come from the theme and resources.
 */

/** One outlined action of the action row (Call · SMS · Email, or Call · SMS · Edit). */
data class ContactAction(val label: String, val icon: DrawableResource, val enabled: Boolean = true, val onClick: () -> Unit)

/** The outlined action row under a detail header (docs/UX.md, Enquiry detail and Customer detail). */
@Composable
fun ContactActionRow(actions: List<ContactAction>, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        actions.forEach { a ->
            SecondaryButton(a.label, a.onClick, Modifier.weight(1f), enabled = a.enabled, icon = a.icon)
        }
    }
}

/** A plain label and value pair ("Details: plain text with no card"). Hidden when [value] is blank. */
@Composable
fun LabelValue(label: String, value: String?, modifier: Modifier = Modifier) {
    if (value.isNullOrBlank()) return
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s)) {
        Text(label, style = AgentProTheme.text.bodyMuted)
        Text(value, style = AgentProTheme.text.body)
    }
}

/** A detail screen's header: the name (title style), then one supporting line and an optional trailing element. */
@Composable
fun DetailHeader(title: String, supporting: String?, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(title, style = AgentProTheme.text.title, modifier = Modifier.semantics { heading() })
        if (trailing != null || supporting != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                trailing?.invoke()
                if (supporting != null) {
                    Text(supporting, style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * The frame of every CRM form (docs/UX.md, Global rule 3): back + title, the [FormRenderer] fields
 * (6 or fewer visible, the rest under More details), a sticky Save bar ("Needs internet" offline), an
 * optional line just above Save (the policy form's live summary), and "Discard changes?" when leaving
 * with unsaved changes.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CrmFormScreen(
    title: String,
    form: FormState,
    saving: Boolean,
    onSave: () -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
    visibleKeys: Set<String>? = null,
    saveLabel: String = stringResource(Res.string.crm_save),
    aboveSave: (@Composable () -> Unit)? = null,
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    val leave = { if (form.isDirty && !saving) confirmDiscard = true else onBack() }
    BackHandler(enabled = form.isDirty && !saving) { confirmDiscard = true }
    val online = LocalIsOnline.current
    AppScaffold(
        topBar = { AppTopBar(title = title, onBack = leave) },
        modifier = modifier,
        offline = !online,
        snackbarHostState = snackbar,
        bottomBar = {
            Column {
                if (aboveSave != null) {
                    HairlineDivider()
                    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s)) { aboveSave() }
                }
                BottomActionBar(saveLabel, onSave, enabled = online && !saving, helperText = offlineHelper())
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.m),
        ) {
            FormRenderer(form, visibleKeys = visibleKeys, enabled = !saving, onDone = { if (online) onSave() })
        }
    }
    if (confirmDiscard) {
        DiscardChangesDialog(onDiscard = { confirmDiscard = false; onBack() }, onKeepEditing = { confirmDiscard = false })
    }
}

/** "Needs internet" under a disabled Save while offline (docs/UX.md, Global rule 7). */
@Composable
fun offlineHelper(): String? = if (LocalIsOnline.current) null else stringResource(Res.string.needs_internet)

/** Shows an [AppError]'s one-line message. */
suspend fun SnackbarHostState.showError(error: AppError) {
    showSnackbar(getString(error.message))
}
