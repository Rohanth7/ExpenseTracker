package com.example.expensetracker.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DateUtilTest {
    private val utcOct1 = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(2026, 9, 1) }.timeInMillis
    private fun local(t: Long) = Calendar.getInstance().apply { timeInMillis = t }

    @Test fun `a picker date becomes the start of that local day`() {
        val c = local(utcMidnightToLocalDayStart(utcOct1))
        assertEquals(listOf(2026, 9, 1, 0, 0, 0, 0), listOf(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND), c.get(Calendar.MILLISECOND)))
    }

    @Test fun `and the end of that local day`() {
        val c = local(utcMidnightToLocalDayEnd(utcOct1))
        assertEquals(listOf(2026, 9, 1, 23, 59, 59, 999), listOf(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND), c.get(Calendar.MILLISECOND)))
    }
}
