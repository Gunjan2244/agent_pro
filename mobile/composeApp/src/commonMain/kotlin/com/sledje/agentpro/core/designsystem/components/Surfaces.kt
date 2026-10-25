package com.sledje.agentpro.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.AppShapes
import com.sledje.agentpro.core.designsystem.Dimens
import com.sledje.agentpro.core.designsystem.Spacing

/**
 * A card (docs/DESIGN_SYSTEM.md, Surfaces): white with a 1dp outlineVariant hairline and no shadow in
 * light; surfaceContainerLow with no border in dark. Never nest cards.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AgentProTheme.colors
    val border = if (colors.isDark) null else BorderStroke(Dimens.hairline, colors.cardBorder)
    val body: @Composable () -> Unit = { Column(Modifier.padding(Spacing.m), content = content) }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = AppShapes.md, color = colors.card, border = border, content = body)
    } else {
        Surface(modifier = modifier, shape = AppShapes.md, color = colors.card, border = border, content = body)
    }
}

/** The hairline allowed between list rows (and nowhere else). */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Dimens.hairline)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * A round avatar with the person's initials (agent data, never translated). With [photoUrl] the photo covers the
 * initials once it has loaded; while it loads, offline or if it fails, the initials show.
 */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, photoUrl: String? = null) {
    val initials = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }
    Box(
        modifier
            .size(Dimens.avatar)
            .background(MaterialTheme.colorScheme.surfaceContainer, AppShapes.full)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            style = AgentProTheme.text.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().clip(AppShapes.full),
            )
        }
    }
}
