package com.example.expensetracker.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Money that came in. Kept apart from [Expense] on purpose: every existing sum, budget and alert
 * means spending, and stays that way.
 */
@Entity(tableName = "incomes")
data class Income(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val description: String,
    val date: Long = System.currentTimeMillis(),
    /** "Manual" or "Recurring" (logged by RecurringManager). */
    val source: String = "Manual",
    val tags: String = "",
    /** The [RecurringIncome] that logged it, if any. */
    val recurringIncomeId: Long? = null,
    /** Reserved for the accounts feature; always null for now. */
    val accountId: Long? = null
)
