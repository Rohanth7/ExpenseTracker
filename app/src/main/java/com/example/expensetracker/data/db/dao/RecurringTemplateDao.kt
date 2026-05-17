package com.example.expensetracker.data.db.dao

import androidx.room.*
import com.example.expensetracker.data.db.entity.RecurringTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringTemplateDao {
    @Query("SELECT * FROM recurring_templates ORDER BY name ASC")
    fun getAll(): Flow<List<RecurringTemplate>>

    @Query("SELECT * FROM recurring_templates WHERE enabled = 1")
    suspend fun getAllEnabled(): List<RecurringTemplate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: RecurringTemplate): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(templates: List<RecurringTemplate>)

    @Update
    suspend fun update(template: RecurringTemplate)

    @Delete
    suspend fun delete(template: RecurringTemplate)

    @Query("DELETE FROM recurring_templates")
    suspend fun deleteAll()
}
