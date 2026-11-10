package com.example.expensetracker.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormInputTest {
    /** BUG-26: the loan icon counted UTF-16 units, so a flag (4) or a family (8) didn't fit in 2. */
    @Test fun `one emoji is one character, however it's encoded`() {
        listOf("🚗", "🇮🇳", "👨‍👩‍👧", "👍🏽", "🏳️‍🌈", "A").forEach { assertEquals(it, it, oneCharacter("", it)) }
        assertEquals("", oneCharacter("🏦", ""))
    }

    @Test fun `typing another replaces it, on either side`() {
        assertEquals("🇮🇳", oneCharacter("🏦", "🏦🇮🇳"))
        assertEquals("🇮🇳", oneCharacter("🏦", "🇮🇳🏦"))
        assertEquals("👨‍👩‍👧", oneCharacter("🏦", "🏦👨‍👩‍👧"))
        assertEquals("C", oneCharacter("A", "ABC"))
    }

    @Test fun `a whole number must be in range`() {
        assertEquals(31, wholeNumberIn("31", 1..31))
        listOf("", "0", "32", "1.5", "-1").forEach { assertNull("'$it'", wholeNumberIn(it, 1..31)) }
        assertEquals(0, wholeNumberIn("0", 0..14))
    }
}
