package com.example.expensetracker.ui.screens.statistics

import com.example.expensetracker.data.db.entity.Income
import com.example.expensetracker.data.period.DateRange

/** "Income ₹X · Spent ₹Y · Saved ₹Z" for a stretch of days. Saved is negative when more went out than came in. */
data class IncomeSummary(val income: Double, val spent: Double) {
    val saved: Double get() = income - spent

    companion object {
        /** Null when no income was logged in [range]: someone who doesn't log income sees no change. */
        fun of(incomes: List<Income>, range: DateRange, spent: Double): IncomeSummary? =
            incomes.filter { it.date in range }.sumOf { it.amount }.takeIf { it > 0 }?.let { IncomeSummary(it, spent) }
    }
}
