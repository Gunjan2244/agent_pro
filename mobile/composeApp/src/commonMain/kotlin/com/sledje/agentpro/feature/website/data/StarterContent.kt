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
 *   (achievements, testimonials, and offers unless the family is `offers`);
 * - the agent's insurance types (DECISIONS #93; [SiteServices.agentTypes]) pick the services ([SiteSeed.servicesFor]:
 *   `services.offered` and one item with its seeded description per interest) and the copy mix ([SiteSeed.mixFor]: the
 *   life-and-family texts when every type is life, the general ones otherwise). No types means [SiteSeed.WHEN_NONE].
 *
 * Testimonials, achievements, years of experience and clients served are never seeded (no invented social proof).
 * The section and field keys are checked against the generated [SiteSchema] by StarterContentTest.
 */
object StarterContent {
    const val HERO = "hero"
    const val HEADLINE = "headline"
    const val PHOTO = "photo"
    const val ABOUT = "about"
    const val BIO = "bio"
    const val DESIGNATION = "designation"
    const val CONTACT = "contact"
    const val PHONE = "phone"
    const val SERVICES = "services"
    const val OFFERED = "offered"
    const val ITEMS = "items"
    const val INTEREST = "interest"
    const val DESCRIPTION = "description"

    /** `fromInsuranceTypes` kinds in site-seed.json (the services list is the other one, `serviceItems`). */
    const val FROM_TYPES_INTEREST_KEYS = "interestKeys"

