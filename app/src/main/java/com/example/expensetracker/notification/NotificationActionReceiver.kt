package com.example.expensetracker.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.ui.widgets.WidgetUpdateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_CATEGORIZE = "com.example.expensetracker.ACTION_CATEGORIZE"
        const val EXTRA_EXPENSE_ID = "extra_expense_id"
        const val EXTRA_CATEGORY_ID = "extra_category_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CATEGORIZE) return

        val expenseId = intent.getLongExtra(EXTRA_EXPENSE_ID, -1L)
        val categoryId = intent.getLongExtra(EXTRA_CATEGORY_ID, -1L)

        if (expenseId == -1L || categoryId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val expense = db.expenseDao().getById(expenseId)
                if (expense != null) {
                    db.expenseDao().update(expense.copy(categoryId = categoryId))
                    
                    // Smart learning: update mapping
                    if (expense.source != "Manual") {
                        db.merchantMappingDao().insert(
                            com.example.expensetracker.data.db.entity.MerchantMapping(expense.description, categoryId)
                        )
                    }
                    
                    val prefs = PreferencesManager(context)
                    if (prefs.budgetAlertsEnabled) {
                        BudgetAlertHelper.checkAndNotify(
                            context, categoryId, 
                            com.example.expensetracker.data.repository.CategoryRepository(db.categoryDao(), db.expenseDao()),
                            com.example.expensetracker.data.repository.ExpenseRepository(db.expenseDao(), context.applicationContext)
                        )
                    }
                    WidgetUpdateHelper.update(context)
                }
                
                // Cancel the notification
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(expenseId.toInt())
                
            } finally {
                pendingResult.finish()
            }
        }
    }
}
