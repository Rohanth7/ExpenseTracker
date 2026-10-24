package com.example.expensetracker.data.export

import com.example.expensetracker.data.period.BudgetPeriod
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class ExportPreset(val label: String) {
    THIS_MONTH("This month"), LAST_MONTH("Last month"), THIS_YEAR("This year"), ALL("All time"), CUSTOM("Choose dates")
}

/**
 * Date ranges for CSV export. "This month" and "Last month" are budget months, which follow the
 * user's month start day like the Overview (see BudgetPeriod). Ends are 23:59:59.999.
 */
object ExportRange {
    /** (start, endInclusive), or null for all time. */
    fun forPreset(preset: ExportPreset, now: Long, custom: Pair<Long, Long>? = null, monthStartDay: Int = 1): Pair<Long, Long>? = when (preset) {
        ExportPreset.THIS_MONTH -> BudgetPeriod.current(now, monthStartDay).let { it.start to it.endInclusive }
        ExportPreset.LAST_MONTH -> BudgetPeriod.forOffset(now, monthStartDay, -1).let { it.start to it.endInclusive }
        ExportPreset.THIS_YEAR -> {
            val start = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.DAY_OF_YEAR, 1); startOfDay() }
            val end = (start.clone() as Calendar).apply { add(Calendar.YEAR, 1); add(Calendar.MILLISECOND, -1) }
            start.timeInMillis to end.timeInMillis
        }
        ExportPreset.ALL -> null
        ExportPreset.CUSTOM -> custom
    }

    fun fileName(preset: ExportPreset, range: Pair<Long, Long>?): String {
        fun f(pattern: String, t: Long) = SimpleDateFormat(pattern, Locale.US).format(t)
        val part = when {
            range == null -> "all"
            // A calendar month is named by its month; a salary-cycle month by its dates (below).
            (preset == ExportPreset.THIS_MONTH || preset == ExportPreset.LAST_MONTH) && f("d", range.first) == "1" -> f("yyyy-MM", range.first)
            preset == ExportPreset.THIS_YEAR -> f("yyyy", range.first)
            else -> "${f("yyyy-MM-dd", range.first)}_to_${f("yyyy-MM-dd", range.second)}"
        }
        return "mymoney-expenses-$part.csv"
    }

    private fun Calendar.startOfDay() {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
}
