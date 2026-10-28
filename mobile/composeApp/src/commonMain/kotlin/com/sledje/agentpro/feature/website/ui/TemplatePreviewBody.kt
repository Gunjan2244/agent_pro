package com.sledje.agentpro.feature.website.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.sledje.agentpro.core.catalog.TemplateSpec
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.AppShapes
import com.sledje.agentpro.core.designsystem.Dimens
import com.sledje.agentpro.core.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * The template preview shared by onboarding step 3 and Website → Appearance → Template (docs/UX.md, Template
 * preview): the one template opened from the [TemplateGrid], rendered live in a card ([preview], the SitePreview of
 * the agent's own content), with the template's name and description below.
 */
@Composable
fun TemplatePreviewBody(template: TemplateSpec, modifier: Modifier = Modifier, preview: @Composable () -> Unit) {
    Column(modifier.fillMaxSize().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .clip(AppShapes.lg)
                .border(Dimens.hairline, AgentProTheme.colors.cardBorder, AppShapes.lg),
        ) { preview() }
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(stringResource(template.name), style = AgentProTheme.text.title, modifier = Modifier.semantics { heading() })
            Text(stringResource(template.description), style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
