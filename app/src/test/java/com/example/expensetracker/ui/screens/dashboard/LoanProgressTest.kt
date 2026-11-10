package com.example.expensetracker.ui.screens.dashboard

import com.example.expensetracker.data.db.entity.Loan
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class LoanProgressTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, 0) }.timeInMillis
    /** As the Add/Edit Loan dialog stores it: the 1st of the first EMI's month. */
    private fun loan(firstEmi: Pair<Int, Int>, tenure: Int, dueDay: Int) =
        Loan(name = "Loan", totalAmount = 0.0, monthlyEmi = 5000.0, tenureMonths = tenure, startDate = at(firstEmi.first, firstEmi.second, 1, 0), dueDayOfMonth = dueDay)

    /** BUG-08: the EMI in the first month wasn't counted, so a finished loan showed 11 of 12 and a phantom EMI. */
    @Test fun `a 12-month loan is finished once its 12th EMI is due`() {
        val l = loan(2025 to 11, tenure = 12, dueDay = 6)
        assertEquals(12, LoanProgress.paid(l, at(2026, 11, 5)))
        assertEquals(0, LoanProgress.remaining(l, at(2026, 11, 5)))
        assertEquals(11, LoanProgress.paid(l, at(2026, 10, 5)))
        assertEquals(12, LoanProgress.paid(l, at(2026, 10, 6)))
    }

    /** BUG-08: due on the 31st, the count never moved in 30-day months or February. */
    @Test fun `a 31st due day counts on the last day of shorter months`() {
        val l = loan(2026 to 8, tenure = 12, dueDay = 31)
        assertEquals(4, LoanProgress.paid(l, at(2026, 11, 30)))   // 31 Aug, 30 Sep, 31 Oct, 30 Nov
        assertEquals(3, LoanProgress.paid(l, at(2026, 11, 29)))
        assertEquals(7, LoanProgress.paid(l, at(2027, 2, 28)))
    }

    @Test fun `the first month's EMI counts once its day comes`() {
        val l = loan(2026 to 11, tenure = 24, dueDay = 5)
        assertEquals(0, LoanProgress.paid(l, at(2026, 11, 4)))
        assertEquals(1, LoanProgress.paid(l, at(2026, 11, 5)))
        assertEquals(0, LoanProgress.paid(l, at(2026, 10, 20)))   // before the loan starts
    }

    @Test fun `the last EMI's due date is clamped too`() =
        assertEquals(at(2027, 2, 28, 0), LoanProgress.lastDueDate(loan(2026 to 3, tenure = 12, dueDay = 31)))
}
