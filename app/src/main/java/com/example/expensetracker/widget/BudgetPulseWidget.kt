package com.example.expensetracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.example.expensetracker.LaunchAction
import com.example.expensetracker.MainActivity
import com.example.expensetracker.R
import com.example.expensetracker.ui.util.fmtINR
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.ui.mascot.mascotBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

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
                appWidgetIds.forEach { id ->
                    val width = appWidgetManager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                    val views = if (data.widgetEnabled) buildViews(context, data, width.toFloat()) else buildDisabledViews(context)
                    appWidgetManager.updateAppWidget(id, views)
                }
            } catch (e: Exception) {
                android.util.Log.e("BudgetPulseWidget", "update failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    /** Resized: the text sizes depend on the width. */
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
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

/**
 * Every version of the widget starts from this. A launcher may apply new views on top of the ones
 * it shows rather than inflating the layout again, so anything not set here would keep its last value.
 */
private fun blankViews(context: Context): RemoteViews =
    RemoteViews(context.packageName, R.layout.budget_pulse_widget_layout).apply {
        setClicks(context)
        for (id in listOf(R.id.widget_label, R.id.widget_amount, R.id.widget_used, R.id.widget_days_left, R.id.widget_note)) setTextViewText(id, "")
        for (id in listOf(R.id.widget_mascot, R.id.widget_bar, R.id.widget_bar_over, R.id.widget_stats, R.id.widget_note)) setViewVisibility(id, View.GONE)
        setTextColor(R.id.widget_amount, INK)
    }

private fun buildDisabledViews(context: Context): RemoteViews = blankViews(context).apply {
    setTextViewText(R.id.widget_note, "Widget disabled in settings")
    setViewVisibility(R.id.widget_note, View.VISIBLE)
}

/** Empty until the data has loaded. */
private fun buildPlaceholder(context: Context): RemoteViews = blankViews(context)

/**
 * A tap anywhere opens the app; the "+" opens the add-expense dialog. Set on every version of the
 * widget (placeholder and disabled too) so it's never dead. Different request codes (0 and 1) keep
 * the two PendingIntents from overwriting each other.
 */
private fun RemoteViews.setClicks(context: Context) {
    setOnClickPendingIntent(R.id.widget_root, widgetOpenAppIntent(context))
    setOnClickPendingIntent(R.id.widget_add, widgetQuickAddIntent(context))
}

// Each gets its own action. Android tells PendingIntents apart by target, action, data and request
// code, not extras; without an action, the "+" (request code 1) was the same PendingIntent as the
// Categorize notification for expense 1, and whichever was built last overwrote the other's extras.
private const val ACTION_WIDGET_OPEN = "com.example.expensetracker.action.WIDGET_OPEN"
private const val ACTION_WIDGET_QUICK_ADD = "com.example.expensetracker.action.WIDGET_QUICK_ADD"

internal fun widgetOpenAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
    context, 0,
    Intent(context, MainActivity::class.java).apply {
        action = ACTION_WIDGET_OPEN
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    },
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)

internal fun widgetQuickAddIntent(context: Context): PendingIntent = PendingIntent.getActivity(
    context, 1,
    Intent(context, MainActivity::class.java).apply {
        action = ACTION_WIDGET_QUICK_ADD
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(LaunchAction.EXTRA_ACTION, LaunchAction.QUICK_ADD)
    },
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)

/** What the widget says, worked out from [WidgetData] alone so it can be tested without a launcher. */
internal data class WidgetText(
    val label: String,
    val amount: String,
    val over: Boolean,
    /** Share of the income used (spent plus still due), for the bar; null without an income. */
    val barPercent: Int?,
    val used: String,
    val daysLeft: String,
    /** In place of the bar and stats when there's no income. */
    val note: String?
)

internal fun widgetText(data: WidgetData): WidgetText {
    if (!data.hasIncome) return WidgetText(
        label = "Spent this month", amount = "₹${fmtINR(data.spent)}", over = false,
        barPercent = null, used = "", daysLeft = "", note = "Set your income in the app"
    )
    val free = data.freeToSpend
    val usedPercent = floor((data.spent + data.stillDue) / data.income * 100).toInt()
    return WidgetText(
        label = if (free < 0) "Over this month" else "Free to spend",
        amount = "₹${fmtINR(if (free < 0) -free else free)}",
        over = free < 0,
        barPercent = usedPercent.coerceIn(0, 100),
        used = "$usedPercent% used",
        daysLeft = if (data.daysLeft == 1) "1 day left" else "${data.daysLeft} days left",
        note = null
    )
}

/** [widthDp] is the widget's width on the home screen; 0 when the launcher hasn't said. */
private fun buildViews(context: Context, data: WidgetData, widthDp: Float): RemoteViews {
    val text = widgetText(data)
    return blankViews(context).apply {
        // Her face follows Home's (the same mascotMonth). Without her, the text starts at the left edge.
        if (data.mood != null) {
            val density = context.resources.displayMetrics.density.takeIf { it > 0f } ?: 2f
            setImageViewBitmap(R.id.widget_mascot, mascotBitmap(data.mood, (58 * density).roundToInt()))
            setViewVisibility(R.id.widget_mascot, View.VISIBLE)
        }

        setTextViewText(R.id.widget_label, text.label)
        setTextViewText(R.id.widget_amount, text.amount)
        if (text.over) setTextColor(R.id.widget_amount, PINK)
        // A large font or a narrow widget would cut the text off; it gets smaller instead.
        if (widthDp > 0f) {
            val inner = widthDp - 2 * PADDING_DP
            val beside = inner - ADD_DP - GAP_DP - (if (data.mood != null) MASCOT_DP + GAP_DP else 0f)
            setTextViewTextSize(R.id.widget_label, TypedValue.COMPLEX_UNIT_SP, fitSp(context, listOf(text.label), beside, SMALL_SP))
            setTextViewTextSize(R.id.widget_amount, TypedValue.COMPLEX_UNIT_SP, fitSp(context, listOf(text.amount), beside, AMOUNT_SP, bold = true))
            val stats = fitSp(context, listOf(text.used, text.daysLeft), inner, SMALL_SP, gapDp = GAP_DP)
            setTextViewTextSize(R.id.widget_used, TypedValue.COMPLEX_UNIT_SP, stats)
            setTextViewTextSize(R.id.widget_days_left, TypedValue.COMPLEX_UNIT_SP, stats)
            text.note?.let { setTextViewTextSize(R.id.widget_note, TypedValue.COMPLEX_UNIT_SP, fitSp(context, listOf(it), inner, SMALL_SP)) }
        }

        if (text.barPercent != null) {
            if (text.over) setViewVisibility(R.id.widget_bar_over, View.VISIBLE)
            else {
                setProgressBar(R.id.widget_bar, 100, text.barPercent, false)
                setViewVisibility(R.id.widget_bar, View.VISIBLE)
            }
            setTextViewText(R.id.widget_used, text.used)
            setTextViewText(R.id.widget_days_left, text.daysLeft)
            setViewVisibility(R.id.widget_stats, View.VISIBLE)
        }
        if (text.note != null) {
            setTextViewText(R.id.widget_note, text.note)
            setViewVisibility(R.id.widget_note, View.VISIBLE)
        }
    }
}

private const val INK = 0xFFF4EEDD.toInt()
private const val PINK = 0xFFF2668C.toInt()

// The layout's sizes, in dp and sp.
private const val PADDING_DP = 14f
private const val MASCOT_DP = 58f
private const val ADD_DP = 40f
private const val GAP_DP = 10f
private const val AMOUNT_SP = 23f
private const val SMALL_SP = 11.5f

/**
 * The largest size from [maxSp] down that fits [texts] side by side in [widthDp], [gapDp] apart,
 * at the phone's font scale. A large font is given up first; it stops at 70% of the design size
 * at the normal font scale, which a 3-wide widget on a 320dp phone at the largest font needs.
 */
internal fun fitSp(context: Context, texts: List<String>, widthDp: Float, maxSp: Float, bold: Boolean = false, gapDp: Float = 0f): Float {
    val metrics = context.resources.displayMetrics
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL) }
    val room = (widthDp - gapDp * (texts.size - 1)) * metrics.density
    val smallest = maxSp * .7f / context.resources.configuration.fontScale.coerceAtLeast(1f)
    var sp = maxSp
    while (sp - .5f >= smallest) {
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics)
        if (texts.sumOf { paint.measureText(it).toDouble() } <= room) break
        sp -= .5f
    }
    return sp
}
