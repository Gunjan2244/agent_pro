package com.sledje.agentpro.app

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.filterNotNull
import com.sledje.agentpro.core.catalog.FeatureId
import com.sledje.agentpro.core.designsystem.Motion
import com.sledje.agentpro.core.designsystem.components.AppNavigationBar
import com.sledje.agentpro.core.designsystem.components.AppSnackbarHost
import com.sledje.agentpro.core.designsystem.components.NavBarItem
import com.sledje.agentpro.core.features.AppFeature
import com.sledje.agentpro.core.features.BottomNavItem
import com.sledje.agentpro.core.features.FeatureHost
import com.sledje.agentpro.core.features.FeatureRegistry
import com.sledje.agentpro.core.features.TabSegment
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.feature.settings.SettingsRoute
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.auth_password_updated
import com.sledje.agentpro.resources.back_online
import com.sledje.agentpro.resources.onboarding_trial_started
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Test tag of the bottom navigation bar (nav smoke test). */
const val MAIN_NAV_BAR_TAG = "main_nav_bar"

/**
 * Main: the bottom bar (4 tabs; DECISIONS #18) and the NavHost, both built from the enabled features
 * of [FeatureRegistry]. Also shows the shell-level snackbars ("Back online", "Your 14-day free trial
 * has started") and routes pending deep links to their tab.
 */
@Composable
fun MainShell(deps: AppDependencies) {
    val nav = rememberNavController()
    val flags by deps.featureFlags.collectAsState()
    val features = remember(flags) { FeatureRegistry.enabled(flags) }
    val tabs = remember(features) { features.mapNotNull { it.bottomNavItem }.sortedBy { it.order } }
    val host = remember(nav, features) { ShellHost(nav, deps, features) }
    val snackbar = remember { SnackbarHostState() }

    ShellMessages(deps, snackbar)
    ShellDeepLinks(deps, nav, tabs, features, host)
    // Realtime on enquiries only while the app is in the foreground (docs/ARCHITECTURE.md, Realtime).
    LifecycleStartEffect(deps.enquiryLive) {
        deps.enquiryLive.start()
        onStopOrDispose { deps.enquiryLive.stop() }
    }
    // The same for customer sign-up links, only while the feature is on.
    val signupOn = flags.isEnabled(FeatureId.CustomerSignup)
    LifecycleStartEffect(deps.customerDraftLive, signupOn) {
        if (signupOn) deps.customerDraftLive.start()
        onStopOrDispose { deps.customerDraftLive.stop() }
    }

    val entry by nav.currentBackStackEntryAsState()
    val selected = entry?.destination?.tab(tabs)

    Scaffold(
        bottomBar = {
            if (selected != null) {
                AppNavigationBar(
                    items = tabs.map { NavBarItem(it.key, stringResource(it.label), it.icon, badge = it.badge?.invoke(host)) },
                    selectedKey = selected.key,
                    onSelect = { key -> tabs.firstOrNull { it.key == key }?.let { nav.openTab(it) } },
                    modifier = Modifier.testTag(MAIN_NAV_BAR_TAG),
                )
            }
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        // Fade-through between tabs, shared-axis X into and out of a tab (docs/DESIGN_SYSTEM.md, Motion).
        // The pop transitions also drive the predictive back gesture.
        NavHost(
            navController = nav,
            startDestination = tabs.first().route,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            enterTransition = { if (isTabSwitch(tabs)) Motion.fadeThroughEnter() else Motion.sharedAxisXEnter(forward = true) },
            exitTransition = { if (isTabSwitch(tabs)) Motion.fadeThroughExit() else Motion.sharedAxisXExit(forward = true) },
            popEnterTransition = { if (isTabSwitch(tabs)) Motion.fadeThroughEnter() else Motion.sharedAxisXEnter(forward = false) },
            popExitTransition = { if (isTabSwitch(tabs)) Motion.fadeThroughExit() else Motion.sharedAxisXExit(forward = false) },
        ) {
            features.forEach { feature -> with(feature) { routes(host) } }
        }
    }
}

private class ShellHost(
    override val nav: NavController,
    override val deps: AppDependencies,
    private val features: List<AppFeature>,
) : FeatureHost {
    override fun segmentsFor(tabKey: String): List<TabSegment> =
        features.flatMap { it.segments }.filter { it.tabKey == tabKey }.sortedBy { it.order }

    override fun openSettings() = nav.navigate(SettingsRoute)

    override fun openTab(tabKey: String): Boolean {
        val tab = features.mapNotNull { it.bottomNavItem }.firstOrNull { it.key == tabKey } ?: return false
        nav.openTab(tab)
        return true
    }
}

private fun NavDestination.tab(tabs: List<BottomNavItem>): BottomNavItem? =
    tabs.firstOrNull { tab -> hierarchy.any { it.hasRoute(tab.route::class) } }

/** True when moving between two different tabs' screens (the bottom bar), not into or out of a tab. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(tabs: List<BottomNavItem>): Boolean {
    val from = initialState.destination.tab(tabs) ?: return false
    val to = targetState.destination.tab(tabs) ?: return false
    return from != to
}

private fun NavController.openTab(tab: BottomNavItem) = navigate(tab.route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun ShellMessages(deps: AppDependencies, snackbar: SnackbarHostState) {
    val online = LocalIsOnline.current
    var wasOnline by remember { mutableStateOf(online) }
    LaunchedEffect(online) {
        if (online && !wasOnline) snackbar.showSnackbar(getString(Res.string.back_online))
        wasOnline = online
    }
    // Collected in one long-lived effect: consuming the message must not cancel the snackbar it shows.
    LaunchedEffect(deps.events) {
        deps.events.message.filterNotNull().collect { m ->
            deps.events.consume(m)
            when (m) {
                is AppMessage.TrialStarted -> snackbar.showSnackbar(getString(Res.string.onboarding_trial_started, m.days))
                AppMessage.PasswordUpdated -> snackbar.showSnackbar(getString(Res.string.auth_password_updated))
            }
        }
    }
}

/** Phase 5 scaffold: a link opens its tab; the detail screens come with Phases 6–10. */
@Composable
private fun ShellDeepLinks(deps: AppDependencies, nav: NavController, tabs: List<BottomNavItem>, features: List<AppFeature>, host: FeatureHost) {
    val pending by deps.deepLinks.pending.collectAsState()
    LaunchedEffect(pending) {
        val link = pending ?: return@LaunchedEffect
        val tabKey = when (link) {
            is DeepLink.Enquiry -> "enquiries"
            is DeepLink.Customer, is DeepLink.Policy, is DeepLink.Dues -> "customers"
            DeepLink.Website, DeepLink.Domain -> "website"
            is DeepLink.AuthCallback -> null
        }
        if (tabKey != null) {
            tabs.firstOrNull { it.key == tabKey }?.let { nav.openTab(it) }
            // Then the feature that owns the link opens its screen (enquiry, customer, policy, dues bucket).
            features.any { it.openDeepLink(link, host) }
            deps.deepLinks.consume(link)
        }
    }
}
