package com.sledje.agentpro.feature.website.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.AppShapes
import com.sledje.agentpro.core.designsystem.Dimens
import com.sledje.agentpro.core.designsystem.Spacing

/**
 * The template preview shared by onboarding step 3 and Website → Appearance → Template (docs/UX.md, Template
 * preview): the one template opened from the [TemplateGrid], rendered live in a card ([preview], the SitePreview of
 * the agent's own content) that fills the space. The template's name is the top bar's title, so it isn't repeated here.
 */
@Composable
fun TemplatePreviewBody(modifier: Modifier = Modifier, preview: @Composable () -> Unit) {
    Box(
        modifier.fillMaxSize().padding(horizontal = Spacing.m, vertical = Spacing.s)
            .clip(AppShapes.lg)
            .border(Dimens.hairline, AgentProTheme.colors.cardBorder, AppShapes.lg),
    ) { preview() }
}
