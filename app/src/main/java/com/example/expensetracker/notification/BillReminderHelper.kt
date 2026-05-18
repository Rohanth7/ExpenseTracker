package com.example.expensetracker.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Bill
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

object BillReminderHelper {
    const val CHANNEL_ID = "bill_reminders"
    private const val WORK_NAME = "bill_reminder_daily"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bill Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Reminds you when a bill is due soon" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun schedule(context: Context) {
        createChannel(context)
        val request = PeriodicWorkRequestBuilder<BillReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun notify(context: Context, bill: Bill, daysUntilDue: Int) {
        val dueText = when (daysUntilDue) {
            0 -> "due today"
            1 -> "due tomorrow"
            else -> "due in $daysUntilDue days"
        }
        val fmt = java.text.NumberFormat.getNumberInstance(java.util.Locale.forLanguageTag("en-IN"))
            .apply { maximumFractionDigits = 0 }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("${bill.name} is $dueText")
            .setContentText("₹${fmt.format(bill.amount)} — day ${bill.dueDayOfMonth} of the month")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(10000 + bill.id.toInt(), notification)
    }
}

class BillReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getInstance(applicationContext)
            val bills = db.billDao().getAll().first()
            val cal = Calendar.getInstance()
            val today = cal.get(Calendar.DAY_OF_MONTH)
            val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

            bills.filter { it.isEnabled && !it.autoLog }.forEach { bill ->
                val diff = bill.dueDayOfMonth - today
                // Wrap negative diffs: bill due early next month (e.g. due on 2nd, today is 28th)
                val daysUntilDue = if (diff < 0) diff + daysInMonth else diff
                if (daysUntilDue in 0..bill.reminderDays) {
                    BillReminderHelper.notify(applicationContext, bill, daysUntilDue)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
