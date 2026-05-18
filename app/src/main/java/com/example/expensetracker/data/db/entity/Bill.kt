package com.example.expensetracker.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Double,
    val dueDayOfMonth: Int,
    val reminderDays: Int = 3,
    val categoryId: Long = -1L,
    val isEnabled: Boolean = true
)
