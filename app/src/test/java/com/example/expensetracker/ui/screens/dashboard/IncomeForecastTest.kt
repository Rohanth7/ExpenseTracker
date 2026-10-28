package com.example.expensetracker.ui.screens.dashboard

import com.example.expensetracker.data.db.entity.Income
import com.example.expensetracker.data.db.entity.RecurringIncome
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
    private fun still(recurring: List<RecurringIncome>, now: Long, startDay: Int = 1, offset: Int = 0) =
        IncomeForecast.recurringStillDue(recurring, now, BudgetPeriod.forOffset(now, startDay, offset))

    @Test fun `a salary later this month is still to come`() = assertEquals(50000.0, still(listOf(salary(50000.0, 25)), at(2026, 10, 16)), 0.0)
    @Test fun `a salary earlier this month has been logged`() = assertEquals(0.0, still(listOf(salary(50000.0, 1)), at(2026, 10, 16)), 0.0)
    // The app logs it on open, but the widget and an app left open overnight see payday start first.
    @Test fun `today's salary counts until it's logged`() =
        assertEquals(50000.0, still(listOf(salary(50000.0, 16)), at(2026, 10, 16)), 0.0)
    @Test fun `once today's salary is logged it isn't counted twice`() {
        val now = at(2026, 10, 16)
        val s = salary(50000.0, 16).copy(id = 7)
        assertEquals(0.0, IncomeForecast.recurringStillDue(listOf(s), now, BudgetPeriod.forOffset(now, 1, 0), loggedToday = setOf(7L)), 0.0)
    }
    @Test fun `tomorrow's salary is still to come`() = assertEquals(50000.0, still(listOf(salary(50000.0, 17)), at(2026, 10, 16, 23)), 0.0)
    @Test fun `disabled recurring income is ignored`() = assertEquals(0.0, still(listOf(salary(50000.0, 25, enabled = false)), at(2026, 10, 16)), 0.0)
    @Test fun `several add up`() = assertEquals(62000.0, still(listOf(salary(50000.0, 25), salary(12000.0, 20), salary(999.0, 2)), at(2026, 10, 16)), 0.0)
    @Test fun `day 31 is the last day of a 30-day month`() = assertEquals(300.0, still(listOf(salary(300.0, 31)), at(2026, 11, 29)), 0.0)
    @Test fun `in a 25th-to-24th cycle, income on the 1st of next month still counts`() =
        assertEquals(50000.0, still(listOf(salary(50000.0, 1)), at(2026, 10, 26), startDay = 25), 0.0)   // cycle 25 Oct – 24 Nov
    @Test fun `in a 25th-to-24th cycle, the next salary on the 25th is next cycle's`() =
        assertEquals(0.0, still(listOf(salary(50000.0, 25)), at(2026, 10, 26), startDay = 25), 0.0)
    @Test fun `a past month has nothing still to come`() =
        assertEquals(0.0, still(listOf(salary(50000.0, 25)), at(2026, 10, 16), offset = -1), 0.0)

    @Test fun `on a 23-hour daylight-saving day, tomorrow's salary still counts`() = inZone("America/New_York") {
        // Clocks go forward on 8 Mar 2026: start of day + 24 h would be 1 a.m. on the 9th, past its midnight.
        assertEquals(100.0, still(listOf(salary(100.0, 9)), at(2026, 3, 8)), 0.0)
    }

    @Test fun `in India, late on the 31st, the salary on the 1st belongs to next month`() = inZone("Asia/Kolkata") {
        val now = at(2026, 10, 31, 23)  // 17:30 UTC: still 31 Oct here
        assertEquals(0.0, still(listOf(salary(50000.0, 1)), now), 0.0)
        assertEquals(50000.0, still(listOf(salary(50000.0, 1)), now, offset = 1), 0.0)
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
        assertEquals(50000.0, IncomeForecast.cycleIncome(listOf(logged), listOf(s), 0.0, now, cycle).total, 0.0)
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
