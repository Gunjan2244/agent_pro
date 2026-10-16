package com.sledje.agentpro.core.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape

/** Shapes (docs/DESIGN_SYSTEM.md, Shape): sm 8, md 12, lg 16, full. */
object AppShapes {
    /** Text fields, chips, small buttons. */
    val sm: Shape = RoundedCornerShape(Tokens.Shape.sm)

    /** Cards, buttons (48dp tall), list groups. */
    val md: Shape = RoundedCornerShape(Tokens.Shape.md)

    /** Dialogs, FAB. */
    val lg: Shape = RoundedCornerShape(Tokens.Shape.lg)

    /** Bottom sheets: top corners only. */
    val sheet: Shape = RoundedCornerShape(topStart = Tokens.Shape.lg, topEnd = Tokens.Shape.lg)

    /** Status pills, avatars. */
    val full: Shape = CircleShape
}

internal val AppMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(Tokens.Shape.sm),
    small = RoundedCornerShape(Tokens.Shape.sm),
    medium = RoundedCornerShape(Tokens.Shape.md),
    large = RoundedCornerShape(Tokens.Shape.lg),
    extraLarge = RoundedCornerShape(Tokens.Shape.lg),
)
