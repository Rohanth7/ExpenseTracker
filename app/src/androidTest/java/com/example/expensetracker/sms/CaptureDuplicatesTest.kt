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

    private fun saved(source: String, secondsAgo: Long, amount: Double = 149.0, raw: String? = null, description: String = "x") = runBlocking {
        db.expenseDao().insert(Expense(amount = amount, description = description, categoryId = -1, date = now - secondsAgo * 1000, source = source, rawSms = raw))
    }

    private fun duplicate(source: String, content: String = sms, payee: String = "CRUNCHYROLL") =
        runBlocking { CaptureDuplicates.isDuplicate(db.expenseDao(), 149.0, source, payee, content, now) }

    @Test fun theSamePaymentSeenByTheUpiAppIsADuplicate() {
        saved("UPI", secondsAgo = 20)
        assertTrue(duplicate("SMS"))
    }

    @Test fun theSamePaymentSeenBySmsIsADuplicateForTheUpiApp() {
        saved("SMS", secondsAgo = 20)
        assertTrue(duplicate("UPI", content = "Paid ₹149 to Crunchyroll"))
    }

    @Test fun anExpenseAddedByHandDoesNotSwallowAnSmsFromAnotherPayee() {
        saved("Manual", secondsAgo = 20, description = "YouTube Premium")
        assertFalse(duplicate("SMS"))
        assertFalse(duplicate("UPI", content = "Paid ₹149 to Crunchyroll"))
    }

    @Test fun anAutoLoggedBillDoesNotSwallowAnSmsFromAnotherPayee() {
        saved("Recurring", secondsAgo = 20, description = "Netflix")
        assertFalse(duplicate("SMS"))
    }

    /** Paid, added it by hand straight away, then the bank's SMS came: one payment, not two. */
    @Test fun theSamePaymentAddedByHandFirstIsADuplicate() {
        saved("Manual", secondsAgo = 40, description = "Crunchyroll")
        assertTrue(duplicate("SMS"))
        assertTrue(duplicate("UPI", content = "Paid ₹149 to Crunchyroll", payee = "GPay → Crunchyroll"))
    }

    @Test fun anAutoLoggedBillForTheSamePayeeIsADuplicate() {
        saved("Recurring", secondsAgo = 60, description = "Netflix")
        assertTrue(duplicate("SMS", payee = "NETFLIX ENTERTAINMENT"))
    }

    @Test fun aNoteAddedByHandIsNotThePayee() {
        saved("Manual", secondsAgo = 20, description = "Lunch")
        assertFalse(duplicate("SMS", payee = "SWIGGY"))
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
