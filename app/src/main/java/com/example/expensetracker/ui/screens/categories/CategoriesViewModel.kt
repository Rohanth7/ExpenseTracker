package com.example.expensetracker.ui.screens.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.data.repository.IncomeRepository
import com.example.expensetracker.ui.screens.expenses.TagSummary
import com.example.expensetracker.ui.screens.expenses.Tags
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryGroup(val parent: Category, val children: List<Category>)

class CategoriesViewModel(
    private val repo: CategoryRepository,
    private val expenseRepo: ExpenseRepository,
    /** Tags are renamed and deleted on income too. */
    private val incomeRepo: IncomeRepository
) : ViewModel() {

    val categories: StateFlow<List<Category>> = repo.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupedCategories: StateFlow<List<CategoryGroup>> = categories.map { list ->
        val parents = list.filter { it.parentId == null }
        val byParent = list.filter { it.parentId != null }.groupBy { it.parentId }
        parents.map { parent -> CategoryGroup(parent, byParent[parent.id] ?: emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTagSummaries: StateFlow<List<TagSummary>> = expenseRepo.allExpenses.map(Tags::summaries)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        Tags.renameEverywhere(expenseRepo, incomeRepo, oldName, newName)
    }

    fun deleteTag(name: String) = viewModelScope.launch {
        Tags.removeEverywhere(expenseRepo, incomeRepo, name)
    }

    companion object {
        fun factory(repo: CategoryRepository, expenseRepo: ExpenseRepository, incomeRepo: IncomeRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CategoriesViewModel(repo, expenseRepo, incomeRepo) as T
            }
    }
}
