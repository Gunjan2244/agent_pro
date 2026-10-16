package com.sledje.agentpro.core.data

import com.sledje.agentpro.core.platform.NetworkMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

/** A local cache table that [SyncEngine] fills (one per synced Supabase table). */
interface SyncTable {
    /** The Supabase table name, e.g. `sites`. */
    val name: String

    /** Inserts or replaces live rows (as returned by PostgREST). */
    suspend fun upsert(rows: List<JsonObject>)

    /** Removes rows by id (tombstones: rows whose `deleted_at` is set). */
    suspend fun delete(ids: List<String>)

    /** Empties the table (sign-out, user change, cache schema change). */
    suspend fun clear()
}

/** Keyset position inside one refresh: the last row's `updated_at` and `id`. */
data class SyncKey(val updatedAt: Instant, val id: String)

/** Fetches changed rows from the server. Implementations throw; [SyncEngine] maps the failure. */
interface SyncRemote {
    /**
     * Rows of [table] ordered by `updated_at, id`, at most [limit]. With [after], rows strictly after
     * that keyset position; otherwise rows with `updated_at > since` (all rows when [since] is null).
     * Soft-deleted rows are included (DECISIONS #27).
     */
    suspend fun fetchChanges(table: String, since: Instant?, after: SyncKey?, limit: Int): List<JsonObject>
}

/** Per-table `updated_at` cursors, kept in DataStore (docs/ARCHITECTURE.md, Data flow). */
class SyncCursorStore(private val store: KeyValueStore) {
    suspend fun get(table: String): Instant? = store.get(StoreKeys.SYNC_CURSOR_PREFIX + table)?.let { runCatching { Instant.parse(it) }.getOrNull() }

    suspend fun set(table: String, value: Instant) = store.put(StoreKeys.SYNC_CURSOR_PREFIX + table, value.toString())

    suspend fun clear() = store.removePrefix(StoreKeys.SYNC_CURSOR_PREFIX)
}

/**
 * Incremental pull sync into the Room read cache (docs/ARCHITECTURE.md, Data flow and offline cache;
 * DECISIONS #8, #27). `refresh(table)`:
 * 1. skips with [AppError.Offline] while offline;
 * 2. pulls rows with `updated_at > cursor` page by page (keyset on `updated_at, id`, so rows sharing a
 *    timestamp are never skipped at a page boundary);
 * 3. upserts live rows and deletes tombstones (`deleted_at` set) from the local table;
 * 4. stores the newest `updated_at` as the table's cursor once every page has been applied.
 *
 * It runs at app start, on pull-to-refresh, when the app returns to the foreground and after writes.
 */
class SyncEngine(
    private val remote: SyncRemote,
    private val cursors: SyncCursorStore,
    private val network: NetworkMonitor,
    tables: List<SyncTable>,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val mapError: (Throwable) -> AppError = { it.toAppError() },
) {
    private val tables: Map<String, SyncTable> = tables.associateBy { it.name }
    private val locks: Map<String, Mutex> = this.tables.mapValues { Mutex() }

    val tableNames: List<String> get() = tables.keys.toList()

    /** Pulls changes of one table. Returns how many rows were applied (upserted or removed). */
    suspend fun refresh(table: String): AppResult<Int> {
        val local = tables[table] ?: return Result.Err(AppError.Unknown("unknown sync table $table"))
        if (!network.isOnline.value) return Result.Err(AppError.Offline)
        return locks.getValue(table).withLock {
            try {
                Result.Ok(pull(local))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.Err(mapError(e))
            }
        }
    }

    /** Refreshes every table in registration order; returns the first failure, if any. */
    suspend fun refreshAll(): AppResult<Unit> {
        var firstError: AppError? = null
        for (name in tables.keys) {
            val result = refresh(name)
            if (result is Result.Err && firstError == null) firstError = result.error
            if (result is Result.Err && result.error == AppError.Offline) break
        }
        return firstError?.let { Result.Err(it) } ?: Result.Ok(Unit)
    }

    /** Applies rows the app just wrote (Room is updated from the returned row, docs/ARCHITECTURE.md). */
    suspend fun applyLocal(table: String, rows: List<JsonObject>) {
        val local = tables[table] ?: return
        locks.getValue(table).withLock { apply(local, rows) }
    }

    /** Empties every table and forgets every cursor (sign-out, user change, cache schema change). */
    suspend fun clearAll() {
        tables.values.forEach { table -> locks.getValue(table.name).withLock { table.clear() } }
        cursors.clear()
    }

    private suspend fun pull(local: SyncTable): Int {
        val since = cursors.get(local.name)
        var after: SyncKey? = null
        var newest: Instant? = null
        var applied = 0
        while (true) {
            val page = remote.fetchChanges(local.name, since, after, pageSize)
            if (page.isEmpty()) break
            applied += apply(local, page)
            val last = page.last()
            val lastKey = SyncKey(requireNotNull(last.instant(UPDATED_AT)) { "${local.name}: row without updated_at" }, last.string(ID).orEmpty())
            after = lastKey
            page.mapNotNull { it.instant(UPDATED_AT) }.maxOrNull()?.let { if (newest == null || it > newest!!) newest = it }
            if (page.size < pageSize) break
        }
        newest?.let { cursors.set(local.name, it) }
        return applied
    }

    private suspend fun apply(local: SyncTable, rows: List<JsonObject>): Int {
        val (tombstones, live) = rows.partition { it.isTombstone() }
        if (live.isNotEmpty()) local.upsert(live)
        if (tombstones.isNotEmpty()) local.delete(tombstones.mapNotNull { it.string(ID) })
        return rows.size
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 500
        const val ID = "id"
        const val UPDATED_AT = "updated_at"
        const val DELETED_AT = "deleted_at"
    }
}

internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

internal fun JsonObject.instant(key: String): Instant? = string(key)?.let { runCatching { Instant.parse(it) }.getOrNull() }

/** A soft-deleted row: `deleted_at` is set (DECISIONS #27). */
fun JsonObject.isTombstone(): Boolean = string(SyncEngine.DELETED_AT) != null
