package com.example.expensetracker.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class ExportRangeTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, 0) }.timeInMillis
    private fun start(y: Int, m: Int, d: Int) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, 0, 0, 0) }.timeInMillis
    private fun end(y: Int, m: Int, d: Int) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, 23, 59, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis

    @Test fun `this month runs from the 1st to the last day`() =
        assertEquals(start(2026, 10, 1) to end(2026, 10, 31), ExportRange.forPreset(ExportPreset.THIS_MONTH, at(2026, 10, 16)))

    @Test fun `last month in January is December of the previous year`() =
        assertEquals(start(2025, 12, 1) to end(2025, 12, 31), ExportRange.forPreset(ExportPreset.LAST_MONTH, at(2026, 1, 10)))

    @Test fun `this year is the calendar year`() =
        assertEquals(start(2026, 1, 1) to end(2026, 12, 31), ExportRange.forPreset(ExportPreset.THIS_YEAR, at(2026, 10, 16)))

    @Test fun `all time has no range`() = assertNull(ExportRange.forPreset(ExportPreset.ALL, at(2026, 10, 16)))

    @Test fun `custom returns the chosen range`() =
        assertEquals(start(2026, 10, 1) to end(2026, 10, 13),
            ExportRange.forPreset(ExportPreset.CUSTOM, at(2026, 10, 16), custom = start(2026, 10, 1) to end(2026, 10, 13)))

    @Test fun `file names say what was exported`() {
        assertEquals("mymoney-expenses-2026-10.csv", ExportRange.fileName(ExportPreset.THIS_MONTH, start(2026, 10, 1) to end(2026, 10, 31)))
        assertEquals("mymoney-expenses-all.csv", ExportRange.fileName(ExportPreset.ALL, null))
        assertEquals("mymoney-expenses-2026-10-01_to_2026-10-13.csv",
            ExportRange.fileName(ExportPreset.CUSTOM, start(2026, 10, 1) to end(2026, 10, 13)))
    }

    @Test fun `with a salary cycle, this and last month follow it`() {
        assertEquals(start(2026, 9, 25) to end(2026, 10, 24), ExportRange.forPreset(ExportPreset.THIS_MONTH, at(2026, 10, 16), monthStartDay = 25))
        assertEquals(start(2026, 8, 25) to end(2026, 9, 24), ExportRange.forPreset(ExportPreset.LAST_MONTH, at(2026, 10, 16), monthStartDay = 25))
    }

    @Test fun `a salary-cycle month is named by its dates`() {
        assertEquals("mymoney-expenses-2026-09-25_to_2026-10-24.csv",
            ExportRange.fileName(ExportPreset.THIS_MONTH, start(2026, 9, 25) to end(2026, 10, 24), monthStartDay = 25))
        // Named by the setting, not by where the range happens to start
        assertEquals("mymoney-expenses-2026-10-01_to_2026-10-31.csv",
            ExportRange.fileName(ExportPreset.THIS_MONTH, start(2026, 10, 1) to end(2026, 10, 31), monthStartDay = 2))
    }

    @Test fun `a "this month" filter moves on with the month`() {
        val filter = DateFilter(ExportPreset.THIS_MONTH)
        assertEquals(start(2026, 10, 10) to end(2026, 11, 9), filter.range(at(2026, 10, 16), 10))
        assertEquals(start(2026, 11, 10) to end(2026, 12, 9), filter.range(at(2026, 11, 12), 10))
    }
}
