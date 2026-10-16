package com.example.expensetracker.data.subscriptions

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Payees the user marked "not a subscription". Shared so the Overview card updates at once. */
object DismissedSubscriptions {
    private const val PREFS = "expense_tracker_prefs"
    private const val KEY = "dismissed_subscriptions"
    @Volatile private var state: MutableStateFlow<Set<String>>? = null

    private fun state(context: Context): MutableStateFlow<Set<String>> = state ?: synchronized(this) {
        state ?: MutableStateFlow(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet())!!.toSet()
        ).also { state = it }
    }

    fun flow(context: Context): StateFlow<Set<String>> = state(context.applicationContext).asStateFlow()

    fun dismiss(context: Context, key: String) = update(context) { it + key }
    fun restore(context: Context, key: String) = update(context) { it - key }

    private fun update(context: Context, change: (Set<String>) -> Set<String>) {
        val s = state(context.applicationContext)
        val next = change(s.value)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, next).apply()
        s.value = next
    }
}
