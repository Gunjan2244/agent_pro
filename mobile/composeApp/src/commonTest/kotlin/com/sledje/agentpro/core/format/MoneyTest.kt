package com.sledje.agentpro.core.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyTest {
    @Test
    fun indianGrouping() {
        assertEquals("₹0", Money.format(0.0))
        assertEquals("₹999", Money.format(999L))
        assertEquals("₹1,000", Money.format(1_000L))
        assertEquals("₹99,999", Money.format(99_999L))
        assertEquals("₹1,00,000", Money.format(100_000L))
        assertEquals("₹1,25,000", Money.format(125_000L))
        assertEquals("₹12,34,567", Money.format(1_234_567L))
        assertEquals("₹1,00,00,000", Money.format(10_000_000L))
        assertEquals("₹12,34,56,789", Money.format(123_456_789L))
        assertEquals("12,34,567", Money.format(1_234_567L, withSymbol = false))
    }

    @Test
    fun paiseOnlyWhenNotZero() {
        assertEquals("₹1,250.50", Money.format(1250.5))
        assertEquals("₹1,250.05", Money.format(1250.05))
        assertEquals("₹1,250", Money.format(1250.0))
        assertEquals("-₹500", Money.format(-500.0))
    }

    @Test
    fun compactLakhAndCrore() {
        assertEquals("₹1.2L", Money.compact(125_000L))
        assertEquals("₹1L", Money.compact(100_000L))
        assertEquals("₹1.3L", Money.compact(130_000L))
        assertEquals("₹12.3L", Money.compact(1_234_567L))
        assertEquals("₹99.9L", Money.compact(9_999_999L))
        assertEquals("₹3.4Cr", Money.compact(34_000_000L))
        assertEquals("₹1Cr", Money.compact(10_000_000L))
        assertEquals("₹1,234Cr", Money.compact(12_340_000_000L))
        assertEquals("₹85,000", Money.compact(85_000L))
        assertEquals("-₹1.2L", Money.compact(-125_000.0))
        assertEquals("1.2L", Money.compact(125_000L, withSymbol = false))
    }

    @Test
    fun parse() {
        assertEquals(125000.5, Money.parse("1,25,000.50"))
        assertEquals(499.0, Money.parse("₹ 499"))
        assertNull(Money.parse("12.345"))
        assertNull(Money.parse("abc"))
    }
}
