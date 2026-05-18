package com.example.expensetracker.data.repository

import com.example.expensetracker.data.db.dao.BillDao
import com.example.expensetracker.data.db.entity.Bill
import kotlinx.coroutines.flow.Flow

class BillRepository(private val dao: BillDao) {
    val allBills: Flow<List<Bill>> = dao.getAll()
    suspend fun insert(bill: Bill) = dao.insert(bill)
    suspend fun update(bill: Bill) = dao.update(bill)
    suspend fun delete(bill: Bill) = dao.delete(bill)
    suspend fun deleteAll() = dao.deleteAll()
}
