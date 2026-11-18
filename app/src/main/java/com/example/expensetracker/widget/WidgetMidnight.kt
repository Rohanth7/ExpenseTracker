package com.example.expensetracker.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Wakes the widget at the next local midnight, so days left and her face move on when nothing is
 * logged. Android doesn't deliver ACTION_DATE_CHANGED to a manifest receiver in the background.
 * Inexact and not a wakeup: it needs no permission, and arrives once the phone is next awake.
 */
internal object WidgetMidnight {
    const val ACTION = "com.thriftpot.app.action.WIDGET_NEW_DAY"

    /** Replaces any earlier one. Each refresh calls it, so the next day's is always set. */
    fun schedule(context: Context, now: Long = System.currentTimeMillis()) {
        context.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, nextMidnight(now), intent(context))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(intent(context))
    }

    fun nextMidnight(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun intent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, BudgetPulseWidgetReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
