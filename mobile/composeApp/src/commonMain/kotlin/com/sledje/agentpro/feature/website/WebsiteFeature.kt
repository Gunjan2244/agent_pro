package com.sledje.agentpro.feature.website

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.sledje.agentpro.core.catalog.FeatureId
import com.sledje.agentpro.core.catalog.Templates
import com.sledje.agentpro.core.catalog.label
import com.sledje.agentpro.core.data.StoreKeys
import com.sledje.agentpro.core.designsystem.components.DiscardChangesDialog
import com.sledje.agentpro.core.features.AppFeature
import com.sledje.agentpro.core.features.BottomNavItem
import com.sledje.agentpro.core.features.FeatureHost
import com.sledje.agentpro.core.features.FeatureRegistry
import com.sledje.agentpro.core.features.HubEntry
import com.sledje.agentpro.core.ui.UiState
import com.sledje.agentpro.feature.offers.OfferPickerRoute
import com.sledje.agentpro.feature.website.data.SiteContent
import com.sledje.agentpro.feature.website.data.SiteLinks
import com.sledje.agentpro.feature.website.data.SitePreviewPayload
import com.sledje.agentpro.feature.website.data.rememberPreviewOffers
import com.sledje.agentpro.feature.website.ui.AppearanceScreen
import com.sledje.agentpro.feature.website.ui.AppearanceViewModel
import com.sledje.agentpro.feature.website.ui.ChangeLinkDialog
import com.sledje.agentpro.feature.website.ui.ContentScreen
import com.sledje.agentpro.feature.website.ui.ContentViewModel
import com.sledje.agentpro.feature.website.ui.ServicesMatchSheet
import com.sledje.agentpro.feature.website.ui.ServicesMatchViewModel
import com.sledje.agentpro.resources.website_match_done
import com.sledje.agentpro.feature.website.ui.HubRow
import com.sledje.agentpro.feature.website.ui.LinkScreen
import com.sledje.agentpro.feature.website.ui.LinkViewModel
import com.sledje.agentpro.feature.website.ui.PreviewScreen
import com.sledje.agentpro.feature.website.ui.PublishState
import com.sledje.agentpro.feature.website.ui.SectionEditorScreen
import com.sledje.agentpro.feature.website.ui.SectionEditorViewModel
import com.sledje.agentpro.feature.website.ui.ShareSheet
import com.sledje.agentpro.feature.website.ui.StatsScreen
import com.sledje.agentpro.feature.website.ui.StatsViewModel
import com.sledje.agentpro.feature.website.ui.TemplatePickerScreen
import com.sledje.agentpro.feature.website.ui.TemplatePreviewScreen
import com.sledje.agentpro.feature.website.ui.WebsiteHubScreen
import com.sledje.agentpro.feature.website.ui.WebsiteHubViewModel
import com.sledje.agentpro.feature.website.ui.sectionFor
import com.sledje.agentpro.feature.website.ui.websiteErrorTextRes
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.feature.website.ui.setupHintFor
import com.sledje.agentpro.resources.ic_bar_chart
import com.sledje.agentpro.resources.ic_language
import com.sledje.agentpro.resources.ic_public
import com.sledje.agentpro.resources.ic_web
import com.sledje.agentpro.resources.tab_website
import com.sledje.agentpro.resources.website_hub_appearance
import com.sledje.agentpro.resources.website_hub_appearance_summary
import com.sledje.agentpro.resources.website_hub_content
import com.sledje.agentpro.resources.website_hub_content_summary
import com.sledje.agentpro.resources.website_hub_link
import com.sledje.agentpro.resources.website_hub_stats
import com.sledje.agentpro.resources.website_hub_stats_summary
import com.sledje.agentpro.resources.website_link_changed
import com.sledje.agentpro.resources.website_link_copied
import com.sledje.agentpro.resources.website_publish_fix
import com.sledje.agentpro.resources.website_published
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** The Website tab (the hub) and its screens (docs/UX.md, Website tab; navigation map). */
@Serializable data object WebsiteRoute
@Serializable data object WebsiteContentRoute
@Serializable data class WebsiteSectionRoute(val key: String, val fromSetup: Boolean = false)
@Serializable data object WebsiteAppearanceRoute
@Serializable data object WebsiteTemplatesRoute
@Serializable data class WebsiteTemplatePreviewRoute(val key: String)
@Serializable data object WebsiteLinkRoute
@Serializable data object WebsiteStatsRoute
@Serializable data object WebsitePreviewRoute

