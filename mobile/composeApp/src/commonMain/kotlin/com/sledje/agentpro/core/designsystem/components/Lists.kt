package com.sledje.agentpro.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.AppShapes
import com.sledje.agentpro.core.designsystem.Dimens
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.action_see_all
import com.sledje.agentpro.resources.ic_chevron_right
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** What sits at the start of a [ListRow]. */
sealed interface ListLeading {
    data class Icon(val icon: DrawableResource) : ListLeading
    data class Avatar(val name: String) : ListLeading
}

/**
 * A list row (calm-screen rule 7): leading avatar or icon, one title line (truncated with an
 * ellipsis), one supporting line (grows taller rather than truncating) and at most one trailing
 * element. 72dp with supporting text, 56dp without. Lists sit on the background with
 * [HairlineDivider]s between rows, never inside cards.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: ListLeading? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    /** When set, a small primary dot before the title marks the row as unseen; this is its TalkBack label. */
    unseenLabel: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val minHeight = if (supporting != null) Dimens.rowTwoLine else Dimens.rowOneLine
    val background = if (selected) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface
    Row(
        modifier
            .fillMaxWidth()
            .background(background)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = minHeight)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (leading) {
            is ListLeading.Icon -> {
                Icon(painterResource(leading.icon), null, Modifier.size(Dimens.icon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(Spacing.m))
            }
            is ListLeading.Avatar -> {
                Avatar(leading.name)
                Spacer(Modifier.width(Spacing.m))
            }
            null -> Unit
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (unseenLabel != null) {
                    Box(
                        Modifier
                            .size(Dimens.unseenDot)
                            .background(MaterialTheme.colorScheme.primary, AppShapes.full)
                            .semantics { contentDescription = unseenLabel },
                    )
                    Spacer(Modifier.width(Spacing.s))
                }
                Text(title, style = AgentProTheme.text.strong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (supporting != null) {
                Text(supporting, style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Spacing.m))
            trailing()
        }
    }
}

/** A row with a chevron that opens a sub-screen (progressive disclosure). */
@Composable
fun NavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: DrawableResource? = null,
) {
    ListRow(
        title = title,
        modifier = modifier,
        supporting = supporting,
        leading = icon?.let { ListLeading.Icon(it) },
        onClick = onClick,
        trailing = {
            Icon(
                painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimens.icon),
            )
        },
    )
}

/** A small grey section label, with an optional "See all". Sits [Spacing.l] below the previous section. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, onSeeAll: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = Spacing.m, top = Spacing.l, end = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = AgentProTheme.text.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (onSeeAll != null) TertiaryButton(stringResource(Res.string.action_see_all), onSeeAll)
    }
}
