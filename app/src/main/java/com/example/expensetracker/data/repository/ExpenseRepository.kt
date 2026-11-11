package com.example.expensetracker.data.repository

import com.example.expensetracker.ui.screens.expenses.Tags
import android.content.Context
import com.example.expensetracker.data.db.dao.ExpenseDao
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.notification.PriceChangeAlerts
import com.example.expensetracker.ui.widgets.WidgetUpdateHelper
import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val dao: ExpenseDao, private val context: Context) {
    val allExpenses: Flow<List<Expense>> = dao.getAll()
    val pendingExpenses: Flow<List<Expense>> = dao.getPending()

    // Tags are saved clean whichever screen wrote them (Tags.normalize).
    private fun Expense.clean() = Tags.normalize(tags).let { if (it == tags) this else copy(tags = it) }

    suspend fun insert(expense: Expense): Long {
        val id = dao.insert(expense.clean())
        WidgetUpdateHelper.update(context)
        return id
    }

    suspend fun insertAll(expenses: List<Expense>) {
        dao.insertAll(expenses.map { it.clean() })
        WidgetUpdateHelper.update(context)
    }

    /** Every edit comes through here, so a capture renamed or re-priced ("UPI" → "Netflix") is checked for a new price. */
    suspend fun update(edited: Expense) {
        val expense = edited.clean()
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
