package com.sledje.agentpro.testing

import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.AppResult
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.feature.offers.data.Company
import com.sledje.agentpro.feature.offers.data.CompanyRepository
import com.sledje.agentpro.feature.offers.data.CompanyOffer
import com.sledje.agentpro.feature.offers.data.LiveOffers
import com.sledje.agentpro.feature.offers.data.Membership
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

fun testCompany(id: String = "c1", en: String = "Alpha Insurance", hi: String = "अल्फा बीमा") = Company(id, mapOf("en" to en, "hi" to hi))

fun testMembership(
    id: String = "m1",
    status: String = "pending",
    company: Company? = testCompany(),
    suggestedName: String? = null,
    agencyCode: String? = "AG123",
    types: List<String> = listOf("life"),
    rejectReason: String? = null,
    reviewNote: String? = null,
    requestedAt: String? = "2026-10-16T08:00:00Z",
) = Membership(id, status, company, suggestedName, agencyCode, types, rejectReason, reviewNote, requestedAt, null)

/** In-memory `CompanyRepository` that mimics the RPCs' rules closely enough for view-model tests. */
class FakeCompanyRepository(
    initial: List<Membership> = emptyList(),
    var companies: List<Company> = listOf(testCompany("c1"), testCompany("c2", "Beta Life", "बीटा लाइफ")),
) : CompanyRepository {
    private val state = MutableStateFlow(initial)
    override val memberships: StateFlow<List<Membership>> = state

    /** Fails the next call (and every one after) with this error until cleared. */
    var error: AppError? = null
    var listError: AppError? = null

    data class Request(val companyId: String?, val suggestedName: String?, val agencyCode: String?, val types: List<String>)
    val requests = mutableListOf<Request>()
    val withdrawn = mutableListOf<String>()
    val typeChanges = mutableListOf<Pair<String, List<String>>>()
    var refreshes = 0
    var cleared = 0
    private var nextId = 100

    /** The `my_company_offers` answer: the live offers (default none), or [liveError]. */
    var liveOffers: List<CompanyOffer> = emptyList()
    var liveError: AppError? = null
    var liveCalls = 0

    override suspend fun liveOffers(): AppResult<LiveOffers> {
        liveCalls++
        liveError?.let { return Result.Err(it) }
        return Result.Ok(LiveOffers(state.value, liveOffers))
    }

    override fun clear() { cleared++; state.value = emptyList() }

    override suspend fun refresh(): AppResult<Unit> {
        refreshes++
        return error?.let { Result.Err(it) } ?: Result.Ok(Unit)
    }

    override suspend fun listCompanies(): AppResult<List<Company>> =
        listError?.let { Result.Err(it) } ?: Result.Ok(companies)

    override suspend fun request(companyId: String?, suggestedName: String?, agencyCode: String?, insuranceTypes: List<String>): AppResult<Membership> {
        requests += Request(companyId, suggestedName, agencyCode, insuranceTypes)
        error?.let { return Result.Err(it) }
        val m = testMembership(
            id = "m${nextId++}", status = "pending", company = companies.firstOrNull { it.id == companyId },
            suggestedName = suggestedName, agencyCode = agencyCode, types = insuranceTypes,
        )
        state.value = state.value.filterNot { it.isActive && it.company?.id == companyId && companyId != null } + m
        return Result.Ok(m)
    }

    override suspend fun withdraw(membershipId: String): AppResult<Unit> {
        withdrawn += membershipId
        error?.let { return Result.Err(it) }
        state.value = state.value.filterNot { it.id == membershipId }
        return Result.Ok(Unit)
    }

    override suspend fun setTypes(membershipId: String, insuranceTypes: List<String>): AppResult<Membership> {
        typeChanges += membershipId to insuranceTypes
        error?.let { return Result.Err(it) }
        val current = state.value.first { it.id == membershipId }
        val updated = current.copy(insuranceTypes = insuranceTypes)
        state.value = state.value.map { if (it.id == membershipId) updated else it }
        return Result.Ok(updated)
    }
}

fun testOffer(
    id: String,
    companyId: String = "c1",
    title: String = "Title $id",
    summary: String = "Summary $id",
    endsAt: String = "2026-12-31T18:29:59Z",
    interest: String? = "term",
) = CompanyOffer(id, companyId, mapOf("en" to title, "hi" to "शीर्षक $id"), mapOf("en" to summary, "hi" to "सारांश $id"), endsAt, interest, "running")
