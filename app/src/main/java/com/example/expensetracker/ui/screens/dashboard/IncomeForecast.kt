package com.example.expensetracker.ui.screens.dashboard

import com.example.expensetracker.data.db.entity.RecurringIncome
import com.example.expensetracker.data.period.BudgetPeriod
import com.example.expensetracker.data.period.DateRange
import java.util.Calendar

/**
 * Which income a budget month's forecast uses. Logged income plus recurring income still to come,
 * once there is any; until then the "Expected monthly income" setting, so nothing changes for
 * someone who hasn't logged income.
 */
object IncomeForecast {
    fun forCycle(loggedThisCycle: Double, recurringStillDue: Double, expectedMonthly: Double): Double =
        if (loggedThisCycle > 0 || recurringStillDue > 0) loggedThisCycle + recurringStillDue else expectedMonthly

    /**
     * Recurring income due after today and inside [cycle]. Today's is left out: it's logged when
     * the app opens, so it's already in the logged total.
     */
    fun recurringStillDue(recurring: List<RecurringIncome>, now: Long, cycle: DateRange): Double {
        // Calendar arithmetic, not + 24 h: a daylight-saving day is 23 or 25 hours long.
        val tomorrowStart = Calendar.getInstance().apply { timeInMillis = BudgetPeriod.startOfDay(now); add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        if (tomorrowStart > cycle.endInclusive) return 0.0
        return recurring.filter { it.isEnabled }.sumOf { r ->
            CashFlow.dueDatesInRange(r.dayOfMonth, maxOf(tomorrowStart, cycle.start), cycle.endInclusive).size * r.amount
        }
    }
}
