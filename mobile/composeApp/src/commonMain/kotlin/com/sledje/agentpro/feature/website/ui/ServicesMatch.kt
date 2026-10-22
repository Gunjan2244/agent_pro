package com.sledje.agentpro.feature.website.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sledje.agentpro.core.catalog.Interest
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppBottomSheet
import com.sledje.agentpro.core.designsystem.components.AppCard
import com.sledje.agentpro.core.designsystem.components.PrimaryButton
import com.sledje.agentpro.core.designsystem.components.TertiaryButton
import com.sledje.agentpro.core.forms.FieldOption
import com.sledje.agentpro.core.forms.FieldSpec
import com.sledje.agentpro.core.forms.FieldType
import com.sledje.agentpro.core.forms.FormRenderer
import com.sledje.agentpro.core.forms.FormState
import com.sledje.agentpro.feature.offers.data.CompanyRepository
import com.sledje.agentpro.feature.offers.data.Membership
import com.sledje.agentpro.feature.settings.data.Profile
import com.sledje.agentpro.feature.settings.data.ProfileRepository
import com.sledje.agentpro.feature.website.data.ServicesUpdate
import com.sledje.agentpro.feature.website.data.Site
import com.sledje.agentpro.feature.website.data.SiteRepository
import com.sledje.agentpro.feature.website.data.SiteServices
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.launch_not_now
import com.sledje.agentpro.resources.needs_internet
import com.sledje.agentpro.resources.website_match_add
import com.sledje.agentpro.resources.website_match_apply
import com.sledje.agentpro.resources.website_match_hint
import com.sledje.agentpro.resources.website_match_hint_action
import com.sledje.agentpro.resources.website_match_keep_text
import com.sledje.agentpro.resources.website_match_remove_label
import com.sledje.agentpro.resources.website_match_title
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Test tags of the Content hint and the sheet's Update button. */
const val SERVICES_MATCH_HINT_TAG = "services_match_hint"
const val SERVICES_MATCH_APPLY_TAG = "services_match_apply"

data class ServicesMatchUiState(
    /** What the draft's services would need to match the agent's insurance types; empty when they match. */
    val update: ServicesUpdate = ServicesUpdate(emptyList(), emptyList()),
    /** The "Update your website to match?" sheet is open. */
    val sheetOpen: Boolean = false,
    /** The opt-in choice of services to remove (one multi-select); null when there is nothing to remove. */
    val removeForm: FormState? = null,
    val saving: Boolean = false,
    /** The last failed save (shown in the sheet). */
    val error: AppError? = null,
    /** One-shot: the draft was updated (the screen says "Services updated…"). */
    val applied: Boolean = false,
) {
    /** The draft's services don't match: Website → Content shows its hint. */
    val mismatch: Boolean get() = !update.isEmpty
}

/**
 * "Update your website to match?" (DECISIONS #93; docs/UX.md, Settings → Profile and Website → Content). Compares the
 * draft's services with the agent's insurance types ([SiteServices.diff]) and, when the agent agrees, adds the seeded
 * services of new types and removes only the services the agent ticks; nothing else in the draft changes, and only the
 * draft is written (publishing stays the agent's step).
 * - Settings → Profile calls [typesChanged] after each successful company change: the sheet opens once if the site no
 *   longer matches; "Not now" closes it until the next change.
 * - Website → Content shows a hint while [ServicesMatchUiState.mismatch] (sites made before this change too); its
 *   action calls [open]. [refreshCompanies] loads the agent's links there, since they are kept in memory only.
 */
