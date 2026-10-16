package com.sledje.agentpro.domain

import com.sledje.agentpro.core.catalog.DueBucket
import com.sledje.agentpro.core.catalog.PremiumMode
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import kotlinx.datetime.plus

/**
 * Premium due dates (docs/BACKEND.md, Due date rule; DECISIONS #31). Pure, unit-tested in commonTest.
 *
 * Dates step forward by whole **calendar months** from the policy's `start_date`; a day that doesn't
 * exist in the target month becomes that month's last day (31 Jan → 28/29 Feb → 31 Mar, never drifting
 * to the 28th). [nextAfterPayment] is exactly the rule `mark_premium_paid` applies in SQL
 * (supabase/migrations/20261015100300_rpcs.sql), so the "Next due becomes …" line the app shows before
 * recording equals what the server stores.
 */
object DueDateCalculator {
    /** [date] + [months] calendar months, clamped to the month end (Postgres `date + interval 'n months'`). */
    fun addMonths(date: LocalDate, months: Int): LocalDate = date.plus(months, DateTimeUnit.MONTH)

    /** Whole calendar months from [from] to [to], ignoring the day (the SQL `v_elapsed`). */
    fun calendarMonthsBetween(from: LocalDate, to: LocalDate): Int =
        (to.year - from.year) * 12 + (to.month.number - from.month.number)

    /** The [k]-th date of the schedule that starts at [start] (k = 0 is [start] itself). */
    fun scheduleDate(start: LocalDate, k: Int, months: Int): LocalDate = addMonths(start, k * months)

    /**
     * The due date after a payment of the period due on [currentDue]:
     * 1. if [start] is set and [currentDue] lies on its schedule (`start + elapsed months == currentDue`),
     *    `start + (elapsed + months)`;
     * 2. otherwise `currentDue + months` (a hand-set date that is off the schedule).
     */
    fun nextAfterPayment(start: LocalDate?, currentDue: LocalDate, months: Int): LocalDate {
        require(months > 0) { "months must be positive" }
        if (start != null && currentDue >= start) {
            val elapsed = calendarMonthsBetween(start, currentDue)
            if (addMonths(start, elapsed) == currentDue) return addMonths(start, elapsed + months)
        }
        return addMonths(currentDue, months)
    }

    fun nextAfterPayment(start: LocalDate?, currentDue: LocalDate, mode: PremiumMode): LocalDate =
        nextAfterPayment(start, currentDue, mode.months)

    /**
     * The first next-due date of a new policy (docs/UX.md, Policy form: "calculated automatically").
     * The premium at the start date is treated as paid when the policy began, so the next due is the
     * first schedule date after the start that is on or after [today]: a policy started in the past
     * gets its upcoming due date, and one starting later gets `start + months`.
     */
    fun initialNextDue(start: LocalDate, months: Int, today: LocalDate): LocalDate {
        require(months > 0) { "months must be positive" }
        var k = 1
        if (today > start) {
            // Jump close to today, then step; month-end clamping can make an estimate one period short.
            k = maxOf(1, calendarMonthsBetween(start, today) / months)
        }
        while (scheduleDate(start, k, months) < today) k++
        return scheduleDate(start, k, months)
    }

    fun initialNextDue(start: LocalDate, mode: PremiumMode, today: LocalDate): LocalDate =
        initialNextDue(start, mode.months, today)
}

/**
 * Due-bucket classification from catalog `dueBuckets` (docs/SHARED_DEFINITIONS.md): the bucket whose
 * `minDays..maxDays` (relative to today; `minDays` null = −∞) contains the due date, or null when the
 * date is later than every bucket (beyond the Dues horizon).
 */
object DueBuckets {
    fun daysUntil(due: LocalDate, today: LocalDate): Int = today.daysUntil(due)

    fun bucketOf(due: LocalDate, today: LocalDate, buckets: List<DueBucket> = DueBucket.active): DueBucket? {
        val days = daysUntil(due, today)
        return buckets.firstOrNull { (it.minDays == null || days >= it.minDays!!) && days <= it.maxDays }
    }

    /** The last day the Dues segment covers (the largest `maxDays`). */
    fun horizonDays(buckets: List<DueBucket> = DueBucket.active): Int = buckets.maxOf { it.maxDays }
}
