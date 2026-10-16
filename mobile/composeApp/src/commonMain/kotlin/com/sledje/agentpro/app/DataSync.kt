package com.sledje.agentpro.app

import com.sledje.agentpro.core.data.AppResult
import com.sledje.agentpro.core.data.KeyValueStore
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.data.StoreKeys
import com.sledje.agentpro.core.data.SyncEngine
import com.sledje.agentpro.core.features.FeatureFlagsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** State of the latest full refresh. The gate waits for the first one when the cache is empty. */
enum class SyncStatus { Idle, Running, Done, Failed }

/** Refreshes the cache and the feature flags; owns sign-out and user-change cleanup. */
interface DataSync {
    val status: StateFlow<SyncStatus>

    /** Every synced table plus `effective_flags()` (app start, foreground, pull-to-refresh). */
    suspend fun refreshAll(): AppResult<Unit>

    /** Call when a session starts: clears the cache if a different user was signed in before. */
    suspend fun onSignedIn(userId: String)

    /** Empties the cache, the cursors and the remembered user (sign-out). */
    suspend fun clearAll()
}

class AppDataSync(
    private val engine: SyncEngine,
    private val flags: FeatureFlagsRepository,
    private val store: KeyValueStore,
    private val cacheSchemaVersion: Int,
) : DataSync {
    private val _status = MutableStateFlow(SyncStatus.Idle)
    override val status: StateFlow<SyncStatus> = _status.asStateFlow()
    private val mutex = Mutex()
    private var prepared = false

    /**
     * Resets the cursors when the Room schema changed: a destructive migration emptied the tables, so
     * the next refresh must pull everything again. Runs once, before the first refresh.
     */
    private suspend fun prepare() {
        if (prepared) return
        val stored = store.get(StoreKeys.CACHE_SCHEMA_VERSION)
        if (stored != cacheSchemaVersion.toString()) {
            engine.clearAll()
            store.put(StoreKeys.CACHE_SCHEMA_VERSION, cacheSchemaVersion.toString())
        }
        prepared = true
    }

    override suspend fun refreshAll(): AppResult<Unit> = mutex.withLock {
        prepare()
        _status.value = SyncStatus.Running
        flags.refresh()
        val result = engine.refreshAll()
        _status.value = if (result is Result.Ok) SyncStatus.Done else SyncStatus.Failed
        result
    }

    override suspend fun onSignedIn(userId: String) = mutex.withLock {
        prepare()
        val last = store.get(StoreKeys.LAST_USER_ID)
        if (last != null && last != userId) engine.clearAll()
        if (last != userId) store.put(StoreKeys.LAST_USER_ID, userId)
    }

    override suspend fun clearAll() = mutex.withLock {
        engine.clearAll()
        store.put(StoreKeys.LAST_USER_ID, null)
        store.put(StoreKeys.SITE_SHARED, null)
        _status.value = SyncStatus.Idle
    }
}
