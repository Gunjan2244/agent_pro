package com.sledje.agentpro.core.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

/**
 * [SyncRemote] over PostgREST. RLS limits every select to the caller's own rows, and select policies
 * keep soft-deleted rows visible so tombstones arrive (DECISIONS #27).
 */
class SupabaseSyncRemote(private val supabase: SupabaseProvider) : SyncRemote {
    override suspend fun fetchChanges(table: String, since: Instant?, after: SyncKey?, limit: Int): List<JsonObject> =
        supabase.require().from(table).select {
            filter {
                when {
                    after != null -> or {
                        gt(SyncEngine.UPDATED_AT, after.updatedAt.toString())
                        and {
                            eq(SyncEngine.UPDATED_AT, after.updatedAt.toString())
                            gt(SyncEngine.ID, after.id)
                        }
                    }
                    since != null -> gt(SyncEngine.UPDATED_AT, since.toString())
                }
            }
            order(SyncEngine.UPDATED_AT, Order.ASCENDING)
            order(SyncEngine.ID, Order.ASCENDING)
            limit(limit.toLong())
        }.decodeList<JsonObject>()
}
