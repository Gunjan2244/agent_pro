package com.sledje.agentpro.feature.customers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.sledje.agentpro.app.DeepLink
import com.sledje.agentpro.core.catalog.DueBucket
import com.sledje.agentpro.core.catalog.FeatureId
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.SegmentOption
import com.sledje.agentpro.core.designsystem.components.SegmentedControl
import com.sledje.agentpro.core.features.AppFeature
import com.sledje.agentpro.core.features.BottomNavItem
import com.sledje.agentpro.core.features.FeatureHost
import com.sledje.agentpro.core.features.TabSegment
import com.sledje.agentpro.core.format.Dates
import com.sledje.agentpro.domain.DueBuckets
import com.sledje.agentpro.feature.customers.ui.CustomerDetailScreen
import com.sledje.agentpro.feature.customers.ui.CustomerDetailViewModel
import com.sledje.agentpro.feature.customers.ui.CustomerFormMode
import com.sledje.agentpro.feature.customers.ui.CustomerFormScreen
import com.sledje.agentpro.feature.customers.ui.CustomerFormViewModel
import com.sledje.agentpro.feature.customers.ui.CustomerListScreen
import com.sledje.agentpro.feature.customers.ui.CustomerListViewModel
import com.sledje.agentpro.feature.home.TabScreen
import com.sledje.agentpro.feature.policies.PolicyDetailRoute
import com.sledje.agentpro.feature.policies.PolicyFormRoute
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.segment_customers
import com.sledje.agentpro.resources.tab_customers
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource

/** The Customers tab. [segment] and [bucket] come from `agentpro://dues?bucket=` (opens the Dues segment). */
@Serializable data class CustomersRoute(val segment: String? = null, val bucket: String? = null)

@Serializable data class CustomerDetailRoute(val id: String)

/**
 * The customer form: add ([name]/[phone] prefilled from the contact picker), edit ([id]) or convert an
 * enquiry ([fromEnquiryId]).
 */
@Serializable
data class CustomerFormRoute(
    val id: String? = null,
    val fromEnquiryId: String? = null,
    val name: String? = null,
    val phone: String? = null,
)

/** The argument a tab passes to its segments (the Dues bucket from a deep link). */
val LocalSegmentArgument = compositionLocalOf<String?> { null }

/**
 * The Customers tab with its `[ Customers | Dues ]` segments (DECISIONS #18), customer detail and the
 * customer form (Phase 7). The segments come from the enabled features (`TabSegment` with tab key
 * [TAB_KEY]); with one segment no control shows. The tab badge counts customers with an overdue premium.
 */
object CustomersFeature : AppFeature {
    const val TAB_KEY = "customers"
    const val SEGMENT_KEY = "customers"

    override val flag = FeatureId.Customers

    override val bottomNavItem = BottomNavItem(
        TAB_KEY, CustomersRoute(), Res.string.tab_customers, FeatureId.Customers.icon, order = 3,
        badge = { host -> overdueCount(host) },
    )

    override val segments = listOf(
        TabSegment(TAB_KEY, SEGMENT_KEY, Res.string.segment_customers, order = 1) { host -> CustomersSegment(host) },
    )

