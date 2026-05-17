package com.example.expensetracker.data.db.dao

import androidx.room.*
import com.example.expensetracker.data.db.entity.MerchantMapping

@Dao
interface MerchantMappingDao {
    @Query("SELECT categoryId FROM merchant_mappings WHERE merchantName = :merchantName")
    suspend fun getCategoryIdForMerchant(merchantName: String): Long?

    @Query("SELECT * FROM merchant_mappings")
    suspend fun getAllMappings(): List<MerchantMapping>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mapping: MerchantMapping)

    @Query("DELETE FROM merchant_mappings")
    suspend fun deleteAll()
}
