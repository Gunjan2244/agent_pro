package com.sledje.agentpro.core.format

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class DatesTest {
    @Test
    fun formats() {
        assertEquals("15 Oct 2026", Dates.format(LocalDate(2026, 10, 15)))
        assertEquals("1 Jan 2027", Dates.format(LocalDate(2027, 1, 1)))
        assertEquals("15 Jan", Dates.formatDayMonth(LocalDate(2027, 1, 15)))
    }

    @Test
    fun daysBetween() {
        val today = LocalDate(2026, 10, 15)
        assertEquals(0, Dates.daysBetween(today, today))
        assertEquals(5, Dates.daysBetween(today, LocalDate(2026, 10, 20)))
        assertEquals(-3, Dates.daysBetween(today, LocalDate(2026, 10, 12)))
        assertEquals(366, Dates.daysBetween(LocalDate(2028, 1, 1), LocalDate(2029, 1, 1)))
    }

    @Test
    fun kolkataDate() {
        // 2026-10-15T19:00Z is already 16 Oct in India (UTC+5:30).
        assertEquals(LocalDate(2026, 10, 16), Dates.dateOf(Instant.parse("2026-10-15T19:00:00Z")))
        assertEquals(LocalDate(2026, 10, 15), Dates.dateOf(Instant.parse("2026-10-15T18:00:00Z")))
    }

    @Test
    fun pickerMillisRoundTrip() {
        val date = LocalDate(2026, 2, 28)
        assertEquals(date, Dates.fromPickerMillis(Dates.toPickerMillis(date)))
        assertEquals(LocalDate(1970, 1, 1), Dates.fromPickerMillis(0L))
    }
}
