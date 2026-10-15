package com.example.expensetracker.notification

import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Category

/** Which categories get a one-tap button on the "new expense" notification. */
object QuickCategories {
    /** Android shows at most three action buttons on a notification. */
    const val MAX_BUTTONS = 3
    private const val USAGE_WINDOW_MS = 60L * 24 * 60 * 60 * 1000

    /**
     * The auto-detected category first, then the ones used most recently-ish, then alphabetical.
     * Ids that no longer exist (a deleted category) are ignored.
     */
    fun pick(categories: List<Category>, usage: Map<Long, Int>, suggestedId: Long?, count: Int = MAX_BUTTONS): List<Category> {
        val suggested = categories.firstOrNull { it.id == suggestedId }
        val rest = categories.filter { it.id != suggested?.id }
            .sortedWith(compareByDescending<Category> { usage[it.id] ?: 0 }.thenBy { it.name.lowercase() })
        return (listOfNotNull(suggested) + rest).take(count)
    }

    /** [pick] using the last 60 days of expenses. */
    suspend fun forNewExpense(db: AppDatabase, suggestedId: Long?): List<Category> {
        val usage = db.expenseDao().categoryUsageSince(System.currentTimeMillis() - USAGE_WINDOW_MS)
            .associate { it.categoryId to it.uses }
        return pick(db.categoryDao().getAllOnce(), usage, suggestedId)
    }
}
