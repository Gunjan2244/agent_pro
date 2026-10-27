package com.sledje.agentpro.feature.website.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.sledje.agentpro.core.catalog.TemplateSpec
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.onboarding_template_position
import org.jetbrains.compose.resources.stringResource

/**
 * The template choice shared by onboarding step 3 and Website → Appearance → Template (docs/UX.md, Template
 * picker): one pager of every template (shared/templates/index.json), opened on [selectedKey]'s.
 */
@Stable
class TemplateChooserState internal constructor(
    val templates: List<TemplateSpec>,
    val pager: PagerState,
) {
    /** The template on show. */
    val current: TemplateSpec? get() = templates.getOrNull(pager.currentPage)
}

@Composable
fun rememberTemplateChooser(templates: List<TemplateSpec>, selectedKey: String): TemplateChooserState {
    val startPage = remember { templates.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = startPage) { templates.size }
    return remember(templates) { TemplateChooserState(templates, pager) }
}

/** The pager with the template's name, description and position below. */
@Composable
fun TemplateChooserBody(state: TemplateChooserState, pageContent: @Composable (TemplateSpec) -> Unit) {
    TemplatePager(state, pageContent)
}

@Composable
private fun TemplatePager(state: TemplateChooserState, pageContent: @Composable (TemplateSpec) -> Unit) {
    val templates = state.templates
    val current = state.current ?: templates.firstOrNull() ?: return
    Column(Modifier.fillMaxSize().padding(vertical = Spacing.m)) {
        HorizontalPager(
            state = state.pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Spacing.l),
            pageSpacing = Spacing.m,
        ) { page -> templates.getOrNull(page)?.let { pageContent(it) } }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.m).padding(top = Spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(stringResource(current.name), style = AgentProTheme.text.title, modifier = Modifier.semantics { heading() })
            Text(stringResource(current.description), style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Text(stringResource(Res.string.onboarding_template_position, state.pager.currentPage + 1, templates.size), style = AgentProTheme.text.bodyMuted)
        }
    }
}
