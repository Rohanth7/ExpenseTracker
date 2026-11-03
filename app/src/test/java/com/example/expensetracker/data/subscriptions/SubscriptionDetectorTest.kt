package com.example.expensetracker.data.subscriptions

import com.example.expensetracker.data.db.entity.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // ── Price changes (F5) ──────────────────────────────────────────

    private fun detect(ex: List<Expense>, at: Long = now) = SubscriptionDetector.detect(ex, at)

    @Test fun `a small rise is reported as a price change`() {
        val s = detect(monthly("NETFLIX", 499.0, 5, months = 6..9) + pay("NETFLIX", 549.0, 2026, 10, 5)).single()
        assertEquals(549.0, s.amount, 0.0)
        assertEquals(499.0, s.previousAmount!!, 0.0)
        assertEquals(50.0, s.priceChange!!, 0.0)
        assertEquals(date(2026, 10, 5), s.priceChangedOn)
        assertEquals(5, s.payments)
    }

    @Test fun `a big rise keeps the subscription and reports the change`() {
        val s = detect(monthly("SPOTIFY", 119.0, 3, months = 5..9) + pay("SPOTIFY", 199.0, 2026, 10, 3)).single()
        assertEquals(199.0, s.amount, 0.0)
        assertEquals(119.0, s.previousAmount!!, 0.0)
        assertEquals(6, s.payments)
        assertEquals(date(2026, 11, 3), s.nextExpected)
    }

    @Test fun `a price drop is reported too`() {
        val s = detect(monthly("HOTSTAR", 649.0, 8, months = 6..9) + pay("HOTSTAR", 499.0, 2026, 10, 8)).single()
        assertEquals(-150.0, s.priceChange!!, 0.0)
    }

    @Test fun `a steady price has no change`() =
        assertNull(detect(monthly("NETFLIX", 499.0, 5, months = 6..10)).single().previousAmount)

    @Test fun `an off-cadence purchase from the same payee is not a price change`() {
        val s = detect(monthly("APPLE", 99.0, 10, months = 6..9) + pay("APPLE", 299.0, 2026, 9, 20)).single()
        assertEquals(99.0, s.amount, 0.0)
        assertNull(s.previousAmount)
    }

    /** Charges billed in dollars move with the exchange rate every month. */
    @Test fun `amounts within 2 percent of each other are the same price`() {
        val ex = listOf(pay("OPENAI", 1690.0, 2026, 7, 12), pay("OPENAI", 1712.0, 2026, 8, 12),
            pay("OPENAI", 1698.0, 2026, 9, 12), pay("OPENAI", 1721.0, 2026, 10, 12))
        val s = detect(ex).single()
        assertEquals(1721.0, s.amount, 0.0)
        assertNull(s.previousAmount)
    }

    @Test fun `the same charge seen twice with slightly different amounts is not a price change`() {
        val ex = monthly("NETFLIX", 499.0, 5, months = 7..9) +
            pay("GPay → Netflix", 506.0, 2026, 10, 5, source = "UPI") + pay("NETFLIX", 499.0, 2026, 10, 6)
        val s = detect(ex).single()
        assertNull(s.previousAmount)
        assertEquals(4, s.payments)
    }

    @Test fun `a new price seen by SMS and notification is one change`() {
        val ex = monthly("NETFLIX", 499.0, 5, months = 6..9) +
            pay("NETFLIX", 549.0, 2026, 10, 5) + pay("GPay → Netflix", 549.0, 2026, 10, 5, source = "UPI")
        val s = detect(ex).single()
        assertEquals(499.0, s.previousAmount!!, 0.0)
        assertEquals(5, s.payments)
    }

    /** Picking the amount group with the most payments mustn't drop either price. */
    @Test fun `the new price wins even when it has as many payments as the old one`() {
        val rise = detect(monthly("SPOTIFY", 119.0, 12, months = 4..6) + monthly("SPOTIFY", 199.0, 12, months = 7..9)).single()
        assertEquals(199.0, rise.amount, 0.0)
        assertEquals(6, rise.payments)
        val drop = detect(monthly("HOTSTAR", 649.0, 12, months = 4..6) + monthly("HOTSTAR", 399.0, 12, months = 7..9)).single()
        assertEquals(399.0, drop.amount, 0.0)
        assertEquals(6, drop.payments)
        val small = detect(monthly("NETFLIX", 499.0, 12, months = 4..6) + monthly("NETFLIX", 549.0, 12, months = 7..9)).single()
        assertEquals(549.0, small.amount, 0.0)
        assertEquals(6, small.payments)
    }

    @Test fun `a change shows for two billing cycles`() {
        val ex = monthly("NETFLIX", 499.0, 5, months = 5..8) + monthly("NETFLIX", 549.0, 5, months = 9..10)
        val shown = detect(ex).single()
        assertEquals(499.0, shown.previousAmount!!, 0.0)
        assertEquals(date(2026, 9, 5), shown.priceChangedOn)
        val later = detect(ex + pay("NETFLIX", 549.0, 2026, 11, 5), at = date(2026, 11, 6)).single()
        assertEquals(549.0, later.amount, 0.0)
        assertNull(later.previousAmount)
        assertNull(later.priceChangedOn)
    }

    @Test fun `a yearly rise is detected`() {
        val renewals = listOf(pay("Domain renewal", 1499.0, 2023, 10, 10), pay("Domain renewal", 1499.0, 2024, 10, 10))
        val big = detect(renewals + pay("Domain renewal", 1799.0, 2025, 10, 10), at = date(2025, 10, 16)).single()
        assertEquals(Cadence.YEARLY, big.cadence)
        assertEquals(1799.0, big.amount, 0.0)
        assertEquals(1499.0, big.previousAmount!!, 0.0)
        val small = detect(renewals + pay("Domain renewal", 1599.0, 2025, 10, 10), at = date(2025, 10, 16)).single()
        assertEquals(1599.0, small.amount, 0.0)
        assertEquals(1499.0, small.previousAmount!!, 0.0)
    }

    /** Yearly needs 2 matching payments, so one payment at the old price isn't a subscription with a change. */
    @Test fun `a yearly price paid only once before is not a change`() {
        assertTrue(detect(listOf(pay("Flipkart", 899.0, 2024, 11, 1), pay("Flipkart", 950.0, 2025, 11, 1))).isEmpty())
        val ex = listOf(pay("Domain", 1499.0, 2023, 11, 1), pay("Domain", 1599.0, 2024, 11, 1), pay("Domain", 1599.0, 2025, 11, 1))
        val s = detect(ex).single()
        assertEquals(1599.0, s.amount, 0.0)
        assertEquals(2, s.payments)
        assertNull(s.previousAmount)
    }

    /** One month at another price isn't a new price; going back to the old one isn't alerted either. */
    @Test fun `a rise that goes back down the next month is not a change`() {
        val small = monthly("NETFLIX", 499.0, 5, months = 5..8) + pay("NETFLIX", 549.0, 2026, 9, 5) + pay("NETFLIX", 499.0, 2026, 10, 5)
        val s = detect(small).single()
        assertEquals(499.0, s.amount, 0.0)
        assertNull(s.previousAmount)
        assertEquals(6, s.payments)
        val big = monthly("SPOTIFY", 119.0, 3, months = 5..8) + pay("SPOTIFY", 199.0, 2026, 9, 3)
        assertEquals(119.0, detect(big, at = date(2026, 9, 16)).single().previousAmount!!, 0.0)
        val back = detect(big + pay("SPOTIFY", 119.0, 2026, 10, 3)).single()
        assertEquals(119.0, back.amount, 0.0)
        assertNull(back.previousAmount)
        assertEquals(6, back.payments)
    }

    @Test fun `a missed payment restarts the count instead of hiding the subscription for good`() {
        val before = monthly("Gym", 1500.0, 5, months = 1..4)
        assertTrue(detect(before + monthly("Gym", 1500.0, 5, months = 6..7), at = date(2026, 7, 16)).isEmpty())
        val s = detect(before + monthly("Gym", 1500.0, 5, months = 6..8), at = date(2026, 8, 16)).single()
        assertEquals(3, s.payments)
        assertEquals(date(2026, 8, 5), s.lastPaid)
    }

    @Test fun `a change followed by a missed month`() {
        val ex = monthly("NETFLIX", 499.0, 5, months = 5..8) + pay("NETFLIX", 549.0, 2026, 9, 5)
        // October wasn't paid: no longer listed once it's past due
        assertTrue(detect(ex).isEmpty())
        // The next payment after the gap starts a new run, not a new change
        val nov = ex + pay("NETFLIX", 549.0, 2026, 11, 5)
        assertTrue(detect(nov, at = date(2026, 11, 16)).isEmpty())
        val back = detect(nov + pay("NETFLIX", 549.0, 2026, 12, 5) + pay("NETFLIX", 549.0, 2027, 1, 5), at = date(2027, 1, 16)).single()
        assertEquals(549.0, back.amount, 0.0)
        assertEquals(3, back.payments)
        assertNull(back.previousAmount)
    }

    /** At a shop you also buy from, an order around the renewal date could be taken for a new price. */
    @Test fun `an order from a payee you also shop at is not a price change`() {
        val prime = monthly("Amazon", 299.0, 3)
        val orders = listOf(pay("Amazon", 1499.0, 2026, 7, 19), pay("Amazon", 2899.0, 2026, 9, 2), pay("Amazon", 549.0, 2026, 10, 9))
        val s = detect(prime + orders + pay("Amazon", 450.0, 2026, 11, 1), at = date(2026, 11, 2)).single()
        assertEquals(299.0, s.amount, 0.0)
        assertNull(s.previousAmount)
        // Without other orders the same charge is a price change
        assertEquals(299.0, detect(prime + pay("Amazon", 450.0, 2026, 11, 1), at = date(2026, 11, 2)).single().previousAmount!!, 0.0)
    }

    @Test fun `a jump to more than double is another purchase, not a price change`() {
        val ex = monthly("Hotstar", 199.0, 5, months = 6..9) + pay("Hotstar", 649.0, 2026, 10, 5)
        val s = detect(ex, at = date(2026, 10, 6)).single()
        assertEquals(199.0, s.amount, 0.0)
        assertNull(s.previousAmount)
        // Like before price changes were tracked: listed again once 649 has been paid 3 times
        assertTrue(detect(ex).isEmpty())
    }

    @Test fun `the old price must have been paid often enough to be a subscription`() {
        val s = detect(monthly("NETFLIX", 499.0, 5, months = 8..9) + pay("NETFLIX", 549.0, 2026, 10, 5)).single()
        assertEquals(549.0, s.amount, 0.0)
        assertNull(s.previousAmount)
    }

    @Test fun `amounts that aren't numbers are ignored`() {
        val s = detect(monthly("NETFLIX", 499.0, 5) + pay("NETFLIX", Double.POSITIVE_INFINITY, 2026, 9, 20) + pay("NETFLIX", Double.NaN, 2026, 8, 20)).single()
        assertEquals(499.0, s.amount, 0.0)
        assertEquals(4, s.payments)
    }
}
