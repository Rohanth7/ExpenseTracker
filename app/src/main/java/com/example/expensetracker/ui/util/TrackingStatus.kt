package com.example.expensetracker.ui.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Whether each auto-tracking source is switched on. */
data class TrackingStatus(val sms: Boolean, val notifications: Boolean) {
    companion object {
        fun read(context: Context) = TrackingStatus(
            sms = SmsPermissionUtil.isGranted(context),
            notifications = NotificationAccessUtil.isGranted(context)
        )
    }
}

/**
 * Current [TrackingStatus], re-read every time the screen resumes, so it updates after the user
 * comes back from Android's settings. Call [refresh] after an in-app permission prompt.
 */
class TrackingStatusHolder(private val context: Context) {
    var status by mutableStateOf(TrackingStatus.read(context))
        private set
    fun refresh() { status = TrackingStatus.read(context) }
}

@Composable
fun rememberTrackingStatus(): TrackingStatusHolder {
    val context = LocalContext.current
    val holder = remember { TrackingStatusHolder(context.applicationContext) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { holder.refresh() }
    return holder
}
