package com.example.expensetracker.data.recurring

import android.content.Context
import android.content.SharedPreferences
import androidx.room.withTransaction
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.db.entity.Income
import com.example.expensetracker.ui.widgets.WidgetUpdateHelper
import java.util.Calendar

object RecurringManager {
    private const val PREFS = "expense_tracker_prefs"

    suspend fun applyIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cal = Calendar.getInstance()
        val currentMonth = "${cal.get(Calendar.YEAR)}_${cal.get(Calendar.MONTH)}"

        val db = AppDatabase.getInstance(context)
        val bills = db.billDao().getAutoLogEnabled()
        val (monthStart, monthEnd) = RecurringSchedule.monthBounds(cal)

        bills.forEach { bill ->
            val key = "bill_applied_${currentMonth}_${bill.id}"
            if (prefs.getBoolean(key, false)) return@forEach
            // Logged on its due day, dated that day (it used to be logged on the 1st).
            val dueDate = RecurringSchedule.dueDateIfReached(bill.dueDayOfMonth, cal) ?: return@forEach

            // The pref flag and the insert aren't atomic, so a crash between them leaves the flag
            // unset. Check the DB too so the next launch doesn't log the bill a second time.
            if (db.expenseDao().countRecurringInMonth(bill.name, bill.amount, monthStart, monthEnd) == 0) {
                db.expenseDao().insert(
                    Expense(
                        amount = bill.amount,
                        description = bill.name,
                        categoryId = bill.categoryId,
                        source = "Recurring",
                        date = dueDate
                    )
                )
            }
            prefs.edit().putBoolean(key, true).apply()
        }

        // The widget's forecast counts logged income.
        if (applyRecurringIncome(db, prefs, cal) > 0) WidgetUpdateHelper.update(context)
    }

    /** After recurring income is added or changed in Settings: logs any that's due now, without waiting for the next launch. */
    suspend fun applyIncomeNow(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (applyRecurringIncome(AppDatabase.getInstance(context), prefs, Calendar.getInstance()) > 0) WidgetUpdateHelper.update(context)
    }

    private const val INCOME_APPLIED = "income_applied_"

    /** The recurring income months marked done (see [RecurringIncomeRule.markKey]); IncomeForecast and backups read them. */
    fun incomeMarks(prefs: SharedPreferences): Set<String> =
        prefs.all.filter { (k, v) -> k.startsWith(INCOME_APPLIED) && v == true }.keys.map { it.removePrefix(INCOME_APPLIED) }.toSet()

    /**
     * After a restore: the phone's marks describe the data that was replaced, so they're swapped for
     * the backup's ([marks]; a salary deleted on purpose stays deleted) and anything due is logged.
     */
    suspend fun restoreIncomeMarks(db: AppDatabase, prefs: SharedPreferences, marks: Collection<String>): Int {
        val valid = marks.filter { (it as String?)?.matches(Regex("""\d+_\d+_\d+""")) == true }
        prefs.edit().apply {
            prefs.all.keys.filter { it.startsWith(INCOME_APPLIED) }.forEach { remove(it) }
            valid.forEach { putBoolean(INCOME_APPLIED + it, true) }
        }.commit()
        return applyRecurringIncome(db, prefs, Calendar.getInstance())
    }

    /**
     * Logs each enabled recurring income on its calendar day (clamped, like bills), dated midnight
     * of that day, once per calendar month, unless [RecurringIncomeRule] finds it's already there.
     * Returns how many were logged.
     */
    suspend fun applyRecurringIncome(db: AppDatabase, prefs: SharedPreferences, now: Calendar): Int {
        val (monthStart, monthEnd) = RecurringSchedule.monthBounds(now)
        var logged = 0
        val enabled = db.recurringIncomeDao().getEnabled()
        enabled.forEach { r ->
            val key = INCOME_APPLIED + RecurringIncomeRule.markKey(now.get(Calendar.YEAR), now.get(Calendar.MONTH), r.id)
            if (prefs.getBoolean(key, false)) return@forEach
            val dueDate = RecurringSchedule.dueDateIfReached(r.dayOfMonth, now) ?: return@forEach
            // The DB check covers a crash between the insert and the pref; doing it in the same
            // transaction as the insert covers two launches running at once.
            val inserted = db.withTransaction {
                if (RecurringIncomeRule.loggedIn(r, db.incomeDao().getInRange(monthStart, monthEnd), enabled)) false
                else {
                    db.incomeDao().insert(Income(amount = r.amount, description = r.name, date = dueDate, source = "Recurring", recurringIncomeId = r.id))
                    true
                }
            }
            if (inserted) logged++
            prefs.edit().putBoolean(key, true).apply()
        }
        return logged
    }
}
