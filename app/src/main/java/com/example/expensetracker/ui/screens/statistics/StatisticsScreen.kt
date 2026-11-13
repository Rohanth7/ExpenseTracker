package com.example.expensetracker.ui.screens.statistics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.ui.components.*
import com.example.expensetracker.ui.theme.*
import com.example.expensetracker.ui.util.fmtINR
import kotlin.math.abs

@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel, onMonthClick: (Long, Long) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    // Collected (and so worked out) only while the Range view is shown
    val range = if (viewMode == ViewMode.RANGE) viewModel.rangeState.collectAsState().value else null
    val selectedRange by viewModel.selectedRange.collectAsState()
    var showRangeSheet by rememberSaveable { mutableStateOf(false) }
    val periodLabel = when (viewMode) {
        ViewMode.MONTHLY -> state.month.label
        ViewMode.RANGE -> range?.label.orEmpty()
        ViewMode.ANNUAL -> state.yearLabel
    }

    Column(modifier = Modifier.fillMaxSize().background(Canvas).padding(horizontal = ScreenPadding)) {
        ScreenHeader(
            title = "Insights",
            actions = {
                when (viewMode) {
                    ViewMode.MONTHLY -> {
                        HeaderIcon(Icons.Default.ChevronLeft, "Previous month", onClick = { viewModel.goToPreviousMonthStats() })
                        HeaderIcon(Icons.Default.ChevronRight, "Next month", onClick = { viewModel.goToNextMonthStats() }, enabled = state.canGoForwardMonth)
                    }
                    ViewMode.ANNUAL -> {
                        HeaderIcon(Icons.Default.ChevronLeft, "Previous year", onClick = { viewModel.goToPreviousYear() })
                        HeaderIcon(Icons.Default.ChevronRight, "Next year", onClick = { viewModel.goToNextYear() }, enabled = state.canGoForward)
                    }
                    ViewMode.RANGE -> {}
                }
            }
        )
        SegTabs(
            options = ViewMode.entries.map { when (it) { ViewMode.MONTHLY -> "Month"; ViewMode.ANNUAL -> "Year"; ViewMode.RANGE -> "Range" } },
            selectedIndex = viewMode.ordinal,
            onSelect = { index ->
                val mode = ViewMode.entries[index]
                if (mode == ViewMode.RANGE) showRangeSheet = true else viewModel.setViewMode(mode)
            }
        )

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            val view = when (viewMode) {
                ViewMode.MONTHLY -> state.month
                ViewMode.RANGE -> range  // null for the moment it takes to work out
                ViewMode.ANNUAL -> null
            }
            if (view != null) {
                if (view.stats.total == 0.0) {
                    view.stats.income?.let { summary -> item { IncomeSpentSavedCard(summary, Modifier.padding(top = 4.dp)) } }
                    item { EmptyText("No expenses for ${view.label}.") }
                } else {
                    item {
                        // "Last 3 months" and "All time" are written as titles; a span of dates reads the same either way
                        val named = viewMode == ViewMode.RANGE && selectedRange !is StatsRange.Custom
                        PeriodTotal(
                            period = if (named) view.label.replaceFirstChar { it.lowercase() } else view.label,
                            total = view.stats.total,
                            onPeriodClick = if (viewMode == ViewMode.RANGE) ({ showRangeSheet = true }) else null
                        ) {
                            val pct = view.stats.vsPreviousPct
                            if (pct != null && view.comparedWith != null) {
                                Text(
                                    text = when {
                                        pct < 0 -> "${abs(pct)}% less than ${view.comparedWith}"
                                        pct > 0 -> "$pct% more than ${view.comparedWith}"
                                        else -> "vs ${view.comparedWith}"
                                    },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when { pct < 0 -> Mint; pct > 0 -> Pink; else -> Muted }
                                )
                            }
                            TextAction("View all expenses →", { onMonthClick(view.start, view.end) })
                        }
                    }
                    view.stats.income?.let { summary -> item { IncomeSpentSavedCard(summary, Modifier.padding(top = 10.dp)) } }
                    if (view.stats.categories.isNotEmpty()) {
                        item {
                            SectionLabel("All categories")
                            // A monthly limit only means something against one budget month
                            CategoriesCard(view.stats.categories, showLimits = viewMode == ViewMode.MONTHLY, onTap = { viewModel.selectCategory(it) })
                        }
                    }
                    if (view.stats.paymentSplit.total > 0) {
                        item {
                            SectionLabel("How you pay")
                            PaymentSplitCard(view.stats.paymentSplit)
                        }
                    }
                    if (view.stats.detectionSplit.total > 0) {
                        item {
                            SectionLabel("How it was tracked")
                            DetectionSplitCard(view.stats.detectionSplit)
                        }
                    }
                    view.stats.insight?.let { insight ->
                        item { InsightCard(insight.label, insight.detail, Modifier.padding(top = 10.dp)) }
                    }
                }
            } else if (viewMode != ViewMode.ANNUAL) {
                // The Range view's numbers are still being worked out
            } else if (state.totalThisYear == 0.0) {
                item { EmptyText("No expenses recorded for ${state.yearLabel}.") }
            } else {
                item {
                    PeriodTotal(period = state.yearLabel, total = state.totalThisYear) {
                        val monthsRecorded = state.monthStats.count { it.total > 0 }
                        Text("over $monthsRecorded months · ₹${fmtINR(state.avgPerMonth)} per active month", fontSize = 13.sp, color = Muted)
                    }
                }

                item {
                    MCard(Modifier.padding(top = 10.dp)) {
                        BarChart(
                            bars = state.monthStats.map { BarData(it.label, it.total) },
                            highlight = state.monthStats.indexOfFirst { it.label == state.busiestMonth },
                            onBarClick = { index -> state.monthStats[index].let { onMonthClick(it.start, it.end) } }
                        )
                    }
                }

                if (state.busiestMonth.isNotEmpty()) {
                    item {
                        val busiestTotal = state.monthStats.find { it.label == state.busiestMonth }?.total ?: 0.0
                        MCard(Modifier.padding(top = 10.dp)) {
                            Text("${state.busiestMonth} was your busiest month", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text("₹${fmtINR(busiestTotal)} spent", fontSize = 13.sp, color = Muted)
                        }
                    }
                }

                if (state.topCategories.isNotEmpty()) {
                    item {
                        SectionLabel("All categories")
                        CategoriesCard(state.topCategories, showLimits = false, onTap = { viewModel.selectCategory(it) })
                    }
                }

                if (state.paymentSplit.total > 0) {
                    item {
                        SectionLabel("How you pay")
                        PaymentSplitCard(state.paymentSplit)
                    }
                }

                if (state.detectionSplit.total > 0) {
                    item {
                        SectionLabel("How it was tracked")
                        DetectionSplitCard(state.detectionSplit)
                    }
                }

                if (state.autoTrackStats.allTimeCount > 0) {
                    item {
                        SectionLabel("Auto-tracking")
                        AutoTrackingCard(state.autoTrackStats, state.yearLabel, state.monthStats.map { it.label })
                    }
                }

                item { InsightCard(state.insightTitle, state.insightBody, Modifier.padding(top = 10.dp)) }
            }
        }
    }

    state.selectedCategory?.let { category ->
        // Split over the same days as the row that was tapped
        val shown = when (viewMode) {
            ViewMode.MONTHLY -> state.month.stats.categories
            ViewMode.RANGE -> range?.stats?.categories.orEmpty()
            ViewMode.ANNUAL -> state.topCategories
        }.find { it.category.id == category.id }
        val trend = state.selectedCategoryTrend.orEmpty()
        // The budget row follows an earlier month when that is the one on screen
        val earlierMonth = viewMode == ViewMode.MONTHLY && state.canGoForwardMonth
        CategoryDetailSheet(
            category = category,
            budgetLabel = if (earlierMonth) state.month.label else "This month",
            budgetSpent = if (earlierMonth) shown?.total ?: 0.0 else trend.lastOrNull()?.amount ?: 0.0,
            trend = trend,
            subcategories = shown?.subcategories.orEmpty(),
            periodLabel = periodLabel,
            onDismiss = { viewModel.selectCategory(null) }
        )
    }

    if (showRangeSheet) RangeSheet(
        current = selectedRange.takeIf { viewMode == ViewMode.RANGE },
        onDismiss = { showRangeSheet = false },
        onPick = { viewModel.setRange(it); showRangeSheet = false }
    )
}

