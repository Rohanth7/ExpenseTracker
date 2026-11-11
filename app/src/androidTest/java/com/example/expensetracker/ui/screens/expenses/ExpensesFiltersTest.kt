package com.example.expensetracker.ui.screens.expenses

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Expenses header and search bar against a throwaway in-memory database and settings file. */
@RunWith(AndroidJUnit4::class)
class ExpensesFiltersTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var vm: ExpensesViewModel
    private var food = 0L

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        context.deleteSharedPreferences("expenses_filters_test")
        runBlocking {
            food = db.categoryDao().insert(Category(name = "Food"))
            db.expenseDao().insert(Expense(amount = 900.0, description = "Dinner", source = "Manual", categoryId = food, tags = "goa"))
            db.expenseDao().insert(Expense(amount = 250.0, description = "Cab", source = "Manual", tags = "goa"))
            db.expenseDao().insert(Expense(amount = 180.0, description = "Coffee", source = "Manual", categoryId = food))
        }
        vm = runBlocking(Dispatchers.Main) { ExpensesViewModel(ExpenseRepository(db.expenseDao(), context), CategoryRepository(db.categoryDao(), db.expenseDao()),
            MerchantMappingRepository(db.merchantMappingDao()), IncomeRepository(db.incomeDao(), db.recurringIncomeDao(), context),
            context, PreferencesManager(context, "expenses_filters_test"), SavedStateHandle()) }
        compose.setContent { ExpensesScreen(vm, onCategorize = {}) }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Dinner")).fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun tearDown() { vm.viewModelScope.cancel(); db.close() }

    private fun shows(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    /** B-09: "THIS WEEK" was summed from the filtered list, so a search changed the week's total. */
    @Test fun theWeekTotalIgnoresSearchAndFilters() {
        // Unfiltered, today's total says the same; filtered, only the week's does
        assertTrue(shows("₹1,330"))
        compose.runOnUiThread { vm.setSearchQuery("Dinner") }
        compose.waitUntil(5_000) { !shows("Cab") }
        compose.onNodeWithText("₹1,330").assertExists()
        compose.runOnUiThread { vm.setSearchQuery(""); vm.setCategoryFilter(food) }
        compose.waitUntil(5_000) { !shows("Cab") && shows("Coffee") }
        compose.onNodeWithText("₹1,330").assertExists()
    }
}
