package com.example.expensetracker.sms

import com.example.expensetracker.data.db.dao.ExpenseDao

/** Whether a captured payment was already saved: shared by the SMS and payment-app capture paths. */
object CaptureDuplicates {
    suspend fun isDuplicate(dao: ExpenseDao, amount: Double, source: String, content: String, now: Long): Boolean {
        // Cross-source: UPI notification + bank SMS arrive within seconds for the same transaction.
        // Descriptions differ between sources, so match on amount only within 3 minutes, and only
        // against other captures: a ₹149 added by hand used to swallow a ₹149 SMS from another payee.
        if (dao.countRecentCapturesFromOtherSource(amount, source, now - 3 * 60 * 1000L) > 0) return true
        // Same-source: the message or notification fired twice (30-second window).
        if (dao.getRecentByAmountAndContentFromSameSource(amount, source, content, now - 30 * 1000L) > 0) return true
        return false
    }
}
