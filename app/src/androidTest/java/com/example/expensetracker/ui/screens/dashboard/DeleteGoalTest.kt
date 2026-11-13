package com.example.expensetracker.ui.screens.dashboard

import android.content.Context
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.SavingsGoal
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.data.repository.BillRepository
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.data.repository.IncomeRepository
import com.example.expensetracker.data.repository.LoanRepository
import com.example.expensetracker.data.repository.SavingsGoalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** B-04: the Overview's goal delete icon removed the goal and what was saved in it, with no question. */
@RunWith(AndroidJUnit4::class)
class DeleteGoalTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var vm: DashboardViewModel

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        context.deleteSharedPreferences("delete_goal_test")
        runBlocking { db.savingsGoalDao().insert(SavingsGoal(name = "Trip", targetAmount = 50000.0, currentAmount = 1200.0)) }
        vm = runBlocking(Dispatchers.Main) {
            DashboardViewModel(context, CategoryRepository(db.categoryDao(), db.expenseDao()), ExpenseRepository(db.expenseDao(), context),
                SavingsGoalRepository(db.savingsGoalDao()), BillRepository(db.billDao()), LoanRepository(db.loanDao()),
                IncomeRepository(db.incomeDao(), db.recurringIncomeDao(), context), PreferencesManager(context, "delete_goal_test"))
        }
        compose.setContent { DashboardScreen(vm, onViewPending = {}) }
        compose.waitUntil(15_000) { vm.uiState.value.savingsGoals.isNotEmpty() }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription("Delete goal Trip"))
    }
    @After fun tearDown() { vm.viewModelScope.cancel(); db.close() }

    private fun goals() = runBlocking { db.savingsGoalDao().getAll().first().map { it.name } }
    private fun dialogButton(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    @Test fun deletingAGoalAsksFirstAndSaysWhatIsSaved() {
        compose.onNodeWithContentDescription("Delete goal Trip").performClick()
        compose.onNodeWithText("Delete goal?").assertExists()
        compose.onNode(hasText("1,200", substring = true) and hasAnyAncestor(isDialog())).assertExists()
        assertEquals(listOf("Trip"), goals())

        dialogButton("Cancel").performClick()
        compose.onNodeWithText("Delete goal?").assertDoesNotExist()
        assertEquals(listOf("Trip"), goals())

        compose.onNodeWithContentDescription("Delete goal Trip").performClick()
        dialogButton("Delete").performClick()
        compose.waitUntil(5_000) { goals().isEmpty() }
    }
}
