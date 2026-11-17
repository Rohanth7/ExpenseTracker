package com.example.expensetracker.widget

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** B-08 and the largest font: the widget's text gets smaller to fit instead of being cut off. */
@RunWith(AndroidJUnit4::class)
class WidgetTextFitTest {
    private fun context(fontScale: Float): Context = ApplicationProvider.getApplicationContext<Context>()
        .createConfigurationContext(Configuration().apply { this.fontScale = fontScale })

    @Test fun withRoomTheDesignSizeIsKept() {
        assertEquals(23f, fitSp(context(1f), listOf("₹21,820"), 150f, 23f, bold = true))
        assertEquals(11.5f, fitSp(context(1f), listOf("65% used", "18 days left"), 240f, 11.5f, gapDp = 10f))
    }

    @Test fun aLargeFontOrALongAmountShrinksToFit() {
        val large = fitSp(context(2f), listOf("₹39,590"), 100f, 23f, bold = true)
        assertTrue("$large", large < 23f)
        val paise = fitSp(context(1f), listOf("₹12,34,567.89"), 100f, 23f, bold = true)
        assertTrue("$paise", paise < 23f)
        val stats = fitSp(context(2f), listOf("108% used", "13 days left"), 200f, 11.5f, gapDp = 10f)
        assertTrue("$stats", stats < 11.5f)
    }
}
