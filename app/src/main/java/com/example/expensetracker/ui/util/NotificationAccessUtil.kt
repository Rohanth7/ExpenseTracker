package com.example.expensetracker.ui.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.example.expensetracker.notification.TransactionNotificationListener

object NotificationAccessUtil {
    fun isGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Opens this app's notification-access switch directly where Android supports it. */
    fun openSettings(context: Context) {
        val component = ComponentName(context, TransactionNotificationListener::class.java)
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { context.startActivity(detail) }.onFailure { context.startActivity(list) }
        } else {
            context.startActivity(list)
        }
    }
}
