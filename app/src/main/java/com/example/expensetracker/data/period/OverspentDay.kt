package com.example.expensetracker.data.period

/**
 * The day this budget month's money ran out, so the pot pleads on that day and is only sad after
 * it. One day is kept, under its budget month's start, and only while the month is still over.
 */
class OverspentDay(private val store: Store) {

    interface Store {
        /** Every stored day, by key. */
        fun all(): Map<String, Long>
        fun put(key: String, epochDay: Long)
        fun remove(keys: Collection<String>)
    }

    /**
     * Days since the money ran out, 0 on the day it happens and when it hasn't. Records today
     * the first time [freeToSpend] is below zero; forgets it once it's back to zero or more.
     */
    fun daysOver(freeToSpend: Double, monthStartEpochDay: Long, todayEpochDay: Long): Int {
        val key = PREFIX + monthStartEpochDay
        val stored = store.all()
        // A new budget month starts clean.
        store.remove(stored.keys - key)
        if (freeToSpend >= 0) {
            if (key in stored) store.remove(listOf(key))
            return 0
        }
        val since = stored[key] ?: todayEpochDay.also { store.put(key, it) }
        return (todayEpochDay - since).coerceAtLeast(0).toInt()
    }

    companion object {
        const val PREFIX = "overspent_since_"
    }
}
