package com.example.expensetracker.ui.screens.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.ui.theme.*
import com.example.expensetracker.ui.util.parseColor
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

private fun fmtINR(amount: Double): String =
    NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply { maximumFractionDigits = 0 }.format(amount)

@Composable
private fun MoneyDisplay(amount: Double, size: Int = 32, color: Color = Ink) {
    Text(
        text = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    fontSize = (size * 0.65f).sp,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    color = color.copy(alpha = 0.55f)
                )
            ) { append("₹") }
            withStyle(
                SpanStyle(
                    fontSize = size.sp,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    color = color
                )
            ) { append(fmtINR(amount)) }
        }
    )
}

@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel, onMonthClick: (Long, Long) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(Canvas)) {
        // Header
        Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 14.dp)) {
            Text(
                text = "STATISTICS",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.5.sp,
                letterSpacing = 1.8.sp,
                color = Muted
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = if (viewMode == ViewMode.MONTHLY) state.monthLabel else state.yearLabel,
                    fontFamily = FontFamily.Serif,
                    fontSize = 36.sp,
                    fontStyle = FontStyle.Italic,
                    color = Ink,
                    lineHeight = 36.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Paper)
                            .border(1.dp, Hairline, CircleShape)
                            .clickable {
                                if (viewMode == ViewMode.MONTHLY) viewModel.goToPreviousMonthStats()
                                else viewModel.goToPreviousYear()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = Ink, modifier = Modifier.size(16.dp))
                    }
                    val canGoForward = if (viewMode == ViewMode.MONTHLY) state.canGoForwardMonth else state.canGoForward
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Paper)
                            .border(1.dp, Hairline, CircleShape)
                            .graphicsLayer { alpha = if (canGoForward) 1f else 0.4f }
                            .clickable(enabled = canGoForward) {
                                if (viewMode == ViewMode.MONTHLY) viewModel.goToNextMonthStats()
                                else viewModel.goToNextYear()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = if (canGoForward) Ink else Muted, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            ViewModeToggle(viewMode = viewMode, onModeChange = viewModel::setViewMode)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (viewMode == ViewMode.MONTHLY) {
                if (state.monthTotal == 0.0) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No expenses for ${state.monthLabel}.", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Muted)
                        }
                    }
                } else {
                    item { MonthlyTotalCard(monthTotal = state.monthTotal, monthVsLastPct = state.monthVsLastPct, onViewAll = { onMonthClick(state.monthStart, state.monthEnd) }) }
                    if (state.monthCategories.isNotEmpty()) {
                        item {
                            Text("TOP CATEGORIES", fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.6.sp, color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                        }
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Paper)
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                state.monthCategories.forEachIndexed { index, stat ->
                                    if (index > 0) HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                                    CategoryStatRow(stat = stat, yearTotal = state.monthTotal, onClick = { viewModel.selectCategory(stat.category.id) })
                                }
                            }
                        }
                    }
                    if (state.monthPaymentSplit.total > 0) {
                        item {
                            Text("HOW YOU PAY", fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.6.sp, color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                        }
                        item { PaymentSplitCard(split = state.monthPaymentSplit) }
                    }
                }
            } else if (state.totalThisYear == 0.0) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No expenses recorded for ${state.yearLabel}.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Muted
                        )
                    }
                }
            } else {
                // Two stat tiles
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Total YTD
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Deep)
                                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)
                        ) {
                            Text(
                                text = "TOTAL YTD",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                letterSpacing = 1.6.sp,
                                color = Ink.copy(alpha = 0.55f)
                            )
                            Spacer(Modifier.height(4.dp))
                            MoneyDisplay(state.totalThisYear, size = 30, color = Ink)
                            Spacer(Modifier.height(6.dp))
                            val monthsRecorded = state.monthStats.count { it.total > 0 }
                            Text(
                                text = "over $monthsRecorded months",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                color = Ink.copy(alpha = 0.6f)
                            )
                        }
                        // Monthly avg
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Paper)
                                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)
                        ) {
                            Text(
                                text = "MONTHLY AVG",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                letterSpacing = 1.6.sp,
                                color = Muted
                            )
                            Spacer(Modifier.height(4.dp))
                            MoneyDisplay(state.avgPerMonth, size = 30, color = Ink)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "per active month",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                color = Muted
                            )
                        }
                    }
                }

                // Busiest month
                if (state.busiestMonth.isNotEmpty()) {
                    item {
                        val busiestTotal = state.monthStats.find { it.label == state.busiestMonth }?.total ?: 0.0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(AmberSoft)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Paper),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "★",
                                    fontFamily = FontFamily.Serif,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 22.sp,
                                    color = Amber
                                )
                            }
                            Column {
                                Text(
                                    text = "BUSIEST MONTH",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.5.sp,
                                    letterSpacing = 1.6.sp,
                                    color = Muted
                                )
                                Text(
                                    text = "${state.busiestMonth} ${state.yearLabel} · ₹${fmtINR(busiestTotal)}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Ink,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Monthly breakdown section label
                item {
                    Text(
                        text = "MONTHLY BREAKDOWN",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 1.6.sp,
                        color = Muted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                // Bar chart card
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Paper)
                            .padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 14.dp)
                    ) {
                        MonthlyBarChart(
                            monthStats = state.monthStats,
                            busiestLabel = state.busiestMonth,
                            onMonthClick = onMonthClick
                        )
                    }
                }

                // Top categories section label
                if (state.topCategories.isNotEmpty()) {
                    item {
                        Text(
                            text = "TOP CATEGORIES",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            letterSpacing = 1.6.sp,
                            color = Muted,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(Paper)
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            state.topCategories.forEachIndexed { index, stat ->
                                if (index > 0) {
                                    HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
                                }
                                CategoryStatRow(
                                    stat = stat,
                                    yearTotal = state.totalThisYear,
                                    onClick = { viewModel.selectCategory(stat.category.id) }
                                )
                            }
                        }
                    }
                }

                // Cash vs Digital split
                if (state.paymentSplit.total > 0) {
                    item {
                        Text(
                            text = "HOW YOU PAY",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            letterSpacing = 1.6.sp,
                            color = Muted,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                    }
                    item {
                        PaymentSplitCard(split = state.paymentSplit)
                    }
                }

                // Auto-tracking card
                if (state.autoTrackStats.allTimeCount > 0) {
                    item {
                        Text(
                            text = "AUTO-TRACKING",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            letterSpacing = 1.6.sp,
                            color = Muted,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                    }
                    item {
                        AutoTrackingCard(stats = state.autoTrackStats, yearLabel = state.yearLabel)
                    }
                }

                // Insight card
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(JadeSoft)
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("✦", fontSize = 18.sp, color = Jade)
                        Column {
                            Text(
                                text = state.insightTitle,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = JadeInk
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = state.insightBody,
                                fontSize = 11.5.sp,
                                color = InkSoft,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
        }
    }

    state.selectedCategory?.let { category ->
        CategoryDetailDialog(
            category = category,
            trend = state.selectedCategoryTrend ?: emptyList(),
            subcategoryBreakdown = state.subcategoryBreakdown,
            onDismiss = { viewModel.selectCategory(null) }
        )
    }
}

@Composable
private fun ViewModeToggle(viewMode: ViewMode, onModeChange: (ViewMode) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(Paper)
            .border(1.dp, Hairline, RoundedCornerShape(100.dp))
            .padding(3.dp)
    ) {
        ViewMode.entries.forEach { mode ->
            val selected = viewMode == mode
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(if (selected) Ink else Color.Transparent)
                    .clickable { onModeChange(mode) }
                    .padding(horizontal = 22.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (mode == ViewMode.MONTHLY) "Monthly" else "Annual",
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) Canvas else Muted
                )
            }
        }
    }
}

