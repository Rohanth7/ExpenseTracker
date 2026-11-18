package com.example.expensetracker.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetTextTest {
    private fun data(income: Double = 85_000.0, spent: Double = 41_280.0, stillDue: Double = 21_900.0, daysLeft: Int = 18) =
        WidgetData(spent = spent, income = income, stillDue = stillDue, daysLeft = daysLeft, mood = null, widgetEnabled = true)

    @Test fun `plenty left shows what is free and the share used`() {
        val text = widgetText(data())
        assertEquals("Free to spend", text.label)
        assertEquals("₹21,820", text.amount)
        assertFalse(text.over)
        // 63,180 of 85,000 is 74.3%
        assertEquals(74, text.barPercent)
        assertEquals("74% used", text.used)
        assertEquals("18 days left", text.daysLeft)
        assertNull(text.note)
    }

    @Test fun `one day left is singular`() = assertEquals("1 day left", widgetText(data(daysLeft = 1)).daysLeft)

    @Test fun `over the month shows by how much, with a full bar`() {
        val text = widgetText(data(spent = 70_000.0))
        assertEquals("Over this month", text.label)
        assertEquals("₹6,900", text.amount)
        assertTrue(text.over)
        assertEquals(100, text.barPercent)
        assertEquals("108% used", text.used)
    }

    @Test fun `without an income it shows what was spent and asks for one`() {
        val text = widgetText(data(income = 0.0, spent = 4_200.0))
        assertEquals("Spent this month", text.label)
        assertEquals("₹4,200", text.amount)
        assertNull(text.barPercent)
        assertEquals("Set your income in the app", text.note)
    }
}
