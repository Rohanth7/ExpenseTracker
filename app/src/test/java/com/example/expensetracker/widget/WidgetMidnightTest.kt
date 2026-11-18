package com.example.expensetracker.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class WidgetMidnightTest {
    private fun at(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0) =
        Calendar.getInstance().apply { clear(); set(year, month - 1, day, hour, minute) }.timeInMillis

    @Test fun theNextLocalMidnight() {
        assertEquals(at(2026, 11, 19), WidgetMidnight.nextMidnight(at(2026, 11, 18, 10, 30)))
        assertEquals(at(2026, 11, 19), WidgetMidnight.nextMidnight(at(2026, 11, 18, 23, 59)))
    }

    @Test fun atMidnightItIsTheNextOne() {
        assertEquals(at(2026, 11, 19), WidgetMidnight.nextMidnight(at(2026, 11, 18)))
    }

    @Test fun acrossMonthAndYearEnds() {
        assertEquals(at(2026, 12, 1), WidgetMidnight.nextMidnight(at(2026, 11, 30, 18)))
        assertEquals(at(2027, 1, 1), WidgetMidnight.nextMidnight(at(2026, 12, 31, 18)))
    }
}
