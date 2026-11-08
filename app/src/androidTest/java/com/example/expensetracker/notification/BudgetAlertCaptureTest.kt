package com.example.expensetracker.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.db.entity.MerchantMapping
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.sms.ParsedTransaction
import com.example.expensetracker.sms.SmsReceiver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BUG-05: an auto-categorized capture took a category over its limit with no alert. Throwaway
 * in-memory database; it reads the app's alert setting and keys, so it uses a category id
 * nobody has and puts both back afterwards.
 */
@RunWith(AndroidJUnit4::class)
class BudgetAlertCaptureTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val prefs = PreferencesManager(context)
    private val categoryId = 987_654L
    private lateinit var db: AppDatabase
    private var wasEnabled = true

    @Before fun setUp() {
        if (Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        BudgetAlertHelper.createChannel(context)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        wasEnabled = prefs.budgetAlertsEnabled
        prefs.budgetAlertsEnabled = true
        forgetAlerts()
        runBlocking {
            db.categoryDao().insert(Category(id = categoryId, name = "Food & Dining", emoji = "🍔", monthlyLimit = 1000.0))
            db.merchantMappingDao().insert(MerchantMapping("SWIGGY", categoryId))
            db.expenseDao().insert(Expense(amount = 900.0, description = "Team lunch", categoryId = categoryId))
        }
    }

    @After fun tearDown() {
        prefs.budgetAlertsEnabled = wasEnabled
        forgetAlerts()
        notifications.activeNotifications.filter { it.tag == BudgetAlertHelper.TAG || it.tag == NotificationHelper.TAG }.forEach { notifications.cancel(it.tag, it.id) }
        db.close()
    }

    private fun forgetAlerts() {
        val alerts = context.getSharedPreferences("budget_alert_prefs", Context.MODE_PRIVATE)
        alerts.edit().apply { alerts.all.keys.filter { "_${categoryId}_" in it }.forEach { remove(it) } }.commit()
    }

    private fun alertTitles(): List<String> {
        val until = SystemClock.uptimeMillis() + 5_000
        fun ours() = notifications.activeNotifications.filter { it.tag == BudgetAlertHelper.TAG && it.id == 100_000 + categoryId.toInt() }
        while (ours().isEmpty() && SystemClock.uptimeMillis() < until) Thread.sleep(50)
        return ours().map { it.notification.extras.getString(Notification.EXTRA_TITLE).orEmpty() }
    }

    @Test fun anSmsFiledIntoACategoryOverItsLimitAlerts() = runBlocking {
        val now = System.currentTimeMillis()
        SmsReceiver.save(context, db, listOf(SmsReceiver.Capture(ParsedTransaction(200.0, "SWIGGY"),
            "ICICI Bank Acct XX123 debited for Rs 200.00; SWIGGY credited.", "JD-ICICIB", now)), now)
        assertEquals(listOf("🍔 Food & Dining limit exceeded!"), alertTitles())
    }

    @Test fun aPaymentAppCaptureFiledIntoACategoryOverItsLimitAlerts() = runBlocking {
        db.merchantMappingDao().insert(MerchantMapping("GPay → SWIGGY", categoryId))
        TransactionNotificationListener.save(context, db, ParsedTransaction(200.0, "GPay → SWIGGY"), "Paid ₹200 to SWIGGY", System.currentTimeMillis())
        assertEquals(listOf("🍔 Food & Dining limit exceeded!"), alertTitles())
    }
}
