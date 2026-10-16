package com.sledje.agentpro.domain

import com.sledje.agentpro.core.catalog.DueBucket
import com.sledje.agentpro.core.catalog.PremiumMode
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * docs/TESTING.md: every premium mode, month ends (31 Jan + 1 month), leap years, policies started in
 * the past. The expectations mirror supabase/tests/50_premiums.sql (the SQL `mark_premium_paid` rule).
 */
class DueDateCalculatorTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate(y, m, day)

    @Test
    fun everyModeStepsByItsCatalogMonths() {
        val start = d(2026, 1, 15)
        assertEquals(12, PremiumMode.Yearly.months)
        assertEquals(6, PremiumMode.HalfYearly.months)
        assertEquals(3, PremiumMode.Quarterly.months)
        assertEquals(1, PremiumMode.Monthly.months)
        assertEquals(d(2027, 1, 15), DueDateCalculator.nextAfterPayment(start, start, PremiumMode.Yearly))
        assertEquals(d(2026, 7, 15), DueDateCalculator.nextAfterPayment(start, start, PremiumMode.HalfYearly))
        assertEquals(d(2026, 4, 15), DueDateCalculator.nextAfterPayment(start, start, PremiumMode.Quarterly))
        assertEquals(d(2026, 2, 15), DueDateCalculator.nextAfterPayment(start, start, PremiumMode.Monthly))
    }

    @Test
    fun monthEndClampsAndNeverDrifts() {
        val start = d(2025, 1, 31)
        val feb = DueDateCalculator.nextAfterPayment(start, start, 1)
        assertEquals(d(2025, 2, 28), feb)
        // Anchored to the start date: 28 Feb → 31 Mar, not 28 Mar.
        val mar = DueDateCalculator.nextAfterPayment(start, feb, 1)
        assertEquals(d(2025, 3, 31), mar)
        assertEquals(d(2025, 4, 30), DueDateCalculator.nextAfterPayment(start, mar, 1))
        // Quarterly from 31 Aug: 30 Nov, then 28 Feb, then 31 May.
        val aug = d(2025, 8, 31)
        val nov = DueDateCalculator.nextAfterPayment(aug, aug, 3)
        assertEquals(d(2025, 11, 30), nov)
        val feb26 = DueDateCalculator.nextAfterPayment(aug, nov, 3)
        assertEquals(d(2026, 2, 28), feb26)
        assertEquals(d(2026, 5, 31), DueDateCalculator.nextAfterPayment(aug, feb26, 3))
    }

    @Test
    fun leapYears() {
        assertEquals(d(2024, 2, 29), DueDateCalculator.addMonths(d(2024, 1, 31), 1))
        val leapDay = d(2024, 2, 29)
        assertEquals(d(2025, 2, 28), DueDateCalculator.nextAfterPayment(leapDay, leapDay, PremiumMode.Yearly))
        // Back on the 29th in the next leap year, because each date is computed from the start.
        assertEquals(d(2028, 2, 29), DueDateCalculator.nextAfterPayment(leapDay, d(2027, 2, 28), PremiumMode.Yearly))
    }

    @Test
    fun offScheduleDueDatesStepFromThemselves() {
        val start = d(2026, 1, 15)
        // A hand-set due date that isn't start + whole months.
        assertEquals(d(2026, 5, 20), DueDateCalculator.nextAfterPayment(start, d(2026, 2, 20), 3))
        // No start date at all.
        assertEquals(d(2024, 2, 29), DueDateCalculator.nextAfterPayment(null, d(2024, 1, 31), 1))
        // A due date before the start date is never anchored.
        assertEquals(d(2026, 2, 10), DueDateCalculator.nextAfterPayment(start, d(2026, 1, 10), 1))
        assertFailsWith<IllegalArgumentException> { DueDateCalculator.nextAfterPayment(start, start, 0) }
    }

    @Test
    fun initialNextDueOfPoliciesStartedInThePast() {
        val today = d(2026, 10, 16)
        // Quarterly from 15 Oct 2026 (yesterday): next due 15 Jan 2027 (the docs/UX.md example).
        assertEquals(d(2027, 1, 15), DueDateCalculator.initialNextDue(d(2026, 10, 15), PremiumMode.Quarterly, today))
        // Yearly policy from 2019: the upcoming anniversary.
        assertEquals(d(2027, 3, 1), DueDateCalculator.initialNextDue(d(2019, 3, 1), PremiumMode.Yearly, today))
        assertEquals(d(2026, 12, 5), DueDateCalculator.initialNextDue(d(2018, 12, 5), PremiumMode.Yearly, today))
        // Due today counts as upcoming.
        assertEquals(today, DueDateCalculator.initialNextDue(d(2026, 4, 16), PremiumMode.HalfYearly, today))
        // Month-end start, monthly: 31 Oct (this month still to come).
        assertEquals(d(2026, 10, 31), DueDateCalculator.initialNextDue(d(2026, 1, 31), PremiumMode.Monthly, today))
        // Clamped month end in the past is skipped: 31 Jan start, today 1 Mar → 31 Mar.
        assertEquals(d(2025, 3, 31), DueDateCalculator.initialNextDue(d(2025, 1, 31), 1, d(2025, 3, 1)))
    }

    @Test
    fun initialNextDueOfPoliciesStartingLaterIsOnePeriodAfterTheStart() {
        val today = d(2026, 10, 16)
        assertEquals(d(2027, 11, 1), DueDateCalculator.initialNextDue(d(2026, 11, 1), PremiumMode.Yearly, today))
        assertEquals(d(2026, 11, 16), DueDateCalculator.initialNextDue(today, PremiumMode.Monthly, today))
    }

    @Test
    fun initialNextDueThenPaymentStaysOnSchedule() {
        val start = d(2024, 1, 31)
        val first = DueDateCalculator.initialNextDue(start, 1, d(2024, 2, 1))
        assertEquals(d(2024, 2, 29), first)
        assertEquals(d(2024, 3, 31), DueDateCalculator.nextAfterPayment(start, first, 1))
    }

    @Test
    fun bucketsComeFromTheCatalog() {
        val today = d(2026, 10, 16)
        assertEquals(DueBucket.Overdue, DueBuckets.bucketOf(d(2026, 10, 15), today))
        assertEquals(DueBucket.Overdue, DueBuckets.bucketOf(d(2020, 1, 1), today))
        assertEquals(DueBucket.ThisWeek, DueBuckets.bucketOf(today, today))
        assertEquals(DueBucket.ThisWeek, DueBuckets.bucketOf(d(2026, 10, 23), today))
        assertEquals(DueBucket.ThisMonth, DueBuckets.bucketOf(d(2026, 10, 24), today))
        assertEquals(DueBucket.ThisMonth, DueBuckets.bucketOf(d(2026, 11, 15), today))
        assertEquals(DueBucket.Next3Months, DueBuckets.bucketOf(d(2026, 11, 16), today))
        assertEquals(DueBucket.Next3Months, DueBuckets.bucketOf(d(2027, 1, 14), today))
        assertNull(DueBuckets.bucketOf(d(2027, 1, 15), today), "beyond the 90-day horizon")
        assertEquals(90, DueBuckets.horizonDays())
    }
}
