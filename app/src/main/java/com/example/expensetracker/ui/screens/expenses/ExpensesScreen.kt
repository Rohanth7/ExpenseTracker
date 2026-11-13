package com.example.expensetracker.ui.screens.expenses

import com.example.expensetracker.ui.util.fmtINR
import com.example.expensetracker.ui.util.rememberSaveableItem
import androidx.compose.runtime.saveable.rememberSaveable
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
    val incomes by viewModel.incomes.collectAsState()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    // Which type the add dialog starts on: the list on show, or Expense for the widget's "+".
    var addAsIncome by rememberSaveable { mutableStateOf(false) }
    var editingExpense by rememberSaveableItem(expenses) { it.id }
    var expenseToDelete by rememberSaveableItem(expenses) { it.id }
    var editingIncome by rememberSaveableItem(incomes) { it.id }
    var incomeToDelete by rememberSaveableItem(incomes) { it.id }
    var showBulkCategorize by rememberSaveable { mutableStateOf(false) }
    var confirmBulkDelete by rememberSaveable { mutableStateOf(false) }
    var showAdvancedFilters by rememberSaveable { mutableStateOf(false) }
    // The widget's "+": a one-shot request, consumed here so it can't fire twice. It replaces any
    // dialog left open (Edit, Delete, filters) or selection, so two dialogs never stack.
    LaunchedEffect(openAdd) {
        if (openAdd) {
            editingExpense = null; expenseToDelete = null; showBulkCategorize = false; confirmBulkDelete = false; showAdvancedFilters = false
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

    val weekTotal by viewModel.weekTotal.collectAsState()
    val grouped = remember(expenses, sortByAmount) { groupByDay(expenses, sortByAmount, { it.date }, { it.amount }) }
    val showIncome by viewModel.showIncome.collectAsState()
    val hasAnyIncome by viewModel.hasAnyIncome.collectAsState()
    val incomeGroups = remember(incomes, sortByAmount) { groupByDay(incomes, sortByAmount, { it.date }, { it.amount }) }
    val weekIncome by viewModel.weekIncome.collectAsState()


    Scaffold(
        containerColor = Canvas,
        floatingActionButton = {
            if (!isSelectionMode) {
                Row(
                    modifier = Modifier
                        .height(58.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Gold)
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
                    onDelete = { confirmBulkDelete = true },
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
                            fontFamily = AnekLatin,
                            fontSize = 10.5.sp,
                            letterSpacing = 1.8.sp,
                            color = Muted,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Expenses",
                            fontFamily = AnekLatin,
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
                                fontFamily = AnekLatin,
                                fontSize = 9.sp,
                                letterSpacing = 1.6.sp,
                                color = Muted
                            )
                            Text(
                                buildAnnotatedString {
                                    val color = if (showIncome) Gold else Ink
                                    withStyle(SpanStyle(fontFamily = AnekLatin, fontStyle = FontStyle.Normal, fontSize = 13.sp, color = color.copy(alpha = 0.55f))) { append(if (showIncome) "+₹" else "₹") }
                                    withStyle(SpanStyle(fontFamily = AnekLatin, fontSize = 22.sp, color = color)) { append(fmtINR(shownWeekTotal)) }
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
                    // The ✕ belongs to the search text and clears only that. Dates have their own
                    // chip below, amount limits the filters dialog's Clear, and the category and
                    // tag chips are untouched. Sort stays reachable while anything is filtered.
                    if (searchQuery.isNotEmpty()) {
                        Icon(Icons.Default.Close, "Clear search", tint = Muted, modifier = Modifier.size(14.dp).clickable { viewModel.setSearchQuery("") })
                    }
                    Icon(
                        Icons.Default.FilterList,
                        if (sortByAmount) "Sort by date" else "Sort by amount",
                        tint = if (sortByAmount) Gold else Muted,
                        modifier = Modifier.size(16.dp).clickable {
                            viewModel.toggleSortOrder()
                            val msg = if (!sortByAmount) "Sorting by Highest Amount" else "Sorting by Date"
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                    Icon(
                        Icons.Default.Tune,
                        "Filters",
                        tint = if (minAmount != null || maxAmount != null || dateRange != null) Gold else Muted,
                        modifier = Modifier.size(16.dp).clickable { showAdvancedFilters = true }
                    )
                }

                // ── Active date filter (from the filters dialog or a Statistics "View all") ──
                dateRange?.let { (from, to) ->
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(100.dp)).background(GoldSoft)
                            .clickable { viewModel.clearDateRange() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, null, tint = Gold, modifier = Modifier.size(13.dp))
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
                                    // "Food" and "food" are one tag, so one chip, lit for either
                                    selected = Tags.same(tagFilter, tag.name),
                                    onClick = {
                                        if (Tags.same(tagFilter, tag.name)) {
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
                                            fontFamily = AnekLatin,
                                            fontSize = 10.5.sp,
                                            letterSpacing = 1.6.sp,
                                            color = Muted,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "₹${fmtINR(dayGroup.total)}",
                                            fontFamily = AnekLatin,
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
                }) { Text("Delete", color = Pink) }
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
                }) { Text("Delete", color = Pink) }
            },
            dismissButton = {
                TextButton(onClick = { expenseToDelete = null }) { Text("Cancel", color = Muted) }
            }
        )
    }

    // Shown only while something is selected: a selection cleared elsewhere closes it for good,
    // so the next selection doesn't reopen it unasked.
    LaunchedEffect(selectedIds.isEmpty()) { if (selectedIds.isEmpty()) confirmBulkDelete = false }
    if (confirmBulkDelete && selectedIds.isNotEmpty()) {
        val count = selectedIds.size
        AppAlertDialog(
            onDismissRequest = { confirmBulkDelete = false },
            containerColor = Paper,
            titleContentColor = Ink,
            textContentColor = Muted,
            title = { Text(if (count == 1) "Delete 1 expense?" else "Delete $count expenses?") },
            text = { Text(if (count == 1) "It will be permanently removed." else "They will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.bulkDelete()
                    confirmBulkDelete = false
                }) { Text("Delete", color = Pink) }
            },
            dismissButton = {
                TextButton(onClick = { confirmBulkDelete = false }) { Text("Cancel", color = Muted) }
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
    var minText by rememberSaveable { mutableStateOf(formatLimitInput(minAmount ?: 0.0)) }
    var maxText by rememberSaveable { mutableStateOf(formatLimitInput(maxAmount ?: 0.0)) }
    // Blank or 0 means no bound. Unreadable text used to drop the bound without a word.
    val min = rememberAmountText(minText) { parseLimitInput(it) }
    val max = rememberAmountText(maxText) { parseLimitInput(it) }
    val minValue = min.value
    val maxValue = max.value
    // An inverted range hid every expense without a word.
    val inverted = minValue != null && maxValue != null && minValue > 0 && maxValue > 0 && minValue > maxValue
    // Dates: ALL means no date filter. A preset stays a preset ("This month" moves on with the month).
    var preset by rememberSaveable { mutableStateOf(dateFilter?.preset ?: ExportPreset.ALL) }
    var custom by rememberSaveable { mutableStateOf(dateFilter?.custom) }

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
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, focusedTextColor = Ink, unfocusedTextColor = Ink)
                    )
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = { maxText = it },
                        label = { Text("Max (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = max.invalid || inverted,
                        supportingText = if (inverted) {{ Text("Less than min") }} else max.supportingText,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, focusedTextColor = Ink, unfocusedTextColor = Ink)
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
            }) { Text("Apply", color = if (valid) Gold else Muted) }
        },
        dismissButton = {
            TextButton(onClick = {
                minText = ""; maxText = ""; onConfirm(null, null, null)
            }) { Text("Clear", color = Pink) }
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
            .background(Gold)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        IconButton(onClick = onClear) {
            Icon(Icons.Default.Close, "Clear selection", tint = Deep)
        }
        Text(
            text = "$selectedCount selected",
            style = MaterialTheme.typography.titleMedium,
            color = Deep,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onCategorize) {
            Icon(Icons.Default.Category, "Categorize selected", tint = Deep)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, "Delete selected", tint = Deep)
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
    var expanded by rememberSaveable { mutableStateOf(false) }
    var selectedCategoryId by rememberSaveable { mutableStateOf(categories.firstOrNull()?.id ?: -1L) }
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
                Text("Confirm", color = Gold)
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
            .background(if (selected) GoldSoft else Paper)
            .border(1.dp, if (selected) GoldInk.copy(alpha = 0.4f) else Hairline, RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            "#$label",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) GoldInk else Muted,
            letterSpacing = 0.1.sp,
            fontFamily = AnekLatin
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
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Gold),
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
    val catColor = if (isPending) Pink else if (category != null) parseColor(category.colorHex) else Muted

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    selected -> Gold.copy(alpha = 0.15f)
                    isPending -> PinkSoft
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
                    if (selected) Gold 
                    else catColor.copy(alpha = if (isPending) 1f else 0.13f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(Icons.Default.Check, null, tint = Canvas, modifier = Modifier.size(20.dp))
            } else if (isPending) {
                Text(
                    "?",
                    fontFamily = AnekLatin,
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
                    color = Pink,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(timeStr, fontFamily = AnekLatin, fontSize = 10.5.sp, color = Muted)
                    Box(Modifier.size(3.dp).clip(CircleShape).background(Whisper))
                    Text(
                        (expense.source ?: "MANUAL").uppercase(),
                        fontFamily = AnekLatin,
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
                val parsedTags = Tags.parse(expense.tags)
                if (parsedTags.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 3.dp)
                    ) {
                        parsedTags.take(3).forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(GoldSoft)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    "#$tag",
                                    fontSize = 9.5.sp,
                                    color = GoldInk,
                                    fontFamily = AnekLatin
                                )
                            }
                        }
                        if (parsedTags.size > 3) {
                            Text("+${parsedTags.size - 3}", fontSize = 9.5.sp, color = Muted, fontFamily = AnekLatin)
                        }
                    }
                }
            }
        }

        // Amount + actions
        Column(horizontalAlignment = Alignment.End) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 13.sp, fontStyle = FontStyle.Normal, color = (if (isPending) Pink else Ink).copy(alpha = 0.55f), fontFamily = AnekLatin)) { append("₹") }
                    withStyle(SpanStyle(fontSize = 20.sp, fontFamily = AnekLatin, color = if (isPending) Pink else Ink)) { append(fmtINR(expense.amount)) }
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