/**
 * The Website tab (flag `siteBuilder`; Phase 8): the hub with its status card, the hub rows from
 * [FeatureRegistry] `websiteHubEntries` (this feature contributes Content, Appearance, Link & domain
 * and Stats; Domains adds its own in Phase 10 behind `customDomains`), Preview and Publish.
 */
object WebsiteFeature : AppFeature {
    override val flag = FeatureId.SiteBuilder

    override val bottomNavItem = BottomNavItem("website", WebsiteRoute, Res.string.tab_website, FeatureId.SiteBuilder.icon, order = 4)

    override val websiteHubEntries: List<HubEntry> = listOf(
        HubEntry(
            key = "content",
            label = Res.string.website_hub_content,
            icon = Res.drawable.ic_web,
            order = 1,
            onClick = { it.nav.navigate(WebsiteContentRoute) },
            supporting = { host ->
                val site = (host.websiteHub().site.collectAsState().value as? UiState.Content)?.data
                site?.let {
                    val visible = SiteContent.orderedSections(it.draftContent).count { s -> !SiteContent.isHidden(it.draftContent, s) }
                    stringResource(Res.string.website_hub_content_summary, visible)
                }
            },
        ),
        HubEntry(
            key = "appearance",
            label = Res.string.website_hub_appearance,
            icon = Res.drawable.ic_language,
            order = 2,
            onClick = { it.nav.navigate(WebsiteAppearanceRoute) },
            supporting = { host ->
                val site = (host.websiteHub().site.collectAsState().value as? UiState.Content)?.data
                val template = site?.let { Templates.fromKey(it.templateKey) }
                val accent = site?.accent ?: template?.defaultAccent
                if (template != null && accent != null) {
                    stringResource(Res.string.website_hub_appearance_summary, stringResource(template.name), accent.label())
                } else {
                    null
                }
            },
        ),
        HubEntry(
            key = "link",
            label = Res.string.website_hub_link,
            icon = Res.drawable.ic_public,
            order = 3,
            onClick = { it.nav.navigate(WebsiteLinkRoute) },
            supporting = { host -> (host.websiteHub().site.collectAsState().value as? UiState.Content)?.data?.let { SiteLinks.display(it.slug) } },
        ),
        HubEntry(
            key = "stats",
            label = Res.string.website_hub_stats,
            icon = Res.drawable.ic_bar_chart,
            order = 4,
            onClick = { it.nav.navigate(WebsiteStatsRoute) },
            supporting = { host ->
                val views by host.websiteHub().weekViews.collectAsState()
                stringResource(Res.string.website_hub_stats_summary, views)
            },
        ),
    )

    override fun NavGraphBuilder.routes(host: FeatureHost) {
        composable<WebsiteRoute> { HubRoute(host) }
        composable<WebsiteContentRoute> { ContentRoute(host) }
        composable<WebsiteSectionRoute> { entry -> SectionRoute(host, entry) }
        composable<WebsiteAppearanceRoute> { AppearanceRoute(host) }
        composable<WebsiteTemplatesRoute> { TemplatesRoute(host) }
        composable<WebsiteTemplatePreviewRoute> { entry -> TemplatePreviewRoute(host, entry) }
        composable<WebsiteLinkRoute> { LinkRoute(host) }
        composable<WebsiteStatsRoute> { StatsRoute(host) }
        composable<WebsitePreviewRoute> { PreviewRoute(host) }
    }
}

/** The hub's view model, shared by every Website screen (scoped to the [WebsiteRoute] entry). */
@Composable
internal fun FeatureHost.websiteHub(): WebsiteHubViewModel {
    val owner = remember(nav) { nav.getBackStackEntry<WebsiteRoute>() }
    return viewModel(viewModelStoreOwner = owner) { WebsiteHubViewModel(deps.sites, deps.profiles, deps.website.stats) }
}

