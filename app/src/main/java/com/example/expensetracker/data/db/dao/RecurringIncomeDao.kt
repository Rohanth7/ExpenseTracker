package com.example.expensetracker.data.db.dao

import androidx.room.*
import com.example.expensetracker.data.db.entity.RecurringIncome
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringIncomeDao {
    @Query("SELECT * FROM recurring_incomes ORDER BY dayOfMonth ASC")
    fun getAll(): Flow<List<RecurringIncome>>

    @Query("SELECT * FROM recurring_incomes ORDER BY dayOfMonth ASC")
    suspend fun getAllOnce(): List<RecurringIncome>

    @Query("SELECT * FROM recurring_incomes WHERE isEnabled = 1")
    suspend fun getEnabled(): List<RecurringIncome>

    @Insert
    suspend fun insert(r: RecurringIncome): Long

    @Insert
    suspend fun insertAll(r: List<RecurringIncome>)

    @Update
    suspend fun update(r: RecurringIncome)

    @Delete
    suspend fun delete(r: RecurringIncome)

    @Query("DELETE FROM recurring_incomes")
    suspend fun deleteAll()
}
