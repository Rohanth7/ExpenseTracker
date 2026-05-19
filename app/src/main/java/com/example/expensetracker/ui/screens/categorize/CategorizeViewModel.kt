package com.example.expensetracker.ui.screens.categorize

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.db.entity.Loan
import com.example.expensetracker.data.db.entity.SavingsGoal
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.data.repository.LoanRepository
import com.example.expensetracker.data.repository.MerchantMappingRepository
import com.example.expensetracker.data.repository.SavingsGoalRepository
import com.example.expensetracker.notification.BudgetAlertHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategorizeViewModel(
    private val expenseRepo: ExpenseRepository,
    private val categoryRepo: CategoryRepository,
    private val mappingRepo: MerchantMappingRepository,
    private val savingsGoalRepo: SavingsGoalRepository,
    private val loanRepo: LoanRepository,
    private val appContext: Context,
    private val prefs: PreferencesManager
) : ViewModel() {

    val categories: StateFlow<List<Category>> = categoryRepo.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savingsGoals: StateFlow<List<SavingsGoal>> = savingsGoalRepo.allGoals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loans: StateFlow<List<Loan>> = loanRepo.allLoans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun getExpense(id: Long): Expense? = expenseRepo.getById(id)

    fun assignCategory(expense: Expense, categoryId: Long, amount: Double, tags: String, onDone: () -> Unit) =
        viewModelScope.launch {
            expenseRepo.update(expense.copy(categoryId = categoryId, amount = amount, tags = tags))
            if (expense.source != "Manual") {
                mappingRepo.saveMapping(expense.description, categoryId)
            }
            if (prefs.budgetAlertsEnabled) {
                BudgetAlertHelper.checkAndNotify(appContext, categoryId, categoryRepo, expenseRepo, prefs.budgetAlertThreshold)
            }
            onDone()
        }

    fun linkToLoanEmi(expense: Expense, onDone: () -> Unit) = viewModelScope.launch {
        expenseRepo.delete(expense)
        onDone()
    }

    fun linkToSavingsGoal(expense: Expense, goal: SavingsGoal, amount: Double, onDone: () -> Unit) =
        viewModelScope.launch {
            savingsGoalRepo.update(goal.copy(currentAmount = goal.currentAmount + amount))
            expenseRepo.delete(expense)
            onDone()
        }

    fun categorizeLater(onDone: () -> Unit) { onDone() }

    fun dismissExpense(expense: Expense, onDone: () -> Unit) = viewModelScope.launch {
        expenseRepo.delete(expense)
        onDone()
    }

    companion object {
        fun factory(
            expenseRepo: ExpenseRepository,
            categoryRepo: CategoryRepository,
            mappingRepo: MerchantMappingRepository,
            savingsGoalRepo: SavingsGoalRepository,
            loanRepo: LoanRepository,
            appContext: Context,
            prefs: PreferencesManager
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CategorizeViewModel(expenseRepo, categoryRepo, mappingRepo, savingsGoalRepo, loanRepo, appContext, prefs) as T
        }
    }
}
