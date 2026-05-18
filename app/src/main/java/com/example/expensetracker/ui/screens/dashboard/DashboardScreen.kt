package com.example.expensetracker.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.db.entity.SavingsGoal
import com.example.expensetracker.ui.screens.dashboard.UpcomingBill
import com.example.expensetracker.ui.components.PieChart
import com.example.expensetracker.ui.components.PieSlice
import com.example.expensetracker.ui.theme.*
import com.example.expensetracker.ui.util.NotificationAccessUtil
import com.example.expensetracker.ui.util.SmsPermissionUtil
import com.example.expensetracker.ui.util.parseColor
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onViewPending: () -> Unit,
    onViewAllSpending: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showIncomeDialog by remember { mutableStateOf(false) }
    var showAddGoalDialog by remember { mutableStateOf(false) }
    var goalToUpdate by remember { mutableStateOf<SavingsGoal?>(null) }
    val notificationAccessGranted = remember { mutableStateOf(NotificationAccessUtil.isGranted(context)) }
    val smsPermissionGranted = remember { mutableStateOf(SmsPermissionUtil.isGranted(context)) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notificationAccessGranted.value = NotificationAccessUtil.isGranted(context)
                smsPermissionGranted.value = SmsPermissionUtil.isGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val today = remember { Calendar.getInstance() }
    val dayOfMonth = today.get(Calendar.DAY_OF_MONTH)
    val dailyPace = if (!state.canGoForward && dayOfMonth > 0 && state.totalSpent > 0)
        state.totalSpent / dayOfMonth else 0.0
    val spentFraction = if (state.monthlyIncome > 0)
        (state.totalSpent / state.monthlyIncome).coerceIn(0.0, 1.0).toFloat() else 0f

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Canvas),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // ── Permission Warning ──────────────────────────────────
        if (!smsPermissionGranted.value) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CoralSoft.copy(alpha = 0.5f))
                        .clickable { SmsPermissionUtil.openSettings(context) }
                        .padding(16.dp, 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Info, null, tint = Coral, modifier = Modifier.size(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "SMS Tracking Disabled",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )
                        Text(
                            "Grant permission to auto-track expenses.",
                            fontSize = 11.sp,
                            color = InkSoft
                        )
                    }
                    Text(
                        "Enable",
                        fontSize = 12.sp,
                        color = Coral,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ── Masthead ────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 14.dp)
            ) {
                Text(
                    "OVERVIEW",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                    letterSpacing = 1.8.sp,
                    color = Muted,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Month label + chevrons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Paper)
                                .border(1.dp, Hairline, CircleShape)
                                .clickable { viewModel.goToPreviousMonth() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ChevronLeft, null, tint = Ink, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            state.monthLabel,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 28.sp,
                            color = Ink,
                            lineHeight = 30.sp
                        )
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Paper)
                                .border(1.dp, Hairline, CircleShape)
                                .graphicsLayer { alpha = if (state.canGoForward) 1f else 0.3f }
                                .clickable(enabled = state.canGoForward) { viewModel.goToNextMonth() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ChevronRight, null,
                                tint = if (state.canGoForward) Ink else Muted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    // Notification bell
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Paper)
                            .border(1.dp, Hairline, CircleShape)
                            .clickable {
                                if (!notificationAccessGranted.value) {
                                    NotificationAccessUtil.openSettings(context)
                                } else if (state.pendingCount > 0) {
                                    onViewPending()
                                } else {
                                    android.widget.Toast.makeText(context, "No pending transactions to categorize", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = Ink,
                            modifier = Modifier.size(18.dp)
                        )
                        if (state.pendingCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Coral)
                                    .border(1.5.dp, Paper, CircleShape)
                                    .align(Alignment.TopEnd)
                            )
                        }
                    }
                }
            }
        }

        // ── Hero card ───────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Deep)
                    .padding(20.dp, 20.dp, 20.dp, 20.dp)
            ) {
                Text(
                    if (!state.canGoForward) "SPENT THIS MONTH"
                    else "SPENT IN ${state.monthLabel.split(" ").firstOrNull()?.uppercase() ?: state.monthLabel.uppercase()}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 1.8.sp,
                    color = Ink.copy(alpha = 0.55f),
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.height(18.dp))
                MoneyDisplay(state.totalSpent, size = 64, color = Ink)

                // Progress bar
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(Ink.copy(alpha = 0.10f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(spentFraction)
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (state.overBudget) Coral else Jade)
                    )
                }

                // Caption
                Spacer(Modifier.height(10.dp))
                if (state.monthlyIncome > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showIncomeDialog = true },
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${(spentFraction * 100).roundToInt()}% of ₹${fmtINR(state.monthlyIncome)} income",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Ink.copy(alpha = 0.7f)
                        )
                        Text(
                            "₹${fmtINR((state.monthlyIncome - state.totalSpent).coerceAtLeast(0.0))} left",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Ink,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Text(
                        "Set monthly income →",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = JadeInk,
                        modifier = Modifier.clickable { showIncomeDialog = true }
                    )
                }

                // Daily pace inset tile
                if (!state.canGoForward && dailyPace > 0) {
                    val paceVsPrev = if (state.prevMonthDailyPace > 0)
                        ((dailyPace - state.prevMonthDailyPace) / state.prevMonthDailyPace * 100).roundToInt()
                    else null
                    val paceOver = paceVsPrev != null && paceVsPrev > 10
                    val paceUnder = paceVsPrev != null && paceVsPrev < -10
                    val paceStatusText = when {
                        paceOver -> "over pace"
                        paceUnder -> "under pace"
                        else -> "on track"
                    }
                    val paceStatusColor = if (paceOver) Coral else Jade
                    val paceChipColor = if (paceOver) Coral.copy(alpha = 0.15f) else Jade.copy(alpha = 0.15f)
                    val paceChipTextColor = if (paceOver) Coral else JadeInk

                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceOverlay06)
                            .padding(14.dp, 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "DAILY PACE",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                letterSpacing = 1.6.sp,
                                color = Ink.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                buildAnnotatedString {
                                    append("₹${fmtINR(dailyPace)} per day · ")
                                    withStyle(SpanStyle(color = paceStatusColor)) { append(paceStatusText) }
                                },
                                fontSize = 13.sp,
                                color = Ink,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (paceVsPrev != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(paceChipColor)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                val sign = if (paceVsPrev >= 0) "+" else ""
                                Text(
                                    "${sign}${paceVsPrev}% vs prev",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = paceChipTextColor
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Pending strip ───────────────────────────────────────
        if (state.pendingCount > 0) {
            item {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CoralSoft)
                        .clickable(onClick = onViewPending)
                        .padding(16.dp, 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Coral),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${state.pendingCount}",
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "UPI transactions need categorizing",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink
                        )
                        Text(
                            "Tap to review →",
                            fontSize = 11.sp,
                            color = InkSoft,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Ink, modifier = Modifier.size(18.dp))
                }
            }
        }

        // ── Spending breakdown ──────────────────────────────────
        if (state.categorySummaries.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                DsSectionLabel(
                    title = "Spending breakdown",
                    action = "View all →",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onActionClick = onViewAllSpending
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Paper)
                        .padding(horizontal = 18.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(196.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PieChart(
                            slices = state.categorySummaries.map { s ->
                                PieSlice(color = parseColor(s.category.colorHex), value = s.spent.toFloat(), label = s.category.name)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "TOTAL",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Muted,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            MoneyDisplay(state.totalSpent, size = 38, color = Ink)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    state.categorySummaries.take(4).forEachIndexed { i, summary ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(HairlineSoft))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(summary.category.emoji, fontSize = 16.sp, modifier = Modifier.width(22.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(summary.category.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)
                                Text(
                                    "${(if (state.totalSpent > 0) summary.spent / state.totalSpent * 100 else 0.0).roundToInt()}% of total",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = Muted,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                            }
                            MoneyDisplay(summary.spent, size = 20, color = Ink)
                        }
                    }
                }
            }
        }

        // ── Insights Strip ──────────────────────────────────────
        if (state.monthlyIncome > 0 && !state.canGoForward) {
            item {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(JadeSoft)
                        .padding(16.dp, 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Jade),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "₹",
                            fontFamily = FontFamily.Serif,
                            fontSize = 18.sp,
                            color = Deep
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Safe to spend today",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink
                        )
                        Text(
                            "₹${fmtINR(state.safeToSpendToday)} remaining",
                            fontSize = 11.sp,
                            color = JadeInk,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                }
            }
        }

        // ── Against budgets ─────────────────────────────────────
        val budgeted = state.categorySummaries.filter { it.category.monthlyLimit > 0 }
        if (budgeted.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                DsSectionLabel(
                    title = "Against budgets",
                    action = "View details →",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onActionClick = onViewAllSpending
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Paper)
                        .padding(18.dp, 18.dp, 18.dp, 12.dp)
                ) {
                    budgeted.forEachIndexed { i, summary ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(HairlineSoft))
                        val pct = (summary.spent / summary.category.monthlyLimit).coerceIn(0.0, 1.0).toFloat()
                        val over = summary.spent > summary.category.monthlyLimit
                        val catColor = parseColor(summary.category.colorHex)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = if (i == 0) 0.dp else 12.dp)
                                .then(if (i == 0) Modifier.padding(bottom = 12.dp) else Modifier)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(summary.category.emoji, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
                                Text(summary.category.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.weight(1f))
                                Text(
                                    buildAnnotatedString {
                                        append("₹${fmtINR(summary.spent)} ")
                                        withStyle(SpanStyle(color = Muted.copy(alpha = 0.5f))) {
                                            append("/ ₹${fmtINR(summary.category.monthlyLimit)}")
                                        }
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = if (over) Coral else Muted
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(HairlineSoft)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(pct)
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(if (over) Coral else catColor)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 6-month trend ───────────────────────────────────────
        if (state.monthlyTrends.any { it.total > 0 }) {
            item {
                Spacer(Modifier.height(4.dp))
                DsSectionLabel(title = "6-month trend", modifier = Modifier.padding(horizontal = 16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Paper)
                        .padding(18.dp, 20.dp, 18.dp, 16.dp)
                ) {
                    TrendBars(state.monthlyTrends)
                }
            }
        }

        // ── Savings Goals ───────────────────────────────────────
        item {
            Spacer(Modifier.height(4.dp))
            DsSectionLabel(
                title = "Savings Goals",
                action = "Add Goal +",
                modifier = Modifier.padding(horizontal = 16.dp),
                onActionClick = { showAddGoalDialog = true }
            )
        }

        if (state.savingsGoals.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Paper)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No goals yet. Save for your future!", color = Muted, fontSize = 13.sp)
                }
            }
        } else {
            items(state.savingsGoals.size) { i ->
                val goal = state.savingsGoals[i]
                SavingsGoalCard(
                    goal = goal,
                    onAddSurplus = { viewModel.addAmountToGoal(goal, state.monthlySurplus) },
                    onDelete = { viewModel.deleteSavingsGoal(goal) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        // ── Upcoming Bills ──────────────────────────────────────
        if (state.upcomingBills.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                DsSectionLabel(title = "Upcoming Bills", modifier = Modifier.padding(horizontal = 16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Paper)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    state.upcomingBills.forEachIndexed { index, upcoming ->
                        if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(HairlineSoft))
                        UpcomingBillRow(upcoming)
                    }
                }
            }
        }

        // ── EMI Commitments ─────────────────────────────────────
        if (state.activeLoans.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                DsSectionLabel(title = "EMI Commitments", modifier = Modifier.padding(horizontal = 16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Paper)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Total monthly EMI",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Muted
                        )
                        MoneyDisplay(state.totalMonthlyEmi, size = 20, color = Coral)
                    }
                    state.activeLoans.forEachIndexed { index, loanStatus ->
                        if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(HairlineSoft))
                        LoanStatusRow(loanStatus)
                    }
                }
            }
        }

        // ── Empty state ─────────────────────────────────────────
        if (state.categorySummaries.isEmpty() && state.pendingCount == 0) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No expenses this month.\nTransactions from SMS will appear here.",
                        color = Muted,
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }

    if (showIncomeDialog) {
        IncomeDialog(
            currentIncome = state.monthlyIncome,
            onDismiss = { showIncomeDialog = false },
            onConfirm = { income -> viewModel.setIncome(income); showIncomeDialog = false }
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
}

@Composable
private fun SavingsGoalCard(
    goal: SavingsGoal,
    onAddSurplus: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = (goal.currentAmount / goal.targetAmount).coerceIn(0.0, 1.0).toFloat()
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Paper)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(goal.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text(
                    "₹${fmtINR(goal.currentAmount)} / ₹${fmtINR(goal.targetAmount)}",
                    fontSize = 11.sp,
                    color = Muted,
                    fontFamily = FontFamily.Monospace
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onAddSurplus, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Add, null, tint = Jade, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, null, tint = Muted, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(HairlineSoft)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(100.dp))
                    .background(Jade)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${(progress * 100).toInt()}% achieved",
            fontSize = 10.sp,
            color = JadeInk,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun AddGoalDialog(onDismiss: () -> Unit, onConfirm: (String, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    
    AlertDialog(
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
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedTextColor = Ink, unfocusedTextColor = Ink)
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Target Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Jade, focusedTextColor = Ink, unfocusedTextColor = Ink)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { 
                val amt = target.toDoubleOrNull()
                if (name.isNotBlank() && amt != null && amt > 0) {
                    onConfirm(name.trim(), amt)
                }
            }) { Text("Create", color = Jade) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }
    )
}

