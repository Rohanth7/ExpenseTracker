package com.example.expensetracker.data.preferences

import android.content.Context

class PreferencesManager(context: Context) {
    private val prefs = context.getSharedPreferences("expense_tracker_prefs", Context.MODE_PRIVATE)

    var monthlyIncome: Double
        get() = Double.fromBits(prefs.getLong("monthly_income", 0L))
        set(value) = prefs.edit().putLong("monthly_income", value.toBits()).apply()
}
