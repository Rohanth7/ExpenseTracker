package com.example.expensetracker.data.db.dao

import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.mapping.MerchantRules

object MappingHelper {
    suspend fun getCategoryId(db: AppDatabase, merchantName: String): Long? {
        val exact = db.merchantMappingDao().getCategoryIdForMerchant(merchantName)
        if (exact != null) return exact

        return MerchantRules.findCategoryId(merchantName, db.merchantMappingDao().getAllMappings())
    }
}
