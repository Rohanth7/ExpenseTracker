package com.example.expensetracker.ui.screens.expenses

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.db.entity.Income
import com.example.expensetracker.ui.theme.*
import com.example.expensetracker.ui.util.AmountCalculator
import com.example.expensetracker.ui.util.AmountField
import com.example.expensetracker.data.export.ExportPreset
import com.example.expensetracker.data.export.DateFilter
import com.example.expensetracker.data.period.BudgetPeriod
import com.example.expensetracker.ui.components.DatePresetOptions
import com.example.expensetracker.ui.util.PastOrTodayDates
import com.example.expensetracker.ui.util.buildTimestamp
import com.example.expensetracker.ui.util.rememberAmountInputState
import com.example.expensetracker.ui.util.toUtcMidnight
import com.example.expensetracker.ui.util.parseColor
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import com.example.expensetracker.ui.util.formatLimitInput
import com.example.expensetracker.ui.util.parseLimitInput
import com.example.expensetracker.ui.util.rememberAmountText
import com.example.expensetracker.ui.components.AppAlertDialog
import com.example.expensetracker.ui.components.AppDatePickerDialog

private data class DayGroup<T>(
    /** Unique per list (the date, with its year), for LazyColumn keys. */
    val key: String,
    val dayLabel: String,
    val dateLabel: String,
    val items: List<T>,
    val total: Double
)

