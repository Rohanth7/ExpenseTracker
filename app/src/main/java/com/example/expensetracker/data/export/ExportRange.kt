package com.example.expensetracker.data.export

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class ExportPreset(val label: String) {
    THIS_MONTH("This month"), LAST_MONTH("Last month"), THIS_YEAR("This year"), ALL("All time"), CUSTOM("Choose dates")
}

/**
 * Date ranges for CSV export. Calendar months for now; the salary-cycle feature switches
 * THIS_MONTH and LAST_MONTH to the user's cycle. Ends are 23:59:59.999.
 */
object ExportRange {
    /** (start, endInclusive), or null for all time. */
    fun forPreset(preset: ExportPreset, now: Long, custom: Pair<Long, Long>? = null): Pair<Long, Long>? = when (preset) {
        ExportPreset.THIS_MONTH -> month(now, 0)
        ExportPreset.LAST_MONTH -> month(now, -1)
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
            preset == ExportPreset.THIS_MONTH || preset == ExportPreset.LAST_MONTH -> f("yyyy-MM", range.first)
            preset == ExportPreset.THIS_YEAR -> f("yyyy", range.first)
            else -> "${f("yyyy-MM-dd", range.first)}_to_${f("yyyy-MM-dd", range.second)}"
        }
        return "mymoney-expenses-$part.csv"
    }

    private fun month(now: Long, offset: Int): Pair<Long, Long> {
        val start = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.DAY_OF_MONTH, 1); startOfDay(); add(Calendar.MONTH, offset) }
        val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1); add(Calendar.MILLISECOND, -1) }
        return start.timeInMillis to end.timeInMillis
    }

    private fun Calendar.startOfDay() {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
}
