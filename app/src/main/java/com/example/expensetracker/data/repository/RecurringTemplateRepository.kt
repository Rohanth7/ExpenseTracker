package com.example.expensetracker.data.repository

import com.example.expensetracker.data.db.dao.RecurringTemplateDao
import com.example.expensetracker.data.db.entity.RecurringTemplate
import kotlinx.coroutines.flow.Flow

class RecurringTemplateRepository(private val dao: RecurringTemplateDao) {
    val allTemplates: Flow<List<RecurringTemplate>> = dao.getAll()

    suspend fun getAllEnabled(): List<RecurringTemplate> = dao.getAllEnabled()
    suspend fun insert(template: RecurringTemplate) = dao.insert(template)
    suspend fun update(template: RecurringTemplate) = dao.update(template)
    suspend fun delete(template: RecurringTemplate) = dao.delete(template)
    suspend fun deleteAll() = dao.deleteAll()
}
