package com.example.expensetracker.widget

import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.preferences.PreferencesManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class WidgetData(
    val spent: Double,
    val income: Double,
    val monthLabel: String,
    val dayOfMonth: Int,
    val daysInMonth: Int,
    val prevMonthSpent: Double,
    val prevMonthLabel: String,
    val widgetEnabled: Boolean
)

class BudgetPulseRepository(private val db: AppDatabase, private val prefs: PreferencesManager) {

    suspend fun load(): WidgetData {
        val now = Calendar.getInstance()
        val prevCal = (now.clone() as Calendar).apply { add(Calendar.MONTH, -1) }

        val (curStart, curEnd) = monthRange(now)
        val (prevStart, prevEnd) = monthRange(prevCal)

        val fmt = SimpleDateFormat("MMM", Locale.getDefault())

        return WidgetData(
            spent = db.expenseDao().getTotalSpentInRange(curStart, curEnd),
            income = prefs.monthlyIncome,
            monthLabel = fmt.format(now.time).uppercase(Locale.getDefault()),
            dayOfMonth = now.get(Calendar.DAY_OF_MONTH),
            daysInMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH),
            prevMonthSpent = db.expenseDao().getTotalSpentInRange(prevStart, prevEnd),
            prevMonthLabel = fmt.format(prevCal.time).uppercase(Locale.getDefault()),
            widgetEnabled = prefs.widgetEnabled
        )
    }

    private fun monthRange(cal: Calendar): Pair<Long, Long> {
        val start = (cal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = (cal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        return start to end
    }
}
