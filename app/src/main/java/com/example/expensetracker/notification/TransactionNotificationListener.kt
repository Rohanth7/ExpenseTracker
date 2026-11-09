package com.example.expensetracker.notification

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.dao.MappingHelper
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.sms.CaptureDuplicates
import com.example.expensetracker.sms.CustomRules
import com.example.expensetracker.sms.ParsedTransaction
import com.example.expensetracker.sms.ParsingRule
import com.example.expensetracker.sms.ParsingRuleStore
import com.example.expensetracker.sms.RuleMatch
import com.example.expensetracker.ui.widgets.WidgetUpdateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TransactionNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return
        if (!NotificationParser.isMonitored(packageName)) return
        // Don't process our own notifications
        if (packageName == applicationContext.packageName) return

        val extras = sbn.notification?.extras ?: return
        val title = title(extras)
        val body = text(extras)

        if (title.isBlank() && body.isBlank()) return

        val transaction = transaction(ParsingRuleStore.get(applicationContext).rules.value, title, body, packageName) ?: return

        serviceScope.launch {
            save(applicationContext, AppDatabase.getInstance(applicationContext), transaction, body, System.currentTimeMillis())
        }
    }

    companion object {
        /** Often styled (a SpannableString), which getString() would return as null. */
        fun title(extras: Bundle): String = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""

        /**
         * The notification's text: the short text, the expanded text (BigTextStyle) and the
         * lines (InboxStyle). Some apps put the payment only in the expanded part ("Tap to view
         * details"). A part the others already contain is left out, so text the app repeats
         * isn't read twice.
         */
        fun text(extras: Bundle): String {
            val parts = listOfNotNull(
                extras.getCharSequence(Notification.EXTRA_TEXT),
                extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
                extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")
            ).map { it.toString().trim() }.filter { it.isNotEmpty() }
            val kept = mutableListOf<String>()
            for (part in parts) {
                if (kept.any { part in it }) continue
                kept.removeAll { it in part }
                kept += part
            }
            return kept.joinToString("\n")
        }

        /**
         * The user's message rules come first, as for SMS. They're matched against the text an
         * expense keeps (what "Teach" starts from), then with the title, as pasted from the shade.
         */
        fun transaction(rules: List<ParsingRule>, title: String, body: String, packageName: String): ParsedTransaction? =
            when (val custom = CustomRules.match(rules, body) ?: CustomRules.match(rules, "$title\n$body")) {
                RuleMatch.Ignore -> null
                is RuleMatch.Payment -> ParsedTransaction(custom.amount, NotificationParser.describe(packageName, title, custom.payee))
                null -> NotificationParser.parse(title, body, packageName)
            }

        suspend fun save(context: Context, db: AppDatabase, transaction: ParsedTransaction, body: String, now: Long) {
            // With no categories yet the expense is still saved, as pending (categoryId -1).
            if (CaptureDuplicates.isDuplicate(db.expenseDao(), transaction.amount, "UPI", transaction.description, body, now)) return

            // Smart auto-categorization with fuzzy matching
            val mappedCategoryId = MappingHelper.getCategoryId(db, transaction.description)
            val finalCategoryId = mappedCategoryId ?: -1L

            val expense = Expense(
                amount = transaction.amount,
                description = transaction.description,
                categoryId = finalCategoryId,
                date = now,
                source = "UPI",
                rawSms = body,
                paymentMethod = "UPI"
            )
            val id = db.expenseDao().insert(expense)
            NotificationHelper.showCategorizationNotification(
                context, id, transaction.amount, transaction.description,
                QuickCategories.forNewExpense(db, mappedCategoryId)
            )
            WidgetUpdateHelper.update(context)
            // The expense is saved; a problem in either check mustn't affect it.
            BudgetAlertHelper.checkAfterCapture(context, db, finalCategoryId)
            runCatching { PriceChangeAlerts.checkAfterCapture(context, db, expense.copy(id = id)) }
        }
    }
}
