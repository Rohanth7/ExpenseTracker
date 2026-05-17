package com.example.expensetracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.notification.NotificationHelper
import com.example.expensetracker.ui.navigation.AppNavGraph
import com.example.expensetracker.ui.theme.ExpenseTrackerTheme
import com.example.expensetracker.ui.util.BiometricUtil
import com.example.expensetracker.ui.widgets.WidgetUpdateHelper
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions handled silently; SMS receiver only works if granted */ }

    private var pendingExpenseId by mutableStateOf<Long?>(null)
    private var isUnlocked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestMissingPermissions()

        val prefs = PreferencesManager(this)
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
                    AppNavGraph(
                        initialExpenseId = pendingExpenseId,
                        onExpenseNavigated = { pendingExpenseId = null }
                    )
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

    private fun requestMissingPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.RECEIVE_SMS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }
}
