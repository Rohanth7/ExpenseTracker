package com.example.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.ui.theme.Gold
import com.example.expensetracker.ui.theme.Muted

data class BarData(val label: String, val value: Double)

// The approved design's colour for a bar that isn't the highlighted one; Color.kt has no token for it.
private val IdleBar = Color(0xFF4A52A8)

/**
 * Bars sharing the width, scaled to the tallest, with the one at [highlight] in Gold. Twelve month
 * names don't fit under their bars, so only the first, middle and last labels are written; a bar
 * that can be tapped is described by its own label.
 */
@Composable
fun BarChart(
    bars: List<BarData>,
    highlight: Int?,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 120.dp,
    onBarClick: ((Int) -> Unit)? = null
) {
    if (bars.isEmpty()) return
    val max = bars.maxOf { it.value }.coerceAtLeast(1.0)

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(chartHeight),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            bars.forEachIndexed { i, bar ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(
                            if (onBarClick == null) Modifier
                            else Modifier.semantics { contentDescription = bar.label }.clickable { onBarClick(i) }
                        ),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (bar.value > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 2.dp)
                                .fillMaxHeight((bar.value / max).toFloat().coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (i == highlight) Gold else IdleBar)
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, (bars.size - 1) / 2, bars.lastIndex).distinct().forEach {
                Text(bars[it].label, fontSize = 13.sp, color = Muted)
            }
        }
    }
}
