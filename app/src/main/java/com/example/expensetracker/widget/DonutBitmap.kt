package com.example.expensetracker.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

object DonutBitmap {
    /**
     * Renders the donut ring into a Bitmap.
     * Design spec: 90dp canvas, radius=38, stroke=8, starting at -90° (12 o'clock).
     */
    fun render(percent: Int, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val strokeW = sizePx * 8f / 90f
        val radius = sizePx * 38f / 90f
        paint.strokeWidth = strokeW

        // Track ring — 8% white
        paint.color = android.graphics.Color.argb(0x14, 0xF4, 0xEF, 0xE1)
        canvas.drawCircle(cx, cy, radius, paint)

        // Progress arc — jade
        val sweep = (percent.coerceIn(0, 100) / 100f * 360f)
        if (sweep > 0f) {
            val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            paint.color = android.graphics.Color.parseColor("#7DC9A5")
            canvas.drawArc(oval, -90f, sweep, false, paint)
        }

        return bitmap
    }
}
