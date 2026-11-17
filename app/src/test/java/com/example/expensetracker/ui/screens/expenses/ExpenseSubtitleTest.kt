package com.example.expensetracker.ui.screens.expenses

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseSubtitleTest {
    @Test fun categoryThenPaymentMethod() {
        assertEquals("Food · UPI", expenseSubtitle("Food", "UPI", ""))
    }

    @Test fun onlyTheFirstTagIsShown() {
        assertEquals("Food › Coffee · UPI · #work", expenseSubtitle("Food › Coffee", "UPI", "#work, #team"))
    }

    @Test fun missingPartsLeaveNoStraySeparator() {
        assertEquals("UPI", expenseSubtitle(null, "UPI", ""))
        assertEquals("Manual · #bonus", expenseSubtitle("Manual", "", "#bonus"))
        assertEquals("", expenseSubtitle(null, "", ""))
    }
}
