package com.example.expensetracker.sms

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Expense
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against a throwaway in-memory database; the app's data isn't touched. */
@RunWith(AndroidJUnit4::class)
class CaptureDuplicatesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private val now = 1_790_000_000_000L
    private val sms = "ICICI Bank Acct XX123 debited for Rs 149.00; CRUNCHYROLL credited."

    @Before fun setUp() { db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build() }
    @After fun tearDown() { db.close() }

    private fun saved(source: String, secondsAgo: Long, amount: Double = 149.0, raw: String? = null) = runBlocking {
        db.expenseDao().insert(Expense(amount = amount, description = "x", categoryId = -1, date = now - secondsAgo * 1000, source = source, rawSms = raw))
    }

    private fun duplicate(source: String, content: String = sms) =
        runBlocking { CaptureDuplicates.isDuplicate(db.expenseDao(), 149.0, source, content, now) }

    @Test fun theSamePaymentSeenByTheUpiAppIsADuplicate() {
        saved("UPI", secondsAgo = 20)
        assertTrue(duplicate("SMS"))
    }

    @Test fun theSamePaymentSeenBySmsIsADuplicateForTheUpiApp() {
        saved("SMS", secondsAgo = 20)
        assertTrue(duplicate("UPI", content = "Paid ₹149 to Crunchyroll"))
    }

    @Test fun anExpenseAddedByHandDoesNotSwallowAnSms() {
        saved("Manual", secondsAgo = 20)
        assertFalse(duplicate("SMS"))
        assertFalse(duplicate("UPI", content = "Paid ₹149 to Crunchyroll"))
    }

    @Test fun anAutoLoggedBillDoesNotSwallowAnSms() {
        saved("Recurring", secondsAgo = 20)
        assertFalse(duplicate("SMS"))
    }

    @Test fun anOtherSourceCaptureOlderThan3MinutesIsNotADuplicate() {
        saved("UPI", secondsAgo = 181)
        assertFalse(duplicate("SMS"))
    }

    @Test fun aDifferentAmountIsNotADuplicate() {
        saved("UPI", secondsAgo = 20, amount = 150.0)
        assertFalse(duplicate("SMS"))
    }

    @Test fun theSameSmsTwiceWithin30SecondsIsADuplicate() {
        saved("SMS", secondsAgo = 10, raw = sms)
        assertTrue(duplicate("SMS"))
    }

    @Test fun aDifferentSmsForTheSameAmountIsNotADuplicate() {
        saved("SMS", secondsAgo = 10, raw = "ICICI Bank Acct XX123 debited for Rs 149.00; YOUTUBE credited.")
        assertFalse(duplicate("SMS"))
    }
}
