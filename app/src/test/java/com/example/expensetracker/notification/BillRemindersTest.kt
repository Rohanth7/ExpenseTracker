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

    /** Runs the daily check on each of [days], remembering what was sent like the worker does; returns "day: days until due". */
    private fun run(b: Bill, vararg days: Long): List<String> {
        var sent = BillReminderHelper.Sent()
        return days.flatMap { now ->
            BillReminderHelper.remindersToSend(listOf(b), now) { sent }.map { r ->
                sent = r.sent
                "${Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_MONTH)}: ${r.daysUntilDue}"
            }
        }
    }

    /** B-20: a bill was reminded about every day of its window (4 notifications for "3 days before"). */
    @Test fun `a bill is reminded about on the first day of its window and on its due day`() {
        val rent = bill(15, remind = 3)
        assertEquals(listOf("12: 3", "15: 0"), run(rent, at(2026, 11, 11), at(2026, 11, 12), at(2026, 11, 13), at(2026, 11, 14), at(2026, 11, 15), at(2026, 11, 16)))
    }

    @Test fun `a check that runs twice in a day reminds once`() {
        val rent = bill(15, remind = 3)
        assertEquals(listOf("12: 3", "15: 0"), run(rent, at(2026, 11, 12, 9), at(2026, 11, 12, 21), at(2026, 11, 15, 9), at(2026, 11, 15, 21)))
    }

    @Test fun `if the first day was missed, the first check inside the window reminds`() {
        val rent = bill(15, remind = 3)
        assertEquals(listOf("14: 1", "15: 0"), run(rent, at(2026, 11, 14), at(2026, 11, 15)))
        // Only the due day was caught: one reminder, not two
        assertEquals(listOf("15: 0"), run(rent, at(2026, 11, 15)))
    }

    @Test fun `remind 0 days before is the due day alone, and next month reminds again`() {
        assertEquals(listOf("15: 0"), run(bill(15, remind = 0), at(2026, 11, 14), at(2026, 11, 15)))
        assertEquals(listOf("12: 3", "15: 0", "12: 3", "15: 0"),
            run(bill(15, remind = 3), at(2026, 11, 12), at(2026, 11, 13), at(2026, 11, 15), at(2026, 12, 12), at(2026, 12, 14), at(2026, 12, 15)))
    }
}
