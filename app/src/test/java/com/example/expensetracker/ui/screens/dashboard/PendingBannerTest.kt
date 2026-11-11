package com.example.expensetracker.ui.screens.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** B-21: the banner said "UPI transactions" for every pending expense, card and hand-entered ones too. */
class PendingBannerTest {
    @Test fun `the banner names no payment method`() {
        listOf(1, 2, 14).forEach { assertFalse(pendingBannerText(it).contains("UPI", ignoreCase = true)) }
    }

    @Test fun `one expense needs, several need`() {
        assertEquals("expense needs categorizing", pendingBannerText(1))
        assertEquals("expenses need categorizing", pendingBannerText(2))
    }
}
