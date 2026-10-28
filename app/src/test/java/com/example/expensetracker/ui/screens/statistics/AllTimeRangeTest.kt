package com.example.expensetracker.ui.screens.statistics

import com.example.expensetracker.data.period.BudgetPeriod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class AllTimeRangeTest {
    private fun at(y: Int, m: Int, d: Int) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, 12, 0) }.timeInMillis

    @Test fun `all time starts at the first expense or income, whichever is earlier`() {
        val r = allTimeRange(listOf(at(2026, 3, 3)), listOf(at(2026, 1, 1)), now = at(2026, 10, 28))
        assertEquals(BudgetPeriod.startOfDay(at(2026, 1, 1)), r.start)
    }

    @Test fun `income alone gives a range`() {
        val r = allTimeRange(emptyList(), listOf(at(2026, 9, 1)), now = at(2026, 10, 28))
        assertEquals(BudgetPeriod.startOfDay(at(2026, 9, 1)), r.start)
    }

    @Test fun `it runs to today, or to a later dated entry`() {
        val now = at(2026, 10, 28)
        assertEquals(BudgetPeriod.dayRange(now, now).endInclusive, allTimeRange(listOf(at(2026, 3, 3)), emptyList(), now).endInclusive)
        assertEquals(BudgetPeriod.dayRange(now, at(2026, 11, 2)).endInclusive, allTimeRange(emptyList(), listOf(at(2026, 11, 2)), now).endInclusive)
    }
}
