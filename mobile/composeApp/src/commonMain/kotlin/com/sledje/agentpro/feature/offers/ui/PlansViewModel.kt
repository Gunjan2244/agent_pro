package com.sledje.agentpro.feature.offers.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.feature.offers.data.CompanyPlan
import com.sledje.agentpro.feature.offers.data.PlanChoice
import com.sledje.agentpro.feature.offers.data.PlansRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Website → Plans on your site (DECISIONS #129). [plans] are the official plans the agent may show: those of their
 * approved companies, for the insurance types they deal in there (the server decides). [auto] shows them all, new
 * ones included; otherwise the site shows [selected].
 */
data class PlansState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val plans: List<CompanyPlan> = emptyList(),
    val auto: Boolean = true,
    val selected: Set<String> = emptySet(),
    /** Something changed since the last load or save. */
    val dirty: Boolean = false,
    val saving: Boolean = false,
    /** A save just succeeded (the screen shows a confirmation once, then calls [PlansViewModel.consumeSaved]). */
    val saved: Boolean = false,
    val saveError: AppError? = null,
) {
    /** The plans grouped by company, in the server's order (approval order, then each company's own order). */
    val byCompany: List<Pair<String, List<CompanyPlan>>> get() = plans.groupBy { it.company }.toList()
}

class PlansViewModel(private val repo: PlansRepository) : ViewModel() {
    private val _state = MutableStateFlow(PlansState())
    val state: StateFlow<PlansState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.load()) {
                is Result.Ok -> _state.update { apply(it, r.value).copy(loading = false) }
                is Result.Err -> _state.update { it.copy(loading = false, error = r.error) }
            }
        }
    }

    fun setAuto(on: Boolean) = _state.update {
        // Turning automatic off starts from everything the site shows now, so nothing disappears by surprise.
        val start = if (!on && it.auto) it.plans.map(CompanyPlan::id).toSet() else it.selected
        it.copy(auto = on, selected = start, dirty = true, saved = false)
    }

    fun toggle(planId: String) = _state.update {
        if (it.auto) return@update it
        val next = if (planId in it.selected) it.selected - planId else it.selected + planId
        it.copy(selected = next, dirty = true, saved = false)
    }

    /** Selects every plan of [company], or clears them all when they are all selected. */
    fun toggleCompany(company: String) = _state.update { s ->
        if (s.auto) return@update s
        val ids = s.plans.filter { it.company == company }.map(CompanyPlan::id).toSet()
        val next = if (s.selected.containsAll(ids)) s.selected - ids else s.selected + ids
        s.copy(selected = next, dirty = true, saved = false)
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        // The server keeps the order it is given: the plans' own order (company, then the company's order).
        val ids = if (s.auto) null else s.plans.map(CompanyPlan::id).filter { it in s.selected }
        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            when (val r = repo.save(ids)) {
                is Result.Ok -> _state.update { apply(it, r.value).copy(saving = false, saved = true) }
                is Result.Err -> _state.update { it.copy(saving = false, saveError = r.error) }
            }
        }
    }

    fun consumeSaved() = _state.update { it.copy(saved = false) }

    private fun apply(s: PlansState, c: PlanChoice): PlansState =
        s.copy(plans = c.plans, auto = c.auto, selected = c.selected.toSet(), dirty = false, error = null)
}
