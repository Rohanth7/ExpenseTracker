package com.example.expensetracker.ui.screens.expenses

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.notification.BudgetAlertHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ExpensesViewModel(
    private val expenseRepo: ExpenseRepository,
    private val categoryRepo: CategoryRepository,
    private val appContext: Context
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _categoryFilter = MutableStateFlow<Long?>(-1L)

    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val categoryFilter: StateFlow<Long?> = _categoryFilter.asStateFlow()

    val categories: StateFlow<List<Category>> = categoryRepo.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _allExpenses: StateFlow<List<Expense>> = expenseRepo.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasAnyExpenses: StateFlow<Boolean> = _allExpenses
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val expenses: StateFlow<List<Expense>> = combine(
        _allExpenses,
        _searchQuery,
        _categoryFilter
    ) { all, query, catFilter ->
        all.filter { expense ->
            val matchesQuery = query.isBlank() || expense.description.contains(query, ignoreCase = true)
            val matchesCat = catFilter == null || catFilter == -1L || expense.categoryId == catFilter
            matchesQuery && matchesCat
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setCategoryFilter(id: Long?) { _categoryFilter.value = id }

    fun addExpense(amount: Double, description: String, categoryId: Long) = viewModelScope.launch {
        expenseRepo.insert(Expense(amount = amount, description = description, categoryId = categoryId))
        if (categoryId != -1L) {
            BudgetAlertHelper.checkAndNotify(appContext, categoryId, categoryRepo, expenseRepo)
        }
    }

    fun updateExpense(expense: Expense, amount: Double, description: String, categoryId: Long) = viewModelScope.launch {
        expenseRepo.update(expense.copy(amount = amount, description = description, categoryId = categoryId))
        if (categoryId != -1L) {
            BudgetAlertHelper.checkAndNotify(appContext, categoryId, categoryRepo, expenseRepo)
        }
    }

    fun deleteExpense(expense: Expense) = viewModelScope.launch {
        expenseRepo.delete(expense)
    }

    companion object {
        fun factory(expenseRepo: ExpenseRepository, categoryRepo: CategoryRepository, appContext: Context) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ExpensesViewModel(expenseRepo, categoryRepo, appContext) as T
            }
    }
}
