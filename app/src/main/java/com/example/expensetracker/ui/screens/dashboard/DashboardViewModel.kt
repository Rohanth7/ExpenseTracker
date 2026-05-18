package com.example.expensetracker.ui.screens.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.db.entity.Bill
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.db.entity.Loan
import com.example.expensetracker.data.db.entity.SavingsGoal
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.data.repository.BillRepository
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.LoanRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.data.repository.SavingsGoalRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

data class CategorySummary(
    val category: Category,
    val spent: Double,
    val percentage: Float
)

data class MonthlyTrend(val label: String, val total: Double)

data class SmartInsight(val emoji: String, val label: String, val detail: String)

enum class NotifType { CAPTURE, BUDGET, BILL }
data class NotifItem(
    val type: NotifType,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val isUrgent: Boolean = false
)

data class UpcomingBill(val bill: Bill, val daysUntilDue: Int)

data class LoanStatus(
    val loan: Loan,
    val paidMonths: Int,
    val remainingMonths: Int,
    val progressFraction: Float
)

data class DashboardUiState(
    val totalSpent: Double = 0.0,
    val monthlyIncome: Double = 0.0,
    val overBudget: Boolean = false,
    val categorySummaries: List<CategorySummary> = emptyList(),
    val pendingCount: Int = 0,
    val monthLabel: String = "",
    val canGoForward: Boolean = false,
    val monthlyTrends: List<MonthlyTrend> = emptyList(),
    val prevMonthDailyPace: Double = 0.0,
    val savingsGoals: List<SavingsGoal> = emptyList(),
    val safeToSpendToday: Double = 0.0,
    val monthlySurplus: Double = 0.0,
    val upcomingBills: List<UpcomingBill> = emptyList(),
    val activeLoans: List<LoanStatus> = emptyList(),
    val totalMonthlyEmi: Double = 0.0,
    val smartInsight: SmartInsight? = null,
    val notifItems: List<NotifItem> = emptyList()
)

