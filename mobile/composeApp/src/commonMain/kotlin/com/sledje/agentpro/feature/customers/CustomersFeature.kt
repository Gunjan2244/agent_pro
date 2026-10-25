package com.sledje.agentpro.feature.customers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
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
import com.sledje.agentpro.core.features.FeatureRegistry
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
import com.sledje.agentpro.feature.customers.data.DraftSource
import com.sledje.agentpro.feature.customers.ui.IncompleteFormsScreen
import com.sledje.agentpro.feature.customers.ui.IncompleteFormsViewModel
import com.sledje.agentpro.feature.customers.ui.SignupLiveScreen
import com.sledje.agentpro.feature.customers.ui.SignupLiveViewModel
import com.sledje.agentpro.feature.customers.ui.SignupStartContent
import com.sledje.agentpro.feature.customers.ui.SignupStartState
import com.sledje.agentpro.feature.customers.ui.SignupStartViewModel
import com.sledje.agentpro.feature.privacy.data.PrivacyLinks
import com.sledje.agentpro.feature.privacy.ui.CustomerDutyGate
import com.sledje.agentpro.feature.privacy.ui.CustomerDutyGateHost
import com.sledje.agentpro.feature.privacy.ui.CustomerDutyViewModel
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
    /** Resumes an unfinished form from Incomplete forms (DECISIONS #108). */
    val draftId: String? = null,
)

/** Incomplete forms: the agent's unfinished customer forms and the open sign-up links. */
@Serializable data object IncompleteFormsRoute

/** "Send the customer a link" was chosen: makes the link, then replaces itself with [CustomerSignupLiveRoute]. */
@Serializable data object CustomerSignupStartRoute

/** One sign-up link, live. [token] is only known right after it was made (it is never stored). */
@Serializable data class CustomerSignupLiveRoute(val draftId: String, val token: String? = null)

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
            val items = remember(flags) { FeatureRegistry.enabled(flags).flatMap { it.customerDetailItems }.sortedBy { it.order } }
            CustomerDetailScreen(
                vm = vm,
                showPolicies = flags.isEnabled(FeatureId.Policies),
                onBack = { host.nav.popBackStack() },
                onEdit = { host.nav.navigate(CustomerFormRoute(id = it)) },
                onAddPolicy = { host.nav.navigate(PolicyFormRoute(customerId = it)) },
                onOpenPolicy = { host.nav.navigate(PolicyDetailRoute(it)) },
                onCall = deps.urlLauncher::call,
                onSms = deps.urlLauncher::sms,
                detailExtras = { customerId ->
                    items.forEach { item ->
                        key(item.key) {
                            item.content(host, customerId)
                            Spacer(Modifier.height(Spacing.s))
                        }
                    }
                },
            )
        }
        signupRoutes(host)
        composable<CustomerFormRoute> { entry ->
            val route = entry.toRoute<CustomerFormRoute>()
            val mode = when {
                route.id != null -> CustomerFormMode.Edit(route.id)
                route.fromEnquiryId != null -> CustomerFormMode.Convert(route.fromEnquiryId)
                else -> CustomerFormMode.Add(route.name, route.phone, route.draftId)
            }
            val vm = viewModel(key = "customer_form_$mode") {
                CustomerFormViewModel(mode, deps.customers, deps.enquiries, deps.consents, drafts = deps.customerDrafts)
            }
            CustomerFormScreen(
                vm = vm,
                onBack = { host.nav.popBackStack() },
                // Adding a customer (manually, from contacts or from an enquiry) needs the one-time attestation.
                dutyGate = if (mode !is CustomerFormMode.Edit) {
                    { snackbar -> CustomerDutyGateHost(deps, snackbar, onDeclined = { host.nav.popBackStack() }) }
                } else {
                    null
                },
                onSaved = { id ->
                    host.nav.popBackStack()
                    // Edit returns to the detail screen it came from; add and convert open the new customer.
                    if (mode !is CustomerFormMode.Edit) host.nav.navigate(CustomerDetailRoute(id))
                },
            )
        }
    }

    private fun NavGraphBuilder.signupRoutes(host: FeatureHost) {
        val deps = host.deps
        composable<IncompleteFormsRoute> {
            val vm = viewModel { IncompleteFormsViewModel(deps.customerDrafts) }
            IncompleteFormsScreen(
                vm = vm,
                onBack = { host.nav.popBackStack() },
                onOpen = { draft ->
                    if (draft.source == DraftSource.Agent) {
                        host.nav.navigate(CustomerFormRoute(draftId = draft.id))
                    } else {
                        host.nav.navigate(CustomerSignupLiveRoute(draft.id))
                    }
                },
            )
        }
        composable<CustomerSignupStartRoute> {
            val vm = viewModel { SignupStartViewModel(deps.customerDrafts) }
            val state by vm.state.collectAsState()
            (state as? SignupStartState.Created)?.let { created ->
                LaunchedEffect(created) {
                    host.nav.navigate(CustomerSignupLiveRoute(created.draftId, created.token)) {
                        popUpTo<CustomerSignupStartRoute> { inclusive = true }
                    }
                }
            }
            SignupStartContent(state, onBack = { host.nav.popBackStack() }, onRetry = vm::create)
        }
        composable<CustomerSignupLiveRoute> { entry ->
            val route = entry.toRoute<CustomerSignupLiveRoute>()
            val vm = viewModel(key = "signup_live_${route.draftId}") {
                SignupLiveViewModel(route.draftId, route.token, deps.customerDrafts, deps.sites.observeMine())
            }
            SignupLiveScreen(
                vm = vm,
                onBack = { host.nav.popBackStack() },
                onShare = deps.urlLauncher::share,
                onOpenCustomer = { id ->
                    host.nav.navigate(CustomerDetailRoute(id)) { popUpTo<CustomerSignupLiveRoute> { inclusive = true } }
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
    val vm = viewModel { CustomerListViewModel(deps.customers, deps.policies, deps.store, today = { Dates.today() }, drafts = deps.customerDrafts) }
    val pick = deps.contactPicker.rememberContactPicker { contact ->
        if (contact != null) host.nav.navigate(CustomerFormRoute(name = contact.name, phone = contact.phone))
    }
    val flags by deps.featureFlags.collectAsState()
    // "Send the customer a link" first needs the one-time customer-data attestation (docs/PRIVACY.md, section 3).
    val duty = viewModel { CustomerDutyViewModel(deps.consents) }
    val dutyState by duty.state.collectAsState()
    val site by deps.sites.observeMine().collectAsState(null)
    var linkRequested by remember { mutableStateOf(false) }
    LaunchedEffect(linkRequested, dutyState.accepted) {
        if (linkRequested && dutyState.accepted == true) {
            linkRequested = false
            host.nav.navigate(CustomerSignupStartRoute)
        }
    }
    if (linkRequested) {
        CustomerDutyGate(
            vm = duty,
            privacyUrl = site?.slug?.let { PrivacyLinks.sitePrivacy(it) },
            onShare = deps.urlLauncher::share,
            onDeclined = { linkRequested = false },
        )
    }
    CustomerListScreen(
        vm = vm,
        onOpen = { host.nav.navigate(CustomerDetailRoute(it)) },
        onAddManually = { host.nav.navigate(CustomerFormRoute()) },
        onPickContact = pick,
        onSendLink = if (flags.isEnabled(FeatureId.CustomerSignup)) ({ linkRequested = true }) else null,
        onOpenIncomplete = { host.nav.navigate(IncompleteFormsRoute) },
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
