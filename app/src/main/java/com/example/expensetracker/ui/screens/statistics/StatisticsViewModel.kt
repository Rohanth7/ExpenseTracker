package com.example.expensetracker.ui.screens.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.period.BudgetPeriod
import com.example.expensetracker.data.period.DateRange
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.*
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

enum class ViewMode { MONTHLY, ANNUAL, RANGE }

/** What the Range view covers. Months are budget months (see BudgetPeriod). */
sealed class StatsRange {
    data class LastMonths(val n: Int) : StatsRange()
    object AllTime : StatsRange()
    data class Custom(val start: Long, val end: Long) : StatsRange()
}

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
    val autoTrackStats: AutoTrackStats = AutoTrackStats(),
    val month: PeriodView = PeriodView(),
    val canGoForwardMonth: Boolean = false,
    /** Only worked out while the Range view is shown. */
    val range: PeriodView = PeriodView(),
    /** Part of the state so the view and its numbers change together. */
    val viewMode: ViewMode = ViewMode.MONTHLY
)

/** One stretch of days as the Monthly and Range views show it. */
data class PeriodView(
    val label: String = "",
    val start: Long = 0L,
    val end: Long = 0L,
    val stats: PeriodStats = PeriodStats(0.0, emptyList(), null, PaymentSplit(), DetectionSplit(), null),
    /** What the percentage compares with ("last month"), or null when there's nothing before it. */
    val comparedWith: String? = null
)

/** What the monthly view (and a chosen range) shows for one stretch of days. */
data class PeriodStats(
    val total: Double,
    val categories: List<CategoryYearlyStat>,
    val vsPreviousPct: Int?,
    val paymentSplit: PaymentSplit,
    val detectionSplit: DetectionSplit,
    val insight: MonthlyInsight?
)

/**
 * [previous]'s total to compare [range]'s with: while [range] is still running, scaled to the
 * days of it gone so far; once it's over, the whole total.
 */
internal fun comparableTotal(prevTotal: Double, previous: DateRange, range: DateRange, now: Long): Double {
    if (now !in range) return prevTotal
    return prevTotal / BudgetPeriod.daysInCycle(previous) * BudgetPeriod.daysInCycle(DateRange(range.start, now))
}

private data class PeriodInputs(val monthOffset: Int, val startDay: Int, val range: StatsRange, val mode: ViewMode)

