package com.example.expensetracker.data.db.entity

import androidx.room.Entity

/**
 * A month RecurringManager has handled for a recurring income: it logged an entry, or found one
 * entered by hand ([byHand]). Kept apart from the entry, so deleting or moving the entry doesn't
 * make the month log again, and restored with the data it describes.
 */
@Entity(tableName = "recurring_income_months", primaryKeys = ["recurringIncomeId", "year", "month"])
data class RecurringIncomeMonth(
    val recurringIncomeId: Long,
    val year: Int,
    /** 1–12. */
    val month: Int,
    val byHand: Boolean = false
)