@Composable
private fun MonthlyTotalCard(monthTotal: Double, monthVsLastPct: Int?, onViewAll: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Deep)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = "TOTAL SPENT",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    letterSpacing = 1.6.sp,
                    color = Ink.copy(alpha = 0.55f)
                )
                Spacer(Modifier.height(4.dp))
                MoneyDisplay(monthTotal, size = 38, color = Ink)
            }
            if (monthVsLastPct != null && monthVsLastPct != 0) {
                val isUp = monthVsLastPct > 0
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (isUp) Coral.copy(alpha = 0.18f) else Jade.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "${if (isUp) "▲" else "▼"} ${abs(monthVsLastPct)}%",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isUp) Coral else Jade
                    )
                }
            }
        }
        if (monthVsLastPct != null) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = "vs last month",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = Ink.copy(alpha = 0.5f)
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Ink.copy(alpha = 0.12f))
                .clickable { onViewAll() }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "View all expenses →",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = Ink.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun PaymentSplitCard(split: PaymentSplit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Paper)
            .padding(18.dp)
    ) {
        // Segmented bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(100.dp))
        ) {
            val autoFrac = split.fraction(split.autoAmount)
            val manFrac = split.fraction(split.manualAmount)
            val recFrac = split.fraction(split.recurringAmount)
            if (autoFrac > 0) Box(Modifier.fillMaxHeight().weight(autoFrac).background(Jade))
            if (manFrac > 0) Box(Modifier.fillMaxHeight().weight(manFrac).background(Amber))
            if (recFrac > 0) Box(Modifier.fillMaxHeight().weight(recFrac).background(Coral))
            // fill remainder if rounding leaves gap
            val remainder = 1f - autoFrac - manFrac - recFrac
            if (remainder > 0.001f) Box(Modifier.fillMaxHeight().weight(remainder).background(HairlineSoft))
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (split.autoAmount > 0) {
                PaymentSplitLegend(
                    label = "UPI / SMS",
                    amount = split.autoAmount,
                    pct = (split.fraction(split.autoAmount) * 100).toInt(),
                    color = Jade,
                    modifier = Modifier.weight(1f)
                )
            }
            if (split.manualAmount > 0) {
                PaymentSplitLegend(
                    label = "Cash / Manual",
                    amount = split.manualAmount,
                    pct = (split.fraction(split.manualAmount) * 100).toInt(),
                    color = Amber,
                    modifier = Modifier.weight(1f)
                )
            }
            if (split.recurringAmount > 0) {
                PaymentSplitLegend(
                    label = "Recurring",
                    amount = split.recurringAmount,
                    pct = (split.fraction(split.recurringAmount) * 100).toInt(),
                    color = Coral,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PaymentSplitLegend(label: String, amount: Double, pct: Int, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color))
            Text(label, fontSize = 10.sp, color = Muted, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 11.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = Ink.copy(alpha = 0.5f))) { append("₹") }
                withStyle(SpanStyle(fontSize = 17.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = Ink)) { append(fmtINR(amount)) }
            }
        )
        Text("$pct%", fontSize = 10.sp, color = Muted, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun CategoryDetailDialog(
    category: com.example.expensetracker.data.db.entity.Category,
    trend: List<CategoryTrend>,
    subcategoryBreakdown: List<SubcategoryBreakdown>?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(parseColor(category.colorHex).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Text(category.emoji, fontSize = 18.sp)
                }
                Text(category.name, color = Ink, style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text("LAST 6 MONTHS TREND", style = MaterialTheme.typography.labelSmall, color = Muted, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(20.dp))
                CategoryTrendChart(trend = trend, color = parseColor(category.colorHex))
                if (!subcategoryBreakdown.isNullOrEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Text("THIS YEAR BY SUBCATEGORY", style = MaterialTheme.typography.labelSmall, color = Muted, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(14.dp))
                    SubcategoryBreakdownChart(subcategoryBreakdown)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = Jade) }
        }
    )
}

