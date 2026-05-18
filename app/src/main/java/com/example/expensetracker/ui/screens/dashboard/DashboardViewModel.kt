package com.example.expensetracker.ui.screens.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.db.entity.Bill
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Loan
import com.example.expensetracker.data.db.entity.SavingsGoal
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
    val totalMonthlyEmi: Double = 0.0
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
            bills.filter { it.isEnabled }.mapNotNull { bill ->
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
            totalMonthlyEmi = totalMonthlyEmi
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
