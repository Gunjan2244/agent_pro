package com.sledje.agentpro.feature.website.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.AppShapes
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTextField
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.designsystem.components.ListSkeleton
import com.sledje.agentpro.core.designsystem.components.SectionHeader
import com.sledje.agentpro.core.designsystem.components.TertiaryButton
import com.sledje.agentpro.core.forms.fieldTestTag
import com.sledje.agentpro.core.forms.message
import com.sledje.agentpro.feature.onboarding.ui.SlugStatus
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.action_cancel
import com.sledje.agentpro.resources.needs_internet
import com.sledje.agentpro.resources.onboarding_link_available
import com.sledje.agentpro.resources.onboarding_link_checking
import com.sledje.agentpro.resources.onboarding_link_label
import com.sledje.agentpro.resources.onboarding_link_preview
import com.sledje.agentpro.resources.onboarding_link_taken
import com.sledje.agentpro.resources.website_change_link
import com.sledje.agentpro.resources.website_change_link_warning
import com.sledje.agentpro.resources.website_free_link
import com.sledje.agentpro.resources.website_hub_link
import org.jetbrains.compose.resources.stringResource

/** The slug field of the Change link dialog. */
const val CHANGE_LINK_FIELD = "change_slug"

/**
 * Link & domain (docs/UX.md), free-link section: the address with **Change link** (text button), then
 * the Share / QR content (QR code, Share, Copy link). The custom-domain section comes with Phase 10
 * (`customDomains`; DECISIONS #21).
 */
@Composable
fun LinkScreen(
    state: LinkUiState,
    url: (String) -> String,
    display: (String) -> String,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onChange: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
) {
    val online = isOnline()
    AppScaffold(
        topBar = { AppTopBar(title = stringResource(Res.string.website_hub_link), onBack = onBack) },
        offline = !online,
        snackbarHostState = snackbar,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val site = state.site
            if (site == null) {
                ListSkeleton(rows = 2)
                return@Box
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Spacing.l)) {
                SectionHeader(stringResource(Res.string.website_free_link))
                Column(Modifier.padding(horizontal = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(display(site.slug), style = AgentProTheme.text.strong, modifier = Modifier.padding(top = Spacing.s))
                    TertiaryButton(stringResource(Res.string.website_change_link), onChange, enabled = online)
                    if (!online) Text(stringResource(Res.string.needs_internet), style = AgentProTheme.text.bodyMuted)
                }
                ShareContent(url(site.slug), display(site.slug), onShare, onCopy, Modifier.padding(top = Spacing.l))
            }
        }
    }
}

/**
 * Change link (docs/UX.md): validates and checks availability live, and warns that old links and QR
 * codes stop working.
 */
@Composable
fun ChangeLinkDialog(
    change: ChangeLinkState,
    currentSlug: String?,
    display: (String) -> String,
    onInput: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val online = isOnline()
    val (helper, error) = when (val s = change.status) {
        SlugStatus.Empty -> null to null
        is SlugStatus.Invalid -> null to s.error.message()
        SlugStatus.Checking -> stringResource(Res.string.onboarding_link_checking) to null
        SlugStatus.Available -> (if (change.slug != currentSlug) stringResource(Res.string.onboarding_link_available) else null) to null
        SlugStatus.Taken -> null to stringResource(Res.string.onboarding_link_taken)
        is SlugStatus.Unchecked -> null to null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.website_change_link), style = AgentProTheme.text.title) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(stringResource(Res.string.website_change_link_warning), style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AppTextField(
                    value = change.slug,
                    onValueChange = onInput,
                    label = stringResource(Res.string.onboarding_link_label),
                    error = error,
                    helper = helper,
                    enabled = !change.saving,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    inputModifier = Modifier.testTag(fieldTestTag(CHANGE_LINK_FIELD)),
                )
                if (change.slug.isNotEmpty()) {
                    Text(stringResource(Res.string.onboarding_link_preview, display(change.slug)), style = AgentProTheme.text.bodyMuted)
                }
                change.error?.let { ErrorText(websiteErrorText(it)) }
            }
        },
        confirmButton = {
            TertiaryButton(stringResource(Res.string.website_change_link), onConfirm, enabled = online && change.canSave(currentSlug))
        },
        dismissButton = { TertiaryButton(stringResource(Res.string.action_cancel), onDismiss) },
        shape = AppShapes.lg,
        containerColor = AgentProTheme.colors.elevatedSurface,
    )
}
