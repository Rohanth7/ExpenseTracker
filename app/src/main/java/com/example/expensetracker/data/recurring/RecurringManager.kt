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
        val monthStart = (cal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val monthEnd = (cal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }.timeInMillis

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

    /**
     * After a restore: the "logged this month" marks describe the data that was replaced, so they're
     * dropped and anything due is logged (the database checks still stop duplicates).
     */
    suspend fun reapplyIncomeAfterRestore(db: AppDatabase, prefs: SharedPreferences): Int {
        prefs.edit().apply { prefs.all.keys.filter { it.startsWith(INCOME_APPLIED) }.forEach { remove(it) } }.apply()
        return applyRecurringIncome(db, prefs, Calendar.getInstance())
    }

    private const val INCOME_APPLIED = "income_applied_"

    /**
     * Logs each enabled recurring income on its calendar day (clamped, like bills), dated midnight
     * of that day, once per calendar month. Returns how many were logged.
     */
    suspend fun applyRecurringIncome(db: AppDatabase, prefs: SharedPreferences, now: Calendar): Int {
        val month = "${now.get(Calendar.YEAR)}_${now.get(Calendar.MONTH)}"
        val monthStart = (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val monthEnd = (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        var logged = 0
        db.recurringIncomeDao().getEnabled().forEach { r ->
            val key = "$INCOME_APPLIED${month}_${r.id}"
            if (prefs.getBoolean(key, false)) return@forEach
            val dueDate = RecurringSchedule.dueDateIfReached(r.dayOfMonth, now) ?: return@forEach
            // The DB check covers a crash between the insert and the pref; doing it in the same
            // transaction as the insert covers two launches running at once. A match logged by hand
            // (same name and amount, like bills) means it was entered before it was set up here.
            val inserted = db.withTransaction {
                if (db.incomeDao().countRecurringInRange(r.id, monthStart, monthEnd) > 0 ||
                    db.incomeDao().countByHandInRange(r.name, r.amount, monthStart, monthEnd) > 0) false
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
