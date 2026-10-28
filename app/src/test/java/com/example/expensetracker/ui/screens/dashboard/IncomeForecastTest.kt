package com.example.expensetracker.ui.screens.dashboard

import com.example.expensetracker.data.db.entity.Income
import com.example.expensetracker.data.db.entity.RecurringIncome
import com.example.expensetracker.data.db.entity.RecurringIncomeMonth
import com.example.expensetracker.data.period.BudgetPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class IncomeForecastTest {
    @Test fun `uses logged income when there is some`() = assertEquals(52000.0, IncomeForecast.forCycle(52000.0, 0.0, 40000.0), 0.0)
    @Test fun `adds recurring income still to come`() = assertEquals(55000.0, IncomeForecast.forCycle(5000.0, 50000.0, 40000.0), 0.0)
    @Test fun `recurring income still to come is enough on its own`() = assertEquals(50000.0, IncomeForecast.forCycle(0.0, 50000.0, 40000.0), 0.0)
    @Test fun `falls back to the expected figure`() = assertEquals(40000.0, IncomeForecast.forCycle(0.0, 0.0, 40000.0), 0.0)
    @Test fun `nothing set means zero`() = assertEquals(0.0, IncomeForecast.forCycle(0.0, 0.0, 0.0), 0.0)

    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, 0) }.timeInMillis
    private fun salary(amount: Double, day: Int, enabled: Boolean = true) =
        RecurringIncome(name = "s$amount", amount = amount, dayOfMonth = day, isEnabled = enabled)
    private fun still(recurring: List<RecurringIncome>, now: Long, startDay: Int = 1, offset: Int = 0, done: List<RecurringIncomeMonth> = emptyList()) =
        IncomeForecast.recurringStillDue(recurring, now, BudgetPeriod.forOffset(now, startDay, offset), months = done)
    /** RecurringManager's record that [r]'s month ([month] 1–12) is handled. */
    private fun done(r: RecurringIncome, year: Int, month: Int, byHand: Boolean = false) = RecurringIncomeMonth(r.id, year, month, byHand)

    @Test fun `a salary later this month is still to come`() = assertEquals(50000.0, still(listOf(salary(50000.0, 25)), at(2026, 10, 16)), 0.0)
    @Test fun `a salary earlier this month that was logged isn't`() {
        val s = salary(50000.0, 1).copy(id = 7)
        assertEquals(0.0, still(listOf(s), at(2026, 10, 16), done = listOf(done(s, 2026, 10))), 0.0)
    }
    // RecurringManager logs it when the app opens; until then the widget (or an app left open
    // overnight) must still count payday's salary, and one whose day passed while the app was closed.
    @Test fun `today's salary counts until it's logged`() =
        assertEquals(50000.0, still(listOf(salary(50000.0, 16)), at(2026, 10, 16)), 0.0)
    @Test fun `a salary whose day passed while the app was closed counts until it's logged`() =
        assertEquals(50000.0, still(listOf(salary(50000.0, 1)), at(2026, 10, 3)), 0.0)
    @Test fun `once logged it isn't counted twice`() {
        val s = salary(50000.0, 16).copy(id = 7)
        assertEquals(0.0, still(listOf(s), at(2026, 10, 16), done = listOf(done(s, 2026, 10))), 0.0)
    }
    @Test fun `last month's missed day isn't counted, since it won't be logged`() {
        // 25th-to-24th cycle on 3 Nov: the salary on the 28th was due in October, which RecurringManager no longer logs
        assertEquals(0.0, still(listOf(salary(50000.0, 28)), at(2026, 11, 3), startDay = 25), 0.0)
    }

    // What the forecast leaves out must match what RecurringManager won't log (RecurringIncomeRule).
    @Test fun `a salary already entered by hand this month isn't still to come`() {
        val now = at(2026, 10, 3)
        val s = RecurringIncome(id = 7, name = "Salary", amount = 50000.0, dayOfMonth = 5)
        val hand = Income(amount = 50000.0, description = "salary", date = at(2026, 10, 3))
        assertEquals(0.0, IncomeForecast.recurringStillDue(listOf(s), now, BudgetPeriod.forOffset(now, 1, 0), incomes = listOf(hand)), 0.0)
        // Last month's hand entry doesn't count for this month
        val old = hand.copy(date = at(2026, 9, 3))
        assertEquals(50000.0, IncomeForecast.recurringStillDue(listOf(s), now, BudgetPeriod.forOffset(now, 1, 0), incomes = listOf(old)), 0.0)
    }

    @Test fun `a month RecurringManager has handled isn't still to come`() {
        // Logged on the 1st and then deleted, or its day moved from 1 to 30 after it was logged
        val s = salary(50000.0, 30).copy(id = 7)
        assertEquals(0.0, still(listOf(s), at(2026, 10, 16), done = listOf(done(s, 2026, 10))), 0.0)
        assertEquals(50000.0, still(listOf(s), at(2026, 10, 16), done = listOf(done(s, 2026, 9))), 0.0)
    }

    @Test fun `in a 25th-to-24th cycle, next month's salary checks next month`() {
        val now = at(2026, 10, 26)  // cycle 25 Oct – 24 Nov; salary on the 1st is due 1 Nov
        val s = salary(50000.0, 1).copy(id = 7)
        val octEntry = income(50000.0, at(2026, 10, 1, 0)).copy(source = "Recurring", recurringIncomeId = 7)
        assertEquals(50000.0, IncomeForecast.recurringStillDue(listOf(s), now, BudgetPeriod.forOffset(now, 25, 0),
            incomes = listOf(octEntry), months = listOf(done(s, 2026, 10))), 0.0)
    }

    @Test fun `alike recurring incomes take hand entries by due day, the way RecurringManager does`() {
        val now = at(2026, 10, 1)
        val late = RecurringIncome(id = 3, name = "Salary", amount = 40000.0, dayOfMonth = 15)
        val early = RecurringIncome(id = 5, name = "Salary", amount = 40000.0, dayOfMonth = 1)
        val hand = Income(amount = 40000.0, description = "Salary", date = at(2026, 10, 1))
        // The hand entry is the 1st's; the 15th's is still to come
        assertEquals(40000.0, IncomeForecast.recurringStillDue(listOf(late, early), now, BudgetPeriod.forOffset(now, 1, 0), incomes = listOf(hand)), 0.0)
    }

    @Test fun `tomorrow's salary is still to come`() = assertEquals(50000.0, still(listOf(salary(50000.0, 17)), at(2026, 10, 16, 23)), 0.0)
    @Test fun `disabled recurring income is ignored`() = assertEquals(0.0, still(listOf(salary(50000.0, 25, enabled = false)), at(2026, 10, 16)), 0.0)
    @Test fun `several add up`() {
        val logged = salary(999.0, 2).copy(id = 9)
        assertEquals(62000.0, still(listOf(salary(50000.0, 25), salary(12000.0, 20), logged), at(2026, 10, 16), done = listOf(done(logged, 2026, 10))), 0.0)
    }
    @Test fun `day 31 is the last day of a 30-day month`() = assertEquals(300.0, still(listOf(salary(300.0, 31)), at(2026, 11, 29)), 0.0)
    @Test fun `in a 25th-to-24th cycle, income on the 1st of next month still counts`() =
        assertEquals(50000.0, still(listOf(salary(50000.0, 1)), at(2026, 10, 26), startDay = 25), 0.0)   // cycle 25 Oct – 24 Nov
    @Test fun `in a 25th-to-24th cycle, the next salary on the 25th is next cycle's`() {
        val s = salary(50000.0, 25).copy(id = 7)  // this cycle's, on 25 Oct, is logged
        assertEquals(0.0, still(listOf(s), at(2026, 10, 26), startDay = 25, done = listOf(done(s, 2026, 10))), 0.0)
    }
    @Test fun `a past month has nothing still to come`() =
        assertEquals(0.0, still(listOf(salary(50000.0, 25)), at(2026, 10, 16), offset = -1), 0.0)

    @Test fun `on a 23-hour daylight-saving day, tomorrow's salary still counts`() = inZone("America/New_York") {
        // Clocks go forward on 8 Mar 2026: start of day + 24 h would be 1 a.m. on the 9th, past its midnight.
        assertEquals(100.0, still(listOf(salary(100.0, 9)), at(2026, 3, 8)), 0.0)
    }

    @Test fun `in India, late on the 31st, the salary on the 1st belongs to next month`() = inZone("Asia/Kolkata") {
        val now = at(2026, 10, 31, 23)  // 17:30 UTC: still 31 Oct here
        val s = salary(50000.0, 1).copy(id = 7)
        assertEquals(0.0, still(listOf(s), now, done = listOf(done(s, 2026, 10))), 0.0)
        assertEquals(50000.0, still(listOf(s), now, offset = 1, done = listOf(done(s, 2026, 10))), 0.0)
    }

    private fun income(amount: Double, date: Long) = Income(amount = amount, description = "i", date = date)

    @Test fun `a month's income counts only the income logged inside it`() {
        val now = at(2026, 10, 16)
        val c = IncomeForecast.cycleIncome(listOf(income(50000.0, at(2026, 10, 1, 0)), income(2000.0, at(2026, 10, 10)), income(9.0, at(2026, 9, 30, 23))),
            listOf(salary(1000.0, 25)), expectedMonthly = 40000.0, now = now, cycle = BudgetPeriod.forOffset(now, 1, 0))
        assertEquals(52000.0, c.logged, 0.0)
        assertEquals(1000.0, c.stillDue, 0.0)
        assertEquals(53000.0, c.total, 0.0)
        assertTrue(c.fromRecords)
    }

    @Test fun `with no income records the month uses the expected figure`() {
        val now = at(2026, 10, 16)
        val c = IncomeForecast.cycleIncome(listOf(income(9.0, at(2026, 9, 30))), emptyList(), 40000.0, now, BudgetPeriod.forOffset(now, 1, 0))
        assertEquals(40000.0, c.total, 0.0)
        assertFalse(c.fromRecords)
    }

    @Test fun `on payday, before and after the salary is logged, it counts once`() {
        val now = at(2026, 10, 16, 0)
        val cycle = BudgetPeriod.forOffset(now, 1, 0)
        val s = salary(50000.0, 16).copy(id = 7)
        assertEquals(50000.0, IncomeForecast.cycleIncome(emptyList(), listOf(s), 0.0, now, cycle).total, 0.0)
        val logged = income(50000.0, at(2026, 10, 16, 0)).copy(source = "Recurring", recurringIncomeId = 7)
        assertEquals(50000.0, IncomeForecast.cycleIncome(listOf(logged), listOf(s), 0.0, now, cycle, months = listOf(done(s, 2026, 10))).total, 0.0)
    }

    @Test fun `a past month uses what was logged in it`() {
        val now = at(2026, 10, 16)
        val c = IncomeForecast.cycleIncome(listOf(income(48000.0, at(2026, 9, 1, 0))), listOf(salary(50000.0, 1)), 40000.0, now, BudgetPeriod.forOffset(now, 1, -1))
        assertEquals(48000.0, c.total, 0.0)
    }

    private fun inZone(id: String, block: () -> Unit) {
        val saved = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(id))
            block()
        } finally {
            TimeZone.setDefault(saved)
        }
    }
}
