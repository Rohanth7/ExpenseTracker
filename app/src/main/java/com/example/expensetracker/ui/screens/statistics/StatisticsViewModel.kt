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
import kotlin.math.roundToInt

enum class ViewMode { MONTHLY, ANNUAL }

data class MonthStat(val label: String, val total: Double, val start: Long, val end: Long)
data class SubcategoryRow(val category: Category, val amount: Double)
data class CategoryYearlyStat(val category: Category, val total: Double, val subcategories: List<SubcategoryRow> = emptyList())
data class CategoryTrend(val label: String, val amount: Double)
data class SubcategoryBreakdown(val category: Category, val amount: Double, val fraction: Float)

data class PaymentSplit(
    val upiAmount: Double = 0.0,
    val creditCardAmount: Double = 0.0,
    val cashAmount: Double = 0.0
) {
    val total get() = upiAmount + creditCardAmount + cashAmount
    fun fraction(amount: Double) = if (total > 0) (amount / total).toFloat().coerceIn(0f, 1f) else 0f
}

data class MonthlyInsight(val emoji: String, val label: String, val detail: String)

data class DetectionSplit(
    val autoAmount: Double = 0.0,
    val autoCount: Int = 0,
    val manualAmount: Double = 0.0,
    val manualCount: Int = 0,
    val recurringAmount: Double = 0.0,
    val recurringCount: Int = 0
) {
    val total get() = autoAmount + manualAmount + recurringAmount
    fun fraction(amount: Double) = if (total > 0) (amount / total).toFloat().coerceIn(0f, 1f) else 0f
}

data class AutoTrackStats(
    val autoCount: Int = 0,
    val totalCount: Int = 0,
    val monthlyAutoCounts: List<Int> = emptyList(),
    val todayCount: Int = 0,
    val thisMonthCount: Int = 0,
    val allTimeCount: Int = 0
) {
    val rate: Int get() = if (totalCount > 0) (autoCount * 100 / totalCount) else 0
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
    val detectionSplit: DetectionSplit = DetectionSplit(),
    val subcategoryBreakdown: List<SubcategoryBreakdown>? = null,
    val autoTrackStats: AutoTrackStats = AutoTrackStats(),
    // Monthly view
    val monthLabel: String = "",
    val monthTotal: Double = 0.0,
    val monthCategories: List<CategoryYearlyStat> = emptyList(),
    val monthVsLastPct: Int? = null,
    val monthPaymentSplit: PaymentSplit = PaymentSplit(),
    val monthDetectionSplit: DetectionSplit = DetectionSplit(),
    val monthInsight: MonthlyInsight? = null,
    val canGoForwardMonth: Boolean = false,
    val monthStart: Long = 0L,
    val monthEnd: Long = 0L
)

