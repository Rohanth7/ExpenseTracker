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
    suspend fun getAll(): List<Category> = dao.getAllOnce()

    // Expenses go back to pending, bills become uncategorized and learned merchants are forgotten,
    // so nothing keeps pointing at the deleted category.
    suspend fun delete(category: Category) {
        expenseDao.resetCategoryForExpenses(category.id)
        dao.clearBillCategory(category.id)
        dao.deleteMerchantMappingsFor(category.id)
        dao.delete(category)
    }

    suspend fun deleteAll() {
        dao.deleteAllMerchantMappings()
        dao.deleteAll()
    }
}
