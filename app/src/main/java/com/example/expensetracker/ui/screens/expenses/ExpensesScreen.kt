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
import com.example.expensetracker.ui.theme.*
import com.example.expensetracker.ui.util.parseColor
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private data class DayGroup(
    val dayLabel: String,
    val dateLabel: String,
    val items: List<Expense>
)

@Composable
fun ExpensesScreen(viewModel: ExpensesViewModel, onCategorize: (Long) -> Unit) {
    val context = LocalContext.current
    val expenses by viewModel.expenses.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val categoryFilter by viewModel.categoryFilter.collectAsState()
    val sortByAmount by viewModel.sortByAmount.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val minAmount by viewModel.minAmount.collectAsState()
    val maxAmount by viewModel.maxAmount.collectAsState()
    val hasAnyExpenses by viewModel.hasAnyExpenses.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }
    var showBulkCategorize by remember { mutableStateOf(false) }
    var showAdvancedFilters by remember { mutableStateOf(false) }

    val isSelectionMode = selectedIds.isNotEmpty()
    BackHandler(isSelectionMode) { viewModel.clearSelection() }

    val weekStartsOnMonday by viewModel.weekStartsOnMonday.collectAsState()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.syncWeekStart()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val weekTotal = remember(expenses, weekStartsOnMonday) {
        expenses.filter { it.date >= weekStart(weekStartsOnMonday) }.sumOf { it.amount }
    }
    val grouped = remember(expenses, sortByAmount) { groupByDay(expenses, sortByAmount) }

    Scaffold(
        containerColor = Canvas,
        floatingActionButton = {
            if (!isSelectionMode) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (categories.isEmpty()) Muted else Jade)
                        .clickable {
                            if (categories.isEmpty()) {
                                android.widget.Toast.makeText(context, "Please add a category first", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                showAddDialog = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add expense", tint = Deep, modifier = Modifier.size(26.dp))
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
                    if (weekTotal > 0) {
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
                                    withStyle(SpanStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Normal, fontSize = 13.sp, color = Ink.copy(alpha = 0.55f))) { append("₹") }
                                    withStyle(SpanStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 22.sp, color = Ink)) { append(fmtINR(weekTotal)) }
                                }
                            )
                        }
                    }
                }

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
                    if (searchQuery.isNotEmpty() || minAmount != null || maxAmount != null) {
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
                        tint = if (minAmount != null || maxAmount != null) Jade else Muted,
                        modifier = Modifier.size(16.dp).clickable { showAdvancedFilters = true }
                    )
                }

                // ── Filter chips ────────────────────────────────────
                if (categories.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        item {
                            ChipPill(
                                label = "All",
                                selected = categoryFilter == -1L || categoryFilter == null,
                                onClick = { viewModel.setCategoryFilter(-1L) }
                            )
                        }
                        items(categories) { cat ->
                            ChipPill(
                                label = cat.name,
                                selected = categoryFilter == cat.id,
                                onClick = { viewModel.setCategoryFilter(cat.id) }
                            )
                        }
                    }
                }
            }

            // ── Expense list ────────────────────────────────────
            when {
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
                                item(key = "header_${dayGroup.dayLabel}_${dayGroup.dateLabel}") {
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
                                            "₹${fmtINR(dayGroup.items.sumOf { it.amount })}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = Muted
                                        )
                                    }
                                }
                            }
                            item(key = "card_${dayGroup.dayLabel}_${dayGroup.dateLabel}") {
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
            onDismiss = { showAddDialog = false },
            onConfirm = { amount, description, categoryId, date ->
                viewModel.addExpense(amount, description, categoryId, date)
                showAddDialog = false
            }
        )
    }

    editingExpense?.let { expense ->
        EditExpenseDialog(
            expense = expense,
            categories = categories,
            onDismiss = { editingExpense = null },
            onConfirm = { amount, description, categoryId, date ->
                viewModel.updateExpense(expense, amount, description, categoryId, date)
                editingExpense = null
            }
        )
    }

    expenseToDelete?.let { expense ->
        AlertDialog(
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
            onDismiss = { showAdvancedFilters = false },
            onConfirm = { min, max ->
                viewModel.setMinAmount(min)
                viewModel.setMaxAmount(max)
                showAdvancedFilters = false
            }
        )
    }
}

