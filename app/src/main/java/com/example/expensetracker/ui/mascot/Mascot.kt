package com.example.expensetracker.ui.mascot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.expensetracker.ui.theme.Canvas as CanvasColor
import com.example.expensetracker.ui.theme.Muted

// She is drawn on a 120 x 124 grid whose top edge is y = -4, which leaves room for her coin.
private const val GridWidth = 120f
private const val GridHeight = 124f
private const val GridTop = -4f

private val Clay = Color(0xFFCF6E44)
private val ClayRim = Color(0xFF9E472A)
private val ClaySlot = Color(0xFF7A3018)
private val ClayDots = Color(0xBFF8E6CB)
private val FaceInk = Color(0xFF4A1E0E)
private val Coin = Color(0xFFF4BC42)
private val CoinEdge = Color(0xFFC98A1B)
private val Cheek = Color(0xFFF0808A)
private val Tear = Color(0xFF8CC7E8)

private fun svg(data: String): Path = PathParser().parsePathString(data).toPath()

private val Body = svg("M48 26c0 4 2 6 2 8C33 40 20 55 20 74c0 22 18 37 40 37s40-15 40-37c0-19-13-34-30-40 0-2 2-4 2-8z")
private val Sparkles = svg("M76 8l5-3M78 16l5 1M44 8l-5-3")
private val TearDrop = svg("M43.5 70c-2.2 3-3 4.4-3 5.8a3 3 0 0 0 6 0c0-1.4-.8-2.8-3-5.8z")

/** The lines of each face; eyes that are dots are drawn separately, growing with the mood. */
private val FaceLines = mapOf(
    MascotMood.Happy to svg("M45 63q5-6 10 0M65 63q5-6 10 0M52 71q8 8 16 0"),
    MascotMood.Worried to svg("M44 57l9-3M76 57l-9-3M54 76q6-3 12 0"),
    MascotMood.Sad to svg("M43 57l10-5M77 57l-10-5M53 78q7-5.5 14 0"),
    MascotMood.Pleading to svg("M42 56l10-5M78 56l-10-5M56 80q4-3.5 8 0"),
    MascotMood.Asleep to svg("M45 64q5 4 10 0M65 64q5 4 10 0M56 74q4 3 8 0"),
)

/** Radius and height of her dot eyes. Bigger and shinier as she gets sadder; none when her eyes are closed. */
private fun dotEyes(mood: MascotMood): Pair<Float, Float>? = when (mood) {
    MascotMood.Worried -> 2.8f to 64f
    MascotMood.Sad -> 3.7f to 64.5f
    MascotMood.Pleading -> 5f to 65f
    MascotMood.Happy, MascotMood.Asleep -> null
}

private fun DrawScope.face(mood: MascotMood, ink: Color) {
    dotEyes(mood)?.let { (r, y) ->
        for (x in listOf(50f, 70f)) {
            drawCircle(ink, r, Offset(x, y))
            if (r > 3f) drawCircle(Color.White, r * .3f, Offset(x + r * .34f, y - r * .38f))
            if (mood == MascotMood.Pleading) drawCircle(Color.White, r * .16f, Offset(x - r * .36f, y + r * .36f))
        }
    }
    drawPath(FaceLines.getValue(mood), ink, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    when (mood) {
        MascotMood.Happy -> for (x in listOf(42f, 78f)) drawCircle(Cheek, 4f, Offset(x, 71f), alpha = .55f)
        MascotMood.Pleading -> {
            for (x in listOf(40f, 80f)) drawCircle(Cheek, 3.6f, Offset(x, 75f), alpha = .45f)
            drawPath(TearDrop, Tear)
        }
        else -> {}
    }
}

/** The clay pot, whole. Decorative: whatever she is saying belongs in text next to her. */
@Composable
fun Mascot(mood: MascotMood, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(GridWidth / GridHeight)) {
        val unit = size.width / GridWidth
        withTransform({ scale(unit, unit, Offset.Zero); translate(0f, -GridTop) }) {
            drawOval(Color.Black, Offset(30f, 109f), Size(60f, 8f), alpha = .2f)
            drawPath(Body, Clay)
            drawOval(ClayRim, Offset(48f, 21.5f), Size(24f, 9f))
            drawRoundRect(ClaySlot, Offset(50f, 44f), Size(20f, 4.5f), CornerRadius(2.2f))
            for ((x, y) in listOf(38f to 100f, 49f to 104f, 60f to 105.5f, 71f to 104f, 82f to 100f)) drawCircle(ClayDots, 2f, Offset(x, y))
            if (mood == MascotMood.Happy) {
                drawCircle(Coin, 7f, Offset(60f, 9f))
                drawCircle(CoinEdge, 7f, Offset(60f, 9f), style = Stroke(2f))
                drawPath(Sparkles, Coin, style = Stroke(2.5f, cap = StrokeCap.Round))
            }
            // Her face sits lower on the pot than on the small pill, which is drawn on the same grid.
            withTransform({ translate(0f, 12f) }) { face(mood, FaceInk) }
        }
    }
}

private val FaceTint = mapOf(
    MascotMood.Happy to Color(0xFF5FC08C),
    MascotMood.Worried to Color(0xFFF6A723),
    MascotMood.Sad to Color(0xFFF08A3C),
    MascotMood.Pleading to Color(0xFFFF6B7F),
    MascotMood.Asleep to Muted,
)

/** Her face alone on a tinted pill, small enough to sit beside one budget row. */
@Composable
fun MascotFace(mood: MascotMood, modifier: Modifier = Modifier) {
    val tint = FaceTint.getValue(mood)
    Canvas(modifier.aspectRatio(52f / 39f)) {
        val unit = size.width / 52f
        withTransform({ scale(unit, unit, Offset.Zero); translate(-34f, -48f) }) {
            drawRoundRect(tint, Offset(34f, 48f), Size(52f, 39f), CornerRadius(19f), alpha = .2f)
            face(mood, tint)
        }
    }
}

@Preview
@Composable
private fun MascotMoodsPreview() {
    Column(Modifier.background(CanvasColor).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MascotMood.entries.forEach { Mascot(it, Modifier.size(120.dp)) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MascotMood.entries.forEach { MascotFace(it, Modifier.size(34.dp)) } }
    }
}