@Composable
private fun TrendBars(trends: List<MonthlyTrend>) {
    val maxValue = trends.maxOf { it.total }.coerceAtLeast(1.0)
    val lastIdx = trends.size - 1

    Row(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        trends.forEachIndexed { i, trend ->
            val fraction = (trend.total / maxValue).toFloat().coerceIn(0f, 1f)
            val isHighlight = i == lastIdx
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                if (isHighlight && trend.total > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(AmberSoft)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            fmtINR(trend.total),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            color = Ink,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                val barHeight = ((100 * fraction).dp).coerceAtLeast(if (trend.total > 0) 2.dp else 0.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when {
                                trend.total == 0.0 -> Ink.copy(alpha = 0.05f)
                                isHighlight -> Ink
                                else -> Ink.copy(alpha = 0.20f)
                            }
                        )
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    trend.label.uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = if (isHighlight) Ink else Muted,
                    fontWeight = if (isHighlight) FontWeight.SemiBold else FontWeight.Normal,
                    letterSpacing = 0.4.sp
                )
            }
        }
    }
}

@Composable
fun DsSectionLabel(title: String, modifier: Modifier = Modifier, action: String? = null, onActionClick: (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title.uppercase(),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp,
            letterSpacing = 1.8.sp,
            color = Muted,
            fontWeight = FontWeight.Medium
        )
        if (action != null) {
            Text(
                action,
                fontSize = 12.sp,
                color = JadeInk,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.then(if (onActionClick != null) Modifier.clickable { onActionClick() } else Modifier)
            )
        }
    }
}

