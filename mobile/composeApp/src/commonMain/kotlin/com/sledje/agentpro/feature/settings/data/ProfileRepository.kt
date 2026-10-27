package com.sledje.agentpro.feature.settings.data

import com.sledje.agentpro.core.data.AppResult
import com.sledje.agentpro.core.data.RoomSyncTable
import com.sledje.agentpro.core.data.RowJson
import com.sledje.agentpro.core.data.SupabaseProvider
import com.sledje.agentpro.core.data.SyncEngine
import com.sledje.agentpro.core.data.SyncTable
import com.sledje.agentpro.core.data.remoteCall
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The agent's profile: read from the Room cache, written to Supabase first (docs/ARCHITECTURE.md, Data flow). */
interface ProfileRepository {
    fun observeMine(): Flow<Profile?>
    suspend fun update(input: ProfileInput): AppResult<Profile>
}

const val PROFILES_TABLE = "profiles"

fun profilesSyncTable(dao: ProfileDao): SyncTable = RoomSyncTable(
    name = PROFILES_TABLE,
    serializer = ProfileDto.serializer(),
    toEntity = { it.toEntity() },
    upsertAll = dao::upsert,
    deleteByIds = dao::deleteByIds,
    deleteAll = dao::deleteAll,
)

class SupabaseProfileRepository(
    private val supabase: SupabaseProvider,
    private val dao: ProfileDao,
    private val sync: SyncEngine,
) : ProfileRepository {
    override fun observeMine(): Flow<Profile?> = dao.observeMine().map { it?.toModel() }

    override suspend fun update(input: ProfileInput): AppResult<Profile> = remoteCall {
        val client = supabase.require()
        val uid = client.auth.currentUserOrNull()?.id ?: error("no session")
        val row = client.from(PROFILES_TABLE).update(input.toJson()) {
            select()
            filter { eq("id", uid) }
        }.decodeSingle<JsonObject>()
        sync.applyLocal(PROFILES_TABLE, listOf(row))
        sync.refresh(PROFILES_TABLE) // "after each write"; a failure here doesn't fail the write
        RowJson.decodeFromJsonElement(ProfileDto.serializer(), row).toEntity().toModel()
    }
}

private fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

internal fun ProfileInput.toJson(): JsonObject = buildJsonObject {
    put("full_name", fullName.trim())
    put("phone", phone.trim())
    put("city", city.blankToNull())
    put("agency_code", agencyCode.blankToNull())
    put("branch", branch.blankToNull())
    put("photo_url", photoUrl.blankToNull())
}
