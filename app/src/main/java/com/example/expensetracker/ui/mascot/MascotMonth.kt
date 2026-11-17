package com.example.expensetracker.ui.mascot

import com.example.expensetracker.data.budget.Budget
import com.example.expensetracker.data.period.BudgetPeriod
import com.example.expensetracker.data.period.OverspentDay

/**
 * The current budget month as she sees it, from the figures Home shows. Home and the widget both
 * build it here, so they always show the same face.
 *
 * @param stillDue bills plus EMIs still due this month
 * @param now the moment it is computed for; only its day matters
 */
fun mascotMonth(
    income: Double,
    spent: Double,
    stillDue: Double,
    budgets: List<Budget>,
    alertPercent: Int,
    now: Long,
    monthStartDay: Int,
    overspent: OverspentDay,
): MascotMonth {
    val today = BudgetPeriod.epochDay(now)
    val monthStart = BudgetPeriod.epochDay(BudgetPeriod.current(now, monthStartDay).start)
    // Without an income there is no "free to spend", so the month can't have run out.
    val free = if (income > 0) income - spent - stillDue else 0.0
    return MascotMonth(
        income = income,
        spent = spent,
        stillDue = stillDue,
        budgets = budgets,
        alertPercent = alertPercent,
        daysLeft = BudgetPeriod.daysLeft(now, monthStartDay).coerceAtLeast(1),
        firstDay = today == monthStart,
        monthStartDay = monthStartDay,
        daysOver = overspent.daysOver(free, monthStart, today),
        epochDay = today,
    )
}
