package com.example.expensetracker.ui.screens.settings

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
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
import com.example.expensetracker.data.db.entity.Bill
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Loan
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
    val bills by viewModel.bills.collectAsState()
    val loans by viewModel.loans.collectAsState()
    val privacyMode by viewModel.privacyMode.collectAsState()
    val biometricLockEnabled by viewModel.biometricLockEnabled.collectAsState()
    val widgetEnabled by viewModel.widgetEnabled.collectAsState()
    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsState()
    val driveConnected by viewModel.driveConnected.collectAsState()
    val driveAccountEmail by viewModel.driveAccountEmail.collectAsState()
    val budgetAlertsEnabled by viewModel.budgetAlertsEnabled.collectAsState()
    val weekStartsOnMonday by viewModel.weekStartsOnMonday.collectAsState()
    val captureStats by viewModel.captureStats.collectAsState()
    val monthlyIncome by viewModel.monthlyIncome.collectAsState()
    val snackbarMessage = viewModel.snackbarMessage
    val pendingImportUri = viewModel.pendingImportUri
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddRecurring by remember { mutableStateOf(false) }
    var showAddBill by remember { mutableStateOf(false) }
    var showAddLoan by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showMockDataDialog by remember { mutableStateOf(false) }
    var templateToDelete by remember { mutableStateOf<RecurringTemplate?>(null) }
    var billToDelete by remember { mutableStateOf<Bill?>(null) }
    var loanToDelete by remember { mutableStateOf<Loan?>(null) }
    var showIncomeDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? Activity
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

    val driveSignInOptions = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.appdata"))
            .build()
    }
    val driveSignInClient = remember { GoogleSignIn.getClient(context, driveSignInOptions) }
    val driveSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                account?.email?.let { viewModel.setDriveAccount(it) }
            } catch (_: ApiException) {
                // sign-in failed — user cancelled or no network
            }
        }
    }

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

            // ── Bills ──────────────────────────────────────────
            item {
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BILLS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 1.6.sp,
                        color = Muted
                    )
                    Text(
                        text = "Add +",
                        fontSize = 12.sp,
                        color = Jade,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { showAddBill = true }
                    )
                }
            }

            if (bills.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Paper)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No bills set up yet.\nTap Add + to track rent, EMI, subscriptions.", fontSize = 12.sp, color = Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 18.sp)
                    }
                }
            } else {
                items(bills) { bill ->
                    val category = categories.find { it.id == bill.categoryId }
                    BillRow(
                        bill = bill,
                        category = category,
                        onToggle = { viewModel.toggleBill(bill) },
                        onDelete = { billToDelete = bill }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ── Loans ──────────────────────────────────────────
            item {
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LOANS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 1.6.sp,
                        color = Muted
                    )
                    Text(
                        text = "Add +",
                        fontSize = 12.sp,
                        color = Jade,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { showAddLoan = true }
                    )
                }
            }

            if (loans.isEmpty()) {
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
                            "No loans tracked.\nTap Add + to monitor EMI progress.",
                            fontSize = 12.sp,
                            color = Muted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                items(loans) { loan ->
                    LoanRow(
                        loan = loan,
                        onDelete = { loanToDelete = loan }
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
                        icon = { Icon(Icons.Default.Science, null, tint = Jade, modifier = Modifier.size(17.dp)) },
                        title = "Load mock data",
                        subtitle = "Fill app with 3 months of sample expenses for testing",
                        onClick = { showMockDataDialog = true }
                    )
                    HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                    SettingRow(
                        icon = { Icon(Icons.Default.Delete, null, tint = Coral, modifier = Modifier.size(17.dp)) },
                        title = "Reset everything",
                        subtitle = "Delete all expenses, categories & templates",
                        danger = true,
                        onClick = { showResetDialog = true }
                    )
                    HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                    ToggleRow(
                        title = "Automatic Cloud Backup",
                        subtitle = "Daily sync to secure storage",
                        checked = autoBackupEnabled,
                        onCheckedChange = { viewModel.setAutoBackupEnabled(it) }
                    )
                }
            }

            // ── Cloud Card ─────────────────────────────────────
            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "CLOUD",
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
                        .padding(20.dp)
                ) {
                    if (driveConnected) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(JadeSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Cloud, null, tint = JadeInk, modifier = Modifier.size(20.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Google Drive", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Ink)
                                Text(driveAccountEmail, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Muted)
                            }
                            TextButton(
                                onClick = {
                                    driveSignInClient.signOut()
                                    viewModel.disconnectDrive()
                                }
                            ) {
                                Text("Disconnect", color = Coral, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Backup syncs automatically every week when Auto Cloud Backup is enabled.",
                            fontSize = 11.sp,
                            color = Muted,
                            lineHeight = 16.sp
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Canvas),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CloudOff, null, tint = Muted, modifier = Modifier.size(20.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Google Drive", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Ink)
                                Text("Not connected", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Muted)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Connect your Google account to automatically sync encrypted backups to your private Drive App Data folder.",
                            fontSize = 11.sp,
                            color = Muted,
                            lineHeight = 16.sp
                        )
                        Spacer(Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Jade)
                                .clickable { driveSignInLauncher.launch(driveSignInClient.signInIntent) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Connect Google Drive",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Deep
                            )
                        }
                    }
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

    if (showAddLoan) {
        AddLoanDialog(
            onDismiss = { showAddLoan = false },
            onConfirm = { name, emoji, total, emi, tenure, startDate, dueDay ->
                viewModel.addLoan(name, emoji, total, emi, tenure, startDate, dueDay)
                showAddLoan = false
            }
        )
    }

    loanToDelete?.let { loan ->
        AlertDialog(
            onDismissRequest = { loanToDelete = null },
            containerColor = Paper,
            titleContentColor = Ink,
            textContentColor = Muted,
            title = { Text("Delete loan?") },
            text = { Text("\"${loan.name}\" will be removed from your EMI tracker.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteLoan(loan); loanToDelete = null }) {
                    Text("Delete", color = Coral)
                }
            },
            dismissButton = {
                TextButton(onClick = { loanToDelete = null }) { Text("Cancel", color = Muted) }
            }
        )
    }

    if (showAddBill) {
        AddBillDialog(
            categories = categories,
            onDismiss = { showAddBill = false },
            onConfirm = { name, amount, day, reminderDays, catId ->
                viewModel.addBill(name, amount, day, reminderDays, catId)
                showAddBill = false
            }
        )
    }

    billToDelete?.let { bill ->
        AlertDialog(
            onDismissRequest = { billToDelete = null },
            containerColor = Paper,
            titleContentColor = Ink,
            textContentColor = Muted,
            title = { Text("Delete bill?") },
            text = { Text("\"${bill.name}\" will be removed from your bill reminders.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteBill(bill); billToDelete = null }) {
                    Text("Delete", color = Coral)
                }
            },
            dismissButton = {
                TextButton(onClick = { billToDelete = null }) { Text("Cancel", color = Muted) }
            }
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

    if (showMockDataDialog) {
        AlertDialog(
            onDismissRequest = { showMockDataDialog = false },
            containerColor = Paper,
            titleContentColor = Ink,
            textContentColor = Muted,
            title = { Text("Load mock data?") },
            text = { Text("This will replace all existing data with 3 months of sample expenses, categories, bills, a loan, and savings goals.") },
            confirmButton = {
                TextButton(onClick = { viewModel.loadMockData(); showMockDataDialog = false }) {
                    Text("Load", color = Jade)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMockDataDialog = false }) { Text("Cancel", color = Muted) }
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
private fun BillRow(
    bill: Bill,
    category: Category?,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val ordinal = when {
        bill.dueDayOfMonth in 11..13 -> "${bill.dueDayOfMonth}th"
        bill.dueDayOfMonth % 10 == 1 -> "${bill.dueDayOfMonth}st"
        bill.dueDayOfMonth % 10 == 2 -> "${bill.dueDayOfMonth}nd"
        bill.dueDayOfMonth % 10 == 3 -> "${bill.dueDayOfMonth}rd"
        else -> "${bill.dueDayOfMonth}th"
    }
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
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (category != null) parseColor(category.colorHex).copy(alpha = 0.15f) else Muted.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text(category?.emoji ?: "📋", fontSize = 18.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(bill.name, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Ink)
            Text(
                "₹${fmtINR(bill.amount)} · due $ordinal · remind ${bill.reminderDays}d before",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = Muted
            )
        }
        Switch(
            checked = bill.isEnabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(checkedThumbColor = Ink, checkedTrackColor = Jade, uncheckedThumbColor = Ink, uncheckedTrackColor = Hairline)
        )
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Muted, modifier = Modifier.size(18.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBillDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Int, Int, Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dueDay by remember { mutableStateOf("") }
    var reminderDays by remember { mutableStateOf("3") }
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id ?: -1L) }
    var expanded by remember { mutableStateOf(false) }
    val selectedCategory = categories.find { it.id == selectedCategoryId }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        titleContentColor = Ink,
        title = { Text("Add Bill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Bill name (e.g. Rent, Netflix)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink)
                    )
                    OutlinedTextField(
                        value = dueDay,
                        onValueChange = { if (it.length <= 2) dueDay = it.filter { c -> c.isDigit() } },
                        label = { Text("Due day") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.7f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink)
                    )
                }
                OutlinedTextField(
                    value = reminderDays,
                    onValueChange = { reminderDays = it.filter { c -> c.isDigit() } },
                    label = { Text("Remind N days before") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink)
                )
                if (categories.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = selectedCategory?.let { "${it.emoji} ${it.name}" } ?: "Category (optional)",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink)
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = Paper) {
                            categories.forEach { cat ->
                                DropdownMenuItem(text = { Text("${cat.emoji} ${cat.name}", color = Ink) }, onClick = { selectedCategoryId = cat.id; expanded = false })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = amount.toDoubleOrNull() ?: return@TextButton
                val day = dueDay.toIntOrNull()?.coerceIn(1, 31) ?: return@TextButton
                val remind = reminderDays.toIntOrNull()?.coerceIn(0, 14) ?: 3
                if (name.isNotBlank()) onConfirm(name.trim(), amt, day, remind, selectedCategoryId)
            }) { Text("Add", color = Jade) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }
    )
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

@Composable
private fun LoanRow(loan: Loan, onDelete: () -> Unit) {
    val paidMonths = run {
        val now = java.util.Calendar.getInstance()
        val startCal = java.util.Calendar.getInstance().apply { timeInMillis = loan.startDate }
        var elapsed = (now.get(java.util.Calendar.YEAR) - startCal.get(java.util.Calendar.YEAR)) * 12 +
            (now.get(java.util.Calendar.MONTH) - startCal.get(java.util.Calendar.MONTH))
        if (now.get(java.util.Calendar.DAY_OF_MONTH) < loan.dueDayOfMonth) elapsed--
        elapsed.coerceIn(0, loan.tenureMonths)
    }
    val progress = (paidMonths.toFloat() / loan.tenureMonths).coerceIn(0f, 1f)
    val remaining = loan.tenureMonths - paidMonths

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
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Canvas),
            contentAlignment = Alignment.Center
        ) {
            Text(loan.emoji, fontSize = 20.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(loan.name, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Ink)
            Text(
                "₹${fmtINR(loan.monthlyEmi)}/mo · ${loan.tenureMonths} months",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = Muted
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(100.dp)).background(Canvas)
            ) {
                Box(
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(progress).clip(RoundedCornerShape(100.dp)).background(Jade)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "$paidMonths paid · $remaining remaining",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = Muted
            )
        }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Muted, modifier = Modifier.size(18.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLoanDialog(onDismiss: () -> Unit, onConfirm: (String, String, Double, Double, Int, Long, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🏦") }
    var totalAmount by remember { mutableStateOf("") }
    var monthlyEmi by remember { mutableStateOf("") }
    var tenureMonths by remember { mutableStateOf("") }
    var monthsAgo by remember { mutableStateOf("0") }
    var dueDay by remember { mutableStateOf("5") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        titleContentColor = Ink,
        title = { Text("Add Loan / EMI") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { if (it.length <= 2) emoji = it },
                        label = { Text("Icon") },
                        singleLine = true,
                        modifier = Modifier.width(72.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink)
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Loan name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = totalAmount,
                        onValueChange = { totalAmount = it },
                        label = { Text("Total (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink)
                    )
                    OutlinedTextField(
                        value = monthlyEmi,
                        onValueChange = { monthlyEmi = it },
                        label = { Text("EMI (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tenureMonths,
                        onValueChange = { tenureMonths = it.filter { c -> c.isDigit() } },
                        label = { Text("Tenure (mo)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink)
                    )
                    OutlinedTextField(
                        value = monthsAgo,
                        onValueChange = { monthsAgo = it.filter { c -> c.isDigit() } },
                        label = { Text("Months ago") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink)
                    )
                }
                OutlinedTextField(
                    value = dueDay,
                    onValueChange = { if (it.length <= 2) dueDay = it.filter { c -> c.isDigit() } },
                    label = { Text("Due day of month") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val total = totalAmount.toDoubleOrNull() ?: return@TextButton
                val emi = monthlyEmi.toDoubleOrNull() ?: return@TextButton
                val tenure = tenureMonths.toIntOrNull() ?: return@TextButton
                val ago = monthsAgo.toIntOrNull() ?: 0
                val due = dueDay.toIntOrNull()?.coerceIn(1, 31) ?: 5
                if (name.isNotBlank() && tenure > 0 && emi > 0) {
                    val startDate = java.util.Calendar.getInstance().apply {
                        add(java.util.Calendar.MONTH, -ago)
                        set(java.util.Calendar.DAY_OF_MONTH, 1)
                        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    onConfirm(name.trim(), emoji.ifBlank { "🏦" }, total, emi, tenure, startDate, due)
                }
            }) { Text("Add", color = Jade) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }
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
