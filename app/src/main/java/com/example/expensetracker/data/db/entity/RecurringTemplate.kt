package com.example.expensetracker.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recurring_templates")
data class RecurringTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Double,
    val categoryId: Long = -1L,
    val enabled: Boolean = true
)
