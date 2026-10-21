package com.example.expensetracker.ui.screens.expenses

import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import org.junit.Assert.assertEquals
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
    @Test fun `last used category is the default when it still exists`() {
        val cats = listOf(Category(id = 1, name = "Food"), Category(id = 2, name = "Travel"))
        assertEquals(2L, defaultCategoryId(cats, lastUsedId = 2))
    }
    @Test fun `otherwise the default is pending, not the first category`() {
        val cats = listOf(Category(id = 1, name = "Food"))
        assertEquals(-1L, defaultCategoryId(cats, lastUsedId = 99)); assertEquals(-1L, defaultCategoryId(emptyList(), -1))
    }
}
