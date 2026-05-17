package com.example.expensetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

data class PieSlice(val color: Color, val value: Float, val label: String)

@Composable
fun PieChart(slices: List<PieSlice>, modifier: Modifier = Modifier.size(200.dp)) {
    Canvas(modifier = modifier) {
        val total = slices.sumOf { it.value.toDouble() }.toFloat()
        if (total == 0f) return@Canvas
        var startAngle = -90f
        val strokeWidth = size.minDimension * 0.15f
        val radius = (size.minDimension - strokeWidth) / 2f

        slices.forEach { slice ->
            val sweep = slice.value / total * 360f
            drawArc(
                color = slice.color,
                startAngle = startAngle,
                sweepAngle = sweep - 1f,
                useCenter = false,
                style = Stroke(width = strokeWidth),
                topLeft = androidx.compose.ui.geometry.Offset(
                    (size.width - radius * 2) / 2f,
                    (size.height - radius * 2) / 2f
                ),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
            )
            startAngle += sweep
        }
    }
}
