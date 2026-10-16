package com.sledje.agentpro.feature.dues

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import com.sledje.agentpro.core.catalog.FeatureId
import com.sledje.agentpro.core.features.AppFeature
import com.sledje.agentpro.core.features.FeatureHost
import com.sledje.agentpro.core.features.TabSegment
import com.sledje.agentpro.core.format.Dates
import com.sledje.agentpro.feature.customers.CustomersFeature
import com.sledje.agentpro.feature.customers.LocalSegmentArgument
import com.sledje.agentpro.feature.dues.ui.DuesScreen
import com.sledje.agentpro.feature.dues.ui.DuesViewModel
import com.sledje.agentpro.feature.policies.PolicyDetailRoute
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.segment_dues

/**
 * Dues: a segment of the Customers tab, not a tab (DECISIONS #18). Buckets from catalog `dueBuckets`
 * with ₹ totals; a row opens Policy detail (Record premium paid); Call is the one quick action.
 */
object DuesFeature : AppFeature {
    override val flag = FeatureId.Dues

    override val segments = listOf(
        TabSegment(CustomersFeature.TAB_KEY, CustomersFeature.DUES_SEGMENT_KEY, Res.string.segment_dues, order = 2) { host -> DuesSegment(host) },
    )

    override fun NavGraphBuilder.routes(host: FeatureHost) = Unit
}

@Composable
private fun DuesSegment(host: FeatureHost) {
    val deps = host.deps
    val bucket = LocalSegmentArgument.current
    val vm = viewModel(key = "dues_$bucket") {
        DuesViewModel(deps.policies, deps.customers, deps.store, today = { Dates.today() }, initialBucket = bucket)
    }
    DuesScreen(
        vm = vm,
        onOpenPolicy = { host.nav.navigate(PolicyDetailRoute(it)) },
        onCall = { deps.urlLauncher.call(it) },
    )
}
