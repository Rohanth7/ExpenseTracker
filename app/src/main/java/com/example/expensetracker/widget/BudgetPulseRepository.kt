package com.example.expensetracker.widget

import com.example.expensetracker.data.budget.Budgets
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.period.BudgetPeriod
import com.example.expensetracker.data.period.OverspentDay
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.ui.mascot.MascotMood
import com.example.expensetracker.ui.mascot.MascotVoice
import com.example.expensetracker.ui.mascot.mascotMonth
import com.example.expensetracker.ui.screens.dashboard.CashFlow
import com.example.expensetracker.ui.screens.dashboard.IncomeForecast
import kotlinx.coroutines.flow.first

data class WidgetData(
    val spent: Double,
    val income: Double,
    /** Bills and EMIs still due this budget month, as on Home. */
    val stillDue: Double,
    /** Days left in the budget month, today included. */
    val daysLeft: Int,
    /** Her face, the same as on Home; null when "Show the pot" is off. */
    val mood: MascotMood?,
    val widgetEnabled: Boolean
) {
    val hasIncome get() = income > 0.0
    val freeToSpend get() = income - spent - stillDue
}

class BudgetPulseRepository(private val db: AppDatabase, private val prefs: PreferencesManager) {

    /** The same figures Home shows for the current month, from the same helpers. */
    suspend fun load(now: Long = System.currentTimeMillis()): WidgetData {
        val startDay = prefs.monthStartDay
        val cur = BudgetPeriod.current(now, startDay)
        val expenses = db.expenseDao().getByDateRange(cur.start, cur.endInclusive).first()
        val spent = expenses.sumOf { it.amount }
        // Same rule as Home: logged and recurring income, else the expected figure
        val income = IncomeForecast.load(db, prefs.monthlyIncome, now, cur).total
        val stillDue = CashFlow.billsStillDue(db.billDao().getAllOnce(), now, cur) +
            CashFlow.emiStillDue(db.loanDao().getAll().first(), now, cur)
        // Recorded whether or not she is shown, like Home does.
        val daysOver = OverspentDay(prefs.overspentDays).record(income, spent, stillDue, now, startDay)
        val mood = if (!prefs.mascotEnabled) null else {
            val budgets = Budgets.of(db.categoryDao().getAllOnce(), expenses.filter { it.categoryId != -1L }).filter { it.spent > 0.0 }
            MascotVoice.say(mascotMonth(income, spent, stillDue, budgets, prefs.budgetAlertThreshold, now, startDay, daysOver)).mood
        }
        return WidgetData(
            spent = spent,
            income = income,
            stillDue = stillDue,
            daysLeft = BudgetPeriod.daysLeft(now, startDay).coerceAtLeast(1),
            mood = mood,
            widgetEnabled = prefs.widgetEnabled
        )
    }
}
