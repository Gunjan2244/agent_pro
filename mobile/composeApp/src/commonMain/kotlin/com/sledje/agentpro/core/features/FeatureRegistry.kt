package com.sledje.agentpro.core.features

import com.sledje.agentpro.feature.customers.CustomersFeature
import com.sledje.agentpro.feature.dues.DuesFeature
import com.sledje.agentpro.feature.enquiries.EnquiriesFeature
import com.sledje.agentpro.feature.home.HomeFeature
import com.sledje.agentpro.feature.offers.OffersFeature
import com.sledje.agentpro.feature.policies.PoliciesFeature
import com.sledje.agentpro.feature.portal.PortalFeature
import com.sledje.agentpro.feature.settings.SettingsFeature
import com.sledje.agentpro.feature.website.WebsiteFeature

/**
 * Every feature, one line each (DECISIONS #11; docs/ARCHITECTURE.md, Feature registry). The nav graph,
 * bottom bar, segments, Home cards, Settings and Website hub entries are built from [enabled].
 * Later phases add Policies, Insights, Domains, Billing and Export here.
 */
object FeatureRegistry {
    val all: List<AppFeature> = listOf(
        HomeFeature,
        EnquiriesFeature,
        CustomersFeature,
        DuesFeature,
        PoliciesFeature,
        WebsiteFeature,
        OffersFeature,
        PortalFeature,
        SettingsFeature,
    )

    /** Core features (no flag) are always on; the rest follow [flags]. */
    fun enabled(flags: FeatureFlags): List<AppFeature> = all.filter { feature -> feature.flag?.let(flags::isEnabled) ?: true }
}
