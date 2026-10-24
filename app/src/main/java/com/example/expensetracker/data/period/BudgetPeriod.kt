package com.example.expensetracker.data.period

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class DateRange(val start: Long, val endInclusive: Long) {
    operator fun contains(t: Long) = t in start..endInclusive
}

/**
 * The single source of truth for "this month". With a salary cycle (e.g. day 25) a month runs
 * 25th → 24th. Start days are limited to 1–28 because 29–31 don't exist in every month.
 * Recurring bills and EMIs keep their calendar due days; they don't use this.
 */
object BudgetPeriod {
    const val MIN_START_DAY = 1
    const val MAX_START_DAY = 28
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    fun current(now: Long, monthStartDay: Int): DateRange = forOffset(now, monthStartDay, 0)

    fun forOffset(now: Long, monthStartDay: Int, offset: Int): DateRange {
        val day = monthStartDay.coerceIn(MIN_START_DAY, MAX_START_DAY)
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        if (cal.get(Calendar.DAY_OF_MONTH) < day) cal.add(Calendar.MONTH, -1)
        cal.set(Calendar.DAY_OF_MONTH, day)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.MONTH, offset)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        cal.add(Calendar.MILLISECOND, -1)
        return DateRange(start, cal.timeInMillis)
    }

    fun lastNMonths(now: Long, monthStartDay: Int, n: Int): DateRange =
        DateRange(forOffset(now, monthStartDay, -(n - 1)).start, current(now, monthStartDay).endInclusive)

    fun daysLeft(now: Long, monthStartDay: Int): Int {
        val end = current(now, monthStartDay).endInclusive
        return (Math.round((startOfDay(end) - startOfDay(now)).toDouble() / DAY_MS) + 1).toInt()
    }

    fun dayOfCycle(now: Long, monthStartDay: Int): Int =
        (Math.round((startOfDay(now) - current(now, monthStartDay).start).toDouble() / DAY_MS) + 1).toInt()

    fun daysInCycle(range: DateRange): Int =
        (Math.round((startOfDay(range.endInclusive) - range.start).toDouble() / DAY_MS) + 1).toInt()

    fun label(range: DateRange, monthStartDay: Int, locale: Locale = Locale.ENGLISH): String =
        if (monthStartDay.coerceIn(MIN_START_DAY, MAX_START_DAY) == 1) SimpleDateFormat("MMM yyyy", locale).format(range.start)
        else SimpleDateFormat("d MMM", locale).let { "${it.format(range.start)} – ${it.format(range.endInclusive)}" }

    private fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
