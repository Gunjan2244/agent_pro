package com.sledje.agentpro.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Dimens
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.action_back
import com.sledje.agentpro.resources.action_more_options
import com.sledje.agentpro.resources.ic_arrow_back
import com.sledje.agentpro.resources.ic_cloud_off
import com.sledje.agentpro.resources.ic_more_vert
import com.sledje.agentpro.resources.offline_banner
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** A top-bar icon action. Every icon button has a content description. */
data class TopBarAction(val icon: DrawableResource, val contentDescription: String, val onClick: () -> Unit)

/** An overflow-menu entry. */
data class OverflowItem(val label: String, val onClick: () -> Unit)

/**
 * The top bar: flat white, title, optional back, at most 2 icon actions and an overflow menu
 * (calm-screen rule 6). A hairline bottom border appears only once the content has scrolled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: List<TopBarAction> = emptyList(),
    overflow: List<OverflowItem> = emptyList(),
    scrolled: Boolean = false,
    /** A trailing element that isn't an icon, e.g. the Home avatar that opens Settings (counts as one icon). */
    trailing: (@Composable () -> Unit)? = null,
) {
    require(actions.size + (if (trailing != null) 1 else 0) <= 2) { "At most 2 top bar icons; put the rest in overflow" }
    val surface = MaterialTheme.colorScheme.surface
    Column(modifier) {
        TopAppBar(
            title = {
                Text(
                    title,
                    style = AgentProTheme.text.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(Res.drawable.ic_arrow_back), stringResource(Res.string.action_back), Modifier.size(Dimens.icon))
                    }
                }
            },
            actions = {
                actions.forEach { action ->
                    IconButton(onClick = action.onClick) {
                        Icon(painterResource(action.icon), action.contentDescription, Modifier.size(Dimens.icon))
                    }
                }
                if (overflow.isNotEmpty()) OverflowMenu(overflow)
                trailing?.invoke()
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = surface,
                scrolledContainerColor = surface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        if (scrolled) HairlineDivider()
    }
}

@Composable
private fun OverflowMenu(items: List<OverflowItem>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(Res.drawable.ic_more_vert), stringResource(Res.string.action_more_options), Modifier.size(Dimens.icon))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = AgentProTheme.colors.elevatedSurface) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label, style = AgentProTheme.text.body) },
                    onClick = { open = false; item.onClick() },
                )
            }
        }
    }
}

/** A thin grey bar under the top bar while offline: "Offline — showing saved data". */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = Spacing.m, vertical = Spacing.s)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(Res.drawable.ic_cloud_off),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.icon),
        )
        Spacer(Modifier.width(Spacing.s))
        Text(stringResource(Res.string.offline_banner), style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The screen frame: top bar, offline banner slot, snackbar host (with Undo), and either a FAB or a
 * sticky [BottomActionBar] for the one primary action.
 */
@Composable
fun AppScaffold(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    offline: Boolean = false,
    snackbarHostState: SnackbarHostState? = null,
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                topBar()
                AnimatedVisibility(visible = offline) { OfflineBanner() }
            }
        },
        snackbarHost = { if (snackbarHostState != null) AppSnackbarHost(snackbarHostState) },
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
        content = content,
    )
}
