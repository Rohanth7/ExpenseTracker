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
 * Runs "Moving from another phone?" restores and remembers what came back. It lives in a
 * ViewModel so rotating mid-restore doesn't cancel it, and the result is saved in prefs so it's
 * still known if the app is closed before setup finishes.
 */
class OnboardingViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = PreferencesManager(app)

    var restoring by mutableStateOf(false)
        private set

    /** The backup restored during this setup, or null. */
    var restored by mutableStateOf(RestoredInfo.fromJson(prefs.onboardingRestore))
        private set

    var restoreError by mutableStateOf<String?>(null)
        private set

    /** True right after a restore, until the screen has moved on from Welcome. */
    var justRestored by mutableStateOf(false)
        private set

    fun restore(uri: Uri) {
        if (restoring) return
        restoring = true
        restoreError = null
        viewModelScope.launch {
            val manager = BackupManager(getApplication())
            when (val read = manager.readFromUri(uri)) {
                is ParseResult.Invalid -> restoreError = read.reason
                is ParseResult.Ok -> try {
                    manager.restore(read.data)
                    val info = restoredInfo(read.data)
                    withContext(Dispatchers.IO) { prefs.onboardingRestore = info.toJson() }
                    restored = info
                    justRestored = true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The database part runs in one transaction, so a failure there changes nothing.
                    restoreError = "The restore didn't finish. Please try again."
                }
            }
            restoring = false
        }
    }

    fun restoreShown() { justRestored = false }
}