    override fun NavGraphBuilder.routes(host: FeatureHost) {
        val deps = host.deps
        composable<CustomersRoute> { entry ->
            val route = entry.toRoute<CustomersRoute>()
            CustomersTab(host.segmentsFor(TAB_KEY), host, initialSegment = route.segment, segmentArgument = route.bucket)
        }
        composable<CustomerDetailRoute> { entry ->
            val id = entry.toRoute<CustomerDetailRoute>().id
            val vm = viewModel(key = "customer_$id") {
                CustomerDetailViewModel(id, deps.customers, deps.policies, deps.enquiries, dateOf = Dates::dateOf)
            }
            val flags by deps.featureFlags.collectAsState()
            CustomerDetailScreen(
                vm = vm,
                showPolicies = flags.isEnabled(FeatureId.Policies),
                onBack = { host.nav.popBackStack() },
                onEdit = { host.nav.navigate(CustomerFormRoute(id = it)) },
                onAddPolicy = { host.nav.navigate(PolicyFormRoute(customerId = it)) },
                onOpenPolicy = { host.nav.navigate(PolicyDetailRoute(it)) },
                onCall = deps.urlLauncher::call,
                onSms = deps.urlLauncher::sms,
            )
        }
        composable<CustomerFormRoute> { entry ->
            val route = entry.toRoute<CustomerFormRoute>()
            val mode = when {
                route.id != null -> CustomerFormMode.Edit(route.id)
                route.fromEnquiryId != null -> CustomerFormMode.Convert(route.fromEnquiryId)
                else -> CustomerFormMode.Add(route.name, route.phone)
            }
            val vm = viewModel(key = "customer_form_$mode") { CustomerFormViewModel(mode, deps.customers, deps.enquiries) }
            CustomerFormScreen(
                vm = vm,
                onBack = { host.nav.popBackStack() },
                onSaved = { id ->
                    host.nav.popBackStack()
                    // Edit returns to the detail screen it came from; add and convert open the new customer.
                    if (mode !is CustomerFormMode.Edit) host.nav.navigate(CustomerDetailRoute(id))
                },
            )
        }
    }

    override fun openDeepLink(link: DeepLink, host: FeatureHost): Boolean = when (link) {
        is DeepLink.Customer -> { host.nav.navigate(CustomerDetailRoute(link.id)); true }
        is DeepLink.Dues -> {
            host.nav.navigate(CustomersRoute(segment = DUES_SEGMENT_KEY, bucket = link.bucket)) { launchSingleTop = true }
            true
        }
        else -> false
    }

    /** The Dues feature's segment key (kept here so a deep link can open it without depending on it). */
    const val DUES_SEGMENT_KEY = "dues"

    @Composable
    private fun overdueCount(host: FeatureHost): Int {
        val deps = host.deps
        val count by remember(deps) {
            combine(deps.customers.observeAll(), deps.policies.observeAll()) { customers, policies ->
                val today = Dates.today()
                CustomerListViewModel.rows(customers, policies)
                    .count { row -> row.nextDue?.let { DueBuckets.bucketOf(it, today) } == DueBucket.Overdue }
            }
        }.collectAsState(0)
        return count
    }
}

@Composable
private fun CustomersSegment(host: FeatureHost) {
    val deps = host.deps
    val vm = viewModel { CustomerListViewModel(deps.customers, deps.policies, deps.store, today = { Dates.today() }) }
    val pick = deps.contactPicker.rememberContactPicker { contact ->
        if (contact != null) host.nav.navigate(CustomerFormRoute(name = contact.name, phone = contact.phone))
    }
    CustomerListScreen(
        vm = vm,
        onOpen = { host.nav.navigate(CustomerDetailRoute(it)) },
        onAddManually = { host.nav.navigate(CustomerFormRoute()) },
        onPickContact = pick,
    )
}

@Composable
fun CustomersTab(segments: List<TabSegment>, host: FeatureHost?, initialSegment: String? = null, segmentArgument: String? = null) {
    var selected by rememberSaveable(initialSegment) { mutableStateOf(initialSegment ?: segments.firstOrNull()?.key.orEmpty()) }
    TabScreen(Res.string.tab_customers) {
        Column {
            if (segments.size > 1) {
                SegmentedControl(
                    options = segments.map { SegmentOption(it.key, stringResource(it.label)) },
                    selectedKey = selected,
                    onSelect = { selected = it },
                    modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
                )
            }
            val segment = segments.firstOrNull { it.key == selected } ?: segments.firstOrNull()
            if (segment != null && host != null) {
                CompositionLocalProvider(LocalSegmentArgument provides segmentArgument) { segment.content(host) }
            }
        }
    }
}
