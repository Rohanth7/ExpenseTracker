package com.example.expensetracker.ui.screens.statistics

import com.example.expensetracker.data.db.entity.Expense
import org.junit.Assert.assertEquals
import org.junit.Test

/** B-18: "How you pay" counted four methods only, so anything else vanished from the split. */
class PaymentSplitTest {
    private fun paid(amount: Double, method: String) = Expense(amount = amount, description = "x", date = 1, paymentMethod = method)

    @Test fun `a method the dialogs don't offer is counted as Other`() {
        val split = PaymentSplit.of(listOf(paid(600.0, "UPI"), paid(250.0, "Credit Card"), paid(100.0, "Net Banking"), paid(50.0, ""), paid(22000.0, "Other")))
        assertEquals(22150.0, split.otherAmount, 0.0)
        assertEquals(23000.0, split.total, 0.0)
        assertEquals(listOf("UPI", "Credit Card", "Other"), split.shares.map { it.label })
    }

    @Test fun `the parts add up to the spending and the percentages to 100`() {
        // Thirds: 33 + 33 + 33 would be 99
        val thirds = PaymentSplit.of(listOf(paid(100.0, "UPI"), paid(100.0, "Cash"), paid(100.0, "Wallet")))
        assertEquals(100, thirds.shares.sumOf { it.percent })
        assertEquals(300.0, thirds.shares.sumOf { it.amount }, 0.0)
        val uneven = PaymentSplit.of(listOf(paid(999.0, "UPI"), paid(1.0, "Credit Card"), paid(333.33, "Debit Card"), paid(0.5, "Cash"), paid(77.7, "Cheque")))
        assertEquals(100, uneven.shares.sumOf { it.percent })
        assertEquals(listOf("UPI", "Credit Card", "Debit Card", "Cash", "Other"), uneven.shares.map { it.label })
    }

    @Test fun `one method is 100 percent and no spending has no shares`() {
        assertEquals(listOf(PaymentSplit.Share("Cash", 40.0, 100)), PaymentSplit.of(listOf(paid(40.0, "Cash"))).shares)
        assertEquals(emptyList<PaymentSplit.Share>(), PaymentSplit.of(emptyList()).shares)
    }
}
