package com.example.expensetracker.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** B-05: the category dialog saved nothing for a blank name, and took a name already in use. */
class CategoryNameTest {
    private val siblings = listOf("Food", "Rent")

    @Test fun `a new name is fine`() {
        assertNull(categoryNameError("Travel", siblings))
        assertNull(categoryNameError("  Travel ", siblings))
        assertNull(categoryNameError("Foods", siblings))
    }

    @Test fun `blank or only spaces is not a name`() {
        assertEquals("Enter a name", categoryNameError("", siblings))
        assertEquals("Enter a name", categoryNameError("   ", siblings))
    }

    @Test fun `a name already used under the same parent is refused, whatever its case`() {
        listOf("Food", "food", "FOOD", " food ").forEach {
            assertEquals("'$it'", "There's already one with this name", categoryNameError(it, siblings))
        }
    }

    @Test fun `the same name under another parent is allowed`() {
        // "Fuel" under Travel doesn't stop "Fuel" under Car: the caller passes that parent's names only.
        assertNull(categoryNameError("Fuel", listOf("Insurance")))
    }

    @Test fun `a name longer than the limit is refused`() {
        assertNull(categoryNameError("a".repeat(MAX_CATEGORY_NAME_LENGTH), siblings))
        assertEquals("Use 40 characters or fewer", categoryNameError("a".repeat(MAX_CATEGORY_NAME_LENGTH + 1), siblings))
    }
}
