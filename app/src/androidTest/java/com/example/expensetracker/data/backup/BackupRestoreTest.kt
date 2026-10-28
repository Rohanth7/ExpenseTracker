package com.example.expensetracker.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Bill
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.db.entity.Income
import com.example.expensetracker.data.db.entity.Loan
import com.example.expensetracker.data.db.entity.MerchantMapping
import com.example.expensetracker.data.db.entity.RecurringIncome
import com.example.expensetracker.data.db.entity.SavingsGoal
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.data.recurring.RecurringManager
import com.example.expensetracker.sms.ParsingRule
import com.example.expensetracker.sms.ParsingRuleStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/** Runs on a device against throwaway in-memory databases; the app's real data isn't touched. */
@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var source: AppDatabase
    private lateinit var target: AppDatabase
    private lateinit var sourcePrefs: PreferencesManager
    private lateinit var targetPrefs: PreferencesManager

    private fun memoryDb() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

    @Before fun setUp() {
        source = memoryDb(); target = memoryDb()
        context.deleteSharedPreferences("backup_test_source")
        context.deleteSharedPreferences("backup_test_target")
        context.deleteSharedPreferences("backup_test_rules_source")
        context.deleteSharedPreferences("backup_test_rules_target")
        sourcePrefs = PreferencesManager(context, "backup_test_source")
        targetPrefs = PreferencesManager(context, "backup_test_target")
    }

    @After fun tearDown() { source.close(); target.close() }

    private fun rulesFor(prefs: PreferencesManager) =
        if (prefs === sourcePrefs) sourceRules else targetRules
    private val sourceRules by lazy { ParsingRuleStore(context, "backup_test_rules_source") }
    private val targetRules by lazy { ParsingRuleStore(context, "backup_test_rules_target") }
    private fun manager(db: AppDatabase, prefs: PreferencesManager) = BackupManager(context, { db }, { prefs }, { rulesFor(prefs) })

    private suspend fun seedSource() {
        source.categoryDao().insertAll(listOf(Category(id = 10, name = "Food"), Category(id = 11, name = "Rent")))
        source.expenseDao().insert(Expense(id = 100, amount = 250.0, description = "Swiggy", categoryId = 10))
        source.expenseDao().insert(Expense(id = 101, amount = 22000.0, description = "Rent", categoryId = 11, source = "Recurring"))
        source.billDao().insert(Bill(id = 5, name = "Rent", amount = 22000.0, dueDayOfMonth = 5, categoryId = 11, autoLog = true))
        source.loanDao().insert(Loan(id = 6, name = "Car", totalAmount = 500000.0, monthlyEmi = 12000.0, tenureMonths = 48, startDate = 1L))
        source.savingsGoalDao().insert(SavingsGoal(id = 7, name = "Trip", targetAmount = 50000.0, currentAmount = 1200.0))
        source.merchantMappingDao().insert(MerchantMapping("Swiggy", 10))
        source.recurringIncomeDao().insert(RecurringIncome(id = 4, name = "Salary", amount = 50000.0, dayOfMonth = 1))
        // Dated this month: a restore logs this month's salary if the backup doesn't have it yet
        source.incomeDao().insert(Income(id = 40, amount = 50000.0, description = "Salary", date = System.currentTimeMillis(), source = "Recurring", recurringIncomeId = 4))
        source.incomeDao().insert(Income(id = 41, amount = 3000.0, description = "Freelance", date = 3L, tags = "side"))
        sourcePrefs.sharedPreferences.edit().putBoolean("income_applied_2026_9_4", true).commit()
        sourcePrefs.monthlyIncome = 75000.0
        sourcePrefs.budgetAlertThreshold = 85
        sourceRules.add(ParsingRule("r1", ParsingRule.Kind.IGNORE, "^Promo\\s+\\d+$", label = "Promo #", createdAt = 1L))
    }

    private suspend fun seedTargetWithOtherData() {
        target.categoryDao().insert(Category(id = 1, name = "Old category"))
        target.expenseDao().insert(Expense(id = 1, amount = 5.0, description = "Old expense"))
        target.billDao().insert(Bill(id = 1, name = "Old bill", amount = 10.0, dueDayOfMonth = 1))
        target.incomeDao().insert(Income(id = 1, amount = 7.0, description = "Old income", date = 1L))
        target.recurringIncomeDao().insert(RecurringIncome(id = 1, name = "Old salary", amount = 8.0, dayOfMonth = 15))
    }

    private suspend fun targetIncome() = target.incomeDao().getAllOnce() to target.recurringIncomeDao().getAllOnce()

    @Test fun everythingRoundTripsThroughAFile() = runBlocking {
        seedSource(); seedTargetWithOtherData()
        val json = BackupCodec.toJson(manager(source, sourcePrefs).snapshot())
        val parsed = (BackupCodec.parse(json) as ParseResult.Ok).data
        manager(target, targetPrefs).restore(parsed)

        assertEquals(source.categoryDao().getAllOnce(), target.categoryDao().getAllOnce())
        assertEquals(source.expenseDao().getAll().first(), target.expenseDao().getAll().first())
        assertEquals(source.billDao().getAll().first(), target.billDao().getAll().first())
        assertEquals(source.loanDao().getAll().first(), target.loanDao().getAll().first())
        assertEquals(source.savingsGoalDao().getAll().first(), target.savingsGoalDao().getAll().first())
        assertEquals(source.merchantMappingDao().getAllMappings(), target.merchantMappingDao().getAllMappings())
        assertEquals(source.incomeDao().getAllOnce(), target.incomeDao().getAllOnce())
        assertEquals(source.recurringIncomeDao().getAllOnce(), target.recurringIncomeDao().getAllOnce())
        assertTrue("2026_9_4" in RecurringManager.incomeMarks(targetPrefs.sharedPreferences))
        assertEquals(75000.0, targetPrefs.monthlyIncome, 0.0)
        assertEquals(85, targetPrefs.budgetAlertThreshold)
        assertEquals(sourceRules.rules.value, targetRules.rules.value)
        // Old data is gone, not merged
        assertTrue(target.expenseDao().getAll().first().none { it.description == "Old expense" })
    }

    @Test fun olderBackupLeavesSectionsItDoesNotHave() = runBlocking {
        seedTargetWithOtherData()
        val v1 = BackupData(version = 1, categories = listOf(Category(id = 2, name = "Food")),
            expenses = listOf(Expense(id = 2, amount = 9.0, description = "Tea", categoryId = 2)))
        manager(target, targetPrefs).restore(v1)
        assertEquals(listOf("Food"), target.categoryDao().getAllOnce().map { it.name })
        // The v1 file had no bills, so the phone's bill is kept
        assertEquals(listOf("Old bill"), target.billDao().getAll().first().map { it.name })
    }

    @Test fun version2BackupLeavesThePhonesIncomeAlone() = runBlocking {
        seedTargetWithOtherData()
        val before = targetIncome()
        val v2 = BackupCodec.parse("""{"version":2,"exportedAt":1,"categories":[],"expenses":[],"recurringTemplates":[],
            "bills":[],"loans":[],"savingsGoals":[],"merchantMappings":[],"parsingRules":[]}""") as ParseResult.Ok
        manager(target, targetPrefs).restore(v2.data)
        // The restore ran (expenses and bills replaced), but income wasn't in the file
        assertTrue(target.expenseDao().getAll().first().isEmpty())
        assertTrue(target.billDao().getAll().first().isEmpty())
        assertEquals(before, targetIncome())
    }

    /** The phone's "logged this month" marks are about the data a restore replaces. */
    @Test fun restoredRecurringIncomeIsLoggedEvenIfThePhoneHadLoggedTheSameId() = runBlocking {
        val now = Calendar.getInstance()
        context.getSharedPreferences("backup_test_target", Context.MODE_PRIVATE).edit()
            .putBoolean("income_applied_${now.get(Calendar.YEAR)}_${now.get(Calendar.MONTH)}_1", true).commit()
        // Taken before payday: a salary on the 1st with nothing logged for it this month
        manager(target, targetPrefs).restore(BackupData(categories = emptyList(), expenses = emptyList(), incomes = emptyList(),
            recurringIncomes = listOf(RecurringIncome(id = 1, name = "Salary", amount = 50000.0, dayOfMonth = 1))))
        assertEquals(listOf(1L), target.incomeDao().getAllOnce().map { it.recurringIncomeId })
    }

    /** A salary deleted on purpose this month (marked done, no entry) stays deleted after a restore. */
    @Test fun aDeletedSalaryInTheBackupIsNotLoggedAgain() = runBlocking {
        val now = Calendar.getInstance()
        manager(target, targetPrefs).restore(BackupData(categories = emptyList(), expenses = emptyList(), incomes = emptyList(),
            recurringIncomes = listOf(RecurringIncome(id = 1, name = "Salary", amount = 50000.0, dayOfMonth = 1)),
            recurringIncomeLogged = listOf("${now.get(Calendar.YEAR)}_${now.get(Calendar.MONTH)}_1")))
        assertTrue(target.incomeDao().getAllOnce().isEmpty())
    }

    @Test fun failedRestoreLeavesIncomeUnchanged() = runBlocking {
        seedTargetWithOtherData()
        val before = targetIncome()
        // Incomes are replaced first; the duplicate recurring income id then fails the transaction.
        val broken = BackupData(
            categories = emptyList(), expenses = emptyList(),
            incomes = listOf(Income(id = 2, amount = 1.0, description = "New")),
            recurringIncomes = listOf(RecurringIncome(id = 9, name = "A", amount = 1.0, dayOfMonth = 1), RecurringIncome(id = 9, name = "B", amount = 2.0, dayOfMonth = 2))
        )
        assertTrue("restore should fail", runCatching { manager(target, targetPrefs).restore(broken) }.isFailure)
        assertEquals(before, targetIncome())
    }

    @Test fun failedRestoreChangesNothing() = runBlocking {
        seedTargetWithOtherData()
        val before = Triple(target.categoryDao().getAllOnce(), target.expenseDao().getAll().first(), target.billDao().getAll().first())
        // Two bills with the same id: the second insert fails after categories and expenses
        // have already been replaced inside the transaction.
        val broken = BackupData(
            categories = listOf(Category(id = 3, name = "New")),
            expenses = listOf(Expense(id = 3, amount = 1.0, description = "New")),
            bills = listOf(Bill(id = 9, name = "A", amount = 1.0, dueDayOfMonth = 1), Bill(id = 9, name = "B", amount = 2.0, dueDayOfMonth = 2))
        )
        val failed = runCatching { manager(target, targetPrefs).restore(broken) }.isFailure
        assertTrue("restore should fail", failed)
        assertEquals(before, Triple(target.categoryDao().getAllOnce(), target.expenseDao().getAll().first(), target.billDao().getAll().first()))
    }

    @Test fun restoringCategoriesForgetsTheLastUsedOne() = runBlocking {
        // Category ids come from the backup, so id 10 may now be a different category; the add
        // dialog must not start on it.
        seedSource(); seedTargetWithOtherData()
        targetPrefs.lastManualCategoryId = 10
        manager(target, targetPrefs).restore(manager(source, sourcePrefs).snapshot())
        assertEquals(-1L, targetPrefs.lastManualCategoryId)
    }
}
