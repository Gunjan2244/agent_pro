package com.sledje.agentpro.feature.website.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sledje.agentpro.core.data.Result
import kotlinx.serialization.json.JsonObject

/**
 * A small in-memory cache of `preview_site_offers` answers, keyed by (draft content, template key), so
 * swiping the template pager back and forth doesn't refetch (DECISIONS #84). Only successes are kept;
 * the oldest entry goes first once [capacity] is reached.
 */
class PreviewOffersCache(private val capacity: Int = 16) {
    private val entries = LinkedHashMap<Pair<JsonObject, String>, JsonObject>()

    fun get(content: JsonObject, templateKey: String): JsonObject? = entries[content to templateKey]

    fun put(content: JsonObject, templateKey: String, value: JsonObject) {
        val key = content to templateKey
        entries.remove(key)
        entries[key] = value
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }
}

/** The answer for [content] in [templateKey], or null on any failure (offline, the RPC missing, an error). */
suspend fun SiteRepository.previewOffersOrNull(content: JsonObject, templateKey: String): JsonObject? =
    (previewOffers(content, templateKey) as? Result.Ok)?.value

/**
 * The `{company, offers}` for a preview, loaded after the preview is already on screen (null until it
 * arrives, and for good if it fails). Reset when [content] or [templateKey] changes, so a page never shows
 * another template's offers.
 */
@Composable
fun rememberPreviewOffers(sites: SiteRepository, content: JsonObject, templateKey: String): JsonObject? {
    var offers by remember(content, templateKey) { mutableStateOf<JsonObject?>(null) }
    LaunchedEffect(content, templateKey) { offers = sites.previewOffersOrNull(content, templateKey) }
    return offers
}
