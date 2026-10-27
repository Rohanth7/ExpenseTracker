package com.example.expensetracker.ui.screens.onboarding

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.backup.BackupData
import com.example.expensetracker.data.backup.BackupManager
import com.example.expensetracker.data.backup.ParseResult
import com.example.expensetracker.data.preferences.PreferencesManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs "Moving from another phone?" restores and remembers what came back. It lives in a
 * ViewModel so rotating mid-restore doesn't cancel it, and the result is saved in prefs so it's
 * still known if the app is closed before setup finishes.
 */
class OnboardingViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = PreferencesManager(app)
    private val manager = BackupManager(app)

    /** True while a file is being read or restored. */
    var busy by mutableStateOf(false)
        private set

    /** A checked backup waiting for the user to confirm; nothing has been replaced yet. */
    var pending by mutableStateOf<BackupData?>(null)
        private set

    /** The backup restored during this setup, or null. */
    var restored by mutableStateOf(RestoredInfo.fromJson(prefs.onboardingRestore))
        private set

    var restoreError by mutableStateOf<String?>(null)
        private set

    /** True right after a restore, until the screen has moved on from Welcome. */
    var justRestored by mutableStateOf(false)
        private set

    fun pick(uri: Uri) {
        if (busy) return
        busy = true
        restoreError = null
        viewModelScope.launch {
            when (val read = manager.readFromUri(uri)) {
                is ParseResult.Invalid -> restoreError = read.reason
                is ParseResult.Ok -> pending = read.data
            }
            busy = false
        }
    }

    fun cancel() { pending = null }

    fun confirm() {
        val data = pending ?: return
        pending = null
        busy = true
        viewModelScope.launch {
            try {
                val info = restoredInfo(data)
                // Once the database is replaced, setup must learn about it, even if the user
                // leaves the app this instant; so the restore and the note about it can't be cancelled.
                withContext(NonCancellable) {
                    manager.restore(data)
                    prefs.onboardingRestore = info.toJson()
                }
                restored = info
                justRestored = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The database part runs in one transaction, so a failure there changes nothing.
                restoreError = "The restore didn't finish. Please try again."
            }
            busy = false
        }
    }

    fun restoreShown() { justRestored = false }

    /** Setup is done; forget the restore so a later "Reset everything" starts clean. */
    fun finished() { restored = null }
}
