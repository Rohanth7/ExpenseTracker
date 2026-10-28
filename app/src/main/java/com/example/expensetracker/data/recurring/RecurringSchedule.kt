package com.example.expensetracker.data.recurring

import java.util.Calendar

object RecurringSchedule {
    /**
     * Midnight of the bill's due day this month, or null if that day hasn't come yet. Day 31 means
     * the last day of a shorter month.
     */
    fun dueDateIfReached(dueDayOfMonth: Int, now: Calendar): Long? {
        val dueDay = dueDayOfMonth.coerceIn(1, now.getActualMaximum(Calendar.DAY_OF_MONTH))
        if (now.get(Calendar.DAY_OF_MONTH) < dueDay) return null
        return (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, dueDay)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** First and last millisecond of [at]'s calendar month. */
    fun monthBounds(at: Calendar): Pair<Long, Long> {
        val start = (at.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }.timeInMillis - 1
        return start.timeInMillis to end
    }
}
