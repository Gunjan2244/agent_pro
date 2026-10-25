package com.sledje.agentpro.feature.portal

import androidx.navigation.NavGraphBuilder
import com.sledje.agentpro.core.catalog.FeatureId
import com.sledje.agentpro.core.features.AppFeature
import com.sledje.agentpro.core.features.CustomerDetailItem
import com.sledje.agentpro.feature.portal.ui.WebsiteLoginCard

/**
 * Customer portal, agent side (flag `customerPortal`): the "Website login" card on the customer detail's
 * Details tab. It has no routes of its own.
 */
object PortalFeature : AppFeature {
    override val flag = FeatureId.CustomerPortal

    override fun NavGraphBuilder.routes(host: com.sledje.agentpro.core.features.FeatureHost) = Unit

    override val customerDetailItems = listOf(
        CustomerDetailItem("website_login", order = 1) { host, customerId -> WebsiteLoginCard(host, customerId) },
    )
}
