package com.example.expensetracker.ui.screens.settings

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.RecurringTemplate
import com.example.expensetracker.ui.theme.*
import com.example.expensetracker.ui.util.parseColor
import java.text.NumberFormat
import java.util.Locale

private fun fmtINR(amount: Double): String =
    NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply { maximumFractionDigits = 0 }.format(amount)

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val categories by viewModel.categories.collectAsState()
    val templates by viewModel.recurringTemplates.collectAsState()
    val privacyMode by viewModel.privacyMode.collectAsState()
    val biometricLockEnabled by viewModel.biometricLockEnabled.collectAsState()
    val widgetEnabled by viewModel.widgetEnabled.collectAsState()
    val budgetAlertsEnabled by viewModel.budgetAlertsEnabled.collectAsState()
    val weekStartsOnMonday by viewModel.weekStartsOnMonday.collectAsState()
    val captureStats by viewModel.captureStats.collectAsState()
    val monthlyIncome by viewModel.monthlyIncome.collectAsState()
    val snackbarMessage = viewModel.snackbarMessage
    val pendingImportUri = viewModel.pendingImportUri
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddRecurring by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var templateToDelete by remember { mutableStateOf<RecurringTemplate?>(null) }
    var showIncomeDialog by remember { mutableStateOf(false) }

    val activity = LocalContext.current as? Activity
    LaunchedEffect(privacyMode) {
        if (privacyMode) {
            activity?.window?.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

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

    Scaffold(
        containerColor = Canvas,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Canvas),
            contentPadding = PaddingValues(16.dp)
        ) {
            // ── Header ─────────────────────────────────────────
            item {
                Text(
                    "SYSTEM",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                    letterSpacing = 1.8.sp,
                    color = Muted,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Settings",
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 28.sp,
                    color = Ink,
                    lineHeight = 30.sp
                )
                Spacer(Modifier.height(24.dp))
            }

            // ── Auto-track Stats ───────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Paper)
                        .padding(20.dp)
                ) {
                    Text(
                        "AUTO-TRACKING STATS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        letterSpacing = 1.6.sp,
                        color = Muted
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatItem("Today", captureStats.today.toString())
                        StatItem("This Month", captureStats.thisMonth.toString())
                        StatItem("All Time", captureStats.allTime.toString())
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // ── Recurring Expenses ──────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECURRING",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 1.6.sp,
                        color = Muted
                    )
                    if (categories.isNotEmpty()) {
                        Text(
                            text = "Add +",
                            fontSize = 12.sp,
                            color = Jade,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { showAddRecurring = true }
                        )
                    }
                }
            }

            if (templates.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Paper)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No recurring expenses set up.",
                            fontSize = 12.sp,
                            color = Muted
                        )
                    }
                }
            } else {
                items(templates) { template ->
                    val category = categories.find { it.id == template.categoryId }
                    RecurringRow(
                        template = template,
                        category = category,
                        onToggle = { viewModel.toggleTemplate(template) },
                        onDelete = { templateToDelete = template }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ── Data Card ──────────────────────────────────────
            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "DATA",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 1.6.sp,
                    color = Muted,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Paper)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    SettingRow(
                        icon = { Icon(Icons.Default.CloudUpload, null, tint = Ink, modifier = Modifier.size(17.dp)) },
                        title = "Export backup",
                        subtitle = "Save expenses + categories as JSON",
                        onClick = { exportLauncher.launch("expense_backup.json") }
                    )
                    HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                    SettingRow(
                        icon = { Icon(Icons.Default.Restore, null, tint = Ink, modifier = Modifier.size(17.dp)) },
                        title = "Restore from backup",
                        subtitle = "Replace all data with a backup file",
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }
                    )
                    HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                    SettingRow(
                        icon = { Icon(Icons.Default.Delete, null, tint = Coral, modifier = Modifier.size(17.dp)) },
                        title = "Reset everything",
                        subtitle = "Delete all expenses, categories & templates",
                        danger = true,
                        onClick = { showResetDialog = true }
                    )
                }
            }

            // ── Preferences Card ───────────────────────────────
            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "PREFERENCES",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 1.6.sp,
                    color = Muted,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Paper)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    // Currency
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Currency", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Ink)
                        Text("₹ INR", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Muted, letterSpacing = 0.4.sp)
                    }
                    HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                    // Monthly Income
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showIncomeDialog = true }.padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Monthly Income", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Ink)
                        Text("₹${fmtINR(monthlyIncome)}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Jade, letterSpacing = 0.4.sp)
                    }
                    HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                    // Budget Alerts
                    ToggleRow(
                        title = "Budget alerts",
                        subtitle = "Get notified at 80% & 100% of limit",
                        checked = budgetAlertsEnabled,
                        onCheckedChange = { viewModel.setBudgetAlertsEnabled(it) }
                    )
                    HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                    // Week start
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.setWeekStartsOnMonday(!weekStartsOnMonday) }.padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Week starts on", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Ink)
                        Text(if (weekStartsOnMonday) "Monday" else "Sunday", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Jade, letterSpacing = 0.4.sp)
                    }
                    HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                    // Privacy Mode
                    ToggleRow(
                        title = "Privacy mode",
                        subtitle = "Hides screen in app switcher",
                        checked = privacyMode,
                        onCheckedChange = { viewModel.setPrivacyMode(it) }
                    )
                    HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                    // Biometric Lock
                    ToggleRow(
                        title = "Biometric Lock",
                        subtitle = "Require fingerprint or PIN",
                        checked = biometricLockEnabled,
                        onCheckedChange = { viewModel.setBiometricLockEnabled(it) }
                    )
                    HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                    // Home Screen Widget
                    ToggleRow(
                        title = "Home Screen Widget",
                        subtitle = "Show progress on home screen",
                        checked = widgetEnabled,
                        onCheckedChange = { viewModel.setWidgetEnabled(it) }
                    )
                }
            }

            item {
                Spacer(Modifier.height(32.dp))
                Text(
                    text = "Expense Tracker · v2.1",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Whisper,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    // ── Dialogs ──────────────────────────────────────────

    if (showIncomeDialog) {
        IncomeDialog(
            currentIncome = monthlyIncome,
            onDismiss = { showIncomeDialog = false },
            onConfirm = { viewModel.setMonthlyIncome(it); showIncomeDialog = false }
        )
    }

    if (showAddRecurring) {
        AddRecurringDialog(
            categories = categories,
            onDismiss = { showAddRecurring = false },
            onConfirm = { name, amount, catId ->
                viewModel.addTemplate(name, amount, catId)
                showAddRecurring = false
            }
        )
    }

    templateToDelete?.let { template ->
        AlertDialog(
            onDismissRequest = { templateToDelete = null },
            containerColor = Paper,
            titleContentColor = Ink,
            textContentColor = Muted,
            title = { Text("Delete recurring expense?") },
            text = { Text("\"${template.name}\" will no longer be added automatically each month.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteTemplate(template); templateToDelete = null }) {
                    Text("Delete", color = Coral)
                }
            },
            dismissButton = {
                TextButton(onClick = { templateToDelete = null }) { Text("Cancel", color = Muted) }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = Paper,
            titleContentColor = Ink,
            textContentColor = Muted,
            title = { Text("Reset everything?") },
            text = { Text("This will permanently delete all expenses, categories, and recurring templates. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.resetAll(); showResetDialog = false }) {
                    Text("Delete all", color = Coral)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel", color = Muted) }
            }
        )
    }

    if (pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelImport() },
            containerColor = Paper,
            titleContentColor = Ink,
            textContentColor = Muted,
            title = { Text("Replace all data?") },
            text = { Text("This will delete all your current expenses and categories, then restore from the backup file.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmImport() }) { Text("Restore", color = Jade) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelImport() }) { Text("Cancel", color = Muted) }
            }
        )
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(label, fontSize = 10.sp, color = Muted)
    }
}

@Composable
private fun RecurringRow(template: RecurringTemplate, category: Category?, onToggle: () -> Unit, onDelete: () -> Unit) {
    val catColor = category?.let { parseColor(it.colorHex) } ?: Muted
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Paper)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(catColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(category?.emoji ?: "🔁", fontSize = 18.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(template.name, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Ink)
            Text("₹${fmtINR(template.amount)}/mo", fontSize = 11.sp, color = Muted)
        }
        Switch(
            checked = template.enabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(checkedThumbColor = Ink, checkedTrackColor = Jade, uncheckedThumbColor = Ink, uncheckedTrackColor = Hairline)
        )
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Muted, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Ink)
            Text(subtitle, fontSize = 11.5.sp, color = Muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Ink, checkedTrackColor = Jade, uncheckedThumbColor = Ink, uncheckedTrackColor = Hairline)
        )
    }
}

