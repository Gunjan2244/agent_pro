package com.sledje.agentpro.feature.website.data

import com.sledje.agentpro.core.catalog.SiteSchema
import com.sledje.agentpro.feature.settings.data.Profile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The first draft of a new site, filled from the agent's profile so the site can be published straight
 * from onboarding (`publish_site` requires `hero.headline` and `contact.phone`; docs/SHARED_DEFINITIONS.md):
 * - `hero.headline` = the agent's name (cut to the schema's maxLength),
 * - `about.designation` = the profile designation (when set),
 * - `contact.phone` = the profile mobile.
 * Every other field starts empty; the agent edits it later in Website → Content.
 * The section and field keys are checked against the generated [SiteSchema] by StarterContentTest.
 */
object StarterContent {
    const val HERO = "hero"
    const val HEADLINE = "headline"
    const val ABOUT = "about"
    const val DESIGNATION = "designation"
    const val CONTACT = "contact"
    const val PHONE = "phone"

    fun from(profile: Profile): JsonObject = buildJsonObject {
        put(HERO, buildJsonObject { profile.fullName?.trim()?.takeIf { it.isNotEmpty() }?.let { put(HEADLINE, it.take(maxLength(HERO, HEADLINE))) } })
        profile.designation?.trim()?.takeIf { it.isNotEmpty() }?.let { designation ->
            put(ABOUT, buildJsonObject { put(DESIGNATION, designation.take(maxLength(ABOUT, DESIGNATION))) })
        }
        put(CONTACT, buildJsonObject { profile.phone?.trim()?.takeIf { it.isNotEmpty() }?.let { put(PHONE, it) } })
    }

    private fun maxLength(section: String, field: String): Int =
        SiteSchema.section(section)?.fields?.firstOrNull { it.key == field }?.maxLength ?: Int.MAX_VALUE
}
