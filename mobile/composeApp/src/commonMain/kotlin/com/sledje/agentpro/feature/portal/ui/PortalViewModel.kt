package com.sledje.agentpro.feature.portal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.KeyValueStore
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.data.StoreKeys
import com.sledje.agentpro.core.format.Phone
import com.sledje.agentpro.feature.customers.data.Customer
import com.sledje.agentpro.feature.portal.data.InviteResult
import com.sledje.agentpro.feature.portal.data.PortalLinks
import com.sledje.agentpro.feature.portal.data.PortalRepository
import com.sledje.agentpro.feature.portal.data.PortalState
import com.sledje.agentpro.feature.portal.data.PortalStatus
import com.sledje.agentpro.feature.website.data.Site
import com.sledje.agentpro.feature.website.data.SiteLinks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

/** A one-line problem under the card's buttons after an action failed. */
sealed interface PortalNotice {
    data class Failed(val error: AppError) : PortalNotice
    data object InvalidPhone : PortalNotice
    data object NoSite : PortalNotice
    data object Unavailable : PortalNotice
}

/** What the "Website login" card shows (docs/UX.md, Customer detail). */
sealed interface WebsiteLoginUiState {
    data object Loading : WebsiteLoginUiState

    /** `portal_status` failed; the card offers Retry. */
    data class Error(val error: AppError) : WebsiteLoginUiState

    /** No valid Indian mobile on the customer: the card links to Edit customer. */
    data class NeedsPhone(val customerName: String) : WebsiteLoginUiState

    /** No published website. */
    data class NeedsSite(val customerName: String) : WebsiteLoginUiState

    data class Ready(
        val customerName: String,
        val status: PortalStatus,
        val online: Boolean = true,
        /** An invite or turn-off is running. */
        val busy: Boolean = false,
        val notice: PortalNotice? = null,
        /** The clock for "Signed in 3h ago"; fixed in screenshots. */
        val now: Instant = Clock.System.now(),
    ) : WebsiteLoginUiState
}

/** The message to share after an invite: the card fills in the words (EN/HI strings). */
data class PortalShare(val url: String, val customerName: String, val agentName: String)

/** The "Website login" card's state machine. Online only; nothing is cached. */
class PortalViewModel(
    private val customerId: String,
    private val portal: PortalRepository,
    customer: Flow<Customer?>,
    site: Flow<Site?>,
    online: Flow<Boolean>,
    private val agentName: Flow<String?>,
    private val now: () -> Instant = { Clock.System.now() },
    /** Gets the "invite sent" mark for Home's checklist; null in tests that don't care. */
    private val store: KeyValueStore? = null,
) : ViewModel() {
    private sealed interface Remote {
        data object Loading : Remote
        data class Loaded(val status: PortalStatus) : Remote
        data class Failed(val error: AppError) : Remote
    }

    private val remote = MutableStateFlow<Remote>(Remote.Loading)
    private val busy = MutableStateFlow(false)
    private val notice = MutableStateFlow<PortalNotice?>(null)
    private val latestSite = MutableStateFlow<Site?>(null)
    private val latestAgent = MutableStateFlow<String?>(null)
    private val latestCustomer = MutableStateFlow<Customer?>(null)

    private val _share = MutableStateFlow<PortalShare?>(null)

    /** Set after a successful invite; the card shares it, then calls [shareHandled]. */
    val share: StateFlow<PortalShare?> = _share.asStateFlow()

    val state: StateFlow<WebsiteLoginUiState> = combine(customer, site, online, remote, combine(busy, notice) { b, n -> b to n }) { c, s, on, r, bn ->
        latestSite.value = s
        latestCustomer.value = c
        when {
            c == null -> WebsiteLoginUiState.Loading
            c.phone == null || !Phone.isValid(c.phone) -> WebsiteLoginUiState.NeedsPhone(c.name)
            r is Remote.Loading -> WebsiteLoginUiState.Loading
            r is Remote.Failed -> WebsiteLoginUiState.Error(r.error)
            // An existing login keeps showing; only starting one needs a published site.
            s?.isPublished != true && (r as Remote.Loaded).status.state == PortalState.None -> WebsiteLoginUiState.NeedsSite(c.name)
            else -> WebsiteLoginUiState.Ready((c.name), (r as Remote.Loaded).status, on, bn.first, bn.second, now())
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WebsiteLoginUiState.Loading)

    init {
        viewModelScope.launch { agentName.collect { latestAgent.value = it } }
        load()
    }

    fun load() {
        remote.value = Remote.Loading
        viewModelScope.launch { fetch() }
    }

    private suspend fun fetch(): Boolean = when (val r = portal.status(customerId)) {
        is Result.Ok -> { remote.value = Remote.Loaded(r.value); true }
        is Result.Err -> { remote.value = Remote.Failed(r.error); false }
    }

    /** Send login link / Send again / Send new link: `portal_invite`, then the share sheet. */
    fun send() {
        if (busy.value) return
        val site = latestSite.value
        val customer = latestCustomer.value ?: return
        busy.value = true
        notice.value = null
        viewModelScope.launch {
            when (val r = portal.invite(customerId)) {
                is Result.Err -> notice.value = PortalNotice.Failed(r.error)
                is Result.Ok -> when (val invite = r.value) {
                    InviteResult.InvalidPhone -> notice.value = PortalNotice.InvalidPhone
                    InviteResult.NoSite -> notice.value = PortalNotice.NoSite
                    InviteResult.Unavailable -> notice.value = PortalNotice.Unavailable
                    is InviteResult.Created -> {
                        store?.put(StoreKeys.PORTAL_INVITED, "true") // Home setup checklist: "Give a customer their login"
                        val previous = (remote.value as? Remote.Loaded)?.status
                        remote.value = Remote.Loaded(
                            PortalStatus(PortalState.Invited, inviteExpiresAt = invite.expiresAt, sharedWith = previous?.sharedWith.orEmpty(), portalAvailable = previous?.portalAvailable ?: true),
                        )
                        val slug = site?.slug
                        if (slug != null) {
                            _share.value = PortalShare(
                                PortalLinks.startUrl(SiteLinks.url(slug), invite.token),
                                customer.name,
                                latestAgent.value.orEmpty(),
                            )
                        } else {
                            notice.value = PortalNotice.NoSite
                        }
                        fetch() // best effort: a failed refresh keeps the optimistic "invited" status
                        if (remote.value is Remote.Failed) remote.value = Remote.Loaded(
                            PortalStatus(PortalState.Invited, inviteExpiresAt = invite.expiresAt, sharedWith = previous?.sharedWith.orEmpty(), portalAvailable = previous?.portalAvailable ?: true),
                        )
                    }
                }
            }
            busy.value = false
        }
    }

    /** Turn off: `portal_revoke`. */
    fun turnOff() {
        if (busy.value) return
        busy.value = true
        notice.value = null
        viewModelScope.launch {
            when (val r = portal.revoke(customerId)) {
                is Result.Ok -> remote.value = Remote.Loaded(PortalStatus.None.copy(portalAvailable = (remote.value as? Remote.Loaded)?.status?.portalAvailable ?: true))
                is Result.Err -> notice.value = PortalNotice.Failed(r.error)
            }
            busy.value = false
        }
    }

    fun shareHandled() { _share.value = null }

    fun noticeShown() { notice.value = null }
}
