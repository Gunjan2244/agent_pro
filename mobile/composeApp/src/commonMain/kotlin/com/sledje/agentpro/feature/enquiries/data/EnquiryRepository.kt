package com.sledje.agentpro.feature.enquiries.data

import com.sledje.agentpro.core.catalog.EnquiryStatus
import com.sledje.agentpro.core.data.AppResult
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.data.RoomSyncTable
import com.sledje.agentpro.core.data.SupabaseProvider
import com.sledje.agentpro.core.data.SyncEngine
import com.sledje.agentpro.core.data.SyncTable
import com.sledje.agentpro.core.data.decodeRow
import com.sledje.agentpro.core.data.insertRow
import com.sledje.agentpro.core.data.map
import com.sledje.agentpro.core.data.remoteCall
import com.sledje.agentpro.core.data.toJson
import com.sledje.agentpro.core.data.updateRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Clock
import kotlin.time.Instant

const val ENQUIRIES_TABLE = "enquiries"

/** Enquiries: read from Room; every change is written to Supabase first (docs/ARCHITECTURE.md, Data flow). */
interface EnquiryRepository {
    fun observeAll(): Flow<List<Enquiry>>

    fun observe(id: String): Flow<Enquiry?>

    /** "Add enquiry" (status `new`; the server CHECKs source and interest against the catalog). */
    suspend fun add(input: NewEnquiry): AppResult<Enquiry>

    /** Changes the status; Follow-up also sets [followUpAt] (other statuses keep the stored date). */
    suspend fun setStatus(id: String, status: EnquiryStatus, followUpAt: Instant? = null): AppResult<Enquiry>

    /** Sets or clears the follow-up date (reminders are Phase 12; only `follow_up_at` is stored). */
    suspend fun setFollowUp(id: String, at: Instant?): AppResult<Enquiry>

    suspend fun setNotes(id: String, notes: String?): AppResult<Enquiry>

    /** Sets `seen_at` (opening the detail screen); a no-op when it's already set. */
    suspend fun markSeen(id: String): AppResult<Unit>

    /** Soft delete (sets `deleted_at`, DECISIONS #27). [restore] is the Undo. */
    suspend fun delete(id: String): AppResult<Unit>

    suspend fun restore(id: String): AppResult<Enquiry>

    /** Pulls changes (pull-to-refresh). */
    suspend fun refresh(): AppResult<Unit>
}

fun enquiriesSyncTable(dao: EnquiryDao): SyncTable = RoomSyncTable(
    name = ENQUIRIES_TABLE,
    serializer = EnquiryDto.serializer(),
    toEntity = { it.toEntity() },
    upsertAll = dao::upsert,
    deleteByIds = dao::deleteByIds,
    deleteAll = dao::deleteAll,
)

/** The `enquiries` column patch for a status change (pure, so it's unit-tested). */
fun statusPatch(status: EnquiryStatus, followUpAt: Instant?): JsonObject = buildJsonObject {
    put("status", JsonPrimitive(status.key))
    if (status == EnquiryStatus.FollowUp && followUpAt != null) put("follow_up_at", JsonPrimitive(followUpAt.toString()))
}

class SupabaseEnquiryRepository(
    private val supabase: SupabaseProvider,
    private val dao: EnquiryDao,
    private val sync: SyncEngine,
    private val clock: Clock = Clock.System,
) : EnquiryRepository {
    override fun observeAll(): Flow<List<Enquiry>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    override fun observe(id: String): Flow<Enquiry?> = dao.observe(id).map { it?.toModel() }

    override suspend fun add(input: NewEnquiry): AppResult<Enquiry> = write {
        supabase.insertRow(
            ENQUIRIES_TABLE,
            buildJsonObject {
                put("name", JsonPrimitive(input.name))
                put("phone", input.phone.toJson())
                put("interest", input.interest.toJson())
                put("source", JsonPrimitive(input.source))
                put("message", input.message.toJson())
                put("status", JsonPrimitive(EnquiryStatus.New.key))
                // Added by the agent, so it's already seen.
                put("seen_at", JsonPrimitive(clock.now().toString()))
            },
        )
    }

    override suspend fun setStatus(id: String, status: EnquiryStatus, followUpAt: Instant?) =
        write { supabase.updateRow(ENQUIRIES_TABLE, id, statusPatch(status, followUpAt)) }

    override suspend fun setFollowUp(id: String, at: Instant?) =
        write { supabase.updateRow(ENQUIRIES_TABLE, id, buildJsonObject { put("follow_up_at", at?.toString().toJson()) }) }

    override suspend fun setNotes(id: String, notes: String?) =
        write { supabase.updateRow(ENQUIRIES_TABLE, id, buildJsonObject { put("notes", notes?.takeIf { it.isNotBlank() }.toJson()) }) }

    override suspend fun markSeen(id: String): AppResult<Unit> =
        write { supabase.updateRow(ENQUIRIES_TABLE, id, buildJsonObject { put("seen_at", JsonPrimitive(clock.now().toString())) }) }
            .map { }

    override suspend fun delete(id: String): AppResult<Unit> =
        write { supabase.updateRow(ENQUIRIES_TABLE, id, buildJsonObject { put("deleted_at", JsonPrimitive(clock.now().toString())) }) }
            .map { }

    override suspend fun restore(id: String) =
        write { supabase.updateRow(ENQUIRIES_TABLE, id, buildJsonObject { put("deleted_at", JsonNull) }) }

    override suspend fun refresh(): AppResult<Unit> = sync.refresh(ENQUIRIES_TABLE).map { }

    /** Runs the write, then applies the returned row to Room (a tombstone removes it). */
    private suspend fun write(block: suspend () -> JsonObject): AppResult<Enquiry> {
        val result = remoteCall { block() }
        if (result is Result.Err) return result
        val row = (result as Result.Ok).value
        sync.applyLocal(ENQUIRIES_TABLE, listOf(row))
        return Result.Ok(row.decodeRow(EnquiryDto.serializer()).toEntity().toModel())
    }
}
