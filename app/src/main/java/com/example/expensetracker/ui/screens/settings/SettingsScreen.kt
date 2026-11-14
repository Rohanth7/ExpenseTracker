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
import com.example.expensetracker.ui.components.*
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
    if (home == SettingsHome.Plan) LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshBudgets() }
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

    Scaffold(containerColor = Canvas, contentWindowInsets = NoInsets, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
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
                            Text(if (day == 1) "1st (calendar month)" else BudgetPeriod.ordinal(day), color = Ink, fontSize = 15.sp)
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
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 40.dp)
    ) {
        item { ScreenHeader("Settings", onBack = onBack) }
        item { AutoTrackingCard(tracking, onFixSms, onFixNotifications) }
        item {
            MCard(Modifier.padding(top = 10.dp), padding = RowCardPadding) {
                MenuRow(
                    "Message rules",
                    if (ruleCount == 0) "Teach MyMoney a message it reads wrong" else "$ruleCount rule${if (ruleCount == 1) "" else "s"} you've taught",
                    onOpenMessageRules
                )
                RowDivider()
                MenuRow("Categories & tags", "${categories.size} ${if (categories.size != 1) "categories" else "category"}", onOpenCategories)
                RowDivider()
                MenuRow("Cloud Backup", if (driveConnected) driveAccountEmail else "Not connected", onClick = { onNavigate(SettingsPage.Cloud) })
                RowDivider()
                MenuRow("Preferences", "Alerts, privacy, widget, week start", onClick = { onNavigate(SettingsPage.Preferences) })
                RowDivider()
                MenuRow("Data & Backup", "Export, import, reset all data", onClick = { onNavigate(SettingsPage.Data) })
            }
        }
        item {
            val context = LocalContext.current
            Text(
                appFooter(stringResource(R.string.app_name), appVersionName(context)), fontSize = 13.sp, color = Whisper,
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp), textAlign = TextAlign.Center
            )
        }
    }
}

/** One line when both sources are on; otherwise what is off, with a button for the first fix and a link for the second. */
@Composable
private fun AutoTrackingCard(tracking: TrackingStatus, onFixSms: () -> Unit, onFixNotifications: () -> Unit) {
    MCard {
        if (tracking.sms && tracking.notifications) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToneDot(Mint)
                Text("Auto-tracking is on", fontSize = 15.sp, color = Ink)
            }
            return@MCard
        }
        val bothOff = !tracking.sms && !tracking.notifications
        Text("Auto-tracking needs ${if (bothOff) "two fixes" else "one fix"}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Text(
            when {
                bothOff -> "Bank SMS and payment app notifications are off."
                !tracking.sms -> "Payment app notifications are on. Bank SMS is off."
                else -> "Bank SMS is on. Payment app notifications are off."
            },
            fontSize = 13.sp, color = Muted
        )
        Spacer(Modifier.height(12.dp))
        if (!tracking.sms) PrimaryButton("Allow SMS access", onFixSms) else PrimaryButton("Turn on notifications", onFixNotifications)
        if (bothOff) {
            TextAction("Turn on notifications", onFixNotifications, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
        }
    }
}

