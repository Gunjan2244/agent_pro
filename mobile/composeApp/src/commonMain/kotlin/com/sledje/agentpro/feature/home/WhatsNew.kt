package com.sledje.agentpro.feature.home

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sledje.agentpro.core.catalog.FeatureId
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.ListLeading
import com.sledje.agentpro.core.designsystem.components.ListRow
import com.sledje.agentpro.core.designsystem.components.PrimaryButton
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.ic_person
import com.sledje.agentpro.resources.ic_savings
import com.sledje.agentpro.resources.ic_web
import com.sledje.agentpro.resources.whats_new_companies
import com.sledje.agentpro.resources.whats_new_customers
import com.sledje.agentpro.resources.whats_new_got_it
import com.sledje.agentpro.resources.whats_new_website
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The one-time "What's new" sheet on Home (DECISIONS #103). Bump [VERSION] and edit [WhatsNewItem] when a release
 * has something existing agents should be led to; agents who finish onboarding are marked as having seen the current one.
 */
object WhatsNew {
    const val VERSION = 1

    /** True when the stored `whats_new_seen` value is below [VERSION] (missing or unreadable counts as 0). */
    fun isUnseen(seen: String?): Boolean = (seen?.toIntOrNull() ?: 0) < VERSION

    /** Existing agents only: the sheet shows when this version is unseen and the agent already has a site. */
    fun shouldShow(seen: String?, hasSite: Boolean): Boolean = hasSite && isUnseen(seen)

    /** The rows to show: those whose feature flag is on (all when [enabled] says so). */
    fun rows(enabled: (FeatureId) -> Boolean): List<WhatsNewItem> = WhatsNewItem.entries.filter { enabled(it.feature) }
}

/** One row of the "What's new" sheet, shown only while its [feature] is enabled. */
enum class WhatsNewItem(val feature: FeatureId, val label: StringResource, val icon: DrawableResource) {
    Companies(FeatureId.Offers, Res.string.whats_new_companies, Res.drawable.ic_savings),
    CustomerLogin(FeatureId.CustomerPortal, Res.string.whats_new_customers, Res.drawable.ic_person),
    ForMyCustomers(FeatureId.CustomerPortal, Res.string.whats_new_website, Res.drawable.ic_web),
}

/** The sheet's body (the frame, with its "What's new" title, is the shared bottom sheet). */
@Composable
fun ColumnScope.WhatsNewContent(items: List<WhatsNewItem>, onItem: (WhatsNewItem) -> Unit, onGotIt: () -> Unit) {
    items.forEach { item ->
        ListRow(title = stringResource(item.label), leading = ListLeading.Icon(item.icon), onClick = { onItem(item) })
    }
    PrimaryButton(stringResource(Res.string.whats_new_got_it), onClick = onGotIt, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.m))
}
