package com.example.expensetracker.ui.screens.expenses

import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense

/** A copy for "Duplicate": a new manual entry dated now. Captured SMS text is never copied. */
fun Expense.duplicateForNow(now: Long): Expense = copy(id = 0, date = now, source = "Manual", rawSms = null)

/** The add dialog's starting category: the last one used if it still exists, else pending (-1). */
fun defaultCategoryId(categories: List<Category>, lastUsedId: Long): Long =
    if (categories.any { it.id == lastUsedId }) lastUsedId else -1L
