package com.example.expensetracker.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "loans")
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "🏦",
    val totalAmount: Double,
    val monthlyEmi: Double,
    val tenureMonths: Int,
    val startDate: Long,        // epoch ms for the first EMI month
    val dueDayOfMonth: Int = 5,
    val isActive: Boolean = true
)
