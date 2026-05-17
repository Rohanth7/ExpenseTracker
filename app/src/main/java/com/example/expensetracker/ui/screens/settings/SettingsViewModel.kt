package com.example.expensetracker.ui.screens.settings

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.backup.BackupData
import com.example.expensetracker.data.backup.BackupManager
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.RecurringTemplate
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.data.repository.RecurringTemplateRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val categoryRepo: CategoryRepository,
    private val expenseRepo: ExpenseRepository,
    private val recurringRepo: RecurringTemplateRepository,
    private val backupManager: BackupManager
) : ViewModel() {

    val categories: StateFlow<List<Category>> = categoryRepo.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recurringTemplates: StateFlow<List<RecurringTemplate>> = recurringRepo.allTemplates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var snackbarMessage by mutableStateOf<String?>(null)
        private set

    var pendingImportUri by mutableStateOf<Uri?>(null)
        private set

    fun clearSnackbar() { snackbarMessage = null }
    fun requestImport(uri: Uri) { pendingImportUri = uri }
    fun cancelImport() { pendingImportUri = null }

    fun exportTo(uri: Uri) = viewModelScope.launch {
        val cats = categoryRepo.allCategories.first()
        val exps = expenseRepo.allExpenses.first()
        val ok = backupManager.exportToUri(uri, BackupData(categories = cats, expenses = exps))
        snackbarMessage = if (ok) "Backup exported (${exps.size} expenses)" else "Export failed"
    }

    fun confirmImport() = viewModelScope.launch {
        val uri = pendingImportUri ?: return@launch
        pendingImportUri = null
        val data = backupManager.importFromUri(uri) ?: run {
            snackbarMessage = "Import failed — invalid or corrupted file"
            return@launch
        }
        // Clear existing data then restore
        expenseRepo.deleteAll()
        categoryRepo.deleteAll()
        recurringRepo.deleteAll()
        data.categories.forEach { categoryRepo.insert(it) }
        data.expenses.forEach { expenseRepo.insert(it) }
        snackbarMessage = "Restored ${data.expenses.size} expenses and ${data.categories.size} categories"
    }

    fun addTemplate(name: String, amount: Double, categoryId: Long) = viewModelScope.launch {
        recurringRepo.insert(RecurringTemplate(name = name, amount = amount, categoryId = categoryId))
    }

    fun toggleTemplate(template: RecurringTemplate) = viewModelScope.launch {
        recurringRepo.update(template.copy(enabled = !template.enabled))
    }

    fun deleteTemplate(template: RecurringTemplate) = viewModelScope.launch {
        recurringRepo.delete(template)
    }

    companion object {
        fun factory(
            categoryRepo: CategoryRepository,
            expenseRepo: ExpenseRepository,
            recurringRepo: RecurringTemplateRepository,
            backupManager: BackupManager
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(categoryRepo, expenseRepo, recurringRepo, backupManager) as T
        }
    }
}
