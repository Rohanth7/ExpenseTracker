package com.example.expensetracker.sms

import android.app.NotificationManager
import android.content.Context
import android.telephony.SmsMessage
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The receiver's parse and save steps with a real PDU, against a throwaway in-memory database. */
@RunWith(AndroidJUnit4::class)
class SmsCaptureTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase

    // A 3GPP SMS-DELIVER from 9876543210, sent 4 Nov 2026 22:15 IST:
    // "Rs 349.00 debited from A/c XX12 to SWIGGY. Ref 530912345620"
    private val pdu = ("00040A81896745230100086211402251002276005200730020003300340039002E0030003000200064006500620069007400650064002000660072006F006D00200041002F00630020005800580031003200200074006F0020005300570049004700470059002E00200052006500660020003500330030003900310032003300340035003600320030")
        .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val message = SmsMessage.createFromPdu(pdu, "3gpp")
    private val sent = message.timestampMillis

    @Before fun setUp() { db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build() }
    @After fun tearDown() {
        context.getSystemService(NotificationManager::class.java).cancelAll()
        db.close()
    }

    private fun capture(now: Long) = runBlocking {
        val captures = SmsReceiver.captures(listOf(message), emptyList())
        SmsReceiver.save(context, db, captures, now)
        db.expenseDao().getAll().first().single()
    }

    /** BUG-16: an SMS that arrives hours late (phone off) was dated when it arrived. */
    @Test fun aLateSmsIsDatedWhenItWasSent() {
        val expense = capture(now = sent + 6 * 60 * 60 * 1000L)
        assertEquals("SWIGGY", expense.description)
        assertEquals(349.0, expense.amount, 0.0)
        assertEquals(sent, expense.date)
    }

    @Test fun aSentTimeInTheFutureIsCappedAtNow() {
        val now = sent - 60 * 60 * 1000L
        assertEquals(now, capture(now).date)
    }
}
