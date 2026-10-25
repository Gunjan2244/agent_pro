package com.sledje.agentpro.feature.offers.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sledje.agentpro.core.catalog.Offers
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.format.Dates
import com.sledje.agentpro.feature.offers.data.CompanyOffer
import com.sledje.agentpro.feature.offers.data.CompanyRepository
import com.sledje.agentpro.feature.offers.data.LiveOffers
import com.sledje.agentpro.feature.offers.data.OfferDraft
import com.sledje.agentpro.feature.offers.data.OfferRef
import com.sledje.agentpro.feature.website.data.Site
import com.sledje.agentpro.feature.website.data.SiteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Which site field the picker edits (the [com.sledje.agentpro.feature.offers.OfferPickerRoute] pair). */
enum class PickerMode {
    /** `offers.items`: "Offers on your site". */
    Items,

    /** `hero.featuredOffer`: one choice. */
    Featured,
    ;

    companion object {
        fun of(section: String, field: String): PickerMode? = when {
            section == "offers" && field == "items" -> Items
            section == "hero" && field == "featuredOffer" -> Featured
            else -> null
        }
    }
}

/** Why a placed offer won't show on the site. */
enum class OfferFlag { Ended, Unavailable }

/**
 * One offer row. [title] is blank for a placed offer that no longer exists. [companyName] is shown only inside the app and
 * never reaches the site (DECISIONS #77).
 */
data class OfferRow(
    val ref: OfferRef,
    val title: String,
    val summary: String,
    val endsOn: LocalDate?,
    /** A `catalog.interests` key. */
    val interest: String?,
    val companyName: String?,
    val flag: OfferFlag? = null,
    val featured: Boolean = false,
    /** Edit is offered (a live company offer to copy, or one of the agent's own). */
    val editable: Boolean = false,
)

/** Where to go next after a change. */
sealed interface PickerEvent {
    /** Open the section editor to edit the agent's own offers (a copy was just made when [copied]). */
    data class OpenEditor(val copied: Boolean) : PickerEvent

    /** The featured choice was saved: close the picker. */
    data object Done : PickerEvent
}

data class OfferPickerState(
    val mode: PickerMode,
    val cap: Int = Offers.maxSectionItems,
    /** The site draft or `my_company_offers` is still loading. */
    val loading: Boolean = true,
    val loadError: AppError? = null,
    /** `offers.items`, in order. */
    val onSite: List<OfferRow> = emptyList(),
    /** Live company offers that aren't on the site yet. */
    val available: List<OfferRow> = emptyList(),
    val featured: OfferRef? = null,
    /** Null until `my_company_offers` has answered. */
    val hasApprovedCompany: Boolean? = null,
    val saving: Boolean = false,
    val error: AppError? = null,
    /** A company offer waiting for the "make your own copy" confirmation. */
    val confirmCopy: OfferRef? = null,
    val event: PickerEvent? = null,
) {
    val full: Boolean get() = onSite.size >= cap
    val noCompany: Boolean get() = loadError == null && hasApprovedCompany == false
}

/**
 * The offer picker (DECISIONS #104; docs/OFFERS.md, Place offers). Every change is saved straight to the draft with
 * [SiteRepository.saveDraft], on top of the latest draft, as the section editor does; there is no Save button.
 */
