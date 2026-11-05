package com.example.expensetracker.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.sms.CustomRules
import com.example.expensetracker.sms.ParsingRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Payment-app notifications built as the apps build them, read and saved the way the listener
 * does, against a throwaway in-memory database.
 */
@RunWith(AndroidJUnit4::class)
class NotificationCaptureTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val gpay = "com.google.android.apps.nbu.paisa.user"
    private val phonepe = "com.phonepe.app"
    private lateinit var db: AppDatabase
    private var rules = emptyList<ParsingRule>()

    @Before fun setUp() { db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build() }
    @After fun tearDown() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter { it.tag == NotificationHelper.TAG }.forEach { manager.cancel(it.tag, it.id) }
        db.close()
    }

    private fun post(pkg: String, title: String, text: String, style: Notification.Style? = null) = runBlocking {
        val n = Notification.Builder(context, NotificationHelper.CHANNEL_ID).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(text).apply { if (style != null) setStyle(style) }.build()
        val body = TransactionNotificationListener.text(n.extras)
        val transaction = TransactionNotificationListener.transaction(rules, title, body, pkg) ?: return@runBlocking
        TransactionNotificationListener.save(context, db, transaction, body, System.currentTimeMillis())
    }

    private fun saved(): List<Expense> = runBlocking { db.expenseDao().getAll().first() }

    /** BUG-21: a reward line and "<payee> received your money" dropped completed payments. */
    @Test fun completedPaymentsWithRewardOrReceivedWordingAreSaved() {
        post(gpay, "Payment successful", "Paid ₹250 to Swiggy. You won a scratch card!")
        post(phonepe, "₹500 paid to Ramesh", "Ramesh received your money")
        assertEquals(setOf("GPay → Swiggy" to 250.0, "PhonePe → Ramesh" to 500.0), saved().map { it.description to it.amount }.toSet())
    }

    /** BUG-22: only EXTRA_TEXT was read. */
    @Test fun detailsOnlyInTheExpandedTextAreSaved() {
        post(phonepe, "Payment successful", "Tap to view details", Notification.BigTextStyle().bigText("Paid ₹430 to Dominos Pizza from HDFC Bank XX12"))
        val e = saved().single()
        assertEquals(430.0, e.amount, 0.0)
        assertEquals("PhonePe → Dominos Pizza", e.description)
    }

    @Test fun detailsInInboxLinesAreSaved() {
        post(gpay, "GPay", "1 new update", Notification.InboxStyle().addLine("Paid ₹75 to Metro Card").addLine("UPI ID ending 1234"))
        assertEquals("GPay → Metro Card", saved().single().description)
    }

    @Test fun expandedTextThatRepeatsTheShortTextIsReadOnce() {
        val n = Notification.Builder(context, NotificationHelper.CHANNEL_ID).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentText("Paid ₹250 to Swiggy").setStyle(Notification.BigTextStyle().bigText("Paid ₹250 to Swiggy. You won a scratch card!")).build()
        assertEquals("Paid ₹250 to Swiggy. You won a scratch card!", TransactionNotificationListener.text(n.extras))
    }

    /** BUG-09: a "Not a payment" rule was ignored for notifications. */
    @Test fun aNotAPaymentRuleStopsTheNotification() {
        val text = "Paid ₹99 to Google Play"
        rules = listOf((CustomRules.build(text, ParsingRule.Kind.IGNORE, id = "test-ignore", now = 1) as CustomRules.BuildResult.Ok).rule)
        post(gpay, "", text)
        assertEquals(emptyList<Expense>(), saved())
    }
}
