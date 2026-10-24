package com.example.expensetracker.widget

import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.period.BudgetPeriod
import com.example.expensetracker.data.period.DateRange
import com.example.expensetracker.data.preferences.PreferencesManager
import java.text.SimpleDateFormat
import java.util.Locale

data class WidgetData(
    val spent: Double,
    val income: Double,
    val monthLabel: String,
    /** Day of the budget month (1 on its first day) and its length; see BudgetPeriod. */
    val dayOfMonth: Int,
    val daysInMonth: Int,
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
        val month = SimpleDateFormat("MMM", Locale.getDefault())
        val day = SimpleDateFormat("d MMM", Locale.getDefault())
        fun label(r: DateRange) = (if (startDay == 1) month.format(r.start)
            else "${day.format(r.start)}–${day.format(r.endInclusive)}").uppercase(Locale.getDefault())

        return WidgetData(
            spent = db.expenseDao().getTotalSpentInRange(cur.start, cur.endInclusive),
            income = prefs.monthlyIncome,
            monthLabel = label(cur),
            dayOfMonth = BudgetPeriod.dayOfCycle(now, startDay),
            daysInMonth = BudgetPeriod.daysInCycle(cur),
            prevMonthSpent = db.expenseDao().getTotalSpentInRange(prev.start, prev.endInclusive),
            prevMonthLabel = if (startDay == 1) label(prev) else "LAST MONTH",
            widgetEnabled = prefs.widgetEnabled
        )
    }
}
