package com.sledje.agentpro.feature.website.data

import com.sledje.agentpro.core.catalog.InsuranceType
import com.sledje.agentpro.core.catalog.Interest
import com.sledje.agentpro.core.catalog.SiteSeed
import com.sledje.agentpro.feature.offers.data.Membership
import com.sledje.agentpro.feature.settings.data.Profile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * What a site's services would need to match the agent's insurance types (DECISIONS #93): [add] the interests seeded for
 * a type the site has no service of yet, and [remove] the services whose type the agent no longer deals in. Interests
 * with no type (`other`) never count either way.
 */
data class ServicesUpdate(val add: List<String>, val remove: List<String>) {
    val isEmpty: Boolean get() = add.isEmpty() && remove.isEmpty()
}

/**
 * The agent's insurance types and how a site's services relate to them (DECISIONS #93; docs/UX.md, Settings → Profile
 * and Website → Content). Pure and unit-tested; the screens only call it. It only ever adds service items with seeded
 * descriptions and removes the items and `offered` keys the agent chose; it never touches any other text.
 */
object SiteServices {
    /** The union of the types over the agent's pending and approved links, in catalog order (unknown keys skipped). */
    fun agentTypes(memberships: List<Membership>): List<String> {
        val declared = memberships.filter { it.isActive }.flatMap { it.insuranceTypes }.toSet()
        return InsuranceType.active.map { it.key }.filter { it in declared }
    }

    /** The interest keys the site's `services` names (`offered`, then the items' interests), each once. */
    fun siteInterests(content: JsonObject): List<String> {
        val services = content[StarterContent.SERVICES] as? JsonObject ?: return emptyList()
        val offered = (services[StarterContent.OFFERED] as? JsonArray).orEmpty().mapNotNull { it.str() }
        val items = (services[StarterContent.ITEMS] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get(StarterContent.INTEREST)?.str() }
        return (offered + items).distinct()
    }

    /** The insurance type of an interest key; null for `other` or an unknown key. */
    fun typeOf(interest: String): String? = Interest.fromKey(interest)?.insuranceType

    /**
     * The update that would make [content]'s services match [types] (the agent's, from [agentTypes]); empty when they
     * already match, and always empty while the agent has declared no type (nothing to compare with).
     */
    fun diff(content: JsonObject, types: List<String>): ServicesUpdate {
        if (types.isEmpty()) return ServicesUpdate(emptyList(), emptyList())
        val present = siteInterests(content)
        val presentTypes = present.mapNotNull(::typeOf).toSet()
        val missing = types.filter { it !in presentTypes }
        val add = if (missing.isEmpty()) emptyList() else SiteSeed.servicesFor(missing, StarterContent.servicesMaxItems()).items.filter { it !in present }
        val remove = present.filter { k -> typeOf(k)?.let { it !in types } == true }
        return ServicesUpdate(add, remove)
    }

    /**
     * [content] with [add] appended to `services.offered` and, while the list has room, as items with their seeded
     * description in [locale]; and with every interest in [remove] taken out of both. Everything else is kept as is.
     */
    fun apply(content: JsonObject, add: List<String>, remove: Collection<String>, profile: Profile?, locale: String, agentId: String): JsonObject {
        val services = content[StarterContent.SERVICES] as? JsonObject ?: JsonObject(emptyMap())
        val offered = (services[StarterContent.OFFERED] as? JsonArray).orEmpty().filter { it.str() !in remove }
        val items = (services[StarterContent.ITEMS] as? JsonArray).orEmpty().filter { (it as? JsonObject)?.get(StarterContent.INTEREST)?.str() !in remove }
        val presentOffered = offered.mapNotNull { it.str() }.toSet()
        val presentItems = items.mapNotNull { (it as? JsonObject)?.get(StarterContent.INTEREST)?.str() }.toSet()
        val newOffered = offered + add.filter { it !in presentOffered }.map(::JsonPrimitive)
        val max = StarterContent.servicesMaxItems()
        val newItems = items.toMutableList<JsonElement>()
        for (interest in add) {
            if (newItems.size >= max) break
            if (interest in presentItems) continue
            val fields = linkedMapOf<String, JsonElement>(StarterContent.INTEREST to JsonPrimitive(interest))
            StarterContent.serviceDescription(interest, newItems.size, profile, locale, agentId)?.let { fields[StarterContent.DESCRIPTION] = JsonPrimitive(it) }
            newItems += JsonObject(fields)
        }
        val updated = LinkedHashMap(services)
        if (newOffered.isEmpty()) updated.remove(StarterContent.OFFERED) else updated[StarterContent.OFFERED] = JsonArray(newOffered)
        if (newItems.isEmpty()) updated.remove(StarterContent.ITEMS) else updated[StarterContent.ITEMS] = JsonArray(newItems)
        val out = LinkedHashMap(content)
        if (updated.isEmpty()) out.remove(StarterContent.SERVICES) else out[StarterContent.SERVICES] = JsonObject(updated)
        return JsonObject(out)
    }

    private fun JsonElement.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}
