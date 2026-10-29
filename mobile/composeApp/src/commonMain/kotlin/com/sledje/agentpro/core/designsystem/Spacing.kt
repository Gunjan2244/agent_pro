package com.sledje.agentpro.core.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The only 3 spacing values (docs/DESIGN_SYSTEM.md, Spacing), from the generated [Tokens.Space].
 * Every padding, margin, gap and Spacer uses one of these; nothing else is allowed.
 */
object Spacing {
    /** 8dp: closely related things (icon + label, field label + field, chips in a row, pill padding). */
    val s: Dp = Tokens.Space.s

    /** 16dp: the default (screen gutter, card/sheet/row padding, leading icon to text). */
    val m: Dp = Tokens.Space.m

    /** 24dp: between sections, above a section header, above the bottom action bar. */
    val l: Dp = Tokens.Space.l
}

/**
 * Fixed component dimensions (not spacing). They live only inside core/designsystem
 * (docs/DESIGN_SYSTEM.md, Spacing: touch ≥ 48, buttons 48, rows 72/56, icons 24, radii 8/12/16).
 */
object Dimens {
    /** No elevation / no gap (flat surfaces: the bottom bar, the onboarding progress bar). */
    val none: Dp = 0.dp
    val touchTarget: Dp = 48.dp
    val buttonHeight: Dp = 48.dp
    val rowTwoLine: Dp = 72.dp
    val rowOneLine: Dp = 56.dp
    val icon: Dp = 24.dp

    /** Icons inside pills and chips, so a pill stays one text line tall. */
    val iconSmall: Dp = 18.dp

    /** The unseen-row dot of a ListRow (new enquiries). */
    val unseenDot: Dp = 8.dp
    val pillHeight: Dp = 32.dp
    val avatar: Dp = 40.dp
    val hairline: Dp = 1.dp

    /** The border that marks the chosen card in a grid of choices (the current template). */
    val selectedBorder: Dp = 2.dp
    val swatch: Dp = 40.dp
    val imageSlot: Dp = 96.dp
    val chartHeight: Dp = 200.dp
    val stepperConnector: Dp = 2.dp
    val skeletonLine: Dp = 14.dp
    val skeletonBlock: Dp = 96.dp

    /** Photo crop: the height of a "How customers see it" preview. */
    val cropPreview: Dp = 72.dp

    /** The site QR code in the Share sheet (Website hub), on its own white quiet zone. */
    val qrCode: Dp = 200.dp

    // Charts (docs/DESIGN_SYSTEM.md, Charts: Marks)
    val chartColumnRadius: Dp = 4.dp
    val chartColumnGap: Dp = 2.dp
    val chartColumnWidth: Dp = 16.dp
    val chartLineThickness: Dp = 2.dp
    val chartPoint: Dp = 8.dp
}
