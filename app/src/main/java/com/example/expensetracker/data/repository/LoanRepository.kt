package com.example.expensetracker.data.repository

import com.example.expensetracker.data.db.dao.LoanDao
import com.example.expensetracker.data.db.entity.Loan
import kotlinx.coroutines.flow.Flow

class LoanRepository(private val dao: LoanDao) {
    val allLoans: Flow<List<Loan>> = dao.getAll()

    suspend fun insert(loan: Loan): Long = dao.insert(loan)
    suspend fun update(loan: Loan) = dao.update(loan)
    suspend fun delete(loan: Loan) = dao.delete(loan)
    suspend fun deleteAll() = dao.deleteAll()
}
