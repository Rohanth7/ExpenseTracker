package com.example.expensetracker.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.backup.BackupData
import com.example.expensetracker.data.backup.BackupManager
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Bill
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Loan
import com.example.expensetracker.data.db.entity.RecurringTemplate
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.data.repository.BillRepository
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.LoanRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.data.repository.MerchantMappingRepository
import com.example.expensetracker.data.repository.RecurringTemplateRepository
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
    private val appContext: Context,
    private val categoryRepo: CategoryRepository,
    private val expenseRepo: ExpenseRepository,
    private val recurringRepo: RecurringTemplateRepository,
    private val billRepo: BillRepository,
    private val loanRepo: LoanRepository,
    private val backupManager: BackupManager,
    private val prefs: PreferencesManager
) : ViewModel() {

    val categories: StateFlow<List<Category>> = categoryRepo.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recurringTemplates: StateFlow<List<RecurringTemplate>> = recurringRepo.allTemplates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bills: StateFlow<List<Bill>> = billRepo.allBills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loans: StateFlow<List<Loan>> = loanRepo.allLoans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addLoan(name: String, emoji: String, totalAmount: Double, monthlyEmi: Double, tenureMonths: Int, startDate: Long, dueDayOfMonth: Int) = viewModelScope.launch {
        loanRepo.insert(Loan(name = name, emoji = emoji, totalAmount = totalAmount, monthlyEmi = monthlyEmi, tenureMonths = tenureMonths, startDate = startDate, dueDayOfMonth = dueDayOfMonth))
    }

    fun deleteLoan(loan: Loan) = viewModelScope.launch { loanRepo.delete(loan) }

    fun addBill(name: String, amount: Double, dueDayOfMonth: Int, reminderDays: Int, categoryId: Long) = viewModelScope.launch {
        billRepo.insert(Bill(name = name, amount = amount, dueDayOfMonth = dueDayOfMonth, reminderDays = reminderDays, categoryId = categoryId))
    }

    fun toggleBill(bill: Bill) = viewModelScope.launch {
        billRepo.update(bill.copy(isEnabled = !bill.isEnabled))
    }

    fun deleteBill(bill: Bill) = viewModelScope.launch {
        billRepo.delete(bill)
    }

    // ── Preferences ────────────────────────────────────────────

    private val _privacyMode = MutableStateFlow(prefs.privacyMode)
    val privacyMode: StateFlow<Boolean> = _privacyMode.asStateFlow()
    fun setPrivacyMode(enabled: Boolean) { prefs.privacyMode = enabled; _privacyMode.value = enabled }

    private val _budgetAlertsEnabled = MutableStateFlow(prefs.budgetAlertsEnabled)
    val budgetAlertsEnabled: StateFlow<Boolean> = _budgetAlertsEnabled.asStateFlow()
    fun setBudgetAlertsEnabled(enabled: Boolean) { prefs.budgetAlertsEnabled = enabled; _budgetAlertsEnabled.value = enabled }

    private val _weekStartsOnMonday = MutableStateFlow(prefs.weekStartsOnMonday)
    val weekStartsOnMonday: StateFlow<Boolean> = _weekStartsOnMonday.asStateFlow()
    fun setWeekStartsOnMonday(monday: Boolean) { prefs.weekStartsOnMonday = monday; _weekStartsOnMonday.value = monday }

    private val _biometricLockEnabled = MutableStateFlow(prefs.biometricLockEnabled)
    val biometricLockEnabled: StateFlow<Boolean> = _biometricLockEnabled.asStateFlow()
    fun setBiometricLockEnabled(enabled: Boolean) { prefs.biometricLockEnabled = enabled; _biometricLockEnabled.value = enabled }

    private val _widgetEnabled = MutableStateFlow(prefs.widgetEnabled)
    val widgetEnabled: StateFlow<Boolean> = _widgetEnabled.asStateFlow()
    fun setWidgetEnabled(enabled: Boolean) { prefs.widgetEnabled = enabled; _widgetEnabled.value = enabled }

    private val _autoBackupEnabled = MutableStateFlow(prefs.autoBackupEnabled)
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled.asStateFlow()
    fun setAutoBackupEnabled(enabled: Boolean) {
        prefs.autoBackupEnabled = enabled
        _autoBackupEnabled.value = enabled
        if (enabled) backupManager.scheduleAutoBackup() else backupManager.cancelAutoBackup()
    }

    private val _driveConnected = MutableStateFlow(prefs.driveConnected)
    val driveConnected: StateFlow<Boolean> = _driveConnected.asStateFlow()

    private val _driveAccountEmail = MutableStateFlow(prefs.driveAccountEmail)
    val driveAccountEmail: StateFlow<String> = _driveAccountEmail.asStateFlow()

    fun setDriveAccount(email: String) {
        prefs.driveConnected = true
        prefs.driveAccountEmail = email
        _driveConnected.value = true
        _driveAccountEmail.value = email
        snackbarMessage = "Google Drive connected"
    }

    fun disconnectDrive() {
        prefs.driveConnected = false
        prefs.driveAccountEmail = ""
        _driveConnected.value = false
        _driveAccountEmail.value = ""
        snackbarMessage = "Google Drive disconnected"
    }

    private val _monthlyIncome = MutableStateFlow(prefs.monthlyIncome)
    val monthlyIncome: StateFlow<Double> = _monthlyIncome.asStateFlow()
    fun setMonthlyIncome(value: Double) { prefs.monthlyIncome = value; _monthlyIncome.value = value }

    // ── UPI capture stats ───────────────────────────────────────

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

    // ── Backup / restore ────────────────────────────────────────

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
        expenseRepo.deleteAll()
        categoryRepo.deleteAll()
        recurringRepo.deleteAll()
        data.categories.forEach { categoryRepo.insert(it) }
        data.expenses.forEach { expenseRepo.insert(it) }
        val templates = data.recurringTemplates ?: emptyList()
        templates.forEach { recurringRepo.insert(it) }
        snackbarMessage = "Restored ${data.expenses.size} expenses, ${data.categories.size} categories" +
            if (templates.isNotEmpty()) ", ${templates.size} recurring" else ""
    }

    fun resetAll() = viewModelScope.launch {
        expenseRepo.deleteAll()
        categoryRepo.deleteAll()
        recurringRepo.deleteAll()
        snackbarMessage = "All data deleted"
    }

    // ── Recurring templates ─────────────────────────────────────

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
            billRepo: BillRepository,
            loanRepo: LoanRepository,
            mappingRepo: MerchantMappingRepository,
            backupManager: BackupManager,
            prefs: PreferencesManager
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(context.applicationContext, categoryRepo, expenseRepo, recurringRepo, billRepo, loanRepo, backupManager, prefs) as T
        }
    }
}
