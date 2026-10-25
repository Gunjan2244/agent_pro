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
import com.sledje.agentpro.feature.offers.ui.OfferPickerActions
import com.sledje.agentpro.feature.offers.ui.OfferPickerScreen
import com.sledje.agentpro.feature.offers.ui.OfferPickerViewModel
import com.sledje.agentpro.feature.offers.ui.PickerEvent
import com.sledje.agentpro.feature.offers.ui.PickerMode
import com.sledje.agentpro.feature.settings.ProfileRoute
import com.sledje.agentpro.feature.website.WebsiteSectionRoute

/**
 * Offers (flag `offers`; DECISIONS #104): the offer picker that Website → Content opens for `offers.items` and
 * `hero.featuredOffer`. Company links are managed in Settings → Profile (`feature/offers/ui/CompaniesUi`).
 */
object OffersFeature : AppFeature {
    override val flag = FeatureId.Offers

    override fun NavGraphBuilder.routes(host: FeatureHost) {
        composable<OfferPickerRoute> { entry -> PickerRoute(host, entry) }
    }
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
