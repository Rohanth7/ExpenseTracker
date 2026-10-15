package com.example.expensetracker

import android.app.Application
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.privacy.CapturedTextRetention
import com.example.expensetracker.data.mapping.MappingCleanup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.example.expensetracker.notification.BudgetAlertHelper
import com.example.expensetracker.notification.NotificationHelper

class ExpenseTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        BudgetAlertHelper.createChannel(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { MappingCleanup.runOnce(this@ExpenseTrackerApp) }
            // After the cleanup above, which reads captured text. Runs whenever the process starts,
            // including for an incoming SMS, so it happens even if the app is never opened.
            runCatching { CapturedTextRetention.purge(AppDatabase.getInstance(this@ExpenseTrackerApp)) }
        }
    }
}
