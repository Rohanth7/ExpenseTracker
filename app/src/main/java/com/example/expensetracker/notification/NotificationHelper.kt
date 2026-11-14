package com.example.expensetracker.notification

import com.example.expensetracker.ui.util.fmtINR
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.expensetracker.MainActivity

object NotificationHelper {
    const val CHANNEL_ID = "expense_categorization"
    const val EXTRA_EXPENSE_ID = "expense_id"
    /** Each feature posts with its own tag, so ids from different features can't replace each other (as in PriceChangeAlerts). */
    const val TAG = "capture"

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

    /** Opens Categorize for [expenseId]. One per expense: the request code is the masked id. */
    internal fun categorizeIntent(context: Context, expenseId: Long): PendingIntent = PendingIntent.getActivity(
        context, (expenseId and 0x7FFFFFFF).toInt(),
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_EXPENSE_ID, expenseId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun showCategorizationNotification(
        context: Context,
        expenseId: Long,
        amount: Double,
        description: String,
        categories: List<com.example.expensetracker.data.db.entity.Category> = emptyList()
    ) {
        val pendingIntent = categorizeIntent(context, expenseId)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New expense: ₹${fmtINR(amount)}")
            .setContentText(description)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$description\nTap to choose another category"))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        // Android shows at most three buttons, so they're all categories (a separate "Categorize"
        // button used to take one slot and hide the third category). Tapping the notification
        // opens the full category picker.
        categories.take(QuickCategories.MAX_BUTTONS).forEach { category ->
            builder.addAction(0, category.name, categoryActionIntent(context, expenseId, category.id))
        }

        context.getSystemService(NotificationManager::class.java)
            .notify(TAG, (expenseId and 0x7FFFFFFF).toInt(), builder.build())
    }

    /**
     * A category button. The data Uri names the expense and the category, so no two buttons are
     * the same PendingIntent. A request code built from both (expense × 100 + category) collided:
     * expense 1 + category 101 = expense 2 + category 1, and the newer button replaced the older.
     */
    internal fun categoryActionIntent(context: Context, expenseId: Long, categoryId: Long): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_CATEGORIZE
            data = Uri.parse("thriftpot://categorize/$expenseId/$categoryId")
            putExtra(NotificationActionReceiver.EXTRA_EXPENSE_ID, expenseId)
            putExtra(NotificationActionReceiver.EXTRA_CATEGORY_ID, categoryId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
