package com.example.expensetracker.notification

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.expensetracker.data.db.entity.Bill
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** BUG-13: each feature posts with its own tag, so equal ids from two features don't replace each other. */
@RunWith(AndroidJUnit4::class)
class NotificationTagsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val tags = setOf(NotificationHelper.TAG, BillReminderHelper.TAG)

    @Before fun setUp() {
        if (Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        NotificationHelper.createChannel(context)
        BillReminderHelper.createChannel(context)
    }

    @After fun tearDown() = ours().forEach { notifications.cancel(it.tag, it.id) }

    private fun ours() = notifications.activeNotifications.filter { it.tag in tags }

    @Test fun aCaptureDoesNotReplaceABillReminder() {
        BillReminderHelper.notify(context, Bill(id = 1, name = "Electricity", amount = 1800.0, dueDayOfMonth = 10), 1)
        // The reminder used id 10000 + bill id, a capture the expense id
        NotificationHelper.showCategorizationNotification(context, 10001, 149.0, "Zomato")
        NotificationHelper.showCategorizationNotification(context, 1, 99.0, "Chai Point")
        val until = SystemClock.uptimeMillis() + 5_000
        while (ours().size < 3 && SystemClock.uptimeMillis() < until) Thread.sleep(50)
        assertEquals(setOf(BillReminderHelper.TAG to 1, NotificationHelper.TAG to 10001, NotificationHelper.TAG to 1), ours().map { it.tag to it.id }.toSet())
    }
}
