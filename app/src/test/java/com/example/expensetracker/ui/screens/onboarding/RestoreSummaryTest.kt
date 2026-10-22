package com.example.expensetracker.ui.screens.onboarding

import com.example.expensetracker.data.backup.BackupData
import com.example.expensetracker.data.backup.BackupSettings
import com.example.expensetracker.data.db.entity.Bill
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreSummaryTest {
    private fun data(expenses: Int, categories: Int, bills: Int? = null, income: Double? = null) = BackupData(
        exportedAt = 1L,
        categories = List(categories) { Category(id = it + 1L, name = "C$it") },
        expenses = List(expenses) { Expense(amount = 10.0, description = "E$it") },
        recurringTemplates = emptyList(),
        bills = bills?.let { n -> List(n) { Bill(name = "B$it", amount = 1.0, dueDayOfMonth = 1) } },
        settings = income?.let { BackupSettings(monthlyIncome = it) }
    )

    @Test fun `lists what was restored`() =
        assertEquals("43 expenses, 7 categories, 4 bills", restoreSummary(data(43, 7, bills = 4)))

    @Test fun `uses singular words for one`() = assertEquals("1 expense, 1 category", restoreSummary(data(1, 1)))

    @Test fun `skips sections the backup doesn't have`() = assertEquals("0 expenses, 2 categories", restoreSummary(data(0, 2)))

    @Test fun `knows whether the backup had a monthly income`() {
        assertTrue(restoredIncome(data(1, 1, income = 75000.0)))
        assertFalse(restoredIncome(data(1, 1, income = 0.0)))
        assertFalse(restoredIncome(data(1, 1)))
    }

    @Test fun `restored info remembers income and whether there were categories`() {
        assertEquals(RestoredInfo("1 expense, 0 categories", hasIncome = true, hadCategories = false), restoredInfo(data(1, 0, income = 5000.0)))
        assertTrue(restoredInfo(data(1, 2)).hadCategories)
    }

    @Test fun `restored info survives being saved as text`() {
        val info = RestoredInfo("43 expenses, 7 categories", hasIncome = true, hadCategories = true)
        assertEquals(info, RestoredInfo.fromJson(info.toJson()))
    }

    @Test fun `missing or damaged saved info reads as no restore`() {
        assertEquals(null, RestoredInfo.fromJson(null))
        assertEquals(null, RestoredInfo.fromJson("not json"))
        assertEquals(null, RestoredInfo.fromJson("{}"))
    }
}
