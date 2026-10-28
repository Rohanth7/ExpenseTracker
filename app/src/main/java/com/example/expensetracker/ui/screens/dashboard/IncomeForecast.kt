package com.example.expensetracker.ui.screens.dashboard

import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Income
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
    /** One budget month's income: what was logged in it, recurring income still to come, and the fallback. */
    data class CycleIncome(val logged: Double, val stillDue: Double, val expected: Double) {
        /** True once income has been logged or is on its way; the expected figure is then ignored. */
        val fromRecords: Boolean get() = logged > 0 || stillDue > 0
        val total: Double get() = forCycle(logged, stillDue, expected)
    }

    fun cycleIncome(incomes: List<Income>, recurring: List<RecurringIncome>, expectedMonthly: Double, now: Long, cycle: DateRange): CycleIncome {
        val inCycle = incomes.filter { it.date in cycle }
        val loggedToday = inCycle.filter { it.date >= BudgetPeriod.startOfDay(now) }.mapNotNull { it.recurringIncomeId }.toSet()
        return CycleIncome(inCycle.sumOf { it.amount }, recurringStillDue(recurring, now, cycle, loggedToday), expectedMonthly)
    }

    /** The same, straight from the database (widgets). */
    suspend fun load(db: AppDatabase, expectedMonthly: Double, now: Long, cycle: DateRange): CycleIncome {
        val loggedToday = db.incomeDao().recurringIdsInRange(maxOf(BudgetPeriod.startOfDay(now), cycle.start), cycle.endInclusive).toSet()
        return CycleIncome(db.incomeDao().sumInRange(cycle.start, cycle.endInclusive),
            recurringStillDue(db.recurringIncomeDao().getEnabled(), now, cycle, loggedToday), expectedMonthly)
    }

    fun forCycle(loggedThisCycle: Double, recurringStillDue: Double, expectedMonthly: Double): Double =
        if (loggedThisCycle > 0 || recurringStillDue > 0) loggedThisCycle + recurringStillDue else expectedMonthly

    /**
     * Recurring income due from today to the end of [cycle]. Today's counts until it's logged
     * ([loggedToday] holds the ids already logged from today on): the app logs it when it opens,
     * but the widget, or an app left open overnight, sees payday start before that.
     */
    fun recurringStillDue(recurring: List<RecurringIncome>, now: Long, cycle: DateRange, loggedToday: Set<Long> = emptySet()): Double {
        val todayStart = BudgetPeriod.startOfDay(now)
        // Calendar arithmetic, not + 24 h: a daylight-saving day is 23 or 25 hours long.
        val tomorrowStart = Calendar.getInstance().apply { timeInMillis = todayStart; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        if (todayStart > cycle.endInclusive) return 0.0
        return recurring.filter { it.isEnabled }.sumOf { r ->
            CashFlow.dueDatesInRange(r.dayOfMonth, maxOf(todayStart, cycle.start), cycle.endInclusive)
                .count { it >= tomorrowStart || r.id !in loggedToday } * r.amount
        }
    }
}
