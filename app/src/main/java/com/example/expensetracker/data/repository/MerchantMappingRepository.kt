package com.example.expensetracker.data.repository

import com.example.expensetracker.data.db.dao.MerchantMappingDao
import com.example.expensetracker.data.db.entity.MerchantMapping
import com.example.expensetracker.data.mapping.MerchantRules

class MerchantMappingRepository(private val dao: MerchantMappingDao) {
    suspend fun saveMapping(merchantName: String, categoryId: Long) {
        if (categoryId != -1L && MerchantRules.isLearnable(merchantName)) {
            dao.insert(MerchantMapping(merchantName, categoryId))
        }
    }
    suspend fun deleteAll() = dao.deleteAll()
}