@Composable
private fun RangeSheet(current: StatsRange?, onDismiss: () -> Unit, onPick: (StatsRange) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    MSheet(onDismiss) {
        SheetTitle("Show")
        MCard(padding = RowCardPadding) {
            listOf(StatsRange.LastMonths(3) to "Last 3 months", StatsRange.LastMonths(6) to "Last 6 months", StatsRange.AllTime to "All time")
                .forEach { (range, label) ->
                    RangeOption(label, selected = current == range) { onPick(range) }
                    RowDivider()
                }
            RangeOption("Choose dates", selected = current is StatsRange.Custom) { showPicker = true }
        }
    }
    if (showPicker) {
        val custom = current as? StatsRange.Custom
        DateRangePickerDialog("Dates to show", initial = custom?.let { it.start to it.end }, onDismiss = { showPicker = false }) {
            showPicker = false
            onPick(StatsRange.Custom(it.first, it.second))
        }
    }
}

@Composable
private fun RangeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 15.sp, color = Ink, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = Gold, modifier = Modifier.size(20.dp))
    }
}

/** "Spent in November" over the total, with [below] for what goes under the number. */
@Composable
private fun PeriodTotal(period: String, total: Double, onPeriodClick: (() -> Unit)? = null, below: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            "Spent in $period", fontSize = 13.sp, color = Muted,
            modifier = if (onPeriodClick != null) Modifier.clickable(onClick = onPeriodClick) else Modifier
        )
        BigNumber("₹${fmtINR(total)}", size = 36)
        below()
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(text, fontSize = 13.sp, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp))
}

