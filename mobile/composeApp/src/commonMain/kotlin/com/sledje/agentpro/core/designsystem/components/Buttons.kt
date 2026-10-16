package com.sledje.agentpro.core.designsystem.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.AppShapes
import com.sledje.agentpro.core.designsystem.Dimens
import com.sledje.agentpro.core.designsystem.Spacing
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/*
 * Button kinds (docs/DESIGN_SYSTEM.md, Buttons): primary (one per screen), secondary (at most 2),
 * tertiary (inline links, "More details"), destructive (only inside confirm dialogs).
 * All are 48dp tall with a 12dp radius; labels may wrap instead of being cut off.
 */

private val ButtonPadding = PaddingValues(horizontal = Spacing.m)

@Composable
private fun ButtonContent(text: String, icon: DrawableResource?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(Dimens.icon))
            Spacer(Modifier.width(Spacing.s))
        }
        Text(text, style = AgentProTheme.text.label, textAlign = TextAlign.Center)
    }
}

/** Filled primary. One per screen. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: DrawableResource? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = Dimens.buttonHeight),
        enabled = enabled,
        shape = AppShapes.md,
        contentPadding = ButtonPadding,
        elevation = null,
    ) { ButtonContent(text, icon) }
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
