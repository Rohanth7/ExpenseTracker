package com.example.expensetracker.ui.screens.statistics

import com.example.expensetracker.data.period.BudgetPeriod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ComparableTotalTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, 0) }.timeInMillis

    @Test fun `a running range is compared with the same share of the previous one`() {
        val now = at(2026, 10, 26)
        val range = BudgetPeriod.lastNMonths(now, 25, 3)  // 25 Aug – 24 Nov: 92 days, 63 of them so far
        val previous = BudgetPeriod.previousOfSameLength(range)
        assertEquals(6300.0, comparableTotal(9200.0, previous, range, now), 0.001)
    }

    @Test fun `a finished range is compared with the whole previous one`() {
        val now = at(2026, 10, 26)
        val range = BudgetPeriod.dayRange(at(2026, 10, 1), at(2026, 10, 13))
        assertEquals(9200.0, comparableTotal(9200.0, BudgetPeriod.previousOfSameLength(range), range, now), 0.0)
    }
}
