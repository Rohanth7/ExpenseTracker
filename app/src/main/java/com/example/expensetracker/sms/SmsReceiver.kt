package com.example.expensetracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.dao.MappingHelper
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.notification.NotificationHelper
import com.example.expensetracker.notification.PriceChangeAlerts
import com.example.expensetracker.notification.QuickCategories
import com.example.expensetracker.ui.widgets.WidgetUpdateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val captures = captures(messages.toList(), ParsingRuleStore.get(context).rules.value)
        if (captures.isEmpty()) return

        // goAsync() called once for the entire onReceive, not per message
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                save(context, AppDatabase.getInstance(context), captures, System.currentTimeMillis())
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** A payment read from one SMS (all its parts); [sentAt] is the time the network gave it. */
    data class Capture(val transaction: ParsedTransaction, val body: String, val sender: String, val sentAt: Long)

    companion object {
        fun captures(messages: List<SmsMessage>, rules: List<ParsingRule>): List<Capture> {
            // A long SMS arrives as several parts in one broadcast; join them per sender so the
            // parser (and the user's rules) see the whole message, not just the first 160 characters.
            val joined = messages
                .filter { it.originatingAddress != null && it.messageBody != null }
                .groupBy { it.originatingAddress!! }
            return joined.mapNotNull { (sender, parts) ->
                val body = parts.joinToString("") { it.messageBody!! }
                // The user's own rules come first: they know their bank's messages best.
                val transaction = when (val custom = CustomRules.match(rules, body)) {
                    RuleMatch.Ignore -> return@mapNotNull null
                    is RuleMatch.Payment -> ParsedTransaction(custom.amount, custom.payee ?: MerchantExtractor.fallbackDescription(sender))
                    null -> {
                        if (!SmsParser.isTransactionSms(sender, body)) return@mapNotNull null
                        SmsParser.parse(sender, body) ?: return@mapNotNull null
                    }
                }
                Capture(transaction, body, sender, parts.minOf { it.timestampMillis })
            }
        }

        /**
         * When the payment happened: the SMS's sent time, so one that arrives late (phone off,
         * no signal) lands in the right day and budget month. Never in the future: some
         * networks' clocks are off.
         */
        fun paymentTime(sentAt: Long, now: Long): Long = if (sentAt in 1..now) sentAt else now

        suspend fun save(context: Context, db: AppDatabase, captures: List<Capture>, now: Long) {
            // With no categories yet the expense is still saved, as pending (categoryId -1).
            val saved = mutableListOf<Expense>()

            captures.forEach { (transaction, body, _, sentAt) ->
                val isCc = SmsParser.isCreditCardSms(body)
                val source = if (isCc) "CC-SMS" else "SMS"
                val date = paymentTime(sentAt, now)
                if (CaptureDuplicates.isDuplicate(db.expenseDao(), transaction.amount, source, transaction.description, body, date)) return@forEach

                // Smart auto-categorization with fuzzy matching
                val mappedCategoryId = MappingHelper.getCategoryId(db, transaction.description)
                val finalCategoryId = mappedCategoryId ?: -1L
                val expense = Expense(
                    amount = transaction.amount,
                    description = transaction.description,
                    categoryId = finalCategoryId,
                    date = date,
                    source = source,
                    rawSms = body,
                    paymentMethod = SmsParser.detectPaymentMethod(body)
                )
                val id = db.expenseDao().insert(expense)
                NotificationHelper.showCategorizationNotification(
                    context, id, transaction.amount, transaction.description,
                    QuickCategories.forNewExpense(db, mappedCategoryId)
                )
                saved += expense.copy(id = id)
            }
            WidgetUpdateHelper.update(context)
            // After everything is saved and shown; a problem in price-change detection mustn't affect it.
            saved.forEach { runCatching { PriceChangeAlerts.checkAfterCapture(context, db, it) } }
        }
    }
}
