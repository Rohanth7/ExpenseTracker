package com.example.expensetracker.data.preferences

import android.content.Context

class PreferencesManager(context: Context, fileName: String = "expense_tracker_prefs") {
    private val prefs = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)

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

    /** Set once the first-run setup has been finished or skipped for an existing install. */
    var onboardingCompleted: Boolean
        get() = prefs.getBoolean("onboarding_completed", false)
        set(value) = prefs.edit().putBoolean("onboarding_completed", value).apply()

    /** Set on the first launch, after checking whether this install predates setup (`SetupGate`). */
    var onboardingFirstLaunchChecked: Boolean
        get() = prefs.getBoolean("onboarding_first_launch_checked", false)
        set(value) { prefs.edit().putBoolean("onboarding_first_launch_checked", value).commit() }

    /** `RestoredInfo` JSON for a backup restored during setup; cleared when setup finishes. */
    var onboardingRestore: String?
        get() = prefs.getString("onboarding_restore", null)
        set(value) { prefs.edit().putString("onboarding_restore", value).commit() }

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
