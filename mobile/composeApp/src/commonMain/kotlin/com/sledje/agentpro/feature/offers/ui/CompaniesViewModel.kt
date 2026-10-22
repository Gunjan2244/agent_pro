package com.sledje.agentpro.feature.offers.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sledje.agentpro.core.catalog.InsuranceType
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.forms.FieldOption
import com.sledje.agentpro.core.forms.FieldSpec
import com.sledje.agentpro.core.forms.FieldType
import com.sledje.agentpro.core.forms.FormState
import com.sledje.agentpro.feature.offers.data.Company
import com.sledje.agentpro.feature.offers.data.CompanyRepository
import com.sledje.agentpro.feature.offers.data.Membership
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.company_agency_code
import com.sledje.agentpro.resources.company_name_label
import com.sledje.agentpro.resources.company_types_label
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the add / edit sheet is for (docs/UX.md, Your companies). */
sealed interface CompanyTarget {
    /** A new link to a listed [company], or (null) to a company that isn't listed. */
    data class New(val company: Company?) : CompanyTarget

    /** Only the insurance types of a pending or approved link: no new review. */
    data class Types(val membership: Membership) : CompanyTarget

    /** Asking again after a rejection or removal, prefilled from the old request. */
    data class Again(val membership: Membership) : CompanyTarget
}

/** The sheet showing over the companies block. */
sealed interface CompanySheet {
    data object Picker : CompanySheet

    /** [form] holds the fields for [target]; [error] is the last save failure. */
    data class Form(val target: CompanyTarget, val form: FormState, val error: AppError? = null) : CompanySheet
}

data class CompaniesUiState(
    /** Every link except withdrawn ones. */
    val memberships: List<Membership> = emptyList(),
    /** The first load is running (or failed: see [loadError]). */
    val loading: Boolean = true,
    val loadError: AppError? = null,
    val sheet: CompanySheet? = null,
    /** The picker's companies; null until `list_companies` answers. */
    val companies: List<Company>? = null,
    val companiesError: AppError? = null,
    val saving: Boolean = false,
    /** A removal waiting for the agent's confirmation. */
    val confirmRemove: Membership? = null,
    /** A failed remove from the list (the sheet shows its own errors). */
    val error: AppError? = null,
) {
    /** The links that count: pending or approved. */
    val active: List<Membership> get() = memberships.filter { it.isActive }

    /** Companies already linked (pending or approved), so the picker can mark them. */
    val linkedCompanyIds: Set<String> get() = active.mapNotNull { it.company?.id }.toSet()

    /** Onboarding step 1 may continue (docs/UX.md, Your companies). */
    val satisfiesOnboarding: Boolean get() = CompanyRules.hasEnough(memberships)
}

/** The rules of "Your companies" that don't need a screen (unit-tested). */
object CompanyRules {
    /** At least one pending or approved company, each with at least one insurance type. */
    fun hasEnough(memberships: List<Membership>): Boolean =
        memberships.any { it.isActive && it.insuranceTypes.isNotEmpty() }
}

/**
 * The company list and its add / edit / remove flows, shared by onboarding step 1 and Settings → Profile (DECISIONS #89).
 * Every change is one RPC (`request_company`, `set_membership_types`, `withdraw_company_request`), so the server is the
 * truth and nothing is lost if the app closes between steps; the list refreshes after each.
 */
