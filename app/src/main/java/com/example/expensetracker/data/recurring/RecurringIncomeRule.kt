package com.example.expensetracker.data.recurring

import com.example.expensetracker.data.db.entity.Income
import com.example.expensetracker.data.db.entity.RecurringIncome

/**
 * When a recurring income's month is already logged. RecurringManager logs by it and IncomeForecast
 * leaves out what it says is done, so the forecast never counts income that won't be logged.
 */
object RecurringIncomeRule {
    /** RecurringManager's "done this month" mark, set once the due day is reached, logged or not. Month is 0-based. */
    fun markKey(year: Int, month: Int, id: Long) = "${year}_${month}_$id"

    /**
     * True if [monthIncomes] (one calendar month's) has [r]'s income: an entry logged for it, or one
     * entered by hand with the same name (ignoring case and spaces) and amount, the way bills dedupe.
     * Recurring incomes that are alike share the hand entries one each, lowest id first; [enabled]
     * is all of them.
     */
    fun loggedIn(r: RecurringIncome, monthIncomes: List<Income>, enabled: List<RecurringIncome>): Boolean {
        if (monthIncomes.any { it.recurringIncomeId == r.id }) return true
        val byHand = monthIncomes.count { it.recurringIncomeId == null && alike(it.description, it.amount, r) }
        if (byHand == 0) return false
        val waiting = enabled.filter { alike(it.name, it.amount, r) && monthIncomes.none { i -> i.recurringIncomeId == it.id } }.sortedBy { it.id }
        return waiting.indexOfFirst { it.id == r.id } in 0 until byHand
    }

    private fun alike(name: String, amount: Double, r: RecurringIncome) =
        amount == r.amount && name.trim().equals(r.name.trim(), ignoreCase = true)
}
