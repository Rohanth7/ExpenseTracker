package com.example.expensetracker.data.recurring

import com.example.expensetracker.data.db.entity.Income
import com.example.expensetracker.data.db.entity.RecurringIncome
import com.example.expensetracker.data.db.entity.RecurringIncomeMonth

/**
 * When a recurring income's month is done. RecurringManager logs by it and IncomeForecast leaves
 * out what it says is done, so the forecast never counts income that won't be logged.
 */
object RecurringIncomeRule {
    /**
     * True if [r]'s [year]/[month] (1–12) is done: RecurringManager has handled it (a row in
     * [months], whatever happened to its entry since), or [monthIncomes] (that calendar month's)
     * has it entered by hand: same name, ignoring case and spaces, and amount, the way bills dedupe.
     * Recurring incomes that are alike share hand entries one each, earliest due day first;
     * [enabled] is all of them.
     */
    fun done(
        r: RecurringIncome, year: Int, month: Int, monthIncomes: List<Income>,
        enabled: List<RecurringIncome>, months: Collection<RecurringIncomeMonth>
    ): Boolean {
        fun handled(id: Long) = months.firstOrNull { it.recurringIncomeId == id && it.year == year && it.month == month }
        if (handled(r.id) != null) return true
        val alike = enabled.filter { alike(it.name, it.amount, r) }
        // Hand entries not already taken by an alike one that RecurringManager matched to them
        val free = monthIncomes.count { it.recurringIncomeId == null && alike(it.description, it.amount, r) } -
            alike.count { handled(it.id)?.byHand == true }
        val waiting = alike.filter { handled(it.id) == null }.sortedWith(compareBy({ it.dayOfMonth }, { it.id }))
        return waiting.indexOfFirst { it.id == r.id } in 0 until free
    }

    private fun alike(name: String, amount: Double, r: RecurringIncome) =
        amount == r.amount && name.trim().equals(r.name.trim(), ignoreCase = true)
}
