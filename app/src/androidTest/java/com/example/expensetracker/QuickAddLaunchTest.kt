package com.example.expensetracker

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.data.preferences.PreferencesManager
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The widget's "+" end to end, from a cold start: the launch opens the add dialog, and the same
 * intent re-delivered from Recents (FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) doesn't open it again.
 * Uses the app's real preferences; the test run uninstalls the app afterwards anyway.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class QuickAddLaunchTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun skipOnboardingAndLock() {
        PreferencesManager(context).apply { onboardingCompleted = true; biometricLockEnabled = false }
    }

    private fun quickAddIntent(extraFlags: Int = 0) = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or extraFlags)
        putExtra(LaunchAction.EXTRA_ACTION, LaunchAction.QUICK_ADD)
    }

    @Test fun coldStartFromTheWidgetOpensTheAddDialog() {
        ActivityScenario.launch<MainActivity>(quickAddIntent()).use {
            compose.waitUntilAtLeastOneExists(hasText("Add Expense"), timeoutMillis = 30_000)
        }
    }

    @Test fun reopeningFromRecentsDoesNotReplayIt() {
        ActivityScenario.launch<MainActivity>(quickAddIntent(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)).use {
            // The Expenses tab's label shows once the app is up; the dialog must not follow.
            compose.waitUntilAtLeastOneExists(hasText("Overview"), timeoutMillis = 30_000)
            compose.waitForIdle()
            Thread.sleep(2_000)  // give a wrongly replayed launch time to open the dialog
            compose.waitForIdle()
            assertTrue("add dialog opened from Recents", compose.onAllNodes(hasText("Add Expense")).fetchSemanticsNodes().isEmpty())
        }
    }
}
