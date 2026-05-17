package com.example.expensetracker.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.expensetracker.data.backup.BackupManager
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.data.recurring.RecurringManager
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.data.repository.RecurringTemplateRepository
import com.example.expensetracker.ui.screens.categories.CategoriesScreen
import com.example.expensetracker.ui.screens.categories.CategoriesViewModel
import com.example.expensetracker.ui.screens.categorize.CategorizeScreen
import com.example.expensetracker.ui.screens.categorize.CategorizeViewModel
import com.example.expensetracker.ui.screens.dashboard.DashboardScreen
import com.example.expensetracker.ui.screens.dashboard.DashboardViewModel
import com.example.expensetracker.ui.screens.expenses.ExpensesScreen
import com.example.expensetracker.ui.screens.expenses.ExpensesViewModel
import com.example.expensetracker.ui.screens.settings.SettingsScreen
import com.example.expensetracker.ui.screens.settings.SettingsViewModel

sealed class Screen(val route: String, val label: String) {
    object Dashboard : Screen("dashboard", "Dashboard")
    object Expenses : Screen("expenses", "Expenses")
    object Categories : Screen("categories", "Categories")
    object Settings : Screen("settings", "Settings")
    object Categorize : Screen("categorize/{expenseId}", "Categorize") {
        fun route(expenseId: Long) = "categorize/$expenseId"
    }
}

private val bottomNavItems = listOf(Screen.Dashboard, Screen.Expenses, Screen.Categories, Screen.Settings)

@Composable
fun AppNavGraph(initialExpenseId: Long? = null, onExpenseNavigated: () -> Unit = {}) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val categoryRepo = remember { CategoryRepository(db.categoryDao(), db.expenseDao()) }
    val expenseRepo = remember { ExpenseRepository(db.expenseDao()) }
    val recurringRepo = remember { RecurringTemplateRepository(db.recurringTemplateDao()) }
    val prefs = remember { PreferencesManager(context) }
    val backupManager = remember { BackupManager(context.applicationContext) }

    val navController = rememberNavController()

    // Apply recurring expenses if it's a new month
    LaunchedEffect(Unit) {
        RecurringManager.applyIfNeeded(context)
    }

    LaunchedEffect(initialExpenseId) {
        if (initialExpenseId != null && initialExpenseId != -1L) {
            navController.navigate(Screen.Categorize.route(initialExpenseId))
            onExpenseNavigated()
        }
    }

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDest = navBackStackEntry?.destination
            val showBottomBar = bottomNavItems.any { it.route == currentDest?.route }
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        val selected = currentDest?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                when (screen) {
                                    Screen.Dashboard -> Icon(Icons.Default.Home, screen.label)
                                    Screen.Expenses -> Icon(Icons.AutoMirrored.Filled.List, screen.label)
                                    Screen.Categories -> Icon(Icons.Default.Category, screen.label)
                                    Screen.Settings -> Icon(Icons.Default.Settings, screen.label)
                                    else -> {}
                                }
                            },
                            label = { Text(screen.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Dashboard.route) {
                val vm: DashboardViewModel = viewModel(
                    factory = DashboardViewModel.factory(categoryRepo, expenseRepo, prefs)
                )
                DashboardScreen(
                    viewModel = vm,
                    onViewPending = { navController.navigate(Screen.Expenses.route) }
                )
            }
            composable(Screen.Expenses.route) {
                val vm: ExpensesViewModel = viewModel(
                    factory = ExpensesViewModel.factory(expenseRepo, categoryRepo, context.applicationContext)
                )
                ExpensesScreen(
                    viewModel = vm,
                    onCategorize = { id -> navController.navigate(Screen.Categorize.route(id)) }
                )
            }
            composable(Screen.Categories.route) {
                val vm: CategoriesViewModel = viewModel(
                    factory = CategoriesViewModel.factory(categoryRepo)
                )
                CategoriesScreen(viewModel = vm)
            }
            composable(Screen.Settings.route) {
                val vm: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.factory(categoryRepo, expenseRepo, recurringRepo, backupManager)
                )
                SettingsScreen(viewModel = vm)
            }
            composable(Screen.Categorize.route) { backStackEntry ->
                val expenseId = backStackEntry.arguments?.getString("expenseId")?.toLongOrNull() ?: return@composable
                val vm: CategorizeViewModel = viewModel(
                    factory = CategorizeViewModel.factory(expenseRepo, categoryRepo, context.applicationContext)
                )
                CategorizeScreen(
                    expenseId = expenseId,
                    viewModel = vm,
                    onDone = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
