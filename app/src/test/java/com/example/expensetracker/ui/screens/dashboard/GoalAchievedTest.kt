package com.example.expensetracker.ui.screens.dashboard

import com.example.expensetracker.data.db.entity.SavingsGoal
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalAchievedTest {
    private fun goal(saved: Double) = SavingsGoal(name = "Goa trip", targetAmount = 1000.0, currentAmount = saved)

    /** BUG-11: deposits past the target are kept (they were cut off from the Overview), and shown. */
    @Test fun `saving past the target keeps counting`() {
        assertEquals("0% achieved", goalAchieved(goal(0.0)))
        assertEquals("90% achieved", goalAchieved(goal(900.0)))
        assertEquals("100% achieved", goalAchieved(goal(1000.0)))
        assertEquals("110% achieved", goalAchieved(goal(1100.0)))
    }
}