@Composable
private fun CategoryTrendChart(trend: List<CategoryTrend>, color: Color) {
    val max = trend.maxOfOrNull { it.amount }?.coerceAtLeast(1.0) ?: 1.0
    Row(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        trend.forEach { item ->
            val fraction = (item.amount / max).toFloat().coerceIn(0f, 1f)
            val barHeight = (100f * fraction).coerceAtLeast(2f).dp
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (item.amount > 0) {
                    Text("₹${fmtINR(item.amount)}", fontSize = 8.sp, color = Ink, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(4.dp))
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(barHeight).clip(RoundedCornerShape(3.dp)).background(if (item.amount > 0) color else HairlineSoft)
                )
                Spacer(Modifier.height(6.dp))
                Text(item.label, fontSize = 9.sp, color = Muted, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun MonthlyBarChart(monthStats: List<MonthStat>, busiestLabel: String, onMonthClick: (Long, Long) -> Unit) {
    val max = monthStats.maxOfOrNull { it.total }?.coerceAtLeast(1.0) ?: 1.0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        monthStats.forEach { month ->
            val isBusiest = month.label == busiestLabel
            val fraction = (month.total / max).toFloat().coerceIn(0f, 1f)
            val barHeight: Dp = if (month.total > 0) (130f * fraction).coerceAtLeast(2f).dp else 0.dp

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onMonthClick(month.start, month.end) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            when {
                                isBusiest -> Ink
                                month.total > 0 -> Ink.copy(alpha = 0.18f)
                                else -> Ink.copy(alpha = 0.05f)
                            }
                        )
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = month.label,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = if (isBusiest) Ink else Muted,
                    fontWeight = if (isBusiest) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun SubcategoryBreakdownChart(breakdown: List<SubcategoryBreakdown>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(100.dp))
    ) {
        breakdown.forEach { item ->
            if (item.fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(item.fraction)
                        .background(parseColor(item.category.colorHex))
                )
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    breakdown.forEach { item ->
        val pct = (item.fraction * 100).toInt()
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(parseColor(item.category.colorHex))
            )
            Text(
                text = "${item.category.emoji} ${item.category.name}",
                fontSize = 12.sp,
                color = Ink,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 10.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = Ink.copy(alpha = 0.5f))) { append("₹") }
                    withStyle(SpanStyle(fontSize = 14.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = Ink)) { append(fmtINR(item.amount)) }
                }
            )
            Text(
                text = "$pct%",
                fontSize = 10.sp,
                color = Muted,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(28.dp)
            )
        }
    }
}

