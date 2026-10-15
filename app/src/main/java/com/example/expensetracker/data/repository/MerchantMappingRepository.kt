package com.example.expensetracker.data.repository

import com.example.expensetracker.data.db.dao.MerchantMappingDao
import com.example.expensetracker.data.db.entity.MerchantMapping
import com.example.expensetracker.data.mapping.MerchantRules

class MerchantMappingRepository(private val dao: MerchantMappingDao) {
    suspend fun getCategoryId(merchantName: String): Long? {
        // 1. Try exact match first (fast)
        val exact = dao.getCategoryIdForMerchant(merchantName)
        if (exact != null) return exact

        // 2. Whole-word match, e.g. learned "Swiggy" matches "Swiggy Instamart"
        return MerchantRules.findCategoryId(merchantName, dao.getAllMappings())
    }

    suspend fun saveMapping(merchantName: String, categoryId: Long) {
        if (categoryId != -1L && MerchantRules.isLearnable(merchantName)) {
            dao.insert(MerchantMapping(merchantName, categoryId))
        }
    }
    suspend fun deleteAll() = dao.deleteAll()
}
