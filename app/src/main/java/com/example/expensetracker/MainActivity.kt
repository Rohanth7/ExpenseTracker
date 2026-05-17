package com.example.expensetracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.expensetracker.notification.NotificationHelper
import com.example.expensetracker.ui.navigation.AppNavGraph
import com.example.expensetracker.ui.theme.ExpenseTrackerTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions handled silently; SMS receiver only works if granted */ }

    private var pendingExpenseId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestMissingPermissions()

        pendingExpenseId = intent?.getLongExtra(NotificationHelper.EXTRA_EXPENSE_ID, -1L)
            ?.takeIf { it != -1L }

        setContent {
            ExpenseTrackerTheme {
                AppNavGraph(
                    initialExpenseId = pendingExpenseId,
                    onExpenseNavigated = { pendingExpenseId = null }
                )
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
