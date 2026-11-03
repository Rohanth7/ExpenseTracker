package com.example.expensetracker.ui.screens.subscriptions

import com.example.expensetracker.data.subscriptions.Cadence
import com.example.expensetracker.data.subscriptions.Subscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class PriceChangeLabelTest {
    private val oct5 = Calendar.getInstance().apply { clear(); set(2026, Calendar.OCTOBER, 5, 10, 0) }.timeInMillis
    private fun sub(amount: Double, previous: Double?) = Subscription("netflix", "NETFLIX", amount, Cadence.MONTHLY, oct5, oct5, 5, 4,
        previousAmount = previous, priceChangedOn = previous?.let { oct5 })

    @Test fun `rises and drops say how much and since when`() {
        val locale = Locale.getDefault()
        Locale.setDefault(Locale.UK)
        try {
            assertEquals("↑ ₹50 since 5 Oct", priceChangeLabel(sub(549.0, 499.0)))
            assertEquals("↓ ₹150 since 5 Oct", priceChangeLabel(sub(499.0, 649.0)))
        } finally { Locale.setDefault(locale) }
    }

    @Test fun `no change, no label`() = assertNull(priceChangeLabel(sub(499.0, null)))
}
