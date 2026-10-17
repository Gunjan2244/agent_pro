package com.sledje.agentpro.feature.website.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.sledje.agentpro.core.catalog.FeatureId
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Dimens
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppCard
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.designsystem.components.BottomActionBar
import com.sledje.agentpro.core.designsystem.components.EmptyState
import com.sledje.agentpro.core.designsystem.components.HairlineDivider
import com.sledje.agentpro.core.designsystem.components.NavRow
import com.sledje.agentpro.core.designsystem.components.PrimaryButton
import com.sledje.agentpro.core.designsystem.components.ScreenState
import com.sledje.agentpro.core.designsystem.components.CardSkeleton
import com.sledje.agentpro.core.ui.UiState
import com.sledje.agentpro.feature.website.data.Site
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.action_more_options
import com.sledje.agentpro.resources.ic_more_vert
import com.sledje.agentpro.resources.needs_internet
import com.sledje.agentpro.resources.tab_website
import com.sledje.agentpro.resources.website_add_to_home
import com.sledje.agentpro.resources.website_copy_link
import com.sledje.agentpro.resources.website_none
import com.sledje.agentpro.resources.website_open
import com.sledje.agentpro.resources.website_preview
import com.sledje.agentpro.resources.website_publish
import com.sledje.agentpro.resources.website_share
import com.sledje.agentpro.resources.website_show_qr
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** One hub row, resolved from a FeatureRegistry `HubEntry` (title and supporting text already translated). */
data class HubRow(val key: String, val title: String, val supporting: String?, val icon: DrawableResource, val onClick: () -> Unit)

/** Test tag of the hub's sticky Publish bar. */
const val HUB_PUBLISH_BAR_TAG = "website_publish_bar"

/**
 * The Website tab (docs/UX.md, Website tab (hub)): one status card (address, status pill, Share, and
 * an overflow with Copy link, Open, Add to home screen (once published) and Show QR code), the NavRows
 * from the registry, and a sticky bar with Preview and Publish whenever the draft differs from what's live.
 */
@Composable
fun WebsiteHubScreen(
    site: UiState<Site>,
    display: (Site) -> String,
    rows: List<HubRow>,
    publishing: Boolean,
    snackbar: SnackbarHostState,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
    onAddToHome: () -> Unit,
    onShowQr: () -> Unit,
    onPreview: () -> Unit,
    onPublish: () -> Unit,
    onRetry: () -> Unit,
) {
    val online = isOnline()
    val content = (site as? UiState.Content)?.data
    AppScaffold(
        topBar = { AppTopBar(title = stringResource(Res.string.tab_website)) },
        offline = !online,
        snackbarHostState = snackbar,
        bottomBar = {
            if (content != null && content.hasUnpublishedChanges) {
                BottomActionBar(
                    text = stringResource(Res.string.website_publish),
                    onClick = onPublish,
                    enabled = online && !publishing,
                    helperText = if (!online) stringResource(Res.string.needs_internet) else null,
                    secondaryText = stringResource(Res.string.website_preview),
                    onSecondary = onPreview,
                    modifier = Modifier.testTag(HUB_PUBLISH_BAR_TAG),
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            ScreenState(
                state = site,
                empty = { EmptyState(FeatureId.SiteBuilder.icon, stringResource(Res.string.website_none)) },
                onRetry = onRetry,
                skeleton = { Column(Modifier.padding(Spacing.m)) { CardSkeleton() } },
            ) { s ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    StatusCard(s, display(s), onShare, onCopy, onOpen, onAddToHome, onShowQr, Modifier.padding(Spacing.m))
                    rows.forEachIndexed { i, row ->
                        if (i > 0) HairlineDivider()
                        NavRow(row.title, row.onClick, supporting = row.supporting, icon = row.icon, modifier = Modifier.testTag("hub_${row.key}"))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    site: Site,
    address: String,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
    onAddToHome: () -> Unit,
    onShowQr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(address, style = AgentProTheme.text.strong, maxLines = 2, overflow = TextOverflow.Ellipsis)
                SiteStatusPill(site.status)
            }
            CardOverflow(
                buildList {
                    add(stringResource(Res.string.website_copy_link) to onCopy)
                    add(stringResource(Res.string.website_open) to onOpen)
                    // A never-published site isn't installable (its manifest is a 404).
                    if (site.isPublished) add(stringResource(Res.string.website_add_to_home) to onAddToHome)
                    add(stringResource(Res.string.website_show_qr) to onShowQr)
                },
            )
        }
        PrimaryButton(stringResource(Res.string.website_share), onShare, Modifier.fillMaxWidth().padding(top = Spacing.m))
    }
}

@Composable
private fun CardOverflow(items: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                painterResource(Res.drawable.ic_more_vert),
                stringResource(Res.string.action_more_options),
                Modifier.size(Dimens.icon),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = AgentProTheme.colors.elevatedSurface) {
            items.forEach { (label, action) ->
                DropdownMenuItem(text = { Text(label, style = AgentProTheme.text.body) }, onClick = { open = false; action() })
            }
        }
    }
}
