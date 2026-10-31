package com.sledje.agentpro.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sledje.agentpro.app.AppDependencies
import com.sledje.agentpro.core.catalog.FeatureId
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.data.StoreKeys
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Dimens
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppBottomSheet
import com.sledje.agentpro.core.designsystem.components.AvatarButton
import com.sledje.agentpro.core.designsystem.components.HairlineDivider
import com.sledje.agentpro.core.designsystem.components.ListLeading
import com.sledje.agentpro.core.designsystem.components.ListRow
import com.sledje.agentpro.core.designsystem.components.NavRow
import com.sledje.agentpro.core.features.AppFeature
import com.sledje.agentpro.core.features.BottomNavItem
import com.sledje.agentpro.core.features.FeatureHost
import com.sledje.agentpro.feature.customers.CustomerFormRoute
import com.sledje.agentpro.feature.settings.ProfileRoute
import com.sledje.agentpro.feature.website.WebsiteContentRoute
import com.sledje.agentpro.feature.website.WebsiteLinkRoute
import com.sledje.agentpro.feature.website.WebsitePreviewRoute
import com.sledje.agentpro.feature.website.WebsiteSectionRoute
import com.sledje.agentpro.feature.website.WebsiteTemplatesRoute
import com.sledje.agentpro.feature.website.data.Site
import com.sledje.agentpro.feature.website.data.StarterContent
import kotlinx.serialization.json.JsonObject
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.action_settings
import com.sledje.agentpro.resources.home_greeting
import com.sledje.agentpro.resources.home_greeting_plain
import com.sledje.agentpro.resources.home_nothing_due
import com.sledje.agentpro.resources.home_setup_row
import com.sledje.agentpro.resources.ic_check_circle
import com.sledje.agentpro.resources.ic_home
import com.sledje.agentpro.resources.ic_radio_button_unchecked
import com.sledje.agentpro.resources.setup_about
import com.sledje.agentpro.resources.setup_companies
import com.sledje.agentpro.resources.setup_portal
import com.sledje.agentpro.resources.whats_new_title
import com.sledje.agentpro.resources.setup_customer
import com.sledje.agentpro.resources.setup_done
import com.sledje.agentpro.resources.setup_next
import com.sledje.agentpro.resources.setup_profile
import com.sledje.agentpro.resources.setup_publish
import com.sledje.agentpro.resources.setup_share
import com.sledje.agentpro.resources.setup_title
import com.sledje.agentpro.resources.setup_todo
import com.sledje.agentpro.resources.tab_home
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Serializable data object HomeRoute

/** The full-screen "What's new" walkthrough for important new features (docs/UX.md, What's new; DECISIONS #121). */
@Serializable data object WhatsNewRoute

/** Home: always on (no catalog flag). Phase 5 has the greeting, the setup checklist and "Nothing due today"; Phase 11 adds Today, KPIs and the chart. */
object HomeFeature : AppFeature {
    override val flag = null

    override val bottomNavItem = BottomNavItem("home", HomeRoute, Res.string.tab_home, Res.drawable.ic_home, order = 1)

    override fun NavGraphBuilder.routes(host: FeatureHost) {
        composable<HomeRoute> {
            val vm = viewModel { HomeViewModel(host.deps) }
            val state by vm.state.collectAsState()
            // Important news opens full screen once, on the first Home of this app run that has it.
            LaunchedEffect(state.walkthrough.isNotEmpty()) {
                if (state.walkthrough.isNotEmpty() && vm.claimWalkthrough()) host.nav.navigate(WhatsNewRoute)
            }
            HomeScreen(
                state,
                onOpenSettings = host::openSettings,
                onSetupItem = { host.openSetupItem(it) },
                onWhatsNewItem = { item ->
                    vm.markWhatsNewSeen()
                    host.openWhatsNewItem(item)
                },
                onWhatsNewSeen = vm::markWhatsNewSeen,
            )
        }
        composable<WhatsNewRoute> { entry ->
            // Home's view model: the pages it found, and the same "seen" mark.
            val homeEntry = remember(entry) { host.nav.getBackStackEntry<HomeRoute>() }
            val vm: HomeViewModel = viewModel(viewModelStoreOwner = homeEntry) { HomeViewModel(host.deps) }
            // Kept as opened: marking them seen empties the state while the screen is still animating out.
            val pages = remember { vm.state.value.walkthrough }
            if (pages.isEmpty()) {
                LaunchedEffect(Unit) { host.nav.popBackStack() }
                return@composable
            }
            WhatsNewScreen(
                pages,
                onShowMe = { item ->
                    vm.markWhatsNewSeen()
                    host.nav.popBackStack()
                    host.openWhatsNewItem(item)
                },
                onDone = {
                    vm.markWhatsNewSeen()
                    host.nav.popBackStack()
                },
            )
        }
    }
}

