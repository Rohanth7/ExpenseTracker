package com.example.expensetracker.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.backup.BackupData
import com.example.expensetracker.data.backup.BackupManager
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.RecurringTemplate
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.data.repository.MerchantMappingRepository
import com.example.expensetracker.data.repository.RecurringTemplateRepository
import com.example.expensetracker.ui.widgets.WidgetUpdateHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class SettingsViewModel(
    private val context: Context,
    private val db: AppDatabase,
    private val categoryRepo: CategoryRepository,
    private val expenseRepo: ExpenseRepository,
    private val recurringRepo: RecurringTemplateRepository,
    private val mappingRepo: MerchantMappingRepository,
    private val backupManager: BackupManager,
    private val prefs: PreferencesManager
) : ViewModel() {

    val categories: StateFlow<List<Category>> = categoryRepo.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recurringTemplates: StateFlow<List<RecurringTemplate>> = recurringRepo.allTemplates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _privacyMode = MutableStateFlow(prefs.privacyMode)
    val privacyMode: StateFlow<Boolean> = _privacyMode.asStateFlow()

    fun setPrivacyMode(enabled: Boolean) {
        prefs.privacyMode = enabled
        _privacyMode.value = enabled
    }

    private val _budgetAlertsEnabled = MutableStateFlow(prefs.budgetAlertsEnabled)
    val budgetAlertsEnabled: StateFlow<Boolean> = _budgetAlertsEnabled.asStateFlow()

    fun setBudgetAlertsEnabled(enabled: Boolean) {
        prefs.budgetAlertsEnabled = enabled
        _budgetAlertsEnabled.value = enabled
    }

    private val _weekStartsOnMonday = MutableStateFlow(prefs.weekStartsOnMonday)
    val weekStartsOnMonday: StateFlow<Boolean> = _weekStartsOnMonday.asStateFlow()

    fun setWeekStartsOnMonday(monday: Boolean) {
        prefs.weekStartsOnMonday = monday
        _weekStartsOnMonday.value = monday
    }

    private val _biometricLockEnabled = MutableStateFlow(prefs.biometricLockEnabled)
    val biometricLockEnabled: StateFlow<Boolean> = _biometricLockEnabled.asStateFlow()

    fun setBiometricLockEnabled(enabled: Boolean) {
        prefs.biometricLockEnabled = enabled
        _biometricLockEnabled.value = enabled
    }

    private val _widgetEnabled = MutableStateFlow(prefs.widgetEnabled)
    val widgetEnabled: StateFlow<Boolean> = _widgetEnabled.asStateFlow()

    fun setWidgetEnabled(enabled: Boolean) {
        prefs.widgetEnabled = enabled
        _widgetEnabled.value = enabled
        viewModelScope.launch {
            WidgetUpdateHelper.update(context)
        }
    }

    private val _monthlyIncome = MutableStateFlow(prefs.monthlyIncome)
    val monthlyIncome: StateFlow<Double> = _monthlyIncome.asStateFlow()

    fun setMonthlyIncome(value: Double) {
        prefs.monthlyIncome = value
        _monthlyIncome.value = value
        viewModelScope.launch {
            WidgetUpdateHelper.update(context)
        }
    }

    data class CaptureStats(val today: Int, val thisMonth: Int, val allTime: Int)

    val captureStats: StateFlow<CaptureStats> = expenseRepo.allExpenses.map { expenses ->
        val autoExpenses = expenses.filter { it.source != "Manual" && it.source != "Recurring" }
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val monthStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        CaptureStats(
            today = autoExpenses.count { it.date >= todayStart },
            thisMonth = autoExpenses.count { it.date >= monthStart },
            allTime = autoExpenses.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CaptureStats(0, 0, 0))

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
        val templates = recurringRepo.allTemplates.first()
        val ok = backupManager.exportToUri(uri, BackupData(categories = cats, expenses = exps, recurringTemplates = templates))
        snackbarMessage = if (ok) "Backup exported (${exps.size} expenses)" else "Export failed"
    }

    fun confirmImport() = viewModelScope.launch {
        val uri = pendingImportUri ?: return@launch
        pendingImportUri = null
        val data = backupManager.importFromUri(uri) ?: run {
            snackbarMessage = "Import failed — invalid or corrupted file"
            return@launch
        }
        
        try {
            db.withTransaction {
                // Clear existing data then restore
                expenseRepo.deleteAll()
                categoryRepo.deleteAll()
                recurringRepo.deleteAll()
                mappingRepo.deleteAll()
                
                categoryRepo.insertAll(data.categories)
                expenseRepo.insertAll(data.expenses)
                data.recurringTemplates?.let { recurringRepo.insertAll(it) }
            }
            val templates = data.recurringTemplates ?: emptyList()
            snackbarMessage = "Restored ${data.expenses.size} expenses, ${data.categories.size} categories" +
                if (templates.isNotEmpty()) ", ${templates.size} recurring" else ""
        } catch (e: Exception) {
            e.printStackTrace()
            snackbarMessage = "Import failed during database update"
        }
    }

    fun resetAll() = viewModelScope.launch {
        expenseRepo.deleteAll()
        categoryRepo.deleteAll()
        recurringRepo.deleteAll()
        mappingRepo.deleteAll()
        snackbarMessage = "All data deleted"
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
            context: Context,
            db: AppDatabase,
            categoryRepo: CategoryRepository,
            expenseRepo: ExpenseRepository,
            recurringRepo: RecurringTemplateRepository,
            mappingRepo: MerchantMappingRepository,
            backupManager: BackupManager,
            prefs: PreferencesManager
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(context, db, categoryRepo, expenseRepo, recurringRepo, mappingRepo, backupManager, prefs) as T
        }
    }
}
