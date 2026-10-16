package com.sledje.agentpro.feature.website.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.ui.UiState
import com.sledje.agentpro.feature.settings.data.Profile
import com.sledje.agentpro.feature.settings.data.ProfileRepository
import com.sledje.agentpro.feature.website.data.PublishIssue
import com.sledje.agentpro.feature.website.data.PublishIssues
import com.sledje.agentpro.feature.website.data.PublishOutcome
import com.sledje.agentpro.feature.website.data.Site
import com.sledje.agentpro.feature.website.data.SiteRepository
import com.sledje.agentpro.feature.website.data.SiteStatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The outcome of the latest Publish (docs/UX.md, Website tab: sticky bar; Preview). */
sealed interface PublishState {
    data object Idle : PublishState
    data object Publishing : PublishState

    /** One-shot: "Your website is live". */
    data object Live : PublishState

    /** One-shot: `publish_site` refused the draft; the Content screen lists [count] things to fix. */
    data class NeedsFixes(val count: Int) : PublishState

    /** One-shot: the call failed (offline, read-only, …). */
    data class Failed(val error: AppError) : PublishState
}

/**
 * The Website tab's state, shared by the hub's sub-screens (scoped to the [com.sledje.agentpro.feature.website.WebsiteRoute]
 * back-stack entry): the site, the agent's public profile, this week's views, Publish, and the
 * `publish_site` errors mapped onto sections and fields (cleared section by section as the agent saves).
 */
class WebsiteHubViewModel(
    private val sites: SiteRepository,
    profiles: ProfileRepository,
    stats: SiteStatsRepository,
) : ViewModel() {
    val site: StateFlow<UiState<Site>> = sites.observeMine()
        .map { if (it == null) UiState.Empty else UiState.Content(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    val profile: StateFlow<Profile?> = profiles.observeMine().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val weekViews: StateFlow<Int> = stats.observeWeekViews().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _issues = MutableStateFlow<List<PublishIssue>>(emptyList())
    val issues: StateFlow<List<PublishIssue>> = _issues.asStateFlow()

    private val _publish = MutableStateFlow<PublishState>(PublishState.Idle)
    val publish: StateFlow<PublishState> = _publish.asStateFlow()

    fun publish() {
        if (_publish.value == PublishState.Publishing) return
        _publish.value = PublishState.Publishing
        viewModelScope.launch {
            _publish.value = when (val result = sites.publish()) {
                is Result.Ok -> when (val outcome = result.value) {
                    is PublishOutcome.Published -> {
                        _issues.value = emptyList()
                        PublishState.Live
                    }
                    is PublishOutcome.Invalid -> {
                        val resolved = PublishIssues.resolve(outcome.errors)
                        _issues.value = resolved
                        PublishState.NeedsFixes(resolved.size.coerceAtLeast(1))
                    }
                }
                is Result.Err -> PublishState.Failed(result.error)
            }
        }
    }

    /** The issues of one section (Content row flag, section editor list). */
    fun issuesFor(sectionKey: String): List<PublishIssue> = _issues.value.filter { it.section.key == sectionKey }

    /** After the agent saved [sectionKey], its old server errors no longer apply. */
    fun clearIssues(sectionKey: String) = _issues.update { list -> list.filterNot { it.section.key == sectionKey } }

    fun consumePublishResult() = _publish.update { if (it == PublishState.Publishing) it else PublishState.Idle }
}
