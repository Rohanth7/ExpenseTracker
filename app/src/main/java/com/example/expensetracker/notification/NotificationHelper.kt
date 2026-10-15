package com.example.expensetracker.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.expensetracker.MainActivity

object NotificationHelper {
    const val CHANNEL_ID = "expense_categorization"
    const val EXTRA_EXPENSE_ID = "expense_id"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Expense Categorization",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Prompts to categorize detected expenses"
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    fun showCategorizationNotification(
        context: Context,
        expenseId: Long,
        amount: Double,
        description: String,
        categories: List<com.example.expensetracker.data.db.entity.Category> = emptyList()
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_EXPENSE_ID, expenseId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, (expenseId and 0x7FFFFFFF).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New expense: ₹${"%.2f".format(amount)}")
            .setContentText(description)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$description\nTap to choose another category"))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        // Android shows at most three buttons, so they're all categories (a separate "Categorize"
        // button used to take one slot and hide the third category). Tapping the notification
        // opens the full category picker.
        categories.take(QuickCategories.MAX_BUTTONS).forEach { category ->
            val actionIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_CATEGORIZE
                putExtra(NotificationActionReceiver.EXTRA_EXPENSE_ID, expenseId)
                putExtra(NotificationActionReceiver.EXTRA_CATEGORY_ID, category.id)
            }
            val actionPendingIntent = PendingIntent.getBroadcast(
                context, ((expenseId * 100 + category.id) and 0x7FFFFFFF).toInt(), actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, category.name, actionPendingIntent)
        }

        context.getSystemService(NotificationManager::class.java)
            .notify((expenseId and 0x7FFFFFFF).toInt(), builder.build())
    }
}
