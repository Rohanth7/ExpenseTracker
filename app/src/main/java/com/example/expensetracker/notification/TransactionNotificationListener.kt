package com.example.expensetracker.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Expense
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
            // Skip if the same amount was already recorded in the last 2 minutes (SMS + UPI duplicate)
            val since = System.currentTimeMillis() - 2 * 60 * 1000L
            if (db.expenseDao().getRecentByAmount(transaction.amount, since) > 0) return@launch
            val expense = Expense(
                amount = transaction.amount,
                description = transaction.description,
                categoryId = -1L,
                source = "UPI"
            )
            val id = db.expenseDao().insert(expense)
            NotificationHelper.showCategorizationNotification(
                applicationContext, id, transaction.amount, transaction.description
            )
        }
    }
}
