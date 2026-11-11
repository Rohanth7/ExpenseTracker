package com.example.expensetracker.data.recurring

import com.example.expensetracker.data.db.entity.Bill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

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

    private fun at(year: Int, month: Int, day: Int) = Calendar.getInstance().apply { clear(); set(year, month - 1, day, 10, 0) }.timeInMillis
    private fun bill(id: Long, dueDay: Int, autoLog: Boolean = true, enabled: Boolean = true) =
        Bill(id = id, name = "Bill $id", amount = 500.0, dueDayOfMonth = dueDay, autoLog = autoLog, isEnabled = enabled)

    /** Review of B-01: a backup from before bill months were saved restored as "nothing handled", so a bill was logged twice. */
    @Test fun `an older backup's bills were handled if their due day had come when it was made`() {
        val bills = listOf(bill(1, dueDay = 5), bill(2, dueDay = 20), bill(3, dueDay = 10))
        assertEquals(listOf(BillMonth(1, 2026, 11), BillMonth(3, 2026, 11)), BillMonth.inferred(bills, at(2026, 11, 10)))
        assertEquals(emptyList<BillMonth>(), BillMonth.inferred(bills, at(2026, 11, 3)))
    }

    @Test fun `a due day past the month's end counts on its last day`() {
        assertEquals(listOf(BillMonth(1, 2026, 11)), BillMonth.inferred(listOf(bill(1, dueDay = 31)), at(2026, 11, 30)))
        assertEquals(emptyList<BillMonth>(), BillMonth.inferred(listOf(bill(1, dueDay = 31)), at(2026, 11, 29)))
    }

    @Test fun `reminder-only and switched-off bills are never handled, and a file with no date says nothing`() {
        assertEquals(emptyList<BillMonth>(), BillMonth.inferred(listOf(bill(1, 5, autoLog = false), bill(2, 5, enabled = false)), at(2026, 11, 10)))
        assertEquals(emptyList<BillMonth>(), BillMonth.inferred(listOf(bill(1, 5)), 0))
    }
}
