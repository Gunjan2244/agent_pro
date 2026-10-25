package com.sledje.agentpro.testing

import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.AppResult
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.feature.customers.data.CustomerDraft
import com.sledje.agentpro.feature.customers.data.CustomerDraftLive
import com.sledje.agentpro.feature.customers.data.CustomerDraftRepository
import com.sledje.agentpro.feature.customers.data.DraftSource
import com.sledje.agentpro.feature.customers.data.SignupLinkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/** A draft for tests: by default the agent's own, unfinished, last changed at [TEST_NOW]. */
fun testDraft(
    id: String = "draft-1",
    source: DraftSource = DraftSource.Agent,
    fields: Map<String, String> = emptyMap(),
    activeField: String? = null,
    seenAt: Instant? = null,
    openedAt: Instant? = null,
    consented: Boolean = false,
    expiresAt: Instant? = null,
    completedAt: Instant? = null,
    customerId: String? = null,
    updatedAt: Instant = TEST_NOW,
) = CustomerDraft(
    id = id, source = source, fields = fields, activeField = activeField, customerSeenAt = seenAt, openedAt = openedAt,
    consented = consented, linkExpiresAt = expiresAt, completedAt = completedAt, customerId = customerId,
    createdAt = updatedAt, updatedAt = updatedAt,
)

/** In-memory `customer_drafts`: agent writes behave like the real repository; the link RPCs return what the test sets. */
class FakeCustomerDraftRepository(initial: List<CustomerDraft> = emptyList()) : CustomerDraftRepository {
    val rows = MutableStateFlow(initial)
    var error: AppError? = null
    var linkResult: AppResult<SignupLinkResult> =
        Result.Ok(SignupLinkResult.Created("draft-link", "tok123", Instant.parse("2026-10-23T10:00:00Z")))
    var renewResult: AppResult<SignupLinkResult> =
        Result.Ok(SignupLinkResult.Created("draft-link", "tok456", Instant.parse("2026-10-30T10:00:00Z")))
    val calls = mutableListOf<String>()
    var refreshes = 0
    private var nextId = 1
    private val deleted = mutableMapOf<String, CustomerDraft>()

    /** Fails the next [n] agent-draft writes (create / update / complete) with [error]. */
    var failWrites = 0

    override fun observeIncomplete(): Flow<List<CustomerDraft>> =
        rows.map { list -> list.filter { !it.isCompleted }.sortedByDescending { it.updatedAt } }

    override fun observe(id: String): Flow<CustomerDraft?> = rows.map { list -> list.firstOrNull { it.id == id } }

    private fun failing(): AppError? {
        if (failWrites > 0) { failWrites--; return error ?: AppError.Offline }
        return error
    }

    override suspend fun createAgentDraft(fields: Map<String, String>): AppResult<CustomerDraft> {
        calls += "create:${fields.toSortedMap()}"
        failing()?.let { return Result.Err(it) }
        val d = testDraft(id = "draft-${nextId++}", fields = fields)
        rows.value = rows.value + d
        return Result.Ok(d)
    }

    override suspend fun updateAgentDraft(id: String, fields: Map<String, String>): AppResult<CustomerDraft> {
        calls += "update:$id:${fields.toSortedMap()}"
        failing()?.let { return Result.Err(it) }
        return change(id) { it.copy(fields = fields) }
    }

    override suspend fun complete(id: String, customerId: String): AppResult<CustomerDraft> {
        calls += "complete:$id:$customerId"
        failing()?.let { return Result.Err(it) }
        return change(id) { it.copy(completedAt = TEST_NOW, customerId = customerId, fields = emptyMap()) }
    }

    override suspend fun delete(id: String): AppResult<Unit> {
        calls += "delete:$id"
        error?.let { return Result.Err(it) }
        rows.value.firstOrNull { it.id == id }?.let { deleted[id] = it }
        rows.value = rows.value.filterNot { it.id == id }
        return Result.Ok(Unit)
    }

    override suspend fun restore(id: String): AppResult<CustomerDraft> {
        calls += "restore:$id"
        val d = deleted.remove(id) ?: return Result.Err(AppError.Unknown("not deleted"))
        rows.value = rows.value + d
        return Result.Ok(d)
    }

    override suspend fun createLink(): AppResult<SignupLinkResult> { calls += "link"; return linkResult }

    override suspend fun renewLink(draftId: String): AppResult<SignupLinkResult> { calls += "renew:$draftId"; return renewResult }

    override suspend fun refresh(): AppResult<Unit> { refreshes++; return Result.Ok(Unit) }

    private fun change(id: String, transform: (CustomerDraft) -> CustomerDraft): AppResult<CustomerDraft> {
        val current = rows.value.firstOrNull { it.id == id } ?: return Result.Err(AppError.Unknown("not found"))
        val updated = transform(current)
        rows.value = rows.value.map { if (it.id == id) updated else it }
        return Result.Ok(updated)
    }
}

class FakeCustomerDraftLive : CustomerDraftLive {
    var started = 0
    var stopped = 0
    override fun start() { started++ }
    override fun stop() { stopped++ }
}
