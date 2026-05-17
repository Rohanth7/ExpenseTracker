package com.example.expensetracker.data.db.dao

import androidx.room.*
import com.example.expensetracker.data.db.entity.Expense
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAll(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE categoryId = -1 ORDER BY date DESC")
    fun getPending(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE date BETWEEN :start AND :end ORDER BY date DESC")
    fun getByDateRange(start: Long, end: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE categoryId = :categoryId ORDER BY date DESC")
    fun getByCategory(categoryId: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): Expense?

    @Query("SELECT COUNT(*) FROM expenses WHERE amount = :amount AND date >= :since")
    suspend fun getRecentByAmount(amount: Double, since: Long): Int

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expenses WHERE categoryId = :categoryId AND date BETWEEN :start AND :end")
    suspend fun getSpentForCategory(categoryId: Long, start: Long, end: Long): Double

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()

    @Query("UPDATE expenses SET categoryId = -1 WHERE categoryId = :categoryId")
    suspend fun resetCategoryForExpenses(categoryId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: Expense): Long

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)
}
