package com.example.expensetracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
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
        // A long SMS arrives as several parts in one broadcast; join them per sender so the
        // parser (and the user's rules) see the whole message, not just the first 160 characters.
        val joined = messages
            .filter { it.originatingAddress != null && it.messageBody != null }
            .groupBy { it.originatingAddress!! }
            .mapValues { (_, parts) -> parts.joinToString("") { it.messageBody!! } }
        val rules = ParsingRuleStore.get(context).rules.value
        val transactions = joined.mapNotNull { (sender, body) ->
            // The user's own rules come first: they know their bank's messages best.
            val transaction = when (val custom = CustomRules.match(rules, body)) {
                RuleMatch.Ignore -> return@mapNotNull null
                is RuleMatch.Payment -> ParsedTransaction(custom.amount, custom.payee ?: MerchantExtractor.fallbackDescription(sender))
                null -> {
                    if (!SmsParser.isTransactionSms(sender, body)) return@mapNotNull null
                    SmsParser.parse(sender, body) ?: return@mapNotNull null
                }
            }
            Triple(transaction, body, sender)
        }

        if (transactions.isEmpty()) return

        // goAsync() called once for the entire onReceive, not per message
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                // With no categories yet the expense is still saved, as pending (categoryId -1).
                val saved = mutableListOf<Expense>()

                transactions.forEach { (transaction, body, _) ->
                    val isCc = SmsParser.isCreditCardSms(body)
                    val source = if (isCc) "CC-SMS" else "SMS"
                    if (CaptureDuplicates.isDuplicate(db.expenseDao(), transaction.amount, source, transaction.description, body, System.currentTimeMillis())) return@forEach

                    // Smart auto-categorization with fuzzy matching
                    val mappedCategoryId = MappingHelper.getCategoryId(db, transaction.description)
                    val finalCategoryId = mappedCategoryId ?: -1L
                    val expense = Expense(
                        amount = transaction.amount,
                        description = transaction.description,
                        categoryId = finalCategoryId,
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
            } finally {
                pendingResult.finish()
            }
        }
    }
}
