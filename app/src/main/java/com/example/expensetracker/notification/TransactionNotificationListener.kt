package com.example.expensetracker.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.dao.MappingHelper
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.sms.CaptureDuplicates
import com.example.expensetracker.ui.widgets.WidgetUpdateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
            // With no categories yet the expense is still saved, as pending (categoryId -1).
            if (CaptureDuplicates.isDuplicate(db.expenseDao(), transaction.amount, "UPI", body, System.currentTimeMillis())) return@launch

            // Smart auto-categorization with fuzzy matching
            val mappedCategoryId = MappingHelper.getCategoryId(db, transaction.description)
            val finalCategoryId = mappedCategoryId ?: -1L

            val expense = Expense(
                amount = transaction.amount,
                description = transaction.description,
                categoryId = finalCategoryId,
                source = "UPI",
                rawSms = body,
                paymentMethod = "UPI"
            )
            val id = db.expenseDao().insert(expense)
            NotificationHelper.showCategorizationNotification(
                applicationContext, id, transaction.amount, transaction.description,
                QuickCategories.forNewExpense(db, mappedCategoryId)
            )
            WidgetUpdateHelper.update(applicationContext)
            // The expense is saved; a problem in price-change detection mustn't affect it.
            runCatching { PriceChangeAlerts.checkAfterCapture(applicationContext, db, expense.copy(id = id)) }
        }
    }
}