@Composable
private fun AdvancedFiltersDialog(
    minAmount: Double?,
    maxAmount: Double?,
    onDismiss: () -> Unit,
    onConfirm: (Double?, Double?) -> Unit
) {
    var minText by remember { mutableStateOf(minAmount?.toString() ?: "") }
    var maxText by remember { mutableStateOf(maxAmount?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        title = { Text("Advanced Filters", color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Amount Range", style = MaterialTheme.typography.labelSmall, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { minText = it },
                        label = { Text("Min (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedTextColor = Ink, unfocusedTextColor = Ink)
                    )
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = { maxText = it },
                        label = { Text("Max (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedTextColor = Ink, unfocusedTextColor = Ink)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(minText.toDoubleOrNull(), maxText.toDoubleOrNull())
            }) { Text("Apply", color = Jade) }
        },
        dismissButton = {
            TextButton(onClick = {
                minText = ""; maxText = ""; onConfirm(null, null)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        title = { Text("Move to Category", color = Ink) },
        text = {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selectedCategory?.let { "${it.emoji} ${it.name}" } ?: "Select category",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text("${cat.emoji} ${cat.name}") },
                            onClick = { selectedCategoryId = cat.id; expanded = false }
                        )
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
                        Icon(Icons.Default.Edit, null, tint = Muted, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, null, tint = Muted, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

// ── Dialogs ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (Double, String, Long, Long) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id ?: -1L) }
    var expanded by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf("") }
    var selectedDateUtcMidnight by remember { mutableStateOf(toUtcMidnight(System.currentTimeMillis())) }
    val selectedCategory = categories.find { it.id == selectedCategoryId }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        title = { Text("Add Expense", color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (categories.isEmpty()) {
                    Text(
                        "⚠️ No categories yet. Go to the Categories tab to create one first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Coral
                    )
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it; amountError = "" },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = amountError.isNotEmpty(),
                    supportingText = if (amountError.isNotEmpty()) {{ Text(amountError) }} else null,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Notes (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (categories.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = selectedCategory?.let { "${it.emoji} ${it.name}" } ?: "Select category",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text("${cat.emoji} ${cat.name}") },
                                    onClick = { selectedCategoryId = cat.id; expanded = false }
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
            TextButton(
                enabled = categories.isNotEmpty(),
                onClick = {
                    val amt = amount.toDoubleOrNull()
                    if (amt == null || amt <= 0) { amountError = "Enter a valid amount"; return@TextButton }
                    if (selectedCategoryId == -1L && categories.isNotEmpty()) {
                        selectedCategoryId = categories.first().id
                    }
                    onConfirm(amt, description.trim(), selectedCategoryId, buildTimestamp(selectedDateUtcMidnight, System.currentTimeMillis()))
                }
            ) { Text("Add", color = if (categories.isNotEmpty()) Jade else Muted) }
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
    onConfirm: (Double, String, Long, Long) -> Unit
) {
    var amount by remember { mutableStateOf("%.2f".format(expense.amount)) }
    var description by remember { mutableStateOf(expense.description) }
    var selectedCategoryId by remember { mutableStateOf(expense.categoryId) }
    var expanded by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf("") }
    var selectedDateUtcMidnight by remember { mutableStateOf(toUtcMidnight(expense.date)) }
    val selectedCategory = categories.find { it.id == selectedCategoryId }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        title = { Text("Edit Expense", color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it; amountError = "" },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = amountError.isNotEmpty(),
                    supportingText = if (amountError.isNotEmpty()) {{ Text(amountError) }} else null,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Notes (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedCategory?.let { "${it.emoji} ${it.name}" } ?: "Uncategorized",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text("${cat.emoji} ${cat.name}") },
                                onClick = { selectedCategoryId = cat.id; expanded = false }
                            )
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
                val amt = amount.toDoubleOrNull()
                if (amt == null || amt <= 0) { amountError = "Enter a valid amount"; return@TextButton }
                onConfirm(amt, description.trim(), selectedCategoryId, buildTimestamp(selectedDateUtcMidnight, expense.date))
            }) { Text("Save", color = Jade) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }
    )
}

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
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
            }
        )
        DatePickerDialog(
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

private fun groupByDay(expenses: List<Expense>, sortByAmount: Boolean): List<DayGroup> {
    if (expenses.isEmpty()) return emptyList()
    
    if (sortByAmount) {
        return listOf(
            DayGroup(
                dayLabel = "Highest",
                dateLabel = "Amount",
                items = expenses.sortedByDescending { it.amount }
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
        val c = Calendar.getInstance().apply { timeInMillis = exp.date }
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
            DayGroup(dayLabel = dayLabel, dateLabel = dateLabel, items = items.sortedByDescending { it.date })
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

private fun toUtcMidnight(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun buildTimestamp(utcMidnight: Long, referenceMillis: Long): Long {
    val dateCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnight }
    val timeCal = Calendar.getInstance().apply { timeInMillis = referenceMillis }
    return Calendar.getInstance().apply {
        set(Calendar.YEAR, dateCal.get(Calendar.YEAR))
        set(Calendar.MONTH, dateCal.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, dateCal.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
        set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
        set(Calendar.SECOND, timeCal.get(Calendar.SECOND))
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun fmtINR(n: Double): String {
    if (n.isNaN() || n.isInfinite()) return "—"
    return NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply {
        maximumFractionDigits = 0; minimumFractionDigits = 0
    }.format(n.toLong())
}
