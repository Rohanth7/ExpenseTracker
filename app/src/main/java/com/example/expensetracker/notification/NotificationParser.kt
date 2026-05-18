package com.example.expensetracker.notification

import com.example.expensetracker.sms.ParsedTransaction
import java.util.regex.Pattern

object NotificationParser {

    private val UPI_PACKAGES = setOf(
        "com.google.android.apps.nbu.paisa.user",  // GPay
        "com.phonepe.app",                          // PhonePe
        "net.one97.paytm",                          // Paytm
        "in.org.npci.upiapp",                       // BHIM
        "in.amazon.mShop.android.shopping",         // Amazon Pay
        "com.dreamplug.androidapp",                 // CRED
        "com.whatsapp",                             // WhatsApp Pay
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

    private val AMOUNT_PATTERNS = listOf(
        Pattern.compile("""(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""([\d,]+(?:\.\d{1,2})?)\s*(?:Rs\.?|INR|₹)""", Pattern.CASE_INSENSITIVE)
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
        "expiring", "expires soon"
    )

    fun isMonitored(packageName: String) = packageName in UPI_PACKAGES || packageName in BANK_PACKAGES

    fun parse(title: String, body: String, packageName: String): ParsedTransaction? {
        val fullText = "$title $body"
        val lowerText = fullText.lowercase()

        // Reject bill reminders, offers, and promotions
        if (EXCLUDE_KEYWORDS.any { lowerText.contains(it) }) return null

        // Must have a confirmed debit keyword — amount alone is not enough
        val isDebit = DEBIT_KEYWORDS.any { lowerText.contains(it) }
        if (!isDebit) return null

        // Skip credits
        val isCredit = CREDIT_KEYWORDS.any { lowerText.contains(it) }
        if (isCredit) return null

        val amount = extractAmount(fullText) ?: return null
        val description = extractDescription(title, body, packageName)
        return ParsedTransaction(amount, description)
    }

    private fun extractAmount(text: String): Double? {
        for (pattern in AMOUNT_PATTERNS) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                return matcher.group(1)?.replace(",", "")?.toDoubleOrNull()
            }
        }
        return null
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

        // Try to extract merchant name from body
        val merchantPattern = Pattern.compile(
            """(?:paid to|sent to|to|at|merchant[:\s]+)([\w\s.\-@]{2,30})""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = merchantPattern.matcher(body)
        if (matcher.find()) {
            val merchant = matcher.group(1)?.trim()?.take(30)
            if (!merchant.isNullOrBlank()) return "$appName → $merchant"
        }

        return if (body.isNotBlank()) "$appName: ${body.take(50)}" else appName
    }
}
