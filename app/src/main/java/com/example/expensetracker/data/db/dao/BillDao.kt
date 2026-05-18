package com.example.expensetracker.data.db.dao

import androidx.room.*
import com.example.expensetracker.data.db.entity.Bill
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY dueDayOfMonth ASC")
    fun getAll(): Flow<List<Bill>>

    @Insert
    suspend fun insert(bill: Bill)

    @Update
    suspend fun update(bill: Bill)

    @Delete
    suspend fun delete(bill: Bill)

    @Query("SELECT * FROM bills WHERE autoLog = 1 AND isEnabled = 1")
    suspend fun getAutoLogEnabled(): List<Bill>

    @Query("DELETE FROM bills")
    suspend fun deleteAll()
}
