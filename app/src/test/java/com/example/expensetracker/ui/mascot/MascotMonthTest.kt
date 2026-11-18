package com.example.expensetracker.ui.mascot

import com.example.expensetracker.data.period.BudgetPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MascotMonthTest {
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12) =
        Calendar.getInstance().apply { clear(); set(year, month - 1, day, hour, 0) }.timeInMillis

    private fun month(now: Long, income: Double = 50_000.0, spent: Double = 10_000.0, stillDue: Double = 5_000.0, startDay: Int = 1, daysOver: Int = 0) =
        mascotMonth(income, spent, stillDue, emptyList(), 80, now, startDay, daysOver)

    @Test fun carriesHomesFigures() {
        val m = month(at(2026, 11, 17))
        assertEquals(50_000.0, m.income, 0.0)
        assertEquals(10_000.0, m.spent, 0.0)
        assertEquals(5_000.0, m.stillDue, 0.0)
        assertEquals(80, m.alertPercent)
        assertEquals(1, m.monthStartDay)
        // 17 to 30 November, today included
        assertEquals(14, m.daysLeft)
        assertFalse(m.firstDay)
        assertEquals(0, m.daysOver)
    }

    @Test fun todayIsTheLocalDateAsAnEpochDay() {
        // 17 Nov 2026 is 20,774 days after 1 Jan 1970, at any hour of the day.
        assertEquals(20_774L, month(at(2026, 11, 17, hour = 0)).epochDay)
        assertEquals(20_774L, month(at(2026, 11, 17, hour = 23)).epochDay)
        assertEquals(BudgetPeriod.epochDay(at(2026, 11, 17)), month(at(2026, 11, 17)).epochDay)
    }

    @Test fun theFirstDayFollowsTheMonthStartDay() {
        assertTrue(month(at(2026, 11, 1)).firstDay)
        assertFalse(month(at(2026, 11, 25)).firstDay)
        assertTrue(month(at(2026, 11, 25), startDay = 25).firstDay)
        assertFalse(month(at(2026, 11, 1), startDay = 25).firstDay)
    }

    @Test fun carriesTheDaysOverItIsGiven() {
        assertEquals(MascotMood.Pleading, MascotVoice.say(month(at(2026, 11, 17), spent = 60_000.0)).mood)
        assertEquals(MascotMood.Sad, MascotVoice.say(month(at(2026, 11, 18), spent = 60_000.0, daysOver = 1)).mood)
    }
}
