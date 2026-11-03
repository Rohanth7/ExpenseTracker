package com.example.expensetracker.data.recurring

import android.content.Context
import androidx.room.withTransaction
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.db.entity.Income
import com.example.expensetracker.data.db.entity.RecurringIncomeMonth
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
        if (applyRecurringIncome(db, cal) > 0) WidgetUpdateHelper.update(context)
    }

    /** After recurring income is added or changed in Settings: logs any that's due now, without waiting for the next launch. */
    suspend fun applyIncomeNow(context: Context) {
        if (applyRecurringIncome(AppDatabase.getInstance(context), Calendar.getInstance()) > 0) WidgetUpdateHelper.update(context)
    }

    /**
     * Logs each enabled recurring income on its calendar day (clamped, like bills), dated midnight
     * of that day, once per calendar month, unless [RecurringIncomeRule] finds the month done.
     * Either way the month is recorded (a [RecurringIncomeMonth]) in the same transaction, so a
     * deleted or moved entry doesn't make it log again. Returns how many were logged.
     */
    suspend fun applyRecurringIncome(db: AppDatabase, now: Calendar): Int {
        val (monthStart, monthEnd) = RecurringSchedule.monthBounds(now)
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH) + 1
        val dao = db.recurringIncomeDao()
        val enabled = dao.getEnabled()
        // By due day, the order the rule hands out hand entries in
        var logged = 0
        enabled.sortedWith(compareBy({ it.dayOfMonth }, { it.id })).forEach { r ->
            val dueDate = RecurringSchedule.dueDateIfReached(r.dayOfMonth, now) ?: return@forEach
            // One transaction, so two launches running at once log it once.
            val inserted = db.withTransaction {
                val months = dao.getMonthsIn(year, month)
                when {
                    months.any { it.recurringIncomeId == r.id } -> false
                    RecurringIncomeRule.done(r, year, month, db.incomeDao().getInRange(monthStart, monthEnd), enabled, months) -> {
                        dao.insertMonth(RecurringIncomeMonth(r.id, year, month, byHand = true)); false
                    }
                    else -> {
                        dao.insertMonth(RecurringIncomeMonth(r.id, year, month))
                        db.incomeDao().insert(Income(amount = r.amount, description = r.name, date = dueDate, source = "Recurring", recurringIncomeId = r.id))
                        true
                    }
                }
            }
            if (inserted) logged++
        }
        return logged
    }
}