@Composable
fun ExpensesScreen(
    viewModel: ExpensesViewModel,
    onCategorize: (Long) -> Unit,
    openAdd: Boolean = false,
    onAddOpened: () -> Unit = {}
) {
    val context = LocalContext.current
    val expenses by viewModel.expenses.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val categoryFilter by viewModel.categoryFilter.collectAsState()
    val sortByAmount by viewModel.sortByAmount.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val minAmount by viewModel.minAmount.collectAsState()
    val maxAmount by viewModel.maxAmount.collectAsState()
    val dateRange by viewModel.dateRange.collectAsState()
    val dateFilter by viewModel.dateFilter.collectAsState()
    val hasAnyExpenses by viewModel.hasAnyExpenses.collectAsState()
    val lastManualCategoryId by viewModel.lastManualCategoryId.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    // Which type the add dialog starts on: the list on show, or Expense for the widget's "+".
    var addAsIncome by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }
    var editingIncome by remember { mutableStateOf<Income?>(null) }
    var incomeToDelete by remember { mutableStateOf<Income?>(null) }
    var showBulkCategorize by remember { mutableStateOf(false) }
    var showAdvancedFilters by remember { mutableStateOf(false) }
    // The widget's "+": a one-shot request, consumed here so it can't fire twice. It replaces any
    // dialog left open (Edit, Delete, filters) or selection, so two dialogs never stack.
    LaunchedEffect(openAdd) {
        if (openAdd) {
            editingExpense = null; expenseToDelete = null; showBulkCategorize = false; showAdvancedFilters = false
            editingIncome = null; incomeToDelete = null
            viewModel.clearSelection()
            addAsIncome = false
            showAddDialog = true
            onAddOpened()
        }
    }

    val isSelectionMode = selectedIds.isNotEmpty()
    BackHandler(isSelectionMode) { viewModel.clearSelection() }

    val weekStartsOnMonday by viewModel.weekStartsOnMonday.collectAsState()
    val tagFilter by viewModel.tagFilter.collectAsState()
    val allTagSummaries by viewModel.allTagSummaries.collectAsState()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.syncPrefs()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val weekTotal = remember(expenses, weekStartsOnMonday) {
        expenses.filter { it.date >= weekStart(weekStartsOnMonday) }.sumOf { it.amount }
    }
    val grouped = remember(expenses, sortByAmount) { groupByDay(expenses, sortByAmount, { it.date }, { it.amount }) }
    val showIncome by viewModel.showIncome.collectAsState()
    val incomes by viewModel.incomes.collectAsState()
    val hasAnyIncome by viewModel.hasAnyIncome.collectAsState()
    val incomeGroups = remember(incomes, sortByAmount) { groupByDay(incomes, sortByAmount, { it.date }, { it.amount }) }
    val weekIncome = remember(incomes, weekStartsOnMonday) { incomes.filter { it.date >= weekStart(weekStartsOnMonday) }.sumOf { it.amount } }


    Scaffold(
        containerColor = Canvas,
        floatingActionButton = {
            if (!isSelectionMode) {
                Row(
                    modifier = Modifier
                        .height(58.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Jade)
                        .clickable { addAsIncome = showIncome; showAddDialog = true }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // The text labels the button, so the icon needs no description of its own.
                    Icon(Icons.Default.Add, contentDescription = null, tint = Deep, modifier = Modifier.size(26.dp))
                    Text(if (showIncome) "Add income" else "Add expense", color = Deep, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Canvas)
        ) {
            if (isSelectionMode) {
                BulkActionsToolbar(
                    selectedCount = selectedIds.size,
                    onClear = { viewModel.clearSelection() },
                    onDelete = { viewModel.bulkDelete() },
                    onCategorize = { showBulkCategorize = true }
                )
            } else {
                // ── Header ─────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            "LEDGER",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            letterSpacing = 1.8.sp,
                            color = Muted,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Expenses",
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 28.sp,
                            color = Ink,
                            lineHeight = 30.sp
                        )
                    }
                    val shownWeekTotal = if (showIncome) weekIncome else weekTotal
                    if (shownWeekTotal > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "THIS WEEK",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                letterSpacing = 1.6.sp,
                                color = Muted
                            )
                            Text(
                                buildAnnotatedString {
                                    val color = if (showIncome) Jade else Ink
                                    withStyle(SpanStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Normal, fontSize = 13.sp, color = color.copy(alpha = 0.55f))) { append(if (showIncome) "+₹" else "₹") }
                                    withStyle(SpanStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 22.sp, color = color)) { append(fmtINR(shownWeekTotal)) }
                                }
                            )
                        }
                    }
                }

                // ── Spending | Income ───────────────────────────────
                TwoWayToggle("Spending", "Income", rightSelected = showIncome, onSelect = { viewModel.setShowIncome(it) },
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp))

                // ── Search bar ──────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 10.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(Paper)
                        .border(1.dp, Hairline, RoundedCornerShape(100.dp))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Search, null, tint = Muted, modifier = Modifier.size(16.dp))
                    BasicSearchField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier.weight(1f)
                    )
                    if (searchQuery.isNotEmpty() || minAmount != null || maxAmount != null || dateRange != null) {
                        Icon(Icons.Default.Close, null, tint = Muted, modifier = Modifier.size(14.dp).clickable { viewModel.clearAllFilters() })
                    } else {
                        Icon(
                            Icons.Default.FilterList,
                            null,
                            tint = if (sortByAmount) Jade else Muted,
                            modifier = Modifier.size(16.dp).clickable { 
                                viewModel.toggleSortOrder()
                                val msg = if (!sortByAmount) "Sorting by Highest Amount" else "Sorting by Date"
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    Icon(
                        Icons.Default.Tune,
                        null,
                        tint = if (minAmount != null || maxAmount != null || dateRange != null) Jade else Muted,
                        modifier = Modifier.size(16.dp).clickable { showAdvancedFilters = true }
                    )
                }

                // ── Active date filter (from the filters dialog or a Statistics "View all") ──
                dateRange?.let { (from, to) ->
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(100.dp)).background(JadeSoft)
                            .clickable { viewModel.clearDateRange() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, null, tint = Jade, modifier = Modifier.size(13.dp))
                        Text(BudgetPeriod.dayRangeLabel(from, to), fontSize = 12.sp, color = Ink)
                        Icon(Icons.Default.Close, contentDescription = "Clear dates", tint = Muted, modifier = Modifier.size(13.dp))
                    }
                }

                // ── Filter chips ────────────────────────────────────
                val parentCategories = remember(categories) { categories.filter { it.parentId == null } }
                // Category and tag chips are spending's (income has no category, and tag rename/delete
                // covers expenses only), so they're hidden for income; search matches income tags.
                if (!showIncome && (parentCategories.isNotEmpty() || allTagSummaries.isNotEmpty())) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        if (parentCategories.isNotEmpty()) {
                            item {
                                ChipPill(
                                    label = "All",
                                    selected = (categoryFilter == -1L || categoryFilter == null) && tagFilter == null,
                                    onClick = { viewModel.setCategoryFilter(-1L); viewModel.setTagFilter(null) }
                                )
                            }
                            items(parentCategories) { cat ->
                                ChipPill(
                                    label = cat.name,
                                    selected = categoryFilter == cat.id && tagFilter == null,
                                    onClick = { viewModel.setCategoryFilter(cat.id); viewModel.setTagFilter(null) }
                                )
                            }
                        }
                        if (allTagSummaries.isNotEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(28.dp)
                                        .background(Hairline)
                                )
                            }
                            items(allTagSummaries) { tag ->
                                TagChipPill(
                                    label = tag.name,
                                    selected = tagFilter == tag.name,
                                    onClick = {
                                        if (tagFilter == tag.name) {
                                            viewModel.setTagFilter(null)
                                        } else {
                                            viewModel.setTagFilter(tag.name)
                                            viewModel.setCategoryFilter(-1L)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ── Expense list ────────────────────────────────────
            when {
                showIncome -> IncomeList(
                    groups = incomeGroups,
                    hasAny = hasAnyIncome,
                    showDayHeaders = !sortByAmount,
                    onEdit = { editingIncome = it },
                    onDelete = { incomeToDelete = it }
                )
                !hasAnyExpenses -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No expenses yet.\nSMS transactions will appear here automatically.",
                            color = Muted,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                expenses.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No expenses match your search.", color = Muted, fontSize = 13.sp)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        grouped.forEachIndexed { groupIdx, dayGroup ->
                            if (!sortByAmount) {
                                item(key = "header_${dayGroup.key}") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .padding(top = if (groupIdx == 0) 0.dp else 16.dp, bottom = 8.dp)
                                            .padding(horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${dayGroup.dayLabel}, ${dayGroup.dateLabel}".uppercase(),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.5.sp,
                                            letterSpacing = 1.6.sp,
                                            color = Muted,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "₹${fmtINR(dayGroup.total)}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = Muted
                                        )
                                    }
                                }
                            }
                            item(key = "card_${dayGroup.key}") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Paper)
                                ) {
                                    dayGroup.items.forEachIndexed { i, expense ->
                                        val category = categories.find { it.id == expense.categoryId }
                                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(HairlineSoft))
                                        ExpenseRowItem(
                                            expense = expense,
                                            category = category,
                                            selected = expense.id in selectedIds,
                                            onEdit = { editingExpense = expense },
                                            onDelete = { expenseToDelete = expense },
                                            onCategorize = { onCategorize(expense.id) },
                                            onSelect = { viewModel.toggleSelection(expense.id) },
                                            isSelectionMode = isSelectionMode
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddExpenseDialog(
            categories = categories,
            lastUsedCategoryId = lastManualCategoryId,
            startAsIncome = addAsIncome,
            onDismiss = { showAddDialog = false },
            onConfirm = { amount, description, categoryId, date, tags, paymentMethod ->
                viewModel.addExpense(amount, description, categoryId, date, tags, paymentMethod)
                showAddDialog = false
                viewModel.setShowIncome(false)
            },
            onConfirmIncome = { amount, description, date, tags ->
                viewModel.addIncome(amount, description, date, tags)
                showAddDialog = false
                // Show where it went, so it doesn't seem to vanish (the same for an expense above)
                viewModel.setShowIncome(true)
            }
        )
    }

    editingIncome?.let { income ->
        EditIncomeDialog(
            income = income,
            onDismiss = { editingIncome = null },
            onConfirm = { amount, description, date, tags ->
                viewModel.updateIncome(income, amount, description, date, tags)
                editingIncome = null
            },
            onDuplicate = { amount, description, date, tags, alsoSaved ->
                viewModel.duplicateIncome(income, amount, description, date, tags, saveEdits = alsoSaved)
                editingIncome = null
                val msg = if (alsoSaved) "Saved, and duplicated — the copy is dated today" else "Duplicated — dated today"
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    incomeToDelete?.let { income ->
        AppAlertDialog(
            onDismissRequest = { incomeToDelete = null },
            containerColor = Paper,
            titleContentColor = Ink,
            textContentColor = Muted,
            title = { Text("Delete income?") },
            text = { Text("\"${income.description.ifBlank { "Income" }}\" will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteIncome(income)
                    incomeToDelete = null
                }) { Text("Delete", color = Coral) }
            },
            dismissButton = {
                TextButton(onClick = { incomeToDelete = null }) { Text("Cancel", color = Muted) }
            }
        )
    }

    editingExpense?.let { expense ->
        EditExpenseDialog(
            expense = expense,
            categories = categories,
            onDismiss = { editingExpense = null },
            onConfirm = { amount, description, categoryId, date, tags, paymentMethod ->
                viewModel.updateExpense(expense, amount, description, categoryId, date, tags, paymentMethod)
                editingExpense = null
            },
            onDuplicate = { amount, description, categoryId, date, tags, paymentMethod, alsoSaved ->
                viewModel.duplicateExpense(expense, amount, description, categoryId, date, tags, paymentMethod, saveEdits = alsoSaved)
                editingExpense = null
                val msg = if (alsoSaved) "Saved, and duplicated — the copy is dated today" else "Duplicated — dated today"
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    expenseToDelete?.let { expense ->
        AppAlertDialog(
            onDismissRequest = { expenseToDelete = null },
            containerColor = Paper,
            titleContentColor = Ink,
            textContentColor = Muted,
            title = { Text("Delete expense?") },
            text = { Text("\"${expense.description}\" will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteExpense(expense)
                    expenseToDelete = null
                }) { Text("Delete", color = Coral) }
            },
            dismissButton = {
                TextButton(onClick = { expenseToDelete = null }) { Text("Cancel", color = Muted) }
            }
        )
    }

    if (showBulkCategorize) {
        BulkCategorizeDialog(
            categories = categories,
            onDismiss = { showBulkCategorize = false },
            onConfirm = { catId ->
                viewModel.bulkCategorize(catId)
                showBulkCategorize = false
            }
        )
    }

    if (showAdvancedFilters) {
        AdvancedFiltersDialog(
            minAmount = minAmount,
            maxAmount = maxAmount,
            dateFilter = dateFilter,
            onDismiss = { showAdvancedFilters = false },
            onConfirm = { min, max, filter ->
                viewModel.setMinAmount(min)
                viewModel.setMaxAmount(max)
                viewModel.setDateFilter(filter)
                showAdvancedFilters = false
            }
        )
    }
}

@Composable
private fun AdvancedFiltersDialog(
    minAmount: Double?,
    maxAmount: Double?,
    dateFilter: DateFilter?,
    onDismiss: () -> Unit,
    onConfirm: (Double?, Double?, DateFilter?) -> Unit
) {
    var minText by remember { mutableStateOf(formatLimitInput(minAmount ?: 0.0)) }
    var maxText by remember { mutableStateOf(formatLimitInput(maxAmount ?: 0.0)) }
    // Blank or 0 means no bound. Unreadable text used to drop the bound without a word.
    val min = rememberAmountText(minText) { parseLimitInput(it) }
    val max = rememberAmountText(maxText) { parseLimitInput(it) }
    val minValue = min.value
    val maxValue = max.value
    // An inverted range hid every expense without a word.
    val inverted = minValue != null && maxValue != null && minValue > 0 && maxValue > 0 && minValue > maxValue
    // Dates: ALL means no date filter. A preset stays a preset ("This month" moves on with the month).
    var preset by remember { mutableStateOf(dateFilter?.preset ?: ExportPreset.ALL) }
    var custom by remember { mutableStateOf(dateFilter?.custom) }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        title = { Text("Advanced Filters", color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Dates", style = MaterialTheme.typography.labelSmall, color = Muted)
                DatePresetOptions(preset, custom, pickerTitle = "Dates to show", allLabel = "Any time") { p, c ->
                    preset = p; custom = c
                }
                Text("Amount Range", style = MaterialTheme.typography.labelSmall, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { minText = it },
                        label = { Text("Min (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = min.invalid,
                        supportingText = min.supportingText,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedTextColor = Ink, unfocusedTextColor = Ink)
                    )
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = { maxText = it },
                        label = { Text("Max (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = max.invalid || inverted,
                        supportingText = if (inverted) {{ Text("Less than min") }} else max.supportingText,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedTextColor = Ink, unfocusedTextColor = Ink)
                    )
                }
            }
        },
        confirmButton = {
            val valid = minValue != null && maxValue != null && !inverted &&
                (preset != ExportPreset.CUSTOM || custom != null)
            TextButton(enabled = valid, onClick = {
                // "This month" and "Last month" are budget months, like the Overview.
                onConfirm(minValue?.takeIf { it > 0 }, maxValue?.takeIf { it > 0 },
                    if (preset == ExportPreset.ALL) null else DateFilter(preset, custom.takeIf { preset == ExportPreset.CUSTOM }))
            }) { Text("Apply", color = if (valid) Jade else Muted) }
        },
        dismissButton = {
            TextButton(onClick = {
                minText = ""; maxText = ""; onConfirm(null, null, null)
            }) { Text("Clear", color = Coral) }
        }
    )
}

@Composable
private fun BulkActionsToolbar(
    selectedCount: Int,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onCategorize: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(Jade)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        IconButton(onClick = onClear) {
            Icon(Icons.Default.Close, null, tint = Deep)
        }
        Text(
            text = "$selectedCount selected",
            style = MaterialTheme.typography.titleMedium,
            color = Deep,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onCategorize) {
            Icon(Icons.Default.Category, null, tint = Deep)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, null, tint = Deep)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BulkCategorizeDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id ?: -1L) }
    val selectedCategory = categories.find { it.id == selectedCategoryId }
    val bulkParents = remember(categories) { categories.filter { it.parentId == null } }
    val bulkChildrenMap = remember(categories) { categories.filter { it.parentId != null }.groupBy { it.parentId } }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        title = { Text("Move to Category", color = Ink) },
        text = {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = categoryDisplayText(selectedCategory, categories),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    bulkParents.forEach { parent ->
                        DropdownMenuItem(
                            text = { Text("${parent.emoji} ${parent.name}", fontWeight = FontWeight.SemiBold) },
                            onClick = { selectedCategoryId = parent.id; expanded = false }
                        )
                        bulkChildrenMap[parent.id]?.forEach { child ->
                            DropdownMenuItem(
                                text = {
                                    Row {
                                        Spacer(Modifier.width(16.dp))
                                        Text("↳ ${child.emoji} ${child.name}")
                                    }
                                },
                                onClick = { selectedCategoryId = child.id; expanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedCategoryId) }) {
                Text("Confirm", color = Jade)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) }
        }
    )
}

@Composable
private fun ChipPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(if (selected) Ink else Paper)
            .border(1.dp, if (selected) Color.Transparent else Hairline, RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) Canvas else InkSoft,
            letterSpacing = 0.1.sp
        )
    }
}

