package com.example.expensetracker.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.notification.NotificationHelper
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PendingIntents that Android considers the same (same target, action, data and request code) share
 * one set of extras: FLAG_UPDATE_CURRENT makes the last one built overwrite the other.
 */
@RunWith(AndroidJUnit4::class)
class LaunchIntentsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun widgetTapsAreNotTheSameIntentAsAnyCategorizeNotification() {
        // Expense ids start at 1; request codes are the masked id, so 0 and 1 are the ones at risk.
        for (expenseId in 0L..5L) {
            val notification = NotificationHelper.categorizeIntent(context, expenseId)
            assertNotEquals("widget + vs notification for expense $expenseId", widgetQuickAddIntent(context), notification)
            assertNotEquals("widget tap vs notification for expense $expenseId", widgetOpenAppIntent(context), notification)
        }
    }

    @Test fun theWidgetsTwoTapsAreDifferentIntents() =
        assertNotEquals(widgetOpenAppIntent(context), widgetQuickAddIntent(context))
}
