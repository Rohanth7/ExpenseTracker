package com.example.expensetracker.ui.util

import java.util.Calendar
import java.util.TimeZone

// Material 3 date pickers return the chosen day as UTC midnight. These turn it into the
// matching local day's start or end (23:59:59.999), which is what date ranges here use.

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
