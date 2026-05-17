package com.example.expensetracker.data.repository

import com.example.expensetracker.data.db.dao.CategoryDao
import com.example.expensetracker.data.db.dao.ExpenseDao
import com.example.expensetracker.data.db.entity.Category
import kotlinx.coroutines.flow.Flow

class CategoryRepository(private val dao: CategoryDao, private val expenseDao: ExpenseDao) {
    val allCategories: Flow<List<Category>> = dao.getAll()

    suspend fun insert(category: Category): Long = dao.insert(category)
    suspend fun insertAll(categories: List<Category>) = dao.insertAll(categories)
    suspend fun update(category: Category) = dao.update(category)
    suspend fun getById(id: Long): Category? = dao.getById(id)

    // Reset expenses to pending before deleting so they don't silently orphan
    suspend fun delete(category: Category) {
        expenseDao.resetCategoryForExpenses(category.id)
        dao.delete(category)
    }

    suspend fun deleteAll() = dao.deleteAll()
}