class CompaniesViewModel(
    private val repo: CompanyRepository,
    /** Called after each successful add, change or removal (Settings → Profile offers to update the website, DECISIONS #93). */
    private val onChanged: () -> Unit = {},
) : ViewModel() {
    private val _state = MutableStateFlow(CompaniesUiState(memberships = repo.memberships.value))
    val state: StateFlow<CompaniesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { repo.memberships.collect { list -> _state.update { it.copy(memberships = list) } } }
        refresh()
    }

    /** Loads the agent's links (the first load, and Retry). */
    fun refresh() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            when (val r = repo.refresh()) {
                is Result.Ok -> _state.update { it.copy(loading = false) }
                is Result.Err -> _state.update { it.copy(loading = false, loadError = r.error) }
            }
        }
    }

    // ---- Add ----------------------------------------------------------------------------------

    fun openPicker() {
        _state.update { it.copy(sheet = CompanySheet.Picker, error = null) }
        if (_state.value.companies == null) loadCompanies()
    }

    fun loadCompanies() {
        _state.update { it.copy(companiesError = null) }
        viewModelScope.launch {
            when (val r = repo.listCompanies()) {
                is Result.Ok -> _state.update { it.copy(companies = r.value) }
                is Result.Err -> _state.update { it.copy(companiesError = r.error) }
            }
        }
    }

    /** A listed company was picked ([company]), or null for "My company isn't listed". Already-linked ones are ignored. */
    fun pick(company: Company?) {
        if (company != null && company.id in _state.value.linkedCompanyIds) return
        openForm(CompanyTarget.New(company))
    }

    // ---- Edit, retry, remove ------------------------------------------------------------------

    fun editTypes(membership: Membership) = openForm(CompanyTarget.Types(membership))

    fun tryAgain(membership: Membership) = openForm(CompanyTarget.Again(membership))

    fun askRemove(membership: Membership) = _state.update { it.copy(confirmRemove = membership, error = null) }

    fun cancelRemove() = _state.update { it.copy(confirmRemove = null) }

    fun confirmRemove() {
        val m = _state.value.confirmRemove ?: return
        _state.update { it.copy(confirmRemove = null, saving = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.withdraw(m.id)) {
                is Result.Ok -> { _state.update { it.copy(saving = false) }; onChanged() }
                is Result.Err -> _state.update { it.copy(saving = false, error = r.error) }
            }
        }
    }

    // ---- The form -----------------------------------------------------------------------------

    private fun openForm(target: CompanyTarget) {
        val m = when (target) {
            is CompanyTarget.Types -> target.membership
            is CompanyTarget.Again -> target.membership
            is CompanyTarget.New -> null
        }
        val listed = when (target) {
            is CompanyTarget.New -> target.company != null
            else -> m?.company != null
        }
        val initial = buildMap<String, Any?> {
            m?.let {
                put(TYPES, it.insuranceTypes)
                put(AGENCY_CODE, it.agencyCode)
                put(NAME, it.suggestedName)
            }
        }
        _state.update { it.copy(sheet = CompanySheet.Form(target, FormState(fieldsFor(listed, typesOnly = target is CompanyTarget.Types), initial)), error = null) }
    }

    fun closeSheet() = _state.update { if (it.saving) it else it.copy(sheet = null) }

    /** Back from the form to the picker (a new company only). */
    fun backToPicker() = _state.update { it.copy(sheet = CompanySheet.Picker) }

    /** Saves the open form: validates, calls the RPC, refreshes and closes. Errors stay in the sheet. */
    fun submit() {
        val sheet = _state.value.sheet as? CompanySheet.Form ?: return
        if (_state.value.saving || !sheet.form.validateAll()) return
        val v = sheet.form.values()
        @Suppress("UNCHECKED_CAST")
        val types = (v[TYPES] as? List<String>).orEmpty()
        _state.update { it.copy(saving = true, sheet = sheet.copy(error = null)) }
        viewModelScope.launch {
            val result: Result<*, AppError> = when (val t = sheet.target) {
                is CompanyTarget.Types -> repo.setTypes(t.membership.id, types)
                is CompanyTarget.New -> repo.request(
                    companyId = t.company?.id,
                    suggestedName = if (t.company == null) v[NAME] as? String else null,
                    agencyCode = if (t.company != null) v[AGENCY_CODE] as? String else null,
                    insuranceTypes = types,
                )
                is CompanyTarget.Again -> repo.request(
                    companyId = t.membership.company?.id,
                    suggestedName = if (t.membership.company == null) v[NAME] as? String else null,
                    agencyCode = if (t.membership.company != null) v[AGENCY_CODE] as? String else null,
                    insuranceTypes = types,
                )
            }
            when (result) {
                is Result.Ok -> { _state.update { it.copy(saving = false, sheet = null) }; onChanged() }
                is Result.Err -> _state.update { it.copy(saving = false, sheet = sheet.copy(error = result.error)) }
            }
        }
    }

    companion object {
        const val NAME = "company_name"
        const val AGENCY_CODE = "company_agency_code"
        const val TYPES = "insurance_types"

        private val TYPE_OPTIONS = InsuranceType.active.map { FieldOption(it.key, it.label, it.icon) }

        /** One field of the types, at least one required. */
        private val TYPES_FIELD = FieldSpec(TYPES, FieldType.MultiSelect, Res.string.company_types_label, required = true, options = TYPE_OPTIONS)

        /**
         * The form's fields: a listed company asks for its agency code (the admin verifies it; at most 30 characters),
         * a company that isn't listed asks for its name (at most 80); both ask for the insurance types (docs/UX.md).
         * Changing only the types shows just that field.
         */
        fun fieldsFor(listed: Boolean, typesOnly: Boolean = false): List<FieldSpec> = when {
            typesOnly -> listOf(TYPES_FIELD)
            listed -> listOf(FieldSpec(AGENCY_CODE, FieldType.Text, Res.string.company_agency_code, required = true, maxLength = 30), TYPES_FIELD)
            else -> listOf(FieldSpec(NAME, FieldType.Text, Res.string.company_name_label, required = true, maxLength = 80), TYPES_FIELD)
        }
    }
}
