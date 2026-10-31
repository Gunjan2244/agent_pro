package com.sledje.agentpro.feature.offers.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.forms.FieldSpec
import com.sledje.agentpro.core.forms.FieldType
import com.sledje.agentpro.core.forms.FormState
import com.sledje.agentpro.feature.offers.data.CompanyPlan
import com.sledje.agentpro.feature.offers.data.PlanChoice
import com.sledje.agentpro.feature.offers.data.PlansRepository
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.plans_feature_1
import com.sledje.agentpro.resources.plans_feature_2
import com.sledje.agentpro.resources.plans_feature_3
import com.sledje.agentpro.resources.plans_feature_4
import com.sledje.agentpro.resources.plans_feature_5
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Website → Plans on your site → a plan's key features (DECISIONS #130). By default the agent's site shows the
 * insurer's own words ([CompanyPlan.features], copied by the admin). The agent may write their own (up to 5 points,
 * built by FormRenderer); their site then marks them as the agent's words and points to the insurer's documents for the
 * official terms. "Go back to the insurer's wording" removes the agent's version at any time.
 */
data class PlanFeaturesState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val plan: CompanyPlan? = null,
    val saving: Boolean = false,
    /** The agent's words were just saved / the insurer's wording was just restored (shown once). */
    val saved: Boolean = false,
    val reset: Boolean = false,
    val saveError: AppError? = null,
) {
    /** The site shows the agent's own words. */
    val own: Boolean get() = plan?.ownFeatures != null
}

class PlanFeaturesViewModel(private val repo: PlansRepository, private val planId: String) : ViewModel() {
    val form = FormState(FIELDS)

    private val _state = MutableStateFlow(PlanFeaturesState())
    val state: StateFlow<PlanFeaturesState> = _state.asStateFlow()

    /** The words that were on the site when the screen opened or was last saved. */
    private var loaded: List<String> = emptyList()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.load()) {
                is Result.Ok -> apply(r.value, loading = false)
                is Result.Err -> _state.update { it.copy(loading = false, error = r.error) }
            }
        }
    }

    /** The points to save: trimmed, spaces collapsed, blanks dropped. */
    fun points(): List<String> = FIELDS.map { form.text(it.key).trim().replace(SPACES, " ") }.filter { it.isNotEmpty() }

    /** Something to save: valid, at least one point, and different from what the site shows. */
    fun canSave(): Boolean = !_state.value.saving && form.isValid && points().let { it.isNotEmpty() && it != loaded }

    fun save() {
        if (!form.validateAll() || !canSave()) return
        send(points(), reset = false)
    }

    /** Back to the insurer's wording (the agent's version is removed). */
    fun resetToInsurer() {
        if (_state.value.saving || !_state.value.own) return
        send(null, reset = true)
    }

    fun consumeSaved() = _state.update { it.copy(saved = false, reset = false) }

    private fun send(points: List<String>?, reset: Boolean) {
        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            when (val r = repo.saveFeatures(planId, points)) {
                is Result.Ok -> { apply(r.value, loading = false); _state.update { it.copy(saving = false, saved = !reset, reset = reset) } }
                is Result.Err -> _state.update { it.copy(saving = false, saveError = r.error) }
            }
        }
    }

    private fun apply(c: PlanChoice, loading: Boolean) {
        val plan = c.plans.firstOrNull { it.id == planId }
        if (plan == null) {
            _state.update { it.copy(loading = loading, plan = null, error = AppError.Unknown("plan_not_available")) }
            return
        }
        val words = plan.ownFeatures ?: plan.features
        loaded = words
        FIELDS.forEachIndexed { i, f -> form.set(f.key, words.getOrNull(i).orEmpty()) }
        _state.update { it.copy(loading = loading, plan = plan, error = null) }
    }

    companion object {
        private val SPACES = Regex("\\s+")
        /** Five optional points (PlanChoice.MAX_FEATURES), 3–400 characters each: set_plan_features' rules. */
        val FIELDS: List<FieldSpec> = listOf(Res.string.plans_feature_1, Res.string.plans_feature_2, Res.string.plans_feature_3, Res.string.plans_feature_4, Res.string.plans_feature_5)
            .mapIndexed { i, label -> FieldSpec("point${i + 1}", FieldType.Multiline, label, minLength = PlanChoice.FEATURE_MIN, maxLength = PlanChoice.FEATURE_MAX) }
    }
}
