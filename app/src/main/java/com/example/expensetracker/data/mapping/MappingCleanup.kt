package com.example.expensetracker.data.mapping

import android.content.Context
import android.util.Log
import com.example.expensetracker.data.db.AppDatabase

/**
 * One-time removal of learned rules created by the old merchant regex, which learned word
 * fragments ("mer" from "Customer") and raw message text as merchant names.
 */
object MappingCleanup {
    private const val PREF_KEY = "mapping_cleanup_version"
    private const val VERSION = 1

    suspend fun runOnce(context: Context) {
        val prefs = context.getSharedPreferences("expense_tracker_prefs", Context.MODE_PRIVATE)
        if (prefs.getInt(PREF_KEY, 0) >= VERSION) return

        val db = AppDatabase.getInstance(context)
        val mappings = db.merchantMappingDao().getAllMappings()
        val bodies = mappings.associate { it.merchantName to db.expenseDao().getCapturedTextForDescription(it.merchantName) }
        val broken = MerchantRules.findBroken(mappings, bodies)
        if (broken.isNotEmpty()) {
            broken.chunked(500).forEach { db.merchantMappingDao().deleteByNames(it) }
            Log.i("MappingCleanup", "Removed ${broken.size} broken learned rules")
        }
        prefs.edit().putInt(PREF_KEY, VERSION).apply()
    }
}
