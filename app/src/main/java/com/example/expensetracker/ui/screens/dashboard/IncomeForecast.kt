package com.example.expensetracker.ui.screens.dashboard

import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Income
import com.example.expensetracker.data.db.entity.RecurringIncome
import com.example.expensetracker.data.db.entity.RecurringIncomeMonth
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
        val total: Double get() = if (fromRecords) logged + stillDue else expected
    }

    fun forCycle(loggedThisCycle: Double, recurringStillDue: Double, expectedMonthly: Double): Double =
        CycleIncome(loggedThisCycle, recurringStillDue, expectedMonthly).total

    /** [incomes] can be all of them; [months] are RecurringManager's records (see [RecurringIncomeMonth]). */
    fun cycleIncome(
        incomes: List<Income>, recurring: List<RecurringIncome>, expectedMonthly: Double, now: Long, cycle: DateRange,
        months: List<RecurringIncomeMonth> = emptyList()
    ): CycleIncome {
        val (from, to) = calendarMonthsOf(cycle)
        return CycleIncome(incomes.filter { it.date in cycle }.sumOf { it.amount },
            recurringStillDue(recurring, now, cycle, incomes.filter { it.date in from..to }, months), expectedMonthly)
    }

    /** The same, straight from the database (widgets). */
    suspend fun load(db: AppDatabase, expectedMonthly: Double, now: Long, cycle: DateRange): CycleIncome {
        val (from, to) = calendarMonthsOf(cycle)
        return CycleIncome(db.incomeDao().sumInRange(cycle.start, cycle.endInclusive),
            recurringStillDue(db.recurringIncomeDao().getEnabled(), now, cycle, db.incomeDao().getInRange(from, to),
                db.recurringIncomeDao().getMonthsOnce()), expectedMonthly)
    }

    /**
     * Recurring income due in [cycle] that RecurringManager will still log: due from the start of
     * this calendar month on (it logs any whose day has passed, when the app next opens; it doesn't
     * go back to last month), and not done by [RecurringIncomeRule]. So payday's salary counts in
     * the widget before the app opens, and a hand-entered or deleted one doesn't.
     */
    fun recurringStillDue(
        recurring: List<RecurringIncome>, now: Long, cycle: DateRange,
        incomes: List<Income> = emptyList(), months: List<RecurringIncomeMonth> = emptyList()
    ): Double {
        val from = maxOf(cycle.start, RecurringSchedule.monthBounds(Calendar.getInstance().apply { timeInMillis = now }).first)
        if (from > cycle.endInclusive) return 0.0
        val enabled = recurring.filter { it.isEnabled }
        return enabled.sumOf { r ->
            CashFlow.dueDatesInRange(r.dayOfMonth, from, cycle.endInclusive).count { due ->
                val at = Calendar.getInstance().apply { timeInMillis = due }
                val (start, end) = RecurringSchedule.monthBounds(at)
                !RecurringIncomeRule.done(r, at.get(Calendar.YEAR), at.get(Calendar.MONTH) + 1,
                    incomes.filter { it.date in start..end }, enabled, months)
            } * r.amount
        }
    }

    /** First and last millisecond of the calendar months [cycle] touches. */
    private fun calendarMonthsOf(cycle: DateRange): Pair<Long, Long> =
        RecurringSchedule.monthBounds(Calendar.getInstance().apply { timeInMillis = cycle.start }).first to
            RecurringSchedule.monthBounds(Calendar.getInstance().apply { timeInMillis = cycle.endInclusive }).second
}
