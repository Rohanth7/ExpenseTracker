package com.example.expensetracker.notification

import com.example.expensetracker.data.db.entity.Bill
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class BillRemindersTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 9) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, 0) }.timeInMillis
    private fun bill(dueDay: Int, remind: Int = 3, autoLog: Boolean = false, enabled: Boolean = true) =
        Bill(id = dueDay.toLong(), name = "Bill $dueDay", amount = 2400.0, dueDayOfMonth = dueDay, reminderDays = remind, autoLog = autoLog, isEnabled = enabled)
    private fun days(b: Bill, now: Long) = BillReminderHelper.dueReminders(listOf(b), now).singleOrNull()?.second

    /** BUG-07: a bill due on the 31st said "due tomorrow" on 30 Nov; the Overview says it's due that day. */
    @Test fun `a 31st bill is due on the last day of a shorter month`() {
        assertEquals(0, days(bill(31), at(2026, 11, 30)))
        assertEquals(3, days(bill(31), at(2026, 11, 27)))
        assertEquals(0, days(bill(31), at(2027, 2, 28)))
        assertEquals(1, days(bill(30), at(2027, 2, 27)))
    }

    @Test fun `early next month wraps from the end of this one`() {
        assertEquals(1, days(bill(1), at(2026, 12, 31)))
        assertEquals(2, days(bill(1), at(2026, 11, 29)))
        assertEquals(null, days(bill(5), at(2026, 11, 30)))   // 5 days away, reminder 3 days before
    }

    @Test fun `auto-logged and switched-off bills get no reminder`() =
        assertEquals(emptyList<Pair<Bill, Int>>(), BillReminderHelper.dueReminders(listOf(bill(30, autoLog = true), bill(30, enabled = false)), at(2026, 11, 30)))
}
