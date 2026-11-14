package com.example.expensetracker.ui.screens.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.budget.Budget
import com.example.expensetracker.data.db.entity.SavingsGoal
import com.example.expensetracker.data.period.BudgetPeriod
import com.example.expensetracker.data.period.MonthStartDay
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.ui.components.*
import com.example.expensetracker.ui.theme.*
import com.example.expensetracker.ui.util.AmountCalculator
import com.example.expensetracker.ui.util.fmtINR
import com.example.expensetracker.ui.util.formatLimitInput
import com.example.expensetracker.ui.util.parseAmountOrZero
import com.example.expensetracker.ui.util.rememberAmountText
import com.example.expensetracker.ui.util.rememberSaveableItem

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onViewPending: () -> Unit,
    onEnableTracking: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onViewSubscriptions: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showIncomeDialog by rememberSaveable { mutableStateOf(false) }
    var showAddGoalDialog by rememberSaveable { mutableStateOf(false) }
    var showNotifCenter by rememberSaveable { mutableStateOf(false) }
    var goalToUpdate by rememberSaveableItem(state.savingsGoals) { it.id }
    var goalToDelete by rememberSaveableItem(state.savingsGoals) { it.id }
    // Re-read on every resume so the banner disappears after the user turns tracking on.
    val tracking = com.example.expensetracker.ui.util.rememberTrackingStatus()
    val prefs = remember { PreferencesManager(context.applicationContext) }
    var alertPercent by remember { mutableIntStateOf(prefs.budgetAlertThreshold) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // "As of now" figures move on when the screen comes back (past midnight, a new budget month,
    // a changed alert threshold).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
                alertPercent = prefs.budgetAlertThreshold
                // Its own state: the view model's can come back equal, and days left still has to move on.
                now = System.currentTimeMillis()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isCurrentMonth = !state.canGoForward
    val hasIncome = state.monthlyIncome > 0
    val stillDue = state.billsStillDue + state.emiStillDue
    val freeToSpend = state.monthlyIncome - state.totalSpent - stillDue
    val startDay by MonthStartDay.flow(context).collectAsState()
    val daysLeft = BudgetPeriod.daysLeft(now, startDay).coerceAtLeast(1)
    val comingUp = remember(state.upcomingBills, state.activeLoans, isCurrentMonth, now, startDay) {
        if (isCurrentMonth) ComingUp.of(state.upcomingBills, state.activeLoans, now, BudgetPeriod.current(now, startDay)) else emptyList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Canvas),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 24.dp)
    ) {
        item {
            MonthHeader(
                label = state.monthLabel,
                canGoForward = state.canGoForward,
                onPrevious = { viewModel.goToPreviousMonth() },
                onNext = { viewModel.goToNextMonth() },
                hasUrgent = state.notifItems.any { it.isUrgent },
                onOpenAlerts = { showNotifCenter = true },
                onOpenSettings = onOpenSettings
            )
        }

        // Only when neither source is on: someone using just one of them shouldn't be nagged.
        if (!tracking.status.sms && !tracking.status.notifications) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(CardShape)
                        .background(PinkSoft)
                        .clickable(onClick = onEnableTracking)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-tracking is off", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text("Payments won't be logged automatically. Tap to turn it on.", fontSize = 13.sp, color = Muted)
                    }
                    TextAction("Enable", onEnableTracking, color = Pink)
                }
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    !isCurrentMonth -> {
                        Spacer(Modifier.height(16.dp))
                        Text("Spent in ${state.monthLabel}", fontSize = 13.sp, color = Muted)
                        BigNumber("₹${fmtINR(state.totalSpent)}")
                    }
                    !hasIncome -> {
                        Spacer(Modifier.height(16.dp))
                        Text("Spent this month", fontSize = 13.sp, color = Muted)
                        BigNumber("₹${fmtINR(state.totalSpent)}")
                        TextAction("Set your monthly income", { showIncomeDialog = true })
                    }
                    else -> {
                        val over = freeToSpend < 0
                        Dial(leftFraction = if (over) 0f else (freeToSpend / state.monthlyIncome).coerceIn(0.0, 1.0).toFloat())
                        Spacer(Modifier.height(8.dp))
                        Text(if (over) "Over this month" else "Free to spend", fontSize = 13.sp, color = Muted)
                        BigNumber("₹${fmtINR(if (over) -freeToSpend else freeToSpend)}", color = if (over) Pink else Ink)
                        if (over) {
                            Text("$daysLeft ${if (daysLeft == 1) "day" else "days"} until the month resets", fontSize = 13.sp, color = Muted)
                        } else {
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = Gold, fontWeight = FontWeight.SemiBold)) { append("₹${fmtINR(kotlin.math.floor(state.safeToSpendToday))} a day") }
                                    withStyle(SpanStyle(color = Muted)) { append(" · $daysLeft ${if (daysLeft == 1) "day" else "days"} left") }
                                },
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        if (state.pendingCount > 0) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Pink)
                        .clickable(onClick = onViewPending)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${state.pendingCount} ${pendingBannerText(state.pendingCount)}",
                        fontSize = 15.sp, color = Color.White, modifier = Modifier.weight(1f)
                    )
                    Text("Sort →", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }

        if (state.budgets.isNotEmpty()) {
            item {
                MCard(Modifier.padding(top = 10.dp), padding = RowCardPadding) {
                    state.budgets.forEachIndexed { i, budget ->
                        if (i > 0) RowDivider()
                        BudgetRow(budget, alertPercent)
                    }
                }
            }
        }

        if (isCurrentMonth && hasIncome) {
            item {
                SectionLabel("Cash flow this month")
                MCard {
                    CashFlowRow("Income", fmtINR(state.monthlyIncome), valueColor = Mint, onClick = { showIncomeDialog = true })
                    CashFlowRow("Spent so far", "− ${fmtINR(state.totalSpent)}")
                    if (state.hasCommitments) CashFlowRow("Bills and EMI still due", "− ${fmtINR(stillDue)}")
                    Spacer(Modifier.height(6.dp))
                    RowDivider()
                    Spacer(Modifier.height(6.dp))
                    if (freeToSpend < 0) CashFlowRow("Over this month", fmtINR(-freeToSpend), valueColor = Pink, bold = true)
                    else CashFlowRow("Free to spend", fmtINR(freeToSpend), valueColor = Gold, bold = true)
                }
            }
        }

        if (comingUp.isNotEmpty()) {
            item {
                SectionLabel("Coming up")
                MCard(padding = RowCardPadding) {
                    comingUp.forEachIndexed { i, item ->
                        if (i > 0) RowDivider()
                        val late = item.daysUntilDue < 0
                        AccentRow(
                            accent = if (item.emi != null || late) Pink else Gold,
                            title = item.name,
                            subtitle = ComingUp.subtitle(item),
                            trailing = "₹${fmtINR(item.amount)}",
                            subtitleColor = if (late) Pink else Muted
                        )
                    }
                }
            }
        }

        item { SectionLabel("Savings goals", action = "Add goal", onAction = { showAddGoalDialog = true }) }
        if (state.savingsGoals.isEmpty()) {
            item {
                MCard {
                    Text(
                        "No goals yet. Save for your future!", color = Muted, fontSize = 13.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                }
            }
        } else {
            items(state.savingsGoals.size) { i ->
                val goal = state.savingsGoals[i]
                SavingsGoalCard(
                    goal = goal,
                    onAdd = { goalToUpdate = goal },
                    onDelete = { goalToDelete = goal },
                    modifier = Modifier.padding(top = if (i == 0) 0.dp else 10.dp)
                )
            }
        }

        if (state.subscriptions.isNotEmpty()) {
            item {
                SectionLabel("Subscriptions")
                MCard(onClick = onViewSubscriptions) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            val yearly = state.subscriptions.sumOf { it.yearlyCost }
                            Text("₹${fmtINR(Math.round(yearly / 12).toDouble())} a month", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text("${state.subscriptions.size} active · ₹${fmtINR(Math.round(yearly).toDouble())} a year", fontSize = 13.sp, color = Muted)
                            if (state.subscriptions.any { it.priceRose }) {
                                Text("↑ price up", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Pink,
                                    modifier = Modifier.semantics { contentDescription = "Price up" })
                            }
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = Ink, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        if (state.categorySummaries.isEmpty() && state.pendingCount == 0) {
            item {
                Text(
                    "No expenses this month.\nTransactions from SMS will appear here.",
                    color = Muted,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth().padding(32.dp)
                )
            }
        }
    }

    if (showNotifCenter) {
        NotificationBottomSheet(
            items = state.notifItems,
            onDismiss = { showNotifCenter = false }
        )
    }

    if (showIncomeDialog) {
        IncomeDialog(
            currentIncome = state.expectedIncome,
            // The month on screen, which may not be this one
            monthWithIncome = state.monthLabel.takeIf { state.incomeFromRecords },
            onDismiss = { showIncomeDialog = false },
            onConfirm = { income -> viewModel.setExpectedIncome(income); showIncomeDialog = false }
        )
    }

    if (showAddGoalDialog) {
        AddGoalDialog(
            onDismiss = { showAddGoalDialog = false },
            onConfirm = { name, target ->
                viewModel.addSavingsGoal(name, target)
                showAddGoalDialog = false
            }
        )
    }

    goalToDelete?.let { goal ->
        AppAlertDialog(onDismissRequest = { goalToDelete = null }, containerColor = Paper, titleContentColor = Ink, textContentColor = Muted,
            title = { Text("Delete goal?") },
            text = {
                Text(if (goal.currentAmount > 0) "\"${goal.name}\" and the ₹${fmtINR(goal.currentAmount)} saved in it will be removed."
                     else "\"${goal.name}\" will be removed.")
            },
            confirmButton = { TextButton(onClick = { viewModel.deleteSavingsGoal(goal); goalToDelete = null }) { Text("Delete", color = Pink) } },
            dismissButton = { TextButton(onClick = { goalToDelete = null }) { Text("Cancel", color = Muted) } })
    }

    goalToUpdate?.let { goal ->
        DepositDialog(
            goalName = goal.name,
            surplus = state.monthlySurplus,
            onDismiss = { goalToUpdate = null },
            onConfirm = { amount ->
                viewModel.addAmountToGoal(goal, amount)
                goalToUpdate = null
            }
        )
    }
}

/**
 * The Overview banner's words beside the pending count. Pending expenses come from SMS, payment
 * apps, cards and hand entry ("Choose later"), so it doesn't name a source.
 */
internal fun pendingBannerText(count: Int): String =
    if (count == 1) "expense needs categorizing" else "expenses need categorizing"

/**
 * The month between its chevrons, then the bell and settings. The label takes what is left after
 * the icons and shrinks to fit: a budget month that isn't a calendar month reads
 * "25 Dec 2025 – 24 Jan 2026", which at full size would leave the icons no room.
 */
@Composable
private fun MonthHeader(
    label: String, canGoForward: Boolean, onPrevious: () -> Unit, onNext: () -> Unit,
    hasUrgent: Boolean, onOpenAlerts: () -> Unit, onOpenSettings: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            HeaderIcon(Icons.Default.ChevronLeft, "Previous month", onClick = onPrevious, alignStart = true)
            var scale by remember(label) { mutableFloatStateOf(1f) }
            Text(
                label, fontSize = (22 * scale).sp, fontWeight = FontWeight.SemiBold, color = Ink,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                // Ellipsis is the last resort: it reports as an ellipsized line, not as overflow
                onTextLayout = { if (it.isLineEllipsized(0) && scale > 0.55f) scale *= 0.92f },
                modifier = Modifier.weight(1f, fill = false)
            )
            HeaderIcon(Icons.Default.ChevronRight, "Next month", onClick = onNext, enabled = canGoForward)
        }
        HeaderIcon(Icons.Outlined.Notifications, "Notifications", onClick = onOpenAlerts, badge = hasUrgent)
        HeaderIcon(Icons.Default.Tune, "Settings", onClick = onOpenSettings)
    }
}

