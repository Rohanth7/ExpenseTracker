package com.example.expensetracker.widget

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.period.BudgetPeriod
import com.example.expensetracker.data.period.OverspentDay
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.ui.mascot.MascotMood
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The widget records the day the month's money ran out even while she is hidden; in-memory DB, test prefs. */
@RunWith(AndroidJUnit4::class)
class BudgetPulseOverspentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var prefs: PreferencesManager
    private val now = System.currentTimeMillis()
    private val key = OverspentDay.PREFIX + BudgetPeriod.epochDay(BudgetPeriod.current(now, 1).start)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        context.deleteSharedPreferences("widget_overspent_test")
        prefs = PreferencesManager(context, "widget_overspent_test").apply { monthlyIncome = 10_000.0; monthStartDay = 1 }
        runBlocking { db.expenseDao().insert(Expense(amount = 12_000.0, description = "Rent", date = now, source = "Manual")) }
    }

    @After fun tearDown() { db.close() }

    private fun load() = runBlocking { BudgetPulseRepository(db, prefs).load(now) }

    @Test fun whileSheIsHiddenTheDayIsStillRecorded() {
        prefs.mascotEnabled = false
        assertNull(load().mood)
        assertEquals(mapOf(key to BudgetPeriod.epochDay(now)), prefs.overspentDays.all())
    }

    @Test fun turnedOnDaysAfterTheMoneyRanOutSheIsSad() {
        prefs.overspentDays.put(key, BudgetPeriod.epochDay(now) - 3)
        prefs.mascotEnabled = true
        assertEquals(MascotMood.Sad, load().mood)
    }
}