    /**
     * @param locale the site's language (the app's language at creation); an unknown one falls back to the default.
     * @param agentId the signed-in user's id; it only seeds the variant choice, so it never changes for an agent.
     * @param insuranceTypes the agent's `catalog.insuranceTypes` keys (pending and approved company links).
     */
    fun from(profile: Profile, template: TemplateSpec, locale: String, agentId: String, insuranceTypes: Collection<String> = emptyList()): JsonObject {
        val siteLocale = if (locale in SiteSeed.LOCALES) locale else SiteSeed.DEFAULT_LOCALE
        val ctx = Context(profile, agentId, siteLocale, placeholderValues(profile), SiteSeed.mixFor(insuranceTypes),
            SiteSeed.servicesFor(insuranceTypes, servicesMaxItems()))
        val content = linkedMapOf<String, JsonElement>()
        SiteSeed.sections.forEach { seed ->
            val schema = SiteSchema.section(seed.key) ?: return@forEach
            val fields = linkedMapOf<String, JsonElement>()
            schema.fields.forEach { spec ->
                val value = seed.fields[spec.key]?.let { forMix(it, ctx.mix) } ?: return@forEach
                val json = when (value) {
                    is SeedValue.Items -> itemsJson(ctx, seed.key, spec, value)
                    is SeedValue.FromInsuranceTypes -> fromTypesJson(ctx, seed.key, spec, value)
                    else -> valueJson(ctx, spec, value, SiteSeed.salt(seed.key, spec.key))
                }
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
        val field = SiteSeed.section(ABOUT)?.fields?.get(BIO) ?: return@lazy emptyList()
        // Every copy mix's bio counts as seeded (DECISIONS #93).
        val all = if (field is SeedValue.ByMix) field.values.values.toList() else listOf(field)
        all.filterIsInstance<SeedValue.Variants>().flatMap { it.variants }.flatMap { variant ->
            SiteSeed.LOCALES.flatMap { locale ->
                listOfNotNull(variant.text, variant.fallback).map { patternOf(it.raw(locale)) }
            }
        }
    }

    private fun patternOf(raw: String): Regex {
        val parts = raw.split(Regex("\\{[A-Za-z0-9_]+\\}")).joinToString(".*") { Regex.escape(it) }
        return Regex(parts, RegexOption.DOT_MATCHES_ALL)
    }

    private class Context(
        val profile: Profile,
        val agentId: String,
        val locale: String,
        val values: Map<String, String>,
        /** The copy mix for the agent's insurance types. */
        val mix: String,
        val services: com.sledje.agentpro.core.catalog.SeedServices,
    )

    /** The services list's `maxItems` in the schema (the seeded items are cut to it). */
    fun servicesMaxItems(): Int =
        SiteSchema.section(SERVICES)?.fields?.firstOrNull { it.key == ITEMS }?.maxItems ?: Int.MAX_VALUE

    /** A `byMix` value's form for [mix] (null: that mix seeds nothing here); other values as they are. */
    private fun forMix(value: SeedValue, mix: String): SeedValue? = if (value is SeedValue.ByMix) value.value(mix) else value

    /**
     * The seeded description of the service item for [interest] at [index] in [locale] (DECISIONS #93), filled with
     * [profile]; null when the seed has none. Shared with [SiteServices] (adding services to an existing site).
     */
    fun serviceDescription(interest: String, index: Int, profile: Profile?, locale: String, agentId: String): String? {
        val variants = SiteSeed.serviceDescriptions[interest] ?: return null
        val siteLocale = if (locale in SiteSeed.LOCALES) locale else SiteSeed.DEFAULT_LOCALE
        val values = profile?.let(::placeholderValues).orEmpty()
        val text = SiteSeed.resolve(variants, agentId, SiteSeed.salt(SERVICES, ITEMS, index, DESCRIPTION), siteLocale, values) ?: return null
        val spec = SiteSchema.section(SERVICES)?.fields?.firstOrNull { it.key == ITEMS }?.itemFields?.firstOrNull { it.key == DESCRIPTION }
        val max = spec?.maxLength
        return text.trim().let { if (max != null) it.take(max).trimEnd() else it }.takeIf { it.isNotEmpty() }
    }

    /** `services.offered` (interest keys) or `services.items` (`{interest, description}`) from the agent's types. */
    private fun fromTypesJson(ctx: Context, section: String, spec: FieldSpec, value: SeedValue.FromInsuranceTypes): JsonElement? = when (value.kind) {
        FROM_TYPES_INTEREST_KEYS -> ctx.services.offered.takeIf { it.isNotEmpty() }?.let { keys -> JsonArray(keys.map(::JsonPrimitive)) }
        else -> {
            val items = ctx.services.items.let { if (spec.maxItems != null) it.take(spec.maxItems) else it }.mapIndexed { i, interest ->
                val fields = linkedMapOf<String, JsonElement>(INTEREST to JsonPrimitive(interest))
                SiteSeed.serviceDescriptions[interest]?.let { d ->
                    val descSpec = spec.itemFields.firstOrNull { it.key == DESCRIPTION }
                    val text = SiteSeed.resolve(d, ctx.agentId, SiteSeed.salt(section, spec.key, i, DESCRIPTION), ctx.locale, ctx.values)
                    if (text != null && descSpec != null) textJson(descSpec, text)?.let { fields[DESCRIPTION] = it }
                }
                JsonObject(fields)
            }
            items.takeIf { it.isNotEmpty() }?.let(::JsonArray)
        }
    }

    private fun valueJson(ctx: Context, spec: FieldSpec, value: SeedValue, salt: String): JsonElement? = when (value) {
        is SeedValue.Variants -> SiteSeed.resolve(value, ctx.agentId, salt, ctx.locale, ctx.values)?.let { textJson(spec, it) }
        is SeedValue.FromProfile -> profileValue(ctx.profile, value.field)?.let { textJson(spec, it, validate = true) }
        is SeedValue.Literal -> value.value
        is SeedValue.ByLocale -> value.value(ctx.locale)
        is SeedValue.Items, is SeedValue.ByMix, is SeedValue.FromInsuranceTypes -> null
    }

    private fun itemsJson(ctx: Context, section: String, list: FieldSpec, items: SeedValue.Items): JsonElement? {
        val out = items.items.mapIndexedNotNull { index, item ->
            val fields = linkedMapOf<String, JsonElement>()
            list.itemFields.forEach { spec ->
                val value = item[spec.key]?.let { forMix(it, ctx.mix) } ?: return@forEach
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
