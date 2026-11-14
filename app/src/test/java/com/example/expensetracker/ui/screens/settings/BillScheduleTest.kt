package com.example.expensetracker.ui.screens.settings

import com.example.expensetracker.data.db.entity.Bill
import org.junit.Assert.assertEquals
import org.junit.Test

class BillScheduleTest {
    private fun bill(day: Int, remind: Int = 3, autoLog: Boolean = false) =
        Bill(name = "Rent", amount = 1.0, dueDayOfMonth = day, reminderDays = remind, autoLog = autoLog)

    @Test fun aReminderBillSaysWhenItRemindsYou() {
        assertEquals("15th · remind 2 days before", billSchedule(bill(15, remind = 2)))
        assertEquals("1st · remind 1 day before", billSchedule(bill(1, remind = 1)))
        assertEquals("22nd · remind 0 days before", billSchedule(bill(22, remind = 0)))
    }

    @Test fun anAutoLogBillSaysItIsLogged() {
        assertEquals("3rd · logged automatically", billSchedule(bill(3, autoLog = true)))
    }
}
