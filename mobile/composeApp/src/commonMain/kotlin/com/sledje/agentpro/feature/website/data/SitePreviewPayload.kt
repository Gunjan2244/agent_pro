package com.sledje.agentpro.feature.website.data

import com.sledje.agentpro.core.catalog.Templates
import com.sledje.agentpro.core.data.RowJson
import com.sledje.agentpro.feature.settings.data.Profile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The `window.renderSite(json)` input for the in-app preview (docs/WEB_RENDERER.md, Templates):
 * `{content, template_key, accent, locale, profile}`, shaped like the public payload of
 * `get_public_site` (docs/BACKEND.md, Public payload) but with the **unsaved draft** as `content`.
 * `profile` carries only the public fields; `agency_code` only when `about.showAgencyCode` is on.
 */
object SitePreviewPayload {
    fun build(
        site: Site,
        profile: Profile?,
        content: JsonObject = site.draftContent,
        templateKey: String = site.templateKey,
        offers: JsonObject? = null,
        company: JsonObject? = null,
        companies: JsonArray? = null,
        portalEnabled: Boolean = false,
    ): JsonObject {
        val accent = site.accent?.key ?: Templates.fromKey(templateKey)?.defaultAccent?.key
        val showAgencyCode = ((content["about"] as? JsonObject)?.get("showAgencyCode") as? JsonPrimitive)?.booleanOrNull == true
        // Customer accounts on the site (portal contract F7): the `customerPortal` flag AND the agent's own switch (default on).
        val showLogin = ((content["contact"] as? JsonObject)?.get("showCustomerLogin") as? JsonPrimitive)?.booleanOrNull != false
        return buildJsonObject {
            put("content", content)
            put("portal", portalEnabled && showLogin)
            put("template_key", templateKey)
            put("accent", accent)
            put("locale", site.locale)
            // The real offers and the company disclaimer, when `preview_site_offers` answered (DECISIONS #84).
            if (offers != null) put("offers", offers)
            if (company != null) put("company", company)
            // The agent's approved companies, `[{name, insurance_types}]` (DECISIONS #90), for the "Companies I represent" section.
            if (companies != null) put("companies", companies)
            put(
                "profile",
                buildJsonObject {
                    profile?.fullName?.let { put("full_name", it) }
                    profile?.designation?.let { put("designation", it) }
                    profile?.city?.let { put("city", it) }
                    profile?.photoUrl?.let { put("photo_url", it) }
                    profile?.phone?.let { put("phone", it) }
                    if (showAgencyCode) profile?.agencyCode?.let { put("agency_code", it) }
                },
            )
        }
    }

    /** The payload with the `preview_site_offers` answer ([previewOffers] = `{companies, company, offers}`) merged in; null = none. */
    fun buildWithPreviewOffers(site: Site, profile: Profile?, content: JsonObject, templateKey: String, previewOffers: JsonObject?, portalEnabled: Boolean = false): JsonObject =
        build(
            site, profile, content, templateKey,
            offers = previewOffers?.get("offers") as? JsonObject,
            company = previewOffers?.get("company") as? JsonObject,
            companies = previewOffers?.get("companies") as? JsonArray,
            portalEnabled = portalEnabled,
        )

    fun json(
        site: Site,
        profile: Profile?,
        content: JsonObject = site.draftContent,
        templateKey: String = site.templateKey,
        previewOffers: JsonObject? = null,
        portalEnabled: Boolean = false,
    ): String = RowJson.encodeToString(JsonObject.serializer(), buildWithPreviewOffers(site, profile, content, templateKey, previewOffers, portalEnabled))
}
