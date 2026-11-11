package com.sledje.agentpro.feature.home

import com.sledje.agentpro.core.catalog.EnquiryStatus
import com.sledje.agentpro.core.catalog.PolicyStatus
import com.sledje.agentpro.core.format.Dates
import com.sledje.agentpro.feature.enquiries.data.Enquiry
import com.sledje.agentpro.feature.policies.data.Policy
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus

/** The "Today" block of Home (docs/UX.md, Home): counts, each hidden by the screen when zero. */
data class TodaySummary(
    val newEnquiries: Int = 0,
    val followUpsToday: Int = 0,
    val duesThisWeek: Int = 0,
    val duesThisWeekAmount: Double = 0.0,
) {
    val isEmpty: Boolean get() = newEnquiries == 0 && followUpsToday == 0 && duesThisWeek == 0
}

/** The "This month" block: enquiries in the last 30 days against the 30 before, premium due in the next 30. */
data class MonthSummary(
    val enquiries: Int = 0,
    val previousEnquiries: Int = 0,
    val premiumDue30d: Double = 0.0,
    /** Enquiries per week, oldest first: the week's first day and its count. */
    val weekly: List<Pair<LocalDate, Int>> = emptyList(),
) {
    /** Percent change of [enquiries] against [previousEnquiries]; null when there is nothing to compare with. */
    val enquiryChangePercent: Int? get() =
        if (previousEnquiries == 0) null else ((enquiries - previousEnquiries) * 100.0 / previousEnquiries).toInt()

    val isEmpty: Boolean get() = enquiries == 0 && previousEnquiries == 0 && premiumDue30d == 0.0
}

/** Pure computation behind Home's Today and This month blocks; unit-tested in commonTest. */
object HomeSummary {
    const val WEEKS = 8

    fun today(enquiries: List<Enquiry>, policies: List<Policy>, today: LocalDate): TodaySummary {
        val dueThisWeek = policies.filter { it.status == PolicyStatus.Active && it.dueIn(today) in 0..7 }
        return TodaySummary(
            newEnquiries = enquiries.count { it.status == EnquiryStatus.New },
            followUpsToday = enquiries.count { e ->
                !e.status.terminal && e.followUpAt?.let { Dates.dateOf(it) <= today } == true
            },
            duesThisWeek = dueThisWeek.size,
            duesThisWeekAmount = dueThisWeek.sumOf { it.premiumAmount ?: 0.0 },
        )
    }

    fun month(enquiries: List<Enquiry>, policies: List<Policy>, today: LocalDate): MonthSummary {
        val ages = enquiries.map { today.daysUntil(Dates.dateOf(it.createdAt)) * -1 } // days ago
        val weekly = (WEEKS - 1 downTo 0).map { w ->
            val count = ages.count { it in (w * 7)..(w * 7 + 6) }
            today.minus(w * 7 + 6, DateTimeUnit.DAY) to count
        }
        return MonthSummary(
            enquiries = ages.count { it in 0..29 },
            previousEnquiries = ages.count { it in 30..59 },
            premiumDue30d = policies.filter { it.status == PolicyStatus.Active && it.dueIn(today) in 0..30 }
                .sumOf { it.premiumAmount ?: 0.0 },
            weekly = weekly,
        )
    }

    private fun Policy.dueIn(today: LocalDate): Int = nextDueDate?.let { today.daysUntil(it) } ?: Int.MIN_VALUE
}
