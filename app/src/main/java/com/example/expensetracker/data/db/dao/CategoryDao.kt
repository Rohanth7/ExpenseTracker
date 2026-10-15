package com.example.expensetracker.data.db.dao

import androidx.room.*
import com.example.expensetracker.data.db.entity.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAll(): Flow<List<Category>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCount(): Int

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): Category?

    @Query("SELECT * FROM categories")
    suspend fun getAllOnce(): List<Category>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<Category>)

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    // Other tables point at categories by id without a foreign key, so deleting a category has to
    // clean them up itself. Learned merchants would otherwise file new payments under a category
    // that no longer exists, where they disappear from every list.
    @Query("DELETE FROM merchant_mappings WHERE categoryId = :categoryId")
    suspend fun deleteMerchantMappingsFor(categoryId: Long)

    @Query("UPDATE bills SET categoryId = -1 WHERE categoryId = :categoryId")
    suspend fun clearBillCategory(categoryId: Long)

    @Query("DELETE FROM merchant_mappings")
    suspend fun deleteAllMerchantMappings()
}
