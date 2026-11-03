package com.example.expensetracker.data.db.dao

import androidx.room.*
import com.example.expensetracker.data.db.entity.RecurringIncome
import com.example.expensetracker.data.db.entity.RecurringIncomeMonth
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RecurringIncomeDao {
    @Query("SELECT * FROM recurring_incomes ORDER BY dayOfMonth ASC")
    abstract fun getAll(): Flow<List<RecurringIncome>>

    @Query("SELECT * FROM recurring_incomes ORDER BY dayOfMonth ASC")
    abstract suspend fun getAllOnce(): List<RecurringIncome>

    @Query("SELECT * FROM recurring_incomes WHERE isEnabled = 1")
    abstract suspend fun getEnabled(): List<RecurringIncome>

    @Insert
    abstract suspend fun insert(r: RecurringIncome): Long

    @Insert
    abstract suspend fun insertAll(r: List<RecurringIncome>)

    @Update
    abstract suspend fun update(r: RecurringIncome)

    @Delete
    protected abstract suspend fun delete(r: RecurringIncome)

    /**
     * Deletes [r] but keeps the income it logged, as entries of their own (so adding it back
     * matches them the way it matches hand entries), and forgets the months it handled.
     */
    @Transaction
    open suspend fun deleteKeepingIncome(r: RecurringIncome) {
        detachIncomes(r.id)
        deleteMonthsOf(r.id)
        delete(r)
    }

    @Query("UPDATE incomes SET recurringIncomeId = NULL WHERE recurringIncomeId = :id")
    protected abstract suspend fun detachIncomes(id: Long)

    @Query("DELETE FROM recurring_income_months WHERE recurringIncomeId = :id")
    protected abstract suspend fun deleteMonthsOf(id: Long)

    @Query("DELETE FROM recurring_incomes")
    abstract suspend fun deleteAll()

    @Query("SELECT * FROM recurring_income_months")
    abstract fun getMonths(): Flow<List<RecurringIncomeMonth>>

    @Query("SELECT * FROM recurring_income_months")
    abstract suspend fun getMonthsOnce(): List<RecurringIncomeMonth>

    @Query("SELECT * FROM recurring_income_months WHERE year = :year AND month = :month")
    abstract suspend fun getMonthsIn(year: Int, month: Int): List<RecurringIncomeMonth>

    /** False if the month was already recorded (another run got there first). */
    suspend fun insertMonth(m: RecurringIncomeMonth): Boolean = insertMonthOrIgnore(m) != -1L

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertMonthOrIgnore(m: RecurringIncomeMonth): Long

    @Insert
    abstract suspend fun insertAllMonths(m: List<RecurringIncomeMonth>)

    @Query("DELETE FROM recurring_income_months")
    abstract suspend fun deleteAllMonths()
}
