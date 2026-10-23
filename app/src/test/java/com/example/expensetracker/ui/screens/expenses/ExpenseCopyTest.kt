package com.example.expensetracker.ui.screens.expenses

import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class ExpenseCopyTest {
    private val original = Expense(id = 42, amount = 180.0, description = "SWIGGY", categoryId = 3, date = 1_000L,
        source = "SMS", rawSms = "Rs 180 debited…", tags = "lunch", paymentMethod = "Credit Card")

    @Test fun `a duplicate is a new manual expense dated now`() {
        val copy = original.duplicateForNow(now = 9_999L)
        assertEquals(0L, copy.id); assertEquals(9_999L, copy.date); assertEquals("Manual", copy.source); assertNull(copy.rawSms)
    }
    @Test fun `a duplicate keeps what the user typed`() {
        val copy = original.duplicateForNow(9_999L)
        assertEquals(180.0, copy.amount, 0.0); assertEquals("SWIGGY", copy.description); assertEquals(3L, copy.categoryId)
        assertEquals("lunch", copy.tags); assertEquals("Credit Card", copy.paymentMethod)
    }
    @Test fun `Duplicate copies the edit form as it is, not the saved expense`() {
        // The user changed the amount and category in Edit, then tapped Duplicate.
        val copy = original.duplicateWithEdits(amount = 200.0, description = "SWIGGY dinner", categoryId = 9,
            tags = "team", paymentMethod = "Cash", now = 9_999L)
        assertEquals(200.0, copy.amount, 0.0); assertEquals("SWIGGY dinner", copy.description); assertEquals(9L, copy.categoryId)
        assertEquals("team", copy.tags); assertEquals("Cash", copy.paymentMethod)
        assertEquals(0L, copy.id); assertEquals(9_999L, copy.date); assertEquals("Manual", copy.source); assertNull(copy.rawSms)
    }
    @Test fun `the edit form knows when it has unsaved changes`() {
        // The form starts with the amount as forEditing writes it ("180.00").
        assertFalse(original.isEditedTo("180.00", "SWIGGY", 3, "lunch", "Credit Card", 1_000L))
        assertFalse(original.isEditedTo("180", "SWIGGY", 3, "lunch", "Credit Card", 1_000L))   // retyped, same amount
        assertTrue(original.isEditedTo("180.00", "SWIGGY", 1, "lunch", "Credit Card", 1_000L))   // category
        assertTrue(original.isEditedTo("200", "SWIGGY", 3, "lunch", "Credit Card", 1_000L))   // amount
        assertTrue(original.isEditedTo("180.00", "SWIGGY", 3, "lunch", "Credit Card", 9_000L))   // date
        assertTrue(original.isEditedTo("180.00", "SWIGGY dinner", 3, "lunch", "Credit Card", 1_000L))   // notes
    }
    @Test fun `an untouched expense is not edited, even with stray spaces or paise beyond two places`() {
        // Captured text can have padding, and old data three decimals; the form shows them as they are
        // (amount rounded for display), so opening and duplicating must not rewrite the expense.
        val captured = original.copy(amount = 123.456, description = " SWIGGY ", tags = "lunch, ")
        assertFalse(captured.isEditedTo("123.46", " SWIGGY ", 3, "lunch, ", "Credit Card", 1_000L))
    }
    @Test fun `"Choose later" is remembered only when the user could see the categories`() {
        assertTrue(shouldRememberCategory(categoryId = 4, categoriesLoaded = true))
        assertTrue(shouldRememberCategory(categoryId = -1, categoriesLoaded = true))
        // Confirmed before the list loaded: the dialog showed pending only because it had nothing else.
        assertFalse(shouldRememberCategory(categoryId = -1, categoriesLoaded = false))
    }
    @Test fun `last used category is the default when it still exists`() {
        val cats = listOf(Category(id = 1, name = "Food"), Category(id = 2, name = "Travel"))
        assertEquals(2L, defaultCategoryId(cats, lastUsedId = 2))
    }
    @Test fun `categories that load after the dialog opened still give the last-used default`() {
        // The widget's "+" opens the dialog before categories have loaded.
        assertEquals(-1L, startingCategoryId(userChoice = null, categories = emptyList(), lastUsedId = 1))
        assertEquals(1L, startingCategoryId(userChoice = null, categories = listOf(Category(id = 1, name = "Food")), lastUsedId = 1))
    }
    @Test fun `the user's own choice wins, including choose later`() {
        val cats = listOf(Category(id = 1, name = "Food"), Category(id = 2, name = "Travel"))
        assertEquals(2L, startingCategoryId(userChoice = 2, categories = cats, lastUsedId = 1))
        assertEquals(-1L, startingCategoryId(userChoice = -1, categories = cats, lastUsedId = 1))
    }
    @Test fun `otherwise the default is pending, not the first category`() {
        val cats = listOf(Category(id = 1, name = "Food"))
        assertEquals(-1L, defaultCategoryId(cats, lastUsedId = 99)); assertEquals(-1L, defaultCategoryId(emptyList(), -1))
    }
}
