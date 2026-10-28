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
    private val evaluate = { dc: Boolean -> { t: String -> AmountCalculator.evaluate(t, dc) } }
    private val orZero = { dc: Boolean -> { t: String -> parseAmountOrZero(t, dc) } }
    private val noneIfBlank = { dc: Boolean -> { t: String -> parseLimitInput(t, dc) } }

    private fun read(text: String, dc: Boolean = false, locale: Locale = india, parse: (Boolean) -> (String) -> Double? = evaluate) =
        readAmountText(text, parse(dc), dc, locale)
    private fun hint(text: String, dc: Boolean = false, locale: Locale = india, parse: (Boolean) -> (String) -> Double? = evaluate): String? =
        read(text, dc, locale, parse).let { if (it.invalid) INVALID_AMOUNT else it.preview }

    @Test fun `text that isn't an amount says so`() {
        listOf("abc", "12a", "1,2,3", "0", "-5").forEach { assertEquals("'$it'", INVALID_AMOUNT, hint(it)) }
    }

    @Test fun `nothing typed yet, a plain number, or text on its way to a number, needs no hint`() {
        // "1," and "120+" are typed on the way to "1,500" and "120+45": not red on every keystroke.
        listOf("", "  ", "1500", "99.5", "1,", "120+", "3×").forEach { assertNull("'$it'", hint(it)) }
        // ...but they aren't amounts yet, so Add stays disabled.
        listOf("", "1,", "120+").forEach { assertNull("'$it'", read(it).value) }
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

    @Test fun `where blank or 0 means none (filters, limits), those are fine`() {
        listOf("", "0", "0,00").forEach { assertEquals("'$it'", 0.0, read(it, parse = noneIfBlank).value!!, 0.0) }
        assertEquals(INVALID_AMOUNT, hint("abc", parse = noneIfBlank))
    }

    @Test fun `bills, loan totals and income accept 0 but not nothing`() {
        // Saved with 0 before (a reminder-only bill); they must stay editable. Blank income used
        // to do nothing on Save; it mustn't wipe the income now.
        assertEquals(0.0, read("0", parse = orZero).value!!, 0.0)
        assertEquals(1500.0, read("1,500", parse = orZero).value!!, 0.0)
        assertNull(read("", parse = orZero).value)
        assertNull(hint("", parse = orZero))
        assertEquals(INVALID_AMOUNT, hint("abc", parse = orZero))
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
        val screens = File("src/main/java").walk()
            .filter { it.extension == "kt" && "/ui/" in it.invariantSeparatorsPath && it.name != "AmountCalculator.kt" }.toList()
        check(screens.isNotEmpty()) { "run from the app module" }
        val found = screens.flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> if ("toDoubleOrNull" in l) "${f.name}:${i + 1}" else null } }
        assertEquals(emptyList<String>(), found)
    }
}
