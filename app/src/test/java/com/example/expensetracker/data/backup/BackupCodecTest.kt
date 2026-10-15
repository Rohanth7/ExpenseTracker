package com.example.expensetracker.data.backup

import com.example.expensetracker.data.db.entity.Bill
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.db.entity.Loan
import com.example.expensetracker.data.db.entity.MerchantMapping
import com.example.expensetracker.data.db.entity.SavingsGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private fun ok(json: String) = (BackupCodec.parse(json) as? ParseResult.Ok)?.data
        ?: throw AssertionError("Expected a valid backup, got ${BackupCodec.parse(json)}")

    private fun invalid(json: String): String {
        val r = BackupCodec.parse(json)
        assertTrue("Expected invalid, got $r", r is ParseResult.Invalid)
        return (r as ParseResult.Invalid).reason
    }

    @Test fun `full backup round-trips`() {
        val data = BackupData(
            exportedAt = 1_760_000_000_000,
            categories = listOf(Category(id = 1, name = "Food", emoji = "🍔", monthlyLimit = 2499.99)),
            expenses = listOf(Expense(id = 7, amount = 250.0, description = "Swiggy", categoryId = 1, date = 1_760_000_000_000)),
            recurringTemplates = emptyList(),
            bills = listOf(Bill(id = 3, name = "Rent", amount = 22000.0, dueDayOfMonth = 5, autoLog = true)),
            loans = listOf(Loan(id = 2, name = "Car", totalAmount = 500000.0, monthlyEmi = 12000.0, tenureMonths = 48, startDate = 1L)),
            savingsGoals = listOf(SavingsGoal(id = 4, name = "Trip", targetAmount = 50000.0, currentAmount = 1200.0)),
            merchantMappings = listOf(MerchantMapping("Swiggy", 1)),
            settings = BackupSettings(monthlyIncome = 75000.0, budgetAlertsEnabled = true, budgetAlertThreshold = 85, weekStartsOnMonday = true)
        )
        assertEquals(data, ok(BackupCodec.toJson(data)))
        assertTrue(data.missingSections.isEmpty())
    }

    @Test fun `version 1 backups still restore, and say what they don't contain`() {
        val v1 = """{"version":1,"exportedAt":1700000000000,
            "categories":[{"id":1,"name":"Food","emoji":"🍔","colorHex":"#E53935","monthlyLimit":0.0}],
            "expenses":[{"id":1,"amount":99.0,"description":"Tea","categoryId":1,"date":1700000000000,"source":"Manual","tags":"","paymentMethod":"UPI"}],
            "recurringTemplates":[]}"""
        val data = ok(v1)
        assertEquals(1, data.version)
        assertEquals(null, data.bills)
        assertEquals(listOf("bills", "loans", "savings goals", "learned categories"), data.missingSections)
    }

    @Test fun `backup from a newer app version is refused`() {
        assertTrue(invalid("""{"version":99,"categories":[],"expenses":[]}""").contains("newer version"))
    }

    @Test fun `files that aren't backups are refused`() {
        invalid("")
        invalid("not json at all")
        invalid("{}")
        invalid("""{"categories":[]}""")
        invalid("[1,2,3]")
        // Truncated mid-file, e.g. an interrupted download
        assertTrue(invalid("""{"version":2,"categories":[{"id":1,"name":"Fo""").contains("damaged"))
    }

    @Test fun `damaged entries are caught before restoring`() {
        assertTrue(invalid("""{"version":2,"categories":[],"expenses":[{"id":1,"amount":5.0}]}""").contains("expense 1"))
        assertTrue(invalid("""{"version":2,"categories":[{"id":1}],"expenses":[]}""").contains("category 1"))
        assertTrue(invalid("""{"version":2,"categories":[],"expenses":[],"bills":[{"id":1,"amount":10.0}]}""").contains("bill 1"))
        assertTrue(invalid("""{"version":2,"categories":[],"expenses":[null]}""").contains("expense 1"))
    }
}
