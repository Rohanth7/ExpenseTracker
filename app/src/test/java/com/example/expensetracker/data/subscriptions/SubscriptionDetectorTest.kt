package com.example.expensetracker.data.subscriptions

import com.example.expensetracker.data.db.entity.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SubscriptionDetectorTest {
    private fun date(y: Int, m: Int, d: Int): Long =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d, 10, 0) }.timeInMillis

    private val now = date(2026, 10, 16)
    private fun pay(desc: String, amount: Double, y: Int, m: Int, d: Int, source: String = "SMS", cat: Long = 4) =
        Expense(amount = amount, description = desc, date = date(y, m, d), source = source, categoryId = cat)

    private fun monthly(desc: String, amount: Double, day: Int, months: IntRange = 7..10) =
        months.map { pay(desc, amount, 2026, it, day) }

    @Test fun `monthly subscription is found with its next date and yearly cost`() {
        val subs = SubscriptionDetector.detect(monthly("netflix.upi@hdfcbank", 649.0, 5), now)
        assertEquals(1, subs.size)
        val s = subs[0]
        assertEquals(Cadence.MONTHLY, s.cadence)
        assertEquals(649.0 * 12, s.yearlyCost, 0.01)
        assertEquals(date(2026, 11, 5), s.nextExpected)
        assertEquals(4, s.payments)
    }

    @Test fun `GPay and SMS names for the same payee are one subscription`() {
        val ex = listOf(pay("GPay → Spotify", 119.0, 2026, 8, 12), pay("SPOTIFY", 119.0, 2026, 9, 12), pay("Spotify", 119.0, 2026, 10, 12))
        assertEquals(listOf("spotify"), SubscriptionDetector.detect(ex, now).map { it.key })
    }

    @Test fun `a small price rise doesn't break it, and the latest price is shown`() {
        val ex = listOf(pay("YouTube", 129.0, 2026, 7, 20), pay("YouTube", 129.0, 2026, 8, 20), pay("YouTube", 139.0, 2026, 9, 20))
        assertEquals(139.0, SubscriptionDetector.detect(ex, now).single().amount, 0.0)
    }

    @Test fun `frequent orders from the same shop are not a subscription`() {
        val ex = (1..30 step 3).map { pay("Swiggy", 200.0 + it * 13, 2026, 9, it) } +
            (1..15 step 4).map { pay("Swiggy", 300.0, 2026, 10, it) }
        assertTrue(SubscriptionDetector.detect(ex, now).isEmpty())
    }

    @Test fun `a subscription inside other purchases from the same payee is still found`() {
        val prime = monthly("Amazon", 299.0, 3)
        val orders = listOf(pay("Amazon", 1499.0, 2026, 7, 19), pay("Amazon", 2899.0, 2026, 9, 2), pay("Amazon", 549.0, 2026, 10, 9))
        val s = SubscriptionDetector.detect(prime + orders, now).single()
        assertEquals(299.0, s.amount, 0.0)
        assertEquals(4, s.payments)
    }

    @Test fun `two monthly payments aren't enough`() =
        assertTrue(SubscriptionDetector.detect(monthly("Gym", 1500.0, 1, 9..10), now).isEmpty())

    @Test fun `stopped subscriptions are not listed`() =
        assertTrue(SubscriptionDetector.detect(monthly("Hotstar", 299.0, 1, 3..6), now).isEmpty())

    @Test fun `the same charge seen by SMS and notification counts once`() {
        val ex = monthly("Netflix", 649.0, 5) + pay("GPay → Netflix", 649.0, 2026, 10, 5, source = "UPI")
        assertEquals(4, SubscriptionDetector.detect(ex, now).single().payments)
    }

    @Test fun `weekly and yearly cadences`() {
        val weekly = (0..4).map { pay("Milk", 420.0, 2026, 9, 18 + it * 7) }
        assertEquals(Cadence.WEEKLY, SubscriptionDetector.detect(weekly, now).single().cadence)
        val yearly = listOf(pay("Domain renewal", 899.0, 2024, 11, 1), pay("Domain renewal", 899.0, 2025, 11, 1))
        assertEquals(Cadence.YEARLY, SubscriptionDetector.detect(yearly, now).single().cadence)
    }

    @Test fun `yearly needs matching amounts`() {
        val ex = listOf(pay("Flipkart", 899.0, 2024, 11, 1), pay("Flipkart", 1499.0, 2025, 11, 1))
        assertTrue(SubscriptionDetector.detect(ex, now).isEmpty())
    }

    @Test fun `bills, auto-logged bills, dismissed payees and fallback names are left out`() {
        val ex = monthly("Rent", 22000.0, 1, 7..10).map { it.copy(source = "Recurring") } +
            monthly("Airtel", 999.0, 8) + monthly("Cult Fit", 1500.0, 2) + monthly("HDFC Bank debit", 5000.0, 7)
        val subs = SubscriptionDetector.detect(ex, now, dismissedKeys = setOf("cult fit"), billNames = listOf("Airtel Broadband"))
        assertTrue("got ${subs.map { it.key }}", subs.isEmpty())
    }

    @Test fun `most expensive per year comes first`() {
        val ex = monthly("Spotify", 119.0, 12) + monthly("Netflix", 649.0, 5) + (0..4).map { pay("Milk", 420.0, 2026, 9, 18 + it * 7) }
        assertEquals(listOf("milk", "netflix", "spotify"), SubscriptionDetector.detect(ex, now).map { it.key })
    }
}
