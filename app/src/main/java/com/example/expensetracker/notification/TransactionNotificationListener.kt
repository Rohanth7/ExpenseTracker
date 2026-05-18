package com.example.expensetracker.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.dao.MappingHelper
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.ui.widgets.WidgetUpdateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TransactionNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return
        if (!NotificationParser.isMonitored(packageName)) return
        // Don't process our own notifications
        if (packageName == applicationContext.packageName) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (title.isBlank() && body.isBlank()) return

        val transaction = NotificationParser.parse(title, body, packageName) ?: return

        serviceScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            if (db.categoryDao().getCount() == 0) return@launch // Avoid adding without category

            val categories = db.categoryDao().getAll().first()
            val now = System.currentTimeMillis()
            // Cross-source: bank SMS + UPI notification arrive within seconds for the same transaction.
            // Descriptions differ between sources, so match on amount only within 3 minutes.
            if (db.expenseDao().getRecentByAmountFromDifferentSource(transaction.amount, "UPI", now - 3 * 60 * 1000L) > 0) return@launch
            
            // Same-source: UPI app notification fired twice (30-sec window)
            if (db.expenseDao().getRecentByAmountAndContentFromSameSource(transaction.amount, "UPI", body, now - 30 * 1000L) > 0) return@launch
            
            // Smart auto-categorization with fuzzy matching
            val mappedCategoryId = MappingHelper.getCategoryId(db, transaction.description)
            val finalCategoryId = mappedCategoryId ?: -1L

            val expense = Expense(
                amount = transaction.amount,
                description = transaction.description,
                categoryId = finalCategoryId,
                source = "UPI",
                rawSms = body
            )
            val id = db.expenseDao().insert(expense)
            NotificationHelper.showCategorizationNotification(
                applicationContext, id, transaction.amount, transaction.description, categories
            )
            WidgetUpdateHelper.update(applicationContext)
        }
    }
}
