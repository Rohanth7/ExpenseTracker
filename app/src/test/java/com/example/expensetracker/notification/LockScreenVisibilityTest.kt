package com.example.expensetracker.notification

import androidx.core.app.NotificationCompat
import org.junit.Assert.assertEquals
import org.junit.Test

class LockScreenVisibilityTest {
    @Test fun appLockOrPrivacyModeKeepsNotificationsOffTheLockScreen() {
        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, LockScreenPrivacy.visibility(appLock = false, privacyMode = false))
        assertEquals(NotificationCompat.VISIBILITY_SECRET, LockScreenPrivacy.visibility(appLock = true, privacyMode = false))
        assertEquals(NotificationCompat.VISIBILITY_SECRET, LockScreenPrivacy.visibility(appLock = false, privacyMode = true))
        assertEquals(NotificationCompat.VISIBILITY_SECRET, LockScreenPrivacy.visibility(appLock = true, privacyMode = true))
    }
}
