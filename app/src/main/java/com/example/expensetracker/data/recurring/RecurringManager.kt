package com.example.expensetracker.data.recurring

import android.content.Context
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Expense
import java.util.Calendar

object RecurringManager {
    private const val PREFS = "expense_tracker_prefs"

    suspend fun applyIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cal = Calendar.getInstance()
        val currentMonth = "${cal.get(Calendar.YEAR)}_${cal.get(Calendar.MONTH)}"

        val db = AppDatabase.getInstance(context)
        val bills = db.billDao().getAutoLogEnabled()
        val firstOfMonth = cal.apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        bills.forEach { bill ->
            val key = "bill_applied_${currentMonth}_${bill.id}"
            if (prefs.getBoolean(key, false)) return@forEach

            db.expenseDao().insert(
                Expense(
                    amount = bill.amount,
                    description = bill.name,
                    categoryId = bill.categoryId,
                    source = "Recurring",
                    date = firstOfMonth
                )
            )
            prefs.edit().putBoolean(key, true).commit()
        }
    }
}
