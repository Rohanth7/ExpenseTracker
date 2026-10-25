package com.example.expensetracker.data.period

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class BudgetPeriodTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, 0) }.timeInMillis
    private fun start(y: Int, m: Int, d: Int) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, 0, 0, 0) }.timeInMillis
    private fun end(y: Int, m: Int, d: Int) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, 23, 59, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis

    @Test fun `start day 1 is the calendar month`() =
        assertEquals(DateRange(start(2026, 10, 1), end(2026, 10, 31)), BudgetPeriod.current(at(2026, 10, 16), 1))
    @Test fun `the day before the start day belongs to the previous cycle`() =
        assertEquals(DateRange(start(2026, 9, 25), end(2026, 10, 24)), BudgetPeriod.current(at(2026, 10, 24), 25))
    @Test fun `the start day begins a new cycle`() {
        assertEquals(DateRange(start(2026, 10, 25), end(2026, 11, 24)), BudgetPeriod.current(at(2026, 10, 25), 25))
        assertEquals(DateRange(start(2026, 10, 25), end(2026, 11, 24)), BudgetPeriod.current(at(2026, 10, 26), 25))
    }
    @Test fun `cycles cross the year boundary`() =
        assertEquals(DateRange(start(2026, 12, 25), end(2027, 1, 24)), BudgetPeriod.current(at(2027, 1, 3), 25))
    @Test fun `February with start day 28`() =
        assertEquals(DateRange(start(2027, 2, 28), end(2027, 3, 27)), BudgetPeriod.current(at(2027, 3, 1), 28))
    @Test fun `offsets move whole cycles`() =
        assertEquals(DateRange(start(2026, 8, 25), end(2026, 9, 24)), BudgetPeriod.forOffset(at(2026, 10, 1), 25, -1))
    @Test fun `start days outside 1 to 28 are clamped`() =
        assertEquals(BudgetPeriod.current(at(2026, 10, 16), 28), BudgetPeriod.current(at(2026, 10, 16), 31))
    @Test fun `days left counts today`() {
        assertEquals(16, BudgetPeriod.daysLeft(at(2026, 10, 16), 1))  // 16..31
        assertEquals(1, BudgetPeriod.daysLeft(at(2026, 10, 24), 25))  // last day of the cycle
    }
    @Test fun `day of cycle and cycle length`() {
        assertEquals(22, BudgetPeriod.dayOfCycle(at(2026, 10, 16), 25))  // 25 Sep is day 1
        assertEquals(30, BudgetPeriod.daysInCycle(BudgetPeriod.current(at(2026, 10, 16), 25)))  // 25 Sep – 24 Oct
    }
    @Test fun `last three months ends with the current cycle`() =
        assertEquals(DateRange(start(2026, 8, 1), end(2026, 10, 31)), BudgetPeriod.lastNMonths(at(2026, 10, 16), 1, 3))
    @Test fun `day range labels drop the year in the current year and collapse one day`() {
        val now = at(2026, 10, 24)
        assertEquals("1 Oct – 13 Oct", BudgetPeriod.dayRangeLabel(start(2026, 10, 1), end(2026, 10, 13), now))
        assertEquals("5 Oct", BudgetPeriod.dayRangeLabel(start(2026, 10, 5), end(2026, 10, 5), now))
        assertEquals("20 Dec 2025 – 3 Jan 2026", BudgetPeriod.dayRangeLabel(start(2025, 12, 20), end(2026, 1, 3), now))
    }
    @Test fun `labels`() {
        val now = at(2026, 10, 16)
        assertEquals("Oct 2026", BudgetPeriod.label(BudgetPeriod.current(now, 1), 1, now))
        assertEquals("25 Sep – 24 Oct", BudgetPeriod.label(BudgetPeriod.current(now, 25), 25, now))
        // An older cycle says which year it's from
        assertEquals("25 Dec 2025 – 24 Jan 2026", BudgetPeriod.label(BudgetPeriod.current(at(2026, 1, 3), 25), 25, now))
    }
    @Test fun `the last day of a long month with start day 28`() =
        assertEquals(DateRange(start(2027, 3, 28), end(2027, 4, 27)), BudgetPeriod.current(at(2027, 3, 31), 28))
    @Test fun `a cycle across February is 28 days`() =
        assertEquals(28, BudgetPeriod.daysInCycle(BudgetPeriod.current(at(2027, 3, 1), 28)))  // 28 Feb – 27 Mar
    @Test fun `the first moment of the start day is in the new cycle`() =
        assertEquals(start(2026, 10, 25), BudgetPeriod.current(start(2026, 10, 25), 25).start)
    @Test fun `start days read as ordinals`() =
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "23rd", "28th"),
            listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 23, 28).map { BudgetPeriod.ordinal(it) })
    @Test fun `the previous range has the same number of days and ends the day before`() {
        assertEquals(DateRange(start(2026, 9, 18), end(2026, 9, 30)), BudgetPeriod.previousOfSameLength(DateRange(start(2026, 10, 1), end(2026, 10, 13))))
        // Three 25th-to-24th months (25 Jul – 24 Oct, 92 days) → the 92 days before
        assertEquals(DateRange(start(2026, 4, 24), end(2026, 7, 24)), BudgetPeriod.previousOfSameLength(DateRange(start(2026, 7, 25), end(2026, 10, 24))))
    }
}
