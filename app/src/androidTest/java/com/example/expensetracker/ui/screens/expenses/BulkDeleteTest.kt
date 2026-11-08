package com.example.expensetracker.ui.screens.expenses

import android.content.Context
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.data.repository.IncomeRepository
import com.example.expensetracker.data.repository.MerchantMappingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** BUG-24: the selection toolbar's trash icon deleted every selected expense at once, with no question. */
@RunWith(AndroidJUnit4::class)
class BulkDeleteTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var vm: ExpensesViewModel

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        context.deleteSharedPreferences("bulk_delete_test")
        val ids = runBlocking {
            listOf("Dinner" to 900.0, "Cab" to 250.0, "Coffee" to 180.0, "Groceries" to 200.0)
                .map { (description, amount) -> db.expenseDao().insert(Expense(amount = amount, description = description, source = "Manual")) }
        }
        vm = runBlocking(Dispatchers.Main) { ExpensesViewModel(ExpenseRepository(db.expenseDao(), context), CategoryRepository(db.categoryDao(), db.expenseDao()),
            MerchantMappingRepository(db.merchantMappingDao()), IncomeRepository(db.incomeDao(), db.recurringIncomeDao(), context),
            context, PreferencesManager(context, "bulk_delete_test"), SavedStateHandle()) }
        compose.setContent { ExpensesScreen(vm, onCategorize = {}) }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Dinner")).fetchSemanticsNodes().isNotEmpty() }
        ids.forEach(vm::toggleSelection)
    }
    // The screen's flows query the database until the ViewModel stops
    @After fun tearDown() { vm.viewModelScope.cancel(); db.close() }

    private fun count() = runBlocking { db.expenseDao().count() }
    private fun dialogButton(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    @Test fun deletingTheSelectionAsksFirst() {
        compose.onNodeWithContentDescription("Delete selected").performClick()
        compose.onNodeWithText("Delete 4 expenses?").assertExists()
        assertEquals(4, count())

        dialogButton("Cancel").performClick()
        compose.onNodeWithText("Delete 4 expenses?").assertDoesNotExist()
        assertEquals(4, count())
        compose.onNodeWithText("4 selected").assertExists()

        compose.onNodeWithContentDescription("Delete selected").performClick()
        dialogButton("Delete").performClick()
        compose.waitUntil(5_000) { count() == 0 }
    }
}
