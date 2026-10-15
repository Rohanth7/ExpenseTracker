package com.example.expensetracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.defaults.DefaultCategories
import com.example.expensetracker.ui.screens.onboarding.OnboardingScreen
import com.example.expensetracker.ui.theme.Canvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.expensetracker.data.backup.BackupManager
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.notification.NotificationHelper
import com.example.expensetracker.ui.navigation.AppNavGraph
import com.example.expensetracker.ui.theme.ExpenseTrackerTheme
import com.example.expensetracker.ui.util.BiometricUtil
import com.example.expensetracker.ui.widgets.WidgetUpdateHelper
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private var pendingExpenseId by mutableStateOf<Long?>(null)
    private var isUnlocked by mutableStateOf(false)
    // null while checking whether this is a new install
    private var showOnboarding by mutableStateOf<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = PreferencesManager(this)
        if (prefs.onboardingCompleted) {
            showOnboarding = false
        } else {
            lifecycleScope.launch {
                showOnboarding = withContext(Dispatchers.IO) { needsOnboarding(prefs) }
            }
        }
        
        // Handle Auto-Backup Schedule
        if (prefs.autoBackupEnabled) {
            BackupManager(this).scheduleAutoBackup()
        }
        if (prefs.biometricLockEnabled && !isUnlocked) {
            BiometricUtil.showBiometricPrompt(
                this,
                onSuccess = { isUnlocked = true },
                onError = { finish() } // Exit app if authentication fails or is cancelled
            )
        } else {
            isUnlocked = true
        }

        MainScope().launch {
            WidgetUpdateHelper.update(applicationContext)
        }

        pendingExpenseId = intent?.getLongExtra(NotificationHelper.EXTRA_EXPENSE_ID, -1L)
            ?.takeIf { it != -1L }

        setContent {
            ExpenseTrackerTheme {
                if (isUnlocked) {
                    when (showOnboarding) {
                        null -> Box(Modifier.fillMaxSize().background(Canvas))
                        true -> OnboardingScreen(onFinish = { income -> finishOnboarding(prefs, income) })
                        false -> AppNavGraph(
                            initialExpenseId = pendingExpenseId,
                            onExpenseNavigated = { pendingExpenseId = null }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingExpenseId = intent.getLongExtra(NotificationHelper.EXTRA_EXPENSE_ID, -1L)
            .takeIf { it != -1L }
    }

    /**
     * A new install gets first-run setup. An existing install that already has data (upgrading
     * from a version without onboarding) skips it, but still gets the starter categories if it
     * has none, so auto-captured payments can be sorted.
     */
    private suspend fun needsOnboarding(prefs: PreferencesManager): Boolean {
        val db = AppDatabase.getInstance(applicationContext)
        val hasData = db.categoryDao().getCount() > 0 || db.expenseDao().count() > 0
        if (!hasData) return true
        DefaultCategories.seedIfEmpty(db)
        prefs.onboardingCompleted = true
        return false
    }

    private fun finishOnboarding(prefs: PreferencesManager, monthlyIncome: Double?) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                DefaultCategories.seedIfEmpty(AppDatabase.getInstance(applicationContext))
                if (monthlyIncome != null) prefs.monthlyIncome = monthlyIncome
                prefs.onboardingCompleted = true
            }
            showOnboarding = false
        }
    }
}
