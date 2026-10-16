package com.sledje.agentpro.feature.enquiries.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sledje.agentpro.core.catalog.EnquirySource
import com.sledje.agentpro.core.catalog.Interest
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.forms.FieldDefault
import com.sledje.agentpro.core.forms.FieldOption
import com.sledje.agentpro.core.forms.FieldSpec
import com.sledje.agentpro.core.forms.FieldType
import com.sledje.agentpro.core.forms.FormState
import com.sledje.agentpro.feature.enquiries.data.EnquiryRepository
import com.sledje.agentpro.feature.enquiries.data.NewEnquiry
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.cat_field_name
import com.sledje.agentpro.resources.enquiry_interest
import com.sledje.agentpro.resources.enquiry_message
import com.sledje.agentpro.resources.enquiry_phone
import com.sledje.agentpro.resources.enquiry_source
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Saving state shared by the CRM forms. [savedId] is set once, when the save succeeded. */
data class SaveState(val saving: Boolean = false, val error: AppError? = null, val savedId: String? = null)

/**
 * "Add enquiry" (docs/UX.md, Enquiries — list: name, phone and interest; source and message under
 * More details). Sources come from catalog `enquirySources` without `website`, which only the site
 * form sets; the default is the first manual source.
 */
class AddEnquiryViewModel(private val repo: EnquiryRepository) : ViewModel() {
    val form = FormState(FIELDS)

    private val _state = MutableStateFlow(SaveState())
    val state: StateFlow<SaveState> = _state.asStateFlow()

    fun save() {
        if (_state.value.saving || !form.validateAll()) return
        val v = form.values()
        _state.value = SaveState(saving = true)
        viewModelScope.launch {
            val result = repo.add(
                NewEnquiry(
                    name = v[NAME] as String,
                    phone = v[PHONE] as String?,
                    interest = v[INTEREST] as String?,
                    source = v[SOURCE] as String? ?: DEFAULT_SOURCE.key,
                    message = v[MESSAGE] as String?,
                ),
            )
            _state.value = when (result) {
                is Result.Ok -> SaveState(savedId = result.value.id)
                is Result.Err -> SaveState(error = result.error)
            }
        }
    }

    companion object {
        const val NAME = "name"
        const val PHONE = "phone"
        const val INTEREST = "interest"
        const val SOURCE = "source"
        const val MESSAGE = "message"

        /** Manual sources: every active source except the website form's. */
        val MANUAL_SOURCES: List<EnquirySource> get() = EnquirySource.active.filter { it != EnquirySource.Website }
        val DEFAULT_SOURCE: EnquirySource get() = MANUAL_SOURCES.first()

        val FIELDS: List<FieldSpec> = listOf(
            FieldSpec(NAME, FieldType.Text, Res.string.cat_field_name, required = true, maxLength = 80),
            FieldSpec(PHONE, FieldType.Phone, Res.string.enquiry_phone),
            FieldSpec(INTEREST, FieldType.Select, Res.string.enquiry_interest, options = Interest.active.map { FieldOption(it.key, it.label, it.icon) }),
            FieldSpec(
                SOURCE, FieldType.Select, Res.string.enquiry_source, required = true,
                options = MANUAL_SOURCES.map { FieldOption(it.key, it.label, it.icon) },
                default = FieldDefault.Text(DEFAULT_SOURCE.key),
            ),
            FieldSpec(MESSAGE, FieldType.Multiline, Res.string.enquiry_message, maxLength = 500),
        )

        /** Shown at once; the rest are under More details. Source is required but has a default. */
        val VISIBLE = setOf(NAME, PHONE, INTEREST)
    }
}

/** Autosave state of the notes editor. */
enum class NotesStatus { Idle, Saving, Saved, Failed }

/**
 * The notes editor (docs/UX.md, Enquiry detail: "a notes editor with autosave"): saves 800ms after
 * typing stops, and once more when the screen closes ([flush]).
 */
class NotesViewModel(private val id: String, private val repo: EnquiryRepository) : ViewModel() {
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val _status = MutableStateFlow(NotesStatus.Idle)
    val status: StateFlow<NotesStatus> = _status.asStateFlow()

    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()

    private var saved: String = ""
    private var pending: Job? = null
    private var loaded = false

    init {
        viewModelScope.launch {
            val notes = repo.observe(id).first()?.notes.orEmpty()
            saved = notes
            if (!loaded) _text.value = notes
            loaded = true
        }
    }

    fun onChange(value: String) {
        loaded = true
        _text.value = value
        pending?.cancel()
        pending = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MILLIS)
            save()
        }
    }

    /** Saves now if anything changed; the screen awaits it before closing. */
    suspend fun flush() {
        pending?.cancel()
        save()
    }

    private suspend fun save() {
        val value = _text.value
        if (value.trim() == saved.trim()) return
        _status.value = NotesStatus.Saving
        when (val result = repo.setNotes(id, value.trim().ifEmpty { null })) {
            is Result.Ok -> { saved = value; _status.value = NotesStatus.Saved }
            is Result.Err -> { _status.value = NotesStatus.Failed; _error.value = result.error }
        }
    }

    companion object {
        const val AUTOSAVE_DELAY_MILLIS = 800L
        const val MAX_LENGTH = 2000
    }
}
