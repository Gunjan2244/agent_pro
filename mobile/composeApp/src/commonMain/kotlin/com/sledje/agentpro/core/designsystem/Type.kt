package com.sledje.agentpro.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.inter_medium
import com.sledje.agentpro.resources.inter_regular
import com.sledje.agentpro.resources.inter_semibold
import com.sledje.agentpro.resources.noto_sans_devanagari
import org.jetbrains.compose.resources.Font

/**
 * Typography (docs/DESIGN_SYSTEM.md, Typography): the app uses only `text.s|m|l` (14/23/39 sp);
 * hierarchy within a size comes from weight (400/500/600) and colour. Inter for `en`, Noto Sans
 * Devanagari for `hi` (it also covers Latin, digits and ₹); in `hi` every line height is +2sp.
 */
private val Regular = FontWeight(Tokens.Font.weights[0])
private val Medium = FontWeight(Tokens.Font.weights[1])
private val SemiBold = FontWeight(Tokens.Font.weights[2])

/** Tabular figures, so money and numbers line up. */
const val TABULAR_FIGURES: String = "tnum"

@Composable
internal fun interFontFamily(): FontFamily = FontFamily(
    Font(Res.font.inter_regular, Regular),
    Font(Res.font.inter_medium, Medium),
    Font(Res.font.inter_semibold, SemiBold),
)

/** One variable font file (google/fonts), instanced at the 3 app weights. */
@OptIn(ExperimentalTextApi::class)
@Composable
internal fun devanagariFontFamily(): FontFamily = FontFamily(
    listOf(Regular, Medium, SemiBold).map { weight ->
        Font(
            Res.font.noto_sans_devanagari,
            weight,
            FontStyle.Normal,
            FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

/** The app's named styles (the "Style" table). Only [bodyMuted] carries a colour. */
@Immutable
data class AppTextStyles(
    val body: TextStyle,
    val bodyMuted: TextStyle,
    val label: TextStyle,
    val strong: TextStyle,
    val title: TextStyle,
    val number: TextStyle,
    val display: TextStyle,
)

private fun lineHeight(token: TextToken, hindi: Boolean): TextUnit =
    if (hindi) (token.lineHeight.value + Tokens.Text.hiLineExtra.value).sp else token.lineHeight

private fun style(family: FontFamily, token: TextToken, weight: FontWeight, hindi: Boolean): TextStyle = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = token.size,
    lineHeight = lineHeight(token, hindi),
    letterSpacing = 0.sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

internal fun appTextStyles(family: FontFamily, hindi: Boolean, muted: Color): AppTextStyles {
    val s = Tokens.Text.s
    val m = Tokens.Text.m
    val l = Tokens.Text.l
    return AppTextStyles(
        body = style(family, s, Regular, hindi),
        bodyMuted = style(family, s, Regular, hindi).copy(color = muted),
        label = style(family, s, Medium, hindi),
        strong = style(family, s, SemiBold, hindi),
        title = style(family, m, SemiBold, hindi),
        number = style(family, m, SemiBold, hindi).copy(fontFeatureSettings = TABULAR_FIGURES),
        display = style(family, l, SemiBold, hindi),
    )
}

/**
 * Every Material 3 slot mapped onto the 3 app sizes, so library components never bring in another size:
 * display → l, headline and title → m (600), body → s (400), label → s (500).
 */
internal fun appTypography(family: FontFamily, hindi: Boolean): Typography {
    val display = style(family, Tokens.Text.l, SemiBold, hindi)
    val heading = style(family, Tokens.Text.m, SemiBold, hindi)
    val body = style(family, Tokens.Text.s, Regular, hindi)
    val label = style(family, Tokens.Text.s, Medium, hindi)
    return Typography(
        displayLarge = display, displayMedium = display, displaySmall = display,
        headlineLarge = heading, headlineMedium = heading, headlineSmall = heading,
        titleLarge = heading, titleMedium = heading, titleSmall = heading,
        bodyLarge = body, bodyMedium = body, bodySmall = body,
        labelLarge = label, labelMedium = label, labelSmall = label,
    )
}

internal val LocalAppTextStyles = staticCompositionLocalOf<AppTextStyles> {
    error("AppTextStyles are provided by AgentProTheme")
}