/** "Debit Card" comes from debit-card SMS (`SmsParser.detectPaymentMethod`); a plain string, no schema change. */
private val PAYMENT_METHODS = listOf("UPI", "Credit Card", "Debit Card", "Cash")

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
    var isIncome by rememberSaveable { mutableStateOf(startAsIncome) }
    val amount = rememberAmountInputState()
    var description by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }
    // null until the user picks one, so the last-used default can appear once categories load.
    var userCategoryChoice by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedCategoryId = startingCategoryId(userCategoryChoice, categories, lastUsedCategoryId)
    var expanded by rememberSaveable { mutableStateOf(false) }
    var selectedDateUtcMidnight by rememberSaveable { mutableStateOf(toUtcMidnight(System.currentTimeMillis())) }
    var selectedPaymentMethod by rememberSaveable { mutableStateOf("UPI") }
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PAYMENT_METHODS.forEach { method ->
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
            ) { Text("Add", color = Gold) }
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
    var description by rememberSaveable { mutableStateOf(expense.description) }
    var tags by rememberSaveable { mutableStateOf(expense.tags) }
    var selectedCategoryId by rememberSaveable { mutableStateOf(expense.categoryId) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var selectedDateUtcMidnight by rememberSaveable { mutableStateOf(toUtcMidnight(expense.date)) }
    // Same day: keep the exact original time (buildTimestamp drops milliseconds, which made an
    // untouched SMS expense look edited). Another day: that day at the original time of day.
    val formDate = if (selectedDateUtcMidnight == toUtcMidnight(expense.date)) expense.date
        else buildTimestamp(selectedDateUtcMidnight, expense.date)
    var selectedPaymentMethod by rememberSaveable { mutableStateOf(expense.paymentMethod) }
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PAYMENT_METHODS.forEach { method ->
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
                        value = categoryDisplayText(selectedCategory, categories, UNCATEGORIZED),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        // Back to pending, like the add dialog's "Choose later".
                        DropdownMenuItem(
                            text = { Text(UNCATEGORIZED, color = Muted) },
                            onClick = { selectedCategoryId = -1L; expanded = false }
                        )
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
            }) { Text("Save", color = Gold) }
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
                }) { Text(if (edited) "Save & duplicate" else "Duplicate", color = Gold) }
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
    var description by rememberSaveable { mutableStateOf(income.description) }
    var tags by rememberSaveable { mutableStateOf(income.tags) }
    var selectedDateUtcMidnight by rememberSaveable { mutableStateOf(toUtcMidnight(income.date)) }
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
            }) { Text("Save", color = Gold) }
        },
        dismissButton = {
            val edited = remember(amount.value.text, description, tags, formDate) {
                income.isEditedTo(amount.value.text, description, tags, formDate)
            }
            Row {
                TextButton(onClick = {
                    val amt = amount.validated() ?: return@TextButton
                    onDuplicate(amt, description.trim(), formDate, tags.trim(), edited)
                }) { Text(if (edited) "Save & duplicate" else "Duplicate", color = Gold) }
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
                            Text("${dayGroup.dayLabel}, ${dayGroup.dateLabel}".uppercase(), fontFamily = AnekLatin,
                                fontSize = 10.5.sp, letterSpacing = 1.6.sp, color = Muted, fontWeight = FontWeight.Medium)
                            Text("+₹${fmtINR(dayGroup.total)}", fontFamily = AnekLatin, fontSize = 11.sp, color = Gold)
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
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Gold.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) { Text(if (income.source == "Recurring") "🔁" else "💰", fontSize = 18.sp) }
        Column(modifier = Modifier.weight(1f)) {
            Text(income.description.ifBlank { "Income" }.take(40), fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(timeStr, fontFamily = AnekLatin, fontSize = 10.5.sp, color = Muted)
                Box(Modifier.size(3.dp).clip(CircleShape).background(Whisper))
                Text(income.source.uppercase(), fontFamily = AnekLatin, fontSize = 9.5.sp, color = Muted, letterSpacing = 0.6.sp)
            }
            val parsedTags = Tags.parse(income.tags)
            if (parsedTags.isNotEmpty()) {
                Text(parsedTags.take(3).joinToString(" ") { "#$it" }, fontSize = 9.5.sp, color = GoldInk, fontFamily = AnekLatin,
                    modifier = Modifier.padding(top = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 13.sp, color = Gold.copy(alpha = 0.7f), fontFamily = AnekLatin)) { append("+₹") }
                    withStyle(SpanStyle(fontSize = 20.sp, fontFamily = AnekLatin, color = Gold)) { append(fmtINR(income.amount)) }
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
private const val UNCATEGORIZED = "Uncategorized (pending)"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(selectedUtcMidnight: Long, onDateSelected: (Long) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
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

