package com.example.expensetracker.ui.screens.expenses

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.data.repository.MerchantMappingRepository
import com.example.expensetracker.notification.BudgetAlertHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ExpensesViewModel(
    private val expenseRepo: ExpenseRepository,
    private val categoryRepo: CategoryRepository,
    private val mappingRepo: MerchantMappingRepository,
    private val appContext: Context,
    private val prefs: PreferencesManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _categoryFilter = MutableStateFlow<Long?>(-1L)
    private val _tagFilter = MutableStateFlow<String?>(null)
    private val _sortByAmount = MutableStateFlow(false)
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _dateRange = MutableStateFlow<Pair<Long, Long>?>(null)
    private val _minAmount = MutableStateFlow<Double?>(null)
    private val _maxAmount = MutableStateFlow<Double?>(null)

    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val categoryFilter: StateFlow<Long?> = _categoryFilter.asStateFlow()
    val tagFilter: StateFlow<String?> = _tagFilter.asStateFlow()
    val sortByAmount: StateFlow<Boolean> = _sortByAmount.asStateFlow()
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()
    val dateRange: StateFlow<Pair<Long, Long>?> = _dateRange.asStateFlow()
    val minAmount: StateFlow<Double?> = _minAmount.asStateFlow()
    val maxAmount: StateFlow<Double?> = _maxAmount.asStateFlow()

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
        combine(_categoryFilter, categories) { f, cats -> f to cats },
        combine(_sortByAmount, _dateRange, _minAmount, _maxAmount, _tagFilter) { b, r, min, max, tag ->
            Quint(b, r, min, max, tag)
        }
    ) { all, query, (catFilter, cats), extra ->
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
            val matchesTag = tag == null || parseTags(expense.tags).any { it.equals(tag, ignoreCase = true) }
            matchesQuery && matchesCat && matchesRange && matchesMin && matchesMax && matchesTag
        }
        if (byAmount) filtered.sortedByDescending { it.amount } else filtered
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    data class TagSummary(val name: String, val count: Int, val totalSpent: Double)

    val allTagSummaries: StateFlow<List<TagSummary>> = _allExpenses.map { expenses ->
        val map = mutableMapOf<String, Pair<Int, Double>>()
        expenses.forEach { expense ->
            parseTags(expense.tags).forEach { tag ->
                val (c, t) = map.getOrDefault(tag, 0 to 0.0)
                map[tag] = (c + 1) to (t + expense.amount)
            }
        }
        map.entries.map { TagSummary(it.key, it.value.first, it.value.second) }
            .sortedByDescending { it.totalSpent }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private data class Quint<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
    private operator fun <A, B, C, D, E> Quint<A, B, C, D, E>.component1() = first
    private operator fun <A, B, C, D, E> Quint<A, B, C, D, E>.component2() = second
    private operator fun <A, B, C, D, E> Quint<A, B, C, D, E>.component3() = third
    private operator fun <A, B, C, D, E> Quint<A, B, C, D, E>.component4() = fourth
    private operator fun <A, B, C, D, E> Quint<A, B, C, D, E>.component5() = fifth

    private val _weekStartsOnMonday = MutableStateFlow(prefs.weekStartsOnMonday)
    val weekStartsOnMonday: StateFlow<Boolean> = _weekStartsOnMonday.asStateFlow()

    fun syncWeekStart() { _weekStartsOnMonday.value = prefs.weekStartsOnMonday }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setCategoryFilter(id: Long?) { _categoryFilter.value = id }
    fun setTagFilter(tag: String?) { _tagFilter.value = tag }
    fun toggleSortOrder() { _sortByAmount.value = !_sortByAmount.value }
    fun setDateRange(start: Long, end: Long) { _dateRange.value = start to end }
    fun clearDateRange() { _dateRange.value = null }
    fun setMinAmount(amt: Double?) { _minAmount.value = amt }
    fun setMaxAmount(amt: Double?) { _maxAmount.value = amt }
    fun clearAllFilters() {
        _searchQuery.value = ""
        _categoryFilter.value = -1L
        _tagFilter.value = null
        _dateRange.value = null
        _minAmount.value = null
        _maxAmount.value = null
    }

    fun renameTag(oldName: String, newName: String) = viewModelScope.launch {
        val trimNew = newName.trim().trimStart('#')
        if (trimNew.isBlank() || trimNew == oldName.trimStart('#')) return@launch
        _allExpenses.value.filter { parseTags(it.tags).any { t -> t.equals(oldName, ignoreCase = true) } }.forEach { expense ->
            val updated = parseTags(expense.tags).map { if (it.equals(oldName, ignoreCase = true)) trimNew else it }
            expenseRepo.update(expense.copy(tags = updated.joinToString(",")))
        }
        if (_tagFilter.value?.equals(oldName, ignoreCase = true) == true) _tagFilter.value = trimNew
    }

    fun deleteTag(name: String) = viewModelScope.launch {
        _allExpenses.value.filter { parseTags(it.tags).any { t -> t.equals(name, ignoreCase = true) } }.forEach { expense ->
            val updated = parseTags(expense.tags).filter { !it.equals(name, ignoreCase = true) }
            expenseRepo.update(expense.copy(tags = updated.joinToString(",")))
        }
        if (_tagFilter.value?.equals(name, ignoreCase = true) == true) _tagFilter.value = null
    }

    fun toggleSelection(id: Long) {
        val current = _selectedIds.value
        _selectedIds.value = if (id in current) current - id else current + id
    }

    fun clearSelection() { _selectedIds.value = emptySet() }

    fun bulkDelete() = viewModelScope.launch {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return@launch
        
        val toDelete = _allExpenses.value.filter { it.id in ids }
        toDelete.forEach { expenseRepo.delete(it) }
        _selectedIds.value = emptySet()
    }

    fun bulkCategorize(categoryId: Long) = viewModelScope.launch {
        val ids = _selectedIds.value
        if (ids.isEmpty() || categoryId == -1L) return@launch
        
        val toUpdate = _allExpenses.value.filter { it.id in ids }
        toUpdate.forEach { 
            expenseRepo.update(it.copy(categoryId = categoryId))
            if (it.source != "Manual") {
                mappingRepo.saveMapping(it.description, categoryId)
            }
            if (prefs.budgetAlertsEnabled) {
                BudgetAlertHelper.checkAndNotify(appContext, categoryId, categoryRepo, expenseRepo, prefs.budgetAlertThreshold)
            }
        }
        _selectedIds.value = emptySet()
    }

    fun addExpense(amount: Double, description: String, categoryId: Long, date: Long, tags: String = "", paymentMethod: String = "UPI") = viewModelScope.launch {
        expenseRepo.insert(Expense(amount = amount, description = description, categoryId = categoryId, date = date, tags = tags, paymentMethod = paymentMethod))
        if (categoryId != -1L && prefs.budgetAlertsEnabled) {
            BudgetAlertHelper.checkAndNotify(appContext, categoryId, categoryRepo, expenseRepo, prefs.budgetAlertThreshold)
        }
    }

    fun updateExpense(expense: Expense, amount: Double, description: String, categoryId: Long, date: Long, tags: String = "", paymentMethod: String = expense.paymentMethod) = viewModelScope.launch {
        expenseRepo.update(expense.copy(amount = amount, description = description, categoryId = categoryId, date = date, tags = tags, paymentMethod = paymentMethod))
        if (expense.source != "Manual" && categoryId != -1L) {
            mappingRepo.saveMapping(description, categoryId)
        }
        if (categoryId != -1L && prefs.budgetAlertsEnabled) {
            BudgetAlertHelper.checkAndNotify(appContext, categoryId, categoryRepo, expenseRepo, prefs.budgetAlertThreshold)
        }
    }

    fun deleteExpense(expense: Expense) = viewModelScope.launch {
        expenseRepo.delete(expense)
    }

    companion object {
        fun parseTags(raw: String): List<String> =
            raw.split(",").map { it.trim().trimStart('#') }.filter { it.isNotBlank() }

        fun factory(expenseRepo: ExpenseRepository, categoryRepo: CategoryRepository, mappingRepo: MerchantMappingRepository, appContext: Context, prefs: PreferencesManager) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ExpensesViewModel(expenseRepo, categoryRepo, mappingRepo, appContext, prefs) as T
            }
    }
}
