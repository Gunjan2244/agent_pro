package com.sledje.agentpro.feature.customers.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sledje.agentpro.core.catalog.CustomerSignup
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.ui.UiState
import com.sledje.agentpro.feature.customers.data.CustomerDraft
import com.sledje.agentpro.feature.customers.data.CustomerDraftRepository
import com.sledje.agentpro.feature.customers.data.DraftSource
import com.sledje.agentpro.feature.customers.data.SignupLinkResult
import com.sledje.agentpro.feature.customers.data.SignupLinks
import com.sledje.agentpro.feature.website.data.Site
import com.sledje.agentpro.feature.website.data.SiteLinks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Where a customer sign-up link stands, from the draft's timestamps (pure, unit-tested). "On the form now" means
 * the customer's page was seen within `CustomerSignup.presenceSeconds`.
 */
sealed interface Presence {
    data object OnFormNow : Presence

    /** Opened before; [at] is the last time the page was seen (or first opened). */
    data class LastSeen(val at: Instant, val openedAt: Instant?) : Presence

    data object NotOpened : Presence

    data class Expired(val at: Instant) : Presence
}

/** The supporting line of an Incomplete forms row. */
sealed interface DraftLine {
    /** "Filled in by you · {relative time}". */
    data class ByYou(val updatedAt: Instant) : DraftLine

    /** "Customer link · …" with the link's [presence]. */
    data class CustomerLink(val presence: Presence) : DraftLine
}

object IncompleteForms {
    fun isOnForm(draft: CustomerDraft, now: Instant): Boolean {
        val seen = draft.customerSeenAt ?: return false
        return now - seen <= CustomerSignup.presenceSeconds.seconds
    }

    fun presence(draft: CustomerDraft, now: Instant): Presence {
        if (isOnForm(draft, now)) return Presence.OnFormNow
        draft.linkExpiresAt?.let { if (it <= now) return Presence.Expired(it) }
        val last = draft.customerSeenAt ?: draft.openedAt
        return if (last != null) Presence.LastSeen(last, draft.openedAt) else Presence.NotOpened
    }

    fun line(draft: CustomerDraft, now: Instant): DraftLine =
        if (draft.source == DraftSource.Agent) DraftLine.ByYou(draft.updatedAt) else DraftLine.CustomerLink(presence(draft, now))
}

/** The "Incomplete forms" screen's one-shot messages. */
sealed interface IncompleteFormsEvent {
    /** Deleted; the snackbar offers Undo for [id]. */
    data class Deleted(val id: String) : IncompleteFormsEvent

    data class Failed(val error: AppError) : IncompleteFormsEvent
}

