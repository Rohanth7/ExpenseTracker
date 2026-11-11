package com.example.expensetracker.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** B-16: a cancelled or failed Google sign-in said nothing. */
class DriveSignInFailureTest {
    @Test fun `closing the account chooser says it was cancelled`() {
        assertEquals("Google sign-in was cancelled", driveSignInFailure(12501))
        assertEquals("Google sign-in was cancelled", driveSignInFailure(null))
    }

    @Test fun `no network asks to check the connection`() {
        assertEquals("Couldn't sign in to Google. Check your connection and try again.", driveSignInFailure(7))
    }

    @Test fun `any other failure names its code, such as 10 for an unregistered OAuth client`() {
        assertEquals("Couldn't sign in to Google (error 10)", driveSignInFailure(10))
    }
}
