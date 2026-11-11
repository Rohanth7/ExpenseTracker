package com.example.expensetracker.widget

import com.example.expensetracker.ui.util.fmtINR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** B-08: with paise shown, the widget's spent figure is longer; it steps down instead of being cut off. */
class SpentTextSizeTest {
    @Test fun `short amounts keep the large size`() {
        assertEquals(36f, spentTextSizeSp(fmtINR(0.0)))
        assertEquals(36f, spentTextSizeSp(fmtINR(9999.0)))
    }

    @Test fun `longer amounts and paise step down, never below 16sp`() {
        val sizes = listOf(9999.0, 99999.0, 100100.0, 100100.74, 1234567.89, 123456789.99).map { spentTextSizeSp(fmtINR(it)) }
        assertEquals(sizes.sortedDescending(), sizes)
        assertEquals(16f, sizes.last())
        assertTrue(sizes.all { it >= 16f })
    }
}
