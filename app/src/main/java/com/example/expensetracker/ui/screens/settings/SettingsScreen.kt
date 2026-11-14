package com.example.expensetracker.ui.screens.settings

import com.example.expensetracker.R
import androidx.compose.ui.res.stringResource
import com.example.expensetracker.ui.util.appFooter
import com.example.expensetracker.ui.util.appVersionName
import com.example.expensetracker.ui.util.fmtINR
import com.example.expensetracker.ui.screens.dashboard.LoanProgress
import com.example.expensetracker.ui.util.rememberSaveableItem
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.Manifest
import androidx.core.app.ActivityCompat
import com.example.expensetracker.ui.util.NotificationAccessUtil
import com.example.expensetracker.ui.util.SmsPermissionUtil
import com.example.expensetracker.ui.util.TrackingStatus
import com.example.expensetracker.ui.util.rememberTrackingStatus
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.db.entity.Bill
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Loan
import com.example.expensetracker.data.db.entity.RecurringIncome
import com.example.expensetracker.ui.theme.*
import com.example.expensetracker.data.backup.BackupData
import com.example.expensetracker.data.export.ExportPreset
import com.example.expensetracker.data.period.BudgetPeriod
import com.example.expensetracker.ui.components.DatePresetOptions
import com.example.expensetracker.data.period.MonthStartDay
import com.example.expensetracker.data.export.ExportRange
import androidx.fragment.app.FragmentActivity
import com.example.expensetracker.security.AppLock
import com.example.expensetracker.ui.util.BiometricUtil
import com.example.expensetracker.ui.util.isDebugBuild
import com.example.expensetracker.ui.util.formatLimitInput
import com.example.expensetracker.ui.util.parseColor
import com.example.expensetracker.ui.util.AmountCalculator
import com.example.expensetracker.ui.util.AmountField
import com.example.expensetracker.ui.util.rememberAmountInputState
import com.example.expensetracker.ui.util.parseLimitInput
import java.util.Locale
import com.example.expensetracker.ui.util.rememberAmountText
import com.example.expensetracker.ui.util.parseAmountOrZero
import com.example.expensetracker.ui.util.oneCharacter
import com.example.expensetracker.ui.util.wholeNumberIn
import com.example.expensetracker.ui.components.AppAlertDialog
import com.example.expensetracker.ui.components.LocalAppLocked

// An enum so the open page survives rotation and Android closing the app (rememberSaveable).
internal enum class SettingsPage { Bills, Loans, CategoryBudgets, Cloud, Preferences, Data }

