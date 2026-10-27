package com.example.expensetracker.ui.screens.expenses

import com.example.expensetracker.data.db.entity.Income
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomeCopyTest {
    private val salary = Income(id = 7, amount = 50000.0, description = "Salary", date = 1_000L, source = "Recurring", tags = "job", recurringIncomeId = 3)

    @Test fun `Duplicate copies the form as a new manual income dated now`() {
        val copy = salary.duplicateWithEdits(amount = 52000.0, description = "Salary + bonus", tags = "job,bonus", now = 9_999L)
        assertEquals(0L, copy.id); assertEquals(9_999L, copy.date); assertEquals("Manual", copy.source)
        assertEquals(52000.0, copy.amount, 0.0); assertEquals("Salary + bonus", copy.description); assertEquals("job,bonus", copy.tags)
        // Not logged by the recurring income, so it doesn't stop next month's from being logged
        assertNull(copy.recurringIncomeId)
    }

    @Test fun `the edit form knows when it has unsaved changes`() {
        assertFalse(salary.isEditedTo("50000.00", "Salary", "job", 1_000L))
        assertFalse(salary.isEditedTo("50000", "Salary", "job", 1_000L))
        assertTrue(salary.isEditedTo("51000", "Salary", "job", 1_000L))
        assertTrue(salary.isEditedTo("50000.00", "Pay", "job", 1_000L))
        assertTrue(salary.isEditedTo("50000.00", "Salary", "", 1_000L))
        assertTrue(salary.isEditedTo("50000.00", "Salary", "job", 2_000L))
    }
}
