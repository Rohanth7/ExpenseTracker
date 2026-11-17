package com.example.expensetracker.ui.screens.categories

import org.junit.Assert.assertEquals
import org.junit.Test

class WarnAtTest {
    @Test fun isTheAlertShareOfTheLimit() {
        assertEquals(9_600.0, warnAt(12_000.0, 80), 0.0)
        assertEquals(2_500.0, warnAt(5_000.0, 50), 0.0)
    }

    @Test fun roundsDown() {
        // 85% of 999 is 849.15
        assertEquals(849.0, warnAt(999.0, 85), 0.0)
        // 95% of 1,999 is 1,899.05
        assertEquals(1_899.0, warnAt(1_999.0, 95), 0.0)
    }
}
