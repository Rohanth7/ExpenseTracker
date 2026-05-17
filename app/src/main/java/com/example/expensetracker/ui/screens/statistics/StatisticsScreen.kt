package com.example.expensetracker.ui.screens.statistics

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.expensetracker.ui.components.BarChart
import com.example.expensetracker.ui.components.BarData
import com.example.expensetracker.ui.util.parseColor

@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Year navigation
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.goToPreviousYear() }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous year")
                }
                Text(
                    text = state.yearLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = { viewModel.goToNextYear() },
                    enabled = state.canGoForward
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Next year",
                        tint = if (state.canGoForward) LocalContentColor.current
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                    )
                }
            }
        }

        if (state.totalThisYear == 0.0) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No expenses recorded for ${state.yearLabel}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Summary cards row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        label = "Total Spent",
                        value = "₹${"%.0f".format(state.totalThisYear)}"
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        label = "Avg / Month",
                        value = "₹${"%.0f".format(state.avgPerMonth)}"
                    )
                }
            }

            // Busiest month chip
            if (state.busiestMonth.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📍", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Busiest Month",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    state.busiestMonth,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 12-month bar chart
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Monthly Breakdown", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(16.dp))
                        BarChart(
                            bars = state.monthStats.map { month ->
                                BarData(
                                    label = month.label,
                                    emoji = if (month.label == state.busiestMonth) "📍" else "·",
                                    color = if (month.label == state.busiestMonth)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                    value = month.total
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Top categories
            if (state.topCategories.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text("Top Categories", style = MaterialTheme.typography.titleMedium)
                            state.topCategories.forEach { stat ->
                                CategoryStatRow(stat = stat, yearTotal = state.totalThisYear)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(modifier: Modifier, label: String, value: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CategoryStatRow(stat: CategoryYearlyStat, yearTotal: Double) {
    val color = parseColor(stat.category.colorHex)
    val fraction = if (yearTotal > 0) (stat.total / yearTotal).coerceIn(0.0, 1.0).toFloat() else 0f

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${stat.category.emoji} ${stat.category.name}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "₹${"%.0f".format(stat.total)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(30.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
