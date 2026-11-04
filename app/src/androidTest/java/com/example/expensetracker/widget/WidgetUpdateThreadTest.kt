package com.example.expensetracker.widget

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.expensetracker.ui.widgets.WidgetUpdateHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Asking Android for the widget ids is a call into the system that can stall when it's busy;
 * made on the main thread at startup it froze the app ("Input dispatching timed out").
 */
@RunWith(AndroidJUnit4::class)
class WidgetUpdateThreadTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val original = WidgetUpdateHelper.request

    @After fun tearDown() { WidgetUpdateHelper.request = original }

    @Test fun anUpdateAskedForOnTheMainThreadRunsOffIt() {
        val done = CountDownLatch(1)
        var onMain = true
        WidgetUpdateHelper.request = { onMain = Looper.myLooper() == Looper.getMainLooper(); done.countDown() }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { WidgetUpdateHelper.update(context) }
        assertTrue("update never ran", done.await(5, TimeUnit.SECONDS))
        assertFalse("ran on the main thread", onMain)
    }

    @Test fun aSlowSystemDoesNotHoldUpTheCaller() {
        val release = CountDownLatch(1)
        WidgetUpdateHelper.request = { release.await(5, TimeUnit.SECONDS) }
        val started = System.nanoTime()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { WidgetUpdateHelper.update(context) }
        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        release.countDown()
        assertTrue("the caller waited ${tookMs}ms", tookMs < 1000)
    }

    @Test fun aFailingUpdateDoesNotCrashTheApp() {
        val done = CountDownLatch(2)
        WidgetUpdateHelper.request = { done.countDown(); error("system busy") }
        WidgetUpdateHelper.update(context)
        WidgetUpdateHelper.update(context)
        assertTrue(done.await(5, TimeUnit.SECONDS))
        // The process is still here to run the next one.
        val next = CountDownLatch(1)
        WidgetUpdateHelper.request = { next.countDown() }
        WidgetUpdateHelper.update(context)
        assertEquals(true, next.await(5, TimeUnit.SECONDS))
    }
}