@Composable
private fun AutoTrackingCard(stats: AutoTrackStats, yearLabel: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Paper)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(JadeSoft),
                contentAlignment = Alignment.Center
            ) {
                Text("📡", fontSize = 24.sp)
            }
            Column {
                Text(
                    text = "${stats.rate}%",
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 34.sp,
                    color = Ink,
                    lineHeight = 36.sp
                )
                Text(
                    text = "auto-captured in $yearLabel",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Muted
                )
                Text(
                    text = "${stats.autoCount} of ${stats.totalCount} expenses",
                    fontSize = 11.sp,
                    color = InkSoft,
                    modifier = Modifier.padding(top = 1.dp)
                )
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
                    .fillMaxWidth(stats.rate / 100f)
                    .clip(RoundedCornerShape(100.dp))
                    .background(Jade)
            )
        }
        if (stats.monthlyAutoCounts.any { it > 0 }) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = "BY MONTH",
                fontFamily = FontFamily.Monospace,
                fontSize = 8.5.sp,
                letterSpacing = 1.4.sp,
                color = Muted
            )
            Spacer(Modifier.height(8.dp))
            val maxCount = stats.monthlyAutoCounts.maxOrNull()?.coerceAtLeast(1) ?: 1
            val monthLabels = listOf("J","F","M","A","M","J","J","A","S","O","N","D")
            Row(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                stats.monthlyAutoCounts.forEachIndexed { i, count ->
                    val frac = count.toFloat() / maxCount
                    val barH = if (count > 0) (40f * frac).coerceAtLeast(3f).dp else 0.dp
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(barH)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Jade.copy(alpha = if (count > 0) (0.5f + 0.5f * frac) else 0f))
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(monthLabels[i], fontSize = 8.sp, color = Muted, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = HairlineSoft, thickness = 0.5.dp)
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AutoStatPill("Today", stats.todayCount.toString())
            AutoStatPill("This Month", stats.thisMonthCount.toString())
            AutoStatPill("All Time", stats.allTimeCount.toString())
        }
    }
}

@Composable
private fun AutoStatPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontSize = 24.sp,
            color = Ink
        )
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = Muted
        )
    }
}

@Composable
private fun CategoryStatRow(stat: CategoryYearlyStat, yearTotal: Double, onClick: () -> Unit) {
    val color = parseColor(stat.category.colorHex)
    val fraction = if (yearTotal > 0) (stat.total / yearTotal).coerceIn(0.0, 1.0).toFloat() else 0f
    val pct = (fraction * 100).toInt()

    Column(modifier = Modifier.clickable(onClick = onClick).padding(vertical = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stat.category.emoji, fontSize = 16.sp)
            Text(
                text = stat.category.name,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = Ink,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            color = Ink.copy(alpha = 0.55f)
                        )
                    ) { append("₹") }
                    withStyle(
                        SpanStyle(
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            color = Ink
                        )
                    ) { append(fmtINR(stat.total)) }
                }
            )
            Text(
                text = "$pct%",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.5.sp,
                color = Muted,
                modifier = Modifier.width(30.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 26.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(HairlineSoft)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(100.dp))
                    .background(color)
            )
        }
    }
}
