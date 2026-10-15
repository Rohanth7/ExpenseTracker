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
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.example.expensetracker.security.AppLock
import com.example.expensetracker.ui.screens.lock.LockedScreen
import com.example.expensetracker.ui.theme.Ink
import com.example.expensetracker.ui.theme.Jade
import com.example.expensetracker.ui.theme.Muted
import com.example.expensetracker.ui.theme.Paper
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
    // App lock. The lock state itself is process-wide (AppLock), so a rotation doesn't ask again.
    private var locked by mutableStateOf(false)
    private var lockMessage by mutableStateOf<String?>(null)
    private var showLockTurnedOff by mutableStateOf(false)
    private var promptShowing = false
    private lateinit var prefs: PreferencesManager
    // null while checking whether this is a new install
    private var showOnboarding by mutableStateOf<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        prefs = PreferencesManager(this)
        // Privacy mode was only applied once Settings was opened, so the app switcher showed the
        // dashboard after every restart. Settings still toggles it live.
        if (prefs.privacyMode) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        // Decided again in onStart; set here too so the first frame never shows unlocked content.
        locked = prefs.biometricLockEnabled && !AppLock.state.unlocked
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

        MainScope().launch {
            WidgetUpdateHelper.update(applicationContext)
        }

        pendingExpenseId = intent?.getLongExtra(NotificationHelper.EXTRA_EXPENSE_ID, -1L)
            ?.takeIf { it != -1L }

        setContent {
            ExpenseTrackerTheme {
                Box(Modifier.fillMaxSize()) {
                    // The app stays composed under the lock screen, so a file picker or Google
                    // sign-in that was open when it locked can still deliver its result. While
                    // locked it's hidden from screen readers too.
                    Box(Modifier.fillMaxSize().then(if (locked) Modifier.clearAndSetSemantics {} else Modifier)) {
                        when (showOnboarding) {
                            null -> Box(Modifier.fillMaxSize().background(Canvas))
                            true -> OnboardingScreen(onFinish = { income -> finishOnboarding(prefs, income) })
                            false -> AppNavGraph(
                                initialExpenseId = pendingExpenseId,
                                onExpenseNavigated = { pendingExpenseId = null }
                            )
                        }
                    }
                    if (locked) LockedScreen(message = lockMessage, onUnlock = ::promptUnlock)
                    if (showLockTurnedOff) {
                        AlertDialog(
                            onDismissRequest = { showLockTurnedOff = false },
                            containerColor = Paper, titleContentColor = Ink, textContentColor = Muted,
                            title = { Text("App lock turned off") },
                            text = { Text("This phone no longer has a screen lock, so MyMoney can't ask for one. " +
                                "Set up a PIN, pattern or fingerprint in Android settings, then turn the lock " +
                                "back on in Settings → Preferences.") },
                            confirmButton = { TextButton(onClick = { showLockTurnedOff = false }) { Text("OK", color = Jade) } }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val mustLock = AppLock.state.onForeground(prefs.biometricLockEnabled, SystemClock.elapsedRealtime())
        if (!mustLock) {
            locked = false
        } else if (!BiometricUtil.canAuthenticate(this)) {
            // The screen lock was removed after the app lock was turned on. Asking would fail
            // forever and lock the user out of their own data, so turn the app lock off and say so.
            prefs.biometricLockEnabled = false
            AppLock.state.unlock()
            locked = false
            showLockTurnedOff = true
        } else {
            locked = true
            promptUnlock()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) AppLock.state.onBackground(SystemClock.elapsedRealtime())
    }

    private fun promptUnlock() {
        if (promptShowing) return
        promptShowing = true
        lockMessage = null
        BiometricUtil.showBiometricPrompt(
            this,
            onSuccess = {
                promptShowing = false
                AppLock.state.unlock()
                locked = false
            },
            // Cancelling just leaves the lock screen up; tapping Unlock asks again.
            onError = { message ->
                promptShowing = false
                lockMessage = message
            }
        )
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
