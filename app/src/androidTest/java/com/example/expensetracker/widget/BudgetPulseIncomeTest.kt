package com.example.expensetracker.widget

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Income
import com.example.expensetracker.data.db.entity.RecurringIncome
import com.example.expensetracker.data.preferences.PreferencesManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/** The widgets' income follows the Overview's rule (IncomeForecast); in-memory DB, test prefs. */
@RunWith(AndroidJUnit4::class)
class BudgetPulseIncomeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var prefs: PreferencesManager

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        context.deleteSharedPreferences("widget_income_test")
        prefs = PreferencesManager(context, "widget_income_test").apply { monthlyIncome = 85000.0; monthStartDay = 1 }
    }

    @After fun tearDown() { db.close() }

    private fun load() = runBlocking { BudgetPulseRepository(db, prefs).load() }

    @Test fun withNoIncomeRecordsTheExpectedFigureIsUsed() = assertEquals(85000.0, load().income, 0.0)

    @Test fun loggedIncomeThisMonthReplacesTheExpectedFigure() = runBlocking {
        db.incomeDao().insert(Income(amount = 3000.0, description = "Freelance", date = System.currentTimeMillis()))
        // Last month's doesn't count
        db.incomeDao().insert(Income(amount = 9999.0, description = "Old", date = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.timeInMillis))
        assertEquals(3000.0, load().income, 0.0)
    }

    @Test fun recurringIncomeStillDueThisMonthCounts() = runBlocking {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        val last = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH)
        if (today == last) return@runBlocking  // nothing is still to come on the month's last day
        db.recurringIncomeDao().insert(RecurringIncome(name = "Salary", amount = 50000.0, dayOfMonth = last))
        assertEquals(50000.0, load().income, 0.0)
    }
}
