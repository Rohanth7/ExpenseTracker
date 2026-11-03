package com.example.expensetracker.ui.screens.statistics

import com.example.expensetracker.data.db.entity.Income
import com.example.expensetracker.data.period.DateRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomeSummaryTest {
    private val range = DateRange(1_000L, 2_000L)
    private fun income(amount: Double, date: Long) = Income(amount = amount, description = "i", date = date)

    @Test fun `counts income inside the range, ends included`() {
        val s = IncomeSummary.of(listOf(income(100.0, 1_000L), income(50.0, 2_000L), income(9.0, 999L), income(9.0, 2_001L)), range, spent = 30.0)!!
        assertEquals(150.0, s.income, 0.0)
        assertEquals(30.0, s.spent, 0.0)
        assertEquals(120.0, s.saved, 0.0)
    }

    @Test fun `spending more than came in saves a negative amount`() =
        assertEquals(-40.0, IncomeSummary.of(listOf(income(60.0, 1_500L)), range, spent = 100.0)!!.saved, 0.0)

    @Test fun `no income in the range shows nothing, so people who don't log income see no change`() =
        assertNull(IncomeSummary.of(listOf(income(60.0, 5_000L)), range, spent = 100.0))
}
