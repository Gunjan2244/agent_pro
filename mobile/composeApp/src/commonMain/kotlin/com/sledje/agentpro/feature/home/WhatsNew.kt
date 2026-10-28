package com.sledje.agentpro.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.sledje.agentpro.core.catalog.FeatureId
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.AppShapes
import com.sledje.agentpro.core.designsystem.Dimens
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.designsystem.components.BottomActionBar
import com.sledje.agentpro.core.designsystem.components.ListLeading
import com.sledje.agentpro.core.designsystem.components.ListRow
import com.sledje.agentpro.core.designsystem.components.PrimaryButton
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.ic_arrow_forward
import com.sledje.agentpro.resources.ic_description
import com.sledje.agentpro.resources.ic_person
import com.sledje.agentpro.resources.ic_savings
import com.sledje.agentpro.resources.ic_web
import com.sledje.agentpro.resources.whats_new_companies
import com.sledje.agentpro.resources.whats_new_customers
import com.sledje.agentpro.resources.whats_new_got_it
import com.sledje.agentpro.resources.whats_new_next
import com.sledje.agentpro.resources.whats_new_not_now
import com.sledje.agentpro.resources.whats_new_page
import com.sledje.agentpro.resources.whats_new_pages
import com.sledje.agentpro.resources.whats_new_pages_body
import com.sledje.agentpro.resources.whats_new_show_me
import com.sledje.agentpro.resources.whats_new_templates
import com.sledje.agentpro.resources.whats_new_templates_body
import com.sledje.agentpro.resources.whats_new_title
import com.sledje.agentpro.resources.whats_new_website
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * How existing agents learn about new features (DECISIONS #103, #121; CLAUDE.md, Rolling out to existing users). Each
 * [WhatsNewItem] carries the release version that introduced it ([WhatsNewItem.since]); an agent who has seen version n
 * is shown only the items above n. If any of them is important ([WhatsNewItem.walkthrough]), they all show as the
 * full-screen walkthrough on the next app open ([WhatsNewScreen]); otherwise as the Home sheet ([WhatsNewContent]).
 * Shipping one is adding an item with `since` = [VERSION] + 1. New sign-ups never see any: finishing onboarding marks
 * the current version seen.
 */
object WhatsNew {
    /** The newest item's version; what "seen" is set to. */
    val VERSION: Int = WhatsNewItem.entries.maxOf { it.since }

    /** The stored `whats_new_seen` value as a version (missing or unreadable counts as 0). */
    fun seenVersion(seen: String?): Int = seen?.toIntOrNull() ?: 0

    /** Existing agents only (they have a site): the items newer than [seen] whose feature is on. */
    fun pending(seen: String?, hasSite: Boolean, enabled: (FeatureId) -> Boolean): List<WhatsNewItem> =
        if (!hasSite) {
            emptyList()
        } else {
            WhatsNewItem.entries.filter { it.since > seenVersion(seen) && enabled(it.feature) }
        }

    /** The walkthrough's pages: every pending item, important ones first, as soon as one is important; else none. */
    fun walkthrough(pending: List<WhatsNewItem>): List<WhatsNewItem> =
        if (pending.any { it.walkthrough }) pending.sortedByDescending { it.walkthrough } else emptyList()

    /** The Home sheet's rows: the pending items when none is important (those go to the walkthrough). */
    fun sheet(pending: List<WhatsNewItem>): List<WhatsNewItem> = if (pending.any { it.walkthrough }) emptyList() else pending
}

/**
 * One new thing to show existing agents, while its [feature] is enabled. [since] is the release version that brought
 * it; [walkthrough] marks an important one (it changes their public website, needs them to act or decide, adds a
 * required step, or moves something they use), which takes the full-screen walkthrough with [body] under [label].
 */
enum class WhatsNewItem(
    val since: Int,
    val feature: FeatureId,
    val label: StringResource,
    val icon: DrawableResource,
    val walkthrough: Boolean = false,
    val body: StringResource? = null,
) {
    Companies(1, FeatureId.Offers, Res.string.whats_new_companies, Res.drawable.ic_savings),
    CustomerLogin(1, FeatureId.CustomerPortal, Res.string.whats_new_customers, Res.drawable.ic_person),
    ForMyCustomers(1, FeatureId.CustomerPortal, Res.string.whats_new_website, Res.drawable.ic_web),

    /** The redesigned templates (#117) changed every site's look; the grid (#120) lets the agent pick another. */
    NewTemplates(2, FeatureId.SiteBuilder, Res.string.whats_new_templates, Res.drawable.ic_web, walkthrough = true, body = Res.string.whats_new_templates_body),

    /** Every public website became multi-page (#122): Home, Insurance with a page per plan type, About me, Contact. */
    MultiPageWebsite(3, FeatureId.SiteBuilder, Res.string.whats_new_pages, Res.drawable.ic_description, walkthrough = true, body = Res.string.whats_new_pages_body),
}

/** The sheet's body (the frame, with its "What's new" title, is the shared bottom sheet). */
@Composable
fun ColumnScope.WhatsNewContent(items: List<WhatsNewItem>, onItem: (WhatsNewItem) -> Unit, onGotIt: () -> Unit) {
    items.forEach { item ->
        ListRow(title = stringResource(item.label), leading = ListLeading.Icon(item.icon), onClick = { onItem(item) })
    }
    PrimaryButton(stringResource(Res.string.whats_new_got_it), onClick = onGotIt, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.m))
}

/**
 * The full-screen walkthrough (docs/UX.md, What's new): one page per item in [pages], with "n of N" when there are
 * several. **Show me →** (the arrow nudges) opens the item's screen and ends the walkthrough ([onShowMe]); **Next**
 * turns the page, and on the last page **Not now** ends it ([onDone]), as does Back. No back arrow: it isn't a place.
 */
@OptIn(ExperimentalComposeUiApi::class) // BackHandler (ui-backhandler)
@Composable
fun WhatsNewScreen(pages: List<WhatsNewItem>, onShowMe: (WhatsNewItem) -> Unit, onDone: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val item = pages.getOrNull(page) ?: return
    val last = page == pages.lastIndex
    BackHandler(onBack = onDone)
    AppScaffold(
        topBar = { AppTopBar(title = stringResource(Res.string.whats_new_title)) },
        bottomBar = {
            BottomActionBar(
                text = stringResource(Res.string.whats_new_show_me),
                onClick = { onShowMe(item) },
                trailingIcon = Res.drawable.ic_arrow_forward,
                nudgeTrailingIcon = true,
                secondaryText = stringResource(if (last) Res.string.whats_new_not_now else Res.string.whats_new_next),
                onSecondary = { if (last) onDone() else page++ },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (pages.size > 1) {
                Text(stringResource(Res.string.whats_new_page, page + 1, pages.size), style = AgentProTheme.text.bodyMuted)
            }
            Box(
                Modifier.size(Dimens.imageSlot).background(MaterialTheme.colorScheme.primaryContainer, AppShapes.full),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(item.icon), null, Modifier.size(Dimens.icon), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(
                stringResource(item.label),
                style = AgentProTheme.text.title,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            item.body?.let {
                Text(stringResource(it), style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}
