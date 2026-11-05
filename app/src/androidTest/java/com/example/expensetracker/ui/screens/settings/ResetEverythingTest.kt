package com.example.expensetracker.ui.screens.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import com.example.expensetracker.data.backup.BackupCodec
import com.example.expensetracker.data.backup.BackupManager
import com.example.expensetracker.data.backup.ParseResult
import com.example.expensetracker.data.backup.worker.AutoBackupWorker
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.defaults.DefaultCategories
import com.example.expensetracker.data.period.MonthStartDay
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.data.repository.BillRepository
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.data.repository.IncomeRepository
import com.example.expensetracker.data.repository.LoanRepository
import com.example.expensetracker.data.repository.RecurringTemplateRepository
import com.example.expensetracker.data.repository.SavingsGoalRepository
import com.example.expensetracker.data.subscriptions.DismissedSubscriptions
import com.example.expensetracker.sms.ParsingRule
import com.example.expensetracker.sms.ParsingRuleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * "Reset everything" against a throwaway database, settings and rules file. It also clears files
 * only the app has (dismissed subscriptions, alert history) and the backup job, so those are put
 * back afterwards.
 */
@RunWith(AndroidJUnit4::class)
class ResetEverythingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var prefs: PreferencesManager
    private lateinit var rules: ParsingRuleStore
    private lateinit var manager: BackupManager
    private lateinit var file: File
    private val appFiles = listOf("budget_alert_prefs", "price_alert_prefs")
    private lateinit var savedFiles: Map<String, Map<String, *>>
    private lateinit var savedDismissed: Set<String>
    private var savedMonthStart = 1

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        context.deleteSharedPreferences("reset_test_prefs")
        context.deleteSharedPreferences("reset_test_rules")
        prefs = PreferencesManager(context, "reset_test_prefs")
        rules = ParsingRuleStore(context, "reset_test_rules")
        manager = BackupManager(context, { db }, { prefs }, { rules })
        file = File(context.cacheDir, "reset_test_backup.json").apply { delete() }
        savedFiles = appFiles.associateWith { context.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        savedDismissed = DismissedSubscriptions.flow(context).value
        savedMonthStart = MonthStartDay.get(context)
    }

    @After fun tearDown() {
        savedFiles.forEach { (name, entries) -> context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().putAll(entries).commit() }
        DismissedSubscriptions.clear(context)
        savedDismissed.forEach { DismissedSubscriptions.dismiss(context, it) }
        MonthStartDay.set(context, savedMonthStart)
        if (PreferencesManager(context).autoBackupEnabled) BackupManager(context).scheduleAutoBackup()
        file.delete()
        db.close()
    }

    private fun SharedPreferences.Editor.putAll(entries: Map<String, *>) = apply {
        clear()
        entries.forEach { (k, v) ->
            when (v) {
                is Boolean -> putBoolean(k, v)
                is Long -> putLong(k, v)
                is Int -> putInt(k, v)
                is Float -> putFloat(k, v)
                is String -> putString(k, v)
                is Set<*> -> putStringSet(k, v.filterIsInstance<String>().toSet())
            }
        }
    }

    private suspend fun reset() {
        val vm = SettingsViewModel(
            context, CategoryRepository(db.categoryDao(), db.expenseDao()), ExpenseRepository(db.expenseDao(), context),
            RecurringTemplateRepository(db.recurringTemplateDao()), BillRepository(db.billDao()), LoanRepository(db.loanDao()),
            SavingsGoalRepository(db.savingsGoalDao()), IncomeRepository(db.incomeDao(), db.recurringIncomeDao(), context),
            manager, prefs, rules
        )
        withContext(Dispatchers.Main) { vm.resetAll() }.join()
    }

    private fun backedUpExpenses() = (BackupCodec.parse(file.readText()) as ParseResult.Ok).data.expenses!!.size

    /** BUG-01: the daily job stayed scheduled and uploaded the empty phone over the backup. */
    @Test fun resetTurnsAutoBackupOff() = runBlocking {
        prefs.autoBackupEnabled = true
        manager.scheduleAutoBackup()
        reset()
        assertFalse(prefs.autoBackupEnabled)
        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork("daily_backup").get()
        assertTrue("daily_backup still scheduled: ${work.map { it.state }}", work.all { it.state.isFinished })
    }

    /** BUG-01: even if a backup runs after a reset (setup adds the starter categories), it keeps the old one. */
    @Test fun aBackupAfterResetKeepsTheBackedUpData() = runBlocking {
        db.categoryDao().insert(Category(id = 1, name = "Food"))
        repeat(25) { db.expenseDao().insert(Expense(amount = 100.0 + it, description = "Expense $it", categoryId = 1)) }
        AutoBackupWorker.backUp(manager, file, drive = null)
        assertEquals(25, backedUpExpenses())

        reset()
        db.categoryDao().insertAll(DefaultCategories.STARTER)
        AutoBackupWorker.backUp(manager, file, drive = null)
        assertEquals(25, backedUpExpenses())

        // Once the user adds something, backups carry on
        db.expenseDao().insert(Expense(amount = 50.0, description = "Tea"))
        AutoBackupWorker.backUp(manager, file, drive = null)
        assertEquals(1, backedUpExpenses())
    }

    /** BUG-25: message rules, dismissed subscriptions, alert history and the last category survived. */
    @Test fun resetForgetsWhatWasLearnedFromTheData() = runBlocking {
        rules.add(ParsingRule("reset-test", ParsingRule.Kind.IGNORE, "^Hello\\s+\\d+$", label = "Hello #", createdAt = 1))
        DismissedSubscriptions.dismiss(context, "reset test payee")
        prefs.lastManualCategoryId = 7
        appFiles.forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().putBoolean("reset_test", true).commit() }
        prefs.biometricLockEnabled = true
        prefs.privacyMode = true
        prefs.driveConnected = true
        prefs.driveAccountEmail = "user@example.com"

        reset()

        assertEquals(emptyList<ParsingRule>(), rules.rules.value)
        assertEquals(emptySet<String>(), DismissedSubscriptions.flow(context).value)
        assertEquals(-1L, prefs.lastManualCategoryId)
        appFiles.forEach { assertTrue("$it not cleared", context.getSharedPreferences(it, Context.MODE_PRIVATE).all.isEmpty()) }
        // This phone's settings stay, as the dialog says
        assertTrue(prefs.biometricLockEnabled && prefs.privacyMode && prefs.driveConnected)
        assertEquals("user@example.com", prefs.driveAccountEmail)
    }
}
