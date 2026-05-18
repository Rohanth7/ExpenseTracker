package com.example.expensetracker.data.preferences

import android.content.Context

class PreferencesManager(context: Context) {
    private val prefs = context.getSharedPreferences("expense_tracker_prefs", Context.MODE_PRIVATE)

    var monthlyIncome: Double
        get() = Double.fromBits(prefs.getLong("monthly_income", 0L))
        set(value) = prefs.edit().putLong("monthly_income", value.toBits()).apply()

    var privacyMode: Boolean
        get() = prefs.getBoolean("privacy_mode", false)
        set(value) = prefs.edit().putBoolean("privacy_mode", value).apply()

    var budgetAlertsEnabled: Boolean
        get() = prefs.getBoolean("budget_alerts_enabled", true)
        set(value) = prefs.edit().putBoolean("budget_alerts_enabled", value).apply()

    var budgetAlertThreshold: Int
        get() = prefs.getInt("budget_alert_threshold", 80)
        set(value) = prefs.edit().putInt("budget_alert_threshold", value).apply()

    var weekStartsOnMonday: Boolean
        get() = prefs.getBoolean("week_starts_monday", true)
        set(value) = prefs.edit().putBoolean("week_starts_monday", value).apply()

    var biometricLockEnabled: Boolean
        get() = prefs.getBoolean("biometric_lock_enabled", false)
        set(value) = prefs.edit().putBoolean("biometric_lock_enabled", value).apply()

    var widgetEnabled: Boolean
        get() = prefs.getBoolean("widget_enabled", true)
        set(value) = prefs.edit().putBoolean("widget_enabled", value).apply()

    var autoBackupEnabled: Boolean
        get() = prefs.getBoolean("auto_backup_enabled", false)
        set(value) = prefs.edit().putBoolean("auto_backup_enabled", value).apply()

    var driveConnected: Boolean
        get() = prefs.getBoolean("drive_connected", false)
        set(value) = prefs.edit().putBoolean("drive_connected", value).apply()

    var driveAccountEmail: String
        get() = prefs.getString("drive_account_email", "") ?: ""
        set(value) = prefs.edit().putString("drive_account_email", value).apply()
}
