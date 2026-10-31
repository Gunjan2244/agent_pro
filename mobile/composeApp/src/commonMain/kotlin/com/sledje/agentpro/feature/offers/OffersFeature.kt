package com.sledje.agentpro.feature.offers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.sledje.agentpro.core.catalog.FeatureId
import com.sledje.agentpro.core.features.AppFeature
import com.sledje.agentpro.core.features.FeatureHost
import com.sledje.agentpro.core.features.HubEntry
import com.sledje.agentpro.feature.offers.ui.PlansActions
import com.sledje.agentpro.feature.offers.ui.PlansScreen
import com.sledje.agentpro.feature.offers.ui.PlansViewModel
import com.sledje.agentpro.feature.offers.ui.OfferPickerActions
import com.sledje.agentpro.feature.offers.ui.OfferPickerScreen
import com.sledje.agentpro.feature.offers.ui.OfferPickerViewModel
import com.sledje.agentpro.feature.offers.ui.PickerEvent
import com.sledje.agentpro.feature.offers.ui.PickerMode
import com.sledje.agentpro.feature.settings.ProfileRoute
import com.sledje.agentpro.feature.website.WebsiteSectionRoute
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.ic_description
import com.sledje.agentpro.resources.plans_title

/**
 * Offers (flag `offers`; DECISIONS #104): the offer picker that Website → Content opens for `offers.items` and
 * `hero.featuredOffer`. Company links are managed in Settings → Profile (`feature/offers/ui/CompaniesUi`). The Website
 * hub's "Plans on your site" opens the choice of the companies' official plans (DECISIONS #129).
 */
object OffersFeature : AppFeature {
    override val flag = FeatureId.Offers

    override val websiteHubEntries: List<HubEntry> = listOf(
        HubEntry(
            key = "plans",
            label = Res.string.plans_title,
            icon = Res.drawable.ic_description,
            order = 5,
            onClick = { it.nav.navigate(SitePlansRoute) },
        ),
    )

    override fun NavGraphBuilder.routes(host: FeatureHost) {
        composable<OfferPickerRoute> { entry -> PickerRoute(host, entry) }
        composable<SitePlansRoute> { PlansRoute(host) }
    }
}

@Composable
private fun PlansRoute(host: FeatureHost) {
    val vm = viewModel { PlansViewModel(host.deps.plans) }
    val state by vm.state.collectAsState()
    val actions = remember(vm) {
        PlansActions(
            onBack = { host.nav.popBackStack() },
            onRetry = vm::load,
            onAuto = vm::setAuto,
            onToggle = vm::toggle,
            onToggleCompany = vm::toggleCompany,
            onSave = vm::save,
            onSavedShown = vm::consumeSaved,
        )
    }
    PlansScreen(state, actions)
}

@Composable
private fun PickerRoute(host: FeatureHost, entry: NavBackStackEntry) {
    val route = entry.toRoute<OfferPickerRoute>()
    val mode = PickerMode.of(route.section, route.field)
    if (mode == null) {
        LaunchedEffect(Unit) { host.nav.popBackStack() }
        return
    }
    val vm = viewModel { OfferPickerViewModel(mode, host.deps.sites, host.deps.companies) }
    val state by vm.state.collectAsState()
    LaunchedEffect(state.event) {
        when (val e = state.event) {
            is PickerEvent.OpenEditor -> { vm.consumeEvent(); host.nav.navigate(WebsiteSectionRoute("offers")) }
            PickerEvent.Done -> { vm.consumeEvent(); host.nav.popBackStack() }
            null -> Unit
        }
    }
    val actions = remember(vm) {
        OfferPickerActions.of(
            vm,
            onBack = { host.nav.popBackStack() },
            onAddOwn = { host.nav.navigate(WebsiteSectionRoute("offers")) },
            onOpenProfile = { host.nav.navigate(ProfileRoute) },
        )
    }
    OfferPickerScreen(state, actions)
}
