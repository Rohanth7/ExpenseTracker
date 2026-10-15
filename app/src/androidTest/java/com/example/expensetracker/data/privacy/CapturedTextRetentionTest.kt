package com.example.expensetracker.data.privacy

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Expense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a device against a throwaway in-memory database; the app's real data isn't touched. */
@RunWith(AndroidJUnit4::class)
class CapturedTextRetentionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private val now = 1_800_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    @Before fun setUp() { runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        db.expenseDao().insert(Expense(id = 1, amount = 10.0, description = "OLD", source = "SMS", rawSms = "old sms", date = now - 8 * day))
        db.expenseDao().insert(Expense(id = 2, amount = 20.0, description = "EDGE", source = "UPI", rawSms = "7 days and 1 minute", date = now - 7 * day - 60_000))
        db.expenseDao().insert(Expense(id = 3, amount = 30.0, description = "RECENT", source = "SMS", rawSms = "recent sms", date = now - 2 * day))
        db.expenseDao().insert(Expense(id = 4, amount = 40.0, description = "Lunch", source = "Manual", date = now - 30 * day))
    } }

    @After fun tearDown() = db.close()

    private fun texts() = runBlocking { db.expenseDao().getAll().first().sortedBy { it.id }.map { it.rawSms } }

    @Test fun capturedTextOlderThanSevenDaysIsDeleted() {
        runBlocking { CapturedTextRetention.purge(db, now) }
        assertEquals(listOf(null, null, "recent sms", null), texts())
    }

    @Test fun theExpensesThemselvesAreKept() {
        runBlocking { CapturedTextRetention.purge(db, now) }
        val expenses = runBlocking { db.expenseDao().getAll().first().sortedBy { it.id } }
        assertEquals(listOf("OLD", "EDGE", "RECENT", "Lunch"), expenses.map { it.description })
        assertEquals(listOf(10.0, 20.0, 30.0, 40.0), expenses.map { it.amount })
    }
}
