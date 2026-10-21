package com.example.expensetracker.ui.screens.expenses

import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense

/** A copy for "Duplicate": a new manual entry dated now. Captured SMS text is never copied. */
fun Expense.duplicateForNow(now: Long): Expense = copy(id = 0, date = now, source = "Manual", rawSms = null)

/** The add dialog's starting category: the last one used if it still exists, else pending (-1). */
fun defaultCategoryId(categories: List<Category>, lastUsedId: Long): Long =
    if (categories.any { it.id == lastUsedId }) lastUsedId else -1L

/**
 * The category the add dialog shows: the user's own pick ([userChoice], -1 = "Choose later"), or
 * until they pick, the default from [defaultCategoryId]. Recomputed as categories load, because the
 * widget's "+" can open the dialog before they have.
 */
fun startingCategoryId(userChoice: Long?, categories: List<Category>, lastUsedId: Long): Long =
    userChoice ?: defaultCategoryId(categories, lastUsedId)
