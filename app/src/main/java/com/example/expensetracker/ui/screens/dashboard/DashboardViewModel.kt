package com.example.expensetracker.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.*
import java.util.Calendar

data class CategorySummary(
    val category: Category,
    val spent: Double,
    val percentage: Float
)

data class MonthlyTrend(val label: String, val total: Double)

data class DashboardUiState(
    val totalSpent: Double = 0.0,
    val monthlyIncome: Double = 0.0,
    val overBudget: Boolean = false,
    val categorySummaries: List<CategorySummary> = emptyList(),
    val pendingCount: Int = 0,
    val monthLabel: String = "",
    val canGoForward: Boolean = false,
    val monthlyTrends: List<MonthlyTrend> = emptyList()
)

class DashboardViewModel(
    private val categoryRepo: CategoryRepository,
    private val expenseRepo: ExpenseRepository,
    private val prefs: PreferencesManager
) : ViewModel() {

    private val _income = MutableStateFlow(prefs.monthlyIncome)
    private val _monthOffset = MutableStateFlow(0)

    val uiState: StateFlow<DashboardUiState> = combine(
        categoryRepo.allCategories,
        expenseRepo.allExpenses,
        _income,
        _monthOffset
    ) { categories, expenses, income, offset ->
        val (start, end) = monthRangeForOffset(offset)
        val allMonthExpenses = expenses.filter { it.date in start..end }
        val categorizedExpenses = allMonthExpenses.filter { it.categoryId != -1L }
        val pendingTotal = allMonthExpenses.filter { it.categoryId == -1L }.sumOf { it.amount }
        val categorizedTotal = categorizedExpenses.sumOf { it.amount }
        val total = categorizedTotal + pendingTotal

        val summaries = categories.map { cat ->
            val spent = categorizedExpenses.filter { it.categoryId == cat.id }.sumOf { it.amount }
            CategorySummary(cat, spent, if (categorizedTotal > 0) (spent / categorizedTotal * 100).toFloat() else 0f)
        }.filter { it.spent > 0.0 }.sortedByDescending { it.spent }

        // Always show the last 6 calendar months from today as the trend
        val trends = (5 downTo 0).map { i ->
            val (s, e) = monthRangeForOffset(-i)
            MonthlyTrend(
                label = monthLabelShort(-i),
                total = expenses.filter { it.date in s..e }.sumOf { it.amount }
            )
        }

        DashboardUiState(
            totalSpent = total,
            monthlyIncome = income,
            overBudget = income > 0 && total > income,
            categorySummaries = summaries,
            pendingCount = expenses.count { it.categoryId == -1L },
            monthLabel = monthLabelFull(offset),
            canGoForward = offset < 0,
            monthlyTrends = trends
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    fun setIncome(value: Double) {
        prefs.monthlyIncome = value
        _income.value = value
    }

    fun goToPreviousMonth() { _monthOffset.value-- }
    fun goToNextMonth() { if (_monthOffset.value < 0) _monthOffset.value++ }

    private fun monthRangeForOffset(offset: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, offset)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        return start to cal.timeInMillis
    }

    private val MONTHS = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")

    private fun monthLabelFull(offset: Int): String {
        val cal = Calendar.getInstance().also { it.add(Calendar.MONTH, offset) }
        return "${MONTHS[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}"
    }

    private fun monthLabelShort(offset: Int): String {
        val cal = Calendar.getInstance().also { it.add(Calendar.MONTH, offset) }
        return MONTHS[cal.get(Calendar.MONTH)]
    }

    companion object {
        fun factory(categoryRepo: CategoryRepository, expenseRepo: ExpenseRepository, prefs: PreferencesManager) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DashboardViewModel(categoryRepo, expenseRepo, prefs) as T
            }
    }
}
