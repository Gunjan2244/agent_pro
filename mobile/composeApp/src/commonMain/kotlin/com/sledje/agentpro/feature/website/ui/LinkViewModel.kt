package com.sledje.agentpro.feature.website.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.forms.Validators
import com.sledje.agentpro.feature.onboarding.ui.SlugStatus
import com.sledje.agentpro.feature.website.data.Site
import com.sledje.agentpro.feature.website.data.SiteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The Change link dialog (docs/UX.md, Link & domain: validates and checks availability live). */
data class ChangeLinkState(
    val slug: String,
    val status: SlugStatus,
    val saving: Boolean = false,
    val error: AppError? = null,
) {
    fun canSave(current: String?): Boolean =
        !saving && slug != current && (status == SlugStatus.Available || status is SlugStatus.Unchecked)
}

data class LinkUiState(
    val site: Site? = null,
    /** Non-null while the Change link dialog is open. */
    val change: ChangeLinkState? = null,
    /** One-shot: the link was changed ("Link changed"). */
    val changed: Boolean = false,
)

/**
 * Link & domain, free-link part (docs/UX.md): the current `agentpro.site/a/{slug}` with Copy, Share and
 * QR, and Change link. The new slug is checked locally (format, catalog `reservedSlugs`) and then with
 * `is_slug_available` after a 300 ms pause, like onboarding step 3. Custom domains are Phase 10.
 */
class LinkViewModel(
    private val sites: SiteRepository,
    private val debounceMillis: Long = SLUG_DEBOUNCE_MILLIS,
) : ViewModel() {
    private val _state = MutableStateFlow(LinkUiState())
    val state: StateFlow<LinkUiState> = _state.asStateFlow()
    private var checkJob: Job? = null

    init {
        viewModelScope.launch { sites.observeMine().collect { s -> _state.update { it.copy(site = s) } } }
    }

    fun openChange() {
        val slug = _state.value.site?.slug ?: return
        _state.update { it.copy(change = ChangeLinkState(slug, SlugStatus.Available)) }
    }

    fun dismissChange() {
        checkJob?.cancel()
        _state.update { it.copy(change = null) }
    }

    fun onSlugInput(input: String) {
        val change = _state.value.change ?: return
        val slug = input.trim().lowercase()
        checkJob?.cancel()
        val local = if (slug.isEmpty()) null else Validators.slug(slug)
        val status = when {
            slug.isEmpty() -> SlugStatus.Empty
            local != null -> SlugStatus.Invalid(local)
            slug == _state.value.site?.slug -> SlugStatus.Available
            else -> SlugStatus.Checking
        }
        _state.update { it.copy(change = change.copy(slug = slug, status = status, error = null)) }
        if (status != SlugStatus.Checking) return
        checkJob = viewModelScope.launch {
            delay(debounceMillis)
            val checked = when (val result = sites.isSlugAvailable(slug)) {
                is Result.Ok -> if (result.value) SlugStatus.Available else SlugStatus.Taken
                is Result.Err -> SlugStatus.Unchecked(result.error)
            }
            _state.update { s -> s.change?.takeIf { it.slug == slug }?.let { s.copy(change = it.copy(status = checked)) } ?: s }
        }
    }

    fun confirmChange() {
        val change = _state.value.change ?: return
        if (!change.canSave(_state.value.site?.slug)) return
        _state.update { it.copy(change = change.copy(saving = true, error = null)) }
        viewModelScope.launch {
            when (val result = sites.changeSlug(change.slug)) {
                is Result.Ok -> _state.update { it.copy(change = null, changed = true) }
                is Result.Err -> _state.update { s ->
                    val c = s.change ?: return@update s
                    if (result.error == AppError.Conflict) s.copy(change = c.copy(saving = false, status = SlugStatus.Taken))
                    else s.copy(change = c.copy(saving = false, error = result.error))
                }
            }
        }
    }

    fun consumeChanged() = _state.update { it.copy(changed = false) }

    companion object {
        const val SLUG_DEBOUNCE_MILLIS = 300L
    }
}
