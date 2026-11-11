package com.example.expensetracker.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** B-26: Settings ended with "Expense Tracker · v2.1" in an app called MyMoney at version 1.0. */
class AppFooterTest {
    @Test fun `the footer is the app's name and version`() {
        assertEquals("MyMoney · v1.0", appFooter("MyMoney", "1.0"))
        assertEquals("MyMoney", appFooter("MyMoney", ""))
    }

    @Test fun `no screen writes the app's name or version by hand`() {
        val settings = File("src/main/java/com/example/expensetracker/ui/screens/settings/SettingsScreen.kt").readText()
        assertFalse(settings.contains("Expense Tracker"))
        assertTrue(settings.contains("appFooter("))
    }

    /** READ_SMS was declared but never requested or used; only incoming SMS are read (RECEIVE_SMS). */
    @Test fun `the manifest asks for incoming SMS only`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("android.permission.READ_SMS"))
        assertTrue(manifest.contains("android.permission.RECEIVE_SMS"))
    }
}
