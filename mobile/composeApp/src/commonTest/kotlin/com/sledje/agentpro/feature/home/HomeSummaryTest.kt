package com.sledje.agentpro.feature.home

import com.sledje.agentpro.core.catalog.EnquiryStatus
import com.sledje.agentpro.core.catalog.PolicyStatus
import com.sledje.agentpro.core.catalog.PremiumMode
import com.sledje.agentpro.feature.enquiries.data.Enquiry
import com.sledje.agentpro.feature.policies.data.Policy
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class HomeSummaryTest {
    private val today = LocalDate(2026, 11, 9)

    private fun enquiry(status: EnquiryStatus = EnquiryStatus.New, createdOn: String = "2026-11-08", followUp: String? = null) = Enquiry(
        id = createdOn + status.key + followUp, name = "A", phone = null, email = null, interest = null, source = null, message = null,
        status = status, followUpAt = followUp?.let { Instant.parse("${it}T04:00:00Z") }, notes = null, seenAt = null,
        customerId = null, createdAt = Instant.parse("${createdOn}T06:00:00Z"),
    )

    private fun policy(due: LocalDate?, amount: Double = 1000.0, status: PolicyStatus = PolicyStatus.Active) = Policy(
        id = "p$due", customerId = "c", policyNumber = null, planName = null, sumAssured = null, premiumAmount = amount,
        premiumMode = PremiumMode.entries.first(), startDate = null, nextDueDate = due, status = status, notes = null,
    )

    @Test fun nothingIsEmpty() {
        assertTrue(HomeSummary.today(emptyList(), emptyList(), today).isEmpty)
        assertTrue(HomeSummary.month(emptyList(), emptyList(), today).isEmpty)
    }

    @Test fun todayCountsNewFollowUpsAndDuesThisWeek() {
        val t = HomeSummary.today(
            listOf(enquiry(), enquiry(EnquiryStatus.FollowUp, followUp = "2026-11-09"), enquiry(EnquiryStatus.FollowUp, followUp = "2026-11-12")),
            listOf(policy(today.let { LocalDate(2026, 11, 12) }, 500.0), policy(LocalDate(2026, 11, 20)), policy(LocalDate(2026, 11, 10), status = PolicyStatus.Lapsed)),
            today,
        )
        assertEquals(1, t.newEnquiries)
        assertEquals(1, t.followUpsToday)
        assertEquals(1, t.duesThisWeek)
        assertEquals(500.0, t.duesThisWeekAmount)
    }

    @Test fun monthComparesWithThePreviousThirtyDaysAndBucketsWeeks() {
        val m = HomeSummary.month(
            listOf(enquiry(createdOn = "2026-11-08"), enquiry(createdOn = "2026-11-01"), enquiry(createdOn = "2026-10-01"), enquiry(createdOn = "2026-08-01")),
            listOf(policy(LocalDate(2026, 11, 30), 2000.0), policy(LocalDate(2026, 12, 20))),
            today,
        )
        assertEquals(2, m.enquiries)
        assertEquals(1, m.previousEnquiries)
        assertEquals(100, m.enquiryChangePercent)
        assertEquals(2000.0, m.premiumDue30d)
        assertEquals(HomeSummary.WEEKS, m.weekly.size)
        assertEquals(1, m.weekly.last().second)
        assertEquals(1, m.weekly[HomeSummary.WEEKS - 2].second)
    }

    @Test fun noChangeFigureWithoutAPreviousPeriod() {
        assertNull(HomeSummary.month(listOf(enquiry()), emptyList(), today).enquiryChangePercent)
    }
}
