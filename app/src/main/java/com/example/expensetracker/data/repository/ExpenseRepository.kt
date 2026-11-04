package com.example.expensetracker.data.repository

import android.content.Context
import com.example.expensetracker.data.db.dao.ExpenseDao
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.notification.PriceChangeAlerts
import com.example.expensetracker.ui.widgets.WidgetUpdateHelper
import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val dao: ExpenseDao, private val context: Context) {
    val allExpenses: Flow<List<Expense>> = dao.getAll()
    val pendingExpenses: Flow<List<Expense>> = dao.getPending()

    suspend fun insert(expense: Expense): Long {
        val id = dao.insert(expense)
        WidgetUpdateHelper.update(context)
        return id
    }

    suspend fun insertAll(expenses: List<Expense>) {
        dao.insertAll(expenses)
        WidgetUpdateHelper.update(context)
    }

    /** Every edit comes through here, so a capture renamed or re-priced ("UPI" → "Netflix") is checked for a new price. */
    suspend fun update(expense: Expense) {
        val before = dao.getById(expense.id)
        dao.update(expense)
        WidgetUpdateHelper.update(context)
        if (before != null) PriceChangeAlerts.checkAfterEdit(context, before, expense)
    }

    suspend fun delete(expense: Expense) {
        dao.delete(expense)
        WidgetUpdateHelper.update(context)
    }

    suspend fun getById(id: Long): Expense? = dao.getById(id)

    fun getByDateRange(start: Long, end: Long): Flow<List<Expense>> =
        dao.getByDateRange(start, end)

    fun getByCategory(categoryId: Long): Flow<List<Expense>> =
        dao.getByCategory(categoryId)

    suspend fun getSpentForCategory(categoryId: Long, start: Long, end: Long): Double =
        dao.getSpentForCategory(categoryId, start, end)

    suspend fun getSpentForCategories(categoryIds: List<Long>, start: Long, end: Long): Double =
        if (categoryIds.isEmpty()) 0.0 else dao.getSpentForCategories(categoryIds, start, end)

    suspend fun deleteAll() {
        dao.deleteAll()
        WidgetUpdateHelper.update(context)
    }
}
