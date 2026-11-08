package com.example.expensetracker.ui.screens.expenses

import com.example.expensetracker.data.db.entity.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagsTest {
    /** BUG-23: renaming "goa" to "food" on a "goa,food" expense gave "food,food", counted twice. */
    @Test fun `renaming to a tag the expense already has keeps one`() {
        assertEquals("food", Tags.rename("goa,food", "goa", "food"))
        assertEquals("Food,trip", Tags.rename("goa,food,trip", "goa", "Food"))
        assertEquals("beach,trip", Tags.rename("#Goa, trip", "goa", "beach"))
    }

    @Test fun `a tag is on an expense once, whatever its case`() {
        assertEquals(listOf("food", "trip"), Tags.parse("food,#Food, trip,,FOOD"))
        assertEquals("trip", Tags.remove("food,trip,Food", "FOOD"))
        assertEquals("house,EMI,Home Loan", Tags.add("house,EMI", "emi", "Home Loan"))
        assertEquals("Car Loan 2", Tags.add("", "Car, Loan 2"))
    }

    @Test fun `a new tag name can't be blank or hold a comma`() {
        assertEquals("food", Tags.cleanName("  #food "))
        listOf("", " ", "#", "goa,food").forEach { assertNull("'$it'", Tags.cleanName(it)) }
    }

    @Test fun `each expense counts once per tag in the summary`() {
        val summaries = Tags.summaries(listOf(
            Expense(amount = 300.0, description = "Dinner", tags = "food,food"),
            Expense(amount = 200.0, description = "Lunch", tags = "Food,goa"),
            Expense(amount = 50.0, description = "Tea", tags = "")
        ))
        assertEquals(listOf(TagSummary("food", 2, 500.0), TagSummary("goa", 1, 200.0)), summaries)
    }
}
