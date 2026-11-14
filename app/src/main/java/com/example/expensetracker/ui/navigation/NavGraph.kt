package com.example.expensetracker.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.expensetracker.ui.theme.Deep
import com.example.expensetracker.ui.theme.Gold
import com.example.expensetracker.ui.theme.Muted
import com.example.expensetracker.ui.theme.Paper
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.expensetracker.LaunchAction
import com.example.expensetracker.data.backup.BackupManager
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.data.period.MonthStartDay
import com.example.expensetracker.data.recurring.RecurringManager
import com.example.expensetracker.data.repository.BillRepository
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.IncomeRepository
import com.example.expensetracker.data.repository.LoanRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.data.repository.MerchantMappingRepository
import com.example.expensetracker.data.repository.RecurringTemplateRepository
import com.example.expensetracker.data.repository.SavingsGoalRepository
import com.example.expensetracker.notification.BillReminderHelper
import com.example.expensetracker.ui.screens.categories.CategoriesScreen
import com.example.expensetracker.ui.screens.categories.CategoriesViewModel
import com.example.expensetracker.ui.screens.categorize.CategorizeScreen
import com.example.expensetracker.ui.screens.categorize.CategorizeViewModel
import com.example.expensetracker.ui.screens.dashboard.DashboardScreen
import com.example.expensetracker.ui.screens.dashboard.DashboardViewModel
import com.example.expensetracker.ui.screens.expenses.ExpensesScreen
import com.example.expensetracker.ui.screens.expenses.ExpensesViewModel
import com.example.expensetracker.ui.screens.settings.SettingsHome
import com.example.expensetracker.ui.screens.settings.SettingsScreen
import com.example.expensetracker.ui.screens.settings.SettingsViewModel
import com.example.expensetracker.ui.screens.statistics.StatisticsScreen
import com.example.expensetracker.ui.screens.subscriptions.SubscriptionsScreen
import com.example.expensetracker.ui.screens.subscriptions.SubscriptionsViewModel
import com.example.expensetracker.ui.screens.statistics.StatisticsViewModel
import com.example.expensetracker.ui.screens.rules.MessageRulesScreen
import com.example.expensetracker.ui.screens.rules.MessageRulesViewModel
import com.example.expensetracker.ui.screens.rules.TeachMessageScreen
import kotlinx.coroutines.flow.first

sealed class Screen(val route: String, val label: String, val pattern: String = route) {
    object Dashboard : Screen("dashboard", "Home")
    object Expenses : Screen("expenses", "Activity")
    object Categories : Screen("categories", "Categories")
    object Statistics : Screen("statistics", "Insights")
    object Plan : Screen("plan", "Plan")
    object Settings : Screen("settings", "Settings")
    object Subscriptions : Screen("subscriptions", "Subscriptions")
    object Categorize : Screen("categorize", "Categorize", "categorize/{expenseId}") {
        fun createRoute(expenseId: Long) = "categorize/$expenseId"
    }
    object MessageRules : Screen("message_rules", "Message rules")
    object TeachMessage : Screen("teach_message", "Teach a message", "teach_message?expenseId={expenseId}") {
        fun createRoute(expenseId: Long? = null) = if (expenseId == null) "teach_message" else "teach_message?expenseId=$expenseId"
    }
}

/**
 * Go to a top-level tab the way the bottom nav does: back to Home first (so screens don't pile
 * up), one copy of the tab, and its saved state (filters, scroll) restored unless [restore] is false.
 */
private fun NavController.navigateToTab(route: String, restore: Boolean = true) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = restore
}

// The gold Add button sits between the second and third tab.
private val bottomNavItems = listOf(
    Screen.Dashboard to Icons.Default.Home,
    Screen.Expenses to Icons.AutoMirrored.Filled.List,
    Screen.Statistics to Icons.Default.BarChart,
    Screen.Plan to Icons.Default.CalendarMonth,
)

@Composable
private fun RowScope.TabItem(screen: Screen, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, screen.label) },
        label = { Text(screen.label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Gold,
            selectedTextColor = Gold,
            indicatorColor = Color.Transparent,
            unselectedIconColor = Muted,
            unselectedTextColor = Muted,
        )
    )
}

@Composable
private fun RowScope.AddButton(onClick: () -> Unit) {
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Gold)
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add expense", tint = Deep)
        }
    }
}

