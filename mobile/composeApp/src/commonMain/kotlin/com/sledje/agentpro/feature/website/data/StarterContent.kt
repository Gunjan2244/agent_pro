package com.sledje.agentpro.feature.website.data

import com.sledje.agentpro.core.catalog.SeedValue
import com.sledje.agentpro.core.catalog.SiteSchema
import com.sledje.agentpro.core.catalog.SiteSeed
import com.sledje.agentpro.core.catalog.TemplateSpec
import com.sledje.agentpro.core.forms.FieldSpec
import com.sledje.agentpro.core.forms.FieldType
import com.sledje.agentpro.core.forms.Validators
import com.sledje.agentpro.feature.settings.data.Profile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The first draft of a new site (DECISIONS #78), built from the generated [SiteSeed] (`shared/site-seed.json`)
 * and the agent's profile, so the site can be previewed and published straight from onboarding:
 * - text variants are picked deterministically from the agent's user id ([SiteSeed.pick]) and filled with the
 *   profile's name, first name, city and designation; a text that needs a blank city or designation uses its
 *   fallback ([SiteSeed.fill]);
 * - every text is cut to its schema field's `maxLength`; blank values are left out (as `SiteContent.withSection` does);
 * - list items missing a required item field are dropped and lists are cut to `maxItems`, so the draft passes
 *   the publish rules whenever the profile has a name and a mobile;
 * - `sectionOrder` is the template's section order and `hidden` is [SiteSeed.hiddenSections] for its family
 *   (achievements, testimonials, and offers unless the family is `offers`).
 *
 * Testimonials, achievements, years of experience and clients served are never seeded (no invented social proof).
 * The section and field keys are checked against the generated [SiteSchema] by StarterContentTest.
 */
object StarterContent {
    const val HERO = "hero"
    const val HEADLINE = "headline"
    const val ABOUT = "about"
    const val BIO = "bio"
    const val DESIGNATION = "designation"
    const val CONTACT = "contact"
    const val PHONE = "phone"

    /**
     * @param locale the site's language (the app's language at creation); an unknown one falls back to the default.
     * @param agentId the signed-in user's id; it only seeds the variant choice, so it never changes for an agent.
     */
    fun from(profile: Profile, template: TemplateSpec, locale: String, agentId: String): JsonObject {
        val siteLocale = if (locale in SiteSeed.LOCALES) locale else SiteSeed.DEFAULT_LOCALE
        val ctx = Context(profile, agentId, siteLocale, placeholderValues(profile))
        val content = linkedMapOf<String, JsonElement>()
        SiteSeed.sections.forEach { seed ->
            val schema = SiteSchema.section(seed.key) ?: return@forEach
            val fields = linkedMapOf<String, JsonElement>()
            schema.fields.forEach { spec ->
                val value = seed.fields[spec.key] ?: return@forEach
                val json = if (value is SeedValue.Items) itemsJson(ctx, seed.key, spec, value) else valueJson(ctx, spec, value, SiteSeed.salt(seed.key, spec.key))
                if (json != null) fields[spec.key] = json
            }
            if (fields.isNotEmpty()) content[seed.key] = JsonObject(fields)
        }
        content[SiteContent.SECTION_ORDER] = JsonArray(template.sections.map(::JsonPrimitive))
        content[SiteContent.HIDDEN] = JsonArray(SiteSeed.hiddenSections(template.family.key).map(::JsonPrimitive))
        return JsonObject(content)
    }

    /** The `{name}`, `{firstName}`, `{city}` and `{designation}` values; blank ones are left out. */
    fun placeholderValues(profile: Profile): Map<String, String> {
        val name = profile.fullName?.trim().orEmpty()
        return listOf(
            "name" to name,
            "firstName" to name.split(Regex("\\s+")).firstOrNull().orEmpty(),
            "city" to profile.city?.trim().orEmpty(),
            "designation" to profile.designation?.trim().orEmpty(),
        ).filter { it.second.isNotEmpty() }.toMap()
    }

    /**
     * Whether [bio] (the draft's `about.bio`) is the agent's own text: not blank, and not any seeded bio variant
     * in any language. A seeded bio stays recognised after the profile's city or designation changes, because
     * the placeholders match any text. This drives Home's "Make your About section your own".
     */
    fun isOwnBio(bio: String?): Boolean {
        val text = bio?.trim().orEmpty()
        return text.isNotEmpty() && seededBioPatterns.none { it.matches(text) }
    }

    private val seededBioPatterns: List<Regex> by lazy {
        val bio = SiteSeed.section(ABOUT)?.fields?.get(BIO) as? SeedValue.Variants ?: return@lazy emptyList()
        bio.variants.flatMap { variant ->
            SiteSeed.LOCALES.flatMap { locale ->
                listOfNotNull(variant.text, variant.fallback).map { patternOf(it.raw(locale)) }
            }
        }
    }

    private fun patternOf(raw: String): Regex {
        val parts = raw.split(Regex("\\{[A-Za-z0-9_]+\\}")).joinToString(".*") { Regex.escape(it) }
        return Regex(parts, RegexOption.DOT_MATCHES_ALL)
    }

    private class Context(val profile: Profile, val agentId: String, val locale: String, val values: Map<String, String>)

    private fun valueJson(ctx: Context, spec: FieldSpec, value: SeedValue, salt: String): JsonElement? = when (value) {
        is SeedValue.Variants -> SiteSeed.resolve(value, ctx.agentId, salt, ctx.locale, ctx.values)?.let { textJson(spec, it) }
        is SeedValue.FromProfile -> profileValue(ctx.profile, value.field)?.let { textJson(spec, it, validate = true) }
        is SeedValue.Literal -> value.value
        is SeedValue.ByLocale -> value.value(ctx.locale)
        is SeedValue.Items -> null
    }

    private fun itemsJson(ctx: Context, section: String, list: FieldSpec, items: SeedValue.Items): JsonElement? {
        val out = items.items.mapIndexedNotNull { index, item ->
            val fields = linkedMapOf<String, JsonElement>()
            list.itemFields.forEach { spec ->
                val value = item[spec.key] ?: return@forEach
                valueJson(ctx, spec, value, SiteSeed.salt(section, list.key, index, spec.key))?.let { fields[spec.key] = it }
            }
            // An item missing a required field would fail publish; leave it out.
            if (list.itemFields.any { it.required && it.key !in fields }) null else JsonObject(fields)
        }.let { if (list.maxItems != null) it.take(list.maxItems) else it }
        return out.takeIf { it.isNotEmpty() }?.let(::JsonArray)
    }

    /** A trimmed text cut to the field's maxLength; null when blank (or, with [validate], when not a valid phone or email). */
    private fun textJson(spec: FieldSpec, text: String, validate: Boolean = false): JsonElement? {
        val trimmed = text.trim().let { if (spec.maxLength != null) it.take(spec.maxLength).trimEnd() else it }
        if (trimmed.isEmpty()) return null
        if (validate) {
            val error = when (spec.type) {
                FieldType.Phone -> Validators.phone(trimmed)
                FieldType.Email -> Validators.email(trimmed)
                else -> null
            }
            if (error != null) return null
        }
        return JsonPrimitive(trimmed)
    }

    private fun profileValue(profile: Profile, field: String): String? = when (field) {
        "fullName" -> profile.fullName
        "firstName" -> placeholderValues(profile)["firstName"]
        "phone" -> profile.phone
        "email" -> profile.email
        "designation" -> profile.designation
        "city" -> profile.city
        else -> null
    }?.trim()?.takeIf { it.isNotEmpty() }
}
