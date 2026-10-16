package com.sledje.agentpro.core.format

import androidx.compose.runtime.Composable
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.time_days_ago
import com.sledje.agentpro.resources.time_hours_ago
import com.sledje.agentpro.resources.time_just_now
import com.sledje.agentpro.resources.time_minutes_ago
import com.sledje.agentpro.resources.time_yesterday
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Relative times for lists (docs/UX.md, Formatting: "relative in lists, absolute in detail screens"),
 * in Asia/Kolkata: "Just now", "5m ago", "3h ago", "Yesterday", "3d ago", then "12 Oct" (this year)
 * or "12 Oct 2025".
 */
object RelativeTime {
    sealed interface Label {
        data object JustNow : Label
        data class Minutes(val n: Int) : Label
        data class Hours(val n: Int) : Label
        data object Yesterday : Label
        data class Days(val n: Int) : Label
        data class DayMonth(val date: LocalDate) : Label
        data class FullDate(val date: LocalDate) : Label
    }

    private const val WEEK_DAYS = 7

    fun of(instant: Instant, now: Instant): Label {
        val minutes = (now - instant).inWholeMinutes
        val today = Dates.dateOf(now)
        val day = Dates.dateOf(instant)
        val days = Dates.daysBetween(day, today)
        return when {
            minutes < 1 -> Label.JustNow
            minutes < 60 -> Label.Minutes(minutes.toInt())
            days == 0 -> Label.Hours((minutes / 60).toInt())
            days == 1 -> Label.Yesterday
            days < WEEK_DAYS -> Label.Days(days)
            day.year == today.year -> Label.DayMonth(day)
            else -> Label.FullDate(day)
        }
    }
}

@Composable
fun relativeTime(instant: Instant, now: Instant = Clock.System.now()): String = when (val l = RelativeTime.of(instant, now)) {
    RelativeTime.Label.JustNow -> stringResource(Res.string.time_just_now)
    is RelativeTime.Label.Minutes -> stringResource(Res.string.time_minutes_ago, l.n)
    is RelativeTime.Label.Hours -> stringResource(Res.string.time_hours_ago, l.n)
    RelativeTime.Label.Yesterday -> stringResource(Res.string.time_yesterday)
    is RelativeTime.Label.Days -> stringResource(Res.string.time_days_ago, l.n)
    is RelativeTime.Label.DayMonth -> Dates.formatDayMonth(l.date, rememberMonthNames())
    is RelativeTime.Label.FullDate -> Dates.format(l.date, rememberMonthNames())
}

/** Local date and time of day (Asia/Kolkata) ↔ instants, for follow-up dates. Times use 24-hour "10:00". */
object DateTimes {
    fun instantOf(date: LocalDate, time: LocalTime): Instant = LocalDateTime(date, time).toInstant(Dates.zone)

    fun localOf(instant: Instant): LocalDateTime = instant.toLocalDateTime(Dates.zone)

    fun formatTime(time: LocalTime): String = "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}
