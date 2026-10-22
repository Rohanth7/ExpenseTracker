package com.example.expensetracker.ui.screens.onboarding

import com.example.expensetracker.data.backup.BackupData

/** Result of restoring a backup during first-run setup. */
sealed class RestoreOutcome {
    data class Restored(val summary: String, val hasIncome: Boolean) : RestoreOutcome()
    data class Failed(val reason: String) : RestoreOutcome()
}

/** "43 expenses, 7 categories, 4 bills": expenses and categories always, other sections if present. */
fun restoreSummary(d: BackupData): String {
    fun part(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
    return buildList {
        add(part(d.expenses.orEmpty().size, "expense", "expenses"))
        add(part(d.categories.orEmpty().size, "category", "categories"))
        d.bills?.size?.takeIf { it > 0 }?.let { add(part(it, "bill", "bills")) }
        d.loans?.size?.takeIf { it > 0 }?.let { add(part(it, "loan", "loans")) }
        d.savingsGoals?.size?.takeIf { it > 0 }?.let { add(part(it, "savings goal", "savings goals")) }
    }.joinToString(", ")
}

/** True if the backup carried a monthly income, so setup doesn't need to ask for one. */
fun restoredIncome(d: BackupData): Boolean = (d.settings?.monthlyIncome ?: 0.0) > 0
