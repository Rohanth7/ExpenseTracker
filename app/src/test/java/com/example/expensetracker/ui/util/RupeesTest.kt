package com.example.expensetracker.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** B-08: amounts were shown with the paise cut off (₹99.99 as ₹99, ₹0.50 as ₹0), differently per screen. */
class RupeesTest {
    @Test fun `paise are shown when the amount has them`() {
        assertEquals("99.99", fmtINR(99.99))
        assertEquals("0.50", fmtINR(0.5))
        assertEquals("1,00,000.75", fmtINR(100000.75))
        assertEquals("12,34,567.89", fmtINR(1234567.89))
    }

    @Test fun `whole rupees have no decimals`() {
        assertEquals("0", fmtINR(0.0))
        assertEquals("250", fmtINR(250.0))
        assertEquals("1,500", fmtINR(1500.0))
        assertEquals("1,20,000", fmtINR(120000.0))
        assertEquals("10,00,00,000", fmtINR(1e8))
    }

    @Test fun `amounts are rounded to the paisa, never cut`() {
        assertEquals("100", fmtINR(99.999))
        assertEquals("0.01", fmtINR(0.005))
        assertEquals("0", fmtINR(0.004))
        // A sum of doubles: 0.1 + 0.2 is 0.30000000000000004
        assertEquals("0.30", fmtINR(0.1 + 0.2))
        assertEquals("100.49", fmtINR(99.99 + 0.5))
    }

    @Test fun `negative amounts and amounts that are not numbers`() {
        assertEquals("-1,500.50", fmtINR(-1500.5))
        assertEquals("-0.50", fmtINR(-0.5))
        assertEquals("—", fmtINR(Double.NaN))
        assertEquals("—", fmtINR(Double.POSITIVE_INFINITY))
    }

    @Test fun `there is one amount formatter and nothing cuts an amount to whole rupees by hand`() {
        val sources = File("src/main/java").walk().filter { it.extension == "kt" }.toList()
        check(sources.isNotEmpty()) { "run from the app module" }
        fun lines(match: (String) -> Boolean) = sources.flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> if (match(l)) "${f.name}:${i + 1}" else null } }
        assertEquals(listOf("Rupees.kt"), lines { "fun fmtINR(" in it || "fun fmtVM(" in it }.map { it.substringBefore(':') })
        val rupee = "₹"
        assertEquals(emptyList<String>(), lines { rupee in it && (".toLong()" in it || ".toInt()" in it || "%.0f" in it || "%.2f" in it) })
    }
}
