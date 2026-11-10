package com.example.expensetracker.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.example.expensetracker.sms.CustomRules
import com.example.expensetracker.sms.ParsingRule
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

    // BUG-21: reward and tip lines, and "received" said of the payee, dropped completed payments.
    @Test fun `a completed payment with a reward line is kept`() {
        val tx = NotificationParser.parse("Payment successful", "Paid ₹250 to Swiggy. You won a scratch card!", gpay)
        assertEquals(250.0, tx!!.amount, 0.001)
        assertEquals("GPay → Swiggy", tx.description)
    }

    @Test fun `the payee receiving the money is not a credit`() {
        val tx = NotificationParser.parse("₹500 paid to Ramesh", "Ramesh received your money", phonepe)
        assertEquals(500.0, tx!!.amount, 0.001)
        assertEquals("PhonePe → Ramesh", tx.description)
    }

    @Test fun `an app tip under a completed payment is not a reminder`() {
        val tx = NotificationParser.parse("Paid ₹120 to Chai Point", "Pay bills before the due date to avoid late fees", gpay)
        assertEquals("GPay → Chai Point", tx?.description)
    }

    @Test fun `money coming to the user is still a credit`() {
        assertNull(NotificationParser.parse("Received ₹500 from Ravi", "Paid via UPI", gpay))
        assertNull(NotificationParser.parse("You received ₹500", "Ravi paid ₹500", phonepe))
        assertNull(NotificationParser.parse("₹500 paid to you", "Ravi paid ₹500 to you", gpay))
    }

    @Test fun `reminders, requests and unfinished payments are still ignored`() {
        assertNull(NotificationParser.parse("Electricity bill due", "Pay ₹1,200 before the due date", gpay))
        assertNull(NotificationParser.parse("Paid ₹300 to Zomato", "Payment pending at your bank", phonepe))
        assertNull(NotificationParser.parse("You could win ₹500", "Pay ₹50 to a friend and win a scratch card", gpay))
    }

    // BUG-09: message rules were only applied to SMS.
    @Test fun `a message rule applies to payment-app notifications`() {
        val text = "Paid ₹99 to Google Play"
        val ignore = (CustomRules.build(text, ParsingRule.Kind.IGNORE, id = "r1", now = 1) as CustomRules.BuildResult.Ok).rule
        assertNotNull(TransactionNotificationListener.transaction(emptyList(), "", text, gpay))
        assertNull(TransactionNotificationListener.transaction(listOf(ignore), "", text, gpay))
        // Taught from the whole notification as shown, title included
        val pasted = (CustomRules.build("Payment successful\nPaid ₹99 to Google Play", ParsingRule.Kind.IGNORE, id = "r2", now = 1) as CustomRules.BuildResult.Ok).rule
        assertNull(TransactionNotificationListener.transaction(listOf(pasted), "Payment successful", text, gpay))
    }
}