class StatisticsViewModel(
    private val expenseRepo: ExpenseRepository,
    private val categoryRepo: CategoryRepository
) : ViewModel() {

    private val MONTHS = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    private val _yearOffset = MutableStateFlow(0)
    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    private val _monthOffset = MutableStateFlow(0)
    private val _viewMode = MutableStateFlow(ViewMode.MONTHLY)

    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()
    fun setViewMode(mode: ViewMode) { _viewMode.value = mode }
    fun goToPreviousMonthStats() { _monthOffset.value-- }
    fun goToNextMonthStats() { if (_monthOffset.value < 0) _monthOffset.value++ }

    val uiState: StateFlow<StatisticsUiState> = combine(
        expenseRepo.allExpenses,
        categoryRepo.allCategories,
        _yearOffset,
        _selectedCategoryId,
        _monthOffset
    ) { expenses, categories, offset, selectedId, monthOffset ->
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
            val children = categories.filter { it.parentId == parent.id }
            val childIds = children.map { it.id }.toSet()
            val total = yearExpenses.filter { it.categoryId == parent.id || it.categoryId in childIds }.sumOf { it.amount }
            val subcats = children.map { child ->
                SubcategoryRow(child, yearExpenses.filter { it.categoryId == child.id }.sumOf { it.amount })
            }.filter { it.amount > 0 }.sortedByDescending { it.amount }
            CategoryYearlyStat(parent, total, subcats)
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

        fun isAutoSource(s: String) = s != "Manual" && s != "Recurring"
        val split = PaymentSplit(
            upiAmount = yearExpenses.filter { it.paymentMethod == "UPI" }.sumOf { it.amount },
            creditCardAmount = yearExpenses.filter { it.paymentMethod == "Credit Card" }.sumOf { it.amount },
            cashAmount = yearExpenses.filter { it.paymentMethod == "Cash" }.sumOf { it.amount }
        )
        val detectionSplit = DetectionSplit(
            autoAmount = yearExpenses.filter { isAutoSource(it.source) }.sumOf { it.amount },
            autoCount = yearExpenses.count { isAutoSource(it.source) },
            manualAmount = yearExpenses.filter { it.source == "Manual" }.sumOf { it.amount },
            manualCount = yearExpenses.count { it.source == "Manual" },
            recurringAmount = yearExpenses.filter { it.source == "Recurring" }.sumOf { it.amount },
            recurringCount = yearExpenses.count { it.source == "Recurring" }
        )

        val todayStartMs = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val thisMonthStartMs = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val allAutoExpenses = expenses.filter { isAutoSource(it.source) }
        val autoStats = AutoTrackStats(
            autoCount = yearExpenses.count { isAutoSource(it.source) },
            totalCount = yearExpenses.size,
            monthlyAutoCounts = monthStats.map { ms ->
                yearExpenses.count { isAutoSource(it.source) && it.date in ms.start..ms.end }
            },
            todayCount = allAutoExpenses.count { it.date >= todayStartMs },
            thisMonthCount = allAutoExpenses.count { it.date >= thisMonthStartMs },
            allTimeCount = allAutoExpenses.size
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

        // ── Monthly stats ────────────────────────────────────────
        val mCal = Calendar.getInstance().apply { add(Calendar.MONTH, monthOffset) }
        val mYear = mCal.get(Calendar.YEAR)
        val mMonth = mCal.get(Calendar.MONTH)
        val mStart = Calendar.getInstance().apply {
            set(mYear, mMonth, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val mEnd = Calendar.getInstance().apply {
            set(mYear, mMonth, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        val monthExpenses = expenses.filter { it.date in mStart..mEnd }
        val mTotal = monthExpenses.sumOf { it.amount }
        val prevCal = Calendar.getInstance().apply {
            set(mYear, mMonth, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0); add(Calendar.MONTH, -1)
        }
        val prevStart = Calendar.getInstance().apply {
            set(prevCal.get(Calendar.YEAR), prevCal.get(Calendar.MONTH), 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val prevEnd = Calendar.getInstance().apply {
            set(prevCal.get(Calendar.YEAR), prevCal.get(Calendar.MONTH), 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        val prevTotal = expenses.filter { it.date in prevStart..prevEnd }.sumOf { it.amount }
        val mVsLastPct = if (prevTotal > 0) ((mTotal - prevTotal) / prevTotal * 100).toInt() else null
        val mLabel = "${MONTHS[mMonth]} $mYear"
        val mCategories = parentCategories.map { parent ->
            val children = categories.filter { it.parentId == parent.id }
            val childIds = children.map { it.id }.toSet()
            val total = monthExpenses.filter { it.categoryId == parent.id || it.categoryId in childIds }.sumOf { it.amount }
            val subcats = children.map { child ->
                SubcategoryRow(child, monthExpenses.filter { it.categoryId == child.id }.sumOf { it.amount })
            }.filter { it.amount > 0 }.sortedByDescending { it.amount }
            CategoryYearlyStat(parent, total, subcats)
        }.filter { it.total > 0 }.sortedByDescending { it.total }
        val mPaymentSplit = PaymentSplit(
            upiAmount = monthExpenses.filter { it.paymentMethod == "UPI" }.sumOf { it.amount },
            creditCardAmount = monthExpenses.filter { it.paymentMethod == "Credit Card" }.sumOf { it.amount },
            cashAmount = monthExpenses.filter { it.paymentMethod == "Cash" }.sumOf { it.amount }
        )
        val mDetectionSplit = DetectionSplit(
            autoAmount = monthExpenses.filter { isAutoSource(it.source) }.sumOf { it.amount },
            autoCount = monthExpenses.count { isAutoSource(it.source) },
            manualAmount = monthExpenses.filter { it.source == "Manual" }.sumOf { it.amount },
            manualCount = monthExpenses.count { it.source == "Manual" },
            recurringAmount = monthExpenses.filter { it.source == "Recurring" }.sumOf { it.amount },
            recurringCount = monthExpenses.count { it.source == "Recurring" }
        )

        val mInsight = buildMonthlyInsight(monthExpenses, prevTotal, categories, mYear, mMonth, monthOffset == 0)

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
            detectionSplit = detectionSplit,
            subcategoryBreakdown = subcategoryBreakdown,
            autoTrackStats = autoStats,
            monthLabel = mLabel,
            monthTotal = mTotal,
            monthCategories = mCategories,
            monthVsLastPct = mVsLastPct,
            monthPaymentSplit = mPaymentSplit,
            monthDetectionSplit = mDetectionSplit,
            monthInsight = mInsight,
            canGoForwardMonth = monthOffset < 0,
            monthStart = mStart,
            monthEnd = mEnd
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatisticsUiState())

    fun goToPreviousYear() { _yearOffset.value-- }
    fun goToNextYear() { if (_yearOffset.value < 0) _yearOffset.value++ }

    fun selectCategory(id: Long?) { _selectedCategoryId.value = id }

    private fun buildMonthlyInsight(
        monthExpenses: List<com.example.expensetracker.data.db.entity.Expense>,
        prevTotal: Double,
        categories: List<Category>,
        mYear: Int,
        mMonth: Int,
        isCurrentMonth: Boolean
    ): MonthlyInsight? {
        if (monthExpenses.isEmpty()) return null
        val total = monthExpenses.sumOf { it.amount }
        val numFmt = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply { maximumFractionDigits = 0 }
        fun fmt(v: Double) = numFmt.format(v)

        val lastDayOfMonth = Calendar.getInstance().apply { set(mYear, mMonth, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH)
        val effectiveDay = if (isCurrentMonth) Calendar.getInstance().get(Calendar.DAY_OF_MONTH) else lastDayOfMonth
        // Previous month's day count for accurate pace calculation
        val prevCal = Calendar.getInstance().apply { set(mYear, mMonth, 1); add(Calendar.MONTH, -1) }
        val prevLastDay = prevCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val pool = mutableListOf<MonthlyInsight>()

        // vs previous month
        if (prevTotal > 0) {
            val pct = if (isCurrentMonth) {
                val prevPaced = prevTotal / prevLastDay * effectiveDay
                if (prevPaced > 0) ((total - prevPaced) / prevPaced * 100).roundToInt() else null
            } else {
                ((total - prevTotal) / prevTotal * 100).roundToInt()
            }
            if (pct != null) when {
                pct > 20 -> pool.add(MonthlyInsight("📈", "${pct}% up on last month", "₹${fmt(kotlin.math.abs(total - prevTotal))} more than ${if (isCurrentMonth) "at this point last month" else "last month"}"))
                pct < -15 -> pool.add(MonthlyInsight("📉", "${-pct}% under last month", "₹${fmt(kotlin.math.abs(total - prevTotal))} less than last month — great job!"))
            }
        }

        // Most-visited merchant (≥ 2 times)
        val topMerchant = monthExpenses.groupBy { it.description.trim().lowercase() }
            .entries.filter { it.value.size >= 2 }.maxByOrNull { it.value.size }
        if (topMerchant != null) {
            val name = monthExpenses.first { it.description.trim().lowercase() == topMerchant.key }.description
            pool.add(MonthlyInsight("🛒", "$name · ${topMerchant.value.size}×", "₹${fmt(topMerchant.value.sumOf { it.amount })} across ${topMerchant.value.size} transactions"))
        }

        // No-spend days
        val daysWithSpend = monthExpenses.map { Calendar.getInstance().apply { timeInMillis = it.date }.get(Calendar.DAY_OF_MONTH) }.toSet()
        val noSpendDays = (1..effectiveDay).count { it !in daysWithSpend }
        if (noSpendDays >= 3) pool.add(MonthlyInsight("✨", "$noSpendDays no-spend days", "You went $noSpendDays day${if (noSpendDays != 1) "s" else ""} without spending this month"))

        // Weekend vs weekday daily spend
        var wkendTotal = 0.0; var wkdayTotal = 0.0
        monthExpenses.forEach { exp ->
            val dow = Calendar.getInstance().apply { timeInMillis = exp.date }.get(Calendar.DAY_OF_WEEK)
            if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) wkendTotal += exp.amount else wkdayTotal += exp.amount
        }
        var wkendDays = 0; var wkdayDays = 0
        for (d in 1..effectiveDay) {
            val dow = Calendar.getInstance().apply { set(mYear, mMonth, d) }.get(Calendar.DAY_OF_WEEK)
            if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) wkendDays++ else wkdayDays++
        }
        val wkendPerDay = if (wkendDays > 0) wkendTotal / wkendDays else 0.0
        val wkdayPerDay = if (wkdayDays > 0) wkdayTotal / wkdayDays else 0.0
        if (wkdayPerDay > 50 && wkendPerDay > 0 && wkendPerDay / wkdayPerDay >= 1.5) {
            val ratio = "%.1f".format(wkendPerDay / wkdayPerDay)
            pool.add(MonthlyInsight("🎉", "${ratio}× more on weekends", "₹${fmt(wkendPerDay)}/day on weekends vs ₹${fmt(wkdayPerDay)}/day on weekdays"))
        }

        // Top category dominance (> 38%)
        val parentCats = categories.filter { it.parentId == null }
        val topCat = parentCats.map { parent ->
            val childIds = categories.filter { it.parentId == parent.id }.map { it.id }.toSet()
            val catTotal = monthExpenses.filter { it.categoryId == parent.id || it.categoryId in childIds }.sumOf { it.amount }
            parent to catTotal
        }.filter { it.second > 0 }.maxByOrNull { it.second }
        if (topCat != null && (topCat.second / total * 100) > 38) {
            val pct = (topCat.second / total * 100).roundToInt()
            pool.add(MonthlyInsight(topCat.first.emoji, "${topCat.first.name} takes the most", "$pct% of this month — ₹${fmt(topCat.second)}"))
        }

        // Biggest expense (fallback)
        val biggest = monthExpenses.maxByOrNull { it.amount }
        if (biggest != null) pool.add(MonthlyInsight("💸", "Biggest expense", "${biggest.description} · ₹${fmt(biggest.amount)}"))

        return pool.firstOrNull()
    }

    companion object {
        fun factory(expenseRepo: ExpenseRepository, categoryRepo: CategoryRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    StatisticsViewModel(expenseRepo, categoryRepo) as T
            }
    }
}