private data class Stat(val label: String, val value: String, val color: Color = Ink)

/** Equal columns, each a label over its value. */
@Composable
private fun StatColumns(vararg stats: Stat) {
    Row(Modifier.fillMaxWidth()) {
        stats.forEach { stat ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stat.label, fontSize = 13.sp, color = Muted, maxLines = 1)
                Text(stat.value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = stat.color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Income, spent and saved; saved is pink when more went out than came in. */
@Composable
private fun IncomeSpentSavedCard(summary: IncomeSummary, modifier: Modifier = Modifier) {
    val saved = summary.saved
    MCard(modifier) {
        StatColumns(
            Stat("Income", fmtINR(summary.income)),
            Stat("Spent", fmtINR(summary.spent)),
            Stat("Saved", "${if (saved < 0) "−" else ""}${fmtINR(abs(saved))}", if (saved < 0) Pink else Mint)
        )
    }
}

/** A name with its amount, over a bar; pink with "over limit" after the name when [over]. */
@Composable
private fun BarRow(
    name: String, amount: Double, fraction: Float,
    over: Boolean = false, onClick: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}
) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(name, fontSize = 15.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (over) Text("over limit", fontSize = 15.sp, color = Pink, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
            }
            Text(fmtINR(amount), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (over) Pink else Ink, maxLines = 1)
            trailing()
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(fraction, if (over) Pink else Gold)
    }
}

private val BarRowCardPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)

@Composable
private fun CategoriesCard(stats: List<CategoryYearlyStat>, showLimits: Boolean, onTap: (Long) -> Unit) {
    val largest = stats.maxOf { it.total }
    val anyExpands = stats.any { it.subcategories.isNotEmpty() }
    MCard(padding = BarRowCardPadding) {
        stats.forEach { stat ->
            val limit = stat.category.monthlyLimit
            CategoryRow(
                stat = stat,
                fraction = (stat.total / largest).toFloat(),
                over = showLimits && limit > 0 && stat.total > limit,
                // Keeps the amounts in one column when only some rows have subcategories to show
                reserveExpand = anyExpands,
                onTap = { onTap(stat.category.id) }
            )
        }
    }
}

@Composable
private fun CategoryRow(stat: CategoryYearlyStat, fraction: Float, over: Boolean, reserveExpand: Boolean, onTap: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        BarRow(stat.category.name, stat.total, fraction, over, onTap) {
            if (stat.subcategories.isNotEmpty()) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).clickable { expanded = !expanded },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
                }
            } else if (reserveExpand) {
                Spacer(Modifier.size(32.dp))
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                stat.subcategories.forEach { sub ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            "${sub.category.emoji} ${sub.category.name}", fontSize = 13.sp, color = Muted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        Text(fmtINR(sub.amount), fontSize = 13.sp, color = Muted, maxLines = 1)
                    }
                }
            }
        }
    }
}

private data class SplitPart(val color: Color, val amount: Double, val legend: String)

