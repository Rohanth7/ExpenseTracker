package com.example.expensetracker

import android.app.Application
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
        }
    }
}
