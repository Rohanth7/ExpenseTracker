package com.example.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class BarData(
    val label: String,
    val emoji: String,
    val color: Color,
    val value: Double,
    val limit: Double = 0.0
)

@Composable
fun BarChart(
    bars: List<BarData>,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 160.dp
) {
    if (bars.isEmpty()) return

    val maxValue = bars.maxOf { maxOf(it.value, it.limit) }.coerceAtLeast(1.0)

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        bars.forEach { bar ->
            BarItem(bar = bar, maxValue = maxValue, chartHeight = chartHeight)
        }
    }
}

@Composable
private fun BarItem(bar: BarData, maxValue: Double, chartHeight: Dp) {
    val barFraction = (bar.value / maxValue).coerceIn(0.0, 1.0).toFloat()
    val limitFraction = if (bar.limit > 0) (bar.limit / maxValue).coerceIn(0.0, 1.0).toFloat() else 0f
    val isOverLimit = bar.limit > 0 && bar.value > bar.limit
    val barColor = if (isOverLimit) MaterialTheme.colorScheme.error else bar.color

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(48.dp)
    ) {
        Text(
            text = "₹${"%.0f".format(bar.value)}",
            fontSize = 8.sp,
            maxLines = 1,
            color = if (isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(2.dp))

        Box(
            modifier = Modifier
                .width(32.dp)
                .height(chartHeight)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Filled bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(barFraction)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(barColor)
            )

            // Dashed limit line
            if (bar.limit > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            val y = size.height * (1f - limitFraction)
                            drawLine(
                                color = Color.Red.copy(alpha = 0.8f),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                            )
                        }
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(bar.emoji, fontSize = 16.sp)
        Text(
            text = bar.label,
            fontSize = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
