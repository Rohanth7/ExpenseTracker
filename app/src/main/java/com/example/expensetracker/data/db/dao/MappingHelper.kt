package com.example.expensetracker.data.db.dao

import com.example.expensetracker.data.db.AppDatabase

object MappingHelper {
    suspend fun getCategoryId(db: AppDatabase, merchantName: String): Long? {
        val exact = db.merchantMappingDao().getCategoryIdForMerchant(merchantName)
        if (exact != null) return exact

        val all = db.merchantMappingDao().getAllMappings()
        return all.find { mapping -> 
            merchantName.contains(mapping.merchantName, ignoreCase = true) ||
            mapping.merchantName.contains(merchantName, ignoreCase = true)
        }?.categoryId
    }
}
