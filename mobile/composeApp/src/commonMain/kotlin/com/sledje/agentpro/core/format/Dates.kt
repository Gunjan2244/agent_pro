package com.sledje.agentpro.core.format

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Dates for the app (docs/UX.md, Formatting): `15 Oct 2026`, in the Asia/Kolkata zone. Month names are
 * passed in so the UI can supply translated ones (`rememberMonthNames()` in core/format/DateText.kt);
 * [ENGLISH_MONTHS] is the default for non-UI callers.
 */
object Dates {
    val zone: TimeZone = TimeZone.of("Asia/Kolkata")

    val ENGLISH_MONTHS: List<String> =
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    private const val MILLIS_PER_DAY = 86_400_000L

    fun today(clock: Clock = Clock.System): LocalDate = clock.todayIn(zone)

    /** The calendar date of [instant] in Asia/Kolkata. */
    fun dateOf(instant: Instant): LocalDate = instant.toLocalDateTime(zone).date

    /** "15 Oct 2026". */
    fun format(date: LocalDate, months: List<String> = ENGLISH_MONTHS): String =
        "${date.day} ${months[date.month.number - 1]} ${date.year}"

    /** "15 Jan" (used by UrgencyChip). */
    fun formatDayMonth(date: LocalDate, months: List<String> = ENGLISH_MONTHS): String =
        "${date.day} ${months[date.month.number - 1]}"

    /** Whole days from [from] to [to]; negative when [to] is earlier. */
    fun daysBetween(from: LocalDate, to: LocalDate): Int = from.daysUntil(to)

    /** UTC midnight of [date] in epoch millis, the format of the Material date picker. */
    fun toPickerMillis(date: LocalDate): Long = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

    /** The date of a Material date picker selection (UTC midnight millis). */
    fun fromPickerMillis(millis: Long): LocalDate =
        Instant.fromEpochMilliseconds(millis.floorDiv(MILLIS_PER_DAY) * MILLIS_PER_DAY)
            .toLocalDateTime(TimeZone.UTC).date
}
