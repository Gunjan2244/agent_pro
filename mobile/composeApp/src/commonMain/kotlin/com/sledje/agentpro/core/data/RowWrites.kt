package com.sledje.agentpro.core.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/*
 * Write helpers for client-writable tables (docs/ARCHITECTURE.md, Data flow: writes go to Supabase
 * first, then Room is updated from the returned row; no write queue, DECISIONS #8). Callers wrap them
 * in `remoteCall { }`. RLS (owner-only plus `can_write()`) decides what is allowed.
 */

/** The signed-in agent's id (`auth.uid()`), or throws when there is no session. */
fun SupabaseProvider.requireUserId(): String = require().auth.currentUserOrNull()?.id ?: error("no session")

/** Inserts one row (with `agent_id` set to the caller) and returns it as PostgREST sends it back. */
suspend fun SupabaseProvider.insertRow(table: String, values: JsonObject): JsonObject {
    val uid = requireUserId()
    val body = buildJsonObject {
        put("agent_id", JsonPrimitive(uid))
        values.forEach { (k, v) -> put(k, v) }
    }
    return require().from(table).insert(body) { select() }.decodeSingle<JsonObject>()
}

/** Updates one row by id and returns it. Setting `deleted_at` is a soft delete (DECISIONS #27). */
suspend fun SupabaseProvider.updateRow(table: String, id: String, patch: JsonObject): JsonObject =
    require().from(table).update(patch) {
        select()
        filter { eq(SyncEngine.ID, id) }
    }.decodeSingle<JsonObject>()

/** Updates every row matching [column] = [value] and returns them (e.g. a customer's policies). */
suspend fun SupabaseProvider.updateRowsWhere(table: String, column: String, value: String, patch: JsonObject): List<JsonObject> =
    require().from(table).update(patch) {
        select()
        filter { eq(column, value) }
    }.decodeList<JsonObject>()

/** Decodes a returned row into its DTO with the sync JSON settings. */
fun <T> JsonObject.decodeRow(serializer: KSerializer<T>): T = RowJson.decodeFromJsonElement(serializer, this)

/** A nullable text column value: the string, or JSON null (clears the column). */
fun String?.toJson(): JsonElement = if (this == null) JsonNull else JsonPrimitive(this)

/** A nullable numeric column value. */
fun Double?.toJson(): JsonElement = if (this == null) JsonNull else JsonPrimitive(this)
