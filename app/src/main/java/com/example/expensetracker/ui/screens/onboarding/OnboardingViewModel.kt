package com.example.expensetracker.ui.screens.onboarding

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.backup.BackupManager
import com.example.expensetracker.data.backup.ParseResult
import com.example.expensetracker.data.preferences.PreferencesManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs "Moving from another phone?" restores. It lives in a ViewModel so rotating the phone or
 * switching dark mode mid-restore doesn't cancel it or lose the result.
 */
class OnboardingViewModel(app: Application) : AndroidViewModel(app) {
    var restoring by mutableStateOf(false)
        private set

    /** The finished restore, until the screen has shown it. */
    var outcome by mutableStateOf<RestoreOutcome?>(null)
        private set

    fun restore(uri: Uri) {
        if (restoring) return
        restoring = true
        viewModelScope.launch {
            outcome = restoreBackup(uri)
            restoring = false
        }
    }

    fun outcomeShown() { outcome = null }

    private suspend fun restoreBackup(uri: Uri): RestoreOutcome {
        val manager = BackupManager(getApplication())
        return when (val read = manager.readFromUri(uri)) {
            is ParseResult.Invalid -> RestoreOutcome.Failed(read.reason)
            is ParseResult.Ok -> try {
                withContext(Dispatchers.IO) { PreferencesManager(getApplication()).onboardingRestoreStarted = true }
                manager.restore(read.data)
                RestoreOutcome.Restored(restoreSummary(read.data), restoredIncome(read.data))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Part of it may have been saved (the database is restored first, in one go). A
                // restore replaces everything, so trying again is safe.
                RestoreOutcome.Failed("The restore didn't finish. Please try again.")
            }
        }
    }
}
