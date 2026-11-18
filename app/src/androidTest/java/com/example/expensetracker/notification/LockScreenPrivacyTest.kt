package com.example.expensetracker.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.expensetracker.data.db.entity.Bill
import com.example.expensetracker.data.preferences.PreferencesManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** QA B-25: a capture notification showed the amount and payee on a locked phone, even with App Lock on. */
@RunWith(AndroidJUnit4::class)
class LockScreenPrivacyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val tags = setOf(NotificationHelper.TAG, BillReminderHelper.TAG)

    @Before fun setUp() {
        if (Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        NotificationHelper.createChannel(context)
        BillReminderHelper.createChannel(context)
        saved = prefs.biometricLockEnabled to prefs.privacyMode
        prefs.biometricLockEnabled = false
        prefs.privacyMode = false
    }

    @After fun tearDown() {
        ours().forEach { notifications.cancel(it.tag, it.id) }
        prefs.biometricLockEnabled = saved.first
        prefs.privacyMode = saved.second
    }

    private val prefs = PreferencesManager(context)
    private var saved = false to false

    private fun ours() = notifications.activeNotifications.filter { it.tag in tags }

    private fun posted(tag: String): Notification {
        val until = SystemClock.uptimeMillis() + 5_000
        while (ours().none { it.tag == tag } && SystemClock.uptimeMillis() < until) Thread.sleep(50)
        return ours().first { it.tag == tag }.notification
    }

    private fun assertRedacted(n: Notification, payee: String, visibility: Int = Notification.VISIBILITY_PRIVATE) {
        assertEquals(visibility, n.visibility)
        val public = n.publicVersion
        assertNotNull(public)
        val shown = listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT)
            .joinToString(" ") { public!!.extras.getCharSequence(it)?.toString().orEmpty() }
        assertFalse(shown, "₹" in shown)
        assertFalse(shown, payee in shown)
        assertEquals(0, public!!.actions?.size ?: 0)
    }

    @Test fun aCaptureHidesItsAmountAndPayee() {
        NotificationHelper.showCategorizationNotification(context, 4242, 349.0, "Zomato")
        assertRedacted(posted(NotificationHelper.TAG), "Zomato")
    }

    @Test fun aBillReminderHidesItsAmountAndName() {
        BillReminderHelper.notify(context, Bill(id = 4242, name = "Electricity", amount = 1800.0, dueDayOfMonth = 10), 1)
        assertRedacted(posted(BillReminderHelper.TAG), "Electricity")
    }

    @Test fun withAppLockOnACaptureStaysOffTheLockScreen() {
        prefs.biometricLockEnabled = true
        NotificationHelper.showCategorizationNotification(context, 4243, 349.0, "Zomato")
        assertRedacted(posted(NotificationHelper.TAG), "Zomato", Notification.VISIBILITY_SECRET)
    }

    @Test fun withPrivacyModeOnABillReminderStaysOffTheLockScreen() {
        prefs.privacyMode = true
        BillReminderHelper.notify(context, Bill(id = 4243, name = "Electricity", amount = 1800.0, dueDayOfMonth = 10), 1)
        assertRedacted(posted(BillReminderHelper.TAG), "Electricity", Notification.VISIBILITY_SECRET)
    }
}
