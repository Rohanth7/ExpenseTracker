package com.example.expensetracker.notification

import com.example.expensetracker.data.db.entity.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickCategoriesTest {
    private fun cat(id: Long, name: String) = Category(id = id, name = name)

    private val bills = cat(1, "Bills & Utilities")
    private val entertainment = cat(2, "Entertainment")
    private val food = cat(3, "Food & Dining")
    private val groceries = cat(4, "Groceries")
    private val transport = cat(5, "Transport")
    private val all = listOf(bills, entertainment, food, groceries, transport)

    private fun names(list: List<Category>) = list.map { it.name }

    @Test fun `most used categories come first, not alphabetical ones`() {
        val usage = mapOf(transport.id to 9, food.id to 14, groceries.id to 2)
        assertEquals(listOf("Food & Dining", "Transport", "Groceries"), names(QuickCategories.pick(all, usage, suggestedId = null)))
    }

    @Test fun `the auto-detected category is always the first button`() {
        val usage = mapOf(transport.id to 9, food.id to 14)
        assertEquals(listOf("Groceries", "Food & Dining", "Transport"), names(QuickCategories.pick(all, usage, suggestedId = groceries.id)))
    }

    @Test fun `never more than three, because Android shows at most three buttons`() {
        val usage = all.associate { it.id to 5 }
        assertEquals(3, QuickCategories.pick(all, usage, suggestedId = food.id).size)
    }

    @Test fun `with no history it falls back to alphabetical`() {
        assertEquals(listOf("Bills & Utilities", "Entertainment", "Food & Dining"), names(QuickCategories.pick(all, emptyMap(), suggestedId = null)))
    }

    @Test fun `a suggestion or usage for a deleted category is ignored`() {
        val usage = mapOf(99L to 50, food.id to 3)
        assertEquals(listOf("Food & Dining", "Bills & Utilities", "Entertainment"), names(QuickCategories.pick(all, usage, suggestedId = 98L)))
    }

    @Test fun `no categories means no buttons`() {
        assertEquals(emptyList<Category>(), QuickCategories.pick(emptyList(), mapOf(1L to 3), suggestedId = 1L))
    }
}
