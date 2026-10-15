package com.example.expensetracker.data.db.dao

import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.mapping.MerchantRules

object MappingHelper {
    suspend fun getCategoryId(db: AppDatabase, merchantName: String): Long? {
        // Installs that deleted a category before deletes cleaned up still have rules pointing at
        // it; skip those so the payment stays pending instead of vanishing.
        val existing = db.categoryDao().getAllOnce().mapTo(HashSet()) { it.id }
        val exact = db.merchantMappingDao().getCategoryIdForMerchant(merchantName)
        if (exact != null && exact in existing) return exact

        return MerchantRules.findCategoryId(merchantName, db.merchantMappingDao().getAllMappings().filter { it.categoryId in existing })
    }
}
