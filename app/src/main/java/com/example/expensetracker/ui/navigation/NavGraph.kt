package com.example.expensetracker.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.expensetracker.ui.theme.Canvas
import com.example.expensetracker.ui.theme.Ink
import com.example.expensetracker.ui.theme.Muted
import com.example.expensetracker.ui.theme.Paper
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.expensetracker.data.backup.BackupManager
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.data.recurring.RecurringManager
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.data.repository.MerchantMappingRepository
import com.example.expensetracker.data.repository.RecurringTemplateRepository
import com.example.expensetracker.data.repository.SavingsGoalRepository
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
import com.example.expensetracker.ui.screens.statistics.StatisticsScreen
import com.example.expensetracker.ui.screens.statistics.StatisticsViewModel

sealed class Screen(val route: String, val label: String) {
    object Dashboard : Screen("dashboard", "Overview")
    object Expenses : Screen("expenses?start={start}&end={end}", "Expenses") {
        fun route(start: Long? = null, end: Long? = null): String {
            return if (start != null && end != null) "expenses?start=$start&end=$end" else "expenses"
        }
    }
    object Categories : Screen("categories", "Categories")
    object Statistics : Screen("statistics", "Stats")
    object Settings : Screen("settings", "Settings")
    object Categorize : Screen("categorize/{expenseId}", "Categorize") {
        fun route(expenseId: Long) = "categorize/$expenseId"
    }
}

private val bottomNavItems = listOf(Screen.Dashboard, Screen.Expenses, Screen.Categories, Screen.Statistics, Screen.Settings)

@Composable
fun AppNavGraph(initialExpenseId: Long? = null, onExpenseNavigated: () -> Unit = {}) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val categoryRepo = remember { CategoryRepository(db.categoryDao(), db.expenseDao()) }
    val expenseRepo = remember { ExpenseRepository(db.expenseDao(), context.applicationContext) }
    val recurringRepo = remember { RecurringTemplateRepository(db.recurringTemplateDao()) }
    val mappingRepo = remember { MerchantMappingRepository(db.merchantMappingDao()) }
    val savingsRepo = remember { SavingsGoalRepository(db.savingsGoalDao()) }
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
                NavigationBar(
                    containerColor = Paper,
                    tonalElevation = 0.dp
                ) {
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
                                    Screen.Statistics -> Icon(Icons.Default.BarChart, screen.label)
                                    Screen.Settings -> Icon(Icons.Default.Settings, screen.label)
                                    else -> {}
                                }
                            },
                            label = { Text(screen.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Canvas,
                                selectedTextColor = Ink,
                                indicatorColor = Ink,
                                unselectedIconColor = Muted,
                                unselectedTextColor = Muted,
                            )
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
                    factory = DashboardViewModel.factory(context.applicationContext, categoryRepo, expenseRepo, savingsRepo, prefs)
                )
                DashboardScreen(
                    viewModel = vm,
                    onViewPending = { navController.navigate(Screen.Expenses.route) },
                    onViewAllSpending = { navController.navigate(Screen.Statistics.route) }
                )
            }
            composable(
                route = Screen.Expenses.route,
                arguments = listOf(
                    navArgument("start") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("end") { type = NavType.LongType; defaultValue = -1L }
                )
            ) { backStackEntry ->
                val start = backStackEntry.arguments?.getLong("start") ?: -1L
                val end = backStackEntry.arguments?.getLong("end") ?: -1L
                
                val vm: ExpensesViewModel = viewModel(
                    factory = ExpensesViewModel.factory(expenseRepo, categoryRepo, mappingRepo, context.applicationContext, prefs)
                )
                
                LaunchedEffect(start, end) {
                    if (start != -1L && end != -1L) {
                        vm.setDateRange(start, end)
                    } else {
                        vm.clearDateRange()
                    }
                }

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
            composable(Screen.Statistics.route) {
                val vm: StatisticsViewModel = viewModel(
                    factory = StatisticsViewModel.factory(expenseRepo, categoryRepo)
                )
                StatisticsScreen(
                    viewModel = vm,
                    onMonthClick = { start, end ->
                        navController.navigate(Screen.Expenses.route(start, end)) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Screen.Settings.route) {
                val vm: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.factory(context.applicationContext, db, categoryRepo, expenseRepo, recurringRepo, mappingRepo, backupManager, prefs)
                )
                SettingsScreen(viewModel = vm)
            }
            composable(Screen.Categorize.route) { backStackEntry ->
                val expenseId = backStackEntry.arguments?.getString("expenseId")?.toLongOrNull() ?: return@composable
                val vm: CategorizeViewModel = viewModel(
                    factory = CategorizeViewModel.factory(expenseRepo, categoryRepo, mappingRepo, context.applicationContext, prefs)
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
