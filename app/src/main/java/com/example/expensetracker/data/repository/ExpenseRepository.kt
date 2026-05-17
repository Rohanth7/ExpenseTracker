package com.example.expensetracker.data.repository

import com.example.expensetracker.data.db.dao.ExpenseDao
import com.example.expensetracker.data.db.entity.Expense
import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val dao: ExpenseDao) {
    val allExpenses: Flow<List<Expense>> = dao.getAll()
    val pendingExpenses: Flow<List<Expense>> = dao.getPending()

    suspend fun insert(expense: Expense): Long = dao.insert(expense)
    suspend fun update(expense: Expense) = dao.update(expense)
    suspend fun delete(expense: Expense) = dao.delete(expense)
    suspend fun getById(id: Long): Expense? = dao.getById(id)

    fun getByDateRange(start: Long, end: Long): Flow<List<Expense>> =
        dao.getByDateRange(start, end)

    fun getByCategory(categoryId: Long): Flow<List<Expense>> =
        dao.getByCategory(categoryId)

    suspend fun getSpentForCategory(categoryId: Long, start: Long, end: Long): Double =
        dao.getSpentForCategory(categoryId, start, end)

    suspend fun deleteAll() = dao.deleteAll()
}
