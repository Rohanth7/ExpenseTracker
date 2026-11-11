package com.example.expensetracker.data.recurring

import com.example.expensetracker.data.db.entity.Bill
import java.util.Calendar

/**
 * A calendar month ([month] 1–12) an auto-log bill has been handled in: logged, or found already
 * paid. Kept in preferences under [key] and carried in backups, so a restore brings back the
 * record that matches the bills and expenses it restores.
 */
data class BillMonth(val billId: Long, val year: Int, val month: Int) {
    // The stored month is Calendar.MONTH (0–11), as it was before this record was in backups.
    val key: String get() = "$PREFIX${year}_${month - 1}_$billId"

    companion object {
        const val PREFIX = "bill_applied_"

        fun fromKey(key: String): BillMonth? {
            if (!key.startsWith(PREFIX)) return null
            val parts = key.removePrefix(PREFIX).split('_').map { it.toLongOrNull() ?: return null }
            if (parts.size != 3 || parts[1] !in 0..11) return null
            return BillMonth(billId = parts[2], year = parts[0].toInt(), month = parts[1].toInt() + 1)
        }

        /**
         * For a backup made before bill months were saved in it: the months its auto-log bills
         * had been handled in by [exportedAt]. The app handles each due bill when it opens, and it
         * was open to make the backup, so a bill whose due day had come that month was handled
         * then. Whatever the user did to its expense afterwards (edited the amount, deleted it) is
         * in the file, and logging it again would add a second one. A bill not yet due when the
         * backup was made is not handled, and is logged after the restore.
         */
        fun inferred(bills: List<Bill>, exportedAt: Long): List<BillMonth> {
            if (exportedAt <= 0) return emptyList()
            val madeAt = Calendar.getInstance().apply { timeInMillis = exportedAt }
            return bills.filter { it.autoLog && it.isEnabled && RecurringSchedule.dueDateIfReached(it.dueDayOfMonth, madeAt) != null }
                .map { BillMonth(it.id, madeAt.get(Calendar.YEAR), madeAt.get(Calendar.MONTH) + 1) }
        }
    }
}
