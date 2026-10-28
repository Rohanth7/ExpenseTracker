package com.example.expensetracker.widget

import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.period.BudgetPeriod
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.data.recurring.RecurringManager
import com.example.expensetracker.ui.screens.dashboard.IncomeForecast

data class WidgetData(
    val spent: Double,
    val income: Double,
    val monthLabel: String,
    /** Day of the budget month (1 on its first day) and its length; see BudgetPeriod. */
    val dayOfMonth: Int,
    val daysInMonth: Int,
    /** Last month's spending up to the same point of the month (see BudgetPeriod.comparableTotal). */
    val prevMonthSpent: Double,
    val prevMonthLabel: String,
    val widgetEnabled: Boolean
)

class BudgetPulseRepository(private val db: AppDatabase, private val prefs: PreferencesManager) {

    suspend fun load(): WidgetData {
        val now = System.currentTimeMillis()
        // Budget months follow the user's start day (see BudgetPeriod); bills don't affect the widget.
        val startDay = prefs.monthStartDay
        val cur = BudgetPeriod.current(now, startDay)
        val prev = BudgetPeriod.forOffset(now, startDay, -1)
        val prevSpent = db.expenseDao().getTotalSpentInRange(prev.start, prev.endInclusive)

        return WidgetData(
            spent = db.expenseDao().getTotalSpentInRange(cur.start, cur.endInclusive),
            // Same rule as the Overview: logged and recurring income, else the expected figure
            income = IncomeForecast.load(db, prefs.monthlyIncome, now, cur, RecurringManager.incomeMarks(prefs.sharedPreferences)).total,
            monthLabel = BudgetPeriod.compactLabel(cur, startDay),
            dayOfMonth = BudgetPeriod.dayOfCycle(now, startDay),
            daysInMonth = BudgetPeriod.daysInCycle(cur),
            // At today's pace, like the app's Overview and Statistics
            prevMonthSpent = BudgetPeriod.comparableTotal(prevSpent, prev, cur, now),
            prevMonthLabel = (if (startDay == 1) BudgetPeriod.compactLabel(prev, startDay) else "LAST MONTH") + " PACE",
            widgetEnabled = prefs.widgetEnabled
        )
    }
}
