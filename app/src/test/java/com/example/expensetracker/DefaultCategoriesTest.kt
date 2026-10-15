package com.example.expensetracker

import com.example.expensetracker.data.defaults.DefaultCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCategoriesTest {
    @Test fun `starter categories are distinct top-level categories with colours`() {
        val starter = DefaultCategories.STARTER
        assertEquals(10, starter.size)
        assertEquals(starter.size, starter.map { it.name.lowercase() }.toSet().size)
        assertTrue(starter.all { it.parentId == null && it.id == 0L && it.monthlyLimit == 0.0 })
        assertTrue(starter.all { Regex("^#[0-9A-F]{6}$").matches(it.colorHex) })
    }
}
