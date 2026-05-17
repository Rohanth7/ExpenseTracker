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
        description: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_EXPENSE_ID, expenseId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, expenseId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New expense: ₹${"%.2f".format(amount)}")
            .setContentText(description)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_add, "Categorize", pendingIntent)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(expenseId.toInt(), notification)
    }
}