/** Incomplete forms (docs/UX.md, Customers tab): unfinished forms newest first; delete is a soft delete with Undo. */
class IncompleteFormsViewModel(private val drafts: CustomerDraftRepository) : ViewModel() {
    val state: StateFlow<UiState<List<CustomerDraft>>> = drafts.observeIncomplete()
        .map { if (it.isEmpty()) UiState.Empty else UiState.Content(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private val _event = MutableStateFlow<IncompleteFormsEvent?>(null)
    val event: StateFlow<IncompleteFormsEvent?> = _event.asStateFlow()

    fun delete(id: String) {
        viewModelScope.launch {
            _event.value = when (val r = drafts.delete(id)) {
                is Result.Ok -> IncompleteFormsEvent.Deleted(id)
                is Result.Err -> IncompleteFormsEvent.Failed(r.error)
            }
        }
    }

    fun undoDelete(id: String) {
        viewModelScope.launch {
            val r = drafts.restore(id)
            if (r is Result.Err) _event.value = IncompleteFormsEvent.Failed(r.error)
        }
    }

    fun eventShown() { _event.value = null }
}

/** Why a link could not be made or renewed (one line under the buttons). */
sealed interface SignupNotice {
    data class Failed(val error: AppError) : SignupNotice
    data object NoSite : SignupNotice
    data object TooMany : SignupNotice
    data object Unavailable : SignupNotice
}

/** The screen between "Send the customer a link" and the live screen: creates the link, then moves on. */
sealed interface SignupStartState {
    data object Creating : SignupStartState
    data class Failed(val notice: SignupNotice) : SignupStartState

    /** The link exists; the screen replaces itself with the live screen, passing the one-time [token]. */
    data class Created(val draftId: String, val token: String) : SignupStartState
}

class SignupStartViewModel(private val drafts: CustomerDraftRepository) : ViewModel() {
    private val _state = MutableStateFlow<SignupStartState>(SignupStartState.Creating)
    val state: StateFlow<SignupStartState> = _state.asStateFlow()

    init { create() }

    fun create() {
        _state.value = SignupStartState.Creating
        viewModelScope.launch {
            _state.value = when (val r = drafts.createLink()) {
                is Result.Err -> SignupStartState.Failed(SignupNotice.Failed(r.error))
                is Result.Ok -> when (val link = r.value) {
                    is SignupLinkResult.Created -> SignupStartState.Created(link.draftId, link.token)
                    SignupLinkResult.NoSite -> SignupStartState.Failed(SignupNotice.NoSite)
                    SignupLinkResult.TooMany -> SignupStartState.Failed(SignupNotice.TooMany)
                    SignupLinkResult.Unavailable -> SignupStartState.Failed(SignupNotice.Unavailable)
                }
            }
        }
    }
}

/** What the live screen shows. */
sealed interface SignupLiveState {
    data object Loading : SignupLiveState

    /** The draft was deleted (here or elsewhere). */
    data object Gone : SignupLiveState

    data class Shown(
        val draft: CustomerDraft,
        /** The link to share; null when the token isn't known any more (reopened later) or there is no site. */
        val url: String?,
        val busy: Boolean = false,
        val notice: SignupNotice? = null,
    ) : SignupLiveState
}

/**
 * The live sign-up screen (docs/UX.md, Customers tab). The token is only known right after the link was created
 * ([initialToken]) or renewed; a screen opened later offers "Send a new link". Realtime refreshes the draft.
 */
class SignupLiveViewModel(
    val draftId: String,
    initialToken: String?,
    private val drafts: CustomerDraftRepository,
    site: Flow<Site?>,
) : ViewModel() {
    private val token = MutableStateFlow(initialToken)
    private val busy = MutableStateFlow(false)
    private val notice = MutableStateFlow<SignupNotice?>(null)
    private val refreshed = MutableStateFlow(false)

    private val _share = MutableStateFlow<String?>(null)

    /** The URL to share after a renewal; the screen shares it, then calls [shareHandled]. */
    val share: StateFlow<String?> = _share.asStateFlow()

    private var latestSlug: String? = null

    val state: StateFlow<SignupLiveState> = combine(
        drafts.observe(draftId),
        site,
        token,
        combine(busy, notice, refreshed) { b, n, r -> Triple(b, n, r) },
    ) { draft, s, t, status ->
        val (isBusy, shownNotice, wasRefreshed) = status
        latestSlug = s?.slug
        when {
            draft == null -> if (wasRefreshed) SignupLiveState.Gone else SignupLiveState.Loading
            else -> {
                val url = s?.slug?.let { slug -> t?.let { SignupLinks.joinUrl(SiteLinks.url(slug), it) } }
                SignupLiveState.Shown(draft, url, isBusy, shownNotice)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SignupLiveState.Loading)

    init {
        viewModelScope.launch {
            drafts.refresh() // best effort; realtime keeps it fresh afterwards
            refreshed.value = true
        }
    }

    /** "Send a new link": `customer_signup_renew`, then the share sheet. */
    fun renew() {
        if (busy.value) return
        busy.value = true
        notice.value = null
        viewModelScope.launch {
            when (val r = drafts.renewLink(draftId)) {
                is Result.Err -> notice.value = SignupNotice.Failed(r.error)
                is Result.Ok -> when (val link = r.value) {
                    is SignupLinkResult.Created -> {
                        token.value = link.token
                        _share.value = latestSlug?.let { SignupLinks.joinUrl(SiteLinks.url(it), link.token) }
                    }
                    SignupLinkResult.NoSite -> notice.value = SignupNotice.NoSite
                    SignupLinkResult.TooMany -> notice.value = SignupNotice.TooMany
                    SignupLinkResult.Unavailable -> notice.value = SignupNotice.Unavailable
                }
            }
            busy.value = false
        }
    }

    fun shareHandled() { _share.value = null }
}
