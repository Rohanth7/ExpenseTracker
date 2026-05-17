package com.example.expensetracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.expensetracker.data.db.AppDatabase
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val transactions = messages.mapNotNull { msg ->
            val sender = msg.originatingAddress ?: return@mapNotNull null
            val body = msg.messageBody ?: return@mapNotNull null
            if (!SmsParser.isTransactionSms(sender, body)) return@mapNotNull null
            val transaction = SmsParser.parse(sender, body) ?: return@mapNotNull null
            Triple(transaction, body, sender)
        }

        if (transactions.isEmpty()) return

        // goAsync() called once for the entire onReceive, not per message
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                transactions.forEach { (transaction, body, _) ->
                    if (isDuplicate(db, transaction.amount)) return@forEach
                    val expense = Expense(
                        amount = transaction.amount,
                        description = transaction.description,
                        categoryId = -1L,
                        source = "SMS",
                        rawSms = body
                    )
                    val id = db.expenseDao().insert(expense)
                    NotificationHelper.showCategorizationNotification(
                        context, id, transaction.amount, transaction.description
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun isDuplicate(db: AppDatabase, amount: Double): Boolean {
        val now = System.currentTimeMillis()
        // Cross-source: UPI notification + bank SMS for same transaction (3-min window)
        if (db.expenseDao().getRecentByAmountFromDifferentSource(amount, "SMS", now - 3 * 60 * 1000L) > 0) return true
        // Same-source: SMS broadcast fired twice (30-sec window)
        if (db.expenseDao().getRecentByAmountFromSameSource(amount, "SMS", now - 30 * 1000L) > 0) return true
        return false
    }
}
