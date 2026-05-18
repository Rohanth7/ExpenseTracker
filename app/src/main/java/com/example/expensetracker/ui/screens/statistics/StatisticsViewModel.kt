package com.example.expensetracker.ui.screens.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.*
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

data class MonthStat(val label: String, val total: Double, val start: Long, val end: Long)
data class CategoryYearlyStat(val category: Category, val total: Double)
data class CategoryTrend(val label: String, val amount: Double)
data class SubcategoryBreakdown(val category: Category, val amount: Double, val fraction: Float)

data class PaymentSplit(
    val autoAmount: Double = 0.0,
    val manualAmount: Double = 0.0,
    val recurringAmount: Double = 0.0
) {
    val total get() = autoAmount + manualAmount + recurringAmount
    fun fraction(amount: Double) = if (total > 0) (amount / total).toFloat().coerceIn(0f, 1f) else 0f
}

data class StatisticsUiState(
    val yearLabel: String = "",
    val totalThisYear: Double = 0.0,
    val avgPerMonth: Double = 0.0,
    val busiestMonth: String = "",
    val monthStats: List<MonthStat> = emptyList(),
    val topCategories: List<CategoryYearlyStat> = emptyList(),
    val canGoForward: Boolean = false,
    val insightTitle: String = "Annual overview",
    val insightBody: String = "Keep tracking each month to see your year-over-year spending trends.",
    val selectedCategoryTrend: List<CategoryTrend>? = null,
    val selectedCategory: Category? = null,
    val paymentSplit: PaymentSplit = PaymentSplit(),
    val subcategoryBreakdown: List<SubcategoryBreakdown>? = null
)

class StatisticsViewModel(
    private val expenseRepo: ExpenseRepository,
    private val categoryRepo: CategoryRepository
) : ViewModel() {

    private val MONTHS = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    private val _yearOffset = MutableStateFlow(0)
    private val _selectedCategoryId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<StatisticsUiState> = combine(
        expenseRepo.allExpenses,
        categoryRepo.allCategories,
        _yearOffset,
        _selectedCategoryId
    ) { expenses, categories, offset, selectedId ->
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
            MonthStat(MONTHS[month], yearExpenses.filter { it.date in start..end }.sumOf { it.amount }, start, end)
        }

        val monthsWithData = monthStats.count { it.total > 0 }
        val avgPerMonth = if (monthsWithData > 0) totalThisYear / monthsWithData else 0.0
        val busiestMonth = monthStats.maxByOrNull { it.total }?.takeIf { it.total > 0 }?.label ?: ""

        val parentCategories = categories.filter { it.parentId == null }
        val topCategories = parentCategories.map { parent ->
            val childIds = categories.filter { it.parentId == parent.id }.map { it.id }.toSet()
            val total = yearExpenses.filter { it.categoryId == parent.id || it.categoryId in childIds }.sumOf { it.amount }
            CategoryYearlyStat(parent, total)
        }.filter { it.total > 0 }.sortedByDescending { it.total }

        // Category trend for last 6 months
        val selectedCategory = categories.find { it.id == selectedId }
        val trend = selectedCategory?.let { cat ->
            val childIds = categories.filter { it.parentId == cat.id }.map { it.id }.toSet()
            (5 downTo 0).map { i ->
                val cal = Calendar.getInstance()
                cal.add(Calendar.MONTH, -i)
                val m = cal.get(Calendar.MONTH)
                val start = Calendar.getInstance().apply {
                    timeInMillis = cal.timeInMillis
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val end = Calendar.getInstance().apply {
                    timeInMillis = start
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                CategoryTrend(MONTHS[m], expenses.filter { (it.categoryId == cat.id || it.categoryId in childIds) && it.date in start..end }.sumOf { it.amount })
            }
        }

        val numFmt = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply { maximumFractionDigits = 0 }
        fun fmt(v: Double) = "₹${numFmt.format(v)}"

        val insightTitle: String
        val insightBody: String
        when {
            totalThisYear == 0.0 -> {
                insightTitle = "Annual overview"
                insightBody = "Keep tracking each month to see your year-over-year spending trends."
            }
            topCategories.isNotEmpty() -> {
                val top = topCategories.first()
                val pct = if (totalThisYear > 0) (top.total / totalThisYear * 100).toInt() else 0
                insightTitle = "${top.category.name} leads $year"
                insightBody = "${top.category.emoji} ${top.category.name} accounts for $pct% of your spending — ${fmt(top.total)} total."
            }
            busiestMonth.isNotEmpty() -> {
                val busiestTotal = monthStats.find { it.label == busiestMonth }?.total ?: 0.0
                insightTitle = "$busiestMonth was your busiest"
                insightBody = "You spent ${fmt(busiestTotal)} in $busiestMonth, averaging ${fmt(avgPerMonth)} per active month."
            }
            else -> {
                insightTitle = "$year in review"
                insightBody = "You've spent ${fmt(totalThisYear)} across $monthsWithData months in $year."
            }
        }

        val split = PaymentSplit(
            autoAmount = yearExpenses.filter { it.source != "Manual" && it.source != "Recurring" }.sumOf { it.amount },
            manualAmount = yearExpenses.filter { it.source == "Manual" }.sumOf { it.amount },
            recurringAmount = yearExpenses.filter { it.source == "Recurring" }.sumOf { it.amount }
        )

        val subcategoryBreakdown = selectedCategory?.let { cat ->
            val children = categories.filter { it.parentId == cat.id }
            if (children.isEmpty()) null
            else {
                val items = children.map { child ->
                    val amount = yearExpenses.filter { it.categoryId == child.id }.sumOf { it.amount }
                    child to amount
                }.filter { (_, amount) -> amount > 0 }.sortedByDescending { (_, amount) -> amount }
                val total = items.sumOf { (_, amount) -> amount }
                if (total == 0.0) null
                else items.map { (child, amount) ->
                    SubcategoryBreakdown(child, amount, (amount / total).toFloat().coerceIn(0f, 1f))
                }
            }
        }

        StatisticsUiState(
            yearLabel = year.toString(),
            totalThisYear = totalThisYear,
            avgPerMonth = avgPerMonth,
            busiestMonth = busiestMonth,
            monthStats = monthStats,
            topCategories = topCategories,
            canGoForward = offset < 0,
            insightTitle = insightTitle,
            insightBody = insightBody,
            selectedCategoryTrend = trend,
            selectedCategory = selectedCategory,
            paymentSplit = split,
            subcategoryBreakdown = subcategoryBreakdown
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatisticsUiState())

    fun goToPreviousYear() { _yearOffset.value-- }
    fun goToNextYear() { if (_yearOffset.value < 0) _yearOffset.value++ }

    fun selectCategory(id: Long?) { _selectedCategoryId.value = id }

    companion object {
        fun factory(expenseRepo: ExpenseRepository, categoryRepo: CategoryRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    StatisticsViewModel(expenseRepo, categoryRepo) as T
            }
    }
}
