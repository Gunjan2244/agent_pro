package com.sledje.agentpro.feature.policies

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.sledje.agentpro.app.DeepLink
import com.sledje.agentpro.core.catalog.FeatureId
import com.sledje.agentpro.core.features.AppFeature
import com.sledje.agentpro.core.features.FeatureHost
import com.sledje.agentpro.core.format.Dates
import com.sledje.agentpro.feature.customers.CustomerDetailRoute
import com.sledje.agentpro.feature.policies.ui.PolicyDetailScreen
import com.sledje.agentpro.feature.policies.ui.PolicyDetailViewModel
import com.sledje.agentpro.feature.policies.ui.PolicyFormScreen
import com.sledje.agentpro.feature.policies.ui.PolicyFormViewModel
import kotlinx.serialization.Serializable

/** Add ([policyId] null) or edit a policy of [customerId]. */
@Serializable data class PolicyFormRoute(val customerId: String, val policyId: String? = null)

@Serializable data class PolicyDetailRoute(val id: String)

/**
 * Policies (Phase 7; docs/UX.md, Policy form and Policy detail): no tab of its own; reached from a
 * customer's Policies tab and from Dues. Recording a premium goes through `mark_premium_paid` with a
 * 5-second Undo (`undo_premium_payment`, latest payment only; DECISIONS #31).
 */
object PoliciesFeature : AppFeature {
    override val flag = FeatureId.Policies

    override fun NavGraphBuilder.routes(host: FeatureHost) {
        val deps = host.deps
        composable<PolicyFormRoute> { entry ->
            val route = entry.toRoute<PolicyFormRoute>()
            val vm = viewModel(key = "policy_form_${route.customerId}_${route.policyId}") {
                PolicyFormViewModel(route.customerId, route.policyId, deps.policies, today = { Dates.today() })
            }
            PolicyFormScreen(
                vm = vm,
                onBack = { host.nav.popBackStack() },
                onSaved = { id ->
                    host.nav.popBackStack()
                    // A new policy opens its detail screen; an edit returns to the detail it came from.
                    if (route.policyId == null) host.nav.navigate(PolicyDetailRoute(id))
                },
            )
        }
        composable<PolicyDetailRoute> { entry ->
            val id = entry.toRoute<PolicyDetailRoute>().id
            val vm = viewModel(key = "policy_$id") { PolicyDetailViewModel(id, deps.policies, deps.customers) }
            PolicyDetailScreen(
                vm = vm,
                onBack = { host.nav.popBackStack() },
                onEdit = { customerId, policyId -> host.nav.navigate(PolicyFormRoute(customerId, policyId)) },
                onOpenCustomer = { host.nav.navigate(CustomerDetailRoute(it)) },
            )
        }
    }

    override fun openDeepLink(link: DeepLink, host: FeatureHost): Boolean {
        if (link !is DeepLink.Policy) return false
        host.nav.navigate(PolicyDetailRoute(link.id))
        return true
    }
}
