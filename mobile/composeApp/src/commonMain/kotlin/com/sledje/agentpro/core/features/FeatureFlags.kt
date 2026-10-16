package com.sledje.agentpro.core.features

import com.sledje.agentpro.core.catalog.FeatureId
import com.sledje.agentpro.core.data.AppResult
import com.sledje.agentpro.core.data.KeyValueStore
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.data.RowJson
import com.sledje.agentpro.core.data.StoreKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Which features are on (DECISIONS #11, #35). Defaults come from the generated catalog
 * (`FeatureId.default`); [remote] holds the server's effective values from `effective_flags()`.
 * A remote boolean wins; anything else (missing, null, a string or number) leaves the default.
 */
class FeatureFlags(val remote: Map<String, Boolean> = emptyMap()) {
    fun isEnabled(id: FeatureId): Boolean = remote[id.key] ?: id.default

    companion object {
        /** Catalog defaults only. */
        val Defaults = FeatureFlags()

        /** Keeps only boolean values of [json] (the same rule as `effective_flags()` and `private.flag_enabled`). */
        fun fromJson(json: JsonObject): FeatureFlags = FeatureFlags(
            json.mapNotNull { (key, value) ->
                val primitive = value as? JsonPrimitive ?: return@mapNotNull null
                if (primitive is JsonNull || primitive.isString) return@mapNotNull null
                when (primitive.content) {
                    "true" -> key to true
                    "false" -> key to false
                    else -> null
                }
            }.toMap(),
        )
    }
}

/**
 * The effective flags: the cached `effective_flags()` result (DataStore) over the catalog defaults.
 * [refresh] fetches the server's values and caches them; offline or on failure the cache stays.
 */
class FeatureFlagsRepository(
    private val store: KeyValueStore,
    scope: CoroutineScope,
    private val fetchRemote: suspend () -> AppResult<JsonObject>,
) {
    val flags: StateFlow<FeatureFlags> = store.observe(StoreKeys.FEATURE_FLAGS)
        .map { cached -> cached?.let { parse(it) } ?: FeatureFlags.Defaults }
        .stateIn(scope, SharingStarted.Eagerly, FeatureFlags.Defaults)

    suspend fun refresh(): AppResult<FeatureFlags> = when (val result = fetchRemote()) {
        is Result.Ok -> {
            store.put(StoreKeys.FEATURE_FLAGS, RowJson.encodeToString(JsonObject.serializer(), result.value))
            Result.Ok(FeatureFlags.fromJson(result.value))
        }
        is Result.Err -> result
    }

    private fun parse(text: String): FeatureFlags? =
        runCatching { FeatureFlags.fromJson(RowJson.decodeFromString(JsonObject.serializer(), text)) }.getOrNull()
}
