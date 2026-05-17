package com.example.expensetracker.ui.screens.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.repository.CategoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(private val repo: CategoryRepository) : ViewModel() {

    val categories: StateFlow<List<Category>> = repo.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCategory(name: String, emoji: String, colorHex: String, monthlyLimit: Double) =
        viewModelScope.launch {
            repo.insert(Category(name = name, emoji = emoji, colorHex = colorHex, monthlyLimit = monthlyLimit))
        }

    fun updateCategory(category: Category) = viewModelScope.launch { repo.update(category) }

    fun deleteCategory(category: Category) = viewModelScope.launch { repo.delete(category) }

    companion object {
        fun factory(repo: CategoryRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CategoriesViewModel(repo) as T
        }
    }
}
