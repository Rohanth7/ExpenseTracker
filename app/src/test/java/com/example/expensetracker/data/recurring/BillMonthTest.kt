package com.example.expensetracker.data.recurring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BillMonthTest {
    @Test fun `key is the one written before bill months were backed up`() {
        // November is Calendar.MONTH 10
        assertEquals("bill_applied_2026_10_5", BillMonth(billId = 5, year = 2026, month = 11).key)
    }

    @Test fun `a key reads back as the month it was written for`() {
        val january = BillMonth(billId = 12, year = 2027, month = 1)
        assertEquals(january, BillMonth.fromKey(january.key))
        assertEquals(BillMonth(5, 2026, 12), BillMonth.fromKey("bill_applied_2026_11_5"))
    }

    @Test fun `other preferences are not bill months`() {
        assertNull(BillMonth.fromKey("budget_alert_threshold"))
        assertNull(BillMonth.fromKey("bill_applied_2026_12_5"))
        assertNull(BillMonth.fromKey("bill_applied_2026_x_5"))
        assertNull(BillMonth.fromKey("bill_applied_2026_10"))
    }
}
