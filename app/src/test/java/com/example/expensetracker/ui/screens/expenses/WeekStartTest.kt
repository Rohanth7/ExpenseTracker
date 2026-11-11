package com.example.expensetracker.ui.screens.expenses

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class WeekStartTest {
    private fun at(year: Int, month: Int, day: Int, hour: Int = 0) = Calendar.getInstance().apply { clear(); set(year, month - 1, day, hour, 0) }.timeInMillis

    @Test fun `the week starts at midnight on Monday or Sunday`() {
        // Wednesday 11 Nov 2026, 3 pm
        val now = at(2026, 11, 11, 15)
        assertEquals(at(2026, 11, 9), weekStart(now, startsOnMonday = true))
        assertEquals(at(2026, 11, 8), weekStart(now, startsOnMonday = false))
    }

    @Test fun `on the first day of the week it is that day`() {
        assertEquals(at(2026, 11, 9), weekStart(at(2026, 11, 9, 0), startsOnMonday = true))
        // A Sunday belongs to the week that began the Monday before
        assertEquals(at(2026, 11, 2), weekStart(at(2026, 11, 8, 23), startsOnMonday = true))
        assertEquals(at(2026, 11, 8), weekStart(at(2026, 11, 8, 23), startsOnMonday = false))
    }
}