/** Which front page the screen opens on: the Plan tab, or Settings (reached from Home). */
enum class SettingsHome { Settings, Plan }

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    home: SettingsHome = SettingsHome.Settings,
    onBack: () -> Unit = {},
    onOpenMessageRules: () -> Unit = {},
    onOpenCategories: () -> Unit = {},
    onOpenSubscriptions: () -> Unit = {},
) {
    val ruleCount = com.example.expensetracker.sms.ParsingRuleStore.get(LocalContext.current).rules.collectAsState().value.size
    val categories by viewModel.categories.collectAsState()
    val bills by viewModel.bills.collectAsState()
    val loans by viewModel.loans.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    val recurringIncomes by viewModel.recurringIncomes.collectAsState()
    val privacyMode by viewModel.privacyMode.collectAsState()
    val biometricLockEnabled by viewModel.biometricLockEnabled.collectAsState()
    val widgetEnabled by viewModel.widgetEnabled.collectAsState()
    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsState()
    val driveConnected by viewModel.driveConnected.collectAsState()
    val driveAccountEmail by viewModel.driveAccountEmail.collectAsState()
    val budgetAlertsEnabled by viewModel.budgetAlertsEnabled.collectAsState()
    val priceAlertsEnabled by viewModel.priceAlertsEnabled.collectAsState()
    val budgetAlertThreshold by viewModel.budgetAlertThreshold.collectAsState()
    val weekStartsOnMonday by viewModel.weekStartsOnMonday.collectAsState()
    val monthStartDay by viewModel.monthStartDay.collectAsState()
    val snackbarMessage = viewModel.snackbarMessage
    val pendingRestore = viewModel.pendingRestore
    val snackbarHostState = remember { SnackbarHostState() }

    var currentPage by rememberSaveable { mutableStateOf<SettingsPage?>(null) }

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
    // The chosen range has to outlive the file picker, which can outlive the app's process.
    var pendingCsvRange by rememberSaveable { mutableStateOf<Pair<Long, Long>?>(null) }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.exportCsvTo(it, pendingCsvRange) }
    }
    val tracking = rememberTrackingStatus()
    val smsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        tracking.refresh()
        // Android stops showing the prompt after repeated denials; send the user to App info instead.
        if (!granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECEIVE_SMS)) {
            SmsPermissionUtil.openSettings(context)
        }
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
        // A cancelled or failed sign-in comes back as RESULT_CANCELED, with the reason in the data.
        val email = try {
            if (result.data == null) null
            else GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)?.email
        } catch (e: ApiException) {
            viewModel.showMessage(driveSignInFailure(e.statusCode))
            return@rememberLauncherForActivityResult
        }
        if (email != null) viewModel.setDriveAccount(email) else viewModel.showMessage(driveSignInFailure(null))
    }

    val driveAuthLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onDriveAuthResult(result.resultCode == Activity.RESULT_OK)
    }
    // Results of Drive requests and file reads can arrive while the app is locked. Google's consent
    // screen would open over the lock, and a message would show under it and be gone by unlock.
    val locked = LocalAppLocked.current
    LaunchedEffect(viewModel.driveAuthIntent, locked) {
        if (!locked) viewModel.takeDriveAuthIntent()?.let { driveAuthLauncher.launch(it) }
    }

    LaunchedEffect(snackbarMessage, locked) {
        if (!locked) snackbarMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() }
    }

    Scaffold(containerColor = Canvas, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        when (currentPage) {
            null -> if (home == SettingsHome.Plan) PlanFrontPage(
                budgets = budgets, alertPercent = budgetAlertThreshold,
                bills = bills, loans = loans, categories = categories,
                recurringIncomeCount = recurringIncomes.size,
                onOpenSubscriptions = onOpenSubscriptions,
                modifier = Modifier.padding(padding),
                onNavigate = { currentPage = it }
            ) else SettingsMainMenu(
                categories = categories, onBack = onBack, onOpenCategories = onOpenCategories,
                driveConnected = driveConnected, driveAccountEmail = driveAccountEmail,
                tracking = tracking.status,
                onFixSms = { smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS) },
                onFixNotifications = { NotificationAccessUtil.openSettings(context) },
                ruleCount = ruleCount,
                onOpenMessageRules = onOpenMessageRules,
                modifier = Modifier.padding(padding),
                onNavigate = { currentPage = it }
            )
            SettingsPage.CategoryBudgets -> CategoryBudgetsSubPage(
                categories = categories, viewModel = viewModel,
                modifier = Modifier.padding(padding), onBack = { currentPage = null }
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
                driveBusy = viewModel.driveBusy,
                modifier = Modifier.padding(padding),
                onBack = { currentPage = null },
                onBackUpNow = { viewModel.backUpToDriveNow() },
                onRestoreFromDrive = { viewModel.restoreFromDrive() },
                onConnectDrive = { driveSignInLauncher.launch(driveSignInClient.signInIntent) },
                onDisconnectDrive = { driveSignInClient.signOut(); viewModel.disconnectDrive() },
                onSetAutoBackup = { viewModel.setAutoBackupEnabled(it) }
            )
            SettingsPage.Preferences -> PreferencesSubPage(
                budgetAlertsEnabled = budgetAlertsEnabled,
                budgetAlertThreshold = budgetAlertThreshold,
                priceAlertsEnabled = priceAlertsEnabled,
                weekStartsOnMonday = weekStartsOnMonday,
                monthStartDay = monthStartDay,
                privacyMode = privacyMode,
                biometricLockEnabled = biometricLockEnabled,
                widgetEnabled = widgetEnabled,
                modifier = Modifier.padding(padding),
                onBack = { currentPage = null },
                onSetBudgetAlerts = { viewModel.setBudgetAlertsEnabled(it) },
                onSetBudgetThreshold = { viewModel.setBudgetAlertThreshold(it) },
                onSetPriceAlerts = { viewModel.setPriceAlertsEnabled(it) },
                onSetWeekStart = { viewModel.setWeekStartsOnMonday(it) },
                onSetMonthStart = { viewModel.setMonthStartDay(it) },
                onSetPrivacyMode = { viewModel.setPrivacyMode(it) },
                onSetBiometricLock = { enable ->
                    when {
                        !enable -> viewModel.setBiometricLockEnabled(false)
                        !BiometricUtil.canAuthenticate(context) ->
                            viewModel.showMessage("Set up a screen lock (PIN, pattern or fingerprint) on your phone first")
                        // Ask once before turning it on, so nobody locks themselves out with a
                        // method that doesn't work.
                        activity is FragmentActivity -> BiometricUtil.showBiometricPrompt(
                            activity, title = "Turn on app lock", subtitle = "Confirm it's you",
                            onSuccess = { AppLock.state.unlock(); viewModel.setBiometricLockEnabled(true) },
                            onError = { msg -> msg?.let { viewModel.showMessage(it) } }
                        )
                    }
                },
                onSetWidget = { viewModel.setWidgetEnabled(it) }
            )
            SettingsPage.Data -> DataSubPage(
                modifier = Modifier.padding(padding),
                onBack = { currentPage = null },
                onExport = { exportLauncher.launch("mymoney-backup-${java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())}.json") },
                onImport = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                onExportCsv = { preset, range, monthStartDay ->
                    pendingCsvRange = range
                    csvLauncher.launch(ExportRange.fileName(preset, range, monthStartDay))
                },
                showMockData = isDebugBuild(context),
                onMockData = { viewModel.loadMockData() },
                onReset = { viewModel.resetAll() }
            )
        }
    }

    if (viewModel.confirmEmptyBackup) {
        AppAlertDialog(onDismissRequest = { viewModel.cancelEmptyBackup() }, containerColor = Paper, titleContentColor = Ink, textContentColor = Muted,
            title = { Text("Replace your Drive backup?") },
            text = { Text("This phone has nothing to back up, and the backup in your Google Drive has records. Backing up now replaces it with an empty one. To get those records onto this phone, use Restore from Drive instead.") },
            confirmButton = { TextButton(onClick = { viewModel.backUpToDriveNow(replaceRecords = true) }) { Text("Replace backup", color = Pink) } },
            dismissButton = { TextButton(onClick = { viewModel.cancelEmptyBackup() }) { Text("Cancel", color = Muted) } })
    }

    if (pendingRestore != null) {
        RestoreConfirmDialog(
            data = pendingRestore.data,
            fromDrive = pendingRestore.fromDrive,
            onConfirm = { viewModel.confirmRestore() },
            onDismiss = { viewModel.cancelRestore() }
        )
    }
}

