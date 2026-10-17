package com.sledje.agentpro.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** Age rules (docs/PRIVACY.md, "Customers under 18"). Pure, unit-tested in commonTest. */
object Age {
    const val ADULT_YEARS = 18

    /** True when a person born on [dob] has not yet turned 18 on [today] (they turn 18 on the 18th birthday). */
    fun isUnder18(dob: LocalDate, today: LocalDate): Boolean = dob.plus(DatePeriod(years = ADULT_YEARS)) > today
}
