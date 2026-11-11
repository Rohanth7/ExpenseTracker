package com.example.expensetracker.ui.screens.dashboard

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.db.entity.Loan
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.data.repository.BillRepository
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.data.repository.IncomeRepository
import com.example.expensetracker.data.repository.LoanRepository
import com.example.expensetracker.data.repository.SavingsGoalRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/** The Overview's loans and EMI forecast against a throwaway in-memory database and settings file. */
@RunWith(AndroidJUnit4::class)
class DashboardLoansTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var prefs: PreferencesManager

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        context.deleteSharedPreferences("dashboard_loans_test")
        prefs = PreferencesManager(context, "dashboard_loans_test")
        prefs.monthlyIncome = 50000.0
    }
    @After fun tearDown() { db.close() }

    private fun state() = runBlocking {
        val vm = DashboardViewModel(context, CategoryRepository(db.categoryDao(), db.expenseDao()), ExpenseRepository(db.expenseDao(), context),
            SavingsGoalRepository(db.savingsGoalDao()), BillRepository(db.billDao()), LoanRepository(db.loanDao()),
            IncomeRepository(db.incomeDao(), db.recurringIncomeDao(), context), prefs)
        withTimeout(15_000) { vm.uiState.first { it.monthLabel.isNotEmpty() && it.hasCommitments } }
    }

    private fun monthsAgo(n: Int) = Calendar.getInstance().apply {
        add(Calendar.MONTH, -n); set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** BUG-08: 12 EMIs from 12 months ago were all due by last month, but a 13th showed as still due. */
    @Test fun aFinishedLoanIsNotActiveAndHasNothingDue() = runBlocking {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        // Needs a due day later this month that every month has
        assumeTrue(today <= 27)
        val dueLaterThisMonth = today + 1
        db.loanDao().insert(Loan(name = "Phone EMI", totalAmount = 60000.0, monthlyEmi = 5000.0, tenureMonths = 12, startDate = monthsAgo(12), dueDayOfMonth = dueLaterThisMonth))
        db.loanDao().insert(Loan(name = "Car", totalAmount = 240000.0, monthlyEmi = 10000.0, tenureMonths = 24, startDate = monthsAgo(2), dueDayOfMonth = dueLaterThisMonth))
        val s = state()
        assertEquals(listOf("Car"), s.activeLoans.map { it.loan.name })
        // Car: the EMIs 2 and 1 months ago are paid, this month's is still due
        assertEquals(2, s.activeLoans.single().paidMonths)
        assertEquals(10000.0, s.emiStillDue, 0.01)
    }

    /** B-17: left open across a month's end, the Overview stayed on the old month until data changed. */
    @Test fun theOverviewMovesToTheNewMonthWhenItComesBack() = runBlocking {
        fun at(month: Int, day: Int, hour: Int) = Calendar.getInstance().apply { clear(); set(2026, month - 1, day, hour, 0) }.timeInMillis
        var now = at(11, 30, 23)
        db.expenseDao().insert(Expense(amount = 400.0, description = "November dinner", categoryId = -1, date = at(11, 30, 20)))
        val vm = DashboardViewModel(context, CategoryRepository(db.categoryDao(), db.expenseDao()), ExpenseRepository(db.expenseDao(), context),
            SavingsGoalRepository(db.savingsGoalDao()), BillRepository(db.billDao()), LoanRepository(db.loanDao()),
            IncomeRepository(db.incomeDao(), db.recurringIncomeDao(), context), prefs, clock = { now })
        val november = withTimeout(15_000) { vm.uiState.first { it.totalSpent == 400.0 } }

        now = at(12, 1, 0) + 60_000
        vm.refresh()

        val december = withTimeout(15_000) { vm.uiState.first { it.monthLabel != november.monthLabel } }
        assertEquals(0.0, december.totalSpent, 0.0)
        assertEquals(0, december.pendingCount)
    }
}