@Composable
private fun SettingRow(icon: @Composable () -> Unit, title: String, subtitle: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(if (danger) CoralSoft else Canvas), contentAlignment = Alignment.Center) { icon() }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = if (danger) Coral else Ink)
            Text(subtitle, fontSize = 11.5.sp, color = Muted)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Whisper, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun IncomeDialog(currentIncome: Double, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember { mutableStateOf(if (currentIncome > 0) "%.0f".format(currentIncome) else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        titleContentColor = Ink,
        title = { Text("Set Monthly Income") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Income (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink)
            )
        },
        confirmButton = {
            TextButton(onClick = { text.toDoubleOrNull()?.let { onConfirm(it) } }) { Text("Save", color = Jade) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRecurringDialog(categories: List<Category>, onDismiss: () -> Unit, onConfirm: (String, Double, Long) -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id ?: -1L) }
    var expanded by remember { mutableStateOf(false) }
    val selectedCategory = categories.find { it.id == selectedCategoryId }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        titleContentColor = Ink,
        title = { Text("Add Recurring") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(value = selectedCategory?.let { "${it.emoji} ${it.name}" } ?: "Category", onValueChange = {}, readOnly = true, label = { Text("Category") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = Paper) {
                        categories.forEach { cat ->
                            DropdownMenuItem(text = { Text("${cat.emoji} ${cat.name}", color = Ink) }, onClick = { selectedCategoryId = cat.id; expanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { amount.toDoubleOrNull()?.let { onConfirm(name, it, selectedCategoryId) } }) { Text("Add", color = Jade) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }
    )
}
