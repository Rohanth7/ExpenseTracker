package com.example.expensetracker.ui.screens.settings

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.expensetracker.ui.theme.*
import com.example.expensetracker.ui.util.parseColor
import java.text.NumberFormat
import java.util.Locale

private sealed class SettingsPage {
    object Bills : SettingsPage()
    object Loans : SettingsPage()
    object Cloud : SettingsPage()
    object Preferences : SettingsPage()
    object Data : SettingsPage()
}

private fun fmtINR(amount: Double): String =
    NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply { maximumFractionDigits = 0 }.format(amount)

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val categories by viewModel.categories.collectAsState()
    val bills by viewModel.bills.collectAsState()
    val loans by viewModel.loans.collectAsState()
    val privacyMode by viewModel.privacyMode.collectAsState()
    val biometricLockEnabled by viewModel.biometricLockEnabled.collectAsState()
    val widgetEnabled by viewModel.widgetEnabled.collectAsState()
    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsState()
    val driveConnected by viewModel.driveConnected.collectAsState()
    val driveAccountEmail by viewModel.driveAccountEmail.collectAsState()
    val budgetAlertsEnabled by viewModel.budgetAlertsEnabled.collectAsState()
    val budgetAlertThreshold by viewModel.budgetAlertThreshold.collectAsState()
    val weekStartsOnMonday by viewModel.weekStartsOnMonday.collectAsState()
    val snackbarMessage = viewModel.snackbarMessage
    val pendingImportUri = viewModel.pendingImportUri
    val snackbarHostState = remember { SnackbarHostState() }

    var currentPage by remember { mutableStateOf<SettingsPage?>(null) }

    val context = LocalContext.current
    val activity = context as? Activity

    BackHandler(enabled = currentPage != null) { currentPage = null }

    LaunchedEffect(privacyMode) {
        if (privacyMode) {
            activity?.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportTo(it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.requestImport(it) }
    }

    val driveSignInOptions = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.appdata"))
            .build()
    }
    val driveSignInClient = remember { GoogleSignIn.getClient(context, driveSignInOptions) }
    val driveSignInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
                account?.email?.let { viewModel.setDriveAccount(it) }
            } catch (_: ApiException) {}
        }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() }
    }

    Scaffold(containerColor = Canvas, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        when (currentPage) {
            null -> SettingsMainMenu(
                bills = bills, loans = loans,
                driveConnected = driveConnected, driveAccountEmail = driveAccountEmail,
                modifier = Modifier.padding(padding),
                onNavigate = { currentPage = it }
            )
            SettingsPage.Bills -> BillsSubPage(
                bills = bills, categories = categories, viewModel = viewModel,
                modifier = Modifier.padding(padding), onBack = { currentPage = null }
            )
            SettingsPage.Loans -> LoansSubPage(
                loans = loans, viewModel = viewModel,
                modifier = Modifier.padding(padding), onBack = { currentPage = null }
            )
            SettingsPage.Cloud -> CloudSubPage(
                driveConnected = driveConnected, driveAccountEmail = driveAccountEmail,
                autoBackupEnabled = autoBackupEnabled,
                modifier = Modifier.padding(padding),
                onBack = { currentPage = null },
                onConnectDrive = { driveSignInLauncher.launch(driveSignInClient.signInIntent) },
                onDisconnectDrive = { driveSignInClient.signOut(); viewModel.disconnectDrive() },
                onSetAutoBackup = { viewModel.setAutoBackupEnabled(it) }
            )
            SettingsPage.Preferences -> PreferencesSubPage(
                budgetAlertsEnabled = budgetAlertsEnabled,
                budgetAlertThreshold = budgetAlertThreshold,
                weekStartsOnMonday = weekStartsOnMonday,
                privacyMode = privacyMode,
                biometricLockEnabled = biometricLockEnabled,
                widgetEnabled = widgetEnabled,
                modifier = Modifier.padding(padding),
                onBack = { currentPage = null },
                onSetBudgetAlerts = { viewModel.setBudgetAlertsEnabled(it) },
                onSetBudgetThreshold = { viewModel.setBudgetAlertThreshold(it) },
                onSetWeekStart = { viewModel.setWeekStartsOnMonday(it) },
                onSetPrivacyMode = { viewModel.setPrivacyMode(it) },
                onSetBiometricLock = { viewModel.setBiometricLockEnabled(it) },
                onSetWidget = { viewModel.setWidgetEnabled(it) }
            )
            SettingsPage.Data -> DataSubPage(
                modifier = Modifier.padding(padding),
                onBack = { currentPage = null },
                onExport = { exportLauncher.launch("expense_backup.json") },
                onImport = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                onMockData = { viewModel.loadMockData() },
                onReset = { viewModel.resetAll() }
            )
        }
    }

    if (pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelImport() },
            containerColor = Paper, titleContentColor = Ink, textContentColor = Muted,
            title = { Text("Replace all data?") },
            text = { Text("This will delete all your current expenses and categories, then restore from the backup file.") },
            confirmButton = { TextButton(onClick = { viewModel.confirmImport() }) { Text("Restore", color = Jade) } },
            dismissButton = { TextButton(onClick = { viewModel.cancelImport() }) { Text("Cancel", color = Muted) } }
        )
    }
}

