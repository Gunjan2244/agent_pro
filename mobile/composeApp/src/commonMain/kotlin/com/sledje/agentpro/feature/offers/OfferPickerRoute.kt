package com.sledje.agentpro.feature.offers

import kotlinx.serialization.Serializable

/** Opens the offer picker for a site field: `offers`/`items` (offerList) or `hero`/`featuredOffer` (offerRef). */
@Serializable
data class OfferPickerRoute(val section: String, val field: String)

/** Website → Plans on your site: the agent's choice of their companies' official plans (DECISIONS #129). */
@Serializable
data object SitePlansRoute
