package com.example.expensetracker.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import java.util.Calendar

object BudgetAlertHelper {
    const val CHANNEL_ID = "budget_alerts"
    private const val PREFS = "budget_alert_prefs"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Budget Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notifies when a category approaches or exceeds its monthly limit" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    suspend fun checkAndNotify(
        context: Context,
        categoryId: Long,
        categoryRepo: CategoryRepository,
        expenseRepo: ExpenseRepository
    ) {
        val category = categoryRepo.getById(categoryId) ?: return
        if (category.monthlyLimit <= 0) return

        val cal = Calendar.getInstance()
        val start = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val spent = expenseRepo.getSpentForCategory(categoryId, start, end)
        val pct = spent / category.monthlyLimit
        val monthKey = "${cal.get(Calendar.YEAR)}_${cal.get(Calendar.MONTH)}_$categoryId"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        when {
            pct >= 1.0 && !prefs.getBoolean("${monthKey}_over", false) -> {
                // Mark both 80% and 100% so the 80% warning never fires after the 100% one
                prefs.edit()
                    .putBoolean("${monthKey}_over", true)
                    .putBoolean("${monthKey}_80", true)
                    .apply()
                sendNotification(
                    context,
                    id = 100_000 + categoryId.toInt(),
                    title = "${category.emoji} ${category.name} limit exceeded!",
                    text = "Spent ₹${"%.0f".format(spent)} of ₹${"%.0f".format(category.monthlyLimit)} monthly limit"
                )
            }
            pct >= 0.8 && !prefs.getBoolean("${monthKey}_80", false) -> {
                prefs.edit().putBoolean("${monthKey}_80", true).apply()
                sendNotification(
                    context,
                    id = 200_000 + categoryId.toInt(),
                    title = "${category.emoji} ${category.name} at ${"%.0f".format(pct * 100)}%",
                    text = "Spent ₹${"%.0f".format(spent)} of ₹${"%.0f".format(category.monthlyLimit)} monthly limit"
                )
            }
        }
    }

    private fun sendNotification(context: Context, id: Int, title: String, text: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }
}
