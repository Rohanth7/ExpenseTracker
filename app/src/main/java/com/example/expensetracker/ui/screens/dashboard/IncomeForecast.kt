package com.example.expensetracker.ui.screens.dashboard

import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Income
import com.example.expensetracker.data.db.entity.RecurringIncome
import com.example.expensetracker.data.period.BudgetPeriod
import com.example.expensetracker.data.period.DateRange
import com.example.expensetracker.data.recurring.RecurringIncomeRule
import com.example.expensetracker.data.recurring.RecurringSchedule
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

    /** [incomes] can be all of them; [marks] are RecurringManager's (see [RecurringManager.incomeMarks]). */
    fun cycleIncome(incomes: List<Income>, recurring: List<RecurringIncome>, expectedMonthly: Double, now: Long, cycle: DateRange, marks: Set<String> = emptySet()) =
        CycleIncome(incomes.filter { it.date in cycle }.sumOf { it.amount }, recurringStillDue(recurring, now, cycle, incomes, marks), expectedMonthly)

    /** The same, straight from the database (widgets). */
    suspend fun load(db: AppDatabase, expectedMonthly: Double, now: Long, cycle: DateRange, marks: Set<String>): CycleIncome {
        // The calendar months the cycle's due dates fall in, for RecurringIncomeRule
        val from = RecurringSchedule.monthBounds(Calendar.getInstance().apply { timeInMillis = cycle.start }).first
        val to = RecurringSchedule.monthBounds(Calendar.getInstance().apply { timeInMillis = cycle.endInclusive }).second
        return CycleIncome(db.incomeDao().sumInRange(cycle.start, cycle.endInclusive),
            recurringStillDue(db.recurringIncomeDao().getEnabled(), now, cycle, db.incomeDao().getInRange(from, to), marks), expectedMonthly)
    }

    fun forCycle(loggedThisCycle: Double, recurringStillDue: Double, expectedMonthly: Double): Double =
        if (loggedThisCycle > 0 || recurringStillDue > 0) loggedThisCycle + recurringStillDue else expectedMonthly

    /**
     * Recurring income due from today to the end of [cycle] that RecurringManager will still log:
     * not if its month is marked done, or [RecurringIncomeRule] finds it in [incomes]. Today's
     * counts until then, since the widget, or an app left open overnight, sees payday start first.
     */
    fun recurringStillDue(
        recurring: List<RecurringIncome>, now: Long, cycle: DateRange,
        incomes: List<Income> = emptyList(), marks: Set<String> = emptySet()
    ): Double {
        val todayStart = BudgetPeriod.startOfDay(now)
        if (todayStart > cycle.endInclusive) return 0.0
        val enabled = recurring.filter { it.isEnabled }
        return enabled.sumOf { r ->
            CashFlow.dueDatesInRange(r.dayOfMonth, maxOf(todayStart, cycle.start), cycle.endInclusive)
                .count { due -> !done(r, due, enabled, incomes, marks) } * r.amount
        }
    }

    private fun done(r: RecurringIncome, due: Long, enabled: List<RecurringIncome>, incomes: List<Income>, marks: Set<String>): Boolean {
        val month = Calendar.getInstance().apply { timeInMillis = due }
        if (RecurringIncomeRule.markKey(month.get(Calendar.YEAR), month.get(Calendar.MONTH), r.id) in marks) return true
        val (start, end) = RecurringSchedule.monthBounds(month)
        return RecurringIncomeRule.loggedIn(r, incomes.filter { it.date in start..end }, enabled)
    }
}
