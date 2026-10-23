package com.example.expensetracker.ui.screens.expenses

import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.ui.util.AmountCalculator

/** A copy for "Duplicate": a new manual entry dated now. Captured SMS text is never copied. */
fun Expense.duplicateForNow(now: Long): Expense = copy(id = 0, date = now, source = "Manual", rawSms = null)

/** "Duplicate" from the edit dialog: a copy of what the form shows now, including unsaved changes. */
fun Expense.duplicateWithEdits(
    amount: Double, description: String, categoryId: Long, tags: String, paymentMethod: String, now: Long
): Expense = copy(amount = amount, description = description, categoryId = categoryId, tags = tags, paymentMethod = paymentMethod)
    .duplicateForNow(now)

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

/**
 * True when the edit form differs from this saved expense (so Duplicate also saves it). Takes the
 * form as typed, untrimmed: compared with trimmed or rounded values, an untouched expense with a
 * padded description or a third decimal looked edited and got rewritten. The amount counts as
 * changed only if its text was changed to a different amount ("180" for 180.00 is not).
 */
fun Expense.isEditedTo(amountText: String, description: String, categoryId: Long, tags: String, paymentMethod: String, date: Long): Boolean =
    (amountText != AmountCalculator.forEditing(amount) && AmountCalculator.evaluate(amountText) != amount) ||
        description != this.description || categoryId != this.categoryId ||
        tags != this.tags || paymentMethod != this.paymentMethod || date != this.date

/**
 * Whether the add dialog's category becomes the next default. "Choose later" counts only once the
 * categories had loaded; before that the dialog showed pending because it had nothing else.
 */
fun shouldRememberCategory(categoryId: Long, categoriesLoaded: Boolean): Boolean = categoryId != -1L || categoriesLoaded
