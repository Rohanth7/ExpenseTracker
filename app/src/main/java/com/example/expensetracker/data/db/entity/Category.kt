package com.example.expensetracker.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "📦",
    val colorHex: String = "#6200EE",
    val monthlyLimit: Double = 0.0
)
