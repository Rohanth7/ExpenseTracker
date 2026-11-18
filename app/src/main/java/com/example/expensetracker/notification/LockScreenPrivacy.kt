package com.example.expensetracker.notification

import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.expensetracker.data.preferences.PreferencesManager

/**
 * Every notification the app posts names an amount, and most a payee or bill (QA B-25).
 *
 * Each one carries a public version that only says what kind of alert it is. Android shows that
 * version on a locked phone when the phone is set to hide sensitive notification content. With
 * "show all content", which is many phones' default, Android shows the full notification, and an
 * app can't overrule that: Android ignores an app's own lock screen visibility for its channels.
 *
 * Someone who turned on App Lock or privacy mode asked for the app's data to stay hidden, so
 * their notifications are left off the lock screen altogether (Android honours that for every
 * lock screen with a PIN, pattern or password). Once the phone is unlocked they show in full.
 */
object LockScreenPrivacy {

    /** [NotificationCompat.VISIBILITY_SECRET] with App Lock or privacy mode on, otherwise [NotificationCompat.VISIBILITY_PRIVATE]. */
    fun visibility(appLock: Boolean, privacyMode: Boolean): Int =
        if (appLock || privacyMode) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PRIVATE

    /** Shown in place of the notification on a locked phone: no amount, no payee, no bill name. */
    fun NotificationCompat.Builder.privateOnLockScreen(
        context: Context,
        channelId: String,
        smallIcon: Int,
        title: String,
        text: String,
    ): NotificationCompat.Builder {
        val prefs = PreferencesManager(context)
        return setVisibility(visibility(prefs.biometricLockEnabled, prefs.privacyMode)).setPublicVersion(
            NotificationCompat.Builder(context, channelId)
                .setSmallIcon(smallIcon)
                .setContentTitle(title)
                .setContentText(text)
                .build()
        )
    }
}