/** A half circle whose gold end is the share of the month's income still free to spend. Its centre is left for the mascot. */
@Composable
private fun Dial(leftFraction: Float) {
    Canvas(Modifier.size(220.dp, 117.dp)) {
        val stroke = 14.dp.toPx()
        val topLeft = Offset(stroke / 2, stroke / 2)
        val arc = Size(size.width - stroke, size.width - stroke)
        drawArc(Hairline, 180f, 180f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        val sweep = 180f * leftFraction
        if (sweep > 0f) drawArc(Gold, 360f - sweep, sweep, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun BudgetRow(budget: Budget, alertPercent: Int) {
    val tone = budgetTone(budget.spent, budget.limit, alertPercent)
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        ToneDot(tone)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // "Food › Coffee" for a subcategory's own limit
                Text(budget.name, fontSize = 15.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${fmtINR(budget.spent)} of ${fmtINR(budget.limit)}", fontSize = 13.sp, color = Muted)
            }
            Spacer(Modifier.height(6.dp))
            ProgressBar((budget.spent / budget.limit).toFloat(), tone)
        }
    }
}

@Composable
private fun CashFlowRow(label: String, value: String, valueColor: Color = Ink, bold: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, color = Ink)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
private fun SavingsGoalCard(
    goal: SavingsGoal,
    onAdd: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    MCard(modifier, onClick = onAdd) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(goal.name, fontSize = 15.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("${fmtINR(goal.currentAmount)} of ${fmtINR(goal.targetAmount)}", fontSize = 13.sp, color = Muted)
            Icon(
                Icons.Outlined.DeleteOutline, "Delete goal ${goal.name}", tint = Muted,
                modifier = Modifier.padding(start = 10.dp).size(20.dp).clickable(onClick = onDelete)
            )
        }
        Spacer(Modifier.height(8.dp))
        ProgressBar((goal.currentAmount / goal.targetAmount).toFloat(), Mint)
    }
}

@Composable
internal fun AddGoalDialog(onDismiss: () -> Unit, onConfirm: (String, Double) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    val targetText = rememberAmountText(target) { AmountCalculator.evaluate(it) }
    val canCreate = name.isNotBlank() && targetText.value != null

    AppAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        title = { Text("New Savings Goal", color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("What are you saving for?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FieldShape, colors = fieldColors()
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Target Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = targetText.invalid,
                    supportingText = targetText.supportingText,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FieldShape, colors = fieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = targetText.value ?: return@TextButton
                onConfirm(name.trim(), amt)
            }, enabled = canCreate) { Text("Create", color = if (canCreate) Gold else Muted) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }
    )
}

@Composable
private fun DepositDialog(
    goalName: String,
    surplus: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var amount by rememberSaveable { mutableStateOf("") }
    val amountText = rememberAmountText(amount) { AmountCalculator.evaluate(it) }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        title = { Text("Add to \"$goalName\"", color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)") },
                    placeholder = if (surplus > 0) ({ Text("e.g. ₹${fmtINR(surplus)}", color = Muted) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = amountText.invalid,
                    supportingText = amountText.supportingText,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FieldShape, colors = fieldColors()
                )
                if (surplus > 0) {
                    Text(
                        "Monthly surplus: ₹${fmtINR(surplus)}",
                        fontSize = 13.sp,
                        color = Muted
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                amountText.value?.let(onConfirm)
            }, enabled = amountText.value != null) { Text("Add", color = if (amountText.value != null) Gold else Muted) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }
    )
}

@Composable
private fun IncomeDialog(currentIncome: Double, monthWithIncome: String?, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    // Was "%.0f": rounded off the paise and wrote the phone's own digits.
    var text by rememberSaveable { mutableStateOf(formatLimitInput(currentIncome)) }
    // 0 clears the income. Blank did nothing before, so a cleared field can't wipe it by accident.
    val income = rememberAmountText(text) { parseAmountOrZero(it) }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        title = { Text("Expected monthly income", color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Income (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = income.invalid,
                    supportingText = income.supportingText,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FieldShape, colors = fieldColors()
                )
                Text(
                    if (monthWithIncome != null) "Not used for $monthWithIncome: it has income logged or on its way, so the forecast uses that."
                    else "Used until you log income. Add your salary as recurring income in Plan → Bills & Reminders.",
                    fontSize = 13.sp, color = Muted
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                income.value?.let(onConfirm)
            }, enabled = income.value != null) { Text("Save", color = if (income.value != null) Gold else Muted) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }
    )
}

@Composable
private fun NotificationBottomSheet(items: List<NotifItem>, onDismiss: () -> Unit) {
    MSheet(onDismiss) {
        SheetTitle("Needs your attention")
        if (items.isEmpty()) {
            MCard {
                Text("You're all caught up!", fontSize = 13.sp, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            }
        } else {
            NotifSection("Recent captures", items.filter { it.type == NotifType.CAPTURE }, first = true)
            NotifSection("Budget alerts", items.filter { it.type == NotifType.BUDGET })
            NotifSection("Upcoming bills", items.filter { it.type == NotifType.BILL })
        }
    }
}

@Composable
private fun NotifSection(label: String, items: List<NotifItem>, first: Boolean = false) {
    if (items.isEmpty()) return
    if (first) Text(label.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = Muted, modifier = Modifier.padding(bottom = 8.dp))
    else SectionLabel(label)
    MCard(padding = RowCardPadding) {
        items.forEachIndexed { i, item ->
            if (i > 0) RowDivider()
            AccentRow(
                accent = if (item.isUrgent) Pink else Muted,
                title = item.title,
                subtitle = item.subtitle,
                trailing = "",
                subtitleColor = if (item.isUrgent) Pink else Muted
            )
        }
    }
}
