package com.example.expensetracker.data.repository

import com.example.expensetracker.data.db.dao.MerchantMappingDao
import com.example.expensetracker.data.db.entity.MerchantMapping

class MerchantMappingRepository(private val dao: MerchantMappingDao) {
    suspend fun getCategoryId(merchantName: String): Long? {
        // 1. Try exact match first (fast)
        val exact = dao.getCategoryIdForMerchant(merchantName)
        if (exact != null) return exact

        // 2. Try fuzzy match (if mapping name is a substring of new merchant name)
        // e.g. Mapping "Swiggy" matches "Swiggy-1234"
        val all = dao.getAllMappings()
        return all.find { mapping -> 
            merchantName.contains(mapping.merchantName, ignoreCase = true) ||
            mapping.merchantName.contains(merchantName, ignoreCase = true)
        }?.categoryId
    }

    suspend fun saveMapping(merchantName: String, categoryId: Long) {
        if (merchantName.isNotBlank() && categoryId != -1L) {
            dao.insert(MerchantMapping(merchantName, categoryId))
        }
    }
    suspend fun deleteAll() = dao.deleteAll()
}
