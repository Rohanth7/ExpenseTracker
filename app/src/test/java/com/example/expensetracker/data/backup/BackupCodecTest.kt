package com.example.expensetracker.data.backup

import com.example.expensetracker.data.db.entity.Bill
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.db.entity.Income
import com.example.expensetracker.data.db.entity.Loan
import com.example.expensetracker.data.db.entity.MerchantMapping
import com.example.expensetracker.data.db.entity.RecurringIncome
import com.example.expensetracker.data.db.entity.RecurringIncomeMonth
import com.example.expensetracker.data.db.entity.SavingsGoal
import com.example.expensetracker.data.recurring.BillMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            settings = BackupSettings(monthlyIncome = 75000.0, budgetAlertsEnabled = true, budgetAlertThreshold = 85, weekStartsOnMonday = true, monthStartDay = 25, priceAlertsEnabled = false),
            parsingRules = listOf(com.example.expensetracker.sms.ParsingRule("r1", com.example.expensetracker.sms.ParsingRule.Kind.PAYMENT,
                "^Paid\\s+Rs\\s+(\\d[\\d,]*)\\s+to\\s+(.+)$", 1, 2, "Paid Rs <amount> to <payee>", 1L)),
            incomes = listOf(Income(id = 8, amount = 50000.0, description = "Salary", date = 1_760_000_000_000, source = "Recurring", recurringIncomeId = 9),
                Income(id = 10, amount = 2500.5, description = "Freelance", tags = "side")),
            recurringIncomes = listOf(RecurringIncome(id = 9, name = "Salary", amount = 50000.0, dayOfMonth = 1, isEnabled = false)),
            recurringIncomeMonths = listOf(RecurringIncomeMonth(9, 2026, 10), RecurringIncomeMonth(9, 2026, 9, byHand = true))
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

    /** No format version change: a file from before price alerts just doesn't have the setting. */
    @Test fun `backups from before price alerts leave the setting unset`() {
        val v3 = """{"version":3,"exportedAt":1,"categories":[],"expenses":[],"recurringTemplates":[],
            "settings":{"monthlyIncome":50000.0,"budgetAlertsEnabled":false,"monthStartDay":1}}"""
        assertEquals(null, ok(v3).settings!!.priceAlertsEnabled)
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
        assertEquals(listOf("bills", "loans", "savings goals", "learned categories", "message rules", "income records", "recurring income"), data.missingSections)
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
        assertEquals(listOf("income records", "recurring income"), v2.missingSections)
    }

    @Test fun `damaged income is caught before restoring`() {
        fun v3(section: String) = """{"version":3,"categories":[],"expenses":[],$section}"""
        assertTrue(invalid(v3(""""incomes":[{"id":1,"amount":NaN,"description":"Salary","date":1,"source":"Manual","tags":""}]""")).contains("income 1"))
        assertTrue(invalid(v3(""""incomes":[{"id":1,"amount":5.0,"date":1,"source":"Manual","tags":""}]""")).contains("income 1"))
        assertTrue(invalid(v3(""""incomes":[{"id":1,"amount":5.0,"description":"x","source":"Manual","tags":""}]""")).contains("income 1 has no date"))
        assertTrue(invalid(v3(""""incomes":[null]""")).contains("income 1"))
        assertTrue(invalid(v3(""""recurringIncomes":[{"id":1,"amount":5.0,"dayOfMonth":1,"isEnabled":true}]""")).contains("recurring income 1"))
        assertTrue(invalid(v3(""""recurringIncomes":[{"id":1,"name":"Salary","amount":Infinity,"dayOfMonth":1,"isEnabled":true}]""")).contains("recurring income 1"))
        assertTrue(invalid(v3(""""recurringIncomes":[{"id":1,"name":"Salary","amount":5.0,"isEnabled":true}]""")).contains("recurring income 1"))
        assertTrue(invalid(v3(""""recurringIncomes":[{"id":1,"name":"Salary","amount":5.0,"dayOfMonth":32,"isEnabled":true}]""")).contains("recurring income 1"))
        assertTrue(invalid(v3(""""recurringIncomeMonths":[{"recurringIncomeId":1,"year":2026,"month":13,"byHand":false}]""")).contains("recurring income month 1"))
        assertTrue(invalid(v3(""""recurringIncomeMonths":[null]""")).contains("recurring income month 1"))
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

    /** Gson skips Kotlin defaults, so an older or hand-made file's missing fields were null and the restore failed. */
    @Test fun `missing fields get the entity's defaults`() {
        val data = ok("""{"version":1,"categories":[{"id":1,"name":"Food"}],
            "expenses":[{"id":1,"amount":250.0,"description":"Swiggy","categoryId":1,"date":1790000000000,"tags":null}],
            "bills":[{"id":1,"name":"Rent","amount":20000.0,"dueDayOfMonth":5}],
            "loans":[{"id":1,"name":"Car","totalAmount":100000.0,"monthlyEmi":5000.0,"tenureMonths":24,"startDate":1}],
            "savingsGoals":[{"id":1,"name":"Trip","targetAmount":5000.0}],
            "incomes":[{"id":1,"amount":5.0,"description":"Gift","date":1}],
            "recurringIncomes":[{"id":1,"name":"Salary","amount":50000.0,"dayOfMonth":1}]}""")
        assertEquals(Expense(id = 1, amount = 250.0, description = "Swiggy", categoryId = 1, date = 1790000000000), data.expenses!!.single())
        assertEquals(Category(id = 1, name = "Food"), data.categories!!.single())
        assertEquals(Bill(id = 1, name = "Rent", amount = 20000.0, dueDayOfMonth = 5), data.bills!!.single())
        assertEquals(Loan(id = 1, name = "Car", totalAmount = 100000.0, monthlyEmi = 5000.0, tenureMonths = 24, startDate = 1), data.loans!!.single())
        assertEquals(SavingsGoal(id = 1, name = "Trip", targetAmount = 5000.0), data.savingsGoals!!.single())
        assertEquals(Income(id = 1, amount = 5.0, description = "Gift", date = 1), data.incomes!!.single())
        assertEquals(RecurringIncome(id = 1, name = "Salary", amount = 50000.0, dayOfMonth = 1), data.recurringIncomes!!.single())
        // An expense without a category is pending, not in category 0
        assertEquals(-1L, ok("""{"version":1,"categories":[],"expenses":[{"amount":5.0,"description":"Tea","date":1}]}""").expenses!!.single().categoryId)
    }

    @Test fun `an expense without a date is refused, not dated 1970`() {
        assertTrue(invalid("""{"version":1,"categories":[],"expenses":[{"id":1,"amount":250.0,"description":"Swiggy"}]}""").contains("expense 1 has no date"))
        assertTrue(invalid("""{"version":3,"categories":[],"expenses":[{"id":1,"amount":250.0,"description":"Swiggy","date":0}]}""").contains("expense 1 has no date"))
    }

    /** An amount is a primitive, so a missing one used to be read as ₹0 and pass the checks. */
    @Test fun `a record without an amount or start date is refused, not restored as 0`() {
        assertTrue(invalid("""{"version":3,"categories":[],"expenses":[{"id":1,"description":"Swiggy","date":1}]}""").contains("expense 1 is incomplete"))
        assertTrue(invalid("""{"version":3,"categories":[],"expenses":[],"incomes":[{"id":1,"description":"Gift","date":1}]}""").contains("income 1 is incomplete"))
        val v3 = """{"version":3,"categories":[],"expenses":[],"loans":[{"id":1,"name":"Car","tenureMonths":12"""
        assertTrue(invalid("""$v3,"totalAmount":100000.0,"startDate":1}]}""").contains("loan 1 is incomplete"))
        assertTrue(invalid("""$v3,"monthlyEmi":5000.0,"startDate":1}]}""").contains("loan 1 is incomplete"))
        assertTrue(invalid("""$v3,"totalAmount":100000.0,"monthlyEmi":5000.0}]}""").contains("loan 1 has no start date"))
    }

    /** Values the app's screens can't make: the slider is 50–95%, a goal needs a target, a loan a tenure. */
    @Test fun `values out of range are refused`() {
        fun v3(section: String) = """{"version":3,"categories":[],"expenses":[],$section}"""
        assertTrue(invalid(v3(""""settings":{"budgetAlertThreshold":500}""")).contains("threshold"))
        assertTrue(invalid(v3(""""settings":{"budgetAlertThreshold":49}""")).contains("threshold"))
        assertTrue(invalid(v3(""""settings":{"monthlyIncome":-85000.0}""")).contains("income"))
        assertTrue(invalid("""{"version":3,"categories":[{"id":1,"name":"Food","monthlyLimit":-500.0}],"expenses":[]}""").contains("category 1"))
        assertTrue(invalid("""{"version":3,"categories":[{"id":1,"name":"Food","colorHex":"not-a-colour"}],"expenses":[]}""").contains("category 1"))
        assertTrue(invalid(v3(""""savingsGoals":[{"id":1,"name":"Trip","targetAmount":0.0}]""")).contains("savings goal 1"))
        assertTrue(invalid(v3(""""savingsGoals":[{"id":1,"name":"Trip","targetAmount":10.0,"colorHex":"green-ish"}]""")).contains("savings goal 1"))
        val loan = """"loans":[{"id":1,"name":"Car","totalAmount":100000.0,"monthlyEmi":5000.0,"startDate":1"""
        assertTrue(invalid(v3("""$loan,"tenureMonths":0}]""")).contains("loan 1"))
        assertTrue(invalid(v3("""$loan,"tenureMonths":12,"dueDayOfMonth":99}]""")).contains("loan 1"))
        val bill = """"bills":[{"id":1,"name":"Rent","amount":20000.0"""
        assertTrue(invalid(v3("""$bill,"dueDayOfMonth":0}]""")).contains("bill 1"))
        assertTrue(invalid(v3("""$bill}]""")).contains("bill 1"))
        assertTrue(invalid(v3("""$bill,"dueDayOfMonth":5,"reminderDays":-4}]""")).contains("bill 1"))
        assertTrue(invalid(v3("""$bill,"dueDayOfMonth":5,"reminderDays":15}]""")).contains("bill 1"))
        // The edges the app allows
        ok(v3(""""settings":{"budgetAlertThreshold":50,"monthlyIncome":0.0},"savingsGoals":[{"id":1,"name":"Trip","targetAmount":0.01,"colorHex":"#FF7DC9A5"}],
            $loan,"tenureMonths":1,"dueDayOfMonth":31}],$bill,"dueDayOfMonth":31,"reminderDays":14}]"""))
    }

    @Test fun `an automatic backup never replaces one with records by an empty phone's`() {
        val withRecords = BackupCodec.toJson(BackupData(categories = emptyList(), expenses = listOf(Expense(amount = 1.0, description = "Tea", date = 1))))
        val reset = BackupData(categories = listOf(Category(id = 1, name = "Food")), expenses = emptyList(), bills = emptyList())
        assertFalse(reset.hasRecords)
        assertFalse(BackupCodec.mayReplace(withRecords, reset))
        // Nothing to lose: no backup yet, an empty one, or one that can't be read
        assertTrue(BackupCodec.mayReplace(null, reset))
        assertTrue(BackupCodec.mayReplace(BackupCodec.toJson(reset), reset))
        assertTrue(BackupCodec.mayReplace("not a backup", reset))
        // Once something is entered again, backups go on as before
        val next = reset.copy(incomes = listOf(Income(amount = 5.0, description = "Gift", date = 1)))
        assertTrue(BackupCodec.mayReplace(withRecords, next))
    }

    /** B-01: the months auto-log bills were handled in travel with the bills. */
    @Test fun `bill months round-trip and a file without them still reads`() {
        val months = listOf(BillMonth(billId = 3, year = 2026, month = 11))
        val data = BackupData(categories = emptyList(), expenses = emptyList(),
            bills = listOf(Bill(id = 3, name = "Rent", amount = 22000.0, dueDayOfMonth = 5, autoLog = true)), billMonths = months)
        assertEquals(months, ok(BackupCodec.toJson(data)).billMonths)
        assertEquals(null, ok("""{"version":3,"categories":[],"expenses":[],"bills":[]}""").billMonths)
        assertTrue(invalid("""{"version":3,"categories":[],"expenses":[],"billMonths":[{"billId":3,"year":2026,"month":13}]}""").contains("bill month 1"))
    }
}
