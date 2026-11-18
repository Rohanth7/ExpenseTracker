package com.example.expensetracker.ui.mascot

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.period.BudgetPeriod
import com.example.expensetracker.data.period.MonthStartDay
import com.example.expensetracker.data.period.OverspentDay
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.data.repository.BillRepository
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.data.repository.IncomeRepository
import com.example.expensetracker.data.repository.LoanRepository
import com.example.expensetracker.data.repository.MerchantMappingRepository
import com.example.expensetracker.data.repository.SavingsGoalRepository
import com.example.expensetracker.ui.screens.dashboard.DashboardScreen
import com.example.expensetracker.ui.screens.dashboard.DashboardViewModel
import com.example.expensetracker.ui.screens.expenses.ExpensesScreen
import com.example.expensetracker.ui.screens.expenses.ExpensesViewModel
import com.example.expensetracker.ui.screens.lock.LockedScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Where she shows up, and that "Show the pot" takes her away, against a throwaway database and settings file. */
@RunWith(AndroidJUnit4::class)
class MascotPlacementsTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var prefs: PreferencesManager
    private val shown = mutableStateOf(true)
    private var cleanup: () -> Unit = {}

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        context.deleteSharedPreferences("mascot_placements_test")
        prefs = PreferencesManager(context, "mascot_placements_test")
    }
    @After fun tearDown() { cleanup(); db.close() }

    private fun shows(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun home(spent: Double = 1_000.0, loaded: String = "Free to spend"): DashboardViewModel {
        prefs.monthlyIncome = 50_000.0
        runBlocking { db.expenseDao().insert(Expense(amount = spent, description = "Groceries", source = "Manual")) }
        val vm = runBlocking(Dispatchers.Main) {
            DashboardViewModel(context, CategoryRepository(db.categoryDao(), db.expenseDao()), ExpenseRepository(db.expenseDao(), context),
                SavingsGoalRepository(db.savingsGoalDao()), BillRepository(db.billDao()), LoanRepository(db.loanDao()),
                IncomeRepository(db.incomeDao(), db.recurringIncomeDao(), context), prefs)
        }
        cleanup = { vm.viewModelScope.cancel() }
        compose.setContent {
            CompositionLocalProvider(LocalMascotEnabled provides shown.value) { DashboardScreen(vm, onViewPending = {}) }
        }
        compose.waitUntil(10_000) { shows(loaded) }
        return vm
    }

    // Her wording has its own tests (MascotVoiceTest); these only check where it shows.
    private fun todaysLine(): String {
        val month = mascotMonth(50_000.0, 1_000.0, 0.0, emptyList(), prefs.budgetAlertThreshold,
            System.currentTimeMillis(), MonthStartDay.get(context), daysOver = 0)
        return MascotVoice.say(month).line
    }

    @Test fun homeSpeaksAboutTheCurrentMonthOnly() {
        val vm = home()
        val line = todaysLine()
        compose.onNodeWithText(line).assertExists()

        compose.runOnUiThread { vm.goToPreviousMonth() }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Spent in", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(line).assertDoesNotExist()
    }

    @Test fun withThePotHiddenHomeHasNoSpeechLine() {
        home()
        val line = todaysLine()
        compose.onNodeWithText(line).assertExists()
        shown.value = false
        compose.waitForIdle()
        compose.onNodeWithText(line).assertDoesNotExist()
        assertTrue("Home still shows its figures", shows("Free to spend"))
    }

    @Test fun withThePotHiddenHomeStillRecordsTheDayTheMoneyRanOut() {
        shown.value = false
        home(spent = 60_000.0, loaded = "Over this month")
        val now = System.currentTimeMillis()
        val monthStart = BudgetPeriod.epochDay(BudgetPeriod.current(now, MonthStartDay.get(context)).start)
        assertEquals(mapOf(OverspentDay.PREFIX + monthStart to BudgetPeriod.epochDay(now)), prefs.overspentDays.all())
    }

    @Test fun activityWithNoExpensesSaysSoWithOrWithoutHer() {
        val vm = runBlocking(Dispatchers.Main) {
            ExpensesViewModel(ExpenseRepository(db.expenseDao(), context), CategoryRepository(db.categoryDao(), db.expenseDao()),
                MerchantMappingRepository(db.merchantMappingDao()), IncomeRepository(db.incomeDao(), db.recurringIncomeDao(), context),
                context, prefs, SavedStateHandle())
        }
        cleanup = { vm.viewModelScope.cancel() }
        compose.setContent {
            CompositionLocalProvider(LocalMascotEnabled provides shown.value) { ExpensesScreen(vm, onCategorize = {}) }
        }
        compose.waitUntil(10_000) { shows("Nothing here yet") }
        compose.onNodeWithText("Your first payment will show up on its own.").assertExists()
        shown.value = false
        compose.waitForIdle()
        compose.onNodeWithText("Nothing here yet").assertExists()
        compose.onNodeWithText("Your first payment will show up on its own.").assertExists()
    }

    @Test fun withThePotHiddenTheLockScreenStillUnlocks() {
        var unlockTaps = 0
        shown.value = false
        compose.setContent {
            CompositionLocalProvider(LocalMascotEnabled provides shown.value) { LockedScreen(message = null, onUnlock = { unlockTaps++ }) }
        }
        compose.onNodeWithText("Locked").assertExists()
        compose.onNodeWithText("Unlock").performClick()
        assertEquals(1, unlockTaps)
    }
}
