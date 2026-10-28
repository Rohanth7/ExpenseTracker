package com.example.expensetracker.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Income that comes every month, e.g. a salary, logged on its day like an auto-log bill. */
@Entity(tableName = "recurring_incomes")
data class RecurringIncome(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Double,
    /** Calendar day 1–31; 31 means the last day of a shorter month. */
    val dayOfMonth: Int,
    val isEnabled: Boolean = true
)
