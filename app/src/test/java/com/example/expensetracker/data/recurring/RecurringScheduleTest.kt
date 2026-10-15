package com.example.expensetracker.data.recurring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class RecurringScheduleTest {
    /** Local time, like the app. Month is 1-based here for readability. */
    private fun day(year: Int, month: Int, day: Int, hour: Int = 0) =
        Calendar.getInstance().apply { clear(); set(year, month - 1, day, hour, 0, 0) }

    private fun millis(year: Int, month: Int, day: Int) = day(year, month, day).timeInMillis

    @Test fun `a bill due later this month isn't logged yet`() =
        assertNull(RecurringSchedule.dueDateIfReached(dueDayOfMonth = 25, now = day(2026, 10, 16, hour = 9)))

    @Test fun `a bill whose day has passed is dated on its due day, not the 1st`() =
        assertEquals(millis(2026, 10, 5), RecurringSchedule.dueDateIfReached(dueDayOfMonth = 5, now = day(2026, 10, 16, hour = 9)))

    @Test fun `a bill due today is logged today`() =
        assertEquals(millis(2026, 10, 16), RecurringSchedule.dueDateIfReached(dueDayOfMonth = 16, now = day(2026, 10, 16, hour = 9)))

    @Test fun `day 31 in February is logged on the last day of the month`() =
        assertEquals(millis(2027, 2, 28), RecurringSchedule.dueDateIfReached(dueDayOfMonth = 31, now = day(2027, 2, 28, hour = 8)))

    @Test fun `day 31 in February isn't logged before the last day`() =
        assertNull(RecurringSchedule.dueDateIfReached(dueDayOfMonth = 31, now = day(2027, 2, 27, hour = 23)))
}
