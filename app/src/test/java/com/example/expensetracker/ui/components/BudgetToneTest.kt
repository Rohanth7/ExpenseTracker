package com.example.expensetracker.ui.components

import com.example.expensetracker.ui.theme.Gold
import com.example.expensetracker.ui.theme.Leaf
import com.example.expensetracker.ui.theme.Orange
import com.example.expensetracker.ui.theme.Pink
import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetToneTest {
    @Test fun comfortableBelowTheAlertLevel() {
        assertEquals(Leaf, budgetTone(0.0, 1000.0, 80))
        assertEquals(Leaf, budgetTone(799.99, 1000.0, 80))
    }

    @Test fun goldFromTheAlertLevelUpToTheLimit() {
        assertEquals(Gold, budgetTone(800.0, 1000.0, 80))
        assertEquals(Gold, budgetTone(999.99, 1000.0, 80))
    }

    @Test fun orangeExactlyAtTheLimit() {
        assertEquals(Orange, budgetTone(1000.0, 1000.0, 80))
    }

    @Test fun pinkOverTheLimit() {
        assertEquals(Pink, budgetTone(1000.01, 1000.0, 80))
    }

    @Test fun theAlertLevelIsTheUsers() {
        assertEquals(Leaf, budgetTone(600.0, 1000.0, 80))
        assertEquals(Gold, budgetTone(600.0, 1000.0, 50))
        assertEquals(Orange, budgetTone(1000.0, 1000.0, 100))
    }
}
