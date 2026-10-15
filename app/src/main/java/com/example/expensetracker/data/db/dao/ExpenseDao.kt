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

    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE amount = :amount AND date >= :since")
    suspend fun getRecentByAmount(amount: Double, since: Long): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE amount = :amount AND source != :source AND date >= :since")
    suspend fun getRecentByAmountFromDifferentSource(amount: Double, source: String, since: Long): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE amount = :amount AND description = :description AND source != :source AND date >= :since")
    suspend fun getRecentByAmountAndDescriptionFromDifferentSource(amount: Double, description: String, source: String, since: Long): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE amount = :amount AND source = :source AND date >= :since")
    suspend fun getRecentByAmountFromSameSource(amount: Double, source: String, since: Long): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE amount = :amount AND source = :source AND rawSms = :rawSms AND date >= :since")
    suspend fun getRecentByAmountAndContentFromSameSource(amount: Double, source: String, rawSms: String, since: Long): Int

    // Whole month, not one exact date: bills used to be dated the 1st and are now dated their due
    // day, so an exact-date check would log this month's bill a second time after upgrading.
    @Query("SELECT COUNT(*) FROM expenses WHERE source = 'Recurring' AND description = :description AND amount = :amount AND date BETWEEN :monthStart AND :monthEnd")
    suspend fun countRecurringInMonth(description: String, amount: Double, monthStart: Long, monthEnd: Long): Int

    @Query("SELECT categoryId, COUNT(*) AS uses FROM expenses WHERE categoryId != -1 AND date >= :since GROUP BY categoryId")
    suspend fun categoryUsageSince(since: Long): List<CategoryUsage>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expenses WHERE date BETWEEN :start AND :end")
    suspend fun getTotalSpentInRange(start: Long, end: Long): Double

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expenses WHERE categoryId = :categoryId AND date BETWEEN :start AND :end")
    suspend fun getSpentForCategory(categoryId: Long, start: Long, end: Long): Double

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expenses WHERE categoryId IN (:categoryIds) AND date BETWEEN :start AND :end")
    suspend fun getSpentForCategories(categoryIds: List<Long>, start: Long, end: Long): Double

    @Query("SELECT rawSms FROM expenses WHERE description = :description AND rawSms IS NOT NULL")
    suspend fun getCapturedTextForDescription(description: String): List<String>

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()

    @Query("UPDATE expenses SET categoryId = -1 WHERE categoryId = :categoryId")
    suspend fun resetCategoryForExpenses(categoryId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: Expense): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<Expense>)

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)
}

data class CategoryUsage(val categoryId: Long, val uses: Int)
