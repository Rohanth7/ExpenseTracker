package com.example.expensetracker.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.notification.NotificationHelper
import com.example.expensetracker.notification.PriceChangeAlerts
import org.junit.Assert.assertEquals
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

    @Test fun thePriceAlertIsNotTheSameIntentAsAnyOtherTap() {
        val alert = PriceChangeAlerts.openSubscriptionsIntent(context)
        assertNotEquals(widgetQuickAddIntent(context), alert)
        assertNotEquals(widgetOpenAppIntent(context), alert)
        for (expenseId in 0L..5L) assertNotEquals("price alert vs notification for expense $expenseId", NotificationHelper.categorizeIntent(context, expenseId), alert)
    }

    /** BUG-12: request code expense × 100 + category made expense 1's "category 101" button expense 2's "category 1". */
    @Test fun categoryButtonsOfDifferentExpensesAreDifferentIntents() {
        assertNotEquals(NotificationHelper.categoryActionIntent(context, 1, 101), NotificationHelper.categoryActionIntent(context, 2, 1))
        assertNotEquals(NotificationHelper.categoryActionIntent(context, 1, 5), NotificationHelper.categoryActionIntent(context, 1, 6))
        assertEquals(NotificationHelper.categoryActionIntent(context, 1, 5), NotificationHelper.categoryActionIntent(context, 1, 5))
    }

    @Test fun theWidgetsTwoTapsAreDifferentIntents() =
        assertNotEquals(widgetOpenAppIntent(context), widgetQuickAddIntent(context))
}
