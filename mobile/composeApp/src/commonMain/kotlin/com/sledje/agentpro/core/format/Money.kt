package com.sledje.agentpro.core.format

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Indian rupee formatting (docs/UX.md, Formatting; docs/DESIGN_SYSTEM.md, Charts).
 * - [format]: ₹1,25,000 (Indian grouping: the last 3 digits, then groups of 2). Paise are shown only
 *   when they aren't zero (₹1,250.50).
 * - [compact]: ₹1.2L / ₹3.4Cr for tiles and charts. Amounts are truncated to one decimal, never
 *   rounded up, so a compact value never overstates money (₹1,25,000 → ₹1.2L). Below 1 lakh the full
 *   grouped amount is shown.
 */
object Money {
    const val SYMBOL: String = "₹"
    private const val LAKH = 100_000.0
    private const val CRORE = 10_000_000.0

    /** "₹1,25,000"; "₹1,250.50" when there are paise; "-₹500" for negatives. */
    fun format(amount: Double, withSymbol: Boolean = true): String {
        val sign = if (amount < 0) "-" else ""
        val totalPaise = (abs(amount) * 100).roundToLong()
        val rupees = totalPaise / 100
        val paise = totalPaise % 100
        val grouped = groupIndian(rupees.toString())
        val fraction = if (paise == 0L) "" else "." + paise.toString().padStart(2, '0')
        return sign + (if (withSymbol) SYMBOL else "") + grouped + fraction
    }

    fun format(amount: Long, withSymbol: Boolean = true): String = format(amount.toDouble(), withSymbol)

    /** "₹1.2L", "₹3.4Cr", "₹85,000"; whole values drop the decimal ("₹1L"). */
    fun compact(amount: Double, withSymbol: Boolean = true): String {
        val sign = if (amount < 0) "-" else ""
        val value = abs(amount)
        val symbol = if (withSymbol) SYMBOL else ""
        val (scaled, suffix) = when {
            value >= CRORE -> value / CRORE to "Cr"
            value >= LAKH -> value / LAKH to "L"
            else -> return sign + symbol + groupIndian(floor(value).toLong().toString())
        }
        // Truncate to one decimal; the small epsilon absorbs binary floating-point error (1.3 → 1.2999…).
        val tenths = floor(scaled * 10 + 1e-9).toLong()
        val whole = tenths / 10
        val decimal = tenths % 10
        val number = if (decimal == 0L) groupIndian(whole.toString()) else "${groupIndian(whole.toString())}.$decimal"
        return sign + symbol + number + suffix
    }

    fun compact(amount: Long, withSymbol: Boolean = true): String = compact(amount.toDouble(), withSymbol)

    /** Indian digit grouping of a plain digit string: "1234567" → "12,34,567". */
    fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3)
        val groups = rest.reversed().chunked(2).map { it.reversed() }.reversed()
        return groups.joinToString(",") + "," + last3
    }

    /**
     * Parses user input such as "1,25,000.50" or "₹ 499" to a number, or null when it isn't one.
     * At most 2 decimals are accepted.
     */
    fun parse(input: String): Double? {
        val cleaned = input.replace(SYMBOL, "").replace(",", "").trim()
        if (!Regex("""\d+(\.\d{1,2})?""").matches(cleaned)) return null
        return cleaned.toDoubleOrNull()
    }
}