class StatisticsViewModel(
    private val expenseRepo: ExpenseRepository,
    private val categoryRepo: CategoryRepository,
    /** Day the budget month starts (see `BudgetPeriod`); the monthly view follows it. */
    monthStartDay: Flow<Int>
) : ViewModel() {

    private val MONTHS = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    private val _yearOffset = MutableStateFlow(0)
    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    private val _monthOffset = MutableStateFlow(0)
    private val _viewMode = MutableStateFlow(ViewMode.MONTHLY)
    private val _range = MutableStateFlow<StatsRange>(StatsRange.LastMonths(3))

    val selectedRange: StateFlow<StatsRange> = _range.asStateFlow()
    fun setViewMode(mode: ViewMode) { _viewMode.value = mode }
    fun setRange(range: StatsRange) { _range.value = range; _viewMode.value = ViewMode.RANGE }
    fun goToPreviousMonthStats() { _monthOffset.value-- }
    fun goToNextMonthStats() { if (_monthOffset.value < 0) _monthOffset.value++ }

    val uiState: StateFlow<StatisticsUiState> = combine(
        expenseRepo.allExpenses,
        categoryRepo.allCategories,
        _yearOffset,
        _selectedCategoryId,
        combine(_monthOffset, monthStartDay, _range, _viewMode) { o, d, r, m -> PeriodInputs(o, d, r, m) }
    ) { expenses, categories, offset, selectedId, (monthOffset, startDay, statsRange, mode) ->
        val nowMs = System.currentTimeMillis()
        val year = Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.YEAR) + offset

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

        // Category trend for the last 6 budget months
        val selectedCategory = categories.find { it.id == selectedId }
        val trend = selectedCategory?.let { cat ->
            val childIds = categories.filter { it.parentId == cat.id }.map { it.id }.toSet()
            (5 downTo 0).map { i ->
                val r = BudgetPeriod.forOffset(nowMs, startDay, -i)
                CategoryTrend(BudgetPeriod.shortLabel(r), expenses.filter { (it.categoryId == cat.id || it.categoryId in childIds) && it.date in r }.sumOf { it.amount })
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

        val todayStartMs = BudgetPeriod.startOfDay(nowMs)
        val thisMonthStartMs = BudgetPeriod.current(nowMs, startDay).start
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

        // ── Monthly stats: one budget month, from the user's start day ──
        val mRange = BudgetPeriod.forOffset(nowMs, startDay, monthOffset)
        val month = periodStats(expenses, categories, mRange, BudgetPeriod.forOffset(nowMs, startDay, monthOffset - 1), nowMs, "month", "last month")
        val range = if (mode == ViewMode.RANGE) rangeView(statsRange, expenses, categories, nowMs, startDay) else PeriodView()

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
            autoTrackStats = autoStats,
            // While the month is still running its percentage is at today's pace, like the insight
            month = PeriodView(BudgetPeriod.label(mRange, startDay), mRange.start, mRange.endInclusive, month,
                if (nowMs in mRange) "this point last month" else "last month"),
            canGoForwardMonth = monthOffset < 0,
            range = range,
            viewMode = mode
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatisticsUiState())

    fun goToPreviousYear() { _yearOffset.value-- }
    fun goToNextYear() { if (_yearOffset.value < 0) _yearOffset.value++ }

    fun selectCategory(id: Long?) { _selectedCategoryId.value = id }

    private fun isAutoSource(s: String) = s != "Manual" && s != "Recurring"

    private fun rangeView(r: StatsRange, expenses: List<Expense>, categories: List<Category>, now: Long, startDay: Int): PeriodView {
        val (range, label, comparedWith) = when (r) {
            is StatsRange.LastMonths -> Triple(BudgetPeriod.lastNMonths(now, startDay, r.n), "Last ${r.n} months", "the previous ${r.n} months")
            StatsRange.AllTime -> Triple(
                BudgetPeriod.dayRange(expenses.minOfOrNull { it.date } ?: now, maxOf(now, expenses.maxOfOrNull { it.date } ?: now)),
                "All time", null)
            is StatsRange.Custom -> {
                val dr = DateRange(r.start, r.end)
                val days = BudgetPeriod.daysInCycle(dr)
                Triple(dr, BudgetPeriod.dayRangeLabel(dr.start, dr.endInclusive, now), if (days == 1) "the day before" else "the $days days before")
            }
        }
        val previous = if (comparedWith == null) null else BudgetPeriod.previousOfSameLength(range)
        // "Last 3 months" includes this month's days still to come, so it's compared at today's pace.
        val stats = periodStats(expenses, categories, range, previous, now, "period", comparedWith)
        return PeriodView(label, range.start, range.endInclusive, stats, comparedWith)
    }

    /**
     * Totals, categories, splits and an insight for [range], compared with [previous] (null = no
     * comparison), which [vs] names ("last month"). [word] names the period in the insight text
     * ("month", or "period" for a range). While [range] is still running, both the percentage and
     * the insight compare at today's pace (see [comparableTotal]).
     */
    private fun periodStats(
        expenses: List<Expense>, categories: List<Category>, range: DateRange, previous: DateRange?, now: Long,
        word: String, vs: String?
    ): PeriodStats {
        val periodExpenses = expenses.filter { it.date in range }
        val total = periodExpenses.sumOf { it.amount }
        val prevTotal = previous?.let { p -> expenses.filter { it.date in p }.sumOf { it.amount } } ?: 0.0
        val comparable = previous?.let { comparableTotal(prevTotal, it, range, now) } ?: 0.0
        val parentCategories = categories.filter { it.parentId == null }
        val cats = parentCategories.map { parent ->
            val children = categories.filter { it.parentId == parent.id }
            val childIds = children.map { it.id }.toSet()
            val catTotal = periodExpenses.filter { it.categoryId == parent.id || it.categoryId in childIds }.sumOf { it.amount }
            val subcats = children.map { child ->
                SubcategoryRow(child, periodExpenses.filter { it.categoryId == child.id }.sumOf { it.amount })
            }.filter { it.amount > 0 }.sortedByDescending { it.amount }
            CategoryYearlyStat(parent, catTotal, subcats)
        }.filter { it.total > 0 }.sortedByDescending { it.total }
        return PeriodStats(
            total = total,
            categories = cats,
            vsPreviousPct = if (comparable > 0) ((total - comparable) / comparable * 100).roundToInt() else null,
            paymentSplit = PaymentSplit(
                upiAmount = periodExpenses.filter { it.paymentMethod == "UPI" }.sumOf { it.amount },
                creditCardAmount = periodExpenses.filter { it.paymentMethod == "Credit Card" }.sumOf { it.amount },
                cashAmount = periodExpenses.filter { it.paymentMethod == "Cash" }.sumOf { it.amount }
            ),
            detectionSplit = DetectionSplit(
                autoAmount = periodExpenses.filter { isAutoSource(it.source) }.sumOf { it.amount },
                autoCount = periodExpenses.count { isAutoSource(it.source) },
                manualAmount = periodExpenses.filter { it.source == "Manual" }.sumOf { it.amount },
                manualCount = periodExpenses.count { it.source == "Manual" },
                recurringAmount = periodExpenses.filter { it.source == "Recurring" }.sumOf { it.amount },
                recurringCount = periodExpenses.count { it.source == "Recurring" }
            ),
            insight = buildPeriodInsight(periodExpenses, comparable, vs, categories, range, now, word)
        )
    }

    private fun buildPeriodInsight(
        periodExpenses: List<Expense>,
        comparable: Double,
        vs: String?,
        categories: List<Category>,
        range: DateRange,
        now: Long,
        word: String
    ): MonthlyInsight? {
        if (periodExpenses.isEmpty()) return null
        val total = periodExpenses.sumOf { it.amount }
        val numFmt = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply { maximumFractionDigits = 0 }
        fun fmt(v: Double) = numFmt.format(v)

        val isCurrent = now in range
        // Days so far (the period may start mid-month): through today if it's still running.
        val effectiveDays = if (isCurrent) BudgetPeriod.daysInCycle(DateRange(range.start, now)) else BudgetPeriod.daysInCycle(range)
        val days = BudgetPeriod.days(range.start, effectiveDays)

        val pool = mutableListOf<MonthlyInsight>()

        // vs the previous period, paced to the same day while this one is still running
        if (vs != null && comparable > 0) {
            val pct = ((total - comparable) / comparable * 100).roundToInt()
            val diff = "₹${fmt(kotlin.math.abs(total - comparable))}"
            val atPoint = if (isCurrent) " at this point" else ""
            when {
                pct > 20 -> pool.add(MonthlyInsight("📈", "${pct}% up on $vs", "$diff more than $vs$atPoint"))
                pct < -15 -> pool.add(MonthlyInsight("📉", "${-pct}% under $vs", "$diff less than $vs$atPoint — great job!"))
            }
        }

        // Most-visited merchant (≥ 2 times)
        val topMerchant = periodExpenses.groupBy { it.description.trim().lowercase() }
            .entries.filter { it.value.size >= 2 }.maxByOrNull { it.value.size }
        if (topMerchant != null) {
            val name = periodExpenses.first { it.description.trim().lowercase() == topMerchant.key }.description
            pool.add(MonthlyInsight("🛒", "$name · ${topMerchant.value.size}×", "₹${fmt(topMerchant.value.sumOf { it.amount })} across ${topMerchant.value.size} transactions"))
        }

        // No-spend days
        val daysWithSpend = periodExpenses.map { BudgetPeriod.dayKey(it.date) }.toSet()
        val noSpendDays = days.count { BudgetPeriod.dayKey(it.timeInMillis) !in daysWithSpend }
        if (noSpendDays >= 3) pool.add(MonthlyInsight("✨", "$noSpendDays no-spend days", "You went $noSpendDays day${if (noSpendDays != 1) "s" else ""} without spending this $word"))

        // Weekend vs weekday daily spend
        var wkendTotal = 0.0; var wkdayTotal = 0.0
        periodExpenses.forEach { exp ->
            val dow = Calendar.getInstance().apply { timeInMillis = exp.date }.get(Calendar.DAY_OF_WEEK)
            if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) wkendTotal += exp.amount else wkdayTotal += exp.amount
        }
        var wkendDays = 0; var wkdayDays = 0
        days.forEach { d ->
            val dow = d.get(Calendar.DAY_OF_WEEK)
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
            val catTotal = periodExpenses.filter { it.categoryId == parent.id || it.categoryId in childIds }.sumOf { it.amount }
            parent to catTotal
        }.filter { it.second > 0 }.maxByOrNull { it.second }
        if (topCat != null && (topCat.second / total * 100) > 38) {
            val pct = (topCat.second / total * 100).roundToInt()
            pool.add(MonthlyInsight(topCat.first.emoji, "${topCat.first.name} takes the most", "$pct% of this $word — ₹${fmt(topCat.second)}"))
        }

        // Biggest expense (fallback)
        val biggest = periodExpenses.maxByOrNull { it.amount }
        if (biggest != null) pool.add(MonthlyInsight("💸", "Biggest expense", "${biggest.description} · ₹${fmt(biggest.amount)}"))

        return pool.firstOrNull()
    }

    companion object {
        fun factory(expenseRepo: ExpenseRepository, categoryRepo: CategoryRepository, monthStartDay: Flow<Int>) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    StatisticsViewModel(expenseRepo, categoryRepo, monthStartDay) as T
            }
    }
}
