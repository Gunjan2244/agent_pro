package com.sledje.agentpro.core.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** JSON settings for Supabase rows: unknown columns are ignored so new server columns never break sync. */
val RowJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/**
 * A [SyncTable] backed by a Room DAO: decodes each PostgREST row with [serializer] into its DTO,
 * converts it to an entity and writes it through the given DAO functions.
 */
class RoomSyncTable<Dto, Entity>(
    override val name: String,
    private val serializer: KSerializer<Dto>,
    private val toEntity: (Dto) -> Entity,
    private val upsertAll: suspend (List<Entity>) -> Unit,
    private val deleteByIds: suspend (List<String>) -> Unit,
    private val deleteAll: suspend () -> Unit,
) : SyncTable {
    override suspend fun upsert(rows: List<JsonObject>) {
        upsertAll(rows.map { toEntity(RowJson.decodeFromJsonElement(serializer, it)) })
    }

    override suspend fun delete(ids: List<String>) = deleteByIds(ids)

    override suspend fun clear() = deleteAll()
}
