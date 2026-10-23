package com.example.expensetracker

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.notification.NotificationHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The widget's "+" end to end, from a cold start: the launch opens the add dialog, and the same
 * intent re-delivered from Recents (FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) doesn't open it again.
 * Also from Categorize, opened by a notification while on the Expenses tab. Uses the app's real
 * preferences and database; the expense it adds is deleted afterwards.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class QuickAddLaunchTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun skipOnboardingAndLock() {
        PreferencesManager(context).apply { onboardingCompleted = true; biometricLockEnabled = false }
    }

    private val db = AppDatabase.getInstance(context)
    private var pendingExpense: Expense? = null

    @After fun deleteTestExpense() = runBlocking { pendingExpense?.let { db.expenseDao().delete(it) }; Unit }

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

    @Test fun quickAddFromCategorizeOpensTheAddDialog() {
        // The "+" restored the Expenses tab's stack with Categorize still on top, so the dialog
        // stayed unseen behind it and popped up later, once the user went back to the list.
        val expense = Expense(amount = 42.0, description = "QuickAddLaunchTest", categoryId = -1, date = System.currentTimeMillis())
        pendingExpense = expense.copy(id = runBlocking { db.expenseDao().insert(expense) })
        // Delivered to the running activity (onNewIntent), as a tap with the app open is.
        val categorize = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(NotificationHelper.EXTRA_EXPENSE_ID, pendingExpense!!.id)
        }
        val quickAdd = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(LaunchAction.EXTRA_ACTION, LaunchAction.QUICK_ADD)
        }
        // A plain intent, not launch(MainActivity::class) (MAIN/LAUNCHER): ActivityScenario only tracks
        // an activity whose intent still matches its launch intent, and onNewIntent replaces it.
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            compose.waitUntilAtLeastOneExists(hasText("Expenses") and hasClickAction(), timeoutMillis = 30_000)
            compose.onNode(hasText("Expenses") and hasClickAction()).performClick()
            scenario.onActivity { it.startActivity(categorize) }
            compose.waitUntilAtLeastOneExists(hasText("Categorize Expense"), timeoutMillis = 10_000)
            scenario.onActivity { it.startActivity(quickAdd) }
            compose.waitUntilAtLeastOneExists(hasText("Add Expense"), timeoutMillis = 10_000)
        }
    }
}
