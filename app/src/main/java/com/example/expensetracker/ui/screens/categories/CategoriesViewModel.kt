package com.example.expensetracker.ui.screens.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.ui.screens.expenses.ExpensesViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryGroup(val parent: Category, val children: List<Category>)

class CategoriesViewModel(
    private val repo: CategoryRepository,
    private val expenseRepo: ExpenseRepository
) : ViewModel() {

    val categories: StateFlow<List<Category>> = repo.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupedCategories: StateFlow<List<CategoryGroup>> = categories.map { list ->
        val parents = list.filter { it.parentId == null }
        val byParent = list.filter { it.parentId != null }.groupBy { it.parentId }
        parents.map { parent -> CategoryGroup(parent, byParent[parent.id] ?: emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTagSummaries: StateFlow<List<ExpensesViewModel.TagSummary>> =
        expenseRepo.allExpenses.map { expenses ->
            val map = mutableMapOf<String, Pair<Int, Double>>()
            expenses.forEach { expense ->
                ExpensesViewModel.parseTags(expense.tags).forEach { tag ->
                    val (c, t) = map.getOrDefault(tag, 0 to 0.0)
                    map[tag] = (c + 1) to (t + expense.amount)
                }
            }
            map.entries.map { ExpensesViewModel.TagSummary(it.key, it.value.first, it.value.second) }
                .sortedByDescending { it.totalSpent }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCategory(name: String, emoji: String, colorHex: String, monthlyLimit: Double, parentId: Long? = null) =
        viewModelScope.launch {
            repo.insert(Category(name = name, emoji = emoji, colorHex = colorHex, monthlyLimit = monthlyLimit, parentId = parentId))
        }

    fun updateCategory(category: Category) = viewModelScope.launch { repo.update(category) }

    fun deleteCategory(category: Category) = viewModelScope.launch {
        // Delete children first (their expenses move to pending via repo.delete)
        categories.value.filter { it.parentId == category.id }.forEach { repo.delete(it) }
        repo.delete(category)
    }

    fun renameTag(oldName: String, newName: String) = viewModelScope.launch {
        val trimNew = newName.trim().trimStart('#')
        if (trimNew.isBlank() || trimNew == oldName) return@launch
        expenseRepo.allExpenses.first()
            .filter { ExpensesViewModel.parseTags(it.tags).any { t -> t.equals(oldName, ignoreCase = true) } }
            .forEach { expense ->
                val updated = ExpensesViewModel.parseTags(expense.tags)
                    .map { if (it.equals(oldName, ignoreCase = true)) trimNew else it }
                expenseRepo.update(expense.copy(tags = updated.joinToString(",")))
            }
    }

    fun deleteTag(name: String) = viewModelScope.launch {
        expenseRepo.allExpenses.first()
            .filter { ExpensesViewModel.parseTags(it.tags).any { t -> t.equals(name, ignoreCase = true) } }
            .forEach { expense ->
                val updated = ExpensesViewModel.parseTags(expense.tags)
                    .filter { !it.equals(name, ignoreCase = true) }
                expenseRepo.update(expense.copy(tags = updated.joinToString(",")))
            }
    }

    companion object {
        fun factory(repo: CategoryRepository, expenseRepo: ExpenseRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CategoriesViewModel(repo, expenseRepo) as T
            }
    }
}
