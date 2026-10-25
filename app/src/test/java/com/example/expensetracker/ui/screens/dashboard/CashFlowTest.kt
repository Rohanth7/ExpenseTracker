package com.example.expensetracker.ui.screens.dashboard

import com.example.expensetracker.data.db.entity.Bill
import com.example.expensetracker.data.db.entity.Loan
import com.example.expensetracker.data.period.BudgetPeriod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class CashFlowTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, 0) }.timeInMillis
    private fun bill(amount: Double, dueDay: Int, autoLog: Boolean = false, enabled: Boolean = true) =
        Bill(name = "b$amount", amount = amount, dueDayOfMonth = dueDay, autoLog = autoLog, isEnabled = enabled)
    private fun still(bills: List<Bill>, now: Long, startDay: Int = 1) = CashFlow.billsStillDue(bills, now, BudgetPeriod.current(now, startDay))

    @Test fun `reminder bills count until the end of their due day`() =
        assertEquals(600.0, still(listOf(bill(100.0, 15), bill(200.0, 16), bill(400.0, 20)), at(2026, 10, 16)), 0.0)
    @Test fun `auto-logged bills count until they are logged on their due day`() =
        assertEquals(22000.0, still(listOf(bill(22000.0, 25, autoLog = true)), at(2026, 10, 16)), 0.0)
    @Test fun `an auto-logged bill due today is already in spent`() =
        assertEquals(0.0, still(listOf(bill(22000.0, 16, autoLog = true), bill(999.0, 1, autoLog = true)), at(2026, 10, 16)), 0.0)
    @Test fun `disabled bills are ignored`() = assertEquals(0.0, still(listOf(bill(500.0, 28, enabled = false)), at(2026, 10, 16)), 0.0)
    @Test fun `day 31 is the last day of a 30-day month`() = assertEquals(300.0, still(listOf(bill(300.0, 31)), at(2026, 11, 30)), 0.0)
    @Test fun `in a 25th-to-24th cycle, a bill due on the 5th of next month still counts`() =
        assertEquals(999.0, still(listOf(bill(999.0, 5)), at(2026, 10, 26), startDay = 25), 0.0)   // cycle 25 Oct – 24 Nov
    @Test fun `in a 25th-to-24th cycle, a bill due on the 26th this month already passed`() =
        assertEquals(0.0, still(listOf(bill(999.0, 26)), at(2026, 10, 27), startDay = 25), 0.0)

    private fun loan(emi: Double, dueDay: Int, startDate: Long = at(2025, 1, 1)) =
        Loan(name = "l$emi", totalAmount = emi * 24, monthlyEmi = emi, tenureMonths = 24, startDate = startDate, dueDayOfMonth = dueDay)
    private fun emi(loans: List<Loan>, now: Long, startDay: Int = 1) = CashFlow.emiStillDue(loans, now, BudgetPeriod.current(now, startDay))

    @Test fun `EMIs count through their due day, like reminder bills`() =
        assertEquals(10000.0, emi(listOf(loan(5000.0, 15), loan(10000.0, 16), loan(7000.0, 10)), at(2026, 10, 16)), 0.0)  // the 15th and 10th have passed
    @Test fun `a loan that starts after this cycle has no EMI yet`() =
        assertEquals(0.0, emi(listOf(loan(5000.0, 20, startDate = at(2026, 11, 1))), at(2026, 10, 16)), 0.0)
    @Test fun `in a 25th-to-24th cycle, an EMI due on the 5th of next month still counts`() =
        assertEquals(5000.0, emi(listOf(loan(5000.0, 5)), at(2026, 10, 26), startDay = 25), 0.0)
    @Test fun `no EMI is counted before the loan starts`() =  // cycle 10 Oct – 9 Nov; 1 Nov is before the 3 Nov start
        assertEquals(0.0, emi(listOf(loan(5000.0, 1, startDate = at(2026, 11, 3))), at(2026, 10, 26), startDay = 10), 0.0)
}
