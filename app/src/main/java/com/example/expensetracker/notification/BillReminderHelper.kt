package com.example.expensetracker.notification

import com.example.expensetracker.ui.util.fmtINR
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Bill
import com.example.expensetracker.data.period.BudgetPeriod
import com.example.expensetracker.ui.screens.dashboard.CashFlow
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

object BillReminderHelper {
    const val CHANNEL_ID = "bill_reminders"
    /** Own tag, so a capture notification for an expense with the same id doesn't replace a reminder. */
    const val TAG = "bill_reminder"
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

    /**
     * The bills inside their reminder window on [now]'s day, with the days until each is due. The due day is
     * clamped to the month's length, like the Overview and auto-log bills: a bill due on the 31st
     * is due on 30 Nov, and on 28 Feb.
     */
    fun dueReminders(bills: List<Bill>, now: Long): List<Pair<Bill, Int>> {
        val todayStart = BudgetPeriod.startOfDay(now)
        val within = Calendar.getInstance().apply { timeInMillis = todayStart; add(Calendar.DAY_OF_YEAR, 45) }.timeInMillis
        return bills.filter { it.isEnabled && !it.autoLog }.mapNotNull { bill ->
            val next = CashFlow.dueDatesInRange(bill.dueDayOfMonth, todayStart, within).first()
            val daysUntilDue = ((next - todayStart) / TimeUnit.DAYS.toMillis(1).toDouble()).roundToInt()
            if (daysUntilDue in 0..bill.reminderDays) bill to daysUntilDue else null
        }
    }

    /** The due date (start of its day) a bill's early reminder and its due-day reminder last went out for. */
    data class Sent(val earlyFor: Long = 0, val dueDayFor: Long = 0)

    /** A reminder to post now, and what to record once it is ([sent]). */
    data class Reminder(val bill: Bill, val daysUntilDue: Int, val sent: Sent)

    /**
     * The reminders to post on [now]'s day. A bill is reminded about at most twice for each due
     * date, not every day of its window: once when it first comes inside the window (its first
     * day, or the first day the daily check runs after that), and once on the day it's due.
     * [sent] is what has gone out for a bill so far.
     */
    fun remindersToSend(bills: List<Bill>, now: Long, sent: (Bill) -> Sent): List<Reminder> {
        val todayStart = BudgetPeriod.startOfDay(now)
        return dueReminders(bills, now).mapNotNull { (bill, daysUntilDue) ->
            val dueDate = Calendar.getInstance().apply { timeInMillis = todayStart; add(Calendar.DAY_OF_YEAR, daysUntilDue) }.timeInMillis
            val before = sent(bill)
            when {
                daysUntilDue == 0 -> if (before.dueDayFor == dueDate) null else Reminder(bill, 0, before.copy(dueDayFor = dueDate))
                before.earlyFor == dueDate -> null
                else -> Reminder(bill, daysUntilDue, before.copy(earlyFor = dueDate))
            }
        }
    }

    private const val PREFS = "bill_reminder_prefs"

    /** Reset everything, or a restore that replaces the bills: what was sent was about the old bills. */
    fun forgetReminders(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** Posts today's reminders ([remindersToSend]) and records them, so tomorrow's check doesn't repeat them. */
    fun remindNow(context: Context, bills: List<Bill>, now: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        remindersToSend(bills, now) { Sent(prefs.getLong("early_${it.id}", 0), prefs.getLong("due_${it.id}", 0)) }.forEach { reminder ->
            notify(context, reminder.bill, reminder.daysUntilDue)
            prefs.edit().putLong("early_${reminder.bill.id}", reminder.sent.earlyFor).putLong("due_${reminder.bill.id}", reminder.sent.dueDayFor).apply()
        }
    }

    fun notify(context: Context, bill: Bill, daysUntilDue: Int) {
        val dueText = when (daysUntilDue) {
            0 -> "due today"
            1 -> "due tomorrow"
            else -> "due in $daysUntilDue days"
        }
        // The day it's actually due (31 → 30 in November)
        val dueDay = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, daysUntilDue) }.get(Calendar.DAY_OF_MONTH)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("${bill.name} is $dueText")
            .setContentText("₹${fmtINR(bill.amount)} — day $dueDay of the month")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(TAG, (bill.id and 0x7FFFFFFF).toInt(), notification)
    }
}

class BillReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val bills = AppDatabase.getInstance(applicationContext).billDao().getAll().first()
            BillReminderHelper.remindNow(applicationContext, bills, System.currentTimeMillis())
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
