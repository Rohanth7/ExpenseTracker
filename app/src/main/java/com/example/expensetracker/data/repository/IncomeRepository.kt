package com.example.expensetracker.data.repository

import android.content.Context
import com.example.expensetracker.data.db.dao.IncomeDao
import com.example.expensetracker.data.db.dao.RecurringIncomeDao
import com.example.expensetracker.data.db.entity.Income
import com.example.expensetracker.data.db.entity.RecurringIncome
import com.example.expensetracker.data.db.entity.RecurringIncomeMonth
import com.example.expensetracker.ui.widgets.WidgetUpdateHelper
import kotlinx.coroutines.flow.Flow

/** Income records and recurring income. Every write refreshes the widget, whose forecast uses them. */
class IncomeRepository(private val dao: IncomeDao, private val recurringDao: RecurringIncomeDao, private val context: Context) {
    val allIncomes: Flow<List<Income>> = dao.getAll()
    val allRecurring: Flow<List<RecurringIncome>> = recurringDao.getAll()
    val allMonths: Flow<List<RecurringIncomeMonth>> = recurringDao.getMonths()

    suspend fun insert(income: Income): Long = dao.insert(income).also { WidgetUpdateHelper.update(context) }
    suspend fun update(income: Income) { dao.update(income); WidgetUpdateHelper.update(context) }
    suspend fun delete(income: Income) { dao.delete(income); WidgetUpdateHelper.update(context) }

    suspend fun insertRecurring(r: RecurringIncome): Long = recurringDao.insert(r).also { WidgetUpdateHelper.update(context) }
    suspend fun updateRecurring(r: RecurringIncome) { recurringDao.update(r); WidgetUpdateHelper.update(context) }
    /** Its logged income stays (see [RecurringIncomeDao.deleteKeepingIncome]). */
    suspend fun deleteRecurring(r: RecurringIncome) { recurringDao.deleteKeepingIncome(r); WidgetUpdateHelper.update(context) }

    /** All the income tables (Reset everything). */
    suspend fun deleteAll() {
        dao.deleteAll()
        recurringDao.deleteAll()
        recurringDao.deleteAllMonths()
        WidgetUpdateHelper.update(context)
    }
}