@Composable
private fun TagChipPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(if (selected) JadeSoft else Paper)
            .border(1.dp, if (selected) JadeInk.copy(alpha = 0.4f) else Hairline, RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            "#$label",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) JadeInk else Muted,
            letterSpacing = 0.1.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun BasicSearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontSize = 13.5.sp),
        singleLine = true,
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Jade),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text("Search merchants, notes…", color = Whisper, fontSize = 13.5.sp)
                }
                innerTextField()
            }
        }
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ExpenseRowItem(
    expense: Expense,
    category: Category?,
    selected: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCategorize: () -> Unit,
    onSelect: () -> Unit,
    isSelectionMode: Boolean
) {
    val isPending = expense.categoryId == -1L
    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(expense.date))
    val catColor = if (isPending) Coral else if (category != null) parseColor(category.colorHex) else Muted

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    selected -> Jade.copy(alpha = 0.15f)
                    isPending -> CoralSoft
                    else -> Color.Transparent
                }
            )
            .combinedClickable(
                onClick = { if (isSelectionMode) onSelect() else if (isPending) onCategorize() else onEdit() },
                onLongClick = onSelect
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Selection indicator or Category tile
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (selected) Jade 
                    else catColor.copy(alpha = if (isPending) 1f else 0.13f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(Icons.Default.Check, null, tint = Canvas, modifier = Modifier.size(20.dp))
            } else if (isPending) {
                Text(
                    "?",
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 18.sp,
                    color = Color.White
                )
            } else {
                Text(category?.emoji ?: "?", fontSize = 18.sp)
            }
        }

        // Description + meta
        Column(modifier = Modifier.weight(1f)) {
            Text(
                expense.description.ifBlank { "No description" }.take(40),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            if (isPending) {
                Text(
                    "Tap to categorize →",
                    fontSize = 11.sp,
                    color = Coral,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(timeStr, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, color = Muted)
                    Box(Modifier.size(3.dp).clip(CircleShape).background(Whisper))
                    Text(
                        (expense.source ?: "MANUAL").uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        color = Muted,
                        letterSpacing = 0.6.sp
                    )
                    if (category != null) {
                        Box(Modifier.size(3.dp).clip(CircleShape).background(Whisper))
                        Text(
                            category.name,
                            fontSize = 11.sp,
                            color = catColor,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                val parsedTags = ExpensesViewModel.parseTags(expense.tags)
                if (parsedTags.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 3.dp)
                    ) {
                        parsedTags.take(3).forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(JadeSoft)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    "#$tag",
                                    fontSize = 9.5.sp,
                                    color = JadeInk,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        if (parsedTags.size > 3) {
                            Text("+${parsedTags.size - 3}", fontSize = 9.5.sp, color = Muted, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        // Amount + actions
        Column(horizontalAlignment = Alignment.End) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 13.sp, fontStyle = FontStyle.Normal, color = (if (isPending) Coral else Ink).copy(alpha = 0.55f), fontFamily = FontFamily.Serif)) { append("₹") }
                    withStyle(SpanStyle(fontSize = 20.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = if (isPending) Coral else Ink)) { append(fmtINR(expense.amount)) }
                }
            )
            if (!isSelectionMode) {
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Muted, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Muted, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

// ── Dialogs ─────────────────────────────────────────────────────────────────

private fun categoryDisplayText(category: Category?, allCategories: List<Category>, placeholder: String = "Select category"): String {
    if (category == null) return placeholder
    return if (category.parentId != null) {
        val parent = allCategories.find { it.id == category.parentId }
        "${parent?.emoji ?: ""}${category.emoji} ${category.name}"
    } else {
        "${category.emoji} ${category.name}"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseDialog(
    categories: List<Category>,
    lastUsedCategoryId: Long,
    startAsIncome: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double, String, Long, Long, String, String) -> Unit,
    onConfirmIncome: (amount: Double, description: String, date: Long, tags: String) -> Unit
) {
    var isIncome by remember { mutableStateOf(startAsIncome) }
    val amount = rememberAmountInputState()
    var description by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    // null until the user picks one, so the last-used default can appear once categories load.
    var userCategoryChoice by remember { mutableStateOf<Long?>(null) }
    val selectedCategoryId = startingCategoryId(userCategoryChoice, categories, lastUsedCategoryId)
    var expanded by remember { mutableStateOf(false) }
    var selectedDateUtcMidnight by remember { mutableStateOf(toUtcMidnight(System.currentTimeMillis())) }
    var selectedPaymentMethod by remember { mutableStateOf("UPI") }
    val selectedCategory = categories.find { it.id == selectedCategoryId }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        title = { Text(if (isIncome) "Add Income" else "Add Expense", color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TwoWayToggle("Expense", "Income", rightSelected = isIncome, onSelect = { isIncome = it })
                AmountField(amount)
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Notes (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (e.g. #vacation, #gift)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // Income has no payment method or category
                if (!isIncome) {
                    Text("Payment Method", style = MaterialTheme.typography.labelSmall, color = Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("UPI", "Credit Card", "Cash").forEach { method ->
                            FilterChip(
                                selected = selectedPaymentMethod == method,
                                onClick = { selectedPaymentMethod = method },
                                label = { Text(method, fontSize = 12.sp) }
                            )
                        }
                    }
                    run {
                        val parents = remember(categories) { categories.filter { it.parentId == null } }
                        val childrenMap = remember(categories) { categories.filter { it.parentId != null }.groupBy { it.parentId } }
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                            OutlinedTextField(
                                value = categoryDisplayText(selectedCategory, categories, CHOOSE_LATER),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Category") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                // Saving without a category files it as pending, sorted later from the list.
                                DropdownMenuItem(
                                    text = { Text(CHOOSE_LATER, color = Muted) },
                                    onClick = { userCategoryChoice = -1L; expanded = false }
                                )
                                parents.forEach { parent ->
                                    DropdownMenuItem(
                                        text = { Text("${parent.emoji} ${parent.name}", fontWeight = FontWeight.SemiBold) },
                                        onClick = { userCategoryChoice = parent.id; expanded = false }
                                    )
                                    childrenMap[parent.id]?.forEach { child ->
                                        DropdownMenuItem(
                                            text = {
                                                Row {
                                                    Spacer(Modifier.width(16.dp))
                                                    Text("↳ ${child.emoji} ${child.name}")
                                                }
                                            },
                                            onClick = { userCategoryChoice = child.id; expanded = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                DatePickerField(
                    selectedUtcMidnight = selectedDateUtcMidnight,
                    onDateSelected = { selectedDateUtcMidnight = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amt = amount.validated() ?: return@TextButton
                    val date = buildTimestamp(selectedDateUtcMidnight, System.currentTimeMillis())
                    if (isIncome) return@TextButton onConfirmIncome(amt, description.trim(), date, tags.trim())
                    onConfirm(amt, description.trim(), selectedCategoryId, date, tags.trim(), selectedPaymentMethod)
                }
            ) { Text("Add", color = Jade) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditExpenseDialog(
    expense: Expense,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (Double, String, Long, Long, String, String) -> Unit,
    onDuplicate: (amount: Double, description: String, categoryId: Long, date: Long, tags: String, paymentMethod: String, alsoSaved: Boolean) -> Unit
) {
    // Locale-free: "%.2f" gave "180,00" on comma-decimal phones.
    val amount = rememberAmountInputState(AmountCalculator.forEditing(expense.amount))
    var description by remember { mutableStateOf(expense.description) }
    var tags by remember { mutableStateOf(expense.tags) }
    var selectedCategoryId by remember { mutableStateOf(expense.categoryId) }
    var expanded by remember { mutableStateOf(false) }
    var selectedDateUtcMidnight by remember { mutableStateOf(toUtcMidnight(expense.date)) }
    // Same day: keep the exact original time (buildTimestamp drops milliseconds, which made an
    // untouched SMS expense look edited). Another day: that day at the original time of day.
    val formDate = if (selectedDateUtcMidnight == toUtcMidnight(expense.date)) expense.date
        else buildTimestamp(selectedDateUtcMidnight, expense.date)
    var selectedPaymentMethod by remember { mutableStateOf(expense.paymentMethod) }
    val selectedCategory = categories.find { it.id == selectedCategoryId }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        title = { Text("Edit Expense", color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountField(amount)
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Notes (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (e.g. #vacation, #gift)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Payment Method", style = MaterialTheme.typography.labelSmall, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("UPI", "Credit Card", "Cash").forEach { method ->
                        FilterChip(
                            selected = selectedPaymentMethod == method,
                            onClick = { selectedPaymentMethod = method },
                            label = { Text(method, fontSize = 12.sp) }
                        )
                    }
                }
                val editParents = remember(categories) { categories.filter { it.parentId == null } }
                val editChildrenMap = remember(categories) { categories.filter { it.parentId != null }.groupBy { it.parentId } }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = categoryDisplayText(selectedCategory, categories, "Uncategorized"),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        editParents.forEach { parent ->
                            DropdownMenuItem(
                                text = { Text("${parent.emoji} ${parent.name}", fontWeight = FontWeight.SemiBold) },
                                onClick = { selectedCategoryId = parent.id; expanded = false }
                            )
                            editChildrenMap[parent.id]?.forEach { child ->
                                DropdownMenuItem(
                                    text = {
                                        Row {
                                            Spacer(Modifier.width(16.dp))
                                            Text("↳ ${child.emoji} ${child.name}")
                                        }
                                    },
                                    onClick = { selectedCategoryId = child.id; expanded = false }
                                )
                            }
                        }
                    }
                }
                DatePickerField(
                    selectedUtcMidnight = selectedDateUtcMidnight,
                    onDateSelected = { selectedDateUtcMidnight = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = amount.validated() ?: return@TextButton
                onConfirm(amt, description.trim(), selectedCategoryId, formDate, tags.trim(), selectedPaymentMethod)
            }) { Text("Save", color = Jade) }
        },
        dismissButton = {
            // With unsaved changes, Duplicate saves them to this expense too (and says so), so a
            // fix made here, like picking a category, isn't lost on the original.
            val edited = remember(amount.value.text, description, selectedCategoryId, tags, selectedPaymentMethod, formDate) {
                expense.isEditedTo(amount.value.text, description, selectedCategoryId, tags, selectedPaymentMethod, formDate)
            }
            Row {
                TextButton(onClick = {
                    val amt = amount.validated() ?: return@TextButton
                    onDuplicate(amt, description.trim(), selectedCategoryId, formDate, tags.trim(), selectedPaymentMethod, edited)
                }) { Text(if (edited) "Save & duplicate" else "Duplicate", color = Jade) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) }
            }
        }
    )
}

@Composable
private fun EditIncomeDialog(
    income: Income,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, description: String, date: Long, tags: String) -> Unit,
    onDuplicate: (amount: Double, description: String, date: Long, tags: String, alsoSaved: Boolean) -> Unit
) {
    val amount = rememberAmountInputState(AmountCalculator.forEditing(income.amount))
    var description by remember { mutableStateOf(income.description) }
    var tags by remember { mutableStateOf(income.tags) }
    var selectedDateUtcMidnight by remember { mutableStateOf(toUtcMidnight(income.date)) }
    // Same day keeps the exact time, as in EditExpenseDialog.
    val formDate = if (selectedDateUtcMidnight == toUtcMidnight(income.date)) income.date
        else buildTimestamp(selectedDateUtcMidnight, income.date)

    AppAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        title = { Text("Edit Income", color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountField(amount)
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Notes (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (e.g. #bonus)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DatePickerField(
                    selectedUtcMidnight = selectedDateUtcMidnight,
                    onDateSelected = { selectedDateUtcMidnight = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = amount.validated() ?: return@TextButton
                onConfirm(amt, description.trim(), formDate, tags.trim())
            }) { Text("Save", color = Jade) }
        },
        dismissButton = {
            val edited = remember(amount.value.text, description, tags, formDate) {
                income.isEditedTo(amount.value.text, description, tags, formDate)
            }
            Row {
                TextButton(onClick = {
                    val amt = amount.validated() ?: return@TextButton
                    onDuplicate(amt, description.trim(), formDate, tags.trim(), edited)
                }) { Text(if (edited) "Save & duplicate" else "Duplicate", color = Jade) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) }
            }
        }
    )
}

/** Two options in a pill, like Statistics' view switch. */
@Composable
private fun TwoWayToggle(left: String, right: String, rightSelected: Boolean, onSelect: (rightSelected: Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(Paper)
            .border(1.dp, Hairline, RoundedCornerShape(100.dp))
            .padding(3.dp)
    ) {
        listOf(false to left, true to right).forEach { (isRight, label) ->
            val selected = isRight == rightSelected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(if (selected) Ink else Color.Transparent)
                    .clickable { onSelect(isRight) }
                    .padding(horizontal = 18.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) Canvas else Muted)
            }
        }
    }
}

@Composable
private fun IncomeList(groups: List<DayGroup<Income>>, hasAny: Boolean, showDayHeaders: Boolean, onEdit: (Income) -> Unit, onDelete: (Income) -> Unit) {
    when {
        !hasAny -> Box(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
            Text(
                "No income yet.\nTap Add income, or add your salary as recurring income in Settings → Bills & Reminders.",
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        groups.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No income matches your search.", color = Muted, fontSize = 13.sp)
        }
        else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
            groups.forEachIndexed { groupIdx, dayGroup ->
                if (showDayHeaders) {
                    item(key = "income_header_${dayGroup.key}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(top = if (groupIdx == 0) 0.dp else 16.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${dayGroup.dayLabel}, ${dayGroup.dateLabel}".uppercase(), fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp, letterSpacing = 1.6.sp, color = Muted, fontWeight = FontWeight.Medium)
                            Text("+₹${fmtINR(dayGroup.total)}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Jade)
                        }
                    }
                }
                item(key = "income_card_${dayGroup.key}") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(18.dp)).background(Paper)
                    ) {
                        dayGroup.items.forEachIndexed { i, income ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(HairlineSoft))
                            IncomeRowItem(income, onEdit = { onEdit(income) }, onDelete = { onDelete(income) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IncomeRowItem(income: Income, onEdit: () -> Unit, onDelete: () -> Unit) {
    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(income.date))
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Jade.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) { Text(if (income.source == "Recurring") "🔁" else "💰", fontSize = 18.sp) }
        Column(modifier = Modifier.weight(1f)) {
            Text(income.description.ifBlank { "Income" }.take(40), fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(timeStr, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, color = Muted)
                Box(Modifier.size(3.dp).clip(CircleShape).background(Whisper))
                Text(income.source.uppercase(), fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, color = Muted, letterSpacing = 0.6.sp)
            }
            val parsedTags = ExpensesViewModel.parseTags(income.tags)
            if (parsedTags.isNotEmpty()) {
                Text(parsedTags.take(3).joinToString(" ") { "#$it" }, fontSize = 9.5.sp, color = JadeInk, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 13.sp, color = Jade.copy(alpha = 0.7f), fontFamily = FontFamily.Serif)) { append("+₹") }
                    withStyle(SpanStyle(fontSize = 20.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = Jade)) { append(fmtINR(income.amount)) }
                }
            )
            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Muted, modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Muted, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

private const val CHOOSE_LATER = "Choose later (pending)"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(selectedUtcMidnight: Long, onDateSelected: (Long) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val displayText = remember(selectedUtcMidnight) {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(selectedUtcMidnight))
    }
    OutlinedCard(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(18.dp), tint = Muted)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Date", style = MaterialTheme.typography.labelSmall, color = Muted)
                Text(displayText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = selectedUtcMidnight,
            selectableDates = PastOrTodayDates
        )
        AppDatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDateSelected(it) }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

/** Expenses or incomes by day, newest first (or one "Highest amount" group when sorting by amount). */
private fun <T> groupByDay(expenses: List<T>, sortByAmount: Boolean, dateOf: (T) -> Long, amountOf: (T) -> Double): List<DayGroup<T>> {
    if (expenses.isEmpty()) return emptyList()
    
    if (sortByAmount) {
        return listOf(
            DayGroup(
                key = "by_amount",
                dayLabel = "Highest",
                dateLabel = "Amount",
                items = expenses.sortedByDescending(amountOf),
                total = expenses.sumOf(amountOf)
            )
        )
    }

    val todayCal = Calendar.getInstance()
    val yesterdayCal = Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, -1) }
    fun isSameDay(c1: Calendar, c2: Calendar) =
        c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
        c1.get(Calendar.MONTH) == c2.get(Calendar.MONTH) &&
        c1.get(Calendar.DAY_OF_MONTH) == c2.get(Calendar.DAY_OF_MONTH)

    return expenses.groupBy { exp ->
        val c = Calendar.getInstance().apply { timeInMillis = dateOf(exp) }
        Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
    }.entries.sortedByDescending { (k, _) -> k.first * 10000 + k.second * 100 + k.third }
        .map { (key, items) ->
            val c = Calendar.getInstance().apply {
                set(Calendar.YEAR, key.first)
                set(Calendar.MONTH, key.second)
                set(Calendar.DAY_OF_MONTH, key.third)
            }
            val dayLabel = when {
                isSameDay(c, todayCal) -> "Today"
                isSameDay(c, yesterdayCal) -> "Yesterday"
                else -> SimpleDateFormat("EEE", Locale.getDefault()).format(c.time)
            }
            val dateLabel = SimpleDateFormat("d MMM", Locale.getDefault()).format(c.time)
            DayGroup(key = "${key.first}_${key.second}_${key.third}", dayLabel = dayLabel, dateLabel = dateLabel, items = items.sortedByDescending(dateOf), total = items.sumOf(amountOf))
        }
}

private fun weekStart(startsOnMonday: Boolean = true): Long {
    val cal = Calendar.getInstance()
    val target = if (startsOnMonday) Calendar.MONDAY else Calendar.SUNDAY
    val diff = (cal.get(Calendar.DAY_OF_WEEK) - target + 7) % 7
    cal.add(Calendar.DAY_OF_YEAR, -diff)
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun fmtINR(n: Double): String {
    if (n.isNaN() || n.isInfinite()) return "—"
    return NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply {
        maximumFractionDigits = 0; minimumFractionDigits = 0
    }.format(n.toLong())
}
