package com.sledje.agentpro.feature.website.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sledje.agentpro.core.designsystem.ChartData
import com.sledje.agentpro.core.designsystem.ChartKind
import com.sledje.agentpro.core.designsystem.ChartSeries
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.designsystem.components.ChartCard
import com.sledje.agentpro.core.designsystem.components.KpiTile
import com.sledje.agentpro.core.designsystem.components.ListSkeleton
import com.sledje.agentpro.core.designsystem.components.SegmentOption
import com.sledje.agentpro.core.designsystem.components.SegmentedControl
import com.sledje.agentpro.core.format.Dates
import com.sledje.agentpro.core.format.rememberMonthNames
import com.sledje.agentpro.feature.website.data.SiteStats
import com.sledje.agentpro.feature.website.data.StatsPeriod
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.website_hub_stats
import com.sledje.agentpro.resources.website_stats_chart_title
import com.sledje.agentpro.resources.website_stats_conversion
import com.sledje.agentpro.resources.website_stats_enquiries
import com.sledje.agentpro.resources.website_stats_no_value
import com.sledje.agentpro.resources.website_stats_percent
import com.sledje.agentpro.resources.website_stats_period_month
import com.sledje.agentpro.resources.website_stats_period_week
import com.sledje.agentpro.resources.website_stats_takeaway
import com.sledje.agentpro.resources.website_stats_views
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToLong

/**
 * Stats (docs/UX.md): Views, Enquiries and Visit → enquiry % for the last 7 or 30 days, and one
 * views-per-day column chart (with its table view). Works offline from the cache.
 */
@Composable
fun StatsScreen(
    period: StatsPeriod,
    stats: SiteStats?,
    onBack: () -> Unit,
    onPeriod: (StatsPeriod) -> Unit,
) {
    AppScaffold(
        topBar = { AppTopBar(title = stringResource(Res.string.website_hub_stats), onBack = onBack) },
        offline = !isOnline(),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                SegmentedControl(
                    options = listOf(
                        SegmentOption(StatsPeriod.Week.name, stringResource(Res.string.website_stats_period_week)),
                        SegmentOption(StatsPeriod.Month.name, stringResource(Res.string.website_stats_period_month)),
                    ),
                    selectedKey = period.name,
                    onSelect = { key -> StatsPeriod.entries.firstOrNull { it.name == key }?.let(onPeriod) },
                )
                if (stats == null) {
                    ListSkeleton(rows = 3)
                    return@Column
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    KpiTile(stringResource(Res.string.website_stats_views), stats.views.toString(), Modifier.weight(1f))
                    KpiTile(stringResource(Res.string.website_stats_enquiries), stats.enquiries.toString(), Modifier.weight(1f))
                }
                KpiTile(
                    stringResource(Res.string.website_stats_conversion),
                    stats.conversionPercent?.let { stringResource(Res.string.website_stats_percent, formatPercent(it)) }
                        ?: stringResource(Res.string.website_stats_no_value),
                    Modifier.fillMaxWidth(),
                )
                val months = rememberMonthNames()
                val labels = stats.days.map { d ->
                    if (stats.period == StatsPeriod.Week) Dates.formatDayMonth(d.day, months) else d.day.day.toString()
                }
                ChartCard(
                    title = stringResource(Res.string.website_stats_chart_title),
                    takeaway = stringResource(Res.string.website_stats_takeaway, stats.views, stats.period.days),
                    data = ChartData(
                        kind = ChartKind.Column,
                        xLabels = labels,
                        series = listOf(ChartSeries(stringResource(Res.string.website_stats_views), stats.days.map { it.views.toDouble() })),
                    ),
                )
            }
        }
    }
}

/** One decimal place, without a trailing ".0" (12.5, 3). */
internal fun formatPercent(value: Double): String {
    val tenths = (value * 10).roundToLong()
    return if (tenths % 10 == 0L) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
}
