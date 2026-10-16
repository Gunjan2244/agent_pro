package com.sledje.agentpro.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

/**
 * Small string settings: sync cursors, the cached feature flags, the app language, the last signed-in
 * user and setup-checklist marks. Backed by one DataStore Preferences file ([DataStoreKeyValueStore]);
 * tests use [InMemoryKeyValueStore].
 */
interface KeyValueStore {
    fun observe(key: String): Flow<String?>
    suspend fun get(key: String): String?

    /** Sets [key]; a null [value] removes it. */
    suspend fun put(key: String, value: String?)

    /** Removes every key starting with [prefix]. */
    suspend fun removePrefix(prefix: String)
}

/** Keys of [KeyValueStore]. */
object StoreKeys {
    const val SYNC_CURSOR_PREFIX = "sync_cursor."
    const val CACHE_SCHEMA_VERSION = "cache_schema_version"
    const val FEATURE_FLAGS = "feature_flags"
    const val APP_LOCALE = "app_locale"
    const val LAST_USER_ID = "last_user_id"
    const val SITE_SHARED = "setup_site_shared"
}

/** Creates the app's DataStore at [path] (a platform file path; see each platform's AppContainer factory). */
fun createPreferencesDataStore(path: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() })

class DataStoreKeyValueStore(private val dataStore: DataStore<Preferences>) : KeyValueStore {
    override fun observe(key: String): Flow<String?> =
        dataStore.data.map { it[stringPreferencesKey(key)] }.distinctUntilChanged()

    override suspend fun get(key: String): String? = observe(key).first()

    override suspend fun put(key: String, value: String?) {
        dataStore.edit { prefs ->
            val k = stringPreferencesKey(key)
            if (value == null) prefs.remove(k) else prefs[k] = value
        }
    }

    override suspend fun removePrefix(prefix: String) {
        dataStore.edit { prefs ->
            prefs.asMap().keys.filter { it.name.startsWith(prefix) }.forEach { prefs.remove(it) }
        }
    }
}

/** A [KeyValueStore] in memory (tests and previews). */
class InMemoryKeyValueStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {
    private val state = MutableStateFlow(initial)

    val snapshot: Map<String, String> get() = state.value

    override fun observe(key: String): Flow<String?> = state.map { it[key] }.distinctUntilChanged()

    override suspend fun get(key: String): String? = state.value[key]

    override suspend fun put(key: String, value: String?) {
        state.value = if (value == null) state.value - key else state.value + (key to value)
    }

    override suspend fun removePrefix(prefix: String) {
        state.value = state.value.filterKeys { !it.startsWith(prefix) }
    }
}
