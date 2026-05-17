package com.example.expensetracker.data.recurring

import android.content.Context
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Expense
import java.util.Calendar

object RecurringManager {
    private const val PREFS = "expense_tracker_prefs"
    private const val KEY_LAST_APPLIED = "last_recurring_month"

    suspend fun applyIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cal = Calendar.getInstance()
        val currentMonth = "${cal.get(Calendar.YEAR)}_${cal.get(Calendar.MONTH)}"
        if (prefs.getString(KEY_LAST_APPLIED, "") == currentMonth) return

        val db = AppDatabase.getInstance(context)
        val templates = db.recurringTemplateDao().getAllEnabled()
        templates.forEach { template ->
            db.expenseDao().insert(
                Expense(
                    amount = template.amount,
                    description = template.name,
                    categoryId = template.categoryId,
                    source = "Recurring"
                )
            )
        }
        prefs.edit().putString(KEY_LAST_APPLIED, currentMonth).apply()
    }
}
