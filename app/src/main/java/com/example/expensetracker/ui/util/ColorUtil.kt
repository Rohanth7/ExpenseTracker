package com.example.expensetracker.ui.util

import androidx.compose.ui.graphics.Color

fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    Color.Gray
}
