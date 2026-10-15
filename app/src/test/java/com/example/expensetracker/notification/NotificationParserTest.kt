package com.example.expensetracker.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationParserTest {
    private val gpay = "com.google.android.apps.nbu.paisa.user"
    private val phonepe = "com.phonepe.app"

    @Test fun `WhatsApp is not monitored`() {
        assertFalse(NotificationParser.isMonitored("com.whatsapp"))
        assertTrue(NotificationParser.isMonitored(gpay))
    }

    @Test fun `payment to merchant`() {
        val tx = NotificationParser.parse("Payment successful", "₹250 paid to Swiggy", gpay)
        assertNotNull(tx)
        assertEquals(250.0, tx!!.amount, 0.001)
        assertEquals("GPay → Swiggy", tx.description)
    }

    @Test fun `phonepe sent to person`() {
        val tx = NotificationParser.parse("Sent ₹1,000", "Sent to Ravi Kumar", phonepe)
        assertEquals("PhonePe → Ravi Kumar", tx?.description)
        assertEquals(1000.0, tx!!.amount, 0.001)
    }

    @Test fun `money received is ignored`() =
        assertNull(NotificationParser.parse("₹500 received", "Ravi sent you ₹500", gpay))

    @Test fun `collect request is ignored`() =
        assertNull(NotificationParser.parse("Payment request", "Ravi requested ₹300. Pay now", gpay))

    @Test fun `failed payment is ignored`() =
        assertNull(NotificationParser.parse("Payment failed", "₹450 payment to Zomato failed", phonepe))

    @Test fun `won't is not treated as the promo word won`() {
        val tx = NotificationParser.parse("Paid ₹99", "Paid to Spotify. You won't be charged again this month", gpay)
        assertNotNull(tx)
    }
}
