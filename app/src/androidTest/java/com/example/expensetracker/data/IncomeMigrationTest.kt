package com.example.expensetracker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Income
import com.example.expensetracker.data.db.entity.RecurringIncome
import com.example.expensetracker.data.db.entity.RecurringIncomeMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A database made by the app before income records (v13, schema pulled from a fresh install of
 * `main` at 25f3a4f) opens with this version: Room runs the migrations and then checks every table
 * against the entities, so a wrong CREATE TABLE fails here with "Migration didn't properly handle".
 */
@RunWith(AndroidJUnit4::class)
class IncomeMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    @After fun tearDown() { context.deleteDatabase(name) }

    @Test fun v13DatabaseUpgradesWithDataIntact() {
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            // The fixture is in the test APK's assets, not the app's.
            InstrumentationRegistry.getInstrumentation().context.assets.open("v13_schema.sql").bufferedReader().readText()
                .split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { db.execSQL(it) }
            db.execSQL("INSERT INTO categories (name, emoji, colorHex, monthlyLimit, parentId) VALUES ('Food', '🍔', '#E57373', 0, NULL)")
            db.execSQL("INSERT INTO expenses (amount, description, categoryId, date, source, rawSms, tags, paymentMethod) VALUES (450, 'SWIGGY', 1, 1000, 'SMS', NULL, '', 'UPI')")
            db.execSQL("INSERT INTO bills (name, amount, dueDayOfMonth, reminderDays, categoryId, isEnabled, autoLog) VALUES ('Rent', 15000, 5, 3, 1, 1, 1)")
            db.version = 13
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*AppDatabase.ALL_MIGRATIONS).build()
        try {
            runBlocking {
                assertEquals(listOf("SWIGGY"), room.expenseDao().getAll().first().map { it.description })
                assertEquals(listOf("Food"), room.categoryDao().getAllOnce().map { it.name })
                assertEquals(listOf("Rent"), room.billDao().getAll().first().map { it.name })
                // The new tables start empty and work.
                assertEquals(0.0, room.incomeDao().sumInRange(0, Long.MAX_VALUE), 0.0)
                room.incomeDao().insert(Income(amount = 50000.0, description = "Salary", date = 2000))
                assertEquals(50000.0, room.incomeDao().sumInRange(0, 3000), 0.0)
                val rid = room.recurringIncomeDao().insert(RecurringIncome(name = "Salary", amount = 50000.0, dayOfMonth = 1))
                room.incomeDao().insert(Income(amount = 50000.0, description = "Salary", date = 2500, source = "Recurring", recurringIncomeId = rid))
                assertEquals(rid, room.incomeDao().getInRange(2500, 2500).single().recurringIncomeId)
                room.recurringIncomeDao().insertMonth(RecurringIncomeMonth(rid, 2026, 10))
                assertEquals(listOf(RecurringIncomeMonth(rid, 2026, 10)), room.recurringIncomeDao().getMonthsOnce())
                assertEquals(listOf("Salary"), room.recurringIncomeDao().getEnabled().map { it.name })
            }
        } finally {
            room.close()
        }
    }
}
