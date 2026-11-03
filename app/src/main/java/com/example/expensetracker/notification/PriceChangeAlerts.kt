package com.example.expensetracker.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.expensetracker.LaunchAction
import com.example.expensetracker.MainActivity
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.preferences.PreferencesManager
import com.example.expensetracker.data.subscriptions.Cadence
import com.example.expensetracker.data.subscriptions.DismissedSubscriptions
import com.example.expensetracker.data.subscriptions.Subscription
import com.example.expensetracker.data.subscriptions.SubscriptionDetector
import com.example.expensetracker.ui.screens.subscriptions.formatRupees
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * One notification when a subscription's new price is first charged. Free for now; it will be a
 * Pro feature, so all alerting goes through [checkAfterCapture].
 */
object PriceChangeAlerts {
    const val CHANNEL_ID = "subscription_alerts"
    /** Which changes have been alerted. Not in backups: a restore isn't a capture, so it can't alert. */
    private const val PREFS = "price_alert_prefs"
    /** Notifications are told apart by tag and id, so these ids can't clash with other alerts'. */
    private const val TAG = "price_change"
    private const val ACTION_OPEN_SUBSCRIPTIONS = "com.example.expensetracker.action.OPEN_SUBSCRIPTIONS"
    /** How close a capture must be to now, and to the change, to be the payment that changed it. */
    private val CAPTURE_WINDOW_MS = TimeUnit.DAYS.toMillis(2)
    private val claimLock = Any()

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Subscription price changes", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Tells you when a subscription's price changes" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /** "Netflix price went up" / "₹499 → ₹549 a month · ₹600 more a year", in whole rupees like the cards. */
    fun text(s: Subscription): Pair<String, String> {
        val old = (s.previousAmount ?: s.amount).roundToLong().toDouble()
        val new = s.amount.roundToLong().toDouble()
        val up = new > old
        // The card's name; a bank SMS's "NETFLIX" is title-cased, "YouTube Premium" is kept
        val name = if (s.name != s.name.uppercase()) s.name
            else s.name.lowercase().split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.ROOT) } }
        val per = when (s.cadence) {
            Cadence.WEEKLY -> "a week"
            Cadence.MONTHLY -> "a month"
            Cadence.QUARTERLY -> "every 3 months"
            Cadence.YEARLY -> "a year"
        }
        val prices = "${formatRupees(old)} → ${formatRupees(new)} $per"
        val yearly = abs(new - old) * s.cadence.perYear
        val body = if (s.cadence == Cadence.YEARLY) prices
            else "$prices · ${formatRupees(yearly)} ${if (up) "more" else "less"} a year"
        return "$name price went ${if (up) "up" else "down"}" to body
    }

    /** One key per change, so the same change never alerts twice. */
    fun alertKey(s: Subscription): String = "price_alert_${s.key}_${s.priceChangedOn}"

    /**
     * The change [expense] is the new price of, if any: its payee's subscription changed price within
     * a couple of days of it. The expense must be recent too, so entering last month's payment today
     * doesn't alert about a change from weeks ago.
     */
    fun changeToAlert(subscriptions: List<Subscription>, expense: Expense, now: Long): Subscription? {
        if (!isRecent(expense, now)) return null
        val key = SubscriptionDetector.keyFor(expense.description)
        return subscriptions.firstOrNull { s ->
            s.key == key && s.previousAmount != null && s.priceChangedOn?.let { abs(it - expense.date) <= CAPTURE_WINDOW_MS } == true
        }
    }

    private fun isRecent(expense: Expense, now: Long) = abs(now - expense.date) <= CAPTURE_WINDOW_MS

    /**
     * Whether an edit can make [after] a new price [before] wasn't: its payee or amount changed (a
     * capture saved as "UPI" renamed to "Netflix"). Other edits can't, so they don't load anything.
     */
    fun worthCheckingAfterEdit(before: Expense, after: Expense) =
        after.amount != before.amount || SubscriptionDetector.keyFor(after.description) != SubscriptionDetector.keyFor(before.description)

    private val PLAIN = Regex("[a-z0-9]+")

    /**
     * The word to find [key]'s payments by: its longest run of a–z and 0–9. SQLite's LIKE ignores
     * case for those only, so "école" is found by "cole" in "ÉCOLE"; a key without any (Hindi) has
     * no case to miss.
     */
    internal fun searchWord(key: String): String =
        PLAIN.findAll(key).map { it.value }.maxByOrNull { it.length } ?: key.split(" ").maxBy { it.length }

    /**
     * Call after a new expense is saved (bank SMS, payment app notification, added by hand). Runs
     * off the main thread and finishes even if the caller is cancelled. Paywall gating goes here.
     */
    suspend fun checkAfterCapture(context: Context, db: AppDatabase, expense: Expense) = withContext(NonCancellable + Dispatchers.IO) {
        val app = context.applicationContext
        if (!PreferencesManager(app).priceAlertsEnabled) return@withContext
        val now = System.currentTimeMillis()
        // Cheap checks first, so most captures never load anything.
        if (!(expense.amount > 0) || expense.source == "Recurring" || !isRecent(expense, now)) return@withContext
        val key = SubscriptionDetector.keyFor(expense.description)
        val dismissed = DismissedSubscriptions.flow(app).value
        if (key.isBlank() || key in dismissed) return@withContext
        // Only this payee's payments matter: load those containing one of its words, then match exactly.
        val payments = db.expenseDao().getWithDescriptionContaining(searchWord(key)).filter { SubscriptionDetector.keyFor(it.description) == key }
        val subscriptions = SubscriptionDetector.detect(payments, now, dismissed, db.billDao().getAllOnce().map { it.name })
        val change = changeToAlert(subscriptions, expense, now) ?: return@withContext
        // Stored even when notifications are off (Android 13+ permission): the change still shows
        // on the cards, and turning them on later shouldn't bring back an old alert.
        if (claim(app, alertKey(change))) post(app, change)
    }

    /**
     * True for the first caller with [key]. The bank SMS and the payment app's notification for one
     * charge are handled at the same time, so checking and storing happen together; commit() so
     * the key is on disk before the alert is shown.
     */
    internal fun claim(context: Context, key: String): Boolean = synchronized(claimLock) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(key, false)) return false
        prefs.edit().putBoolean(key, true).commit()
        true
    }

    private fun post(context: Context, s: Subscription) {
        val (title, body) = text(s)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openSubscriptionsIntent(context))
            .build()
        // Without the notification permission Android drops it; never let that fail the capture.
        runCatching {
            context.getSystemService(NotificationManager::class.java).notify(TAG, s.key.hashCode() and 0x7FFFFFFF, notification)
        }
    }

    /** Opens Subscriptions. One for all price alerts; its own action keeps it apart from other launch intents. */
    internal fun openSubscriptionsIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_SUBSCRIPTIONS
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(LaunchAction.EXTRA_ACTION, LaunchAction.OPEN_SUBSCRIPTIONS)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