/** Share, Copy link and Open: the actions the hub and the Link screen share. */
private class SiteActions(private val host: FeatureHost, private val snackbar: SnackbarHostState, private val copy: (String) -> Unit) {
    suspend fun share(slug: String) {
        host.deps.urlLauncher.share(SiteLinks.url(slug))
        host.deps.store.put(StoreKeys.SITE_SHARED, "true") // Home setup checklist: "Share your link"
    }

    suspend fun copyLink(slug: String) {
        copy(SiteLinks.url(slug))
        snackbar.showSnackbar(getString(Res.string.website_link_copied))
    }

    fun open(slug: String) {
        host.deps.urlLauncher.openUrl(SiteLinks.url(slug))
    }

    /** Opens the site in the browser (not a Custom Tab, which can't install) with its install sheet. */
    fun addToHomeScreen(slug: String) {
        host.deps.urlLauncher.openUrl(SiteLinks.installUrl(slug))
    }
}

@Composable
private fun rememberSiteActions(host: FeatureHost, snackbar: SnackbarHostState): SiteActions {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    return remember(host, snackbar, clipboard) { SiteActions(host, snackbar) { clipboard.setText(AnnotatedString(it)) } }
}

/**
 * Shows the hub's one-shot publish result as a snackbar; a refused publish opens Content. The snackbar runs in [scope],
 * not in the effect: consuming the result changes the effect's key, which would cancel a snackbar shown inside it
 * (it flashed for one frame, so "Your website is live" was never seen).
 */
@Composable
private fun PublishResultEffect(host: FeatureHost, hub: WebsiteHubViewModel, snackbar: SnackbarHostState, openContentOnFix: Boolean) {
    val publish by hub.publish.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(publish) {
        val message = when (val p = publish) {
            PublishState.Live -> getString(Res.string.website_published)
            is PublishState.NeedsFixes -> {
                if (openContentOnFix) host.nav.navigate(WebsiteContentRoute) { launchSingleTop = true }
                getString(Res.string.website_publish_fix, p.count)
            }
            is PublishState.Failed -> getString(websiteErrorTextRes(p.error))
            PublishState.Idle, PublishState.Publishing -> return@LaunchedEffect
        }
        hub.consumePublishResult()
        scope.launch { snackbar.showSnackbar(message) }
    }
}

@Composable
private fun HubRoute(host: FeatureHost) {
    val hub = host.websiteHub()
    val site by hub.site.collectAsState()
    val publish by hub.publish.collectAsState()
    val flags by host.deps.featureFlags.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val actions = rememberSiteActions(host, snackbar)
    var showQr by rememberSaveable { mutableStateOf(false) }
    PublishResultEffect(host, hub, snackbar, openContentOnFix = true)

    val entries = remember(flags) { FeatureRegistry.enabled(flags).flatMap { it.websiteHubEntries }.sortedBy { it.order } }
    val rows = entries.map { e -> HubRow(e.key, stringResource(e.label), e.supporting?.invoke(host), e.icon) { e.onClick(host) } }
    val slug = (site as? UiState.Content)?.data?.slug

    WebsiteHubScreen(
        site = site,
        display = { SiteLinks.display(it.slug) },
        rows = rows,
        publishing = publish == PublishState.Publishing,
        snackbar = snackbar,
        onShare = { slug?.let { scope.launch { actions.share(it) } } },
        onCopy = { slug?.let { scope.launch { actions.copyLink(it) } } },
        onOpen = { slug?.let(actions::open) },
        onAddToHome = { slug?.let(actions::addToHomeScreen) },
        onShowQr = { showQr = true },
        onPreview = { host.nav.navigate(WebsitePreviewRoute) },
        onPublish = hub::publish,
        onRetry = { scope.launch { host.deps.dataSync.refreshAll() } },
    )
    if (showQr && slug != null) {
        ShareSheet(
            url = SiteLinks.url(slug),
            display = SiteLinks.display(slug),
            onShare = { scope.launch { actions.share(slug) } },
            onCopy = { scope.launch { actions.copyLink(slug) } },
            onDismiss = { showQr = false },
        )
    }
}

