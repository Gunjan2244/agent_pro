package com.sledje.agentpro.feature.website.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sledje.agentpro.feature.website.data.SiteStats
import com.sledje.agentpro.feature.website.data.SiteStatsRepository
import com.sledje.agentpro.feature.website.data.StatsPeriod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Stats (docs/UX.md, Stats screen): Views, Enquiries and Visit → enquiry % for the chosen period, plus
 * one views-per-day chart. Computed locally from the synced `site_daily_views` cache, so it works offline.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(stats: SiteStatsRepository) : ViewModel() {
    private val _period = MutableStateFlow(StatsPeriod.Week)
    val period: StateFlow<StatsPeriod> = _period.asStateFlow()

    /** Null until the cache has been read. */
    val stats: StateFlow<SiteStats?> = _period.flatMapLatest { stats.observe(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun selectPeriod(period: StatsPeriod) {
        _period.value = period
    }
}
