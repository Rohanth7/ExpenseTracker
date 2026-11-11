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
import com.example.expensetracker.data.recurring.BillMonth
import com.example.expensetracker.data.recurring.RecurringManager
import com.example.expensetracker.notification.BillReminderHelper
import com.example.expensetracker.notification.BudgetAlertHelper
import com.example.expensetracker.notification.PriceChangeAlerts
import com.example.expensetracker.sms.ParsingRuleStore
import com.example.expensetracker.ui.widgets.WidgetUpdateHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Calendar
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
                recurringIncomeMonths = db.recurringIncomeDao().getMonthsOnce(),
                billMonths = p.billMonths,
                settings = BackupSettings(
                    monthlyIncome = p.monthlyIncome,
                    budgetAlertsEnabled = p.budgetAlertsEnabled,
                    budgetAlertThreshold = p.budgetAlertThreshold,
                    weekStartsOnMonday = p.weekStartsOnMonday,
                    monthStartDay = p.monthStartDay,
                    priceAlertsEnabled = p.priceAlertsEnabled
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
            val categoriesBefore = if (data.categories != null) db.categoryDao().getAllOnce() else null
            data.categories?.let { list -> db.categoryDao().deleteAll(); db.categoryDao().insertAll(list) }
            data.expenses?.let { list -> db.expenseDao().deleteAll(); list.forEach { db.expenseDao().insert(it) } }
            data.recurringTemplates?.let { list -> db.recurringTemplateDao().deleteAll(); db.recurringTemplateDao().insertAll(list) }
            data.bills?.let { list -> db.billDao().deleteAll(); list.forEach { db.billDao().insert(it) } }
            data.loans?.let { list -> db.loanDao().deleteAll(); list.forEach { db.loanDao().insert(it) } }
            data.savingsGoals?.let { list -> db.savingsGoalDao().deleteAll(); list.forEach { db.savingsGoalDao().insert(it) } }
            data.merchantMappings?.let { list -> db.merchantMappingDao().deleteAll(); list.forEach { db.merchantMappingDao().insert(it) } }
            data.incomes?.let { list -> db.incomeDao().deleteAll(); db.incomeDao().insertAll(list) }
            data.recurringIncomes?.let { list ->
                db.recurringIncomeDao().deleteAll(); db.recurringIncomeDao().insertAll(list)
                // The handled months go with the recurring incomes they describe, never the phone's
                db.recurringIncomeDao().deleteAllMonths(); db.recurringIncomeDao().insertAllMonths(data.recurringIncomeMonths.orEmpty())
            }
            // An older file (v1, v2) has categories but not bills or learned merchants. The phone's
            // stay, and their category ids now belong to the file's categories: follow each to the
            // restored category of the same name, or drop the link.
            if (categoriesBefore != null) {
                val links = CategoryLinks(categoriesBefore, data.categories.orEmpty())
                if (data.bills == null) db.billDao().getAllOnce().filter { it.categoryId != -1L }.forEach { bill ->
                    db.billDao().update(bill.copy(categoryId = links.moved(bill.categoryId) ?: -1L))
                }
                if (data.merchantMappings == null) db.merchantMappingDao().getAllMappings().forEach { mapping ->
                    val moved = links.moved(mapping.categoryId)
                    if (moved != null) db.merchantMappingDao().insert(mapping.copy(categoryId = moved))
                    else db.merchantMappingDao().deleteByNames(listOf(mapping.merchantName))
                }
            }
            // Whatever the file said, nothing is left pointing at a category it didn't bring.
            db.expenseDao().moveOrphansToPending()
            db.categoryDao().clearMissingBillCategories()
            db.categoryDao().deleteMerchantMappingsForMissingCategories()
            db.categoryDao().clearMissingTemplateCategories()
        }
        // What the phone remembered about the data it had: alerts already sent this month and
        // price changes already alerted. A dismissed subscription stays dismissed: that is the
        // user's decision about a payee, not a fact about the data ("Reset everything" clears it).
        PriceChangeAlerts.forgetAlerts(context)
        BudgetAlertHelper.forgetAlerts(context)
        if (data.bills != null) BillReminderHelper.forgetReminders(context)
        // Settings are applied only after the database commit succeeded. Category ids now come
        // from the backup, so the add dialog's last-used id may point at another category.
        if (data.categories != null) prefs.lastManualCategoryId = -1L
        // Which months the bills were logged in describes the restored bills and expenses, never
        // the phone's: its own record would skip a bill the backup has no expense for. A file from
        // before they were saved says when it was made, which tells which bills were handled by then.
        data.bills?.let { bills -> prefs.billMonths = data.billMonths ?: BillMonth.inferred(bills, data.exportedAt) }
        data.parsingRules?.let { rules.replaceAll(it) }
        data.settings?.let { s ->
            val p = prefs
            s.monthlyIncome?.let { p.monthlyIncome = it }
            s.budgetAlertsEnabled?.let { p.budgetAlertsEnabled = it }
            s.budgetAlertThreshold?.let { p.budgetAlertThreshold = it }
            s.weekStartsOnMonday?.let { p.weekStartsOnMonday = it }
            s.monthStartDay?.let { p.monthStartDay = it }
            s.priceAlertsEnabled?.let { p.priceAlertsEnabled = it }
        }
        // Log bills and recurring income that are due (a backup from before the due day). The
        // restore is complete either way, and the next launch tries again, so a failure here
        // mustn't fail it. Each on its own: bills failing mustn't skip the income.
        suspend fun bestEffort(log: suspend () -> Unit) {
            try { log() } catch (e: Exception) { if (e is CancellationException) throw e }
        }
        if (data.bills != null) bestEffort { RecurringManager.applyBills(db, prefs, Calendar.getInstance()) }
        if (data.recurringIncomes != null) bestEffort { RecurringManager.applyRecurringIncome(db, Calendar.getInstance()) }
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

    /**
     * Reads and checks a file the user picked. The picker offers any file, so one too large to be
     * a backup (a video) is refused by its size, before it is read into memory.
     */
    suspend fun readFromUri(uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        val tooLarge = ParseResult.Invalid("This file is too large to be a MyMoney backup (over ${MAX_BACKUP_BYTES / (1024 * 1024)} MB).")
        val size = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull()
        if (size != null && size > MAX_BACKUP_BYTES) return@withContext tooLarge
        // Counted while reading too: not every file provider says how large a file is.
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { readCapped(it, MAX_BACKUP_BYTES) ?: return@withContext tooLarge }
        }.getOrNull() ?: return@withContext ParseResult.Invalid("Couldn't read the file.")
        BackupCodec.parse(text)
    }

    companion object {
        /** Far above any real backup: 100,000 expenses come to about 25 MB. */
        const val MAX_BACKUP_BYTES = 50 * 1024 * 1024

        /** [input] as text, or null once it turns out longer than [maxBytes]. */
        internal fun readCapped(input: InputStream, maxBytes: Int): String? {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (out.size() + read > maxBytes) return null
                out.write(buffer, 0, read)
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }
}
