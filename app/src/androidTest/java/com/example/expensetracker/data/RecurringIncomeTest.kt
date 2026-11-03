package com.example.expensetracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Income
import com.example.expensetracker.data.db.entity.RecurringIncome
import com.example.expensetracker.data.recurring.RecurringManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/** Runs against a throwaway in-memory database; the app's data isn't touched. */
@RunWith(AndroidJUnit4::class)
class RecurringIncomeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase

    @Before fun setUp() { db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build() }
    @After fun tearDown() { db.close() }

    private fun day(year: Int, month: Int, day: Int, hour: Int = 12): Calendar =
        Calendar.getInstance().apply { clear(); set(year, month, day, hour, 0) }

    private fun midnight(year: Int, month: Int, day: Int) = day(year, month, day, 0).timeInMillis

    private fun incomes() = runBlocking { db.incomeDao().getAllOnce() }
    private suspend fun apply(at: Calendar) = RecurringManager.applyRecurringIncome(db, at)

    @Test fun salaryOnThe1stIsLoggedOnceDatedThe1st() = runBlocking {
        val id = db.recurringIncomeDao().insert(RecurringIncome(name = "Salary", amount = 50000.0, dayOfMonth = 1))
        assertEquals(1, apply(day(2026, Calendar.OCTOBER, 16)))
        val logged = incomes().single()
        assertEquals(50000.0, logged.amount, 0.0)
        assertEquals("Salary", logged.description)
        assertEquals("Recurring", logged.source)
        assertEquals(id, logged.recurringIncomeId)
        assertEquals(midnight(2026, Calendar.OCTOBER, 1), logged.date)
    }

    @Test fun runningTwiceStillLogsOnce() = runBlocking {
        db.recurringIncomeDao().insert(RecurringIncome(name = "Salary", amount = 50000.0, dayOfMonth = 1))
        apply(day(2026, Calendar.OCTOBER, 16))
        assertEquals(0, apply(day(2026, Calendar.OCTOBER, 17)))
        assertEquals(1, incomes().size)
    }

    @Test fun notLoggedBeforeItsDay() = runBlocking {
        db.recurringIncomeDao().insert(RecurringIncome(name = "Salary", amount = 50000.0, dayOfMonth = 25))
        apply(day(2026, Calendar.OCTOBER, 16))
        assertEquals(0, incomes().size)
    }

    @Test fun aDisabledOneIsNotLogged() = runBlocking {
        db.recurringIncomeDao().insert(RecurringIncome(name = "Salary", amount = 50000.0, dayOfMonth = 1, isEnabled = false))
        apply(day(2026, Calendar.OCTOBER, 16))
        assertEquals(0, incomes().size)
    }

    @Test fun day31IsTheLastDayOfAShortMonth() = runBlocking {
        db.recurringIncomeDao().insert(RecurringIncome(name = "Salary", amount = 50000.0, dayOfMonth = 31))
        apply(day(2027, Calendar.FEBRUARY, 27))
        assertEquals(0, incomes().size)
        apply(day(2027, Calendar.FEBRUARY, 28))
        assertEquals(midnight(2027, Calendar.FEBRUARY, 28), incomes().single().date)
    }

    @Test fun eachMonthIsLoggedAgain() = runBlocking {
        db.recurringIncomeDao().insert(RecurringIncome(name = "Salary", amount = 50000.0, dayOfMonth = 1))
        apply(day(2026, Calendar.SEPTEMBER, 2))
        apply(day(2026, Calendar.OCTOBER, 2))
        assertEquals(listOf(midnight(2026, Calendar.OCTOBER, 1), midnight(2026, Calendar.SEPTEMBER, 1)), incomes().map { it.date })
    }

    /** The month is recorded with the entry, so deleting the entry doesn't bring it back. */
    @Test fun aDeletedSalaryIsNotLoggedAgainThatMonth() = runBlocking {
        db.recurringIncomeDao().insert(RecurringIncome(name = "Salary", amount = 50000.0, dayOfMonth = 1))
        apply(day(2026, Calendar.OCTOBER, 16))
        db.incomeDao().delete(incomes().single())
        assertEquals(0, apply(day(2026, Calendar.OCTOBER, 17)))
    }

    /** Two launches racing (e.g. a rotation while the first is still running) log it once. */
    @Test fun twoAtOnceLogOnce() = runBlocking {
        db.recurringIncomeDao().insert(RecurringIncome(name = "Salary", amount = 50000.0, dayOfMonth = 1))
        val now = day(2026, Calendar.OCTOBER, 16)
        List(4) { async(Dispatchers.IO) { RecurringManager.applyRecurringIncome(db, now.clone() as Calendar) } }.awaitAll()
        assertEquals(1, incomes().size)
    }

    /** Logged by hand before it was set up: adding it in Settings mustn't log the month's salary again. */
    @Test fun salaryAlreadyLoggedByHandThisMonthIsNotLoggedAgain() = runBlocking {
        db.incomeDao().insert(Income(amount = 50000.0, description = "salary ", date = day(2026, Calendar.OCTOBER, 1, 10).timeInMillis))
        db.recurringIncomeDao().insert(RecurringIncome(name = "Salary", amount = 50000.0, dayOfMonth = 1))
        assertEquals(0, apply(day(2026, Calendar.OCTOBER, 28)))
        assertEquals(1, incomes().size)
    }

    @Test fun aDifferentAmountOrLastMonthsByHandStillLogs() = runBlocking {
        db.incomeDao().insert(Income(amount = 2000.0, description = "Salary", date = day(2026, Calendar.OCTOBER, 1).timeInMillis))
        db.incomeDao().insert(Income(amount = 50000.0, description = "Salary", date = day(2026, Calendar.SEPTEMBER, 1).timeInMillis))
        db.recurringIncomeDao().insert(RecurringIncome(name = "Salary", amount = 50000.0, dayOfMonth = 1))
        assertEquals(1, apply(day(2026, Calendar.OCTOBER, 28)))
    }

    /** Deleting a recurring income keeps what it logged, as entries of their own, so adding it back doesn't log the month twice. */
    @Test fun deletingAndAddingItBackDoesNotLogTheMonthTwice() = runBlocking {
        val old = RecurringIncome(id = db.recurringIncomeDao().insert(RecurringIncome(name = "Salary", amount = 85000.0, dayOfMonth = 1)),
            name = "Salary", amount = 85000.0, dayOfMonth = 1)
        apply(day(2026, Calendar.OCTOBER, 10))
        db.recurringIncomeDao().deleteKeepingIncome(old)
        assertNull(incomes().single().recurringIncomeId)
        db.recurringIncomeDao().insert(RecurringIncome(name = "Salary", amount = 85000.0, dayOfMonth = 1))
        assertEquals(0, apply(day(2026, Calendar.OCTOBER, 10)))
        assertEquals(1, incomes().size)
    }

    /** Paid a day late, so its entry was moved from 31 Oct to 1 Nov: November's is still logged. */
    @Test fun movingTheLoggedSalaryIntoNextMonthDoesNotSkipNextMonths() = runBlocking {
        db.recurringIncomeDao().insert(RecurringIncome(name = "Salary", amount = 50000.0, dayOfMonth = 31))
        apply(day(2026, Calendar.OCTOBER, 31))
        db.incomeDao().update(incomes().single().copy(date = midnight(2026, Calendar.NOVEMBER, 1)))
        assertEquals(1, apply(day(2026, Calendar.NOVEMBER, 30)))
        assertEquals(2, incomes().size)
    }

    /** Two tenants paying the same rent, one entered by hand: the other is still logged. */
    @Test fun oneEntryByHandCoversOnlyOneOfTwoAlike() = runBlocking {
        db.incomeDao().insert(Income(amount = 10000.0, description = "Rent received", date = day(2026, Calendar.OCTOBER, 2).timeInMillis))
        db.recurringIncomeDao().insert(RecurringIncome(name = "Rent received", amount = 10000.0, dayOfMonth = 1))
        db.recurringIncomeDao().insert(RecurringIncome(name = "Rent received", amount = 10000.0, dayOfMonth = 1))
        assertEquals(1, apply(day(2026, Calendar.OCTOBER, 16)))
        assertEquals(0, apply(day(2026, Calendar.OCTOBER, 17)))
        assertEquals(2, incomes().size)
    }

    /** Two alike salaries, on the 15th and the 1st: the one entered by hand on the 1st is the 1st's. */
    @Test fun theEarliestDueDayTakesTheHandEntry() = runBlocking {
        db.recurringIncomeDao().insert(RecurringIncome(id = 3, name = "Salary", amount = 40000.0, dayOfMonth = 15))
        db.recurringIncomeDao().insert(RecurringIncome(id = 5, name = "Salary", amount = 40000.0, dayOfMonth = 1))
        db.incomeDao().insert(Income(amount = 40000.0, description = "Salary", date = day(2026, Calendar.OCTOBER, 1, 9).timeInMillis))
        assertEquals(0, apply(day(2026, Calendar.OCTOBER, 1)))
        assertEquals(1, apply(day(2026, Calendar.OCTOBER, 15)))
        assertEquals(3L, incomes().single { it.recurringIncomeId != null }.recurringIncomeId)
    }

    /** Two salaries with the same name and amount are two incomes. */
    @Test fun twoAlikeAreBothLogged() = runBlocking {
        db.recurringIncomeDao().insert(RecurringIncome(name = "Rent received", amount = 12000.0, dayOfMonth = 5))
        db.recurringIncomeDao().insert(RecurringIncome(name = "Rent received", amount = 12000.0, dayOfMonth = 5))
        apply(day(2026, Calendar.OCTOBER, 16))
        assertEquals(2, incomes().size)
    }
}
