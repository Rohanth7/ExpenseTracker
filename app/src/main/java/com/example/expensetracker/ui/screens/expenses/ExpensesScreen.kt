package com.example.expensetracker.ui.screens.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.ui.util.parseColor
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ExpensesScreen(viewModel: ExpensesViewModel, onCategorize: (Long) -> Unit) {
    val expenses by viewModel.expenses.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val categoryFilter by viewModel.categoryFilter.collectAsState()
    val hasAnyExpenses by viewModel.hasAnyExpenses.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add expense")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search expenses...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp)
            )

            // Category filter chips
            if (categories.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = categoryFilter == -1L || categoryFilter == null,
                            onClick = { viewModel.setCategoryFilter(-1L) },
                            label = { Text("All") }
                        )
                    }
                    items(categories) { cat ->
                        FilterChip(
                            selected = categoryFilter == cat.id,
                            onClick = { viewModel.setCategoryFilter(cat.id) },
                            label = { Text("${cat.emoji} ${cat.name}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = parseColor(cat.colorHex).copy(alpha = 0.25f),
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }

            when {
                !hasAnyExpenses -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No expenses yet.\nSMS transactions will appear here automatically.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                expenses.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No expenses match your search.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(expenses, key = { it.id }) { expense ->
                            val category = categories.find { it.id == expense.categoryId }
                            ExpenseItem(
                                expense = expense,
                                category = category,
                                onDelete = { viewModel.deleteExpense(expense) },
                                onEdit = { editingExpense = expense },
                                onCategorize = { onCategorize(expense.id) }
                            )
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
            onConfirm = { amount, description, categoryId ->
                viewModel.addExpense(amount, description, categoryId)
                showAddDialog = false
            }
        )
    }

    editingExpense?.let { expense ->
        EditExpenseDialog(
            expense = expense,
            categories = categories,
            onDismiss = { editingExpense = null },
            onConfirm = { amount, description, categoryId ->
                viewModel.updateExpense(expense, amount, description, categoryId)
                editingExpense = null
            }
        )
    }
}

@Composable
private fun ExpenseItem(
    expense: Expense,
    category: Category?,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onCategorize: () -> Unit
) {
    val isPending = expense.categoryId == -1L
    val dateStr = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(expense.date))

    if (isPending) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            onClick = onCategorize
        ) { ExpenseItemContent(expense, category, isPending, dateStr, onEdit, onDelete) }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) { ExpenseItemContent(expense, category, isPending, dateStr, onEdit, onDelete) }
    }
}

@Composable
private fun ExpenseItemContent(
    expense: Expense,
    category: Category?,
    isPending: Boolean,
    dateStr: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column {
        if (isPending) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 12.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("⚠ UNCATEGORIZED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onError)
                Text("Tap to categorize →", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onError)
            }
        }
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(
                    if (isPending) MaterialTheme.colorScheme.error
                    else if (category != null) parseColor(category.colorHex)
                    else MaterialTheme.colorScheme.outline
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isPending) "?" else category?.emoji ?: "?",
                    fontSize = 18.sp,
                    color = if (isPending) MaterialTheme.colorScheme.onError else Color.Unspecified
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.description.take(40),
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isPending) MaterialTheme.colorScheme.onErrorContainer else Color.Unspecified
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPending) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val sourceLabel = when (expense.source) { "SMS" -> "SMS"; "UPI" -> "UPI"; "Recurring" -> "🔁"; else -> null }
                    sourceLabel?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPending) MaterialTheme.colorScheme.onErrorContainer
                                    else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (!isPending && category != null) {
                    Text(text = category.name, style = MaterialTheme.typography.labelSmall, color = parseColor(category.colorHex))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₹${"%.2f".format(expense.amount)}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isPending) MaterialTheme.colorScheme.error else Color.Unspecified
                )
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            modifier = Modifier.size(16.dp),
                            tint = if (isPending) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(16.dp),
                            tint = if (isPending) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (Double, String, Long) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id ?: -1L) }
    var expanded by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf("") }
    val selectedCategory = categories.find { it.id == selectedCategoryId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (categories.isEmpty()) {
                    Text(
                        "⚠️ No categories yet. Go to the Categories tab to create one first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
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
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = amount.toDoubleOrNull()
                if (amt == null || amt <= 0) { amountError = "Enter a valid amount"; return@TextButton }
                onConfirm(amt, description.trim(), selectedCategoryId)
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditExpenseDialog(
    expense: Expense,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (Double, String, Long) -> Unit
) {
    var amount by remember { mutableStateOf("%.2f".format(expense.amount)) }
    var description by remember { mutableStateOf(expense.description) }
    var selectedCategoryId by remember { mutableStateOf(expense.categoryId) }
    var expanded by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf("") }
    val selectedCategory = categories.find { it.id == selectedCategoryId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Expense") },
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
                        DropdownMenuItem(
                            text = { Text("Uncategorized") },
                            onClick = { selectedCategoryId = -1L; expanded = false }
                        )
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text("${cat.emoji} ${cat.name}") },
                                onClick = { selectedCategoryId = cat.id; expanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = amount.toDoubleOrNull()
                if (amt == null || amt <= 0) { amountError = "Enter a valid amount"; return@TextButton }
                onConfirm(amt, description.trim(), selectedCategoryId)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