/** One stacked bar of [parts] by amount, and a legend line for each under it. */
@Composable
private fun SplitCard(parts: List<SplitPart>) {
    MCard {
        Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)) {
            parts.forEach { Box(Modifier.fillMaxHeight().weight(it.amount.toFloat()).background(it.color)) }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            parts.forEach {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ToneDot(it.color)
                    Text(it.legend, fontSize = 13.sp, color = Muted, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

private fun paymentColor(label: String): Color = when (label) {
    PaymentSplit.UPI -> Gold
    PaymentSplit.CREDIT_CARD -> Sky
    PaymentSplit.DEBIT_CARD -> Mint
    PaymentSplit.CASH -> Muted
    else -> Hairline
}

@Composable
private fun PaymentSplitCard(split: PaymentSplit) {
    SplitCard(split.shares.map { share ->
        SplitPart(paymentColor(share.label), share.amount, "${share.label} ${share.percent}% · ₹${fmtINR(share.amount)}")
    })
}

@Composable
private fun DetectionSplitCard(split: DetectionSplit) {
    fun part(label: String, color: Color, amount: Double, count: Int): SplitPart {
        val pct = (split.fraction(amount) * 100).toInt()
        return SplitPart(color, amount, "$label $pct% · ₹${fmtINR(amount)} · $count txn")
    }
    SplitCard(
        listOf(
            part("Auto-Detected", Gold, split.autoAmount, split.autoCount),
            part("Manually Added", Muted, split.manualAmount, split.manualCount),
            part("Recurring", Hairline, split.recurringAmount, split.recurringCount)
        ).filter { it.amount > 0 }
    )
}

@Composable
private fun InsightCard(title: String, body: String, modifier: Modifier = Modifier) {
    MCard(modifier) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Gold))
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text(body, fontSize = 13.sp, color = Muted)
            }
        }
    }
}

@Composable
private fun AutoTrackingCard(stats: AutoTrackStats, yearLabel: String, monthLabels: List<String>) {
    MCard {
        Text("${stats.rate}%", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Text("auto-captured in $yearLabel", fontSize = 13.sp, color = Muted)
        Text("${stats.autoCount} of ${stats.totalCount} expenses", fontSize = 13.sp, color = Muted)
        Spacer(Modifier.height(10.dp))
        ProgressBar(stats.rate / 100f)
        if (stats.monthlyAutoCounts.any { it > 0 }) {
            Spacer(Modifier.height(14.dp))
            Text("By month", fontSize = 13.sp, color = Muted)
            Spacer(Modifier.height(8.dp))
            BarChart(
                bars = monthLabels.zip(stats.monthlyAutoCounts) { label, count -> BarData(label, count.toDouble()) },
                highlight = null,
                chartHeight = 52.dp
            )
        }
        Spacer(Modifier.height(12.dp))
        RowDivider()
        Spacer(Modifier.height(12.dp))
        StatColumns(
            Stat("Today", stats.todayCount.toString()),
            Stat("This Month", stats.thisMonthCount.toString()),
            Stat("All Time", stats.allTimeCount.toString())
        )
    }
}

@Composable
private fun CategoryDetailSheet(
    category: Category,
    budgetLabel: String,
    budgetSpent: Double,
    trend: List<CategoryTrend>,
    subcategories: List<SubcategoryRow>,
    periodLabel: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val alertPercent = remember { PreferencesManager(context.applicationContext).budgetAlertThreshold }
    MSheet(onDismiss) {
        SheetTitle(category.name)
        val limit = category.monthlyLimit
        if (limit > 0) {
            val tone = budgetTone(budgetSpent, limit, alertPercent)
            MCard(padding = RowCardPadding) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ToneDot(tone)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(budgetLabel, fontSize = 15.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("${fmtINR(budgetSpent)} of ${fmtINR(limit)}", fontSize = 13.sp, color = Muted)
                        }
                        Spacer(Modifier.height(6.dp))
                        ProgressBar((budgetSpent / limit).toFloat(), tone)
                    }
                }
            }
        }
        if (trend.isNotEmpty()) {
            SectionLabel("Last 6 months")
            MCard {
                BarChart(bars = trend.map { BarData(it.label, it.amount) }, highlight = trend.lastIndex, chartHeight = 100.dp)
            }
        }
        if (subcategories.isNotEmpty()) {
            SectionLabel("By subcategory · $periodLabel")
            val largest = subcategories.maxOf { it.amount }
            MCard(padding = BarRowCardPadding) {
                subcategories.forEach { sub ->
                    BarRow("${sub.category.emoji} ${sub.category.name}", sub.amount, (sub.amount / largest).toFloat())
                }
            }
        }
    }
}
