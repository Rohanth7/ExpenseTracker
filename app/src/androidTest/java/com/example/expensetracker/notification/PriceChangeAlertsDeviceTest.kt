package com.example.expensetracker.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.preferences.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/**
 * checkAfterCapture against a throwaway database. It reads the app's own settings and alert keys,
 * so it uses a payee nobody has and puts the setting back afterwards.
 */
@RunWith(AndroidJUnit4::class)
class PriceChangeAlertsDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val prefs = PreferencesManager(context)
    private val payee = "Price Alert Test Payee"
    private lateinit var db: AppDatabase
    private var wasEnabled = true

    @Before fun setUp() {
        if (Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        wasEnabled = prefs.priceAlertsEnabled
        prefs.priceAlertsEnabled = true
        PriceChangeAlerts.createChannel(context)
        forgetAlerts()
    }

    @After fun tearDown() {
        prefs.priceAlertsEnabled = wasEnabled
        forgetAlerts()
        ours().forEach { notifications.cancel(it.tag, it.id) }
        db.close()
    }

    private fun forgetAlerts() {
        val alerts = context.getSharedPreferences("price_alert_prefs", Context.MODE_PRIVATE)
        alerts.edit().apply { alerts.all.keys.filter { "price alert test payee" in it }.forEach { remove(it) } }.commit()
    }

    private fun ours() = notifications.activeNotifications.filter {
        it.tag == "price_change" && it.notification.extras.getString(Notification.EXTRA_TITLE)?.startsWith(payee) == true
    }

    /** Posting reaches the notification list a moment after notify(): wait for [count] of ours, up to 5 s. */
    private fun awaitOurs(count: Int): List<StatusBarNotification> {
        val until = SystemClock.uptimeMillis() + 5_000
        while (ours().size < count && SystemClock.uptimeMillis() < until) Thread.sleep(50)
        return ours()
    }

    private fun monthsAgo(n: Int) = Calendar.getInstance().apply { add(Calendar.MONTH, -n) }.timeInMillis

    /** Four months at ₹499, then today's charge at ₹549 seen by both the bank SMS and the payment app. */
    private suspend fun seedRise(): List<Expense> {
        (4 downTo 1).forEach { db.expenseDao().insert(Expense(amount = 499.0, description = payee, date = monthsAgo(it), source = "SMS")) }
        val now = System.currentTimeMillis()
        return listOf(Expense(amount = 549.0, description = payee, date = now, source = "SMS"),
            Expense(amount = 549.0, description = "GPay → $payee", date = now, source = "UPI"))
            .map { it.copy(id = db.expenseDao().insert(it)) }
    }

    @Test fun bothCapturesOfOneChargeAlertOnce() = runBlocking {
        val captures = seedRise()
        (1..4).flatMap { captures }.map { async(Dispatchers.Default) { PriceChangeAlerts.checkAfterCapture(context, db, it) } }.awaitAll()
        val posted = awaitOurs(1)
        assertEquals(1, posted.size)
        assertEquals("₹499 → ₹549 a month · ₹600 more a year", posted.single().notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(PriceChangeAlerts.CHANNEL_ID, posted.single().notification.channelId)
    }

    @Test fun turnedOffItDoesNotAlert() = runBlocking {
        prefs.priceAlertsEnabled = false
        val capture = seedRise().first()
        PriceChangeAlerts.checkAfterCapture(context, db, capture)
        assertEquals(0, ours().size)
        // Turned on again, the same change still alerts: nothing was stored while it was off
        prefs.priceAlertsEnabled = true
        PriceChangeAlerts.checkAfterCapture(context, db, capture)
        assertEquals(1, awaitOurs(1).size)
    }
}
