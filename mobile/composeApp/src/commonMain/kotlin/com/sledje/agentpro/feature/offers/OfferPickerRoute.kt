package com.sledje.agentpro.feature.offers

import kotlinx.serialization.Serializable

/** Opens the offer picker for a site field: `offers`/`items` (offerList) or `hero`/`featuredOffer` (offerRef). */
@Serializable
data class OfferPickerRoute(val section: String, val field: String)
