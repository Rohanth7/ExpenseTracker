package com.example.expensetracker.data.backup

import com.example.expensetracker.data.db.entity.Bill
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.db.entity.Income
import com.example.expensetracker.data.db.entity.Loan
import com.example.expensetracker.data.db.entity.MerchantMapping
import com.example.expensetracker.data.db.entity.RecurringIncome
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
            settings = BackupSettings(monthlyIncome = 75000.0, budgetAlertsEnabled = true, budgetAlertThreshold = 85, weekStartsOnMonday = true, monthStartDay = 25),
            parsingRules = listOf(com.example.expensetracker.sms.ParsingRule("r1", com.example.expensetracker.sms.ParsingRule.Kind.PAYMENT,
                "^Paid\\s+Rs\\s+(\\d[\\d,]*)\\s+to\\s+(.+)$", 1, 2, "Paid Rs <amount> to <payee>", 1L)),
            incomes = listOf(Income(id = 8, amount = 50000.0, description = "Salary", date = 1_760_000_000_000, source = "Recurring", recurringIncomeId = 9),
                Income(id = 10, amount = 2500.5, description = "Freelance", tags = "side")),
            recurringIncomes = listOf(RecurringIncome(id = 9, name = "Salary", amount = 50000.0, dayOfMonth = 1, isEnabled = false))
        )
        assertEquals(3, data.version)
        assertEquals(data, ok(BackupCodec.toJson(data)))
        assertTrue(data.missingSections.isEmpty())
    }

    @Test fun `version 2 backups from before the month start day leave it unset`() {
        val v2 = """{"version":2,"exportedAt":1,"categories":[],"expenses":[],"recurringTemplates":[],
            "settings":{"monthlyIncome":50000.0,"weekStartsOnMonday":true}}"""
        assertEquals(null, ok(v2).settings!!.monthStartDay)
    }

    @Test fun `backups never contain the captured SMS or notification text`() {
        val sms = "ICICI Bank Acct XX123 debited for Rs 349.00; BIGBASKET credited. Avl Bal Rs 18,204.55"
        val data = BackupData(
            exportedAt = 1L, categories = emptyList(), recurringTemplates = emptyList(),
            expenses = listOf(Expense(id = 1, amount = 349.0, description = "BIGBASKET", source = "SMS", rawSms = sms))
        )
        val json = BackupCodec.toJson(data)
        assertTrue("raw text leaked into the backup", !json.contains("Avl Bal") && !json.contains("XX123"))
        val restored = ok(json).expenses!!.single()
        assertEquals(null, restored.rawSms)
        assertEquals("BIGBASKET", restored.description)
        assertEquals(349.0, restored.amount, 0.0)
    }

    @Test fun `version 1 backups still restore, and say what they don't contain`() {
        val v1 = """{"version":1,"exportedAt":1700000000000,
            "categories":[{"id":1,"name":"Food","emoji":"🍔","colorHex":"#E53935","monthlyLimit":0.0}],
            "expenses":[{"id":1,"amount":99.0,"description":"Tea","categoryId":1,"date":1700000000000,"source":"Manual","tags":"","paymentMethod":"UPI"}],
            "recurringTemplates":[]}"""
        val data = ok(v1)
        assertEquals(1, data.version)
        assertEquals(null, data.bills)
        assertEquals(listOf("bills", "loans", "savings goals", "learned categories", "message rules", "income", "recurring income"), data.missingSections)
    }

    @Test fun `backup from a newer app version is refused`() {
        assertTrue(invalid("""{"version":99,"categories":[],"expenses":[]}""").contains("newer version"))
        assertTrue(invalid("""{"version":4,"categories":[],"expenses":[]}""").contains("newer version"))
    }

    @Test fun `version 2 backups have no income, so restoring leaves the phone's alone`() {
        val v2 = ok("""{"version":2,"exportedAt":1,"categories":[],"expenses":[],"recurringTemplates":[],"bills":[],"loans":[],
            "savingsGoals":[],"merchantMappings":[],"parsingRules":[],"settings":{"monthlyIncome":50000.0}}""")
        assertEquals(null, v2.incomes)
        assertEquals(null, v2.recurringIncomes)
        assertEquals(listOf("income", "recurring income"), v2.missingSections)
    }

    @Test fun `damaged income is caught before restoring`() {
        fun v3(section: String) = """{"version":3,"categories":[],"expenses":[],$section}"""
        assertTrue(invalid(v3(""""incomes":[{"id":1,"amount":NaN,"description":"Salary","date":1,"source":"Manual","tags":""}]""")).contains("income 1"))
        assertTrue(invalid(v3(""""incomes":[{"id":1,"amount":5.0,"date":1,"source":"Manual","tags":""}]""")).contains("income 1"))
        assertTrue(invalid(v3(""""incomes":[{"id":1,"amount":5.0,"description":"x","date":1}]""")).contains("income 1"))
        assertTrue(invalid(v3(""""incomes":[null]""")).contains("income 1"))
        assertTrue(invalid(v3(""""recurringIncomes":[{"id":1,"amount":5.0,"dayOfMonth":1,"isEnabled":true}]""")).contains("recurring income 1"))
        assertTrue(invalid(v3(""""recurringIncomes":[{"id":1,"name":"Salary","amount":Infinity,"dayOfMonth":1,"isEnabled":true}]""")).contains("recurring income 1"))
        assertTrue(invalid(v3(""""recurringIncomes":[{"id":1,"name":"Salary","amount":5.0,"isEnabled":true}]""")).contains("recurring income 1"))
        assertTrue(invalid(v3(""""recurringIncomes":[{"id":1,"name":"Salary","amount":5.0,"dayOfMonth":32,"isEnabled":true}]""")).contains("recurring income 1"))
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

    @Test fun `a damaged message rule is caught before restoring`() {
        assertTrue(invalid("""{"version":2,"categories":[],"expenses":[],"parsingRules":[{"id":"x","kind":"PAYMENT","pattern":"(","label":"x"}]}""").contains("message rule 1"))
        assertTrue(invalid("""{"version":2,"categories":[],"expenses":[],"parsingRules":[{"id":"x","label":"x"}]}""").contains("message rule 1"))
    }
}