@Composable
private fun MonthStartDialog(current: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismiss, containerColor = Paper, titleContentColor = Ink, textContentColor = Muted,
        title = { Text("Month starts on") },
        text = {
            Column {
                Text("Pick the day your salary usually arrives. Budgets, the forecast and monthly statistics will run from that day. Bills keep their own due dates.",
                    fontSize = 13.sp, color = Muted, modifier = Modifier.padding(bottom = 8.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items((BudgetPeriod.MIN_START_DAY..BudgetPeriod.MAX_START_DAY).toList()) { day ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(day) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = day == current, onClick = { onPick(day) },
                                colors = RadioButtonDefaults.colors(selectedColor = Gold, unselectedColor = Muted))
                            Text(if (day == 1) "1st (calendar month)" else BudgetPeriod.ordinal(day), color = Ink, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }
    )
}

/** Shows what a backup contains before it replaces anything. Used by Settings and first-run setup. */
@Composable
internal fun RestoreConfirmDialog(data: BackupData, fromDrive: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val d = data
    val date = remember(d.exportedAt) {
        java.text.SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(java.util.Date(d.exportedAt))
    }
    fun count(n: Int?, one: String, many: String) = n?.let { "$it ${if (it == 1) one else many}" }
    val contents = listOfNotNull(
        count(d.expenses?.size, "expense", "expenses"),
        count(d.categories?.size, "category", "categories"),
        count(d.bills?.size, "bill", "bills"),
        count(d.loans?.size, "loan", "loans"),
        count(d.savingsGoals?.size, "savings goal", "savings goals"),
        count(d.incomes?.size, "income", "incomes"),
        count(d.recurringIncomes?.size, "recurring income", "recurring incomes")
    ).joinToString(", ")
    val kept = d.missingSections
    AppAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper, titleContentColor = Ink, textContentColor = Muted,
        title = { Text(if (fromDrive) "Restore from Google Drive?" else "Restore this backup?") },
        text = {
            Text(buildString {
                append("Backup from $date: $contents.\n\n")
                append("This replaces that data on this phone. If anything goes wrong, nothing is changed.")
                if (kept.isNotEmpty()) {
                    val list = if (kept.size == 1) kept[0] else kept.dropLast(1).joinToString(", ") + " and " + kept.last()
                    append("\n\nThis is an older backup without $list, so those stay as they are.")
                }
            })
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Restore", color = Gold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }
    )
}

// ── Plan ────────────────────────────────────────────────────────────────────

// ── Main Menu ───────────────────────────────────────────────────────────────

@Composable
private fun SettingsMainMenu(
    categories: List<Category>,
    onBack: () -> Unit,
    onOpenCategories: () -> Unit,
    driveConnected: Boolean,
    driveAccountEmail: String,
    tracking: TrackingStatus,
    onFixSms: () -> Unit,
    onFixNotifications: () -> Unit,
    ruleCount: Int,
    onOpenMessageRules: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigate: (SettingsPage) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Canvas),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            SubPageHeader("Settings", onBack)
            Spacer(Modifier.height(16.dp))
        }
        item {
            SectionLabel("AUTO-TRACKING")
            MenuCard {
                MenuRow(icon = Icons.Default.Sms,
                    iconBg = if (tracking.sms) GoldSoft else PinkSoft,
                    iconTint = if (tracking.sms) GoldInk else Pink,
                    title = "Bank SMS",
                    subtitle = if (tracking.sms) "On · payments are logged from bank texts" else "Off · tap to allow SMS access",
                    onClick = { if (!tracking.sms) onFixSms() })
                HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 12.dp))
                MenuRow(icon = Icons.Default.NotificationsActive,
                    iconBg = if (tracking.notifications) GoldSoft else PinkSoft,
                    iconTint = if (tracking.notifications) GoldInk else Pink,
                    title = "Payment app notifications",
                    subtitle = if (tracking.notifications) "On · GPay, PhonePe, Paytm and bank apps" else "Off · tap to turn on notification access",
                    onClick = onFixNotifications)
                HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 12.dp))
                MenuRow(icon = Icons.Default.Rule, iconBg = Canvas, iconTint = Ink,
                    title = "Message rules",
                    subtitle = if (ruleCount == 0) "Teach MyMoney a message it reads wrong" else "$ruleCount rule${if (ruleCount == 1) "" else "s"} you've taught",
                    onClick = onOpenMessageRules)
            }
            Spacer(Modifier.height(20.dp))
        }
        item {
            SectionLabel("CLOUD")
            MenuCard {
                MenuRow(icon = Icons.Default.Cloud,
                    iconBg = if (driveConnected) GoldSoft else Canvas,
                    iconTint = if (driveConnected) GoldInk else Muted,
                    title = "Cloud Backup",
                    subtitle = if (driveConnected) driveAccountEmail else "Not connected",
                    onClick = { onNavigate(SettingsPage.Cloud) })
            }
            Spacer(Modifier.height(20.dp))
        }
        item {
            SectionLabel("CUSTOMISE")
            MenuCard {
                MenuRow(icon = Icons.Default.Category, iconBg = Canvas, iconTint = Ink, title = "Categories & tags",
                    subtitle = "${categories.size} ${if (categories.size != 1) "categories" else "category"}",
                    onClick = onOpenCategories)
                HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 12.dp))
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
            val context = LocalContext.current
            Text(appFooter(stringResource(R.string.app_name), appVersionName(context)), fontFamily = AnekLatin, fontSize = 10.sp, color = Whisper,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ── Sub-pages ───────────────────────────────────────────────────────────────

@Composable
private fun CloudSubPage(
    driveConnected: Boolean, driveAccountEmail: String, autoBackupEnabled: Boolean, driveBusy: Boolean,
    modifier: Modifier = Modifier, onBack: () -> Unit,
    onBackUpNow: () -> Unit, onRestoreFromDrive: () -> Unit,
    onConnectDrive: () -> Unit, onDisconnectDrive: () -> Unit, onSetAutoBackup: (Boolean) -> Unit
) {
    LazyColumn(modifier = modifier.fillMaxSize().background(Canvas), contentPadding = PaddingValues(16.dp)) {
        item { SubPageHeader("Cloud Backup", onBack) ; Spacer(Modifier.height(8.dp)) }
        item {
            SectionLabel("GOOGLE DRIVE")
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Paper).padding(20.dp)) {
                if (driveConnected) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(GoldSoft), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Cloud, null, tint = GoldInk, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Google Drive", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Ink)
                            Text(driveAccountEmail, fontFamily = AnekLatin, fontSize = 10.sp, color = Muted)
                        }
                        TextButton(onClick = onDisconnectDrive) { Text("Disconnect", color = Pink, fontSize = 12.sp) }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                    ToggleRow("Automatic Cloud Backup", "Daily backup to a private app folder in your Drive", autoBackupEnabled, onSetAutoBackup)
                    HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                    SettingRow(icon = { Icon(Icons.Default.CloudUpload, null, tint = Ink, modifier = Modifier.size(17.dp)) },
                        title = "Back up now", subtitle = if (driveBusy) "Working…" else "Save everything to Drive",
                        onClick = { if (!driveBusy) onBackUpNow() })
                    HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                    SettingRow(icon = { Icon(Icons.Default.Restore, null, tint = Ink, modifier = Modifier.size(17.dp)) },
                        title = "Restore from Drive", subtitle = if (driveBusy) "Working…" else "Replace this phone's data with your Drive backup",
                        onClick = { if (!driveBusy) onRestoreFromDrive() })
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Canvas), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CloudOff, null, tint = Muted, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Google Drive", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Ink)
                            Text("Not connected", fontFamily = AnekLatin, fontSize = 10.sp, color = Muted)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Connect your Google account to back up to a private app folder in your Drive. Only MyMoney can see it.",
                        fontSize = 11.sp, color = Muted, lineHeight = 16.sp)
                    Spacer(Modifier.height(14.dp))
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Gold).clickable(onClick = onConnectDrive).padding(vertical = 12.dp),
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
    budgetAlertsEnabled: Boolean, budgetAlertThreshold: Int, priceAlertsEnabled: Boolean,
    weekStartsOnMonday: Boolean, monthStartDay: Int, privacyMode: Boolean, biometricLockEnabled: Boolean, widgetEnabled: Boolean,
    modifier: Modifier = Modifier, onBack: () -> Unit,
    onSetBudgetAlerts: (Boolean) -> Unit, onSetBudgetThreshold: (Int) -> Unit, onSetPriceAlerts: (Boolean) -> Unit,
    onSetWeekStart: (Boolean) -> Unit, onSetMonthStart: (Int) -> Unit, onSetPrivacyMode: (Boolean) -> Unit,
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
                            colors = SwitchDefaults.colors(checkedThumbColor = Ink, checkedTrackColor = Gold, uncheckedThumbColor = Ink, uncheckedTrackColor = Hairline))
                    }
                    androidx.compose.animation.AnimatedVisibility(visible = budgetAlertsEnabled) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Warning threshold", fontSize = 11.5.sp, color = Muted)
                                Text("$budgetAlertThreshold%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Gold, fontFamily = AnekLatin)
                            }
                            Slider(value = budgetAlertThreshold.toFloat(), onValueChange = { onSetBudgetThreshold(it.toInt()) },
                                valueRange = 50f..95f, steps = 8, modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold, inactiveTrackColor = Hairline))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("50%", fontSize = 10.sp, color = Whisper)
                                Text("95%", fontSize = 10.sp, color = Whisper)
                            }
                        }
                    }
                }
                HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Subscription price alerts", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Ink)
                        Text("Tell me when a subscription's price changes", fontSize = 11.5.sp, color = Muted)
                    }
                    Switch(checked = priceAlertsEnabled, onCheckedChange = onSetPriceAlerts,
                        colors = SwitchDefaults.colors(checkedThumbColor = Ink, checkedTrackColor = Gold, uncheckedThumbColor = Ink, uncheckedTrackColor = Hairline))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        item {
            SectionLabel("DISPLAY")
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Paper).padding(horizontal = 16.dp, vertical = 4.dp)) {
                var showWeekStartInfo by rememberSaveable { mutableStateOf(false) }
                Row(modifier = Modifier.fillMaxWidth().clickable { onSetWeekStart(!weekStartsOnMonday) }.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Week starts on", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Ink)
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = Muted, modifier = Modifier.size(15.dp).clickable(
                            indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) { showWeekStartInfo = !showWeekStartInfo })
                    }
                    Text(if (weekStartsOnMonday) "Monday" else "Sunday", fontFamily = AnekLatin, fontSize = 11.sp, color = Gold, letterSpacing = 0.4.sp)
                }
                androidx.compose.animation.AnimatedVisibility(visible = showWeekStartInfo) {
                    Text("Controls which day the 'This week' total resets on in the Expenses screen. Set to Monday for a work-week view, or Sunday for a calendar-week view.",
                        fontSize = 11.5.sp, color = Muted,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Canvas).padding(10.dp, 8.dp).padding(bottom = 6.dp))
                }
                HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                var showMonthStart by rememberSaveable { mutableStateOf(false) }
                Row(modifier = Modifier.fillMaxWidth().clickable { showMonthStart = true }.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Month starts on", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Ink)
                        Text("Budgets, the forecast and monthly stats run from this day", fontSize = 11.5.sp, color = Muted)
                    }
                    Text(if (monthStartDay == 1) "1st (calendar month)" else BudgetPeriod.ordinal(monthStartDay),
                        fontFamily = AnekLatin, fontSize = 11.sp, color = Gold, letterSpacing = 0.4.sp)
                }
                if (showMonthStart) MonthStartDialog(monthStartDay, onDismiss = { showMonthStart = false }) {
                    onSetMonthStart(it); showMonthStart = false
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
                ToggleRow("App Lock", "Ask for fingerprint, face or screen lock to open MyMoney", biometricLockEnabled, onSetBiometricLock)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun DataSubPage(
    modifier: Modifier = Modifier, onBack: () -> Unit,
    onExport: () -> Unit, onImport: () -> Unit, onExportCsv: (ExportPreset, Pair<Long, Long>?, Int) -> Unit,
    showMockData: Boolean, onMockData: () -> Unit, onReset: () -> Unit
) {
    var showMockDataDialog by rememberSaveable { mutableStateOf(false) }
    var showCsvDialog by rememberSaveable { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    LazyColumn(modifier = modifier.fillMaxSize().background(Canvas), contentPadding = PaddingValues(16.dp)) {
        item { SubPageHeader("Data & Backup", onBack) ; Spacer(Modifier.height(8.dp)) }
        item {
            SectionLabel("BACKUP")
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Paper).padding(horizontal = 16.dp, vertical = 4.dp)) {
                SettingRow(icon = { Icon(Icons.Default.CloudUpload, null, tint = Ink, modifier = Modifier.size(17.dp)) },
                    title = "Export backup", subtitle = "Save everything to a file", onClick = onExport)
                HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                SettingRow(icon = { Icon(Icons.Default.TableChart, null, tint = Ink, modifier = Modifier.size(17.dp)) },
                    title = "Export CSV", subtitle = "Expenses and income for Excel or Google Sheets", onClick = { showCsvDialog = true })
                HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                SettingRow(icon = { Icon(Icons.Default.Restore, null, tint = Ink, modifier = Modifier.size(17.dp)) },
                    title = "Restore from backup", subtitle = "Replace all data with a backup file", onClick = onImport)
            }
            Spacer(Modifier.height(20.dp))
        }
        item {
            SectionLabel(if (showMockData) "DEVELOPER" else "DANGER ZONE")
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Paper).padding(horizontal = 16.dp, vertical = 4.dp)) {
                // Debug builds only: it deletes all real data before loading samples.
                if (showMockData) {
                    SettingRow(icon = { Icon(Icons.Default.Science, null, tint = Gold, modifier = Modifier.size(17.dp)) },
                        title = "Load mock data", subtitle = "Fill app with 3 months of sample expenses for testing",
                        onClick = { showMockDataDialog = true })
                    HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                }
                SettingRow(icon = { Icon(Icons.Default.Delete, null, tint = Pink, modifier = Modifier.size(17.dp)) },
                    title = "Reset everything", subtitle = "Delete all data on this phone",
                    danger = true, onClick = { showResetDialog = true })
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (showCsvDialog) {
        CsvExportDialog(
            onDismiss = { showCsvDialog = false },
            onExport = { preset, range, monthStartDay -> showCsvDialog = false; onExportCsv(preset, range, monthStartDay) }
        )
    }
    if (showMockDataDialog) {
        AppAlertDialog(onDismissRequest = { showMockDataDialog = false }, containerColor = Paper, titleContentColor = Ink, textContentColor = Muted,
            title = { Text("Load mock data?") },
            text = { Text("This will replace all existing data with 3 months of sample expenses, categories, bills, a loan, a salary and savings goals.") },
            confirmButton = { TextButton(onClick = { onMockData(); showMockDataDialog = false }) { Text("Load", color = Gold) } },
            dismissButton = { TextButton(onClick = { showMockDataDialog = false }) { Text("Cancel", color = Muted) } })
    }
    if (showResetDialog) {
        AppAlertDialog(onDismissRequest = { showResetDialog = false }, containerColor = Paper, titleContentColor = Ink, textContentColor = Muted,
            title = { Text("Reset everything?") },
            text = { Text("This permanently deletes all expenses, income, categories, bills, loans, savings goals and message rules on this phone. Auto backup is turned off, so your backups keep your data. App Lock, privacy mode and Google Drive stay as they are.") },
            confirmButton = { TextButton(onClick = { onReset(); showResetDialog = false }) { Text("Delete all", color = Pink) } },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel", color = Muted) } })
    }
}

// ── Shared UI helpers ────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontFamily = AnekLatin, fontSize = 10.sp, letterSpacing = 1.6.sp, color = Muted, modifier = Modifier.padding(bottom = 10.dp))
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
        Text(title, fontFamily = AnekLatin, fontSize = 22.sp, color = Ink)
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
            colors = SwitchDefaults.colors(checkedThumbColor = Ink, checkedTrackColor = Gold, uncheckedThumbColor = Ink, uncheckedTrackColor = Hairline))
    }
}

