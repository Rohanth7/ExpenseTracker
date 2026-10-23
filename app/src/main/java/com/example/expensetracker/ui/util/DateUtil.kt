package com.example.expensetracker.ui.util

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import java.util.Calendar
import java.util.TimeZone

// Material 3 date pickers work in UTC midnights: each day is shown and returned as 00:00 UTC.
// These convert between that and local time, so every picker in the app agrees on what "today" is.

/** The picker value for the local day that [localMillis] falls on. */
fun toUtcMidnight(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/** For pickers that only allow past days and today. Compares UTC midnights, never the current instant. */
@OptIn(ExperimentalMaterial3Api::class)
object PastOrTodayDates : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= toUtcMidnight(System.currentTimeMillis())
}

/** The picker's day, at the local time of day of [referenceMillis] (used when editing an expense's date). */
fun buildTimestamp(utcMidnight: Long, referenceMillis: Long): Long {
    val time = Calendar.getInstance().apply { timeInMillis = referenceMillis }
    return localDay(utcMidnight, time.get(Calendar.HOUR_OF_DAY), time.get(Calendar.MINUTE), time.get(Calendar.SECOND), 0)
}

/** The start (00:00) or end (23:59:59.999) of the local day the picker chose, for date ranges. */
fun utcMidnightToLocalDayStart(utcMillis: Long): Long = localDay(utcMillis, 0, 0, 0, 0)

fun utcMidnightToLocalDayEnd(utcMillis: Long): Long = localDay(utcMillis, 23, 59, 59, 999)

private fun localDay(utcMillis: Long, h: Int, m: Int, s: Int, ms: Int): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), h, m, s)
        set(Calendar.MILLISECOND, ms)
    }.timeInMillis
}
