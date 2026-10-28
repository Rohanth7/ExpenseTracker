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

    @Query("SELECT COUNT(*) FROM incomes WHERE recurringIncomeId = :recurringId AND date BETWEEN :start AND :end")
    suspend fun countRecurringInRange(recurringId: Long, start: Long, end: Long): Int

    @Query("SELECT DISTINCT recurringIncomeId FROM incomes WHERE recurringIncomeId IS NOT NULL AND date BETWEEN :start AND :end")
    suspend fun recurringIdsInRange(start: Long, end: Long): List<Long>

    /** Income entered by hand with this name (ignoring case and spaces) and amount. */
    @Query("SELECT COUNT(*) FROM incomes WHERE recurringIncomeId IS NULL AND TRIM(description) = TRIM(:description) COLLATE NOCASE AND amount = :amount AND date BETWEEN :start AND :end")
    suspend fun countByHandInRange(description: String, amount: Double, start: Long, end: Long): Int

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
