package com.sledje.agentpro.feature.website.data

import com.sledje.agentpro.core.catalog.Templates
import com.sledje.agentpro.core.data.RowJson
import com.sledje.agentpro.feature.settings.data.Profile
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
    ): JsonObject {
        val accent = site.accent?.key ?: Templates.fromKey(templateKey)?.defaultAccent?.key
        val showAgencyCode = ((content["about"] as? JsonObject)?.get("showAgencyCode") as? JsonPrimitive)?.booleanOrNull == true
        return buildJsonObject {
            put("content", content)
            put("template_key", templateKey)
            put("accent", accent)
            put("locale", site.locale)
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

    fun json(site: Site, profile: Profile?, content: JsonObject = site.draftContent, templateKey: String = site.templateKey): String =
        RowJson.encodeToString(JsonObject.serializer(), build(site, profile, content, templateKey))
}
