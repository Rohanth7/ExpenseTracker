package com.example.expensetracker.ui.screens.dashboard

import android.content.Context
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
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
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A savings goal's trash icon deleted the goal and what was saved toward it at once, with no question. */
@RunWith(AndroidJUnit4::class)
class GoalDeleteTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var vm: DashboardViewModel

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        context.deleteSharedPreferences("goal_delete_test")
        runBlocking { db.savingsGoalDao().insert(SavingsGoal(name = "Goa trip", targetAmount = 20000.0, currentAmount = 4500.0)) }
        vm = runBlocking(Dispatchers.Main) {
            DashboardViewModel(context, CategoryRepository(db.categoryDao(), db.expenseDao()), ExpenseRepository(db.expenseDao(), context),
                SavingsGoalRepository(db.savingsGoalDao()), BillRepository(db.billDao()), LoanRepository(db.loanDao()),
                IncomeRepository(db.incomeDao(), db.recurringIncomeDao(), context), PreferencesManager(context, "goal_delete_test"))
        }
        compose.setContent { DashboardScreen(vm, onViewPending = {}, onViewAllSpending = {}) }
        runBlocking { withTimeout(15_000) { vm.uiState.first { it.savingsGoals.isNotEmpty() } } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Delete goal"))
    }
    // The screen's flows query the database until the ViewModel stops
    @After fun tearDown() { vm.viewModelScope.cancel(); db.close() }

    private fun goals() = runBlocking { db.savingsGoalDao().getAll().first().map { it.name } }
    private fun dialogButton(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    @Test fun deletingAGoalAsksFirst() {
        compose.onNodeWithContentDescription("Delete goal").performClick()
        compose.onNodeWithText("Delete goal?").assertExists()
        compose.onNodeWithText("\"Goa trip\" and the ₹4,500 saved toward it will be removed.").assertExists()
        assertEquals(listOf("Goa trip"), goals())

        dialogButton("Cancel").performClick()
        compose.onNodeWithText("Delete goal?").assertDoesNotExist()
        assertEquals(listOf("Goa trip"), goals())

        compose.onNodeWithContentDescription("Delete goal").performClick()
        dialogButton("Delete").performClick()
        compose.waitUntil(5_000) { goals().isEmpty() }
    }
}
