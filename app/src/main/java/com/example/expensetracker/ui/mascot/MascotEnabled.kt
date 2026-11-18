package com.example.expensetracker.ui.mascot

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import com.example.expensetracker.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Show the pot" as a live value, so she appears and disappears without a restart. Stored in
 * [PreferencesManager.mascotEnabled].
 */
object MascotEnabled {
    @Volatile private var state: MutableStateFlow<Boolean>? = null

    private fun state(context: Context): MutableStateFlow<Boolean> = state ?: synchronized(this) {
        state ?: MutableStateFlow(PreferencesManager(context.applicationContext).mascotEnabled).also { state = it }
    }

    fun flow(context: Context): StateFlow<Boolean> = state(context).asStateFlow()

    fun set(context: Context, enabled: Boolean) {
        PreferencesManager(context.applicationContext).mascotEnabled = enabled
        state(context).value = enabled
    }
}

/** Whether she is shown. Provided once by MainActivity; screens read this, not the preference. */
val LocalMascotEnabled = compositionLocalOf { true }
