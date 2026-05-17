package com.example.expensetracker

import android.app.Application
import com.example.expensetracker.notification.BudgetAlertHelper
import com.example.expensetracker.notification.NotificationHelper

class ExpenseTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        BudgetAlertHelper.createChannel(this)
    }
}
