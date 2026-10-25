package com.sledje.agentpro.core.features

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import com.sledje.agentpro.app.AppDependencies
import com.sledje.agentpro.app.DeepLink
import com.sledje.agentpro.core.catalog.FeatureId
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/** What a feature's routes get from the main shell. */
interface FeatureHost {
    val nav: NavController
    val deps: AppDependencies

    /** The enabled features' segments for the tab [tabKey], in order (e.g. Customers | Dues). */
    fun segmentsFor(tabKey: String): List<TabSegment>

    /** Opens Settings (the Home avatar). */
    fun openSettings()

    /** Switches to the bottom-bar tab [tabKey] as the bar does (its saved state restored); false when that tab isn't enabled. */
    fun openTab(tabKey: String): Boolean = false
}

/**
 * A bottom-bar tab (docs/UX.md: Home · Enquiries · Customers · Website). [route] is the tab's
 * `@Serializable` start route, registered by the feature's [AppFeature.routes].
 */
data class BottomNavItem(
    val key: String,
    val route: Any,
    val label: StringResource,
    val icon: DrawableResource,
    val order: Int,
    /** The tab's count badge (docs/UX.md: Enquiries = new, Customers = overdue); null or 0 shows none. */
    val badge: (@Composable (FeatureHost) -> Int?)? = null,
)

/** A segment of a tab's segmented control, e.g. Dues inside Customers (DECISIONS #18). */
data class TabSegment(
    val tabKey: String,
    val key: String,
    val label: StringResource,
    val order: Int,
    val content: @Composable (FeatureHost) -> Unit,
)

/** A block on Home (at most 3 above the fold; docs/DESIGN_SYSTEM.md, calm-screen rule 3). Phase 11. */
data class HomeCard(val key: String, val order: Int, val content: @Composable (FeatureHost) -> Unit)

/**
 * A block on the customer detail's Details tab (docs/UX.md, Customer detail), e.g. the portal's "Website
 * login" card. Items of the enabled features are drawn in [order]; [content] gets the customer's id.
 */
data class CustomerDetailItem(val key: String, val order: Int, val content: @Composable (FeatureHost, customerId: String) -> Unit)

/** A row in Settings (docs/UX.md, Settings). Phase 13. */
data class SettingsEntry(val key: String, val group: String, val label: StringResource, val icon: DrawableResource?, val order: Int, val onClick: (FeatureHost) -> Unit)

/**
 * A NavRow in the Website hub (Content, Appearance, Link & domain, Stats; docs/UX.md). [supporting] is
 * the row's second line ("7 sections", "120 views this week"), read inside the hub's composition.
 */
data class HubEntry(
    val key: String,
    val label: StringResource,
    val icon: DrawableResource,
    val order: Int,
    val onClick: (FeatureHost) -> Unit,
    val supporting: (@Composable (FeatureHost) -> String?)? = null,
)

/**
 * A feature plugs into the app through this interface (DECISIONS #11; docs/ARCHITECTURE.md, Feature
 * registry). Adding a feature = a `feature/<name>/` package, one line in [FeatureRegistry.all] and one
 * flag in `catalog.features`.
 */
interface AppFeature {
    /**
     * The catalog flag that switches this feature, or null for a core feature that is always on
     * (Home and Settings: the app has no screen without them, and the catalog has no flag for them).
     */
    val flag: FeatureId?

    /** Registers the feature's `@Serializable` routes in the main NavHost. */
    fun NavGraphBuilder.routes(host: FeatureHost)

    val bottomNavItem: BottomNavItem? get() = null
    val segments: List<TabSegment> get() = emptyList()
    val homeCards: List<HomeCard> get() = emptyList()
    val settingsEntries: List<SettingsEntry> get() = emptyList()
    val customerDetailItems: List<CustomerDetailItem> get() = emptyList()
    val websiteHubEntries: List<HubEntry> get() = emptyList()

    /**
     * Opens a deep link this feature owns (e.g. `agentpro://enquiry/{id}` → the enquiry's detail screen),
     * after the shell has opened the link's tab. Returns false when the link isn't this feature's.
     */
    fun openDeepLink(link: DeepLink, host: FeatureHost): Boolean = false
}