class DashboardViewModel(
    private val context: Context,
    private val categoryRepo: CategoryRepository,
    private val expenseRepo: ExpenseRepository,
    private val savingsRepo: SavingsGoalRepository,
    private val billRepo: BillRepository,
    private val loanRepo: LoanRepository,
    private val prefs: PreferencesManager
) : ViewModel() {

    private val _income = MutableStateFlow(prefs.monthlyIncome)
    private val _monthOffset = MutableStateFlow(0)

    val uiState: StateFlow<DashboardUiState> = combine(
        combine(categoryRepo.allCategories, expenseRepo.allExpenses, _income, _monthOffset) { cats, exps, inc, off ->
            Quad(cats, exps, inc, off)
        },
        combine(savingsRepo.allGoals, billRepo.allBills, loanRepo.allLoans) { goals, bills, loans -> Triple(goals, bills, loans) }
    ) { quad, (goals, bills, loans) ->
        val (categories, expenses, income, offset) = quad
        val (start, end) = monthRangeForOffset(offset)
        val allMonthExpenses = expenses.filter { it.date in start..end }
        val categorizedExpenses = allMonthExpenses.filter { it.categoryId != -1L }
        val pendingTotal = allMonthExpenses.filter { it.categoryId == -1L }.sumOf { it.amount }
        val categorizedTotal = categorizedExpenses.sumOf { it.amount }
        val total = categorizedTotal + pendingTotal

        val parentCategories = categories.filter { it.parentId == null }
        val summaries = parentCategories.map { parent ->
            val childIds = categories.filter { it.parentId == parent.id }.map { it.id }.toSet()
            val spent = categorizedExpenses.filter { it.categoryId == parent.id || it.categoryId in childIds }.sumOf { it.amount }
            CategorySummary(parent, spent, if (categorizedTotal > 0) (spent / categorizedTotal * 100).toFloat() else 0f)
        }.filter { it.spent > 0.0 }.sortedByDescending { it.spent }

        val trends = (5 downTo 0).map { i ->
            val (s, e) = monthRangeForOffset(-i)
            MonthlyTrend(
                label = monthLabelShort(-i),
                total = expenses.filter { it.date in s..e }.sumOf { it.amount }
            )
        }

        val (prevStart, prevEnd) = monthRangeForOffset(offset - 1)
        val prevTotal = expenses.filter { it.date in prevStart..prevEnd }.sumOf { it.amount }
        val prevCal = Calendar.getInstance().also { it.add(Calendar.MONTH, offset - 1) }
        val prevDays = prevCal.getActualMaximum(Calendar.DAY_OF_MONTH).toDouble()

        // safeToSpendToday and surplus — only meaningful for current month
        val surplus = (income - total).coerceAtLeast(0.0)
        val daysLeft = run {
            if (offset != 0) return@run 0
            val cal = Calendar.getInstance()
            val totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            (totalDays - cal.get(Calendar.DAY_OF_MONTH) + 1).coerceAtLeast(1)
        }
        val safeToSpendToday = if (offset == 0 && surplus > 0) surplus / daysLeft else 0.0

        val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        val daysInMonth = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH)
        val upcomingBills = if (offset == 0) {
            bills.filter { it.isEnabled && !it.autoLog }.mapNotNull { bill ->
                val diff = bill.dueDayOfMonth - today
                val daysLeft = if (diff < -3) diff + daysInMonth else diff
                if (daysLeft in -1..6) UpcomingBill(bill, daysLeft) else null
            }.sortedBy { it.daysUntilDue }
        } else emptyList()

        val now = Calendar.getInstance()
        val activeLoans = loans.filter { it.isActive }.mapNotNull { loan ->
            val startCal = Calendar.getInstance().apply { timeInMillis = loan.startDate }
            var monthsElapsed = (now.get(Calendar.YEAR) - startCal.get(Calendar.YEAR)) * 12 +
                (now.get(Calendar.MONTH) - startCal.get(Calendar.MONTH))
            // Don't count current month if EMI due day hasn't passed yet
            if (now.get(Calendar.DAY_OF_MONTH) < loan.dueDayOfMonth) monthsElapsed--
            val paid = monthsElapsed.coerceIn(0, loan.tenureMonths)
            val remaining = loan.tenureMonths - paid
            if (remaining <= 0) return@mapNotNull null
            LoanStatus(
                loan = loan,
                paidMonths = paid,
                remainingMonths = remaining,
                progressFraction = (paid.toFloat() / loan.tenureMonths).coerceIn(0f, 1f)
            )
        }.sortedBy { it.loan.name }
        val totalMonthlyEmi = activeLoans.sumOf { it.loan.monthlyEmi }

        val dayOfMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        val insightPool = if (offset != 0 || total == 0.0 || dayOfMonth < 3) emptyList()
        else buildInsights(allMonthExpenses, summaries, total, prevTotal, prevDays, dayOfMonth)
        val smartInsight = if (insightPool.isEmpty()) null else insightPool[dayOfMonth % insightPool.size]

        // Notification centre items — always from full expense list; budget alerts only for current month
        val notifItems = buildNotifItems(expenses, if (offset == 0) summaries else emptyList(), categories, upcomingBills)

        DashboardUiState(
            totalSpent = total,
            monthlyIncome = income,
            overBudget = income > 0 && total > income,
            categorySummaries = summaries,
            pendingCount = allMonthExpenses.count { it.categoryId == -1L },
            monthLabel = monthLabelFull(offset),
            canGoForward = offset < 0,
            monthlyTrends = trends,
            prevMonthDailyPace = if (prevDays > 0) prevTotal / prevDays else 0.0,
            savingsGoals = goals,
            safeToSpendToday = safeToSpendToday,
            monthlySurplus = surplus,
            upcomingBills = upcomingBills,
            activeLoans = activeLoans,
            totalMonthlyEmi = totalMonthlyEmi,
            smartInsight = smartInsight,
            notifItems = notifItems
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    fun setIncome(value: Double) {
        prefs.monthlyIncome = value
        _income.value = value
    }

    fun goToPreviousMonth() { _monthOffset.value-- }
    fun goToNextMonth() { if (_monthOffset.value < 0) _monthOffset.value++ }

    fun addSavingsGoal(name: String, target: Double) = viewModelScope.launch {
        savingsRepo.insert(SavingsGoal(name = name, targetAmount = target, colorHex = "#7DC9A5"))
    }

    fun deleteSavingsGoal(goal: SavingsGoal) = viewModelScope.launch {
        savingsRepo.delete(goal)
    }

    fun addAmountToGoal(goal: SavingsGoal, amount: Double) = viewModelScope.launch {
        if (amount <= 0) return@launch
        savingsRepo.update(goal.copy(currentAmount = (goal.currentAmount + amount).coerceAtMost(goal.targetAmount)))
    }

    private fun buildInsights(
        allMonthExpenses: List<Expense>,
        summaries: List<CategorySummary>,
        total: Double,
        prevTotal: Double,
        prevDays: Double,
        dayOfMonth: Int
    ): List<SmartInsight> {
        val result = mutableListOf<SmartInsight>()

        // vs last month pace (pro-rated to same day)
        val prevPaced = if (prevDays > 0) prevTotal / prevDays * dayOfMonth else 0.0
        if (prevPaced > 0) {
            val pct = ((total - prevPaced) / prevPaced * 100).roundToInt()
            when {
                pct > 20 -> result.add(SmartInsight("📈", "${pct}% up on last month",
                    "₹${fmtVM(total - prevPaced)} more than at this point last month"))
                pct < -15 -> result.add(SmartInsight("📉", "${-pct}% under last month's pace",
                    "₹${fmtVM(prevPaced - total)} less than at this point last month — great job!"))
            }
        }

        // Most frequent merchant (same exact description, ≥ 3 times)
        val topMerchant = allMonthExpenses
            .groupBy { it.description.trim().lowercase() }
            .entries.filter { it.value.size >= 3 }
            .maxByOrNull { it.value.size }
        if (topMerchant != null) {
            val displayName = allMonthExpenses.first { it.description.trim().lowercase() == topMerchant.key }.description
            val merchantTotal = topMerchant.value.sumOf { it.amount }
            result.add(SmartInsight("🛒", "$displayName · ${topMerchant.value.size}×",
                "₹${fmtVM(merchantTotal)} across ${topMerchant.value.size} transactions this month"))
        }

        // No-spend days (days with zero transactions)
        val daysWithSpend = allMonthExpenses.map { exp ->
            Calendar.getInstance().apply { timeInMillis = exp.date }.get(Calendar.DAY_OF_MONTH)
        }.toSet()
        val noSpendDays = (1..dayOfMonth).count { it !in daysWithSpend }
        if (noSpendDays >= 3) {
            result.add(SmartInsight("✨", "$noSpendDays no-spend days so far",
                "You went without any spending on $noSpendDays day${if (noSpendDays != 1) "s" else ""} this month"))
        }

        // Weekend vs weekday daily spend
        var weekendTotal = 0.0; var weekdayTotal = 0.0
        allMonthExpenses.forEach { exp ->
            val dow = Calendar.getInstance().apply { timeInMillis = exp.date }.get(Calendar.DAY_OF_WEEK)
            if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) weekendTotal += exp.amount
            else weekdayTotal += exp.amount
        }
        val cal = Calendar.getInstance()
        val yr = cal.get(Calendar.YEAR); val mo = cal.get(Calendar.MONTH)
        var wkendDays = 0; var wkdayDays = 0
        for (d in 1..dayOfMonth) {
            val dow = Calendar.getInstance().apply { set(yr, mo, d) }.get(Calendar.DAY_OF_WEEK)
            if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) wkendDays++ else wkdayDays++
        }
        val wkendPerDay = if (wkendDays > 0) weekendTotal / wkendDays else 0.0
        val wkdayPerDay = if (wkdayDays > 0) weekdayTotal / wkdayDays else 0.0
        if (wkdayPerDay > 100 && wkendPerDay > 0 && wkendPerDay / wkdayPerDay >= 1.6) {
            val ratio = "%.1f".format(wkendPerDay / wkdayPerDay)
            result.add(SmartInsight("🎉", "${ratio}× more on weekends",
                "₹${fmtVM(wkendPerDay)}/day on weekends vs ₹${fmtVM(wkdayPerDay)}/day on weekdays"))
        }

        // Top category dominance (> 38% of total)
        val top = summaries.firstOrNull()
        if (top != null && top.percentage > 38f) {
            result.add(SmartInsight(top.category.emoji, "${top.category.name} takes the most",
                "${top.percentage.roundToInt()}% of this month's spending — ₹${fmtVM(top.spent)}"))
        }

        // Biggest single expense (always present as fallback)
        val biggest = allMonthExpenses.maxByOrNull { it.amount }
        if (biggest != null) {
            result.add(SmartInsight("💸", "Biggest expense this month",
                "${biggest.description} · ₹${fmtVM(biggest.amount)}"))
        }

        return result
    }

    private fun buildNotifItems(
        expenses: List<Expense>,
        summaries: List<CategorySummary>,
        categories: List<Category>,
        upcomingBills: List<UpcomingBill>
    ): List<NotifItem> = buildList {
        // Recent auto-captured transactions (last 7, newest first)
        expenses
            .filter { it.source != "Manual" && it.source != "Recurring" }
            .sortedByDescending { it.date }
            .take(7)
            .forEach { exp ->
                val cat = categories.find { it.id == exp.categoryId }
                val isPending = exp.categoryId == -1L
                add(NotifItem(
                    type = NotifType.CAPTURE,
                    emoji = if (isPending) "🔔" else (cat?.emoji ?: "📱"),
                    title = exp.description,
                    subtitle = "₹${fmtVM(exp.amount)} · ${relativeTime(exp.date)}" +
                        if (isPending) " · needs category" else "",
                    isUrgent = isPending
                ))
            }

        // Budget alerts (categories at or above the alert threshold)
        val threshold = prefs.budgetAlertThreshold
        summaries.filter { it.category.monthlyLimit > 0 }.forEach { s ->
            val pct = (s.spent / s.category.monthlyLimit * 100).roundToInt()
            if (pct >= threshold) {
                val isOver = pct >= 100
                add(NotifItem(
                    type = NotifType.BUDGET,
                    emoji = s.category.emoji,
                    title = "${s.category.name} ${if (isOver) "over budget" else "near limit"}",
                    subtitle = "${pct}% of ₹${fmtVM(s.category.monthlyLimit)} limit used this month",
                    isUrgent = isOver
                ))
            }
        }

        // Upcoming bills (already filtered: enabled, not autoLog, within window)
        upcomingBills.forEach { upcoming ->
            val dueText = when (upcoming.daysUntilDue) {
                -1 -> "Overdue since yesterday"
                0  -> "Due today"
                1  -> "Due tomorrow"
                else -> "Due in ${upcoming.daysUntilDue} days"
            }
            add(NotifItem(
                type = NotifType.BILL,
                emoji = "📋",
                title = upcoming.bill.name,
                subtitle = "$dueText · ₹${fmtVM(upcoming.bill.amount)}",
                isUrgent = upcoming.daysUntilDue <= 0
            ))
        }
    }

    private fun relativeTime(ts: Long): String {
        val diff = System.currentTimeMillis() - ts
        val mins = diff / 60_000
        val hours = diff / 3_600_000
        val days = diff / 86_400_000
        return when {
            mins < 2    -> "Just now"
            mins < 60   -> "${mins}m ago"
            hours < 24  -> "${hours}h ago"
            days == 1L  -> "Yesterday"
            else        -> "${days}d ago"
        }
    }

    private fun fmtVM(n: Double): String =
        NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"))
            .apply { maximumFractionDigits = 0; minimumFractionDigits = 0 }
            .format(n.toLong())

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
    private operator fun <A, B, C, D> Quad<A, B, C, D>.component1() = first
    private operator fun <A, B, C, D> Quad<A, B, C, D>.component2() = second
    private operator fun <A, B, C, D> Quad<A, B, C, D>.component3() = third
    private operator fun <A, B, C, D> Quad<A, B, C, D>.component4() = fourth

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
        fun factory(
            context: Context,
            categoryRepo: CategoryRepository,
            expenseRepo: ExpenseRepository,
            savingsRepo: SavingsGoalRepository,
            billRepo: BillRepository,
            loanRepo: LoanRepository,
            prefs: PreferencesManager
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DashboardViewModel(context, categoryRepo, expenseRepo, savingsRepo, billRepo, loanRepo, prefs) as T
        }
    }
}
