package com.sledje.agentpro.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.intl.Locale
import com.sledje.agentpro.core.catalog.AppLocale
import com.sledje.agentpro.core.catalog.Tone

/** Theme choice from Settings → Appearance. [System] follows the device. */
enum class ThemeMode { System, Light, Dark }

/** The app language (catalog `locales`), from the current locale; anything else counts as English. */
@Composable
@ReadOnlyComposable
fun currentAppLocale(): AppLocale = AppLocale.fromKey(Locale.current.language) ?: AppLocale.En

/** `hi` → Noto Sans Devanagari and +2sp line heights; otherwise Inter. */
@Composable
@ReadOnlyComposable
fun isHindiLocale(): Boolean = currentAppLocale() == AppLocale.Hi

/**
 * The app theme ("Clean Professional", docs/DESIGN_SYSTEM.md). Light is the default; dark is designed
 * separately and follows the system unless [themeMode] overrides it. Dynamic colour is off.
 */
@Composable
fun AgentProTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    AgentProTheme(darkTheme = dark, content = content)
}

@Composable
fun AgentProTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val hindi = isHindiLocale()
    val family = if (hindi) devanagariFontFamily() else interFontFamily()
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val appColors = if (darkTheme) DarkAppColors else LightAppColors
    val typography = remember(family, hindi) { appTypography(family, hindi) }
    val textStyles = remember(family, hindi, appColors) { appTextStyles(family, hindi, appColors.muted) }

    MaterialTheme(colorScheme = colorScheme, typography = typography, shapes = AppMaterialShapes) {
        CompositionLocalProvider(
            LocalAppColors provides appColors,
            LocalAppTextStyles provides textStyles,
            LocalContentColor provides colorScheme.onSurface,
            content = content,
        )
    }
}

/** Design-system values not covered by [MaterialTheme]. */
object AgentProTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current

    val text: AppTextStyles
        @Composable @ReadOnlyComposable get() = LocalAppTextStyles.current

    /** Text and container colours of a status [tone] in the current theme. */
    @Composable
    @ReadOnlyComposable
    fun tone(tone: Tone): ToneColors = LocalAppColors.current.tone(tone)
}
