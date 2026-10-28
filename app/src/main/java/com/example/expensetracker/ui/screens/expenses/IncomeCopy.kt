package com.example.expensetracker.ui.screens.expenses

import com.example.expensetracker.data.db.entity.Income
import com.example.expensetracker.ui.util.AmountCalculator

/**
 * "Duplicate" from the income edit dialog: a new manual income dated now, as the form shows it.
 * Not tied to the recurring income that logged the original, so it can't block next month's.
 */
fun Income.duplicateWithEdits(amount: Double, description: String, tags: String, now: Long): Income =
    copy(id = 0, amount = amount, description = description, tags = tags, date = now, source = "Manual", recurringIncomeId = null)

/** True when the edit form differs from this saved income; same rules as the expense version in ExpenseCopy.kt. */
fun Income.isEditedTo(amountText: String, description: String, tags: String, date: Long): Boolean =
    (amountText != AmountCalculator.forEditing(amount) && AmountCalculator.evaluate(amountText) != amount) ||
        description != this.description || tags != this.tags || date != this.date

/**
 * The Income list with the filters the Income view shows: search (description or tags), the date
 * filter and the amount limits. Categories and tag chips belong to spending and are hidden there.
 */
fun filterIncomes(all: List<Income>, query: String, range: Pair<Long, Long>?, min: Double?, max: Double?, byAmount: Boolean): List<Income> {
    val filtered = all.filter { n ->
        (query.isBlank() || n.description.contains(query, ignoreCase = true) || n.tags.contains(query, ignoreCase = true)) &&
            (range == null || n.date in range.first..range.second) &&
            (min == null || n.amount >= min) && (max == null || n.amount <= max)
    }
    return if (byAmount) filtered.sortedByDescending { it.amount } else filtered.sortedByDescending { it.date }
}
