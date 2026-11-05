package com.example.expensetracker.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.ui.widgets.WidgetUpdateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Asking Android for the widget ids is a call into the system that can stall when it's busy;
 * made on the main thread at startup it froze the app ("Input dispatching timed out"). The caller
 * still waits for it (off the main thread), so an SMS receiver's refresh finishes before the
 * receiver tells Android it's done.
 */
@RunWith(AndroidJUnit4::class)
class WidgetUpdateThreadTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val original = WidgetUpdateHelper.request

    @After fun tearDown() { WidgetUpdateHelper.request = original }

    @Test fun anUpdateAskedForOnTheMainThreadRunsOffIt() {
        var onMain = true
        WidgetUpdateHelper.request = { onMain = Looper.myLooper() == Looper.getMainLooper() }
        runBlocking(Dispatchers.Main) { WidgetUpdateHelper.update(context) }
        assertFalse("ran on the main thread", onMain)
    }

    @Test fun theMainThreadStaysFreeWhileTheSystemIsSlow() {
        val release = CountDownLatch(1)
        WidgetUpdateHelper.request = { release.await(10, TimeUnit.SECONDS) }
        CoroutineScope(Dispatchers.Main).launch { WidgetUpdateHelper.update(context) }
        // Something else the main thread has to do (a tap, a frame) runs while the refresh waits.
        val mainRan = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post { mainRan.countDown() }
        val free = mainRan.await(1, TimeUnit.SECONDS)
        release.countDown()
        assertTrue("the main thread was blocked", free)
    }

    @Test fun theCallerWaitsUntilTheRefreshIsDone() {
        var done = false
        WidgetUpdateHelper.request = { Thread.sleep(200); done = true }
        runBlocking { WidgetUpdateHelper.update(context) }
        assertTrue("returned before the refresh finished", done)
    }

    @Test fun aFailingUpdateDoesNotReachTheCaller() {
        WidgetUpdateHelper.request = { error("system busy") }
        runBlocking { WidgetUpdateHelper.update(context) }
    }
}
