package com.example.expensetracker.notification

import com.example.expensetracker.ui.util.fmtINR
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.example.expensetracker.data.period.BudgetPeriod
import com.example.expensetracker.data.period.MonthStartDay
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import java.util.Calendar

object BudgetAlertHelper {
    const val CHANNEL_ID = "budget_alerts"
    private const val PREFS = "budget_alert_prefs"
    /** Own tag, like the other features' notifications; the ids below only need to differ within it. */
    const val TAG = "budget_alert"

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

    /** Reset everything: which alerts were sent this month, so a new budget alerts again. */
    fun forgetAlerts(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    /**
     * After a payment is captured or categorized from its notification: the same check as an
     * expense added by hand, if it has a category (a learned payee). Never fails the capture.
     */
    suspend fun checkAfterCapture(context: Context, db: AppDatabase, categoryId: Long) {
        if (categoryId == -1L) return
        val prefs = PreferencesManager(context)
        if (!prefs.budgetAlertsEnabled) return
        runCatching {
            checkAndNotify(context, categoryId, CategoryRepository(db.categoryDao(), db.expenseDao()),
                ExpenseRepository(db.expenseDao(), context.applicationContext), prefs.budgetAlertThreshold)
        }
    }

    suspend fun checkAndNotify(
        context: Context,
        categoryId: Long,
        categoryRepo: CategoryRepository,
        expenseRepo: ExpenseRepository,
        alertThreshold: Int = 80
    ) {
        val category = categoryRepo.getById(categoryId) ?: return

        // If this is a subcategory, check the parent's budget instead
        val budgetCategory = if (category.parentId != null) {
            categoryRepo.getById(category.parentId) ?: category
        } else category

        if (budgetCategory.monthlyLimit <= 0) return

        // The budget month follows the user's start day (see BudgetPeriod).
        val startDay = MonthStartDay.get(context)
        val cycle = BudgetPeriod.current(System.currentTimeMillis(), startDay)
        val start = cycle.start
        val end = cycle.endInclusive

        // Sum spend across parent + all its subcategories
        val allCategories = categoryRepo.getAll()
        val relatedIds = allCategories
            .filter { it.id == budgetCategory.id || it.parentId == budgetCategory.id }
            .map { it.id }
        val spent = expenseRepo.getSpentForCategories(relatedIds, start, end)

        val pct = spent / budgetCategory.monthlyLimit
        // One alert per budget month. The limit is part of the key so editing it re-arms the alerts.
        // Calendar months keep the old key, so alerts already sent this month aren't repeated.
        val startCal = Calendar.getInstance().apply { timeInMillis = start }
        val cycleKey = "${startCal.get(Calendar.YEAR)}_${startCal.get(Calendar.MONTH)}" + if (startDay == 1) "" else "_d$startDay"
        val monthKey = "${cycleKey}_${budgetCategory.id}_${budgetCategory.monthlyLimit}"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val warningRatio = alertThreshold / 100.0
        val warningKey = "${monthKey}_warn"
        when {
            pct >= 1.0 && !prefs.getBoolean("${monthKey}_over", false) -> {
                prefs.edit()
                    .putBoolean("${monthKey}_over", true)
                    .putBoolean(warningKey, true)
                    .apply()
                sendNotification(
                    context,
                    id = 100_000 + budgetCategory.id.toInt(),
                    title = "${budgetCategory.emoji} ${budgetCategory.name} limit exceeded!",
                    text = "Spent ₹${fmtINR(spent)} of ₹${fmtINR(budgetCategory.monthlyLimit)} monthly limit"
                )
            }
            pct >= warningRatio && !prefs.getBoolean(warningKey, false) -> {
                prefs.edit().putBoolean(warningKey, true).apply()
                sendNotification(
                    context,
                    id = 200_000 + budgetCategory.id.toInt(),
                    title = "${budgetCategory.emoji} ${budgetCategory.name} at ${"%.0f".format(pct * 100)}%",
                    text = "Spent ₹${fmtINR(spent)} of ₹${fmtINR(budgetCategory.monthlyLimit)} monthly limit"
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
        context.getSystemService(NotificationManager::class.java).notify(TAG, id, notification)
    }
}
