package com.example.expensetracker.data.db.dao

import androidx.room.*
import com.example.expensetracker.data.db.entity.SavingsGoal
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsGoalDao {
    @Query("SELECT * FROM savings_goals ORDER BY name ASC")
    fun getAll(): Flow<List<SavingsGoal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: SavingsGoal): Long

    @Update
    suspend fun update(goal: SavingsGoal)

    @Delete
    suspend fun delete(goal: SavingsGoal)

    @Query("DELETE FROM savings_goals")
    suspend fun deleteAll()
}
