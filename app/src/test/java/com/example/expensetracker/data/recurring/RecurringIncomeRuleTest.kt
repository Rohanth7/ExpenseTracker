package com.example.expensetracker.data.recurring

import com.example.expensetracker.data.db.entity.Income
import com.example.expensetracker.data.db.entity.RecurringIncome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringIncomeRuleTest {
    private fun recurring(id: Long, name: String = "Salary", amount: Double = 50000.0) =
        RecurringIncome(id = id, name = name, amount = amount, dayOfMonth = 1)
    private fun byHand(description: String = "Salary", amount: Double = 50000.0) = Income(amount = amount, description = description, date = 1L)
    private fun loggedFor(id: Long) = Income(amount = 50000.0, description = "Salary", date = 1L, source = "Recurring", recurringIncomeId = id)

    @Test fun `an entry logged for it counts`() {
        val r = recurring(1)
        assertTrue(RecurringIncomeRule.loggedIn(r, listOf(loggedFor(1)), listOf(r)))
        assertFalse(RecurringIncomeRule.loggedIn(r, listOf(loggedFor(2)), listOf(r)))
        assertFalse(RecurringIncomeRule.loggedIn(r, emptyList(), listOf(r)))
    }

    @Test fun `one entered by hand with its name and amount counts, ignoring case and spaces`() {
        val r = recurring(1)
        assertTrue(RecurringIncomeRule.loggedIn(r, listOf(byHand(" salary ")), listOf(r)))
        assertFalse(RecurringIncomeRule.loggedIn(r, listOf(byHand(amount = 2000.0)), listOf(r)))
        assertFalse(RecurringIncomeRule.loggedIn(r, listOf(byHand("Bonus")), listOf(r)))
    }

    /** Two tenants paying the same rent: one entered by hand covers one of them, not both. */
    @Test fun `alike recurring incomes share hand entries one each, lowest id first`() {
        val a = recurring(1, "Rent received", 10000.0); val b = recurring(2, "Rent received", 10000.0)
        val hand = byHand("Rent received", 10000.0)
        assertTrue(RecurringIncomeRule.loggedIn(a, listOf(hand), listOf(a, b)))
        assertFalse(RecurringIncomeRule.loggedIn(b, listOf(hand), listOf(a, b)))
        // Once b is logged, the hand entry still covers a, and b is covered by its own entry
        val bLogged = Income(amount = 10000.0, description = "Rent received", date = 1L, source = "Recurring", recurringIncomeId = 2)
        assertTrue(RecurringIncomeRule.loggedIn(a, listOf(hand, bLogged), listOf(a, b)))
        assertTrue(RecurringIncomeRule.loggedIn(b, listOf(hand, bLogged), listOf(a, b)))
        // a logged by itself leaves the hand entry for b
        val aLogged = bLogged.copy(recurringIncomeId = 1)
        assertTrue(RecurringIncomeRule.loggedIn(b, listOf(hand, aLogged), listOf(a, b)))
    }

    @Test fun `mark keys are year, 0-based month and id`() = assertEquals("2026_9_4", RecurringIncomeRule.markKey(2026, 9, 4))
}