/** Opens the screen where [item] gets done (docs/UX.md, Home: each checklist row is a shortcut). */
internal fun FeatureHost.openSetupItem(item: SetupItem) {
    when (item) {
        SetupItem.Profile, SetupItem.Companies -> nav.navigate(ProfileRoute)
        SetupItem.Publish -> openTab("website")
        SetupItem.About -> if (openTab("website")) nav.navigate(WebsiteSectionRoute(StarterContent.ABOUT, fromSetup = true))
        SetupItem.Share -> if (openTab("website")) nav.navigate(WebsiteLinkRoute)
        SetupItem.FirstCustomer -> if (openTab("customers")) nav.navigate(CustomerFormRoute())
        SetupItem.CustomerLogin -> openTab("customers") // then: open a customer, Website login → Send login link
    }
}

/** Opens the screen a "What's new" row leads to. */
internal fun FeatureHost.openWhatsNewItem(item: WhatsNewItem) {
    when (item) {
        WhatsNewItem.Companies -> nav.navigate(ProfileRoute)
        WhatsNewItem.CustomerLogin -> openTab("customers")
        WhatsNewItem.ForMyCustomers -> if (openTab("website")) nav.navigate(WebsiteContentRoute)
        WhatsNewItem.NewTemplates -> if (openTab("website")) nav.navigate(WebsiteTemplatesRoute)
        WhatsNewItem.MultiPageWebsite -> if (openTab("website")) nav.navigate(WebsitePreviewRoute)
        WhatsNewItem.PhotoEditor -> if (openTab("website")) nav.navigate(WebsiteSectionRoute("hero"))
        WhatsNewItem.ProfilePhoto -> nav.navigate(ProfileRoute)
    }
}

/** One item of the setup checklist (docs/UX.md, Home: profile, publish, own About text, share the link, first customer). */
enum class SetupItem(val label: StringResource, val feature: FeatureId? = null) {
    Profile(Res.string.setup_profile),
    Companies(Res.string.setup_companies, FeatureId.Offers),
    Publish(Res.string.setup_publish),
    About(Res.string.setup_about),
    Share(Res.string.setup_share),
    FirstCustomer(Res.string.setup_customer),
    CustomerLogin(Res.string.setup_portal, FeatureId.CustomerPortal),
}

/**
 * [items] are the checklist rows whose feature flag is on (all of them by default); [whatsNew] holds the rows of the
 * one-time "What's new" sheet and [walkthrough] the pages of the full-screen one (at most one of them is non-empty;
 * both are empty when there's nothing new; [WhatsNew]).
 */
data class HomeUiState(
    val firstName: String? = null,
    val photoUrl: String? = null,
    val done: Set<SetupItem> = emptySet(),
    val items: List<SetupItem> = SetupItem.entries.toList(),
    val whatsNew: List<WhatsNewItem> = emptyList(),
    val walkthrough: List<WhatsNewItem> = emptyList(),
) {
    val doneCount: Int get() = items.count { it in done }
    val setupComplete: Boolean get() = items.all { it in done }

    /** The first visible item, in checklist order, that isn't done; null when all are. */
    val next: SetupItem? get() = items.firstOrNull { it !in done }

    /** Every visible row opens its screen, so no step can block the others; [next] is only the suggested one. */
    fun isTappable(item: SetupItem): Boolean = item in items
}

/**
 * Home's state. "Share your link" and "Give a customer their login" are local marks; "Add your first customer" is
 * done once any live customer exists; "Add your companies" is checked online once when Home starts (`my_company_offers`,
 * stays undone offline or on failure, no error UI). Items of a disabled feature are hidden and not counted. The avatar
 * shows the site's hero photo, else the profile photo, as the site does.
 */
