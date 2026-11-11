package com.example.expensetracker.ui.screens.categorize

import android.content.Context
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.data.repository.LoanRepository
import com.example.expensetracker.data.repository.MerchantMappingRepository
import com.example.expensetracker.data.repository.SavingsGoalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** B-03: Categorize's "Dismiss" deleted the expense at once, with no question and no way back. */
@RunWith(AndroidJUnit4::class)
class CategorizeDismissTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var vm: CategorizeViewModel
    private var done = 0

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        context.deleteSharedPreferences("categorize_dismiss_test")
        val id = runBlocking { db.expenseDao().insert(Expense(amount = 250.0, description = "Swiggy", source = "SMS")) }
        vm = runBlocking(Dispatchers.Main) {
            CategorizeViewModel(ExpenseRepository(db.expenseDao(), context), CategoryRepository(db.categoryDao(), db.expenseDao()),
                MerchantMappingRepository(db.merchantMappingDao()), SavingsGoalRepository(db.savingsGoalDao()), LoanRepository(db.loanDao()),
                context, PreferencesManager(context, "categorize_dismiss_test"))
        }
        compose.setContent { CategorizeScreen(id, vm, onDone = { done++ }, onBack = {}) }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Dismiss")).fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun tearDown() { vm.viewModelScope.cancel(); db.close() }

    private fun count() = runBlocking { db.expenseDao().count() }
    private fun dialogButton(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    @Test fun dismissAsksBeforeDeleting() {
        compose.onNodeWithText("Dismiss").performClick()
        compose.onNodeWithText("Delete this expense?").assertExists()
        assertEquals(1, count())

        dialogButton("Cancel").performClick()
        compose.onNodeWithText("Delete this expense?").assertDoesNotExist()
        assertEquals(1, count())
        assertEquals(0, done)

        compose.onNodeWithText("Dismiss").performClick()
        dialogButton("Delete").performClick()
        compose.waitUntil(5_000) { count() == 0 && done == 1 }
    }
}
