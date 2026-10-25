package com.sledje.agentpro.feature.website.ui

import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.setup_hint_about
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sledje.agentpro.core.catalog.SiteSchema
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.forms.FieldSpec
import com.sledje.agentpro.core.forms.FieldType
import com.sledje.agentpro.core.forms.FormState
import com.sledje.agentpro.core.forms.SectionSpec
import com.sledje.agentpro.core.platform.ImageAspect
import com.sledje.agentpro.core.platform.PickedImage
import com.sledje.agentpro.feature.website.data.ImageUploader
import com.sledje.agentpro.feature.website.data.PublishIssue
import com.sledje.agentpro.feature.website.data.SiteContent
import com.sledje.agentpro.feature.website.data.SiteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SectionEditorUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    /** The image field (path, see [SectionEditorViewModel.onImagePicked]) being prepared and uploaded. */
    val uploadingField: String? = null,
    /** `publish_site` errors on this section that the editor should point at (cleared by a save). */
    val issues: List<PublishIssue> = emptyList(),
    val error: AppError? = null,
    /** One-shot: saved, close the editor. */
    val saved: Boolean = false,
) {
    val busy: Boolean get() = loading || saving || uploadingField != null
}

/**
 * The section editor (docs/UX.md, Section editor): a FormRenderer built from the generated [SiteSchema]
 * section, filled from the draft. Images go through the photo picker → [ImageUploader] (crop to the
 * field's `aspect`, WebP ≤ 1024 px, upload to `site-media/<uid>/`). Save validates every field
 * (required, maxLength, min/max, formats, maxItems) and writes only the draft.
 *
 * With [issues] from a refused publish, every error shows at once (`validateAll`), and the issues list
 * stays until the section is saved.
 */
class SectionEditorViewModel(
    val section: SectionSpec,
    private val sites: SiteRepository,
    private val uploader: ImageUploader,
    issues: List<PublishIssue> = emptyList(),
    private val onSaved: (String) -> Unit = {},
) : ViewModel() {
    /** Null until the draft has loaded. */
    var form: FormState? = null
        private set

    private val _state = MutableStateFlow(SectionEditorUiState(issues = issues))
    val state: StateFlow<SectionEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val site = sites.observeMine().filterNotNull().first()
            form = FormState(section.fields, SiteContent.sectionValues(site.draftContent, section)).also {
                if (issues.isNotEmpty()) it.validateAll()
            }
            _state.update { it.copy(loading = false) }
            // The offer picker writes its choice straight into the draft; show it here, and keep it on Save.
            sites.observeMine().filterNotNull().collect { latest ->
                val fresh = SiteContent.sectionValues(latest.draftContent, section)
                section.fields.filter { it.type == FieldType.OfferRef || it.type == FieldType.OfferList }
                    .forEach { spec -> form?.takeIf { it.value(spec.key) != fresh[spec.key] }?.set(spec.key, fresh[spec.key]) }
            }
        }
    }

    val isDirty: Boolean get() = form?.isDirty == true

    /**
     * An image was picked for [fieldPath]: a top-level image field key, or `list.index.key` for an image
     * inside a list item (the path FormRenderer passes to `onPickImage`).
     */
    fun onImagePicked(fieldPath: String, image: PickedImage) {
        val (target, spec) = resolveImageField(fieldPath) ?: return
        if (_state.value.uploadingField != null) return
        _state.update { it.copy(uploadingField = fieldPath, error = null) }
        viewModelScope.launch {
            when (val result = uploader.upload(image, ImageAspect.parse(spec.aspect), imageName(spec))) {
                is Result.Ok -> {
                    target.set(spec.key, result.value)
                    target.onFocusLost(spec.key)
                    _state.update { it.copy(uploadingField = null) }
                }
                is Result.Err -> _state.update { it.copy(uploadingField = null, error = result.error) }
            }
        }
    }

    fun save() {
        val f = form ?: return
        if (_state.value.busy) return
        if (!f.validateAll()) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val latest = sites.observeMine().filterNotNull().first()
            val content = SiteContent.withSection(latest.draftContent, section, f.values())
            when (val result = sites.saveDraft(content)) {
                is Result.Ok -> {
                    onSaved(section.key)
                    _state.update { it.copy(saving = false, saved = true, issues = emptyList()) }
                }
                is Result.Err -> _state.update { it.copy(saving = false, error = result.error) }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }

    private fun resolveImageField(path: String): Pair<FormState, FieldSpec>? {
        val f = form ?: return null
        val parts = path.split('.')
        return when (parts.size) {
            1 -> f.fields.firstOrNull { it.key == path }?.let { f to it }
            3 -> {
                val item = parts[1].toIntOrNull()?.let { f.items(parts[0]).getOrNull(it) } ?: return null
                item.fields.firstOrNull { it.key == parts[2] }?.let { item to it }
            }
            else -> null
        }
    }

    private fun imageName(spec: FieldSpec): String = "${section.key}-${spec.key}"
}

/** The schema section for a route key (null for an unknown key). */
fun sectionFor(key: String): SectionSpec? = SiteSchema.section(key)

/** The prompt shown when a section is opened from the Finish setup list; null when that section has none. */
fun setupHintFor(key: String): org.jetbrains.compose.resources.StringResource? =
    if (key == com.sledje.agentpro.feature.website.data.StarterContent.ABOUT) Res.string.setup_hint_about else null
