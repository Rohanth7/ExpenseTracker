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

    fun clampStartDay(day: Int): Int = day.coerceIn(MIN_START_DAY, MAX_START_DAY)

    /** "1st", "2nd", "23rd", "28th". */
    fun ordinal(day: Int): String = day.toString() + when {
        day % 100 in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }

    fun current(now: Long, monthStartDay: Int): DateRange = forOffset(now, monthStartDay, 0)

    fun forOffset(now: Long, monthStartDay: Int, offset: Int): DateRange {
        val day = clampStartDay(monthStartDay)
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

    /** From the start of [from]'s day to the end (23:59:59.999) of [to]'s day. */
    fun dayRange(from: Long, to: Long): DateRange {
        val end = Calendar.getInstance().apply { timeInMillis = startOfDay(to); add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis - 1
        return DateRange(startOfDay(from), end)
    }

    /** The same number of days as [range], ending the day before it starts (for "vs previous" comparisons). */
    fun previousOfSameLength(range: DateRange): DateRange {
        val start = Calendar.getInstance().apply { timeInMillis = startOfDay(range.start); add(Calendar.DAY_OF_YEAR, -daysInCycle(range)) }
        return DateRange(start.timeInMillis, startOfDay(range.start) - 1)
    }

    fun daysInCycle(range: DateRange): Int =
        (Math.round((startOfDay(range.endInclusive) - range.start).toDouble() / DAY_MS) + 1).toInt()

    /** "Oct 2026" for calendar months; "25 Sep – 24 Oct" for cycles, with years unless it's all in [now]'s year. */
    fun label(range: DateRange, monthStartDay: Int, now: Long = System.currentTimeMillis(), locale: Locale = Locale.ENGLISH): String =
        if (clampStartDay(monthStartDay) == 1) SimpleDateFormat("MMM yyyy", locale).format(range.start)
        else dayRangeLabel(range.start, range.endInclusive, now, locale)

    /** "1 Oct – 13 Oct", or "5 Oct" for one day; with years unless it's all in [now]'s year. */
    fun dayRangeLabel(start: Long, end: Long, now: Long = System.currentTimeMillis(), locale: Locale = Locale.ENGLISH): String {
        val year = SimpleDateFormat("yyyy", locale)
        val thisYear = year.format(start) == year.format(end) && year.format(start) == year.format(now)
        val fmt = SimpleDateFormat(if (thisYear) "d MMM" else "d MMM yyyy", locale)
        val a = fmt.format(start); val b = fmt.format(end)
        return if (a == b) a else "$a – $b"
    }

    /** "Oct": the month most of [range] falls in (its middle day's), short enough for a chart label. */
    fun shortLabel(range: DateRange, locale: Locale = Locale.ENGLISH): String {
        val middle = Calendar.getInstance().apply { timeInMillis = range.start; add(Calendar.DAY_OF_YEAR, daysInCycle(range) / 2) }
        return SimpleDateFormat("MMM", locale).format(middle.time)
    }

    /** [count] days from [from]'s day, as calendars at midnight. */
    fun days(from: Long, count: Int): List<Calendar> {
        val cal = Calendar.getInstance().apply { timeInMillis = startOfDay(from) }
        return List(count) { i -> (cal.clone() as Calendar).also { if (i < count - 1) cal.add(Calendar.DAY_OF_YEAR, 1) } }
    }

    /** The same number for every moment of one local day. */
    fun dayKey(t: Long): Int = Calendar.getInstance().apply { timeInMillis = t }.let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }

    fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