class ServicesMatchViewModel(
    private val sites: SiteRepository,
    private val companies: CompanyRepository,
    profiles: ProfileRepository,
    refreshCompanies: Boolean = false,
) : ViewModel() {
    private val _state = MutableStateFlow(ServicesMatchUiState())
    val state: StateFlow<ServicesMatchUiState> = _state.asStateFlow()

    private var site: Site? = null
    private var profile: Profile? = null

    init {
        viewModelScope.launch {
            combine(sites.observeMine(), companies.memberships) { s, m -> s to m }.collect { (s, m) ->
                site = s
                _state.update { it.copy(update = compute(s, m)) }
            }
        }
        viewModelScope.launch { profiles.observeMine().collect { profile = it } }
        if (refreshCompanies) viewModelScope.launch { companies.refresh() }
    }

    /** A company link was added, changed or removed: offer the update once if the site no longer matches. */
    fun typesChanged() {
        val update = compute(site, companies.memberships.value)
        _state.update { it.copy(update = update) }
        if (!update.isEmpty) open()
    }

    /** Opens the sheet for the current mismatch (no-op when the services match). */
    fun open() {
        val update = _state.value.update
        if (update.isEmpty) return
        val form = if (update.remove.isEmpty()) null else FormState(listOf(removeField(update.remove)))
        _state.update { it.copy(sheetOpen = true, removeForm = form, error = null) }
    }

    fun dismiss() = _state.update { if (it.saving) it else it.copy(sheetOpen = false, removeForm = null) }

    /** The interests the agent ticked for removal. */
    fun chosenRemovals(): List<String> {
        @Suppress("UNCHECKED_CAST")
        return (_state.value.removeForm?.value(REMOVE) as? List<String>).orEmpty()
    }

    /** Update website: saves the draft with the new services added and the ticked ones removed. */
    fun apply() {
        val current = site ?: return
        val s = _state.value
        if (s.saving) return
        val remove = chosenRemovals().filter { it in s.update.remove }
        if (s.update.add.isEmpty() && remove.isEmpty()) return
        val p = profile
        val content = SiteServices.apply(current.draftContent, s.update.add, remove, p, current.locale.orEmpty(), p?.id.orEmpty())
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val r = sites.saveDraft(content)) {
                is Result.Ok -> _state.update { it.copy(saving = false, sheetOpen = false, removeForm = null, applied = true) }
                is Result.Err -> _state.update { it.copy(saving = false, error = r.error) }
            }
        }
    }

    fun consumeApplied() = _state.update { it.copy(applied = false) }

    private fun compute(s: Site?, memberships: List<Membership>): ServicesUpdate =
        if (s == null) ServicesUpdate(emptyList(), emptyList()) else SiteServices.diff(s.draftContent, SiteServices.agentTypes(memberships))

    companion object {
        const val REMOVE = "remove_services"

        /** "Remove services you no longer deal in": one chip per stale service, none ticked (removal is opt-in). */
        fun removeField(interests: List<String>): FieldSpec = FieldSpec(
            REMOVE, FieldType.MultiSelect, Res.string.website_match_remove_label,
            options = interests.mapNotNull { Interest.fromKey(it) }.map { FieldOption(it.key, it.label, it.icon) },
        )
    }
}

/** The hint at the top of Website → Content while the services don't match the agent's insurance types. */
@Composable
fun ServicesMatchHint(onUpdate: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth().testTag(SERVICES_MATCH_HINT_TAG)) {
        Text(stringResource(Res.string.website_match_hint), style = AgentProTheme.text.body)
        TertiaryButton(stringResource(Res.string.website_match_hint_action), onUpdate, enabled = enabled)
    }
}

/**
 * The "Update your website to match?" sheet: the services that will be added (by name), the opt-in removal chips
 * (FormRenderer), a note that the agent's own text stays, then **Update website** and **Not now**.
 */
@Composable
fun ServicesMatchSheet(state: ServicesMatchUiState, onApply: () -> Unit, onDismiss: () -> Unit) {
    if (!state.sheetOpen) return
    AppBottomSheet(stringResource(Res.string.website_match_title), onDismiss) { ServicesMatchContent(state, onApply, onDismiss) }
}

/** The sheet's body (also rendered alone by the screenshot tests). */
@Composable
fun ServicesMatchContent(state: ServicesMatchUiState, onApply: () -> Unit, onDismiss: () -> Unit) {
    val online = isOnline()
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        if (state.update.add.isNotEmpty()) {
            val names = state.update.add.mapNotNull { Interest.fromKey(it) }.map { stringResource(it.label) }.joinToString(", ")
            Text(stringResource(Res.string.website_match_add, names), style = AgentProTheme.text.body)
        }
        state.removeForm?.let { FormRenderer(it, enabled = !state.saving) }
        Text(stringResource(Res.string.website_match_keep_text), style = AgentProTheme.text.bodyMuted)
        state.error?.let { ErrorText(stringResource(websiteErrorTextRes(it))) }
        @Suppress("UNCHECKED_CAST")
        val ticked = (state.removeForm?.value(ServicesMatchViewModel.REMOVE) as? List<String>).orEmpty()
        PrimaryButton(
            stringResource(Res.string.website_match_apply), onApply, Modifier.fillMaxWidth().testTag(SERVICES_MATCH_APPLY_TAG),
            enabled = online && !state.saving && (state.update.add.isNotEmpty() || ticked.isNotEmpty()),
        )
        if (!online) Text(stringResource(Res.string.needs_internet), style = AgentProTheme.text.bodyMuted)
        TertiaryButton(stringResource(Res.string.launch_not_now), onDismiss, Modifier.fillMaxWidth())
    }
}
