package com.sledje.agentpro.feature.website.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.sledje.agentpro.core.catalog.AccentSwatch
import com.sledje.agentpro.core.catalog.AppLocale
import com.sledje.agentpro.core.catalog.TemplateSpec
import com.sledje.agentpro.core.catalog.color
import com.sledje.agentpro.core.catalog.label
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppCard
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.designsystem.components.BottomActionBar
import com.sledje.agentpro.core.designsystem.components.FieldFrame
import com.sledje.agentpro.core.designsystem.components.ListSkeleton
import com.sledje.agentpro.core.designsystem.components.NavRow
import com.sledje.agentpro.core.designsystem.components.SegmentOption
import com.sledje.agentpro.core.designsystem.components.SegmentedControl
import com.sledje.agentpro.core.designsystem.components.SwatchOption
import com.sledje.agentpro.core.designsystem.components.SwatchPicker
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.ic_web
import com.sledje.agentpro.resources.needs_internet
import com.sledje.agentpro.resources.onboarding_template_position
import com.sledje.agentpro.resources.onboarding_use_template
import com.sledje.agentpro.resources.website_accent
import com.sledje.agentpro.resources.website_appearance_live_note
import com.sledje.agentpro.resources.website_hub_appearance
import com.sledje.agentpro.resources.website_site_language
import com.sledje.agentpro.resources.website_template
import com.sledje.agentpro.resources.website_template_picker_title
import org.jetbrains.compose.resources.stringResource

/**
 * Appearance (docs/UX.md): Template (opens the picker), an accent row of the 8 catalog swatches, and
 * Site language (English / हिन्दी, from catalog `locales`). Each change is saved at once; offline the
 * controls are disabled with "Needs internet".
 */
@Composable
fun AppearanceScreen(
    state: AppearanceUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpenTemplates: () -> Unit,
    onAccent: (String) -> Unit,
    onLocale: (String) -> Unit,
) {
    val online = isOnline()
    val enabled = online && !state.saving
    AppScaffold(
        topBar = { AppTopBar(title = stringResource(Res.string.website_hub_appearance), onBack = onBack) },
        offline = !online,
        snackbarHostState = snackbar,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.site == null) {
                ListSkeleton(rows = 3)
                return@Box
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                NavRow(
                    title = stringResource(Res.string.website_template),
                    supporting = state.template?.let { stringResource(it.name) },
                    icon = Res.drawable.ic_web,
                    onClick = { if (enabled) onOpenTemplates() },
                )
                Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                    FieldFrame(stringResource(Res.string.website_accent)) {
                        SwatchPicker(
                            swatches = AccentSwatch.active.map { SwatchOption(it.key, it.label(), it.color()) },
                            selectedKey = state.accent?.key,
                            onSelect = { if (enabled) onAccent(it) },
                        )
                    }
                    FieldFrame(stringResource(Res.string.website_site_language)) {
                        SegmentedControl(
                            options = AppLocale.active.map { SegmentOption(it.key, it.label()) },
                            selectedKey = state.locale.key,
                            onSelect = { if (enabled) onLocale(it) },
                        )
                    }
                    Text(
                        stringResource(if (online) Res.string.website_appearance_live_note else Res.string.needs_internet),
                        style = AgentProTheme.text.bodyMuted,
                    )
                    state.error?.let { ErrorText(websiteErrorText(it)) }
                }
            }
        }
    }
}

/**
 * Template picker (docs/UX.md): a full-height pager of the agent's real draft rendered in each
 * template (SitePreview, injected as [preview]), the name and description below, and **Use this template**.
 */
@Composable
fun TemplatePickerScreen(
    templates: List<TemplateSpec>,
    selectedKey: String,
    saving: Boolean,
    onBack: () -> Unit,
    onUse: (String) -> Unit,
    preview: @Composable (TemplateSpec) -> Unit,
) {
    val online = isOnline()
    val initial = templates.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = initial) { templates.size }
    val current = templates[pager.currentPage.coerceIn(0, templates.lastIndex)]
    AppScaffold(
        topBar = { AppTopBar(title = stringResource(Res.string.website_template_picker_title), onBack = onBack) },
        offline = !online,
        bottomBar = {
            BottomActionBar(
                text = stringResource(Res.string.onboarding_use_template),
                onClick = { onUse(current.key) },
                enabled = online && !saving,
                helperText = if (!online) stringResource(Res.string.needs_internet) else null,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(vertical = Spacing.m)) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Spacing.l),
                pageSpacing = Spacing.m,
            ) { page ->
                AppCard(Modifier.fillMaxSize()) { preview(templates[page]) }
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.m).padding(top = Spacing.m),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text(stringResource(current.name), style = AgentProTheme.text.title, modifier = Modifier.semantics { heading() })
                Text(stringResource(current.description), style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Text(stringResource(Res.string.onboarding_template_position, pager.currentPage + 1, templates.size), style = AgentProTheme.text.bodyMuted)
            }
        }
    }
}