@Composable
private fun CloudSubPage(
    driveConnected: Boolean, driveAccountEmail: String, autoBackupEnabled: Boolean, driveBusy: Boolean,
    modifier: Modifier = Modifier, onBack: () -> Unit,
    onBackUpNow: () -> Unit, onRestoreFromDrive: () -> Unit,
    onConnectDrive: () -> Unit, onDisconnectDrive: () -> Unit, onSetAutoBackup: (Boolean) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Canvas),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 40.dp)
    ) {
        item { ScreenHeader("Cloud Backup", onBack = onBack) }
        item {
            SectionLabel("Google Drive")
            if (driveConnected) {
                MCard(padding = RowCardPadding) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Google Drive", fontSize = 15.sp, color = Ink)
                            Text(driveAccountEmail, fontSize = 13.sp, color = Muted)
                        }
                        TextAction("Disconnect", onDisconnectDrive, color = Pink)
                    }
                    RowDivider()
                    ToggleRow("Automatic Cloud Backup", "Daily backup to a private app folder in your Drive", autoBackupEnabled, onSetAutoBackup)
                    RowDivider()
                    MenuRow("Back up now", if (driveBusy) "Working…" else "Save everything to Drive", onClick = { if (!driveBusy) onBackUpNow() })
                    RowDivider()
                    MenuRow(
                        "Restore from Drive", if (driveBusy) "Working…" else "Replace this phone's data with your Drive backup",
                        onClick = { if (!driveBusy) onRestoreFromDrive() }
                    )
                }
            } else {
                MCard {
                    Text("Google Drive", fontSize = 15.sp, color = Ink)
                    Text("Not connected", fontSize = 13.sp, color = Muted)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Connect your Google account to back up to a private app folder in your Drive. Only MyMoney can see it.",
                        fontSize = 13.sp, color = Muted, lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton("Connect Google Drive", onConnectDrive)
                }
            }
        }
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
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Canvas),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 40.dp)
    ) {
        item { ScreenHeader("Preferences", onBack = onBack) }
        item {
            SectionLabel("Notifications")
            MCard(padding = RowCardPadding) {
                ToggleRow(
                    "Budget alerts",
                    if (budgetAlertsEnabled) "Alert at $budgetAlertThreshold% & 100% of limit" else "Disabled",
                    budgetAlertsEnabled, onSetBudgetAlerts
                )
                androidx.compose.animation.AnimatedVisibility(visible = budgetAlertsEnabled) {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Warning threshold", fontSize = 13.sp, color = Muted)
                            Text("$budgetAlertThreshold%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Gold)
                        }
                        Slider(value = budgetAlertThreshold.toFloat(), onValueChange = { onSetBudgetThreshold(it.toInt()) },
                            valueRange = 50f..95f, steps = 8, modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold, inactiveTrackColor = Hairline))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("50%", fontSize = 13.sp, color = Whisper)
                            Text("95%", fontSize = 13.sp, color = Whisper)
                        }
                    }
                }
                RowDivider()
                ToggleRow("Subscription price alerts", "Tell me when a subscription's price changes", priceAlertsEnabled, onSetPriceAlerts)
            }
        }
        item {
            SectionLabel("Display")
            MCard(padding = RowCardPadding) {
                var showWeekStartInfo by rememberSaveable { mutableStateOf(false) }
                Row(modifier = Modifier.fillMaxWidth().clickable { onSetWeekStart(!weekStartsOnMonday) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Week starts on", fontSize = 15.sp, color = Ink)
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = Muted, modifier = Modifier.size(18.dp).clickable(
                            indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) { showWeekStartInfo = !showWeekStartInfo })
                    }
                    Text(if (weekStartsOnMonday) "Monday" else "Sunday", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Gold)
                }
                androidx.compose.animation.AnimatedVisibility(visible = showWeekStartInfo) {
                    Text("Controls which day the 'This week' total resets on in the Expenses screen. Set to Monday for a work-week view, or Sunday for a calendar-week view.",
                        fontSize = 13.sp, color = Muted, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
                }
                RowDivider()
                var showMonthStart by rememberSaveable { mutableStateOf(false) }
                MenuRow("Month starts on", "Budgets, the forecast and monthly stats run from this day", onClick = { showMonthStart = true }) {
                    Text(if (monthStartDay == 1) "1st (calendar month)" else BudgetPeriod.ordinal(monthStartDay),
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Gold)
                }
                if (showMonthStart) MonthStartDialog(monthStartDay, onDismiss = { showMonthStart = false }) {
                    onSetMonthStart(it); showMonthStart = false
                }
                RowDivider()
                ToggleRow("Home Screen Widget", "Show progress on home screen", widgetEnabled, onSetWidget)
            }
        }
        item {
            SectionLabel("Security")
            MCard(padding = RowCardPadding) {
                ToggleRow("Privacy mode", "Hides screen in app switcher", privacyMode, onSetPrivacyMode)
                RowDivider()
                ToggleRow("App Lock", "Ask for fingerprint, face or screen lock to open MyMoney", biometricLockEnabled, onSetBiometricLock)
            }
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

    LazyColumn(
        modifier = modifier.fillMaxSize().background(Canvas),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 40.dp)
    ) {
        item { ScreenHeader("Data & Backup", onBack = onBack) }
        item {
            SectionLabel("Backup")
            MCard(padding = RowCardPadding) {
                MenuRow("Export backup", "Save everything to a file", onExport)
                RowDivider()
                MenuRow("Export CSV", "Expenses and income for Excel or Google Sheets", onClick = { showCsvDialog = true })
                RowDivider()
                MenuRow("Restore from backup", "Replace all data with a backup file", onImport)
            }
        }
        item {
            SectionLabel(if (showMockData) "Developer" else "Danger zone")
            MCard(padding = RowCardPadding) {
                // Debug builds only: it deletes all real data before loading samples.
                if (showMockData) {
                    MenuRow("Load mock data", "Fill app with 3 months of sample expenses for testing", onClick = { showMockDataDialog = true })
                    RowDivider()
                }
                Column(Modifier.fillMaxWidth().clickable { showResetDialog = true }.padding(vertical = 12.dp)) {
                    Text("Reset everything", fontSize = 15.sp, color = Pink)
                    Text("Delete all data on this phone", fontSize = 13.sp, color = Muted)
                }
            }
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

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 15.sp, color = Ink)
            Text(subtitle, fontSize = 13.sp, color = Muted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = switchColors())
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
                Text("Expenses and income, with date, amount, payee, category, payment method and tags.", fontSize = 13.sp, color = Muted,
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
