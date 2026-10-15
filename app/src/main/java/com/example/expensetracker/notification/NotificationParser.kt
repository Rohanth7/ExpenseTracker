package com.example.expensetracker.notification

import com.example.expensetracker.sms.ParsedTransaction
import com.example.expensetracker.sms.MerchantExtractor
import com.example.expensetracker.sms.TextRules

object NotificationParser {

    private val UPI_PACKAGES = setOf(
        "com.google.android.apps.nbu.paisa.user",  // GPay
        "com.phonepe.app",                          // PhonePe
        "net.one97.paytm",                          // Paytm
        "in.org.npci.upiapp",                       // BHIM
        "in.amazon.mShop.android.shopping",         // Amazon Pay
        "com.dreamplug.androidapp",                 // CRED
        "com.mobikwik_new",                         // MobiKwik
        "com.freecharge.android"                    // Freecharge
    )

    private val BANK_PACKAGES = setOf(
        "com.snapwork.hdfc",
        "com.csam.icici.bank.imobile",
        "com.sbi.lotusintouch",
        "com.axisb.axisnet",
        "com.kotak.mahindra.kotak",
        "com.rbl.bank",
        "com.yesbank"
    )

    private val DEBIT_KEYWORDS = listOf(
        "paid", "sent", "debited", "transferred", "spent", "deducted", "charged",
        "payment successful", "payment done", "payment complete", "successfully paid"
    )

    private val CREDIT_KEYWORDS = listOf(
        "received", "credited", "added", "refund", "cashback",
        "you received", "sent you", "paid you", "paid to you",
        "to your account", "to your wallet", "money received", "amount received",
        "transfer received", "credit received"
    )

    // Notifications with any of these are reminders/offers, not completed transactions
    private val EXCLUDE_KEYWORDS = listOf(
        "is due", "due date", "due on", "due by", "bill due", "overdue",
        "payment due", "payment reminder", "upcoming payment", "upcoming bill",
        "reminder", "pay now", "pay before", "last date", "due amount",
        "promo", "discount", "win", "won",
        "expiring", "expires soon",
        // Not a completed payment
        "will be debited", "will be deducted", "scheduled", "failed", "declined", "unsuccessful",
        "pending", "processing", "requested", "request", "collect request", "approve",
        "otp", "verification code", "mandate", "autopay set up", "autopay setup"
    )

    // WhatsApp is deliberately not monitored: chat messages like "I paid ₹500" were logged as
    // expenses, and reading personal chats is a privacy problem.
    fun isMonitored(packageName: String) = packageName in UPI_PACKAGES || packageName in BANK_PACKAGES

    fun parse(title: String, body: String, packageName: String): ParsedTransaction? {
        val fullText = "$title $body"

        // Reject bill reminders, offers, and promotions
        if (TextRules.containsAnyWord(fullText, EXCLUDE_KEYWORDS)) return null

        // Must have a confirmed debit keyword — amount alone is not enough
        val isDebit = TextRules.containsAnyWord(fullText, DEBIT_KEYWORDS)
        if (!isDebit) return null

        // Skip credits
        val isCredit = TextRules.containsAnyWord(fullText, CREDIT_KEYWORDS)
        if (isCredit) return null

        val amount = TextRules.extractAmount(fullText, TextRules.AMOUNT_PATTERNS) ?: return null
        val description = extractDescription(title, body, packageName)
        return ParsedTransaction(amount, description)
    }

    private fun extractDescription(title: String, body: String, packageName: String): String {
        val appName = when (packageName) {
            "com.google.android.apps.nbu.paisa.user" -> "GPay"
            "com.phonepe.app" -> "PhonePe"
            "net.one97.paytm" -> "Paytm"
            "in.org.npci.upiapp" -> "BHIM"
            "in.amazon.mShop.android.shopping" -> "Amazon Pay"
            "com.dreamplug.androidapp" -> "CRED"
            else -> title.ifBlank { "UPI" }
        }

        val merchant = MerchantExtractor.extract(body) ?: MerchantExtractor.extract(title)
        if (merchant != null) return "$appName → $merchant"

        return if (body.isNotBlank()) "$appName: ${body.take(50)}" else appName
    }
}
