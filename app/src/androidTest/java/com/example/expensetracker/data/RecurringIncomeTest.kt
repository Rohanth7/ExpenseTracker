package com.example.expensetracker.data

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.RecurringIncome
import com.example.expensetracker.data.recurring.RecurringManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/** Runs against a throwaway in-memory database and its own prefs file; the app's data isn't touched. */
@RunWith(AndroidJUnit4::class)
class RecurringIncomeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var prefs: SharedPreferences

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        prefs = context.getSharedPreferences("recurring_income_test", Context.MODE_PRIVATE).apply { edit().clear().commit() }
    }

    @After fun tearDown() {
        db.close()
        prefs.edit().clear().commit()
    }

    private fun day(year: Int, month: Int, day: Int, hour: Int = 12): Calendar =
        Calendar.getInstance().apply { clear(); set(year, month, day, hour, 0) }

    private fun midnight(year: Int, month: Int, day: Int) = day(year, month, day, 0).timeInMillis

    private fun incomes() = runBlocking { db.incomeDao().getAllOnce() }

    @Test fun salaryOnThe1stIsLoggedOnceDatedThe1st() = runBlocking {
        val id = db.recurringIncomeDao().insert(RecurringIncome(name = "Salary", amount = 50000.0, dayOfMonth = 1))
        assertEquals(1, RecurringManager.applyRecurringIncome(db, prefs, day(2026, Calendar.OCTOBER, 16)))
        val logged = incomes().single()
        assertEquals(50000.0, logged.amount, 0.0)
        assertEquals("Salary", logged.description)
        assertEquals("Recurring", logged.source)
        assertEquals(id, logged.recurringIncomeId)
        assertEquals(midnight(2026, Calendar.OCTOBER, 1), logged.date)
    }

    @Test fun runningTwiceStillLogsOnce() = runBlocking {
        db.recurringIncomeDao().insert(RecurringIncome(name = "Salary", amount = 50000.0, dayOfMonth = 1))
        RecurringManager.applyRecurringIncome(db, prefs, day(2026, Calendar.OCTOBER, 16))
        assertEquals(0, RecurringManager.applyRecurringIncome(db, prefs, day(2026, Calendar.OCTOBER, 17)))
        assertEquals(1, incomes().size)
    }

    @Test fun notLoggedBeforeItsDay() = runBlocking {
        db.recurringIncomeDao().insert(RecurringIncome(name = "Salary", amount = 50000.0, dayOfMonth = 25))
        RecurringManager.applyRecurringIncome(db, prefs, day(2026, Calendar.OCTOBER, 16))
        assertEquals(0, incomes().size)
    }

    @Test fun aDisabledOneIsNotLogged() = runBlocking {
        db.recurringIncomeDao().insert(RecurringIncome(name = "Salary", amount = 50000.0, dayOfMonth = 1, isEnabled = false))
        RecurringManager.applyRecurringIncome(db, prefs, day(2026, Calendar.OCTOBER, 16))
        assertEquals(0, incomes().size)
    }

    @Test fun day31IsTheLastDayOfAShortMonth() = runBlocking {
        db.recurringIncomeDao().insert(RecurringIncome(name = "Salary", amount = 50000.0, dayOfMonth = 31))
        RecurringManager.applyRecurringIncome(db, prefs, day(2027, Calendar.FEBRUARY, 27))
        assertEquals(0, incomes().size)
        RecurringManager.applyRecurringIncome(db, prefs, day(2027, Calendar.FEBRUARY, 28))
        assertEquals(midnight(2027, Calendar.FEBRUARY, 28), incomes().single().date)
    }

    @Test fun eachMonthIsLoggedAgain() = runBlocking {
        db.recurringIncomeDao().insert(RecurringIncome(name = "Salary", amount = 50000.0, dayOfMonth = 1))
        RecurringManager.applyRecurringIncome(db, prefs, day(2026, Calendar.SEPTEMBER, 2))
        RecurringManager.applyRecurringIncome(db, prefs, day(2026, Calendar.OCTOBER, 2))
        assertEquals(listOf(midnight(2026, Calendar.OCTOBER, 1), midnight(2026, Calendar.SEPTEMBER, 1)), incomes().map { it.date })
    }

    /** The pref and the insert aren't one write: a crash between them must not log it twice. */
    @Test fun alreadyInTheDatabaseWithoutThePrefIsNotLoggedAgain() = runBlocking {
        db.recurringIncomeDao().insert(RecurringIncome(name = "Salary", amount = 50000.0, dayOfMonth = 1))
        RecurringManager.applyRecurringIncome(db, prefs, day(2026, Calendar.OCTOBER, 16))
        prefs.edit().clear().commit()
        RecurringManager.applyRecurringIncome(db, prefs, day(2026, Calendar.OCTOBER, 16))
        assertEquals(1, incomes().size)
    }

    /** Two launches racing (e.g. a rotation while the first is still running) log it once. */
    @Test fun twoAtOnceLogOnce() = runBlocking {
        db.recurringIncomeDao().insert(RecurringIncome(name = "Salary", amount = 50000.0, dayOfMonth = 1))
        val now = day(2026, Calendar.OCTOBER, 16)
        List(4) { async(Dispatchers.IO) { RecurringManager.applyRecurringIncome(db, prefs, now.clone() as Calendar) } }.awaitAll()
        assertEquals(1, incomes().size)
    }

    /** Two salaries with the same name and amount are two incomes (bills dedupe on name + amount; income uses its id). */
    @Test fun twoAlikeAreBothLogged() = runBlocking {
        db.recurringIncomeDao().insert(RecurringIncome(name = "Rent received", amount = 12000.0, dayOfMonth = 5))
        db.recurringIncomeDao().insert(RecurringIncome(name = "Rent received", amount = 12000.0, dayOfMonth = 5))
        RecurringManager.applyRecurringIncome(db, prefs, day(2026, Calendar.OCTOBER, 16))
        assertEquals(2, incomes().size)
    }
}
