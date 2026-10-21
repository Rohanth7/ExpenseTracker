package com.example.expensetracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchActionTest {
    @Test fun `a notification expense id wins`() = assertEquals(LaunchAction.Categorize(7), LaunchAction.from(LaunchAction.QUICK_ADD, 7))
    @Test fun `quick add from the widget`() = assertEquals(LaunchAction.QuickAdd, LaunchAction.from(LaunchAction.QUICK_ADD, -1))
    @Test fun `open subscriptions`() = assertEquals(LaunchAction.OpenSubscriptions, LaunchAction.from(LaunchAction.OPEN_SUBSCRIPTIONS, -1))
    @Test fun `plain launch does nothing`() = assertNull(LaunchAction.from(null, -1))
    @Test fun `a recreated activity doesn't handle its launch intent again`() {
        // Rotation re-runs onCreate with the same intent; acting on it again reopened the add dialog.
        assertNull(LaunchAction.onCreate(recreated = true, action = LaunchAction.QUICK_ADD, expenseId = -1))
        assertNull(LaunchAction.onCreate(recreated = true, action = null, expenseId = 7))
        assertEquals(LaunchAction.QuickAdd, LaunchAction.onCreate(recreated = false, action = LaunchAction.QUICK_ADD, expenseId = -1))
    }
    @Test fun `an action not handled yet survives recreation`() {
        // A tap that arrived during onboarding, then a rotation: it must still run once the app is up.
        assertEquals(LaunchAction.Categorize(7), LaunchAction.onCreate(recreated = true, action = null, expenseId = 7, stillPending = LaunchAction.Categorize(7)))
        assertEquals(LaunchAction.QuickAdd, LaunchAction.onCreate(recreated = true, action = LaunchAction.QUICK_ADD, expenseId = -1, stillPending = LaunchAction.QuickAdd))
    }
    @Test fun `a pending action round-trips through saved state`() {
        listOf(LaunchAction.Categorize(42), LaunchAction.QuickAdd, LaunchAction.OpenSubscriptions).forEach { a ->
            val (action, id) = a.toExtras()
            assertEquals(a, LaunchAction.from(action, id))
        }
    }
}
