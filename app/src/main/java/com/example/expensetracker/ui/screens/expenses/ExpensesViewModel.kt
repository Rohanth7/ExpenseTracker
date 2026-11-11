package com.example.expensetracker.ui.screens.expenses

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.db.entity.Income
import com.example.expensetracker.data.export.DateFilter
import com.example.expensetracker.data.export.ExportPreset
import com.example.expensetracker.data.period.MonthStartDay
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.data.repository.IncomeRepository
import com.example.expensetracker.data.repository.MerchantMappingRepository
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.notification.BudgetAlertHelper
import com.example.expensetracker.notification.PriceChangeAlerts
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExpensesViewModel(
    private val expenseRepo: ExpenseRepository,
    private val categoryRepo: CategoryRepository,
    private val mappingRepo: MerchantMappingRepository,
    private val incomeRepo: IncomeRepository,
    private val appContext: Context,
    private val prefs: PreferencesManager,
    /** Keeps the filters if Android stops the app in the background. */
    saved: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val _searchQuery = saved.getMutableStateFlow("search", "")
    private val _categoryFilter = saved.getMutableStateFlow<Long?>("category", -1L)
    private val _tagFilter = saved.getMutableStateFlow<String?>("tag", null)
    private val _sortByAmount = saved.getMutableStateFlow("sort_by_amount", false)
    // Saved too: the bulk Categorize dialog comes back after Android closes the app, and acted on
    // an empty selection (nothing changed, with no word).
    private val _selectedIds = MutableStateFlow(saved.get<LongArray>("selected_ids")?.toSet() ?: emptySet())
    private val _dateFilter = saved.getMutableStateFlow<DateFilter?>("date_filter", null)
    /** Refreshed when the screen comes back, so "This month" moves on when the month does. */
    private val _now = MutableStateFlow(System.currentTimeMillis())
    private val _minAmount = saved.getMutableStateFlow<Double?>("min_amount", null)
    private val _maxAmount = saved.getMutableStateFlow<Double?>("max_amount", null)
    /** Spending or Income list; saved like the filters. */
    private val _showIncome = saved.getMutableStateFlow("show_income", false)

    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val categoryFilter: StateFlow<Long?> = _categoryFilter.asStateFlow()
    val tagFilter: StateFlow<String?> = _tagFilter.asStateFlow()
    val sortByAmount: StateFlow<Boolean> = _sortByAmount.asStateFlow()

    init {
        viewModelScope.launch { _selectedIds.collect { saved["selected_ids"] = it.toLongArray() } }
    }
    val dateFilter: StateFlow<DateFilter?> = _dateFilter.asStateFlow()
    /** The dates [dateFilter] covers now. */
    val dateRange: StateFlow<Pair<Long, Long>?> = combine(_dateFilter, _now, MonthStartDay.flow(appContext)) { filter, now, startDay ->
        filter?.range(now, startDay)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, _dateFilter.value?.range(_now.value, MonthStartDay.get(appContext)))
    val minAmount: StateFlow<Double?> = _minAmount.asStateFlow()
    val maxAmount: StateFlow<Double?> = _maxAmount.asStateFlow()
    val showIncome: StateFlow<Boolean> = _showIncome.asStateFlow()
    fun setShowIncome(show: Boolean) { _showIncome.value = show; if (show) _selectedIds.value = emptySet() }

    val categories: StateFlow<List<Category>> = categoryRepo.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Null until the database has answered, so "nothing loaded yet" isn't read as "nothing matches". */
    private val loadedExpenses: StateFlow<List<Expense>?> = expenseRepo.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _allExpenses: StateFlow<List<Expense>> = loadedExpenses.map { it.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasAnyExpenses: StateFlow<Boolean> = _allExpenses
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** The list on screen, or null while it's loading. */
    private val shownExpenses: StateFlow<List<Expense>?> = combine(
        loadedExpenses,
        _searchQuery,
        combine(_categoryFilter, categories) { f, cats -> f to cats },
        combine(_sortByAmount, dateRange, _minAmount, _maxAmount, _tagFilter) { b, r, min, max, tag ->
            Quint(b, r, min, max, tag)
        }
    ) { all, query, (catFilter, cats), extra ->
        if (all == null) return@combine null
        val (byAmount, range, minAmt, maxAmt, tag) = extra
        val filtered = all.filter { expense ->
            val matchesQuery = query.isBlank() ||
                expense.description.contains(query, ignoreCase = true) ||
                expense.tags.contains(query, ignoreCase = true)
            val matchesCat = catFilter == null || catFilter == -1L ||
                expense.categoryId == catFilter ||
                cats.find { it.id == expense.categoryId }?.parentId == catFilter
            val matchesRange = range == null || expense.date in range.first..range.second
            val matchesMin = minAmt == null || expense.amount >= minAmt
            val matchesMax = maxAmt == null || expense.amount <= maxAmt
            val matchesTag = tag == null || Tags.parse(expense.tags).any { it.equals(tag, ignoreCase = true) }
            matchesQuery && matchesCat && matchesRange && matchesMin && matchesMax && matchesTag
        }
        if (byAmount) filtered.sortedByDescending { it.amount } else filtered
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val expenses: StateFlow<List<Expense>> = shownExpenses.map { it.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The selected rows that are on screen: a search or filter that hides a selected row takes it
     * out of the count and out of bulk delete and move (it's selected again if the filter goes).
     * While the list is loading, the saved selection as it was.
     */
    val selectedIds: StateFlow<Set<Long>> = combine(_selectedIds, shownExpenses, ::selectionOnScreen)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _selectedIds.value)

    private fun selectionOnScreen(selected: Set<Long>, shown: List<Expense>?): Set<Long> =
        if (shown == null) selected else shown.mapNotNullTo(mutableSetOf()) { it.id.takeIf(selected::contains) }

    private val _allIncomes: StateFlow<List<Income>> = incomeRepo.allIncomes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasAnyIncome: StateFlow<Boolean> = _allIncomes
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** The Income list: search, dates and amount limits apply (see [filterIncomes]). */
    val incomes: StateFlow<List<Income>> = combine(_allIncomes, _searchQuery, dateRange, combine(_minAmount, _maxAmount, ::Pair), _sortByAmount) { all, query, range, (min, max), byAmount ->
        filterIncomes(all, query, range, min, max, byAmount)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTagSummaries: StateFlow<List<TagSummary>> = _allExpenses.map(Tags::summaries)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private data class Quint<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
    private operator fun <A, B, C, D, E> Quint<A, B, C, D, E>.component1() = first
    private operator fun <A, B, C, D, E> Quint<A, B, C, D, E>.component2() = second
    private operator fun <A, B, C, D, E> Quint<A, B, C, D, E>.component3() = third
    private operator fun <A, B, C, D, E> Quint<A, B, C, D, E>.component4() = fourth
    private operator fun <A, B, C, D, E> Quint<A, B, C, D, E>.component5() = fifth

    private val _weekStartsOnMonday = MutableStateFlow(prefs.weekStartsOnMonday)
    val weekStartsOnMonday: StateFlow<Boolean> = _weekStartsOnMonday.asStateFlow()

    /**
     * "THIS WEEK" in the header: everything spent (or earned) since the week began, whatever the
     * search and filters show. It was summed from the filtered list.
     */
    val weekTotal: StateFlow<Double> = combine(_allExpenses, _weekStartsOnMonday, _now) { all, monday, now ->
        val start = weekStart(now, monday)
        all.filter { it.date >= start }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val weekIncome: StateFlow<Double> = combine(_allIncomes, _weekStartsOnMonday, _now) { all, monday, now ->
        val start = weekStart(now, monday)
        all.filter { it.date >= start }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    /** Settings that can change while this screen is away (Settings, a backup restore). */
    fun syncPrefs() {
        _weekStartsOnMonday.value = prefs.weekStartsOnMonday
        _lastManualCategoryId.value = prefs.lastManualCategoryId
        _now.value = System.currentTimeMillis()
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setCategoryFilter(id: Long?) { _categoryFilter.value = id }
    fun setTagFilter(tag: String?) { _tagFilter.value = tag }
    fun toggleSortOrder() { _sortByAmount.value = !_sortByAmount.value }
    fun setDateRange(start: Long, end: Long) { _dateFilter.value = DateFilter(ExportPreset.CUSTOM, start to end) }
    fun setDateFilter(filter: DateFilter?) { _dateFilter.value = filter }
    fun clearDateRange() { _dateFilter.value = null }
    fun setMinAmount(amt: Double?) { _minAmount.value = amt }
    fun setMaxAmount(amt: Double?) { _maxAmount.value = amt }
    fun renameTag(oldName: String, newName: String) = viewModelScope.launch {
        val name = Tags.cleanName(newName) ?: return@launch
        Tags.renameEverywhere(expenseRepo, incomeRepo, oldName, name)
        if (_tagFilter.value?.equals(oldName, ignoreCase = true) == true) _tagFilter.value = name
    }

    fun deleteTag(name: String) = viewModelScope.launch {
        Tags.removeEverywhere(expenseRepo, incomeRepo, name)
        if (_tagFilter.value?.equals(name, ignoreCase = true) == true) _tagFilter.value = null
    }

    fun toggleSelection(id: Long) {
        val current = _selectedIds.value
        _selectedIds.value = if (id in current) current - id else current + id
    }

    fun clearSelection() { _selectedIds.value = emptySet() }

    fun bulkDelete() = viewModelScope.launch {
        val ids = selectionOnScreen(_selectedIds.value, shownExpenses.value)
        if (ids.isEmpty()) return@launch

        val toDelete = _allExpenses.value.filter { it.id in ids }
        toDelete.forEach { expenseRepo.delete(it) }
        _selectedIds.value = emptySet()
    }

    fun bulkCategorize(categoryId: Long) = viewModelScope.launch {
        val ids = selectionOnScreen(_selectedIds.value, shownExpenses.value)
        if (ids.isEmpty() || categoryId == -1L) return@launch

        val toUpdate = _allExpenses.value.filter { it.id in ids }
        toUpdate.forEach { 
            expenseRepo.update(it.copy(categoryId = categoryId))
            if (it.source != "Manual") {
                mappingRepo.saveMapping(it.description, categoryId)
            }
        }
        alertIfOverBudget(categoryId)
        _selectedIds.value = emptySet()
    }

    fun addExpense(amount: Double, description: String, categoryId: Long, date: Long, tags: String = "", paymentMethod: String = "UPI") {
        // Read at the tap, as the dialog showed them: by the time the insert returns they may have loaded.
        val categoriesLoaded = categories.value.isNotEmpty()
        viewModelScope.launch {
            val expense = Expense(amount = amount, description = description, categoryId = categoryId, date = date, tags = tags, paymentMethod = paymentMethod)
            val id = expenseRepo.insert(expense)
            // Choosing "Choose later" makes it the next default too (see shouldRememberCategory).
            if (shouldRememberCategory(categoryId, categoriesLoaded)) {
                prefs.lastManualCategoryId = categoryId; _lastManualCategoryId.value = categoryId
            }
            alertIfOverBudget(categoryId)
            alertIfPriceChanged(expense.copy(id = id))
        }
    }

    /** The add dialog's starting category (see [defaultCategoryId]). Read from prefs once, not per frame. */
    private val _lastManualCategoryId = MutableStateFlow(prefs.lastManualCategoryId)
    val lastManualCategoryId: StateFlow<Long> = _lastManualCategoryId.asStateFlow()

    /**
     * Edit's Duplicate: a copy of the form dated now (see [duplicateWithEdits]); with [saveEdits],
     * the form is saved to [original] first. One coroutine and one alert check, so the two writes
     * can't race each other into a warning and an "exceeded" alert at once.
     */
    fun duplicateExpense(original: Expense, amount: Double, description: String, categoryId: Long, date: Long, tags: String, paymentMethod: String, saveEdits: Boolean) = viewModelScope.launch {
        if (saveEdits) saveEdit(original, amount, description, categoryId, date, tags, paymentMethod)
        val copy = original.duplicateWithEdits(amount, description, categoryId, tags, paymentMethod, System.currentTimeMillis())
        val id = expenseRepo.insert(copy)
        alertIfOverBudget(categoryId)
        alertIfPriceChanged(copy.copy(id = id))
    }

    fun updateExpense(expense: Expense, amount: Double, description: String, categoryId: Long, date: Long, tags: String = "", paymentMethod: String = expense.paymentMethod) = viewModelScope.launch {
        saveEdit(expense, amount, description, categoryId, date, tags, paymentMethod)
        alertIfOverBudget(categoryId)
    }

    private suspend fun saveEdit(expense: Expense, amount: Double, description: String, categoryId: Long, date: Long, tags: String, paymentMethod: String) {
        expenseRepo.update(expense.copy(amount = amount, description = description, categoryId = categoryId, date = date, tags = tags, paymentMethod = paymentMethod))
        if (expense.source != "Manual" && categoryId != -1L) {
            mappingRepo.saveMapping(description, categoryId)
        }
    }

    /** The one budget-alert check after an expense is added, copied, edited or categorized. */
    private suspend fun alertIfOverBudget(categoryId: Long) {
        if (categoryId != -1L && prefs.budgetAlertsEnabled)
            BudgetAlertHelper.checkAndNotify(appContext, categoryId, categoryRepo, expenseRepo, prefs.budgetAlertThreshold)
    }

    /** A new expense may be a subscription's new price (edits are checked by [ExpenseRepository.update]). Never fails the add. */
    private suspend fun alertIfPriceChanged(expense: Expense) {
        runCatching { PriceChangeAlerts.checkAfterCapture(appContext, AppDatabase.getInstance(appContext), expense) }
    }

    fun deleteExpense(expense: Expense) = viewModelScope.launch {
        expenseRepo.delete(expense)
    }

    // Income writes finish even if the screen goes away mid-save.
    private fun write(block: suspend () -> Unit) = viewModelScope.launch { withContext(NonCancellable) { block() } }

    fun addIncome(amount: Double, description: String, date: Long, tags: String) =
        write { incomeRepo.insert(Income(amount = amount, description = description, date = date, tags = tags)) }

    fun updateIncome(income: Income, amount: Double, description: String, date: Long, tags: String) =
        write { incomeRepo.update(income.copy(amount = amount, description = description, date = date, tags = tags)) }

    /** Edit's Duplicate for income; with [saveEdits] the form is saved to [original] first. */
    fun duplicateIncome(original: Income, amount: Double, description: String, date: Long, tags: String, saveEdits: Boolean) = write {
        if (saveEdits) incomeRepo.update(original.copy(amount = amount, description = description, date = date, tags = tags))
        incomeRepo.insert(original.duplicateWithEdits(amount, description, tags, System.currentTimeMillis()))
    }

    fun deleteIncome(income: Income) = write { incomeRepo.delete(income) }

    companion object {
        fun factory(expenseRepo: ExpenseRepository, categoryRepo: CategoryRepository, mappingRepo: MerchantMappingRepository, incomeRepo: IncomeRepository, appContext: Context, prefs: PreferencesManager) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    ExpensesViewModel(expenseRepo, categoryRepo, mappingRepo, incomeRepo, appContext, prefs, extras.createSavedStateHandle()) as T
            }
    }
}

/** Midnight at the start of the week [now] is in. */
internal fun weekStart(now: Long, startsOnMonday: Boolean): Long {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
    val target = if (startsOnMonday) java.util.Calendar.MONDAY else java.util.Calendar.SUNDAY
    val diff = (cal.get(java.util.Calendar.DAY_OF_WEEK) - target + 7) % 7
    cal.add(java.util.Calendar.DAY_OF_YEAR, -diff)
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