class HomeViewModel(private val deps: AppDependencies) : ViewModel() {
    private val companiesLinked = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            try {
                if (deps.companies.refresh() is Result.Ok) companiesLinked.value = deps.companies.memberships.value.any { it.isActive }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Offline or failing: the item just stays undone.
            }
        }
        // The account keeps what was seen (DECISIONS #127): raise it to this device's mark, whether it's a dismissal
        // here or one from before the account kept it. Offline or failing: tried again on the next app run.
        viewModelScope.launch {
            combine(deps.store.observe(StoreKeys.WHATS_NEW_SEEN), deps.profiles.observeMine()) { device, profile ->
                WhatsNew.seenVersion(device).takeIf { profile != null && it > profile.whatsNewSeen }
            }.filterNotNull().distinctUntilChanged().collect { deps.profiles.markWhatsNewSeen(it) }
        }
    }

    private val marks = combine(
        deps.store.observe(StoreKeys.SITE_SHARED),
        deps.store.observe(StoreKeys.PORTAL_INVITED),
        deps.store.observe(StoreKeys.WHATS_NEW_SEEN),
    ) { shared, invited, seen -> Triple(shared != null, invited != null, seen) }

    val state: StateFlow<HomeUiState> = combine(
        deps.profiles.observeMine(),
        deps.sites.observeMine(),
        deps.featureFlags,
        marks,
        combine(deps.customers.observeAll(), companiesLinked) { customers, linked -> customers.isNotEmpty() to linked },
    ) { profile, site, flags, (shared, invited, seen), (hasCustomers, linked) ->
        val news = WhatsNew.pending(WhatsNew.seenVersion(seen, profile?.whatsNewSeen), hasSite = site != null, enabled = flags::isEnabled)
        HomeUiState(
            firstName = profile?.firstName,
            photoUrl = site?.heroPhoto() ?: profile?.photoUrl?.takeIf { it.isNotBlank() },
            items = SetupItem.entries.filter { it.feature?.let(flags::isEnabled) ?: true },
            done = buildSet {
                if (profile?.isComplete == true) add(SetupItem.Profile)
                if (linked) add(SetupItem.Companies)
                if (site?.isPublished == true) add(SetupItem.Publish)
                // The seeded bio is shared text; the agent's own words rank better (docs/UX.md, Home).
                if (StarterContent.isOwnBio(site?.aboutBio())) add(SetupItem.About)
                if (shared) add(SetupItem.Share)
                if (hasCustomers) add(SetupItem.FirstCustomer)
                if (invited) add(SetupItem.CustomerLogin)
            },
            whatsNew = WhatsNew.sheet(news),
            walkthrough = WhatsNew.walkthrough(news),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    private var walkthroughOpened = false

    /** True the first time only, so Home opens the walkthrough once (not again on Back or recomposition). */
    fun claimWalkthrough(): Boolean = !walkthroughOpened.also { walkthroughOpened = true }

    /**
     * Closing the sheet or the walkthrough, a row, "Got it", "Show me", "Not now": this version won't show again. Marked
     * on the device at once; the account follows (see init).
     */
    fun markWhatsNewSeen() {
        viewModelScope.launch { deps.store.put(StoreKeys.WHATS_NEW_SEEN, WhatsNew.VERSION.toString()) }
    }
}

/** The draft's `hero.photo` URL, null when unset. */
internal fun Site.heroPhoto(): String? =
    ((draftContent[StarterContent.HERO] as? JsonObject)?.get(StarterContent.PHOTO) as? kotlinx.serialization.json.JsonPrimitive)
        ?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

/** The draft's `about.bio` text, null when unset. */
internal fun Site.aboutBio(): String? =
    ((draftContent[StarterContent.ABOUT] as? JsonObject)?.get(StarterContent.BIO) as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content

@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenSettings: () -> Unit,
    onSetupItem: (SetupItem) -> Unit = {},
    onWhatsNewItem: (WhatsNewItem) -> Unit = {},
    onWhatsNewSeen: () -> Unit = {},
) {
    var sheet by remember { mutableStateOf(false) }
    val title = state.firstName?.let { stringResource(Res.string.home_greeting, it) } ?: stringResource(Res.string.home_greeting_plain)
    TabScreen(
        Res.string.tab_home,
        titleText = title,
        trailing = { AvatarButton(state.firstName.orEmpty(), stringResource(Res.string.action_settings), onOpenSettings, photoUrl = state.photoUrl) },
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (!state.setupComplete) {
                NavRow(
                    title = stringResource(Res.string.home_setup_row, state.doneCount, state.items.size),
                    onClick = { sheet = true },
                )
                HairlineDivider()
            }
            Text(
                stringResource(Res.string.home_nothing_due),
                style = AgentProTheme.text.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.m),
            )
        }
    }
    if (sheet) {
        AppBottomSheet(stringResource(Res.string.setup_title), onDismiss = { sheet = false }) {
            SetupChecklistContent(state, onItem = { item ->
                sheet = false
                onSetupItem(item)
            })
        }
    }
    // Never on top of the checklist: it waits until that sheet is closed.
    if (state.whatsNew.isNotEmpty() && !sheet) {
        AppBottomSheet(stringResource(Res.string.whats_new_title), onDismiss = onWhatsNewSeen) {
            WhatsNewContent(state.whatsNew, onItem = onWhatsNewItem, onGotIt = onWhatsNewSeen)
        }
    }
}

/** The checklist sheet's rows: every row opens its screen and the next one is marked. */
@Composable
fun ColumnScope.SetupChecklistContent(state: HomeUiState, onItem: (SetupItem) -> Unit) {
    state.items.forEach { item ->
        val done = item in state.done
        val tappable = state.isTappable(item)
        val isNext = item == state.next
        ListRow(
            title = stringResource(item.label),
            supporting = stringResource(
                when {
                    done -> Res.string.setup_done
                    isNext -> Res.string.setup_next
                    else -> Res.string.setup_todo
                },
            ),
            leading = ListLeading.Icon(if (done) Res.drawable.ic_check_circle else Res.drawable.ic_radio_button_unchecked),
            onClick = if (tappable) ({ onItem(item) }) else null,
            current = isNext,
            muted = !tappable,
        )
    }
}
