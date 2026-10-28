package com.example.expensetracker.data.db.dao

import androidx.room.*
import com.example.expensetracker.data.db.entity.Income
import kotlinx.coroutines.flow.Flow

@Dao
interface IncomeDao {
    @Query("SELECT * FROM incomes ORDER BY date DESC")
    fun getAll(): Flow<List<Income>>

    @Query("SELECT * FROM incomes ORDER BY date DESC")
    suspend fun getAllOnce(): List<Income>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM incomes WHERE date BETWEEN :start AND :end")
    suspend fun sumInRange(start: Long, end: Long): Double

    @Query("SELECT * FROM incomes WHERE date BETWEEN :start AND :end")
    suspend fun getInRange(start: Long, end: Long): List<Income>

    @Insert
    suspend fun insert(income: Income): Long

    @Insert
    suspend fun insertAll(incomes: List<Income>)

    @Update
    suspend fun update(income: Income)

    @Delete
    suspend fun delete(income: Income)

    @Query("DELETE FROM incomes")
    suspend fun deleteAll()
}
