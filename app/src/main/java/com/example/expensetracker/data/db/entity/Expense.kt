package com.example.expensetracker.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
    indices = [Index("categoryId")]
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val description: String,
    val categoryId: Long = -1L,
    val date: Long = System.currentTimeMillis(),
    val source: String = "Manual",
    val rawSms: String? = null,
    val tags: String = ""
)
