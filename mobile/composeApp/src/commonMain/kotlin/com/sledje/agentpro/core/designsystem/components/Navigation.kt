package com.sledje.agentpro.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.sledje.agentpro.core.designsystem.Motion
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Dimens
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.onboarding_step_of
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** One bottom-bar tab. [badge] shows a count (e.g. new enquiries); null or 0 shows none. */
data class NavBarItem(
    val key: String,
    val label: String,
    val icon: DrawableResource,
    val badge: Int? = null,
)

/**
 * The bottom navigation bar (4 tabs; docs/UX.md, Navigation map; DECISIONS #18): flat surface with a
 * hairline on top, grey icons and labels, primary only for the selected tab with a primaryContainer
 * indicator. Labels never truncate mid-word in Hindi; they ellipsize after one line.
 */
@Composable
fun AppNavigationBar(
    items: List<NavBarItem>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        HairlineDivider()
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = Dimens.none) {
            items.forEach { item ->
                NavigationBarItem(
                    selected = item.key == selectedKey,
                    onClick = { onSelect(item.key) },
                    icon = {
                        val badge = item.badge?.takeIf { it > 0 }
                        if (badge != null) {
                            BadgedBox(badge = { Badge { Text(badge.toString()) } }) {
                                Icon(painterResource(item.icon), contentDescription = null, modifier = Modifier.size(Dimens.icon))
                            }
                        } else {
                            Icon(painterResource(item.icon), contentDescription = null, modifier = Modifier.size(Dimens.icon))
                        }
                    },
                    label = { Text(item.label, style = AgentProTheme.text.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

/** One option of a [SegmentedControl]. */
data class SegmentOption(val key: String, val label: String)

/**
 * A segmented control, e.g. `[ Customers | Dues ]` at the top of the Customers tab (docs/UX.md) or the
 * premium mode in the policy form. Single choice; the selected segment uses primaryContainer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedControl(
    options: List<SegmentOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option.key == selectedKey,
                onClick = { onSelect(option.key) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    inactiveContainerColor = MaterialTheme.colorScheme.surface,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    activeBorderColor = MaterialTheme.colorScheme.outline,
                    inactiveBorderColor = MaterialTheme.colorScheme.outline,
                ),
                label = { Text(option.label, style = AgentProTheme.text.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

/**
 * The thin progress bar at the top of each onboarding step (docs/UX.md, Onboarding). TalkBack reads
 * "Step 2 of 3".
 */
@Composable
fun StepProgressBar(step: Int, total: Int, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.onboarding_step_of, step, total)
    // Slides to the new step when the step changes on the same screen (the launch screens).
    val progress by animateFloatAsState(step.toFloat() / total.coerceAtLeast(1), Motion.navigation())
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier.fillMaxWidth().semantics {
            contentDescription = description
            stateDescription = description
        },
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainer,
        strokeCap = StrokeCap.Round,
        gapSize = Dimens.none,
        drawStopIndicator = {},
    )
}

/** The Home avatar that opens Settings: a 48dp touch target around a 40dp [Avatar]. */
@Composable
fun AvatarButton(name: String, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.semantics { this.contentDescription = contentDescription }) {
        Avatar(name)
    }
}
