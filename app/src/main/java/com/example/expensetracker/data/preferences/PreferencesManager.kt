package com.example.expensetracker.data.preferences

import android.content.Context
import com.example.expensetracker.data.period.BudgetPeriod

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

    /** Day the budget month starts, 1–28 (1 = calendar month). Read it live through `MonthStartDay`. */
    var monthStartDay: Int
        get() = BudgetPeriod.clampStartDay(prefs.getInt("month_start_day", 1))
        set(value) = prefs.edit().putInt("month_start_day", BudgetPeriod.clampStartDay(value)).apply()

    /** Set once the first-run setup has been finished or skipped for an existing install. */
    var onboardingCompleted: Boolean
        get() = prefs.getBoolean("onboarding_completed", false)
        set(value) = prefs.edit().putBoolean("onboarding_completed", value).apply()

    /**
     * Set on the first launch (and the first after "Reset everything"), once `SetupGate` has
     * checked whether existing data means setup can be skipped.
     */
    var onboardingFirstLaunchChecked: Boolean
        get() = prefs.getBoolean("onboarding_first_launch_checked", false)
        set(value) = prefs.edit().putBoolean("onboarding_first_launch_checked", value).apply()

    /** `RestoredInfo` JSON for a backup restored during setup; cleared when setup finishes. */
    var onboardingRestore: String?
        get() = prefs.getString("onboarding_restore", null)
        set(value) = prefs.edit().putString("onboarding_restore", value).apply()

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

    /** The category last picked when adding an expense by hand; the add dialog starts on it. */
    var lastManualCategoryId: Long
        get() = prefs.getLong("last_manual_category_id", -1L)
        set(value) = prefs.edit().putLong("last_manual_category_id", value).apply()
}
