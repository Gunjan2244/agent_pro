package com.sledje.agentpro.core.data

import com.sledje.agentpro.testing.FakeNetworkMonitor
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private fun row(id: String, updatedAt: String, deletedAt: String? = null, name: String = id) = buildJsonObject {
    put("id", id)
    put("name", name)
    put("updated_at", updatedAt)
    put("deleted_at", deletedAt?.let { JsonPrimitive(it) } ?: JsonNull)
}

/** The server side: rows of one table, filtered like PostgREST would (updated_at, id keyset). */
private class FakeRemote(var rows: List<JsonObject> = emptyList()) : SyncRemote {
    val requests = mutableListOf<Triple<Instant?, SyncKey?, Int>>()
    var failWith: Throwable? = null

    override suspend fun fetchChanges(table: String, since: Instant?, after: SyncKey?, limit: Int): List<JsonObject> {
        failWith?.let { throw it }
        requests += Triple(since, after, limit)
        fun key(r: JsonObject) = SyncKey(r.instant("updated_at")!!, r.string("id")!!)
        val sorted = rows.sortedWith(compareBy<JsonObject>({ key(it).updatedAt }, { key(it).id }))
        return sorted.filter { r ->
            val k = key(r)
            when {
                after != null -> k.updatedAt > after.updatedAt || (k.updatedAt == after.updatedAt && k.id > after.id)
                since != null -> k.updatedAt > since
                else -> true
            }
        }.take(limit)
    }
}

/** The local cache table. */
private class FakeTable(override val name: String = "customers") : SyncTable {
    val rows = linkedMapOf<String, JsonObject>()
    override suspend fun upsert(rows: List<JsonObject>) = rows.forEach { this.rows[it.string("id")!!] = it }
    override suspend fun delete(ids: List<String>) = ids.forEach { rows.remove(it) }
    override suspend fun clear() = rows.clear()
}

class SyncEngineTest {
    private val store = InMemoryKeyValueStore()
    private val cursors = SyncCursorStore(store)
    private val network = FakeNetworkMonitor()
    private val table = FakeTable()
    private val remote = FakeRemote()

    private fun engine(pageSize: Int = 500) =
        SyncEngine(remote, cursors, network, listOf(table), pageSize = pageSize, mapError = { AppError.Unknown(it.message) })

    @Test
    fun firstRefreshPullsEverythingAndStoresTheNewestUpdatedAt() = runTest {
        remote.rows = listOf(row("a", "2026-10-15T10:00:00Z"), row("b", "2026-10-15T11:30:00.123456+00:00"))
        val result = engine().refresh("customers")
        assertEquals(Result.Ok(2), result)
        assertEquals(setOf("a", "b"), table.rows.keys)
        assertNull(remote.requests.single().first, "no cursor on the first refresh")
        // PostgREST's "+00:00" offset with microseconds parses, and the cursor keeps the precision.
        assertEquals(Instant.parse("2026-10-15T11:30:00.123456Z"), cursors.get("customers"))
    }

    @Test
    fun nextRefreshAsksOnlyForRowsAfterTheCursorAndUpserts() = runTest {
        remote.rows = listOf(row("a", "2026-10-15T10:00:00Z", name = "old"))
        val engine = engine()
        engine.refresh("customers")
        remote.rows = listOf(row("a", "2026-10-15T12:00:00Z", name = "new"), row("c", "2026-10-15T12:05:00Z"))
        assertEquals(Result.Ok(2), engine.refresh("customers"))
        assertEquals(Instant.parse("2026-10-15T10:00:00Z"), remote.requests.last().first, "updated_at > cursor")
        assertEquals("new", table.rows["a"]!!.string("name"), "changed row replaced")
        assertEquals(Instant.parse("2026-10-15T12:05:00Z"), cursors.get("customers"))
    }

    @Test
    fun tombstonesAreRemovedFromTheCache() = runTest {
        remote.rows = listOf(row("a", "2026-10-15T10:00:00Z"), row("b", "2026-10-15T10:01:00Z"))
        val engine = engine()
        engine.refresh("customers")
        remote.rows = listOf(row("b", "2026-10-15T13:00:00Z", deletedAt = "2026-10-15T13:00:00Z"))
        engine.refresh("customers")
        assertEquals(setOf("a"), table.rows.keys)
        assertEquals(Instant.parse("2026-10-15T13:00:00Z"), cursors.get("customers"), "a tombstone also advances the cursor")
    }

    @Test
    fun aTombstoneOfAnUncachedRowIsNeverInserted() = runTest {
        remote.rows = listOf(row("x", "2026-10-15T10:00:00Z", deletedAt = "2026-10-15T10:00:00Z"))
        engine().refresh("customers")
        assertTrue(table.rows.isEmpty())
    }

    @Test
    fun pagesUseTheKeysetSoRowsSharingATimestampAreNeverSkipped() = runTest {
        val t = "2026-10-15T10:00:00Z"
        remote.rows = listOf(row("a", t), row("b", t), row("c", t), row("d", "2026-10-15T10:00:01Z"), row("e", "2026-10-15T10:00:02Z"))
        assertEquals(Result.Ok(5), engine(pageSize = 2).refresh("customers"))
        assertEquals(setOf("a", "b", "c", "d", "e"), table.rows.keys)
        assertEquals(SyncKey(Instant.parse(t), "b"), remote.requests[1].second, "page 2 starts after (t, b)")
        assertEquals(Instant.parse("2026-10-15T10:00:02Z"), cursors.get("customers"))
    }

    @Test
    fun offlineSkipsTheRequestAndKeepsTheCursor() = runTest {
        cursors.set("customers", Instant.parse("2026-10-01T00:00:00Z"))
        network.isOnline.value = false
        remote.rows = listOf(row("a", "2026-10-15T10:00:00Z"))
        assertEquals(Result.Err(AppError.Offline), engine().refresh("customers"))
        assertTrue(remote.requests.isEmpty())
        assertTrue(table.rows.isEmpty())
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), cursors.get("customers"))
        assertEquals(Result.Err(AppError.Offline), engine().refreshAll())
    }

    @Test
    fun aFailedRequestIsMappedAndLeavesTheCursorUnchanged() = runTest {
        remote.failWith = IllegalStateException("boom")
        val result = engine().refresh("customers")
        assertEquals(Result.Err(AppError.Unknown("boom")), result)
        assertNull(cursors.get("customers"))
    }

    @Test
    fun clearAllEmptiesTablesAndCursorsAndApplyLocalWritesReturnedRows() = runTest {
        remote.rows = listOf(row("a", "2026-10-15T10:00:00Z"))
        val engine = engine()
        engine.refresh("customers")
        engine.applyLocal("customers", listOf(row("z", "2026-10-15T14:00:00Z")))
        assertEquals(setOf("a", "z"), table.rows.keys)
        assertEquals(Instant.parse("2026-10-15T10:00:00Z"), cursors.get("customers"), "a local write doesn't move the cursor")
        engine.clearAll()
        assertTrue(table.rows.isEmpty())
        assertNull(cursors.get("customers"))
    }

    @Test
    fun unknownTableIsAnError() = runTest {
        assertTrue(engine().refresh("nope") is Result.Err)
    }
}
