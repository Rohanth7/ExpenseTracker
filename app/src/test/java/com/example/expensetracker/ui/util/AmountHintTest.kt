package com.example.expensetracker.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Bill, loan, goal, deposit, income and filter amounts are read like an expense amount, and say
 * what they were read as. Before, "₹1,500" or "12,5" silently did nothing on Add.
 */
class AmountHintTest {
    private val india = Locale.forLanguageTag("en-IN")
    private fun hint(text: String, dc: Boolean = false, locale: Locale = india, blankOrZeroIsNone: Boolean = false): String? {
        val value = if (blankOrZeroIsNone) parseLimitInput(text, dc) else AmountCalculator.evaluate(text, dc)
        return amountHint(text, value, dc, locale)
    }

    @Test fun `text that isn't an amount says so`() {
        listOf("abc", "12a", "1,2,3", "-5", "0").forEach { assertEquals("'$it'", INVALID_AMOUNT, hint(it)) }
    }

    @Test fun `nothing typed yet, or a plain number, needs no hint`() {
        listOf("", "  ", "1500", "99.5").forEach { assertNull("'$it'", hint(it)) }
    }

    @Test fun `rupee sign, grouping and sums show what they were read as`() {
        assertEquals("= ₹1,500", hint("₹1,500"))
        assertEquals("= ₹10,000", hint("10,000"))  // Android also groups lakhs (1,20,000); the JVM doesn't
        assertEquals("= ₹165", hint("120+45"))
    }

    @Test fun `on a comma-decimal phone a comma is the decimal point`() {
        assertEquals("= ₹12,5", hint("12,5", dc = true, locale = Locale.GERMANY))
        assertEquals("= ₹1.500", hint("1.500", dc = true, locale = Locale.GERMANY))
    }

    @Test fun `where blank or 0 means none (income, filters), those are fine`() {
        listOf("", "0", "0,00").forEach { assertNull("'$it'", hint(it, blankOrZeroIsNone = true)) }
        assertEquals(INVALID_AMOUNT, hint("abc", blankOrZeroIsNone = true))
    }

    @Test fun `a saved amount pre-filled for editing reads back unchanged on any phone`() {
        // Edit Loan pre-filled toLong(): saving an untouched ₹12,345.67 EMI made it ₹12,345.
        listOf(12345.67, 1500.0, 0.5, 99.99, 100000.0).forEach { amount ->
            listOf(false, true).forEach { dc ->
                assertEquals("$amount, decimalComma=$dc", amount, AmountCalculator.evaluate(AmountCalculator.forEditing(amount), dc)!!, 0.0)
            }
        }
    }

    @Test fun `screens don't parse amounts with toDoubleOrNull`() {
        // It takes no ₹, grouping, sums or decimal comma; AmountCalculator is the one parser.
        val screens = File("src/main/java").walk().filter { it.extension == "kt" && "/ui/" in it.path && it.name != "AmountCalculator.kt" }.toList()
        check(screens.isNotEmpty()) { "run from the app module" }
        val found = screens.flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> if ("toDoubleOrNull" in l) "${f.name}:${i + 1}" else null } }
        assertEquals(emptyList<String>(), found)
    }
}
