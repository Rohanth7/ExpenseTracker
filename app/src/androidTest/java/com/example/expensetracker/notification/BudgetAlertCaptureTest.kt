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
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.data.repository.CategoryRepository
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
        alerts.edit().apply { alerts.all.keys.filter { key -> listOf(categoryId, drinksId, coffeeId, snacksId).any { "_${it}_" in key } }.forEach { remove(it) } }.commit()
    }

    private val drinksId = 987_660L
    private val coffeeId = 987_661L
    private val snacksId = 987_662L

    private fun alertTitles(category: Long = categoryId): List<String> {
        val until = SystemClock.uptimeMillis() + 5_000
        fun ours() = notifications.activeNotifications.filter { it.tag == BudgetAlertHelper.TAG && it.id == 100_000 + category.toInt() }
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

    private suspend fun check(category: Long) = BudgetAlertHelper.checkAndNotify(context, category,
        CategoryRepository(db.categoryDao(), db.expenseDao()), ExpenseRepository(db.expenseDao(), context))

    /** B-06: a subcategory's own limit was offered in the dialog and then ignored: only its parent's was checked. */
    @Test fun aSubcategoryOverItsOwnLimitAlerts() = runBlocking {
        db.categoryDao().insert(Category(id = drinksId, name = "Drinks", emoji = "🥤"))
        db.categoryDao().insert(Category(id = coffeeId, name = "Coffee", emoji = "☕", monthlyLimit = 500.0, parentId = drinksId))
        db.expenseDao().insert(Expense(amount = 600.0, description = "Blue Tokai", categoryId = coffeeId))
        check(coffeeId)
        assertEquals(listOf("☕ Drinks › Coffee limit exceeded!"), alertTitles(coffeeId))
    }

    /** A subcategory's spending counts against its own limit and its parent's; a sibling's doesn't count against it. */
    @Test fun aSubcategoryAndItsParentEachAlertOnTheirOwnLimit() = runBlocking {
        db.categoryDao().insert(Category(id = drinksId, name = "Drinks", emoji = "🥤", monthlyLimit = 1000.0))
        db.categoryDao().insert(Category(id = coffeeId, name = "Coffee", emoji = "☕", monthlyLimit = 500.0, parentId = drinksId))
        db.categoryDao().insert(Category(id = snacksId, name = "Snacks", emoji = "🍪", parentId = drinksId))
        db.expenseDao().insert(Expense(amount = 700.0, description = "Chips", categoryId = snacksId))
        db.expenseDao().insert(Expense(amount = 400.0, description = "Blue Tokai", categoryId = coffeeId))
        check(coffeeId)
        // Drinks is at 1,100 of 1,000; Coffee at 400 of 500 is only at its warning level
        assertEquals(listOf("🥤 Drinks limit exceeded!"), alertTitles(drinksId))
        assertEquals(emptyList<String>(), notifications.activeNotifications.filter { it.id == 100_000 + coffeeId.toInt() }.map { it.tag })
        assertEquals(1, notifications.activeNotifications.count { it.tag == BudgetAlertHelper.TAG && it.id == 200_000 + coffeeId.toInt() })
    }
}