@Composable
private fun ContentRoute(host: FeatureHost) {
    val hub = host.websiteHub()
    val vm = viewModel { ContentViewModel(host.deps.sites) }
    val match = viewModel { ServicesMatchViewModel(host.deps.sites, host.deps.companies, host.deps.profiles, refreshCompanies = true) }
    val state by vm.state.collectAsState()
    val matchState by match.state.collectAsState()
    val issues by hub.issues.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(getString(websiteErrorTextRes(it))); vm.consumeError() }
    }
    LaunchedEffect(matchState.applied) {
        if (matchState.applied) { match.consumeApplied(); snackbar.showSnackbar(getString(Res.string.website_match_done)) }
    }
    ServicesMatchSheet(matchState, onApply = match::apply, onDismiss = match::dismiss)
    ContentScreen(
        state = state,
        servicesMismatch = matchState.mismatch,
        onUpdateServices = match::open,
        issueSections = issues.map { it.section.key }.toSet(),
        snackbar = snackbar,
        onBack = { host.nav.popBackStack() },
        onOpenSection = { host.nav.navigate(WebsiteSectionRoute(it)) },
        onToggleVisible = { row, visible -> vm.setHidden(row.section, !visible) },
        onStartReorder = vm::startReorder,
        onMove = vm::move,
        onDone = vm::finishReorder,
    )
}

@OptIn(ExperimentalComposeUiApi::class) // BackHandler (ui-backhandler)
@Composable
private fun SectionRoute(host: FeatureHost, entry: NavBackStackEntry) {
    val route = entry.toRoute<WebsiteSectionRoute>()
    val key = route.key
    val section = sectionFor(key) ?: run { LaunchedEffect(Unit) { host.nav.popBackStack() }; return }
    val hub = host.websiteHub()
    val vm = viewModel {
        SectionEditorViewModel(section, host.deps.sites, host.deps.website.imageUploader, hub.issuesFor(key), onSaved = hub::clearIssues)
    }
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var confirmDiscard by remember { mutableStateOf(false) }
    var pickingField by rememberSaveable { mutableStateOf<String?>(null) }
    val pick = host.deps.website.mediaPicker.rememberImagePicker { picked ->
        val field = pickingField
        if (picked != null && field != null) vm.onImagePicked(field, picked)
        pickingField = null
    }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            host.nav.popBackStack()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(getString(websiteErrorTextRes(it))); vm.consumeError() }
    }
    val back = { if (vm.isDirty) confirmDiscard = true else host.nav.popBackStack() }
    BackHandler(enabled = vm.isDirty) { confirmDiscard = true }
    SectionEditorScreen(
        section = section,
        form = if (state.loading) null else vm.form,
        state = state,
        snackbar = snackbar,
        onBack = { back() },
        onPickImage = { field -> pickingField = field; pick() },
        onOfferField = { field -> host.nav.navigate(OfferPickerRoute(section = key, field = field)) },
        onSave = vm::save,
        hint = if (route.fromSetup) setupHintFor(key) else null,
    )
    if (confirmDiscard) {
        DiscardChangesDialog(onDiscard = { confirmDiscard = false; host.nav.popBackStack() }, onKeepEditing = { confirmDiscard = false })
    }
}

@Composable
private fun AppearanceRoute(host: FeatureHost) {
    val vm = viewModel { AppearanceViewModel(host.deps.sites) }
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(getString(websiteErrorTextRes(it))); vm.consumeError() }
    }
    AppearanceScreen(
        state = state,
        snackbar = snackbar,
        onBack = { host.nav.popBackStack() },
        onOpenTemplates = { host.nav.navigate(WebsiteTemplatesRoute) },
        onAccent = vm::selectAccent,
        onLocale = vm::selectLocale,
    )
}

@Composable
private fun TemplatesRoute(host: FeatureHost) {
    val vm = viewModel { AppearanceViewModel(host.deps.sites) }
    val state by vm.state.collectAsState()
    val site = state.site ?: return
    TemplatePickerScreen(
        templates = Templates.all,
        currentKey = site.templateKey,
        onBack = { host.nav.popBackStack() },
        onOpen = { key -> host.nav.navigate(WebsiteTemplatePreviewRoute(key)) },
    )
}