@Composable
private fun SettingRow(icon: @Composable () -> Unit, title: String, subtitle: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(if (danger) PinkSoft else Canvas), contentAlignment = Alignment.Center) { icon() }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = if (danger) Pink else Ink)
            Text(subtitle, fontSize = 11.5.sp, color = Muted)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Whisper, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = Muted)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Ink)
    }
}

/** Pick what to export: a preset range, or any dates with the date-range picker. */
@Composable
private fun CsvExportDialog(onDismiss: () -> Unit, onExport: (ExportPreset, Pair<Long, Long>?, monthStartDay: Int) -> Unit) {
    var preset by rememberSaveable { mutableStateOf(ExportPreset.THIS_MONTH) }
    var custom by rememberSaveable { mutableStateOf<Pair<Long, Long>?>(null) }
    val context = LocalContext.current

    AppAlertDialog(
        onDismissRequest = onDismiss, containerColor = Paper, titleContentColor = Ink, textContentColor = Muted,
        title = { Text("Export CSV") },
        text = {
            Column {
                DatePresetOptions(preset, custom, pickerTitle = "Dates to export") { p, c ->
                    preset = p; custom = c
                }
                Text("Expenses and income, with date, amount, payee, category, payment method and tags.", fontSize = 12.sp, color = Muted,
                    modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            val ready = preset != ExportPreset.CUSTOM || custom != null
            TextButton(enabled = ready, onClick = {
                val monthStartDay = MonthStartDay.get(context)
                onExport(preset, ExportRange.forPreset(preset, System.currentTimeMillis(), custom, monthStartDay), monthStartDay)
            }) { Text("Export", color = if (ready) Gold else Muted) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }
    )
}
