package com.sledje.agentpro.core.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.ToneColors
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/*
 * Hand-written helpers over the generated catalog enums (Catalog.kt, docs/SHARED_DEFINITIONS.md).
 * Used by StatusPill, FilterChipRow and FormRenderer, so no screen maps keys to labels, colours or
 * icons by hand.
 */

/** The entry's translated label. */
@Composable
fun CatalogEntry.label(): String = stringResource(label)

/** The entry's Material Symbols icon. */
@Composable
fun CatalogIconEntry.icon(): Painter = painterResource(icon)

/** The colours of the entry's status tone in the current theme. */
@Composable
fun CatalogTonedEntry.tone(): ToneColors = AgentProTheme.tone(tone)

/** The swatch colour (`#RRGGBB` from catalog.json). */
fun AccentSwatch.color(): Color = hexColor(hex)

internal fun hexColor(hex: String): Color {
    val rgb = hex.removePrefix("#").toLong(16)
    return Color(0xFF000000 or rgb)
}