/** One template from the picker; taking it saves and returns to Appearance (docs/UX.md, Template preview). */
@Composable
private fun TemplatePreviewRoute(host: FeatureHost, entry: NavBackStackEntry) {
    val template = Templates.fromKey(entry.toRoute<WebsiteTemplatePreviewRoute>().key) ?: return
    val vm = viewModel { AppearanceViewModel(host.deps.sites) }
    val state by vm.state.collectAsState()
    val profile by host.websiteHub().profile.collectAsState()
    LaunchedEffect(state.templateSaved) {
        if (state.templateSaved) {
            vm.consumeTemplateSaved()
            host.nav.popBackStack(WebsiteTemplatesRoute, inclusive = true)
        }
    }
    val site = state.site ?: return
    TemplatePreviewScreen(
        template = template,
        saving = state.saving,
        onBack = { host.nav.popBackStack() },
        onUse = vm::selectTemplate,
        preview = {
            // The page as it would be after switching (DECISIONS #86); nothing is saved until the agent chooses.
            val content = remember(site, template.key) {
                SiteContent.afterSwitch(site.draftContent, Templates.fromKey(site.templateKey), template)
            }
            val offers = rememberPreviewOffers(host.deps.sites, content, template.key)
            val portal = host.deps.featureFlags.collectAsState().value.isEnabled(FeatureId.CustomerPortal)
            val json = remember(site, profile, template.key, content, offers, portal) {
                SitePreviewPayload.json(site, profile, content, template.key, offers, portal)
            }
            host.deps.website.sitePreview.Render(json, Modifier.fillMaxSize())
        },
    )
}

@Composable
private fun LinkRoute(host: FeatureHost) {
    val vm = viewModel { LinkViewModel(host.deps.sites) }
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val actions = rememberSiteActions(host, snackbar)
    LaunchedEffect(state.changed) {
        if (state.changed) {
            val message = getString(Res.string.website_link_changed)
            vm.consumeChanged()
            // In [scope]: consuming flips the effect's key, which would cancel a snackbar shown here.
            scope.launch { snackbar.showSnackbar(message) }
        }
    }
    LinkScreen(
        state = state,
        url = { SiteLinks.url(it) },
        display = { SiteLinks.display(it) },
        snackbar = snackbar,
        onBack = { host.nav.popBackStack() },
        onChange = vm::openChange,
        onShare = { state.site?.slug?.let { scope.launch { actions.share(it) } } },
        onCopy = { state.site?.slug?.let { scope.launch { actions.copyLink(it) } } },
    )
    state.change?.let { change ->
        ChangeLinkDialog(
            change = change,
            currentSlug = state.site?.slug,
            display = { SiteLinks.display(it) },
            onInput = vm::onSlugInput,
            onConfirm = vm::confirmChange,
            onDismiss = vm::dismissChange,
        )
    }
}

@Composable
private fun StatsRoute(host: FeatureHost) {
    val vm = viewModel { StatsViewModel(host.deps.website.stats) }
    val period by vm.period.collectAsState()
    val stats by vm.stats.collectAsState()
    StatsScreen(period = period, stats = stats, onBack = { host.nav.popBackStack() }, onPeriod = vm::selectPeriod)
}

@Composable
private fun PreviewRoute(host: FeatureHost) {
    val hub = host.websiteHub()
    val site by hub.site.collectAsState()
    val profile by hub.profile.collectAsState()
    val publish by hub.publish.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    PublishResultEffect(host, hub, snackbar, openContentOnFix = true)
    PreviewScreen(
        publishing = publish == PublishState.Publishing,
        snackbar = snackbar,
        onBack = { host.nav.popBackStack() },
        onPublish = hub::publish,
        preview = { modifier ->
            val current = (site as? UiState.Content)?.data
            if (current != null) {
                val offers = rememberPreviewOffers(host.deps.sites, current.draftContent, current.templateKey)
                val portal = host.deps.featureFlags.collectAsState().value.isEnabled(FeatureId.CustomerPortal)
                val json = remember(current, profile, offers, portal) { SitePreviewPayload.json(current, profile, previewOffers = offers, portalEnabled = portal) }
                host.deps.website.sitePreview.Render(json, modifier)
            }
        },
    )
}
