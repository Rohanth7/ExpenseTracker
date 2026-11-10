package com.example.expensetracker.ui.screens.categorize

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.db.entity.Loan
import com.example.expensetracker.data.db.entity.SavingsGoal
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.data.repository.BillRepository
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.data.repository.IncomeRepository
import com.example.expensetracker.data.repository.LoanRepository
import com.example.expensetracker.data.repository.MerchantMappingRepository
import com.example.expensetracker.data.repository.SavingsGoalRepository
import com.example.expensetracker.ui.screens.dashboard.DashboardViewModel
import com.example.expensetracker.ui.screens.expenses.Tags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Categorize's "Loan EMI" and "Savings" against a throwaway in-memory database and settings file. */
@RunWith(AndroidJUnit4::class)
class CategorizeLinkTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var prefs: PreferencesManager

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        context.deleteSharedPreferences("categorize_link_test")
        prefs = PreferencesManager(context, "categorize_link_test")
    }
    @After fun tearDown() { db.close() }

    private val categories get() = CategoryRepository(db.categoryDao(), db.expenseDao())
    private val expenses get() = ExpenseRepository(db.expenseDao(), context)
    private val goals get() = SavingsGoalRepository(db.savingsGoalDao())

    private fun categorize() = CategorizeViewModel(expenses, categories, MerchantMappingRepository(db.merchantMappingDao()),
        goals, LoanRepository(db.loanDao()), context, prefs)

    private suspend fun link(vm: CategorizeViewModel, expenseId: Long, loanId: Long) = withContext(Dispatchers.Main) {
        vm.linkToLoanEmi(db.expenseDao().getById(expenseId)!!, db.loanDao().getAll().first().first { it.id == loanId }) {}
    }.join()

    /** BUG-10: "Link to Loan EMI" deleted the ₹35,000 payment and didn't record it against the loan. */
    @Test fun linkingToALoanKeepsThePaymentTaggedWithTheLoan() = runBlocking {
        val loanId = db.loanDao().insert(Loan(name = "Home Loan", totalAmount = 4e6, monthlyEmi = 35000.0, tenureMonths = 180, startDate = 1L))
        val id = db.expenseDao().insert(Expense(amount = 35000.0, description = "HDFC LTD EMI", source = "SMS", tags = "house"))
        val vm = categorize()

        link(vm, id, loanId)

        val linked = db.expenseDao().getById(id)
        assertNotNull("The EMI payment was deleted", linked)
        linked!!
        assertEquals(listOf("house", "emi", "Home Loan"), Tags.parse(linked.tags))
        // Filed under a category, so it leaves the "needs categorizing" list
        assertEquals("Loans & EMIs", db.categoryDao().getById(linked.categoryId)?.name)
        assertEquals(35000.0, linked.amount, 0.0)

        // The next EMI goes to the same category, not a second one
        val next = db.expenseDao().insert(Expense(amount = 35000.0, description = "HDFC LTD EMI", source = "SMS"))
        link(vm, next, loanId)
        assertEquals(1, db.categoryDao().getAllOnce().count { it.name == "Loans & EMIs" })
        assertEquals(linked.categoryId, db.expenseDao().getById(next)!!.categoryId)
    }

    /** BUG-11: ₹500 into a ₹1,000 goal at ₹900 gave ₹1,000 from the Overview and ₹1,400 from Categorize. */
    @Test fun depositsFromTheOverviewAndCategorizeAgree() = runBlocking {
        val viaOverview = db.savingsGoalDao().insert(SavingsGoal(name = "Via Overview", targetAmount = 1000.0, currentAmount = 900.0))
        val viaCategorize = db.savingsGoalDao().insert(SavingsGoal(name = "Via Categorize", targetAmount = 1000.0, currentAmount = 900.0))
        suspend fun goal(id: Long) = db.savingsGoalDao().getAll().first().first { it.id == id }

        val dashboard = DashboardViewModel(context, categories, expenses, goals, BillRepository(db.billDao()), LoanRepository(db.loanDao()),
            IncomeRepository(db.incomeDao(), db.recurringIncomeDao(), context), prefs)
        withContext(Dispatchers.Main) { dashboard.addAmountToGoal(goal(viaOverview), 500.0) }.join()
        val transfer = db.expenseDao().insert(Expense(amount = 500.0, description = "Transfer to RD", source = "SMS"))
        withContext(Dispatchers.Main) { categorize().linkToSavingsGoal(db.expenseDao().getById(transfer)!!, goal(viaCategorize), 500.0) {} }.join()

        assertEquals(1400.0, goal(viaOverview).currentAmount, 0.0)
        assertEquals(1400.0, goal(viaCategorize).currentAmount, 0.0)
    }
}
