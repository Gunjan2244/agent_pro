package com.sledje.agentpro.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.sledje.agentpro.core.catalog.Tone

/**
 * Material 3 colour schemes mapped from the generated [Tokens] (docs/DESIGN_SYSTEM.md, Colour).
 * Roles the tokens don't define are derived from token values, never new colours:
 * - secondary/tertiary = primary, so library components only ever use the one accent;
 *   secondaryContainer = primaryContainer (selected chip, nav indicator);
 * - surfaceVariant = surfaceContainer; surfaceContainerLowest = surface;
 *   surfaceContainerHighest = surfaceContainerHigh; surfaceTint = surface (no tonal tint);
 * - inverse roles (snackbar) = the other theme's surface/onSurface/primary;
 * - errorContainer/onErrorContainer = the `danger` tone.
 */
internal val LightColors: ColorScheme = with(Tokens.ColorLight) {
    lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = Tokens.ColorDark.primary,
        secondary = primary,
        onSecondary = onPrimary,
        secondaryContainer = primaryContainer,
        onSecondaryContainer = onPrimaryContainer,
        tertiary = primary,
        onTertiary = onPrimary,
        tertiaryContainer = primaryContainer,
        onTertiaryContainer = onPrimaryContainer,
        background = background,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceContainer,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = surface,
        inverseSurface = onSurface,
        inverseOnSurface = surface,
        error = error,
        onError = onPrimary,
        errorContainer = Tokens.Tones.danger.light.container,
        onErrorContainer = Tokens.Tones.danger.light.text,
        outline = outline,
        outlineVariant = outlineVariant,
        surfaceBright = surface,
        surfaceDim = surfaceContainerHigh,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHigh,
    )
}

internal val DarkColors: ColorScheme = with(Tokens.ColorDark) {
    darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = Tokens.ColorLight.primary,
        secondary = primary,
        onSecondary = onPrimary,
        secondaryContainer = primaryContainer,
        onSecondaryContainer = onPrimaryContainer,
        tertiary = primary,
        onTertiary = onPrimary,
        tertiaryContainer = primaryContainer,
        onTertiaryContainer = onPrimaryContainer,
        background = background,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceContainer,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = surface,
        inverseSurface = onSurface,
        inverseOnSurface = surface,
        error = error,
        onError = onPrimary,
        errorContainer = Tokens.Tones.danger.dark.container,
        onErrorContainer = Tokens.Tones.danger.dark.text,
        outline = outline,
        outlineVariant = outlineVariant,
        surfaceBright = surfaceContainerHigh,
        surfaceDim = surface,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHigh,
    )
}

/**
 * Colours Material 3 has no role for: `muted`, tones, chart palette and the card/elevated-surface
 * rules (docs/DESIGN_SYSTEM.md, Surfaces and elevation). Read through `AgentProTheme.colors`.
 */
@Immutable
data class AppColors(
    val isDark: Boolean,
    /** Captions, timestamps, helper text, axis labels (≥ 4.5:1). */
    val muted: Color,
    /** Card fill: white in light, surfaceContainerLow in dark. */
    val card: Color,
    /** Card border: outlineVariant in light, none (transparent) in dark. */
    val cardBorder: Color,
    /** Dialogs, menus, date picker: surface in light, surfaceContainerHigh in dark. */
    val elevatedSurface: Color,
    /** Chart series (blue, orange); never status tones. */
    val chartSeries: List<Color>,
    /** Ordinal stages (pipeline). */
    val chartOrdinalRamp: List<Color>,
) {
    /** The colours of [tone] for this theme. */
    fun tone(tone: Tone): ToneColors = Tokens.Tones.of(tone).let { if (isDark) it.dark else it.light }
}

internal val LightAppColors = AppColors(
    isDark = false,
    muted = Tokens.ColorLight.muted,
    card = Tokens.ColorLight.surface,
    cardBorder = Tokens.ColorLight.outlineVariant,
    elevatedSurface = Tokens.ColorLight.surface,
    chartSeries = Tokens.Chart.seriesLight,
    chartOrdinalRamp = Tokens.Chart.ordinalRampLight,
)

internal val DarkAppColors = AppColors(
    isDark = true,
    muted = Tokens.ColorDark.muted,
    card = Tokens.ColorDark.surfaceContainerLow,
    cardBorder = Color.Transparent,
    elevatedSurface = Tokens.ColorDark.surfaceContainerHigh,
    chartSeries = Tokens.Chart.seriesDark,
    chartOrdinalRamp = Tokens.Chart.ordinalRampDark,
)

internal val LocalAppColors = staticCompositionLocalOf { LightAppColors }
