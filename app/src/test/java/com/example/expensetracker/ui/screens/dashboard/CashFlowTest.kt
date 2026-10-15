package com.example.expensetracker.ui.screens.dashboard

import com.example.expensetracker.data.db.entity.Bill
import org.junit.Assert.assertEquals
import org.junit.Test

class CashFlowTest {
    private fun bill(amount: Double, dueDay: Int, autoLog: Boolean = false, enabled: Boolean = true) =
        Bill(name = "b$amount", amount = amount, dueDayOfMonth = dueDay, autoLog = autoLog, isEnabled = enabled)

    @Test fun `reminder bills count until the end of their due day`() {
        val bills = listOf(bill(100.0, dueDay = 15), bill(200.0, dueDay = 16), bill(400.0, dueDay = 20))
        assertEquals(600.0, CashFlow.billsStillDue(bills, today = 16, daysInMonth = 31), 0.0)
    }

    @Test fun `auto-logged bills count until they are logged on their due day`() {
        // Rent auto-logs on the 25th, so on the 16th it's still money that will go out this month.
        val bills = listOf(bill(22000.0, dueDay = 25, autoLog = true))
        assertEquals(22000.0, CashFlow.billsStillDue(bills, today = 16, daysInMonth = 31), 0.0)
    }

    @Test fun `an auto-logged bill due today is already in spent, so it isn't counted twice`() {
        val bills = listOf(bill(22000.0, dueDay = 16, autoLog = true), bill(999.0, dueDay = 1, autoLog = true))
        assertEquals(0.0, CashFlow.billsStillDue(bills, today = 16, daysInMonth = 31), 0.0)
    }

    @Test fun `disabled bills are ignored`() =
        assertEquals(0.0, CashFlow.billsStillDue(listOf(bill(500.0, dueDay = 28, enabled = false)), today = 16, daysInMonth = 31), 0.0)

    @Test fun `day 31 counts as the last day of a 30-day month`() =
        assertEquals(300.0, CashFlow.billsStillDue(listOf(bill(300.0, dueDay = 31)), today = 30, daysInMonth = 30), 0.0)
}
