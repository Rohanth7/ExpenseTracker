package com.example.expensetracker.data.repository

import com.example.expensetracker.data.db.dao.SavingsGoalDao
import com.example.expensetracker.data.db.entity.SavingsGoal
import kotlinx.coroutines.flow.Flow

class SavingsGoalRepository(private val dao: SavingsGoalDao) {
    val allGoals: Flow<List<SavingsGoal>> = dao.getAll()
    suspend fun insert(goal: SavingsGoal) = dao.insert(goal)
    suspend fun update(goal: SavingsGoal) = dao.update(goal)
    suspend fun delete(goal: SavingsGoal) = dao.delete(goal)
    suspend fun deleteAll() = dao.deleteAll()
}
