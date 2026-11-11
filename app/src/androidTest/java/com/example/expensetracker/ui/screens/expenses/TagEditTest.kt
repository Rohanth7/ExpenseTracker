package com.example.expensetracker.ui.screens.expenses

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.db.entity.Income
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.data.repository.IncomeRepository
import com.example.expensetracker.data.repository.MerchantMappingRepository
import com.example.expensetracker.ui.screens.categories.CategoriesViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Renaming and deleting tags from Expenses and Categories, against a throwaway in-memory database. */
@RunWith(AndroidJUnit4::class)
class TagEditTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var prefs: PreferencesManager
    private val screen = CoroutineScope(Dispatchers.Main)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        context.deleteSharedPreferences("tag_edit_test")
        prefs = PreferencesManager(context, "tag_edit_test")
    }
    @After fun tearDown() { screen.cancel(); db.close() }

    private val categories get() = CategoryRepository(db.categoryDao(), db.expenseDao())
    private val expenses get() = ExpenseRepository(db.expenseDao(), context)
    private val incomes get() = IncomeRepository(db.incomeDao(), db.recurringIncomeDao(), context)

    private suspend fun expensesScreen() = withContext(Dispatchers.Main) {
        ExpensesViewModel(expenses, categories, MerchantMappingRepository(db.merchantMappingDao()), incomes, context, prefs, SavedStateHandle())
    }.also { vm -> screen.launch { vm.allTagSummaries.collect {} } }  // the screen keeps this subscribed

    private suspend fun categoriesScreen() = withContext(Dispatchers.Main) { CategoriesViewModel(categories, expenses, incomes) }
        .also { vm -> screen.launch { vm.allTagSummaries.collect {} } }

    private suspend fun act(action: () -> Job) = withContext(Dispatchers.Main) { action() }.join()
    private suspend fun expenseTags() = db.expenseDao().getAll().first().associate { it.description to it.tags }
    private suspend fun incomeTags() = db.incomeDao().getAllOnce().associate { it.description to it.tags }

    /** BUG-23: "goa,food" renamed goa → food became "food,food", and the tag showed ₹600 for a ₹300 dinner. */
    @Test fun renamingFromExpensesToATagAlreadyThereCountsOnce() = runBlocking {
        db.expenseDao().insert(Expense(amount = 300.0, description = "Dinner", tags = "goa,food"))
        db.incomeDao().insert(Income(amount = 2000.0, description = "Refund", tags = "Goa"))
        val vm = expensesScreen()
        withTimeout(10_000) { vm.allTagSummaries.first { it.isNotEmpty() } }

        act { vm.renameTag("goa", "food") }

        assertEquals(mapOf("Dinner" to "food"), expenseTags())
        assertEquals(mapOf("Refund" to "food"), incomeTags())
        val summary = withTimeout(10_000) { vm.allTagSummaries.first { s -> s.none { it.name == "goa" } } }
        assertEquals(listOf(TagSummary("food", 1, 300.0)), summary)
    }

    /** BUG-23: the Categories tab's rename and delete had the same problem and skipped income too. */
    @Test fun renamingAndDeletingFromCategoriesCoverIncome() = runBlocking {
        db.expenseDao().insert(Expense(amount = 300.0, description = "Dinner", tags = "goa,food"))
        db.incomeDao().insert(Income(amount = 2000.0, description = "Refund", tags = "goa,work"))
        val vm = categoriesScreen()

        act { vm.renameTag("goa", "Food") }
        assertEquals(mapOf("Dinner" to "Food"), expenseTags())
        assertEquals(mapOf("Refund" to "Food,work"), incomeTags())

        act { vm.deleteTag("food") }
        assertEquals(mapOf("Dinner" to ""), expenseTags())
        assertEquals(mapOf("Refund" to "work"), incomeTags())
    }

    /** A name with a comma would have become two tags. */
    @Test fun aNameWithACommaIsRefused() = runBlocking {
        db.expenseDao().insert(Expense(amount = 300.0, description = "Dinner", tags = "goa"))
        val fromExpenses = expensesScreen()
        val fromCategories = categoriesScreen()
        act { fromExpenses.renameTag("goa", "beach,trip") }
        act { fromCategories.renameTag("goa", "beach,trip") }
        assertEquals(mapOf("Dinner" to "goa"), expenseTags())
    }

    /** B-12: tags were stored exactly as typed, "#" and repeats included. */
    @Test fun tagsAreStoredCleanWhoeverSavesThem() = runBlocking {
        val vm = expensesScreen()
        withContext(Dispatchers.Main) { vm.addExpense(120.0, "Lunch", -1L, System.currentTimeMillis(), tags = " #Food, food ,, #travel ") }
        withTimeout(10_000) { db.expenseDao().getAll().first { it.isNotEmpty() } }
        assertEquals(mapOf("Lunch" to "Food,travel"), expenseTags())

        act { vm.addIncome(500.0, "Refund", System.currentTimeMillis(), "#side, Side") }
        assertEquals(mapOf("Refund" to "side"), incomeTags())

        val lunch = db.expenseDao().getAll().first().single()
        act { vm.updateExpense(lunch, 120.0, "Lunch", -1L, lunch.date, tags = "#gift,gift") }
        assertEquals(mapOf("Lunch" to "gift"), expenseTags())
    }

    /** B-12: a tag renamed or deleted in Categories stayed as Expenses' filter, which then showed nothing. */
    @Test fun aTagFilterThatNoExpenseHasAnyMoreIsCleared() = runBlocking {
        db.expenseDao().insert(Expense(amount = 300.0, description = "Dinner", tags = "goa"))
        db.expenseDao().insert(Expense(amount = 80.0, description = "Tea"))
        val vm = expensesScreen()
        val shown = screen.launch { vm.expenses.collect {} }
        withContext(Dispatchers.Main) { vm.setTagFilter("Goa") }
        assertEquals(listOf("Dinner"), withTimeout(10_000) { vm.expenses.first { it.size == 1 } }.map { it.description })

        val fromCategories = categoriesScreen()
        act { fromCategories.renameTag("goa", "beach") }

        withTimeout(10_000) { vm.tagFilter.first { it == null } }
        assertEquals(2, withTimeout(10_000) { vm.expenses.first { it.size == 2 } }.size)
        shown.cancel()
    }

    /** A filter whose tag is still in use stays, whatever case it was typed in. */
    @Test fun aTagFilterStillInUseStays() = runBlocking {
        db.expenseDao().insert(Expense(amount = 300.0, description = "Dinner", tags = "goa"))
        val vm = expensesScreen()
        val shown = screen.launch { vm.expenses.collect {} }
        withContext(Dispatchers.Main) { vm.setTagFilter("GOA") }
        db.expenseDao().insert(Expense(amount = 80.0, description = "Tea"))
        withTimeout(10_000) { vm.hasAnyExpenses.first { it } }
        assertEquals(listOf("Dinner"), withTimeout(10_000) { vm.expenses.first { it.size == 1 } }.map { it.description })
        assertEquals("GOA", vm.tagFilter.value)
        shown.cancel()
    }
}
