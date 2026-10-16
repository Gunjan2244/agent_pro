package com.sledje.agentpro.feature.enquiries.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sledje.agentpro.core.catalog.EnquiryStatus
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.feature.customers.data.Customer
import com.sledje.agentpro.feature.customers.data.CustomerRepository
import com.sledje.agentpro.feature.enquiries.data.Enquiry
import com.sledje.agentpro.feature.enquiries.data.EnquiryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Instant

/** What the enquiry detail screen shows. */
sealed interface EnquiryDetailState {
    data object Loading : EnquiryDetailState

    /** Deleted (here or elsewhere). While [canUndo], the snackbar offers Undo. */
    data class Gone(val canUndo: Boolean) : EnquiryDetailState

    /** [customer] is the converted customer, when it still exists ("View customer"). */
    data class Shown(val enquiry: Enquiry, val customer: Customer?) : EnquiryDetailState
}

/** One-shot results the screen shows as snackbars. */
sealed interface EnquiryDetailEvent {
    /** The status changed; Undo restores [previous] (and its follow-up date). */
    data class StatusChanged(val status: EnquiryStatus, val previous: EnquiryStatus, val previousFollowUp: Instant?) : EnquiryDetailEvent
    data object Deleted : EnquiryDetailEvent
    data class Failed(val error: AppError) : EnquiryDetailEvent
}

/**
 * Enquiry detail (docs/UX.md): opening it sets `seen_at`; status changes apply at once with Undo;
 * Follow-up asks for a date and time; Delete is a soft delete with Undo. Convert opens the customer
 * form (CustomerFormViewModel does the conversion).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EnquiryDetailViewModel(
    val id: String,
    private val enquiries: EnquiryRepository,
    customers: CustomerRepository,
) : ViewModel() {
    private val deletedHere = MutableStateFlow(false)

    private val _event = MutableStateFlow<EnquiryDetailEvent?>(null)
    val event: StateFlow<EnquiryDetailEvent?> = _event.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private var loaded = false

    val state: StateFlow<EnquiryDetailState> = combine(
        enquiries.observe(id),
        enquiries.observe(id).flatMapLatest { e -> e?.customerId?.let { customers.observe(it) } ?: flowOf(null) },
        deletedHere,
    ) { enquiry, customer, deleted ->
        when {
            enquiry != null -> { loaded = true; EnquiryDetailState.Shown(enquiry, customer) }
            else -> EnquiryDetailState.Gone(canUndo = deleted)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, EnquiryDetailState.Loading)

    init {
        viewModelScope.launch {
            val first = enquiries.observe(id).first()
            if (first != null && first.unseen) enquiries.markSeen(id)
        }
    }

    private val current: Enquiry? get() = (state.value as? EnquiryDetailState.Shown)?.enquiry

    /** Applies a status at once (Follow-up comes with its date and time); the snackbar offers Undo. */
    fun setStatus(status: EnquiryStatus, followUpAt: Instant? = null) {
        val before = current ?: return
        if (status == before.status && followUpAt == null) return
        launchWrite {
            enquiries.setStatus(id, status, followUpAt).also {
                if (it is Result.Ok) _event.value = EnquiryDetailEvent.StatusChanged(status, before.status, before.followUpAt)
            }
        }
    }

    fun undoStatus(event: EnquiryDetailEvent.StatusChanged) = launchWrite {
        enquiries.setStatus(id, event.previous).let { r ->
            if (r is Result.Ok && r.value.followUpAt != event.previousFollowUp) enquiries.setFollowUp(id, event.previousFollowUp) else r
        }
    }

    fun setFollowUp(at: Instant?) = launchWrite {
        // A follow-up date makes the enquiry a Follow-up (unless it's already closed or converted).
        val e = current
        if (at != null && e != null && !e.status.terminal && e.status != EnquiryStatus.FollowUp) {
            enquiries.setStatus(id, EnquiryStatus.FollowUp, at)
        } else {
            enquiries.setFollowUp(id, at)
        }
    }

    fun delete() = launchWrite {
        enquiries.delete(id).also { if (it is Result.Ok) { deletedHere.value = true; _event.value = EnquiryDetailEvent.Deleted } }
    }

    fun undoDelete() = launchWrite { enquiries.restore(id).also { if (it is Result.Ok) deletedHere.value = false } }

    /** The Undo window closed: Undo is no longer offered. */
    fun undoExpired() { deletedHere.value = false }

    fun eventShown() { _event.value = null }

    private fun launchWrite(block: suspend () -> Result<*, AppError>) {
        viewModelScope.launch {
            _busy.update { true }
            val result = block()
            _busy.update { false }
            if (result is Result.Err) _event.value = EnquiryDetailEvent.Failed(result.error)
        }
    }

    /** True once the enquiry has been shown at least once (a Gone state after that means it was deleted). */
    val wasLoaded: Boolean get() = loaded

    companion object {
        /** "Copy details": name, phone, email, interest label and message, one per line. */
        fun copyText(e: Enquiry, interestLabel: String?): String =
            listOfNotNull(e.name, e.phone, e.email, interestLabel, e.message).joinToString("\n")
    }
}

/** A converted enquiry with a live customer shows "View customer"; otherwise "Convert to customer". */
val EnquiryDetailState.Shown.canViewCustomer: Boolean get() = customer != null
