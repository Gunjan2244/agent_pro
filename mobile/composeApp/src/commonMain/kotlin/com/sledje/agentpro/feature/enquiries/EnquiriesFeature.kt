package com.sledje.agentpro.feature.enquiries

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.sledje.agentpro.app.DeepLink
import com.sledje.agentpro.core.catalog.FeatureId
import com.sledje.agentpro.core.features.AppFeature
import com.sledje.agentpro.core.features.BottomNavItem
import com.sledje.agentpro.core.features.FeatureHost
import com.sledje.agentpro.feature.customers.CustomerDetailRoute
import com.sledje.agentpro.feature.customers.CustomerFormRoute
import com.sledje.agentpro.feature.enquiries.ui.AddEnquiryScreen
import com.sledje.agentpro.feature.privacy.ui.CustomerDutyGateHost
import com.sledje.agentpro.feature.enquiries.ui.AddEnquiryViewModel
import com.sledje.agentpro.feature.enquiries.ui.EnquiryDetailScreen
import com.sledje.agentpro.feature.enquiries.ui.EnquiryDetailViewModel
import com.sledje.agentpro.feature.enquiries.ui.EnquiryListScreen
import com.sledje.agentpro.feature.enquiries.ui.EnquiryListViewModel
import com.sledje.agentpro.feature.enquiries.ui.NotesScreen
import com.sledje.agentpro.feature.enquiries.ui.NotesViewModel
import com.sledje.agentpro.feature.website.data.SiteLinks
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.tab_enquiries
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable data object EnquiriesRoute

@Serializable data class EnquiryDetailRoute(val id: String)

@Serializable data object AddEnquiryRoute

@Serializable data class EnquiryNotesRoute(val id: String)

/**
 * The Enquiries tab (Phase 6; docs/UX.md): list with status chips, search and sort; detail (status,
 * follow-up, notes, Call · SMS · Email, Copy details, Delete with Undo); Add enquiry; Convert to
 * customer (the customer form, registered by CustomersFeature). The tab badge counts unseen enquiries.
 */
object EnquiriesFeature : AppFeature {
    override val flag = FeatureId.Enquiries

    override val bottomNavItem = BottomNavItem(
        "enquiries", EnquiriesRoute, Res.string.tab_enquiries, FeatureId.Enquiries.icon, order = 2,
        badge = { host -> unseenCount(host) },
    )

    override fun NavGraphBuilder.routes(host: FeatureHost) {
        val deps = host.deps
        composable<EnquiriesRoute> {
            val vm = viewModel { EnquiryListViewModel(deps.enquiries, deps.store) }
            val site by remember { deps.sites.observeMine() }.collectAsState(null)
            val slug = site?.slug
            EnquiryListScreen(
                vm = vm,
                onOpen = { host.nav.navigate(EnquiryDetailRoute(it)) },
                onAdd = { host.nav.navigate(AddEnquiryRoute) },
                onShareLink = slug?.let { s -> { deps.urlLauncher.share(SiteLinks.url(s)); Unit } },
            )
        }
        composable<EnquiryDetailRoute> { entry ->
            val id = entry.toRoute<EnquiryDetailRoute>().id
            val vm = viewModel(key = "enquiry_$id") { EnquiryDetailViewModel(id, deps.enquiries, deps.customers) }
            EnquiryDetailScreen(
                vm = vm,
                onBack = { host.nav.popBackStack() },
                onConvert = { host.nav.navigate(CustomerFormRoute(fromEnquiryId = it)) },
                onViewCustomer = { host.nav.navigate(CustomerDetailRoute(it)) },
                onOpenNotes = { host.nav.navigate(EnquiryNotesRoute(it)) },
                onCall = deps.urlLauncher::call,
                onSms = deps.urlLauncher::sms,
                onEmail = deps.urlLauncher::email,
            )
        }
        composable<AddEnquiryRoute> {
            val vm = viewModel { AddEnquiryViewModel(deps.enquiries) }
            AddEnquiryScreen(
                vm = vm,
                onBack = { host.nav.popBackStack() },
                dutyGate = { snackbar -> CustomerDutyGateHost(deps, snackbar, onDeclined = { host.nav.popBackStack() }) },
                onSaved = { id ->
                    host.nav.popBackStack()
                    host.nav.navigate(EnquiryDetailRoute(id))
                },
            )
        }
        composable<EnquiryNotesRoute> { entry ->
            val id = entry.toRoute<EnquiryNotesRoute>().id
            val vm = viewModel(key = "notes_$id") { NotesViewModel(id, deps.enquiries) }
            NotesScreen(vm, onBack = { host.nav.popBackStack() })
        }
    }

    override fun openDeepLink(link: DeepLink, host: FeatureHost): Boolean {
        if (link !is DeepLink.Enquiry) return false
        host.nav.navigate(EnquiryDetailRoute(link.id))
        return true
    }

    @Composable
    private fun unseenCount(host: FeatureHost): Int {
        val count by remember(host.deps) { host.deps.enquiries.observeAll().map { all -> all.count { it.unseen } } }.collectAsState(0)
        return count
    }
}