// ── Main Menu ───────────────────────────────────────────────────────────────

@Composable
private fun SettingsMainMenu(
    bills: List<Bill>,
    loans: List<Loan>,
    driveConnected: Boolean,
    driveAccountEmail: String,
    modifier: Modifier = Modifier,
    onNavigate: (SettingsPage) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Canvas),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Text("SYSTEM", fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, letterSpacing = 1.8.sp, color = Muted, fontWeight = FontWeight.Medium)
            Text("Settings", fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 28.sp, color = Ink, lineHeight = 30.sp)
            Spacer(Modifier.height(24.dp))
        }
        item {
            SectionLabel("TRACKING")
            MenuCard {
                MenuRow(icon = Icons.Default.Receipt, iconBg = JadeSoft, iconTint = JadeInk, title = "Bills & Reminders",
                    subtitle = if (bills.isEmpty()) "No bills set up" else "${bills.size} bill${if (bills.size != 1) "s" else ""} · ${bills.count { it.isEnabled }} active",
                    onClick = { onNavigate(SettingsPage.Bills) })
                HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 12.dp))
                MenuRow(icon = Icons.Default.AccountBalance, iconBg = Canvas, iconTint = Ink, title = "Loans & EMIs",
                    subtitle = if (loans.isEmpty()) "No loans tracked" else "${loans.size} loan${if (loans.size != 1) "s" else ""} tracked",
                    onClick = { onNavigate(SettingsPage.Loans) })
            }
            Spacer(Modifier.height(20.dp))
        }
        item {
            SectionLabel("CLOUD")
            MenuCard {
                MenuRow(icon = Icons.Default.Cloud,
                    iconBg = if (driveConnected) JadeSoft else Canvas,
                    iconTint = if (driveConnected) JadeInk else Muted,
                    title = "Cloud Backup",
                    subtitle = if (driveConnected) driveAccountEmail else "Not connected",
                    onClick = { onNavigate(SettingsPage.Cloud) })
            }
            Spacer(Modifier.height(20.dp))
        }
        item {
            SectionLabel("CUSTOMISE")
            MenuCard {
                MenuRow(icon = Icons.Default.Tune, iconBg = Canvas, iconTint = Ink, title = "Preferences",
                    subtitle = "Alerts, privacy, widget, week start",
                    onClick = { onNavigate(SettingsPage.Preferences) })
            }
            Spacer(Modifier.height(20.dp))
        }
        item {
            SectionLabel("DATA")
            MenuCard {
                MenuRow(icon = Icons.Default.Storage, iconBg = Canvas, iconTint = Ink, title = "Data & Backup",
                    subtitle = "Export, import, reset all data",
                    onClick = { onNavigate(SettingsPage.Data) })
            }
            Spacer(Modifier.height(32.dp))
        }
        item {
            Text("Expense Tracker · v2.1", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Whisper,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ── Sub-pages ───────────────────────────────────────────────────────────────

@Composable
private fun BillsSubPage(bills: List<Bill>, categories: List<Category>, viewModel: SettingsViewModel, modifier: Modifier = Modifier, onBack: () -> Unit) {
    var showAddBill by remember { mutableStateOf(false) }
    var billToDelete by remember { mutableStateOf<Bill?>(null) }
    var billToEdit by remember { mutableStateOf<Bill?>(null) }

    LazyColumn(modifier = modifier.fillMaxSize().background(Canvas), contentPadding = PaddingValues(16.dp)) {
        item { SubPageHeader("Bills & Reminders", onBack) ; Spacer(Modifier.height(8.dp)) }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("BILLS & RECURRING")
                Text("Add +", fontSize = 12.sp, color = Jade, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { showAddBill = true })
            }
        }
        if (bills.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Paper).padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No bills set up yet.\nTap Add + to track subscriptions, rent, or auto-log recurring expenses.",
                        fontSize = 12.sp, color = Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 18.sp)
                }
            }
        } else {
            items(bills) { bill ->
                BillRow(bill = bill, category = categories.find { it.id == bill.categoryId },
                    onToggle = { viewModel.toggleBill(bill) }, onDelete = { billToDelete = bill }, onClick = { billToEdit = bill })
                Spacer(Modifier.height(8.dp))
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }

    if (showAddBill) {
        AddBillDialog(categories = categories, onDismiss = { showAddBill = false },
            onConfirm = { name, amount, day, reminderDays, catId, autoLog ->
                viewModel.addBill(name, amount, day, reminderDays, catId, autoLog); showAddBill = false
            })
    }
    billToDelete?.let { bill ->
        AlertDialog(onDismissRequest = { billToDelete = null }, containerColor = Paper, titleContentColor = Ink, textContentColor = Muted,
            title = { Text("Delete bill?") }, text = { Text("\"${bill.name}\" will be removed from your bill reminders.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteBill(bill); billToDelete = null }) { Text("Delete", color = Coral) } },
            dismissButton = { TextButton(onClick = { billToDelete = null }) { Text("Cancel", color = Muted) } })
    }
    billToEdit?.let { bill ->
        EditBillDialog(bill = bill, categories = categories, onDismiss = { billToEdit = null },
            onConfirm = { updated -> viewModel.updateBill(updated); billToEdit = null })
    }
}

@Composable
private fun LoansSubPage(loans: List<Loan>, viewModel: SettingsViewModel, modifier: Modifier = Modifier, onBack: () -> Unit) {
    var showAddLoan by remember { mutableStateOf(false) }
    var loanToDelete by remember { mutableStateOf<Loan?>(null) }
    var loanToEdit by remember { mutableStateOf<Loan?>(null) }

    LazyColumn(modifier = modifier.fillMaxSize().background(Canvas), contentPadding = PaddingValues(16.dp)) {
        item { SubPageHeader("Loans & EMIs", onBack) ; Spacer(Modifier.height(8.dp)) }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("LOANS")
                Text("Add +", fontSize = 12.sp, color = Jade, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { showAddLoan = true })
            }
        }
        if (loans.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Paper).padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No loans tracked.\nTap Add + to monitor EMI progress.",
                        fontSize = 12.sp, color = Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 18.sp)
                }
            }
        } else {
            items(loans) { loan ->
                LoanRow(loan = loan, onDelete = { loanToDelete = loan }, onClick = { loanToEdit = loan })
                Spacer(Modifier.height(8.dp))
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }

    if (showAddLoan) {
        AddLoanDialog(onDismiss = { showAddLoan = false },
            onConfirm = { name, emoji, total, emi, tenure, startDate, dueDay ->
                viewModel.addLoan(name, emoji, total, emi, tenure, startDate, dueDay); showAddLoan = false
            })
    }
    loanToDelete?.let { loan ->
        AlertDialog(onDismissRequest = { loanToDelete = null }, containerColor = Paper, titleContentColor = Ink, textContentColor = Muted,
            title = { Text("Delete loan?") }, text = { Text("\"${loan.name}\" will be removed from your EMI tracker.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteLoan(loan); loanToDelete = null }) { Text("Delete", color = Coral) } },
            dismissButton = { TextButton(onClick = { loanToDelete = null }) { Text("Cancel", color = Muted) } })
    }
    loanToEdit?.let { loan ->
        EditLoanDialog(loan = loan, onDismiss = { loanToEdit = null },
            onConfirm = { updated -> viewModel.updateLoan(updated); loanToEdit = null })
    }
}

@Composable
private fun CloudSubPage(
    driveConnected: Boolean, driveAccountEmail: String, autoBackupEnabled: Boolean,
    modifier: Modifier = Modifier, onBack: () -> Unit,
    onConnectDrive: () -> Unit, onDisconnectDrive: () -> Unit, onSetAutoBackup: (Boolean) -> Unit
) {
    LazyColumn(modifier = modifier.fillMaxSize().background(Canvas), contentPadding = PaddingValues(16.dp)) {
        item { SubPageHeader("Cloud Backup", onBack) ; Spacer(Modifier.height(8.dp)) }
        item {
            SectionLabel("GOOGLE DRIVE")
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Paper).padding(20.dp)) {
                if (driveConnected) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(JadeSoft), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Cloud, null, tint = JadeInk, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Google Drive", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Ink)
                            Text(driveAccountEmail, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Muted)
                        }
                        TextButton(onClick = onDisconnectDrive) { Text("Disconnect", color = Coral, fontSize = 12.sp) }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                    ToggleRow("Automatic Cloud Backup", "Weekly sync to your Drive App Data folder", autoBackupEnabled, onSetAutoBackup)
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Canvas), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CloudOff, null, tint = Muted, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Google Drive", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Ink)
                            Text("Not connected", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Muted)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Connect your Google account to automatically sync encrypted backups to your private Drive App Data folder.",
                        fontSize = 11.sp, color = Muted, lineHeight = 16.sp)
                    Spacer(Modifier.height(14.dp))
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Jade).clickable(onClick = onConnectDrive).padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center) {
                        Text("Connect Google Drive", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Deep)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun PreferencesSubPage(
    budgetAlertsEnabled: Boolean, budgetAlertThreshold: Int,
    weekStartsOnMonday: Boolean, privacyMode: Boolean, biometricLockEnabled: Boolean, widgetEnabled: Boolean,
    modifier: Modifier = Modifier, onBack: () -> Unit,
    onSetBudgetAlerts: (Boolean) -> Unit, onSetBudgetThreshold: (Int) -> Unit,
    onSetWeekStart: (Boolean) -> Unit, onSetPrivacyMode: (Boolean) -> Unit,
    onSetBiometricLock: (Boolean) -> Unit, onSetWidget: (Boolean) -> Unit
) {
    LazyColumn(modifier = modifier.fillMaxSize().background(Canvas), contentPadding = PaddingValues(16.dp)) {
        item { SubPageHeader("Preferences", onBack) ; Spacer(Modifier.height(8.dp)) }
        item {
            SectionLabel("NOTIFICATIONS")
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Paper).padding(horizontal = 16.dp, vertical = 4.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Budget alerts", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Ink)
                            Text(if (budgetAlertsEnabled) "Alert at $budgetAlertThreshold% & 100% of limit" else "Disabled", fontSize = 11.5.sp, color = Muted)
                        }
                        Switch(checked = budgetAlertsEnabled, onCheckedChange = onSetBudgetAlerts,
                            colors = SwitchDefaults.colors(checkedThumbColor = Ink, checkedTrackColor = Jade, uncheckedThumbColor = Ink, uncheckedTrackColor = Hairline))
                    }
                    androidx.compose.animation.AnimatedVisibility(visible = budgetAlertsEnabled) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Warning threshold", fontSize = 11.5.sp, color = Muted)
                                Text("$budgetAlertThreshold%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Jade, fontFamily = FontFamily.Monospace)
                            }
                            Slider(value = budgetAlertThreshold.toFloat(), onValueChange = { onSetBudgetThreshold(it.toInt()) },
                                valueRange = 50f..95f, steps = 8, modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(thumbColor = Jade, activeTrackColor = Jade, inactiveTrackColor = Hairline))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("50%", fontSize = 10.sp, color = Whisper)
                                Text("95%", fontSize = 10.sp, color = Whisper)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        item {
            SectionLabel("DISPLAY")
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Paper).padding(horizontal = 16.dp, vertical = 4.dp)) {
                var showWeekStartInfo by remember { mutableStateOf(false) }
                Row(modifier = Modifier.fillMaxWidth().clickable { onSetWeekStart(!weekStartsOnMonday) }.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Week starts on", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Ink)
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = Muted, modifier = Modifier.size(15.dp).clickable(
                            indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) { showWeekStartInfo = !showWeekStartInfo })
                    }
                    Text(if (weekStartsOnMonday) "Monday" else "Sunday", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Jade, letterSpacing = 0.4.sp)
                }
                androidx.compose.animation.AnimatedVisibility(visible = showWeekStartInfo) {
                    Text("Controls which day the 'This week' total resets on in the Expenses screen. Set to Monday for a work-week view, or Sunday for a calendar-week view.",
                        fontSize = 11.5.sp, color = Muted,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Canvas).padding(10.dp, 8.dp).padding(bottom = 6.dp))
                }
                HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                ToggleRow("Home Screen Widget", "Show progress on home screen", widgetEnabled, onSetWidget)
            }
            Spacer(Modifier.height(20.dp))
        }
        item {
            SectionLabel("SECURITY")
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Paper).padding(horizontal = 16.dp, vertical = 4.dp)) {
                ToggleRow("Privacy mode", "Hides screen in app switcher", privacyMode, onSetPrivacyMode)
                HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                ToggleRow("Biometric Lock", "Require fingerprint or PIN", biometricLockEnabled, onSetBiometricLock)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun DataSubPage(
    modifier: Modifier = Modifier, onBack: () -> Unit,
    onExport: () -> Unit, onImport: () -> Unit, onMockData: () -> Unit, onReset: () -> Unit
) {
    var showMockDataDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    LazyColumn(modifier = modifier.fillMaxSize().background(Canvas), contentPadding = PaddingValues(16.dp)) {
        item { SubPageHeader("Data & Backup", onBack) ; Spacer(Modifier.height(8.dp)) }
        item {
            SectionLabel("BACKUP")
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Paper).padding(horizontal = 16.dp, vertical = 4.dp)) {
                SettingRow(icon = { Icon(Icons.Default.CloudUpload, null, tint = Ink, modifier = Modifier.size(17.dp)) },
                    title = "Export backup", subtitle = "Save expenses + categories as JSON", onClick = onExport)
                HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                SettingRow(icon = { Icon(Icons.Default.Restore, null, tint = Ink, modifier = Modifier.size(17.dp)) },
                    title = "Restore from backup", subtitle = "Replace all data with a backup file", onClick = onImport)
            }
            Spacer(Modifier.height(20.dp))
        }
        item {
            SectionLabel("DEVELOPER")
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Paper).padding(horizontal = 16.dp, vertical = 4.dp)) {
                SettingRow(icon = { Icon(Icons.Default.Science, null, tint = Jade, modifier = Modifier.size(17.dp)) },
                    title = "Load mock data", subtitle = "Fill app with 3 months of sample expenses for testing",
                    onClick = { showMockDataDialog = true })
                HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                SettingRow(icon = { Icon(Icons.Default.Delete, null, tint = Coral, modifier = Modifier.size(17.dp)) },
                    title = "Reset everything", subtitle = "Delete all expenses, categories & templates",
                    danger = true, onClick = { showResetDialog = true })
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (showMockDataDialog) {
        AlertDialog(onDismissRequest = { showMockDataDialog = false }, containerColor = Paper, titleContentColor = Ink, textContentColor = Muted,
            title = { Text("Load mock data?") },
            text = { Text("This will replace all existing data with 3 months of sample expenses, categories, bills, a loan, and savings goals.") },
            confirmButton = { TextButton(onClick = { onMockData(); showMockDataDialog = false }) { Text("Load", color = Jade) } },
            dismissButton = { TextButton(onClick = { showMockDataDialog = false }) { Text("Cancel", color = Muted) } })
    }
    if (showResetDialog) {
        AlertDialog(onDismissRequest = { showResetDialog = false }, containerColor = Paper, titleContentColor = Ink, textContentColor = Muted,
            title = { Text("Reset everything?") },
            text = { Text("This will permanently delete all expenses, categories, and recurring templates. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { onReset(); showResetDialog = false }) { Text("Delete all", color = Coral) } },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel", color = Muted) } })
    }
}

