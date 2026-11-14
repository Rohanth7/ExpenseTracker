package com.example.expensetracker.ui.screens.expenses

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.budget.Budgets
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.db.entity.Income
import com.example.expensetracker.data.export.DateFilter
import com.example.expensetracker.data.export.ExportPreset
import com.example.expensetracker.data.period.BudgetPeriod
import com.example.expensetracker.ui.components.*
import com.example.expensetracker.ui.theme.*
import com.example.expensetracker.ui.util.AmountCalculator
import com.example.expensetracker.ui.util.AmountField
import com.example.expensetracker.ui.util.PastOrTodayDates
import com.example.expensetracker.ui.util.buildTimestamp
import com.example.expensetracker.ui.util.fmtINR
import com.example.expensetracker.ui.util.formatLimitInput
import com.example.expensetracker.ui.util.parseColor
import com.example.expensetracker.ui.util.parseLimitInput
import com.example.expensetracker.ui.util.rememberAmountInputState
import com.example.expensetracker.ui.util.rememberAmountText
import com.example.expensetracker.ui.util.rememberSaveableItem
import com.example.expensetracker.ui.util.toUtcMidnight
import java.text.SimpleDateFormat
import java.util.*

private data class DayGroup<T>(
    /** Unique per list (the date, with its year), for LazyColumn keys. */
    val key: String,
    /** "TODAY", "YESTERDAY" or "TUE 10 NOV". */
    val label: String,
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
        contentWindowInsets = NoInsets,
        floatingActionButton = {
            // Spending is added from the bottom bar's Add button.
            if (!isSelectionMode && showIncome) {
                Row(
                    modifier = Modifier
                        .height(56.dp)
                        .clip(ButtonShape)
                        .background(Gold)
                        .clickable { addAsIncome = true; showAddDialog = true }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // The text labels the button, so the icon needs no description of its own.
                    Icon(Icons.Default.Add, contentDescription = null, tint = Deep, modifier = Modifier.size(24.dp))
                    Text("Add income", fontSize = 15.sp, color = Deep, fontWeight = FontWeight.SemiBold)
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
                Column(Modifier.padding(horizontal = ScreenPadding)) {
                    ScreenHeader("Activity")
                    SegTabs(listOf("Spending", "Income"), if (showIncome) 1 else 0, onSelect = { viewModel.setShowIncome(it == 1) })

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 10.dp)
                            .clip(FieldShape)
                            .background(Field)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Search, null, tint = Muted, modifier = Modifier.size(20.dp))
                        BasicSearchField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            modifier = Modifier.weight(1f)
                        )
                        // The ✕ belongs to the search text and clears only that. Dates have their own
                        // chip below, amount limits the filters sheet's Clear, and the category and
                        // tag chips are untouched. Sort stays reachable while anything is filtered.
                        if (searchQuery.isNotEmpty()) {
                            Icon(Icons.Default.Close, "Clear search", tint = Muted, modifier = Modifier.size(18.dp).clickable { viewModel.setSearchQuery("") })
                        }
                        Icon(
                            Icons.Default.FilterList,
                            if (sortByAmount) "Sort by date" else "Sort by amount",
                            tint = if (sortByAmount) Gold else Muted,
                            modifier = Modifier.size(20.dp).clickable {
                                viewModel.toggleSortOrder()
                                val msg = if (!sortByAmount) "Sorting by Highest Amount" else "Sorting by Date"
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                        Icon(
                            Icons.Default.Tune,
                            "Filters",
                            tint = if (minAmount != null || maxAmount != null || dateRange != null) Gold else Muted,
                            modifier = Modifier.size(20.dp).clickable { showAdvancedFilters = true }
                        )
                    }

                    // Active date filter (from the filters sheet or an Insights "View all")
                    dateRange?.let { (from, to) ->
                        Row(
                            modifier = Modifier.padding(bottom = 10.dp)
                                .clip(CircleShape).background(Field)
                                .clickable { viewModel.clearDateRange() }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.CalendarToday, null, tint = Gold, modifier = Modifier.size(14.dp))
                            Text(BudgetPeriod.dayRangeLabel(from, to), fontSize = 13.sp, color = Ink)
                            Icon(Icons.Default.Close, contentDescription = "Clear dates", tint = Muted, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                val parentCategories = remember(categories) { categories.filter { it.parentId == null } }
                // Category and tag chips are spending's (income has no category, and tag rename/delete
                // covers expenses only), so they're hidden for income; search matches income tags.
                if (!showIncome && (parentCategories.isNotEmpty() || allTagSummaries.isNotEmpty())) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = ScreenPadding),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        if (parentCategories.isNotEmpty()) {
                            item {
                                Chip(
                                    "All",
                                    selected = (categoryFilter == -1L || categoryFilter == null) && tagFilter == null,
                                    onClick = { viewModel.setCategoryFilter(-1L); viewModel.setTagFilter(null) }
                                )
                            }
                            items(parentCategories) { cat ->
                                Chip(
                                    cat.name,
                                    selected = categoryFilter == cat.id && tagFilter == null,
                                    onClick = { viewModel.setCategoryFilter(cat.id); viewModel.setTagFilter(null) }
                                )
                            }
                        }
                        if (allTagSummaries.isNotEmpty()) {
                            item { Box(Modifier.width(1.dp).height(28.dp).background(Hairline)) }
                            items(allTagSummaries) { tag ->
                                Chip(
                                    "#${tag.name}",
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

                val shownWeekTotal = if (showIncome) weekIncome else weekTotal
                if (shownWeekTotal > 0) {
                    MCard(Modifier.padding(horizontal = ScreenPadding).padding(bottom = 4.dp), padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("This week", fontSize = 13.sp, color = Muted, modifier = Modifier.weight(1f))
                            Text(
                                if (showIncome) "+₹${fmtINR(shownWeekTotal)}" else "₹${fmtINR(shownWeekTotal)}",
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (showIncome) Mint else Ink
                            )
                        }
                    }
                }
            }

            when {
                showIncome -> IncomeList(
                    groups = incomeGroups,
                    hasAny = hasAnyIncome,
                    showDayHeaders = !sortByAmount,
                    onEdit = { editingIncome = it },
                    onDelete = { incomeToDelete = it }
                )
                !hasAnyExpenses -> EmptyText("No expenses yet.\nSMS transactions will appear here automatically.")
                expenses.isEmpty() -> EmptyText("No expenses match your search.")
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 80.dp)
                    ) {
                        grouped.forEach { dayGroup ->
                            if (!sortByAmount) {
                                item(key = "header_${dayGroup.key}") { DayHeader(dayGroup.label, "₹${fmtINR(dayGroup.total)}", Muted) }
                            }
                            item(key = "card_${dayGroup.key}") {
                                Column {
                                    dayGroup.items.forEachIndexed { i, expense ->
                                        val category = categories.find { it.id == expense.categoryId }
                                        if (i > 0) RowDivider()
                                        ExpenseRowItem(
                                            expense = expense,
                                            category = category,
                                            parent = category?.parentId?.let { id -> categories.find { it.id == id } },
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
        AddExpenseSheet(
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
        EditIncomeSheet(
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
        EditExpenseSheet(
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
        BulkCategorizeSheet(
            categories = categories,
            onDismiss = { showBulkCategorize = false },
            onConfirm = { catId ->
                viewModel.bulkCategorize(catId)
                showBulkCategorize = false
            }
        )
    }

    if (showAdvancedFilters) {
        AdvancedFiltersSheet(
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
private fun EmptyText(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Muted, fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DayHeader(label: String, total: String, totalColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = Muted)
        Text(total, fontSize = 13.sp, color = totalColor)
    }
}

@Composable
private fun AdvancedFiltersSheet(
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
    val valid = minValue != null && maxValue != null && !inverted &&
        (preset != ExportPreset.CUSTOM || custom != null)

    MSheet(onDismiss) {
        SheetTitle("Advanced Filters")
        FieldLabel("Dates")
        DatePresetOptions(preset, custom, pickerTitle = "Dates to show", allLabel = "Any time") { p, c ->
            preset = p; custom = c
        }
        Spacer(Modifier.height(10.dp))
        FieldLabel("Amount Range")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = minText,
                onValueChange = { minText = it },
                label = { Text("Min (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = min.invalid,
                supportingText = min.supportingText,
                modifier = Modifier.weight(1f),
                shape = FieldShape, colors = fieldColors()
            )
            OutlinedTextField(
                value = maxText,
                onValueChange = { maxText = it },
                label = { Text("Max (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = max.invalid || inverted,
                supportingText = if (inverted) {{ Text("Less than min") }} else max.supportingText,
                modifier = Modifier.weight(1f),
                shape = FieldShape, colors = fieldColors()
            )
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Apply", enabled = valid, onClick = {
            // "This month" and "Last month" are budget months, like the Overview.
            onConfirm(minValue?.takeIf { it > 0 }, maxValue?.takeIf { it > 0 },
                if (preset == ExportPreset.ALL) null else DateFilter(preset, custom.takeIf { preset == ExportPreset.CUSTOM }))
        })
        UnderButtonAction("Clear", Pink) { minText = ""; maxText = ""; onConfirm(null, null, null) }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = 13.sp, color = Muted, modifier = Modifier.padding(bottom = 6.dp))
}

/** A text action centred under a sheet's buttons: Pink for a destructive one. */
@Composable
private fun ColumnScope.UnderButtonAction(text: String, color: Color, onClick: () -> Unit) {
    TextAction(text, onClick, color = color, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp))
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
            .padding(horizontal = ScreenPadding, vertical = 12.dp)
            .height(56.dp)
            .clip(ButtonShape)
            .background(Gold)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onClear) {
            Icon(Icons.Default.Close, "Clear selection", tint = Deep)
        }
        Text(
            text = "$selectedCount selected",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
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

@Composable
private fun BulkCategorizeSheet(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var selectedCategoryId by rememberSaveable { mutableStateOf(categories.firstOrNull()?.id ?: -1L) }

    MSheet(onDismiss) {
        SheetTitle("Move to Category")
        CategoryDropdown(
            text = categoryDisplayText(categories.find { it.id == selectedCategoryId }, categories),
            categories = categories,
            pendingLabel = null,
            onSelect = { selectedCategoryId = it }
        )
        ButtonPair("Cancel", onDismiss, "Confirm", { onConfirm(selectedCategoryId) })
    }
}

@Composable
private fun BasicSearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = LocalTextStyle.current.copy(color = Ink, fontSize = 15.sp),
        singleLine = true,
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Gold),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text("Search merchants, notes…", color = Whisper, fontSize = 15.sp)
                }
                innerTextField()
            }
        }
    )
}

/** "Food › Coffee · UPI · #work": the category, how it was paid, and the first tag. */
internal fun expenseSubtitle(categoryName: String?, paymentMethod: String, tags: String): String =
    listOfNotNull(categoryName, paymentMethod.takeIf { it.isNotBlank() }, Tags.parse(tags).firstOrNull()?.let { "#$it" })
        .joinToString(" · ")

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ExpenseRowItem(
    expense: Expense,
    category: Category?,
    parent: Category?,
    selected: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCategorize: () -> Unit,
    onSelect: () -> Unit,
    isSelectionMode: Boolean
) {
    val isPending = expense.categoryId == -1L
    AccentRow(
        accent = when {
            selected -> Gold
            isPending -> Pink
            category != null -> parseColor(category.colorHex)
            else -> Muted
        },
        title = expense.description.ifBlank { "No description" }.take(40),
        subtitle = if (isPending) "Needs a category"
            else expenseSubtitle(category?.let { Budgets.name(it, parent) }, expense.paymentMethod, expense.tags),
        trailing = "",
        subtitleColor = if (isPending) Pink else Muted,
        modifier = Modifier
            .background(if (selected) Gold.copy(alpha = 0.15f) else Color.Transparent)
            .combinedClickable(
                onClick = { if (isSelectionMode) onSelect() else if (isPending) onCategorize() else onEdit() },
                onLongClick = onSelect
            ),
        actions = { AmountAndActions("₹${fmtINR(expense.amount)}", Ink, showActions = !isSelectionMode, onEdit, onDelete) }
    )
}

/** The amount with the row's edit and delete under it. */
@Composable
private fun AmountAndActions(amount: String, color: Color, showActions: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(horizontalAlignment = Alignment.End) {
        Text(amount, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
        if (showActions) {
            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Muted, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Muted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

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

/** The category picker of the add, edit and move sheets. [pendingLabel], when given, is a first option that files the expense as pending (-1). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(text: String, categories: List<Category>, pendingLabel: String?, onSelect: (Long) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val parents = remember(categories) { categories.filter { it.parentId == null } }
    val childrenMap = remember(categories) { categories.filter { it.parentId != null }.groupBy { it.parentId } }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
            shape = FieldShape, colors = fieldColors()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = Field) {
            if (pendingLabel != null) {
                DropdownMenuItem(
                    text = { Text(pendingLabel, color = Muted) },
                    onClick = { onSelect(-1L); expanded = false }
                )
            }
            parents.forEach { parent ->
                DropdownMenuItem(
                    text = { Text("${parent.emoji} ${parent.name}", fontWeight = FontWeight.SemiBold) },
                    onClick = { onSelect(parent.id); expanded = false }
                )
                childrenMap[parent.id]?.forEach { child ->
                    DropdownMenuItem(
                        text = {
                            Row {
                                Spacer(Modifier.width(16.dp))
                                Text("↳ ${child.emoji} ${child.name}")
                            }
                        },
                        onClick = { onSelect(child.id); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesAndTags(description: String, onDescription: (String) -> Unit, tags: String, onTags: (String) -> Unit, tagsLabel: String) {
    OutlinedTextField(
        value = description,
        onValueChange = onDescription,
        label = { Text("Notes (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = FieldShape, colors = fieldColors()
    )
    OutlinedTextField(
        value = tags,
        onValueChange = onTags,
        label = { Text(tagsLabel) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = FieldShape, colors = fieldColors()
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentMethodChips(selected: String, onSelect: (String) -> Unit) {
    Column {
        FieldLabel("Payment Method")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PAYMENT_METHODS.forEach { method -> Chip(method, selected = selected == method, onClick = { onSelect(method) }) }
        }
    }
}

@Composable
private fun AddExpenseSheet(
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
    var selectedDateUtcMidnight by rememberSaveable { mutableStateOf(toUtcMidnight(System.currentTimeMillis())) }
    var selectedPaymentMethod by rememberSaveable { mutableStateOf("UPI") }
    val selectedCategory = categories.find { it.id == selectedCategoryId }

    MSheet(onDismiss) {
        SheetTitle(if (isIncome) "Add Income" else "Add Expense")
        SegTabs(listOf("Expense", "Income"), if (isIncome) 1 else 0, onSelect = { isIncome = it == 1 })
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AmountField(amount)
            NotesAndTags(description, { description = it }, tags, { tags = it }, "Tags (e.g. #vacation, #gift)")
            // Income has no payment method or category
            if (!isIncome) {
                PaymentMethodChips(selectedPaymentMethod) { selectedPaymentMethod = it }
                // Saving without a category files it as pending, sorted later from the list.
                CategoryDropdown(
                    text = categoryDisplayText(selectedCategory, categories, CHOOSE_LATER),
                    categories = categories,
                    pendingLabel = CHOOSE_LATER,
                    onSelect = { userCategoryChoice = it }
                )
            }
            DatePickerField(
                selectedUtcMidnight = selectedDateUtcMidnight,
                onDateSelected = { selectedDateUtcMidnight = it }
            )
        }
        ButtonPair("Cancel", onDismiss, "Add", {
            val amt = amount.validated() ?: return@ButtonPair
            val date = buildTimestamp(selectedDateUtcMidnight, System.currentTimeMillis())
            if (isIncome) return@ButtonPair onConfirmIncome(amt, description.trim(), date, tags.trim())
            onConfirm(amt, description.trim(), selectedCategoryId, date, tags.trim(), selectedPaymentMethod)
        })
    }
}

@Composable
private fun EditExpenseSheet(
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
    var selectedDateUtcMidnight by rememberSaveable { mutableStateOf(toUtcMidnight(expense.date)) }
    // Same day: keep the exact original time (buildTimestamp drops milliseconds, which made an
    // untouched SMS expense look edited). Another day: that day at the original time of day.
    val formDate = if (selectedDateUtcMidnight == toUtcMidnight(expense.date)) expense.date
        else buildTimestamp(selectedDateUtcMidnight, expense.date)
    var selectedPaymentMethod by rememberSaveable { mutableStateOf(expense.paymentMethod) }
    val selectedCategory = categories.find { it.id == selectedCategoryId }
    // With unsaved changes, Duplicate saves them to this expense too (and says so), so a
    // fix made here, like picking a category, isn't lost on the original.
    val edited = remember(amount.value.text, description, selectedCategoryId, tags, selectedPaymentMethod, formDate) {
        expense.isEditedTo(amount.value.text, description, selectedCategoryId, tags, selectedPaymentMethod, formDate)
    }

    MSheet(onDismiss) {
        SheetTitle("Edit Expense")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AmountField(amount)
            NotesAndTags(description, { description = it }, tags, { tags = it }, "Tags (e.g. #vacation, #gift)")
            PaymentMethodChips(selectedPaymentMethod) { selectedPaymentMethod = it }
            // "Uncategorized" sends it back to pending, like the add sheet's "Choose later".
            CategoryDropdown(
                text = categoryDisplayText(selectedCategory, categories, UNCATEGORIZED),
                categories = categories,
                pendingLabel = UNCATEGORIZED,
                onSelect = { selectedCategoryId = it }
            )
            DatePickerField(
                selectedUtcMidnight = selectedDateUtcMidnight,
                onDateSelected = { selectedDateUtcMidnight = it }
            )
        }
        ButtonPair("Cancel", onDismiss, "Save", {
            val amt = amount.validated() ?: return@ButtonPair
            onConfirm(amt, description.trim(), selectedCategoryId, formDate, tags.trim(), selectedPaymentMethod)
        })
        UnderButtonAction(if (edited) "Save & duplicate" else "Duplicate", Gold) {
            val amt = amount.validated() ?: return@UnderButtonAction
            onDuplicate(amt, description.trim(), selectedCategoryId, formDate, tags.trim(), selectedPaymentMethod, edited)
        }
    }
}

@Composable
private fun EditIncomeSheet(
    income: Income,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, description: String, date: Long, tags: String) -> Unit,
    onDuplicate: (amount: Double, description: String, date: Long, tags: String, alsoSaved: Boolean) -> Unit
) {
    val amount = rememberAmountInputState(AmountCalculator.forEditing(income.amount))
    var description by rememberSaveable { mutableStateOf(income.description) }
    var tags by rememberSaveable { mutableStateOf(income.tags) }
    var selectedDateUtcMidnight by rememberSaveable { mutableStateOf(toUtcMidnight(income.date)) }
    // Same day keeps the exact time, as in EditExpenseSheet.
    val formDate = if (selectedDateUtcMidnight == toUtcMidnight(income.date)) income.date
        else buildTimestamp(selectedDateUtcMidnight, income.date)
    val edited = remember(amount.value.text, description, tags, formDate) {
        income.isEditedTo(amount.value.text, description, tags, formDate)
    }

    MSheet(onDismiss) {
        SheetTitle("Edit Income")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AmountField(amount)
            NotesAndTags(description, { description = it }, tags, { tags = it }, "Tags (e.g. #bonus)")
            DatePickerField(
                selectedUtcMidnight = selectedDateUtcMidnight,
                onDateSelected = { selectedDateUtcMidnight = it }
            )
        }
        ButtonPair("Cancel", onDismiss, "Save", {
            val amt = amount.validated() ?: return@ButtonPair
            onConfirm(amt, description.trim(), formDate, tags.trim())
        })
        UnderButtonAction(if (edited) "Save & duplicate" else "Duplicate", Gold) {
            val amt = amount.validated() ?: return@UnderButtonAction
            onDuplicate(amt, description.trim(), formDate, tags.trim(), edited)
        }
    }
}

@Composable
private fun IncomeList(groups: List<DayGroup<Income>>, hasAny: Boolean, showDayHeaders: Boolean, onEdit: (Income) -> Unit, onDelete: (Income) -> Unit) {
    when {
        !hasAny -> EmptyText("No income yet.\nTap Add income, or add your salary as recurring income in Plan → Bills & Reminders.")
        groups.isEmpty() -> EmptyText("No income matches your search.")
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 80.dp)
        ) {
            groups.forEach { dayGroup ->
                if (showDayHeaders) {
                    item(key = "income_header_${dayGroup.key}") { DayHeader(dayGroup.label, "+₹${fmtINR(dayGroup.total)}", Mint) }
                }
                item(key = "income_card_${dayGroup.key}") {
                    Column {
                        dayGroup.items.forEachIndexed { i, income ->
                            if (i > 0) RowDivider()
                            AccentRow(
                                accent = Mint,
                                title = income.description.ifBlank { "Income" }.take(40),
                                subtitle = expenseSubtitle(income.source, "", income.tags),
                                trailing = "",
                                onClick = { onEdit(income) },
                                actions = {
                                    AmountAndActions("+₹${fmtINR(income.amount)}", Mint, showActions = true, { onEdit(income) }, { onDelete(income) })
                                }
                            )
                        }
                    }
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
    FieldBox("Date", onClick = { showPicker = true }) {
        Text(displayText, fontSize = 15.sp, color = Ink)
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

/** Expenses or incomes by day, newest first (or one group when sorting by amount, shown without a header). */
private fun <T> groupByDay(expenses: List<T>, sortByAmount: Boolean, dateOf: (T) -> Long, amountOf: (T) -> Double): List<DayGroup<T>> {
    if (expenses.isEmpty()) return emptyList()

    if (sortByAmount) {
        return listOf(DayGroup(key = "by_amount", label = "HIGHEST AMOUNT", items = expenses.sortedByDescending(amountOf), total = expenses.sumOf(amountOf)))
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
            val label = when {
                isSameDay(c, todayCal) -> "TODAY"
                isSameDay(c, yesterdayCal) -> "YESTERDAY"
                else -> SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(c.time).uppercase()
            }
            DayGroup(key = "${key.first}_${key.second}_${key.third}", label = label, items = items.sortedByDescending(dateOf), total = items.sumOf(amountOf))
        }
}
