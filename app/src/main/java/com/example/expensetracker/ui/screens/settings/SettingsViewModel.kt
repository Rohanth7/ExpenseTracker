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
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.db.entity.Loan
import com.example.expensetracker.data.db.entity.RecurringTemplate
import com.example.expensetracker.data.db.entity.SavingsGoal
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.data.repository.BillRepository
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.LoanRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.data.repository.MerchantMappingRepository
import com.example.expensetracker.data.repository.RecurringTemplateRepository
import com.example.expensetracker.data.repository.SavingsGoalRepository
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
    private val savingsGoalRepo: SavingsGoalRepository,
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

    fun loadMockData() = viewModelScope.launch {
        // Clear everything first
        expenseRepo.deleteAll()
        categoryRepo.deleteAll()
        recurringRepo.deleteAll()
        billRepo.deleteAll()
        loanRepo.deleteAll()
        savingsGoalRepo.deleteAll()

        // Categories
        val foodId       = categoryRepo.insert(Category(name = "Food & Dining",  emoji = "🍔", colorHex = "#E53935", monthlyLimit = 8000.0))
        val transportId  = categoryRepo.insert(Category(name = "Transport",       emoji = "🚗", colorHex = "#00897B", monthlyLimit = 3000.0))
        val shoppingId   = categoryRepo.insert(Category(name = "Shopping",        emoji = "🛍️", colorHex = "#1E88E5", monthlyLimit = 10000.0))
        val entertainId  = categoryRepo.insert(Category(name = "Entertainment",   emoji = "🎬", colorHex = "#8E24AA", monthlyLimit = 3000.0))
        val healthId     = categoryRepo.insert(Category(name = "Healthcare",      emoji = "💊", colorHex = "#F4511E", monthlyLimit = 2000.0))
        val utilitiesId  = categoryRepo.insert(Category(name = "Utilities",       emoji = "⚡", colorHex = "#6D4C41", monthlyLimit = 4000.0))
        val rentId       = categoryRepo.insert(Category(name = "Rent",            emoji = "🏠", colorHex = "#3949AB", monthlyLimit = 25000.0))

        fun d(year: Int, month: Int, day: Int): Long = Calendar.getInstance().apply {
            set(year, month - 1, day, 10, 30, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // May 2026 (current month)
        val mayExpenses = listOf(
            Expense(amount = 22000.0, description = "Rent May",              categoryId = rentId,      date = d(2026, 5, 1),  source = "Manual"),
            Expense(amount = 450.0,  description = "Swiggy Biryani",         categoryId = foodId,      date = d(2026, 5, 3),  source = "SMS"),
            Expense(amount = 85.0,   description = "Ola Auto",               categoryId = transportId, date = d(2026, 5, 5),  source = "SMS"),
            Expense(amount = 320.0,  description = "Zomato Pizza",           categoryId = foodId,      date = d(2026, 5, 7),  source = "SMS"),
            Expense(amount = 200.0,  description = "Metro Card Recharge",    categoryId = transportId, date = d(2026, 5, 8),  source = "Manual"),
            Expense(amount = 799.0,  description = "Amazon T-Shirt",         categoryId = shoppingId,  date = d(2026, 5, 9),  source = "SMS"),
            Expense(amount = 499.0,  description = "Netflix Subscription",   categoryId = entertainId, date = d(2026, 5, 10), source = "Manual"),
            Expense(amount = 340.0,  description = "Apollo Pharmacy",        categoryId = healthId,    date = d(2026, 5, 11), source = "Manual"),
            Expense(amount = 1850.0, description = "Electricity Bill",       categoryId = utilitiesId, date = d(2026, 5, 12), source = "Manual"),
            Expense(amount = 280.0,  description = "Domino's Pizza",         categoryId = foodId,      date = d(2026, 5, 13), source = "SMS"),
            Expense(amount = 320.0,  description = "Uber Cab",               categoryId = transportId, date = d(2026, 5, 14), source = "SMS"),
            Expense(amount = 2100.0, description = "BigBazaar Grocery",      categoryId = shoppingId,  date = d(2026, 5, 15), source = "Manual"),
            Expense(amount = 450.0,  description = "BookMyShow",             categoryId = entertainId, date = d(2026, 5, 16), source = "SMS"),
            Expense(amount = 60.0,   description = "Rapido Bike",            categoryId = transportId, date = d(2026, 5, 17), source = "SMS"),
            Expense(amount = 120.0,  description = "Chai Point",             categoryId = foodId,      date = d(2026, 5, 17), source = "Manual"),
            // Pending (uncategorized) — triggers the red banner
            Expense(amount = 1500.0, description = "PhonePe UPI",            categoryId = -1L,         date = d(2026, 5, 18), source = "SMS"),
            Expense(amount = 230.0,  description = "paytm@upi",              categoryId = -1L,         date = d(2026, 5, 18), source = "SMS"),
            Expense(amount = 89.0,   description = "merchant@ybl",           categoryId = -1L,         date = d(2026, 5, 18), source = "SMS")
        )

        // April 2026 (last month)
        val aprExpenses = listOf(
            Expense(amount = 22000.0, description = "Rent April",            categoryId = rentId,      date = d(2026, 4, 1),  source = "Manual"),
            Expense(amount = 380.0,  description = "Swiggy Order",           categoryId = foodId,      date = d(2026, 4, 2),  source = "SMS"),
            Expense(amount = 500.0,  description = "BMTC Monthly Pass",      categoryId = transportId, date = d(2026, 4, 3),  source = "Manual"),
            Expense(amount = 1499.0, description = "Flipkart Shoes",         categoryId = shoppingId,  date = d(2026, 4, 5),  source = "SMS"),
            Expense(amount = 1500.0, description = "Cult.fit Gym",           categoryId = entertainId, date = d(2026, 4, 7),  source = "Manual"),
            Expense(amount = 800.0,  description = "Dentist Clinic",         categoryId = healthId,    date = d(2026, 4, 8),  source = "Manual"),
            Expense(amount = 350.0,  description = "Water Bill",             categoryId = utilitiesId, date = d(2026, 4, 10), source = "Manual"),
            Expense(amount = 260.0,  description = "McDonald's",             categoryId = foodId,      date = d(2026, 4, 12), source = "SMS"),
            Expense(amount = 180.0,  description = "Ola Cab",                categoryId = transportId, date = d(2026, 4, 14), source = "SMS"),
            Expense(amount = 650.0,  description = "Amazon Books",           categoryId = shoppingId,  date = d(2026, 4, 16), source = "SMS"),
            Expense(amount = 420.0,  description = "Zomato Burger",          categoryId = foodId,      date = d(2026, 4, 19), source = "SMS"),
            Expense(amount = 360.0,  description = "INOX Movie",             categoryId = entertainId, date = d(2026, 4, 21), source = "Manual"),
            Expense(amount = 280.0,  description = "MedPlus Pharmacy",       categoryId = healthId,    date = d(2026, 4, 23), source = "Manual"),
            Expense(amount = 999.0,  description = "Airtel Internet Bill",   categoryId = utilitiesId, date = d(2026, 4, 25), source = "Manual"),
            Expense(amount = 540.0,  description = "Myntra Kurta",           categoryId = shoppingId,  date = d(2026, 4, 27), source = "SMS")
        )

        // March 2026 (two months ago)
        val marExpenses = listOf(
            Expense(amount = 22000.0, description = "Rent March",            categoryId = rentId,      date = d(2026, 3, 1),  source = "Manual"),
            Expense(amount = 3200.0, description = "DMart Grocery",          categoryId = shoppingId,  date = d(2026, 3, 5),  source = "Manual"),
            Expense(amount = 480.0,  description = "Swiggy Dinner",          categoryId = foodId,      date = d(2026, 3, 8),  source = "SMS"),
            Expense(amount = 420.0,  description = "Uber Ride",              categoryId = transportId, date = d(2026, 3, 10), source = "SMS"),
            Expense(amount = 1650.0, description = "BESCOM Electricity",     categoryId = utilitiesId, date = d(2026, 3, 12), source = "Manual"),
            Expense(amount = 760.0,  description = "BBQ Nation Dinner",      categoryId = foodId,      date = d(2026, 3, 15), source = "Manual"),
            Expense(amount = 540.0,  description = "PVR Cinemas",            categoryId = entertainId, date = d(2026, 3, 18), source = "Manual"),
            Expense(amount = 390.0,  description = "Medanta Pharmacy",       categoryId = healthId,    date = d(2026, 3, 20), source = "Manual"),
            Expense(amount = 1890.0, description = "Myntra Sale Haul",       categoryId = shoppingId,  date = d(2026, 3, 24), source = "SMS"),
            Expense(amount = 250.0,  description = "Rapido Auto",            categoryId = transportId, date = d(2026, 3, 28), source = "SMS")
        )

        (mayExpenses + aprExpenses + marExpenses).forEach { expenseRepo.insert(it) }

        // Bills
        billRepo.insert(Bill(name = "Rent",          amount = 22000.0, dueDayOfMonth = 1,  reminderDays = 5, categoryId = rentId))
        billRepo.insert(Bill(name = "Electricity",   amount = 1800.0,  dueDayOfMonth = 10, reminderDays = 3, categoryId = utilitiesId))
        billRepo.insert(Bill(name = "Airtel Internet", amount = 999.0, dueDayOfMonth = 5,  reminderDays = 3, categoryId = utilitiesId))
        billRepo.insert(Bill(name = "Netflix",       amount = 499.0,   dueDayOfMonth = 15, reminderDays = 2, categoryId = entertainId))

        // Loan — started 3 years ago
        val loanStart = Calendar.getInstance().apply { add(Calendar.YEAR, -3) }.timeInMillis
        loanRepo.insert(Loan(name = "Home Loan", emoji = "🏦", totalAmount = 4000000.0, monthlyEmi = 35000.0, tenureMonths = 180, startDate = loanStart, dueDayOfMonth = 5))

        // Savings goals
        savingsGoalRepo.insert(SavingsGoal(name = "Emergency Fund", targetAmount = 200000.0, currentAmount = 85000.0, colorHex = "#43A047"))
        savingsGoalRepo.insert(SavingsGoal(name = "New Laptop",     targetAmount = 80000.0,  currentAmount = 25000.0, colorHex = "#1E88E5"))
        savingsGoalRepo.insert(SavingsGoal(name = "Goa Trip",       targetAmount = 50000.0,  currentAmount = 12000.0, colorHex = "#FB8C00"))

        snackbarMessage = "Mock data loaded — ${(mayExpenses + aprExpenses + marExpenses).size} expenses across 3 months"
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
                SettingsViewModel(context.applicationContext, categoryRepo, expenseRepo, recurringRepo, billRepo, loanRepo, SavingsGoalRepository(db.savingsGoalDao()), backupManager, prefs) as T
        }
    }
}
