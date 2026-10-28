package com.example.expensetracker.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.expensetracker.data.backup.worker.AutoBackupWorker
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.period.MonthStartDay
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.data.recurring.RecurringManager
import com.example.expensetracker.sms.ParsingRuleStore
import com.example.expensetracker.ui.widgets.WidgetUpdateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** [database] and [preferences] can be swapped out in tests; the app uses the real ones. */
class BackupManager(
    private val context: Context,
    private val database: (() -> AppDatabase)? = null,
    private val preferences: (() -> PreferencesManager)? = null,
    private val ruleStore: (() -> ParsingRuleStore)? = null
) {
    private val db get() = database?.invoke() ?: AppDatabase.getInstance(context)
    private val prefs get() = preferences?.invoke() ?: PreferencesManager(context)
    private val rules get() = ruleStore?.invoke() ?: ParsingRuleStore.get(context)

    fun scheduleAutoBackup() {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(1, TimeUnit.HOURS) // Don't run immediately on app open
            .addTag("auto_backup")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "daily_backup",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancelAutoBackup() {
        WorkManager.getInstance(context).cancelUniqueWork("daily_backup")
    }

    /** Runs the Drive backup once, now. */
    fun backUpNow() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "backup_now", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AutoBackupWorker>().build()
        )
    }

    /** Everything a restore needs. (Not in a transaction: Room's Flow reads can deadlock inside one.) */
    suspend fun snapshot(): BackupData = withContext(Dispatchers.IO) {
        val db = db
        val p = prefs
        run {
            BackupData(
                categories = db.categoryDao().getAllOnce(),
                expenses = db.expenseDao().getAll().first(),
                recurringTemplates = db.recurringTemplateDao().getAll().first(),
                bills = db.billDao().getAll().first(),
                loans = db.loanDao().getAll().first(),
                savingsGoals = db.savingsGoalDao().getAll().first(),
                merchantMappings = db.merchantMappingDao().getAllMappings(),
                parsingRules = rules.rules.value,
                incomes = db.incomeDao().getAllOnce(),
                recurringIncomes = db.recurringIncomeDao().getAllOnce(),
                recurringIncomeLogged = RecurringManager.incomeMarks(p.sharedPreferences).sorted(),
                settings = BackupSettings(
                    monthlyIncome = p.monthlyIncome,
                    budgetAlertsEnabled = p.budgetAlertsEnabled,
                    budgetAlertThreshold = p.budgetAlertThreshold,
                    weekStartsOnMonday = p.weekStartsOnMonday,
                    monthStartDay = p.monthStartDay
                )
            )
        }
    }

    /**
     * Replaces the phone's data with [data] in a single transaction: if anything fails, nothing
     * changes. Sections missing from the backup (older files) are left as they are. IDs are kept,
     * so expenses still point at the right categories.
     */
    suspend fun restore(data: BackupData) = withContext(Dispatchers.IO) {
        val db = db
        db.withTransaction {
            data.categories?.let { list -> db.categoryDao().deleteAll(); db.categoryDao().insertAll(list) }
            data.expenses?.let { list -> db.expenseDao().deleteAll(); list.forEach { db.expenseDao().insert(it) } }
            data.recurringTemplates?.let { list -> db.recurringTemplateDao().deleteAll(); db.recurringTemplateDao().insertAll(list) }
            data.bills?.let { list -> db.billDao().deleteAll(); list.forEach { db.billDao().insert(it) } }
            data.loans?.let { list -> db.loanDao().deleteAll(); list.forEach { db.loanDao().insert(it) } }
            data.savingsGoals?.let { list -> db.savingsGoalDao().deleteAll(); list.forEach { db.savingsGoalDao().insert(it) } }
            data.merchantMappings?.let { list -> db.merchantMappingDao().deleteAll(); list.forEach { db.merchantMappingDao().insert(it) } }
            data.incomes?.let { list -> db.incomeDao().deleteAll(); db.incomeDao().insertAll(list) }
            data.recurringIncomes?.let { list -> db.recurringIncomeDao().deleteAll(); db.recurringIncomeDao().insertAll(list) }
        }
        // Settings are applied only after the database commit succeeded. Category ids now come
        // from the backup, so the add dialog's last-used id may point at another category.
        if (data.categories != null) prefs.lastManualCategoryId = -1L
        data.parsingRules?.let { rules.replaceAll(it) }
        data.settings?.let { s ->
            val p = prefs
            s.monthlyIncome?.let { p.monthlyIncome = it }
            s.budgetAlertsEnabled?.let { p.budgetAlertsEnabled = it }
            s.budgetAlertThreshold?.let { p.budgetAlertThreshold = it }
            s.weekStartsOnMonday?.let { p.weekStartsOnMonday = it }
            s.monthStartDay?.let { p.monthStartDay = it }
        }
        // Best effort, like the widget below: the data is already restored. The phone's "done this
        // month" marks were about the replaced data; the backup's take their place.
        if (data.recurringIncomes != null) runCatching {
            RecurringManager.restoreIncomeMarks(db, prefs.sharedPreferences, data.recurringIncomeLogged.orEmpty())
        }
        // Screens follow the month start live; tests use their own prefs and leave the app's alone.
        if (preferences == null) MonthStartDay.reload(context)
        // Best effort: the data is already restored, so a widget that can't refresh mustn't fail it.
        if (database == null) runCatching { WidgetUpdateHelper.update(context) }
    }

    suspend fun exportToUri(uri: Uri, data: BackupData): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openOutputStream(uri) ?: error("Can't open file")
            stream.use { it.write(BackupCodec.toJson(data).toByteArray()) }
        }.isSuccess
    }

    suspend fun readFromUri(uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
        }.getOrNull() ?: return@withContext ParseResult.Invalid("Couldn't read the file.")
        BackupCodec.parse(text)
    }
}
