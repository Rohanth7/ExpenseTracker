package com.example.expensetracker.ui.screens.onboarding

import com.example.expensetracker.data.backup.BackupData
import com.google.gson.Gson

/**
 * A backup restored during first-run setup. It's saved in prefs until setup finishes, so the
 * remaining steps still know about it after the app is closed and reopened.
 */
data class RestoredInfo(val summary: String, val hasIncome: Boolean, val hadCategories: Boolean) {
    fun toJson(): String = Gson().toJson(this)

    companion object {
        /** Null if nothing was saved or it can't be read. */
        @Suppress("SENSELESS_COMPARISON")
        fun fromJson(json: String?): RestoredInfo? = json?.let {
            runCatching { Gson().fromJson(it, RestoredInfo::class.java) }.getOrNull()?.takeIf { info -> info.summary != null }
        }
    }
}

fun restoredInfo(d: BackupData) = RestoredInfo(restoreSummary(d), restoredIncome(d), d.categories.orEmpty().isNotEmpty())

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