@OptIn(ExperimentalUuidApi::class)
class OfferPickerViewModel(
    val mode: PickerMode,
    private val sites: SiteRepository,
    private val companies: CompanyRepository,
    private val cap: Int = Offers.maxSectionItems,
    private val today: () -> LocalDate = { Dates.today() },
    private val newId: () -> String = { Uuid.random().toString() },
) : ViewModel() {
    private var site: Site? = null
    private var live: LiveOffers? = null
    private var loadError: AppError? = null
    private var loaded = false

    private val _state = MutableStateFlow(OfferPickerState(mode, cap))
    val state: StateFlow<OfferPickerState> = _state.asStateFlow()

    init {
        viewModelScope.launch { sites.observeMine().filterNotNull().collect { site = it; recompute() } }
        load()
    }

    fun load() {
        loadError = null
        viewModelScope.launch {
            when (val r = companies.liveOffers()) {
                is Result.Ok -> live = r.value
                is Result.Err -> loadError = r.error
            }
            recompute(loaded = true)
        }
    }

    private fun recompute(loaded: Boolean = false) {
        if (loaded) this.loaded = true
        val s = site
        _state.update { old ->
            if (s == null) return@update old.copy(loading = true)
            val locale = s.locale ?: "en"
            val content = s.draftContent
            val refs = OfferDraft.items(content)
            val featured = OfferDraft.featured(content)
            val custom = OfferDraft.custom(content).associateBy { it.id }
            val offers = live?.offers.orEmpty().associateBy { it.id }
            val now = today()
            val rows = refs.map { ref ->
                when (ref.source) {
                    OfferRef.AGENT -> custom[ref.id]?.let { c ->
                        OfferRow(ref, c.title, c.summary, c.endsOn, c.interest, null, if (c.endsOn != null && c.endsOn < now) OfferFlag.Ended else null, ref == featured, editable = true)
                    } ?: OfferRow(ref, "", "", null, null, null, OfferFlag.Unavailable, ref == featured)
                    else -> offers[ref.id]?.let { o ->
                        val end = endDate(o)
                        OfferRow(ref, o.title(locale), o.summary(locale), end, o.interest, live?.companyName(o, locale), if (end != null && end < now) OfferFlag.Ended else null, ref == featured, editable = true)
                    } ?: OfferRow(
                        ref, "", "", null, null, null,
                        // Unknown only once the live list has answered; before that it's just not resolved yet.
                        if (this.loaded && loadError == null) OfferFlag.Unavailable else null, ref == featured,
                    )
                }
            }
            val placed = refs.toSet()
            val available = live?.offers.orEmpty()
                .filter { OfferRef.company(it.id) !in placed }
                .filter { endDate(it)?.let { d -> d >= now } != false }
                .map { o -> OfferRow(OfferRef.company(o.id), o.title(locale), o.summary(locale), endDate(o), o.interest, live?.companyName(o, locale)) }
            old.copy(
                loading = !this.loaded,
                loadError = loadError,
                onSite = rows,
                available = available,
                featured = featured,
                hasApprovedCompany = live?.let { it.approved.isNotEmpty() },
            )
        }
    }

    private fun endDate(o: CompanyOffer): LocalDate? = o.endsAt?.let { runCatching { Dates.dateOf(Instant.parse(it)) }.getOrNull() }

    // ---- Changes: each saves the draft at once -------------------------------------------------

    /** Appends [ref] (a company offer) to the site's offers, unless the cap is reached. */
    fun add(ref: OfferRef) {
        if (_state.value.saving) return
        save { c ->
            val items = OfferDraft.items(c)
            if (ref in items || items.size >= cap) c else OfferDraft.withItems(c, items + ref)
        }
    }

    /** Takes [ref] off the site; when it was the featured offer, the featured offer goes too. */
    fun remove(ref: OfferRef) {
        if (_state.value.saving) return
        save { c ->
            val out = OfferDraft.withItems(c, OfferDraft.items(c) - ref)
            if (OfferDraft.featured(c) == ref) OfferDraft.withFeatured(out, null) else out
        }
    }

    /** Moves the offer at [index] by [delta] places (-1 up, +1 down). */
    fun move(index: Int, delta: Int) {
        if (_state.value.saving) return
        save { c ->
            val items = OfferDraft.items(c).toMutableList()
            val to = index + delta
            if (index !in items.indices || to !in items.indices) return@save c
            items.add(to, items.removeAt(index))
            OfferDraft.withItems(c, items)
        }
    }

    /** Features [ref] (null = none). In the hero picker this closes the screen once saved. */
    fun setFeatured(ref: OfferRef?) {
        if (_state.value.saving) return
        save(after = if (mode == PickerMode.Featured) PickerEvent.Done else null) { OfferDraft.withFeatured(it, ref) }
    }

    /** Edit: an own offer opens its form; a company offer first asks to make an own copy. */
    fun edit(ref: OfferRef) {
        if (ref.source == OfferRef.AGENT) _state.update { it.copy(event = PickerEvent.OpenEditor(copied = false)) }
        else _state.update { it.copy(confirmCopy = ref) }
    }

    fun cancelCopy() = _state.update { it.copy(confirmCopy = null) }

    /** The agent confirmed: converts the company offer into their own copy (see [OfferDraft.copyToCustom]) and opens its form. */
    fun confirmCopy() {
        val ref = _state.value.confirmCopy ?: return
        _state.update { it.copy(confirmCopy = null) }
        val offer = live?.offers?.firstOrNull { it.id == ref.id } ?: return
        val locale = site?.locale ?: "en"
        save(after = PickerEvent.OpenEditor(copied = true), failure = AppError.Validation("offers.custom", "max_items")) { c ->
            OfferDraft.copyToCustom(c, offer, locale, endDate(offer), today(), newId())
        }
    }

    fun consumeEvent() = _state.update { it.copy(event = null) }

    fun consumeError() = _state.update { it.copy(error = null) }

    /**
     * Applies [transform] to the latest draft and saves it. A null result means the change can't be made ([failure] is
     * shown, if given); an unchanged draft isn't saved.
     */
    private fun save(after: PickerEvent? = null, failure: AppError? = null, transform: (JsonObject) -> JsonObject?) {
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val latest = sites.observeMine().filterNotNull().first()
            val next = transform(latest.draftContent)
            if (next == null) {
                _state.update { it.copy(saving = false, error = failure) }
                return@launch
            }
            if (next == latest.draftContent) {
                _state.update { it.copy(saving = false, event = after ?: it.event) }
                return@launch
            }
            when (val r = sites.saveDraft(next)) {
                is Result.Ok -> _state.update { it.copy(saving = false, event = after ?: it.event) }
                is Result.Err -> _state.update { it.copy(saving = false, error = r.error) }
            }
        }
    }
}
