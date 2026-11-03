package com.example.expensetracker.notification

import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.subscriptions.Cadence
import com.example.expensetracker.data.subscriptions.Subscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Calendar

class PriceChangeAlertsTest {
    private fun date(y: Int, m: Int, d: Int, h: Int = 10): Long =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, 0) }.timeInMillis

    private fun sub(key: String, old: Double, new: Double, cadence: Cadence = Cadence.MONTHLY, changedOn: Long = date(2026, 10, 5)) =
        Subscription(key = key, name = key.uppercase(), amount = new, cadence = cadence, lastPaid = changedOn,
            nextExpected = changedOn, payments = 5, categoryId = 4, previousAmount = old, priceChangedOn = changedOn)

    @Test fun `a rise says how much more a year`() =
        assertEquals("Netflix price went up" to "₹499 → ₹549 a month · ₹600 more a year", PriceChangeAlerts.text(sub("netflix", 499.0, 549.0)))

    @Test fun `a drop says how much less a year`() =
        assertEquals("Hotstar price went down" to "₹649 → ₹499 a month · ₹1,800 less a year", PriceChangeAlerts.text(sub("hotstar", 649.0, 499.0)))

    @Test fun `a yearly price doesn't repeat the difference`() =
        assertEquals("₹1,499 → ₹1,799 a year", PriceChangeAlerts.text(sub("domain", 1499.0, 1799.0, Cadence.YEARLY)).second)

    @Test fun `weekly and quarterly wording`() {
        assertEquals("₹420 → ₹450 a week · ₹1,560 more a year", PriceChangeAlerts.text(sub("milk", 420.0, 450.0, Cadence.WEEKLY)).second)
        assertEquals("₹999 → ₹1,199 every 3 months · ₹800 more a year", PriceChangeAlerts.text(sub("jio", 999.0, 1199.0, Cadence.QUARTERLY)).second)
    }

    @Test fun `amounts use Indian grouping in whole rupees, like the cards`() {
        // Android also groups lakhs (₹2,40,000); the JVM doesn't
        assertEquals("₹10,000 → ₹12,000 a month · ₹24,000 more a year", PriceChangeAlerts.text(sub("rent", 10000.0, 12000.0)).second)
        // Paise are rounded off, and the yearly difference adds up from the prices shown
        assertEquals("₹1,690 → ₹1,745 a month · ₹660 more a year", PriceChangeAlerts.text(sub("openai", 1690.4, 1745.1)).second)
    }

    @Test fun `names are the card's, title-cased when all capitals`() {
        assertEquals("Amazon Prime price went up", PriceChangeAlerts.text(sub("amazon prime", 299.0, 349.0)).first)
        assertEquals("*** price went up", PriceChangeAlerts.text(sub("***", 299.0, 349.0)).first)
        assertEquals("YouTube Premium price went up", PriceChangeAlerts.text(sub("youtube premium", 129.0, 149.0).copy(name = "YouTube Premium")).first)
    }

    /** SQLite's LIKE ignores case for A–Z only, so the word searched for is plain letters and digits. */
    @Test fun `the payee is searched for by its longest plain word`() {
        assertEquals("crunchyroll", PriceChangeAlerts.searchWord("crunchyroll anime"))
        assertEquals("apple", PriceChangeAlerts.searchWord("apple.com/bill"))
        assertEquals("coffee", PriceChangeAlerts.searchWord("café coffee day"))
        assertEquals("cole", PriceChangeAlerts.searchWord("école"))
        // No upper or lower case to miss
        assertEquals("नेटफ्लिक्स", PriceChangeAlerts.searchWord("नेटफ्लिक्स"))
    }

    /** A capture saved as "UPI" and renamed to the payee is that payee's new price. */
    @Test fun `an edit is checked when the payee or amount changed`() {
        val upi = Expense(id = 7, amount = 549.0, description = "UPI", date = date(2026, 10, 5), source = "SMS")
        assertEquals(true, PriceChangeAlerts.worthCheckingAfterEdit(upi, upi.copy(description = "Netflix")))
        assertEquals(true, PriceChangeAlerts.worthCheckingAfterEdit(upi.copy(description = "Netflix", amount = 499.0), upi.copy(description = "Netflix")))
        assertEquals(false, PriceChangeAlerts.worthCheckingAfterEdit(upi.copy(description = "NETFLIX"), upi.copy(description = "GPay → Netflix", tags = "tv", categoryId = 3)))
    }

    @Test fun `one alert key per payee`() {
        val oct = sub("netflix", 499.0, 549.0, changedOn = date(2026, 10, 5))
        assertEquals(PriceChangeAlerts.alertKey(oct), PriceChangeAlerts.alertKey(sub("netflix", 549.0, 599.0, changedOn = date(2027, 3, 5))))
        assertNotEquals(PriceChangeAlerts.alertKey(oct), PriceChangeAlerts.alertKey(sub("hotstar", 499.0, 549.0, changedOn = date(2026, 10, 5))))
    }

    /** The same charge saved again a little earlier (by hand, or a late SMS) moves the change's date; it's still the same change. */
    @Test fun `a change is alerted once even if its date moves a little`() {
        val alerted = date(2026, 10, 5, 10)
        assertEquals(true, PriceChangeAlerts.alreadyAlerted(alerted, alerted))
        assertEquals(true, PriceChangeAlerts.alreadyAlerted(alerted, alerted - 7 * 60_000))
        assertEquals(false, PriceChangeAlerts.alreadyAlerted(alerted, date(2027, 3, 5)))
        assertEquals(false, PriceChangeAlerts.alreadyAlerted(null, alerted))
    }

    // ── Which capture alerts ────────────────────────────────────────

    private val change = sub("netflix", 499.0, 549.0, changedOn = date(2026, 10, 5))
    private fun captured(desc: String, at: Long) = Expense(amount = 549.0, description = desc, date = at, source = "SMS")

    @Test fun `the payment that changed the price alerts`() {
        val now = date(2026, 10, 5, 11)
        assertSame(change, PriceChangeAlerts.changeToAlert(listOf(change), captured("NETFLIX", date(2026, 10, 5)), now))
        // The same charge seen again by a notification the next day: same change, so the same key
        assertSame(change, PriceChangeAlerts.changeToAlert(listOf(change), captured("GPay → Netflix", date(2026, 10, 6)), date(2026, 10, 6, 11)))
    }

    /** Entering last month's payment today mustn't alert about a change from weeks ago. */
    @Test fun `a back-dated entry doesn't alert`() =
        assertNull(PriceChangeAlerts.changeToAlert(listOf(change), captured("Netflix", date(2026, 10, 5)), date(2026, 10, 28)))

    @Test fun `later payments at the new price don't alert`() =
        assertNull(PriceChangeAlerts.changeToAlert(listOf(change), captured("NETFLIX", date(2026, 11, 5)), date(2026, 11, 5, 11)))

    @Test fun `other payees and steady prices don't alert`() {
        val now = date(2026, 10, 5, 11)
        assertNull(PriceChangeAlerts.changeToAlert(listOf(change), captured("Spotify", date(2026, 10, 5)), now))
        val steady = change.copy(previousAmount = null, priceChangedOn = null)
        assertNull(PriceChangeAlerts.changeToAlert(listOf(steady), captured("NETFLIX", date(2026, 10, 5)), now))
    }
}
