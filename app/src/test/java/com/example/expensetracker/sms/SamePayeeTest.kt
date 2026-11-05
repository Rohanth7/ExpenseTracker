package com.example.expensetracker.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whether an expense added by hand (or an auto-logged bill) names the payee a capture names. */
class SamePayeeTest {
    private fun same(a: String, b: String) = CaptureDuplicates.samePayee(a, b)

    @Test fun `the same name in another case`() = assertTrue(same("Swiggy", "SWIGGY"))
    @Test fun `a shorter name inside a longer one`() = assertTrue(same("Netflix", "NETFLIX ENTERTAINMENT SERVICES"))
    @Test fun `a payment app's arrow form`() = assertTrue(same("Zomato", "GPay → Zomato"))
    @Test fun `punctuation is ignored`() = assertTrue(same("Amazon Pay", "AMAZON-PAY"))
    @Test fun `different payees`() = assertFalse(same("YouTube Premium", "CRUNCHYROLL"))
    @Test fun `a note rather than a payee`() = assertFalse(same("Lunch", "SWIGGY"))
    @Test fun `part of a word is not a match`() = assertFalse(same("Uber", "UBEREATS"))
    @Test fun `too short to tell`() = assertFalse(same("Ok", "OK CABS"))
    @Test fun `blank never matches`() = assertFalse(same("", "SWIGGY"))
}