@Composable
fun MoneyDisplay(amount: Double, size: Int, color: Color, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(
                fontSize = (size * 0.55).sp,
                fontStyle = FontStyle.Normal,
                color = color.copy(alpha = 0.55f),
                fontFamily = FontFamily.Serif
            )) { append("₹") }
            withStyle(SpanStyle(
                fontSize = size.sp,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Normal,
                color = color,
                letterSpacing = (-0.5).sp
            )) { append(fmtINR(amount)) }
        },
        modifier = modifier
    )
}

@Composable
private fun IncomeDialog(currentIncome: Double, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember { mutableStateOf(if (currentIncome > 0) "%.0f".format(currentIncome) else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        title = { Text("Set Monthly Income", color = Ink) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Income (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val value = text.toDoubleOrNull() ?: return@TextButton
                onConfirm(value)
            }) { Text("Save", color = Jade) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }
    )
}

@Composable
private fun UpcomingBillRow(upcoming: UpcomingBill) {
    val dueText = when (upcoming.daysUntilDue) {
        -1 -> "Yesterday"
        0 -> "Today"
        1 -> "Tomorrow"
        else -> "In ${upcoming.daysUntilDue} days"
    }
    val isOverdue = upcoming.daysUntilDue < 0
    val isDueToday = upcoming.daysUntilDue == 0
    val accentColor = when {
        isOverdue -> Coral
        isDueToday -> Amber
        else -> Muted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${upcoming.bill.dueDayOfMonth}",
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 16.sp,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(upcoming.bill.name, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Ink)
            Text(dueText, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, color = accentColor)
        }
        MoneyDisplay(upcoming.bill.amount, size = 18, color = if (isOverdue) Coral else Ink)
    }
}

@Composable
private fun LoanStatusRow(loanStatus: LoanStatus) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(loanStatus.loan.emoji, fontSize = 18.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(loanStatus.loan.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)
                Text(
                    "${loanStatus.paidMonths}/${loanStatus.loan.tenureMonths} months paid",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Muted
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                MoneyDisplay(loanStatus.loan.monthlyEmi, size = 16, color = Ink)
                Text(
                    "${loanStatus.remainingMonths} mo left",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Coral
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(HairlineSoft)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(loanStatus.progressFraction)
                    .clip(RoundedCornerShape(100.dp))
                    .background(Jade)
            )
        }
    }
}

private fun fmtINR(n: Double): String {
    if (n.isNaN() || n.isInfinite()) return "—"
    return NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply {
        maximumFractionDigits = 0; minimumFractionDigits = 0
    }.format(n.toLong())
}
