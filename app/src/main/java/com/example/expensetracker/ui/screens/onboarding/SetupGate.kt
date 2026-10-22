package com.example.expensetracker.ui.screens.onboarding

/**
 * Whether the app opens on first-run setup. Only the "setup finished" flag decides, except once:
 * on the first launch of a version with setup, an install that already has data (from before setup
 * existed) skips it. Checking for data every launch would end setup early whenever data appears
 * during it, from a restore or an SMS captured after SMS access was granted.
 */
object SetupGate {
    enum class Decision { App, Setup, SkipForUpgrade }

    fun decide(completed: Boolean, firstLaunchChecked: Boolean, hasData: Boolean): Decision = when {
        completed -> Decision.App
        !firstLaunchChecked && hasData -> Decision.SkipForUpgrade
        else -> Decision.Setup
    }
}