// ── Shared UI helpers ────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.6.sp, color = Muted, modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun MenuCard(content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Paper).padding(horizontal = 4.dp, vertical = 4.dp), content = content)
}

@Composable
private fun MenuRow(icon: ImageVector, iconBg: Color, iconTint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(iconBg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Ink)
            Text(subtitle, fontSize = 11.5.sp, color = Muted)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Whisper, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SubPageHeader(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
        }
        Text(title, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 22.sp, color = Ink)
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Ink)
            Text(subtitle, fontSize = 11.5.sp, color = Muted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Ink, checkedTrackColor = Jade, uncheckedThumbColor = Ink, uncheckedTrackColor = Hairline))
    }
}

@Composable
private fun SettingRow(icon: @Composable () -> Unit, title: String, subtitle: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(if (danger) CoralSoft else Canvas), contentAlignment = Alignment.Center) { icon() }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = if (danger) Coral else Ink)
            Text(subtitle, fontSize = 11.5.sp, color = Muted)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Whisper, modifier = Modifier.size(16.dp))
    }
}

// ── Bill / Loan rows & dialogs ───────────────────────────────────────────────

@Composable
private fun BillRow(bill: Bill, category: Category?, onToggle: () -> Unit, onDelete: () -> Unit, onClick: () -> Unit) {
    val ordinal = when {
        bill.dueDayOfMonth in 11..13 -> "${bill.dueDayOfMonth}th"
        bill.dueDayOfMonth % 10 == 1 -> "${bill.dueDayOfMonth}st"
        bill.dueDayOfMonth % 10 == 2 -> "${bill.dueDayOfMonth}nd"
        bill.dueDayOfMonth % 10 == 3 -> "${bill.dueDayOfMonth}rd"
        else -> "${bill.dueDayOfMonth}th"
    }
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Paper).clickable { onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
            .background(if (category != null) parseColor(category.colorHex).copy(alpha = 0.15f) else Muted.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center) {
            Text(category?.emoji ?: if (bill.autoLog) "🔁" else "📋", fontSize = 18.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(bill.name, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if (bill.autoLog) JadeSoft else Canvas).padding(horizontal = 5.dp, vertical = 1.dp)) {
                    Text(if (bill.autoLog) "AUTO" else "REMIND", fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = if (bill.autoLog) JadeInk else Muted)
                }
            }
            Text(
                if (bill.autoLog) "₹${fmtINR(bill.amount)} · logs on 1st of month"
                else "₹${fmtINR(bill.amount)} · due $ordinal · remind ${bill.reminderDays}d before",
                fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Muted)
        }
        Switch(checked = bill.isEnabled, onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(checkedThumbColor = Ink, checkedTrackColor = Jade, uncheckedThumbColor = Ink, uncheckedTrackColor = Hairline))
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Muted, modifier = Modifier.size(18.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBillDialog(categories: List<Category>, onDismiss: () -> Unit, onConfirm: (String, Double, Int, Int, Long, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dueDay by remember { mutableStateOf("") }
    var reminderDays by remember { mutableStateOf("3") }
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id ?: -1L) }
    var expanded by remember { mutableStateOf(false) }
    var autoLog by remember { mutableStateOf(false) }
    val selectedCategory = categories.find { it.id == selectedCategoryId }

    AlertDialog(onDismissRequest = onDismiss, containerColor = Paper, titleContentColor = Ink,
        title = { Text("Add Bill / Recurring") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (e.g. Rent, Netflix)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (autoLog) JadeSoft else Canvas).clickable { autoLog = !autoLog }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Auto-log expense monthly", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)
                        Text(if (autoLog) "Expense added on 1st of each month" else "Send a reminder notification instead", fontSize = 11.sp, color = Muted)
                    }
                    Switch(checked = autoLog, onCheckedChange = { autoLog = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Ink, checkedTrackColor = Jade, uncheckedThumbColor = Ink, uncheckedTrackColor = Hairline))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
                    if (!autoLog) {
                        OutlinedTextField(value = dueDay, onValueChange = { if (it.length <= 2) dueDay = it.filter { c -> c.isDigit() } },
                            label = { Text("Due day") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(0.7f),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
                    }
                }
                if (!autoLog) {
                    OutlinedTextField(value = reminderDays, onValueChange = { reminderDays = it.filter { c -> c.isDigit() } },
                        label = { Text("Remind N days before") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
                }
                if (categories.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(value = selectedCategory?.let { "${it.emoji} ${it.name}" } ?: "Category (optional)",
                            onValueChange = {}, readOnly = true, label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
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
                if (name.isBlank()) return@TextButton
                if (autoLog) { onConfirm(name.trim(), amt, 1, 0, selectedCategoryId, true) }
                else {
                    val day = dueDay.toIntOrNull()?.coerceIn(1, 31) ?: return@TextButton
                    val remind = reminderDays.toIntOrNull()?.coerceIn(0, 14) ?: 3
                    onConfirm(name.trim(), amt, day, remind, selectedCategoryId, false)
                }
            }) { Text("Add", color = Jade) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } })
}

@Composable
private fun LoanRow(loan: Loan, onDelete: () -> Unit, onClick: () -> Unit) {
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

    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Paper).clickable { onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Canvas), contentAlignment = Alignment.Center) {
            Text(loan.emoji, fontSize = 20.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(loan.name, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Ink)
            Text("₹${fmtINR(loan.monthlyEmi)}/mo · ${loan.tenureMonths} months", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Muted)
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(100.dp)).background(Canvas)) {
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(progress).clip(RoundedCornerShape(100.dp)).background(Jade))
            }
            Spacer(Modifier.height(4.dp))
            Text("$paidMonths paid · $remaining remaining", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Muted)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Muted, modifier = Modifier.size(18.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditBillDialog(bill: Bill, categories: List<Category>, onDismiss: () -> Unit, onConfirm: (Bill) -> Unit) {
    var name by remember { mutableStateOf(bill.name) }
    var amount by remember { mutableStateOf(bill.amount.toString()) }
    var dueDay by remember { mutableStateOf(bill.dueDayOfMonth.toString()) }
    var reminderDays by remember { mutableStateOf(bill.reminderDays.toString()) }
    var selectedCategoryId by remember { mutableStateOf(bill.categoryId) }
    var expanded by remember { mutableStateOf(false) }
    var autoLog by remember { mutableStateOf(bill.autoLog) }
    val selectedCategory = categories.find { it.id == selectedCategoryId }

    AlertDialog(onDismissRequest = onDismiss, containerColor = Paper, titleContentColor = Ink,
        title = { Text("Edit Bill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (autoLog) JadeSoft else Canvas).clickable { autoLog = !autoLog }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Auto-log expense monthly", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)
                        Text(if (autoLog) "Expense added on 1st of each month" else "Send a reminder notification instead", fontSize = 11.sp, color = Muted)
                    }
                    Switch(checked = autoLog, onCheckedChange = { autoLog = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Ink, checkedTrackColor = Jade, uncheckedThumbColor = Ink, uncheckedTrackColor = Hairline))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
                    if (!autoLog) {
                        OutlinedTextField(value = dueDay, onValueChange = { if (it.length <= 2) dueDay = it.filter { c -> c.isDigit() } },
                            label = { Text("Due day") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(0.7f),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
                    }
                }
                if (!autoLog) {
                    OutlinedTextField(value = reminderDays, onValueChange = { reminderDays = it.filter { c -> c.isDigit() } },
                        label = { Text("Remind N days before") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
                }
                if (categories.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(value = selectedCategory?.let { "${it.emoji} ${it.name}" } ?: "Category (optional)",
                            onValueChange = {}, readOnly = true, label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
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
                if (name.isBlank()) return@TextButton
                val updated = if (autoLog) {
                    bill.copy(name = name.trim(), amount = amt, dueDayOfMonth = 1, reminderDays = 0, categoryId = selectedCategoryId, autoLog = true)
                } else {
                    val day = dueDay.toIntOrNull()?.coerceIn(1, 31) ?: return@TextButton
                    val remind = reminderDays.toIntOrNull()?.coerceIn(0, 14) ?: 3
                    bill.copy(name = name.trim(), amount = amt, dueDayOfMonth = day, reminderDays = remind, categoryId = selectedCategoryId, autoLog = false)
                }
                onConfirm(updated)
            }) { Text("Save", color = Jade) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } })
}

@Composable
private fun EditLoanDialog(loan: Loan, onDismiss: () -> Unit, onConfirm: (Loan) -> Unit) {
    val paidMonths = run {
        val now = java.util.Calendar.getInstance()
        val startCal = java.util.Calendar.getInstance().apply { timeInMillis = loan.startDate }
        var elapsed = (now.get(java.util.Calendar.YEAR) - startCal.get(java.util.Calendar.YEAR)) * 12 +
            (now.get(java.util.Calendar.MONTH) - startCal.get(java.util.Calendar.MONTH))
        if (now.get(java.util.Calendar.DAY_OF_MONTH) < loan.dueDayOfMonth) elapsed--
        elapsed.coerceIn(0, loan.tenureMonths)
    }
    val progress = (paidMonths.toFloat() / loan.tenureMonths).coerceIn(0f, 1f)

    val initialMonthsAgo = run {
        val now = java.util.Calendar.getInstance()
        val start = java.util.Calendar.getInstance().apply { timeInMillis = loan.startDate }
        ((now.get(java.util.Calendar.YEAR) - start.get(java.util.Calendar.YEAR)) * 12 +
            (now.get(java.util.Calendar.MONTH) - start.get(java.util.Calendar.MONTH))).coerceAtLeast(0)
    }

    var name by remember { mutableStateOf(loan.name) }
    var emoji by remember { mutableStateOf(loan.emoji) }
    var totalAmount by remember { mutableStateOf(loan.totalAmount.toLong().toString()) }
    var monthlyEmi by remember { mutableStateOf(loan.monthlyEmi.toLong().toString()) }
    var tenureMonths by remember { mutableStateOf(loan.tenureMonths.toString()) }
    var monthsAgo by remember { mutableStateOf(initialMonthsAgo.toString()) }
    var dueDay by remember { mutableStateOf(loan.dueDayOfMonth.toString()) }

    AlertDialog(onDismissRequest = onDismiss, containerColor = Paper, titleContentColor = Ink,
        title = { Text("Edit Loan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Progress summary (read-only)
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Canvas).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("$paidMonths of ${loan.tenureMonths} EMIs paid", fontSize = 11.sp, color = Muted)
                        Text("${"%.0f".format(progress * 100)}%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Jade, fontFamily = FontFamily.Monospace)
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(100.dp)).background(Hairline)) {
                        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(progress).clip(RoundedCornerShape(100.dp)).background(Jade))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = emoji, onValueChange = { if (it.length <= 2) emoji = it }, label = { Text("Icon") }, singleLine = true, modifier = Modifier.width(72.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Loan name") }, singleLine = true, modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = totalAmount, onValueChange = { totalAmount = it }, label = { Text("Total (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
                    OutlinedTextField(value = monthlyEmi, onValueChange = { monthlyEmi = it }, label = { Text("EMI (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = tenureMonths, onValueChange = { tenureMonths = it.filter { c -> c.isDigit() } }, label = { Text("Tenure (mo)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
                    OutlinedTextField(value = monthsAgo, onValueChange = { monthsAgo = it.filter { c -> c.isDigit() } }, label = { Text("Months ago") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
                }
                OutlinedTextField(value = dueDay, onValueChange = { if (it.length <= 2) dueDay = it.filter { c -> c.isDigit() } }, label = { Text("Due day of month") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val total = totalAmount.toDoubleOrNull() ?: return@TextButton
                val emi = monthlyEmi.toDoubleOrNull() ?: return@TextButton
                val tenure = tenureMonths.toIntOrNull() ?: return@TextButton
                val ago = monthsAgo.toIntOrNull() ?: 0
                val due = dueDay.toIntOrNull()?.coerceIn(1, 31) ?: 5
                if (name.isBlank() || tenure <= 0 || emi <= 0) return@TextButton
                val newStartDate = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.MONTH, -ago)
                    set(java.util.Calendar.DAY_OF_MONTH, 1)
                    set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
                onConfirm(loan.copy(name = name.trim(), emoji = emoji.ifBlank { "🏦" }, totalAmount = total, monthlyEmi = emi, tenureMonths = tenure, startDate = newStartDate, dueDayOfMonth = due))
            }) { Text("Save", color = Jade) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } })
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

    AlertDialog(onDismissRequest = onDismiss, containerColor = Paper, titleContentColor = Ink,
        title = { Text("Add Loan / EMI") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = emoji, onValueChange = { if (it.length <= 2) emoji = it }, label = { Text("Icon") }, singleLine = true, modifier = Modifier.width(72.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Loan name") }, singleLine = true, modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = totalAmount, onValueChange = { totalAmount = it }, label = { Text("Total (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
                    OutlinedTextField(value = monthlyEmi, onValueChange = { monthlyEmi = it }, label = { Text("EMI (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = tenureMonths, onValueChange = { tenureMonths = it.filter { c -> c.isDigit() } }, label = { Text("Tenure (mo)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
                    OutlinedTextField(value = monthsAgo, onValueChange = { monthsAgo = it.filter { c -> c.isDigit() } }, label = { Text("Months ago") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
                }
                OutlinedTextField(value = dueDay, onValueChange = { if (it.length <= 2) dueDay = it.filter { c -> c.isDigit() } }, label = { Text("Due day of month") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedLabelColor = Jade, unfocusedBorderColor = Hairline, unfocusedLabelColor = Muted, focusedTextColor = Ink, unfocusedTextColor = Ink))
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } })
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = Muted)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Ink)
    }
}
