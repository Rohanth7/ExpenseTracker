package com.example.expensetracker.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.RecurringTemplate

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val categories by viewModel.categories.collectAsState()
    val templates by viewModel.recurringTemplates.collectAsState()
    val snackbarMessage = viewModel.snackbarMessage
    val pendingImportUri = viewModel.pendingImportUri
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportTo(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.requestImport(it) } }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    // Import confirmation dialog
    if (pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelImport() },
            title = { Text("Replace all data?") },
            text = { Text("This will delete all your current expenses and categories, then restore from the backup file. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmImport() }) {
                    Text("Restore", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { viewModel.cancelImport() }) { Text("Cancel") } }
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Backup section
            item {
                Text("Backup & Restore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Export your expenses and categories as a JSON file to keep a backup or move to a new phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { exportLauncher.launch("expense_backup.json") },
                                modifier = Modifier.weight(1f)
                            ) { Text("Export Backup") }
                            OutlinedButton(
                                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Import Backup") }
                        }
                    }
                }
            }

            // Recurring expenses section
            item {
                Spacer(Modifier.height(4.dp))
                Text("Recurring Expenses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            item {
                Text(
                    "These are added automatically as pending expenses at the start of each month.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(templates, key = { it.id }) { template ->
                RecurringTemplateItem(
                    template = template,
                    category = categories.find { it.id == template.categoryId },
                    onToggle = { viewModel.toggleTemplate(template) },
                    onDelete = { viewModel.deleteTemplate(template) }
                )
            }
            item {
                AddRecurringTemplateCard(
                    categories = categories,
                    onAdd = { name, amount, categoryId -> viewModel.addTemplate(name, amount, categoryId) }
                )
            }
        }
    }
}

@Composable
private fun RecurringTemplateItem(
    template: RecurringTemplate,
    category: Category?,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (template.enabled) MaterialTheme.colorScheme.surface
                             else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(category?.emoji ?: "🔁", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(template.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "₹${"%.2f".format(template.amount)}/month" +
                           (category?.let { " · ${it.name}" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = template.enabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRecurringTemplateCard(
    categories: List<Category>,
    onAdd: (String, Double, Long) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        onClick = { showDialog = true }
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Add recurring expense", color = MaterialTheme.colorScheme.primary)
        }
    }

    if (showDialog) {
        AddRecurringDialog(
            categories = categories,
            onDismiss = { showDialog = false },
            onConfirm = { name, amount, catId ->
                onAdd(name, amount, catId)
                showDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRecurringDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id ?: -1L) }
    var expanded by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf("") }
    var amountError by remember { mutableStateOf("") }
    val selectedCategory = categories.find { it.id == selectedCategoryId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Recurring Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = "" },
                    label = { Text("Name (e.g. Rent, Netflix)") },
                    singleLine = true,
                    isError = nameError.isNotEmpty(),
                    supportingText = if (nameError.isNotEmpty()) {{ Text(nameError) }} else null,
                    modifier = Modifier.fillMaxWidth()
                )
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
                var valid = true
                if (name.isBlank()) { nameError = "Name is required"; valid = false }
                val amt = amount.toDoubleOrNull()
                if (amt == null || amt <= 0) { amountError = "Enter a valid amount"; valid = false }
                if (valid) onConfirm(name.trim(), amt!!, selectedCategoryId)
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
