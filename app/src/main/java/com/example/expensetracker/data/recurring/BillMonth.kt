package com.example.expensetracker.data.recurring

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
    }
}
