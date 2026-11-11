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
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.data.repository.IncomeRepository
import com.example.expensetracker.data.repository.MerchantMappingRepository
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

/** BUG-24: the selection toolbar's trash icon deleted every selected expense at once, with no question. */
@RunWith(AndroidJUnit4::class)
class BulkDeleteTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var vm: ExpensesViewModel
    private lateinit var ids: List<Long>

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        context.deleteSharedPreferences("bulk_delete_test")
        ids = runBlocking {
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

    /** Switching to Income clears the selection behind the dialog; the next selection mustn't reopen it. */
    @Test fun aSelectionClearedBehindTheDialogDoesNotReopenIt() {
        compose.onNodeWithContentDescription("Delete selected").performClick()
        compose.runOnUiThread { vm.setShowIncome(true) }
        compose.onNodeWithText("Delete 4 expenses?").assertDoesNotExist()

        compose.runOnUiThread { vm.setShowIncome(false); vm.toggleSelection(ids.first()) }
        compose.onNodeWithText("1 selected").assertExists()
        compose.onNodeWithText("Delete 1 expense?").assertDoesNotExist()
        assertEquals(4, count())
    }

    /** B-11: a search that hid 3 of the 4 selected rows still deleted all 4. */
    @Test fun aFilterLeavesOnlyTheRowsOnScreenSelected() {
        compose.runOnUiThread { vm.setSearchQuery("Dinner") }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Cab")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("1 selected").assertExists()

        compose.onNodeWithContentDescription("Delete selected").performClick()
        compose.onNodeWithText("Delete 1 expense?").assertExists()
        dialogButton("Delete").performClick()
        compose.waitUntil(5_000) { count() == 3 }
        assertEquals(listOf("Cab", "Coffee", "Groceries"), runBlocking { db.expenseDao().getAll().first().map { it.description }.sorted() })
    }

    /** Bulk move, the same way: only the selected rows the filter still shows. */
    @Test fun bulkCategorizeMovesOnlyTheRowsOnScreen() {
        val food = runBlocking { db.categoryDao().insert(Category(name = "Food")) }
        compose.runOnUiThread { vm.setSearchQuery("Dinner") }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Cab")).fetchSemanticsNodes().isEmpty() }
        compose.runOnUiThread { vm.bulkCategorize(food) }
        compose.waitUntil(5_000) { runBlocking { db.expenseDao().getAll().first().any { it.categoryId == food } } }
        compose.waitForIdle()
        assertEquals(listOf("Dinner"), runBlocking { db.expenseDao().getAll().first().filter { it.categoryId == food }.map { it.description } })
    }

    /** Review of B-11: a row a search hid stayed selected out of sight and came back into bulk delete when the search was cleared. */
    @Test fun aRowHiddenByAFilterIsDeselectedForGood() {
        compose.runOnUiThread { vm.setSearchQuery("Dinner") }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Cab")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("1 selected").assertExists()

        compose.runOnUiThread { vm.setSearchQuery("") }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Cab")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("1 selected").assertExists()
        compose.onNodeWithContentDescription("Delete selected").performClick()
        compose.onNodeWithText("Delete 1 expense?").assertExists()
        dialogButton("Delete").performClick()
        compose.waitUntil(5_000) { count() == 3 }
        compose.waitForIdle()
        assertEquals(3, count())
    }

    /** A search that hides every selected row ends the selection. */
    @Test fun hidingEverySelectedRowEndsTheSelection() {
        compose.runOnUiThread { vm.setSearchQuery("zzz") }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Dinner")).fetchSemanticsNodes().isEmpty() }
        compose.runOnUiThread { vm.setSearchQuery("") }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Dinner")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("4 selected").assertDoesNotExist()
        compose.onNodeWithContentDescription("Delete selected").assertDoesNotExist()
        assertEquals(4, count())
    }
}