@Composable
fun AppNavGraph(launchAction: LaunchAction? = null, onLaunchHandled: () -> Unit = {}) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val categoryRepo = remember { CategoryRepository(db.categoryDao(), db.expenseDao()) }
    val expenseRepo = remember { ExpenseRepository(db.expenseDao(), context.applicationContext) }
    val recurringRepo = remember { RecurringTemplateRepository(db.recurringTemplateDao()) }
    val mappingRepo = remember { MerchantMappingRepository(db.merchantMappingDao()) }
    val savingsRepo = remember { SavingsGoalRepository(db.savingsGoalDao()) }
    val billRepo = remember { BillRepository(db.billDao()) }
    val loanRepo = remember { LoanRepository(db.loanDao()) }
    val incomeRepo = remember { IncomeRepository(db.incomeDao(), db.recurringIncomeDao(), context.applicationContext) }
    val prefs = remember { PreferencesManager(context) }
    val backupManager = remember { BackupManager(context.applicationContext) }

    val navController = rememberNavController()
    // The widget's "+", waiting for the Expenses screen to open its add dialog. Saveable only so a
    // rotation in the moment between the tap and the dialog opening doesn't lose the request; the
    // open dialog itself closes on rotation, as every dialog in the app does.
    var quickAddRequested by rememberSaveable { mutableStateOf(false) }
    // Statistics' "View all", waiting for the Expenses screen to filter to those dates. Not a route
    // argument: going back to the saved Expenses tab restores its old arguments, not new ones.
    var expensesRangeRequest by rememberSaveable { mutableStateOf<Pair<Long, Long>?>(null) }

    // Apply recurring expenses if it's a new month; schedule bill reminders
    LaunchedEffect(Unit) {
        // An expense whose category is gone is pending, not a "?" row nobody is asked to sort.
        runCatching { AppDatabase.getInstance(context).expenseDao().moveOrphansToPending() }
        RecurringManager.applyIfNeeded(context)
        BillReminderHelper.schedule(context)
    }

    // Like the bottom nav's Activity tab: the same screen (filters kept), never a pile of copies;
    // the request then opens its add dialog. The tab's restored stack can have a screen on top of
    // the list (Categorize, from a notification), where the dialog would wait unseen until the user
    // came back; so back to the list itself.
    fun openQuickAdd() {
        navController.navigateToTab(Screen.Expenses.route)
        navController.popBackStack(Screen.Expenses.pattern, inclusive = false)
        quickAddRequested = true
    }

    LaunchedEffect(launchAction) {
        if (launchAction == null) return@LaunchedEffect
        // On a cold start this runs before NavHost has set its graph; navigating then crashed
        // ("You must call setGraph() before calling getGraph()"). Wait for the first screen.
        navController.currentBackStackEntryFlow.first()
        when (launchAction) {
            is LaunchAction.Categorize -> navController.navigate(Screen.Categorize.createRoute(launchAction.expenseId))
            LaunchAction.QuickAdd -> openQuickAdd()
            LaunchAction.OpenSubscriptions -> navController.navigate(Screen.Subscriptions.route) {
                popUpTo(navController.graph.findStartDestination().id)
            }
        }
        onLaunchHandled()
    }

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDest = navBackStackEntry?.destination
            val showBottomBar = bottomNavItems.any { (screen, _) -> currentDest?.route == screen.pattern }
            if (showBottomBar) {
                NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
                    bottomNavItems.forEachIndexed { index, (screen, icon) ->
                        if (index == bottomNavItems.size / 2) AddButton(onClick = ::openQuickAdd)
                        TabItem(
                            screen, icon,
                            selected = currentDest?.hierarchy?.any { it.route == screen.pattern } == true,
                            onClick = { navController.navigateToTab(screen.route) }
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
            composable(Screen.Dashboard.pattern) {
                val vm: DashboardViewModel = viewModel(
                    factory = DashboardViewModel.factory(context.applicationContext, categoryRepo, expenseRepo, savingsRepo, billRepo, loanRepo, incomeRepo, prefs)
                )
                DashboardScreen(
                    viewModel = vm,
                    onViewPending = { navController.navigateToTab(Screen.Expenses.route) },
                    onViewSubscriptions = { navController.navigate(Screen.Subscriptions.route) },
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    // The Auto-tracking card is the first thing in Settings.
                    onEnableTracking = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(Screen.Expenses.pattern) {
                val vm: ExpensesViewModel = viewModel(
                    factory = ExpensesViewModel.factory(expenseRepo, categoryRepo, mappingRepo, incomeRepo, context.applicationContext, prefs)
                )
                LaunchedEffect(expensesRangeRequest) {
                    val range = expensesRangeRequest ?: return@LaunchedEffect
                    vm.setDateRange(range.first, range.second)
                    expensesRangeRequest = null
                }

                ExpensesScreen(
                    viewModel = vm,
                    openAdd = quickAddRequested,
                    onAddOpened = { quickAddRequested = false },
                    onCategorize = { id -> navController.navigate(Screen.Categorize.createRoute(id)) }
                )
            }
            composable(Screen.Categories.pattern) {
                val vm: CategoriesViewModel = viewModel(
                    factory = CategoriesViewModel.factory(categoryRepo, expenseRepo, incomeRepo)
                )
                CategoriesScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(Screen.Statistics.pattern) {
                val vm: StatisticsViewModel = viewModel(
                    factory = StatisticsViewModel.factory(expenseRepo, categoryRepo, MonthStartDay.flow(context), incomeRepo.allIncomes)
                )
                StatisticsScreen(
                    viewModel = vm,
                    onMonthClick = { start, end ->
                        expensesRangeRequest = start to end
                        // Back to the list itself, as for the widget's "+" above.
                        navController.navigateToTab(Screen.Expenses.route)
                        navController.popBackStack(Screen.Expenses.pattern, inclusive = false)
                    }
                )
            }
            // Settings and Plan are two front pages over the same sub-pages and view model.
            for ((screen, home) in listOf(Screen.Settings to SettingsHome.Settings, Screen.Plan to SettingsHome.Plan)) {
                composable(screen.pattern) {
                    val vm: SettingsViewModel = viewModel(
                        factory = SettingsViewModel.factory(context.applicationContext, db, categoryRepo, expenseRepo, recurringRepo, billRepo, loanRepo, mappingRepo, incomeRepo, backupManager, prefs)
                    )
                    SettingsScreen(
                        viewModel = vm, home = home,
                        onBack = { navController.popBackStack() },
                        onOpenMessageRules = { navController.navigate(Screen.MessageRules.route) },
                        onOpenCategories = { navController.navigate(Screen.Categories.route) },
                        onOpenSubscriptions = { navController.navigate(Screen.Subscriptions.route) },
                    )
                }
            }
            composable(Screen.Subscriptions.route) {
                val vm: SubscriptionsViewModel = viewModel(
                    factory = SubscriptionsViewModel.factory(context.applicationContext, expenseRepo, categoryRepo, billRepo)
                )
                SubscriptionsScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(Screen.Categorize.pattern) { backStackEntry ->
                val expenseId = backStackEntry.arguments?.getString("expenseId")?.toLongOrNull() ?: return@composable
                val vm: CategorizeViewModel = viewModel(
                    factory = CategorizeViewModel.factory(expenseRepo, categoryRepo, mappingRepo, savingsRepo, loanRepo, context.applicationContext, prefs)
                )
                CategorizeScreen(
                    expenseId = expenseId,
                    viewModel = vm,
                    onDone = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                    onTeach = { id -> navController.navigate(Screen.TeachMessage.createRoute(id)) }
                )
            }
            composable(Screen.MessageRules.route) {
                val vm: MessageRulesViewModel = viewModel(factory = MessageRulesViewModel.factory(context.applicationContext, expenseRepo))
                MessageRulesScreen(vm, onTeach = { navController.navigate(Screen.TeachMessage.createRoute()) }, onBack = { navController.popBackStack() })
            }
            composable(
                Screen.TeachMessage.pattern,
                arguments = listOf(navArgument("expenseId") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) { entry ->
                // A fresh view model per screen, so one teaching session doesn't carry into the next.
                val vm: MessageRulesViewModel = viewModel(viewModelStoreOwner = entry, factory = MessageRulesViewModel.factory(context.applicationContext, expenseRepo))
                TeachMessageScreen(
                    vm, expenseId = entry.arguments?.getString("expenseId")?.toLongOrNull(),
                    onDone = { navController.popBackStack() }, onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
