package com.sledje.agentpro.core.designsystem.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextAlign
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.AppShapes
import com.sledje.agentpro.core.designsystem.Dimens
import com.sledje.agentpro.core.designsystem.Motion
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.rememberReduceMotion
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.ic_google_g
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/*
 * Button kinds (docs/DESIGN_SYSTEM.md, Buttons): primary (one per screen), secondary (at most 2),
 * tertiary (inline links, "More details"), destructive (only inside confirm dialogs).
 * All are 48dp tall with a 12dp radius; labels may wrap instead of being cut off.
 */

private val ButtonPadding = PaddingValues(horizontal = Spacing.m)

@Composable
private fun ButtonContent(text: String, icon: DrawableResource?, trailingIcon: DrawableResource? = null, nudge: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(Dimens.icon))
            Spacer(Modifier.width(Spacing.s))
        }
        Text(text, style = AgentProTheme.text.label, textAlign = TextAlign.Center)
        if (trailingIcon != null) {
            Spacer(Modifier.width(Spacing.s))
            Icon(painterResource(trailingIcon), contentDescription = null, modifier = Modifier.size(Dimens.icon).let { if (nudge) it.nudgeForward() else it })
        }
    }
}

/**
 * Moves the content forward and back by [Spacing.s], forever (the "Continue →" arrow; docs/DESIGN_SYSTEM.md, Motion).
 * Still when the platform's animations are off.
 */
@Composable
private fun Modifier.nudgeForward(): Modifier {
    if (rememberReduceMotion()) return this
    val distance = with(LocalDensity.current) { Spacing.s.toPx() }
    val progress by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(Motion.NUDGE_MILLIS, easing = Motion.StandardEasing), RepeatMode.Reverse),
    )
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return graphicsLayer { translationX = progress * distance * if (rtl) -1f else 1f }
}

/** Filled primary. One per screen. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: DrawableResource? = null,
    /** An icon after the label, e.g. a forward arrow; [nudgeTrailingIcon] moves it back and forth to invite the tap. */
    trailingIcon: DrawableResource? = null,
    nudgeTrailingIcon: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = Dimens.buttonHeight),
        enabled = enabled,
        shape = AppShapes.md,
        contentPadding = ButtonPadding,
        elevation = null,
    ) { ButtonContent(text, icon, trailingIcon, nudgeTrailingIcon) }
}

/** Outlined in the outline colour. At most 2 per screen. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: DrawableResource? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Dimens.buttonHeight),
        enabled = enabled,
        shape = AppShapes.md,
        border = BorderStroke(Dimens.hairline, MaterialTheme.colorScheme.outline),
        contentPadding = ButtonPadding,
    ) { ButtonContent(text, icon) }
}

/**
 * "Continue with Google" (DECISIONS #112): a secondary button with Google's four-colour "G" untinted, as Google's
 * sign-in branding asks. The only place that mark appears.
 */
@Composable
fun GoogleSignInButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Dimens.buttonHeight),
        enabled = enabled,
        shape = AppShapes.md,
        border = BorderStroke(Dimens.hairline, MaterialTheme.colorScheme.outline),
        contentPadding = ButtonPadding,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(Res.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(Dimens.icon))
            Spacer(Modifier.width(Spacing.s))
            Text(text, style = AgentProTheme.text.label, textAlign = TextAlign.Center)
        }
    }
}

/** Text button: inline links, "More details", dialog dismiss. */
@Composable
fun TertiaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: DrawableResource? = null,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Dimens.touchTarget),
        enabled = enabled,
        shape = AppShapes.md,
        contentPadding = ButtonPadding,
    ) { ButtonContent(text, icon) }
}

/** Error-coloured action. Only inside a [ConfirmDialog]. */
@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    val error = MaterialTheme.colorScheme.error
    if (filled) {
        Button(
            onClick = onClick,
            modifier = modifier.heightIn(min = Dimens.buttonHeight),
            shape = AppShapes.md,
            colors = ButtonDefaults.buttonColors(containerColor = error, contentColor = MaterialTheme.colorScheme.onError),
            contentPadding = ButtonPadding,
            elevation = null,
        ) { ButtonContent(text, null) }
    } else {
        TextButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = Dimens.touchTarget),
            shape = AppShapes.md,
            colors = ButtonDefaults.textButtonColors(contentColor = error),
            contentPadding = ButtonPadding,
        ) { ButtonContent(text, null) }
    }
}

/** The screen's primary action as a FAB (elevated; radius 16). With [text] it's extended. */
@Composable
fun AppFab(
    icon: DrawableResource,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
) {
    if (text == null) {
        FloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            shape = AppShapes.lg,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) { Icon(painterResource(icon), contentDescription, Modifier.size(Dimens.icon)) }
    } else {
        ExtendedFloatingActionButton(
            text = { Text(text, style = AgentProTheme.text.label) },
            icon = { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(Dimens.icon)) },
            onClick = onClick,
            modifier = modifier,
            shape = AppShapes.lg,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
