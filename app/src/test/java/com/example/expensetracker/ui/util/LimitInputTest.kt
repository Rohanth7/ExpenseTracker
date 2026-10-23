package com.example.expensetracker.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LimitInputTest {
    private fun parse(s: String, decimalComma: Boolean = false) = parseLimitInput(s, decimalComma)

    @Test fun `blank or zero means no limit`() {
        assertEquals(0.0, parse("")!!, 0.0); assertEquals(0.0, parse("  ")!!, 0.0); assertEquals(0.0, parse("0")!!, 0.0)
    }
    @Test fun `rupee sign, grouping and decimals are read like the amount field`() {
        assertEquals(10000.0, parse("10,000")!!, 0.0); assertEquals(5000.0, parse("₹5000")!!, 0.0)
        assertEquals(120000.0, parse("1,20,000")!!, 0.0); assertEquals(2500.5, parse("2500.50")!!, 0.0)
    }
    @Test fun `on a comma-decimal phone a comma is the decimal point`() {
        // "1,5" was ₹15 as a limit but ₹1.50 as an expense.
        assertEquals(1.5, parse("1,5", decimalComma = true)!!, 0.0); assertEquals(1500.0, parse("1.500", decimalComma = true)!!, 0.0)
    }
    @Test fun `anything else is refused`() {
        listOf("abc", "-5", "1,2,3", "12a").forEach { assertNull("'$it'", parse(it)) }
    }
    @Test fun `a limit pre-filled for editing reads back as the same limit on any phone`() {
        // "1.125" read as ₹1,125 on a comma-decimal phone (dots group thousands there).
        listOf(1.125, 1500.0, 2500.5, 99.99, 0.5).forEach { limit ->
            listOf(false, true).forEach { dc ->
                assertEquals("$limit, decimalComma=$dc", Math.round(limit * 100) / 100.0, parse(formatLimitInput(limit), dc)!!, 0.0)
            }
        }
    }
    @Test fun `a limit that could be read two ways shows what it was read as`() {
        // "50,00" is ₹50 now (a decimal comma), not ₹5000 as before; the field must say so.
        val us = java.util.Locale.US
        fun preview(text: String, dc: Boolean = false, locale: java.util.Locale = us) =
            AmountCalculator.previewText(text, parseLimitInput(text, dc), dc, locale)
        assertEquals("= ₹50", preview("50,00"))
        assertEquals("= ₹1.500", preview("1.500", dc = true, locale = java.util.Locale.GERMANY))
        // Plain numbers, no limit, and unreadable text show nothing extra.
        listOf("5000", "", "0", "0,00", "abc").forEach { assertNull("'$it'", preview(it)) }
    }
}
