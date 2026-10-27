package com.example.expensetracker.data.period

import android.content.Context
import com.example.expensetracker.data.preferences.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "My month starts on the Nth" as a live value, so every screen recomputes when it changes.
 * The value itself is stored in [PreferencesManager.monthStartDay].
 */
object MonthStartDay {
    @Volatile private var state: MutableStateFlow<Int>? = null

    private fun state(context: Context): MutableStateFlow<Int> = state ?: synchronized(this) {
        state ?: MutableStateFlow(PreferencesManager(context.applicationContext).monthStartDay).also { state = it }
    }

    fun flow(context: Context): StateFlow<Int> = state(context).asStateFlow()

    fun get(context: Context): Int = state(context).value

    fun set(context: Context, day: Int) {
        val prefs = PreferencesManager(context.applicationContext)
        prefs.monthStartDay = day
        state(context).value = prefs.monthStartDay
    }

    /** After something else changed the stored value (a restore or a reset). */
    fun reload(context: Context) {
        state(context).value = PreferencesManager(context.applicationContext).monthStartDay
    }
}
