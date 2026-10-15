package com.example.expensetracker.data.defaults

import androidx.room.withTransaction
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Category

/** Starter categories for a new install, so captured payments have somewhere to go. */
object DefaultCategories {
    val STARTER: List<Category> = listOf(
        Category(name = "Food & Dining",     emoji = "🍔", colorHex = "#E53935"),
        Category(name = "Groceries",         emoji = "🛒", colorHex = "#43A047"),
        Category(name = "Transport",         emoji = "🚗", colorHex = "#00897B"),
        Category(name = "Shopping",          emoji = "🛍️", colorHex = "#1E88E5"),
        Category(name = "Bills & Utilities", emoji = "⚡", colorHex = "#6D4C41"),
        Category(name = "Rent",              emoji = "🏠", colorHex = "#3949AB"),
        Category(name = "Health",            emoji = "💊", colorHex = "#F4511E"),
        Category(name = "Entertainment",     emoji = "🎬", colorHex = "#8E24AA"),
        Category(name = "Travel",            emoji = "✈️", colorHex = "#0288D1"),
        Category(name = "Personal Care",     emoji = "💇", colorHex = "#D81B60")
    )

    /** Inserts the starter set only if there are no categories at all. Returns true if it did. */
    suspend fun seedIfEmpty(db: AppDatabase): Boolean = db.withTransaction {
        if (db.categoryDao().getCount() > 0) return@withTransaction false
        db.categoryDao().insertAll(STARTER)
        true
    }
}
