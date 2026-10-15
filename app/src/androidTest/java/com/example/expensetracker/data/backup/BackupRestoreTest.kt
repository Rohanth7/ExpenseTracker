package com.example.expensetracker.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Bill
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.db.entity.Loan
import com.example.expensetracker.data.db.entity.MerchantMapping
import com.example.expensetracker.data.db.entity.SavingsGoal
import com.example.expensetracker.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
        sourcePrefs = PreferencesManager(context, "backup_test_source")
        targetPrefs = PreferencesManager(context, "backup_test_target")
    }

    @After fun tearDown() { source.close(); target.close() }

    private fun manager(db: AppDatabase, prefs: PreferencesManager) = BackupManager(context, { db }, { prefs })

    private suspend fun seedSource() {
        source.categoryDao().insertAll(listOf(Category(id = 10, name = "Food"), Category(id = 11, name = "Rent")))
        source.expenseDao().insert(Expense(id = 100, amount = 250.0, description = "Swiggy", categoryId = 10))
        source.expenseDao().insert(Expense(id = 101, amount = 22000.0, description = "Rent", categoryId = 11, source = "Recurring"))
        source.billDao().insert(Bill(id = 5, name = "Rent", amount = 22000.0, dueDayOfMonth = 5, categoryId = 11, autoLog = true))
        source.loanDao().insert(Loan(id = 6, name = "Car", totalAmount = 500000.0, monthlyEmi = 12000.0, tenureMonths = 48, startDate = 1L))
        source.savingsGoalDao().insert(SavingsGoal(id = 7, name = "Trip", targetAmount = 50000.0, currentAmount = 1200.0))
        source.merchantMappingDao().insert(MerchantMapping("Swiggy", 10))
        sourcePrefs.monthlyIncome = 75000.0
        sourcePrefs.budgetAlertThreshold = 85
    }

    private suspend fun seedTargetWithOtherData() {
        target.categoryDao().insert(Category(id = 1, name = "Old category"))
        target.expenseDao().insert(Expense(id = 1, amount = 5.0, description = "Old expense"))
        target.billDao().insert(Bill(id = 1, name = "Old bill", amount = 10.0, dueDayOfMonth = 1))
    }

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
        assertEquals(75000.0, targetPrefs.monthlyIncome, 0.0)
        assertEquals(85, targetPrefs.budgetAlertThreshold)
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
}
