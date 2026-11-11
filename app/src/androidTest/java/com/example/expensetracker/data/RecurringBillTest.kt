package com.example.expensetracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Bill
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.data.recurring.RecurringManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/** Auto-log bills against a throwaway in-memory database and settings file. */
@RunWith(AndroidJUnit4::class)
class RecurringBillTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private val prefs by lazy { PreferencesManager(context, "recurring_bill_test") }

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        context.deleteSharedPreferences("recurring_bill_test")
    }
    @After fun tearDown() { db.close() }

    private fun day(year: Int, month: Int, day: Int, hour: Int = 12): Calendar =
        Calendar.getInstance().apply { clear(); set(year, month - 1, day, hour, 0) }

    private fun recurring() = runBlocking { db.expenseDao().getAll().first().filter { it.source == "Recurring" }.map { it.description to it.amount } }

    /** BUG-28: the bank's SMS for the Netflix charge, then the auto-log bill: two ₹499 expenses. */
    @Test fun aBillAlreadyPaidThisMonthIsNotLoggedAgain() = runBlocking {
        db.expenseDao().insert(Expense(amount = 499.0, description = "NETFLIX", source = "SMS", date = day(2026, 11, 5).timeInMillis))
        db.billDao().insert(Bill(name = "Netflix", amount = 499.0, dueDayOfMonth = 1, autoLog = true))
        RecurringManager.applyBills(db, prefs, day(2026, 11, 20))
        assertEquals(emptyList<Pair<String, Double>>(), recurring())
        // Next month it's logged as usual
        RecurringManager.applyBills(db, prefs, day(2026, 12, 2))
        assertEquals(listOf("Netflix" to 499.0), recurring())
    }

    @Test fun anotherPayeeOrAmountDoesNotCountAsPaid() = runBlocking {
        db.expenseDao().insert(Expense(amount = 499.0, description = "NETFLIX", source = "SMS", date = day(2026, 11, 5).timeInMillis))
        db.expenseDao().insert(Expense(amount = 649.0, description = "Spotify", source = "Manual", date = day(2026, 10, 30).timeInMillis))
        db.billDao().insert(Bill(name = "Netflix", amount = 649.0, dueDayOfMonth = 1, autoLog = true))
        db.billDao().insert(Bill(name = "Spotify", amount = 649.0, dueDayOfMonth = 3, autoLog = true))
        RecurringManager.applyBills(db, prefs, day(2026, 11, 20))
        assertEquals(setOf("Netflix" to 649.0, "Spotify" to 649.0), recurring().toSet())
    }

    /** B-02: a bill saved with ₹0 (allowed before) must not add a ₹0 expense. */
    @Test fun aZeroAmountBillIsNotLogged() = runBlocking {
        db.billDao().insert(Bill(name = "Rent", amount = 0.0, dueDayOfMonth = 1, autoLog = true))
        RecurringManager.applyBills(db, prefs, day(2026, 11, 20))
        assertEquals(emptyList<Pair<String, Double>>(), recurring())
    }
}
