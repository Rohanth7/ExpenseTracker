package com.example.expensetracker.ui.screens.dashboard

import com.example.expensetracker.data.db.entity.Bill
import com.example.expensetracker.data.db.entity.Loan
import com.example.expensetracker.data.period.BudgetPeriod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ComingUpTest {
    private fun at(month: Int, day: Int, hour: Int = 0) = Calendar.getInstance().apply { clear(); set(2026, month - 1, day, hour, 0) }.timeInMillis
    private val now = at(11, 12, 10)
    private val cycle = BudgetPeriod.current(now, 1)

    private fun bill(name: String, daysUntilDue: Int) = UpcomingBill(Bill(name = name, amount = 1900.0, dueDayOfMonth = 12 + daysUntilDue), daysUntilDue)
    private fun loan(name: String, dueDay: Int, start: Long = at(9, 1), tenure: Int = 36) =
        LoanStatus(Loan(name = name, totalAmount = 450000.0, monthlyEmi = 12500.0, tenureMonths = tenure, startDate = start, dueDayOfMonth = dueDay), 0, tenure, 0f)

    @Test fun billsAndEmisAreMergedSoonestFirst() {
        val items = ComingUp.of(listOf(bill("Electricity", 3), bill("Internet", 6)), listOf(loan("Car loan", 16)), now, cycle)
        assertEquals(listOf("Electricity", "Car loan EMI", "Internet"), items.map { it.name })
        assertEquals(listOf(at(11, 15), at(11, 16), at(11, 18)), items.map { it.dueDate })
    }

    @Test fun anEmiAlreadyPaidThisMonthIsNotComingUp() {
        assertEquals(emptyList<ComingUpItem>(), ComingUp.of(emptyList(), listOf(loan("Car loan", 5)), now, cycle))
    }

    @Test fun aFinishedLoanOrOneNotStartedHasNoEmiComingUp() {
        val finished = loan("Phone", 20, start = at(8, 1), tenure = 3)
        val notStarted = loan("Bike", 20, start = at(12, 1))
        assertEquals(emptyList<ComingUpItem>(), ComingUp.of(emptyList(), listOf(finished, notStarted), now, cycle))
    }

    @Test fun anEmiSaysWhichInstalmentItIs() {
        // September, October, then November's is the third
        val item = ComingUp.of(emptyList(), listOf(loan("Car loan", 20)), now, cycle).single()
        assertEquals("20 Nov · 3 of 36", ComingUp.subtitle(item))
        assertEquals(12500.0, item.amount, 0.0)
    }

    @Test fun nearDatesAreRelativeAndLaterOnesAreDates() {
        fun text(days: Int) = ComingUp.subtitle(ComingUp.of(listOf(bill("Rent", days)), emptyList(), now, cycle).single())
        assertEquals("Yesterday", text(-1))
        assertEquals("Today", text(0))
        assertEquals("Tomorrow", text(1))
        assertEquals("in 3 days", text(3))
        assertEquals("16 Nov", text(4))
    }
}
