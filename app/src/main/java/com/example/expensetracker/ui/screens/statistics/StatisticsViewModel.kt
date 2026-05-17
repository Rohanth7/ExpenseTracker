package com.example.expensetracker.ui.screens.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.*
import java.util.Calendar

data class MonthStat(val label: String, val total: Double)
data class CategoryYearlyStat(val category: Category, val total: Double)

data class StatisticsUiState(
    val yearLabel: String = "",
    val totalThisYear: Double = 0.0,
    val avgPerMonth: Double = 0.0,
    val busiestMonth: String = "",
    val monthStats: List<MonthStat> = emptyList(),
    val topCategories: List<CategoryYearlyStat> = emptyList(),
    val canGoForward: Boolean = false
)

class StatisticsViewModel(
    private val expenseRepo: ExpenseRepository,
    private val categoryRepo: CategoryRepository
) : ViewModel() {

    private val MONTHS = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    private val _yearOffset = MutableStateFlow(0)

    val uiState: StateFlow<StatisticsUiState> = combine(
        expenseRepo.allExpenses,
        categoryRepo.allCategories,
        _yearOffset
    ) { expenses, categories, offset ->
        val year = Calendar.getInstance().get(Calendar.YEAR) + offset

        val yearStart = Calendar.getInstance().apply {
            set(year, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val yearEnd = Calendar.getInstance().apply {
            set(year, Calendar.DECEMBER, 31, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val yearExpenses = expenses.filter { it.date in yearStart..yearEnd }
        val totalThisYear = yearExpenses.sumOf { it.amount }

        val monthStats = (0..11).map { month ->
            val start = Calendar.getInstance().apply {
                set(year, month, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val end = Calendar.getInstance().apply {
                set(year, month, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis
            MonthStat(MONTHS[month], yearExpenses.filter { it.date in start..end }.sumOf { it.amount })
        }

        val monthsWithData = monthStats.count { it.total > 0 }
        val avgPerMonth = if (monthsWithData > 0) totalThisYear / monthsWithData else 0.0
        val busiestMonth = monthStats.maxByOrNull { it.total }?.takeIf { it.total > 0 }?.label ?: ""

        val topCategories = categories.map { cat ->
            CategoryYearlyStat(cat, yearExpenses.filter { it.categoryId == cat.id }.sumOf { it.amount })
        }.filter { it.total > 0 }.sortedByDescending { it.total }

        StatisticsUiState(
            yearLabel = year.toString(),
            totalThisYear = totalThisYear,
            avgPerMonth = avgPerMonth,
            busiestMonth = busiestMonth,
            monthStats = monthStats,
            topCategories = topCategories,
            canGoForward = offset < 0
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatisticsUiState())

    fun goToPreviousYear() { _yearOffset.value-- }
    fun goToNextYear() { if (_yearOffset.value < 0) _yearOffset.value++ }

    companion object {
        fun factory(expenseRepo: ExpenseRepository, categoryRepo: CategoryRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    StatisticsViewModel(expenseRepo, categoryRepo) as T
            }
    }
}
