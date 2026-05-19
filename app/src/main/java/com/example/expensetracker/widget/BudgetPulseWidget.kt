package com.example.expensetracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.example.expensetracker.MainActivity
import com.example.expensetracker.R
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.preferences.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class PillState { OnTrack, WatchPace, OverBudget }

class BudgetPulseWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Push placeholder synchronously — prevents Samsung "Couldn't add widget"
        val placeholder = buildPlaceholder(context)
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, placeholder) }

        // Load real data off the main thread
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = BudgetPulseRepository(
                    AppDatabase.getInstance(context),
                    PreferencesManager(context)
                ).load()
                val density = context.resources.displayMetrics.density.takeIf { it > 0f } ?: 2f
                val donutPx = (90 * density).roundToInt().coerceAtLeast(90)
                val donutBitmap = DonutBitmap.render(
                    percent = if (data.income > 0)
                        (data.spent / data.income * 100).roundToInt().coerceIn(0, 100)
                    else 0,
                    sizePx = donutPx
                )
                val views = if (data.widgetEnabled) buildViews(context, data, donutBitmap)
                            else buildDisabledViews(context)
                appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
            } catch (e: Exception) {
                android.util.Log.e("BudgetPulseWidget", "update failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, BudgetPulseWidgetReceiver::class.java)
            )
            if (ids.isEmpty()) return
            val intent = Intent(context, BudgetPulseWidgetReceiver::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}

private fun buildDisabledViews(context: Context): RemoteViews =
    RemoteViews(context.packageName, R.layout.budget_pulse_widget_layout).apply {
        setTextViewText(R.id.header_date, "")
        setTextViewText(R.id.donut_percent, "")
        setTextViewText(R.id.spent_label, "")
        setTextViewText(R.id.spent_amount, "")
        setTextViewText(R.id.budget_caption, "Widget disabled in settings")
        setViewVisibility(R.id.donut_image, View.INVISIBLE)
        setViewVisibility(R.id.status_pill, View.INVISIBLE)
        setViewVisibility(R.id.comparison_text, View.GONE)
    }

private fun buildPlaceholder(context: Context): RemoteViews =
    RemoteViews(context.packageName, R.layout.budget_pulse_widget_layout).apply {
        setTextViewText(R.id.header_date, "")
        setTextViewText(R.id.donut_percent, "")
        setTextViewText(R.id.spent_label, "")
        setTextViewText(R.id.spent_amount, "")
        setTextViewText(R.id.budget_caption, "")
        setViewVisibility(R.id.status_pill, View.INVISIBLE)
        setViewVisibility(R.id.comparison_text, View.GONE)
    }

private fun buildViews(
    context: Context,
    data: WidgetData,
    donutBitmap: android.graphics.Bitmap
): RemoteViews {
    val hasIncome = data.income > 0.0
    val spent = data.spent
    val income = data.income
    val remaining = (income - spent).coerceAtLeast(0.0)
    val percent = if (hasIncome) (spent / income * 100).roundToInt().coerceIn(0, 100) else 0

    val paceRatio = data.dayOfMonth.toFloat() / data.daysInMonth
    val spendRatio = if (hasIncome) (spent / income).toFloat() else 0f
    val pillState = when {
        spent >= income && hasIncome -> PillState.OverBudget
        spendRatio > paceRatio && hasIncome -> PillState.WatchPace
        else -> PillState.OnTrack
    }
    val pillText = when (pillState) {
        PillState.OnTrack -> if (spent == 0.0 && hasIncome) "Pristine" else "On track"
        PillState.WatchPace -> "Watch pace"
        PillState.OverBudget -> "Over budget"
    }
    val pillBgRes = when (pillState) {
        PillState.OnTrack -> R.drawable.bg_pill_jade
        PillState.WatchPace -> R.drawable.bg_pill_amber
        PillState.OverBudget -> R.drawable.bg_pill_coral
    }
    val pillTextColor = when (pillState) {
        PillState.OnTrack -> Color.parseColor("#C9EBD7")
        PillState.WatchPace -> Color.parseColor("#F2D9A1")
        PillState.OverBudget -> Color.parseColor("#F5C5AC")
    }

    val caption = when {
        !hasIncome -> "Set income in the app"
        spent == 0.0 -> "Nothing spent · ₹${fmtINR(income)} for the month"
        else -> "₹${fmtINR(remaining)} left of ₹${fmtINR(income)}"
    }

    val comparison: String? = if (data.prevMonthSpent > 0 && hasIncome && spent > 0) {
        val pct = ((spent - data.prevMonthSpent) / data.prevMonthSpent * 100).roundToInt()
        val sign = if (pct <= 0) "−" else "+"
        "$sign${abs(pct)}% vs ${data.prevMonthLabel}"
    } else null

    val launchPi = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    return RemoteViews(context.packageName, R.layout.budget_pulse_widget_layout).apply {
        setOnClickPendingIntent(R.id.widget_root, launchPi)

        setTextViewText(R.id.header_date,
            "${data.monthLabel} · DAY ${data.dayOfMonth} / ${data.daysInMonth}")

        setImageViewBitmap(R.id.donut_image, donutBitmap)
        setTextViewText(R.id.donut_percent, if (!hasIncome) "—" else "$percent%")

        setTextViewText(R.id.spent_label, "SPENT · ${data.monthLabel}")
        setTextViewText(R.id.spent_amount, fmtINR(spent))
        setTextViewText(R.id.budget_caption, caption)

        setInt(R.id.status_pill, "setBackgroundResource", pillBgRes)
        setTextViewText(R.id.status_pill, pillText)
        setTextColor(R.id.status_pill, pillTextColor)
        setViewVisibility(R.id.status_pill, View.VISIBLE)

        if (comparison != null) {
            setTextViewText(R.id.comparison_text, comparison)
            setViewVisibility(R.id.comparison_text, View.VISIBLE)
        } else {
            setViewVisibility(R.id.comparison_text, View.GONE)
        }
    }
}

