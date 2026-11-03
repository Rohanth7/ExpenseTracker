package com.example.expensetracker.data.recurring

import com.example.expensetracker.data.db.entity.Income
import com.example.expensetracker.data.db.entity.RecurringIncome
import com.example.expensetracker.data.db.entity.RecurringIncomeMonth
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringIncomeRuleTest {
    private fun recurring(id: Long, name: String = "Salary", amount: Double = 50000.0, day: Int = 1) =
        RecurringIncome(id = id, name = name, amount = amount, dayOfMonth = day)
    private fun byHand(description: String = "Salary", amount: Double = 50000.0) = Income(amount = amount, description = description, date = 1L)
    private fun done(r: RecurringIncome, byHand: Boolean = false) = RecurringIncomeMonth(r.id, 2026, 10, byHand)
    private fun isDone(r: RecurringIncome, incomes: List<Income>, enabled: List<RecurringIncome>, months: List<RecurringIncomeMonth> = emptyList()) =
        RecurringIncomeRule.done(r, 2026, 10, incomes, enabled, months)

    @Test fun `a month RecurringManager handled is done, whatever happened to its entry since`() {
        val r = recurring(1)
        assertTrue(isDone(r, emptyList(), listOf(r), listOf(done(r))))
        assertFalse(isDone(r, emptyList(), listOf(r), listOf(RecurringIncomeMonth(1, 2026, 9))))
        assertFalse(isDone(r, emptyList(), listOf(r), listOf(RecurringIncomeMonth(2, 2026, 10))))
    }

    /** An entry linked to it doesn't count on its own: one moved in from last month isn't this month's. */
    @Test fun `an entry it logged that was moved into this month doesn't count`() {
        val r = recurring(1)
        val moved = Income(amount = 50000.0, description = "Salary", date = 1L, source = "Recurring", recurringIncomeId = 1)
        assertFalse(isDone(r, listOf(moved), listOf(r)))
    }

    @Test fun `one entered by hand with its name and amount counts, ignoring case and spaces`() {
        val r = recurring(1)
        assertTrue(isDone(r, listOf(byHand(" salary ")), listOf(r)))
        assertFalse(isDone(r, listOf(byHand(amount = 2000.0)), listOf(r)))
        assertFalse(isDone(r, listOf(byHand("Bonus")), listOf(r)))
    }

    /** Two tenants paying the same rent: one entered by hand covers one of them, not both. */
    @Test fun `alike recurring incomes share hand entries one each`() {
        val a = recurring(1, "Rent received", 10000.0); val b = recurring(2, "Rent received", 10000.0)
        val hand = byHand("Rent received", 10000.0)
        assertTrue(isDone(a, listOf(hand), listOf(a, b)))
        assertFalse(isDone(b, listOf(hand), listOf(a, b)))
        // Once a is handled by the hand entry, it's used up: b still isn't covered
        assertFalse(isDone(b, listOf(hand), listOf(a, b), listOf(done(a, byHand = true))))
        // If a was logged on its own instead, the hand entry is free for b
        assertTrue(isDone(b, listOf(hand), listOf(a, b), listOf(done(a))))
    }

    @Test fun `the earliest due day takes the hand entry first`() {
        val late = recurring(3, day = 15); val early = recurring(5, day = 1)
        assertTrue(isDone(early, listOf(byHand()), listOf(late, early)))
        assertFalse(isDone(late, listOf(byHand()), listOf(late, early)))
    }
}
