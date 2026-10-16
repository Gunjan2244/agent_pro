package com.sledje.agentpro.feature.website.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.forms.SectionSpec
import com.sledje.agentpro.feature.website.data.Site
import com.sledje.agentpro.feature.website.data.SiteContent
import com.sledje.agentpro.feature.website.data.SiteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/** One row of the Content screen. */
data class SectionRow(
    val section: SectionSpec,
    val hidden: Boolean,
    val summary: SiteContent.Summary,
) {
    /** Pinned sections (hero, contact) are always on and keep their place. */
    val pinned: Boolean get() = section.pinned
}

data class ContentUiState(
    val loading: Boolean = true,
    val rows: List<SectionRow> = emptyList(),
    /** Reorder mode: drag handles on the movable rows; Done saves the order. */
    val reordering: Boolean = false,
    val saving: Boolean = false,
    /** One-shot error of the last save. */
    val error: AppError? = null,
)

/**
 * The Content screen (docs/UX.md, Website: Content screen): one row per schema section with its summary
 * and a visibility switch, and Reorder. Show/hide saves the draft at once; reordering is local until
 * Done, which saves the new `sectionOrder`. Writes only `draft_content`.
 */
class ContentViewModel(private val sites: SiteRepository) : ViewModel() {
    private val _state = MutableStateFlow(ContentUiState())
    val state: StateFlow<ContentUiState> = _state.asStateFlow()

    private var site: Site? = null

    /** The content being reordered (not saved yet). */
    private var reorderDraft: JsonObject? = null

    init {
        viewModelScope.launch {
            sites.observeMine().collect { s ->
                site = s
                if (reorderDraft == null) _state.update { it.copy(loading = false, rows = rowsOf(s?.draftContent)) }
            }
        }
    }

    fun setHidden(section: SectionSpec, hidden: Boolean) {
        val current = site ?: return
        if (section.pinned || _state.value.saving || _state.value.reordering) return
        save(SiteContent.withHidden(current.draftContent, section, hidden))
    }

    fun startReorder() {
        val current = site ?: return
        if (_state.value.saving) return
        reorderDraft = current.draftContent
        _state.update { it.copy(reordering = true) }
    }

    /** Moves a movable row: [from] and [to] index the rows without the pinned ones. */
    fun move(from: Int, to: Int) {
        val draft = reorderDraft ?: return
        val moved = SiteContent.move(draft, from, to)
        reorderDraft = moved
        _state.update { it.copy(rows = rowsOf(moved)) }
    }

    /** Done: saves the order when it changed. */
    fun finishReorder() {
        val draft = reorderDraft ?: return
        val original = site?.draftContent
        reorderDraft = null
        _state.update { it.copy(reordering = false) }
        if (original != null && SiteContent.orderedSections(original) != SiteContent.orderedSections(draft)) save(draft)
    }

    fun consumeError() = _state.update { it.copy(error = null) }

    private fun save(content: JsonObject) {
        _state.update { it.copy(saving = true, error = null, rows = rowsOf(content)) }
        viewModelScope.launch {
            when (val result = sites.saveDraft(content)) {
                is Result.Ok -> _state.update { it.copy(saving = false) }
                is Result.Err -> _state.update { it.copy(saving = false, error = result.error, rows = rowsOf(site?.draftContent)) }
            }
        }
    }

    private fun rowsOf(content: JsonObject?): List<SectionRow> {
        val c = content ?: return emptyList()
        return SiteContent.orderedSections(c).map { SectionRow(it, SiteContent.isHidden(c, it), SiteContent.summary(c, it)) }
    }
}
