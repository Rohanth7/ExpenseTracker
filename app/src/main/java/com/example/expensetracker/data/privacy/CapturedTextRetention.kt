package com.example.expensetracker.data.privacy

import com.example.expensetracker.data.db.AppDatabase

/**
 * The full text of a bank SMS or payment notification (account digits, balance, reference
 * numbers) is kept only as long as it's useful: the 30-second duplicate check and the preview on
 * the categorize screen. After that only the parsed amount, payee and date stay.
 */
object CapturedTextRetention {
    const val KEEP_MS = 7L * 24 * 60 * 60 * 1000

    suspend fun purge(db: AppDatabase, now: Long = System.currentTimeMillis()): Int =
        db.expenseDao().clearCapturedTextBefore(now - KEEP_MS)
}
