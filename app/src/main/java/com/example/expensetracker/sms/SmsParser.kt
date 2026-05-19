package com.example.expensetracker.sms

import java.util.regex.Pattern

data class ParsedTransaction(
    val amount: Double,
    val description: String
)

object SmsParser {
    private val AMOUNT_PATTERNS = listOf(
        Pattern.compile("""(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""([\d,]+(?:\.\d{1,2})?)\s*(?:Rs\.?|INR|₹)""", Pattern.CASE_INSENSITIVE)
    )

    private val DEBIT_KEYWORDS = listOf(
        "debited", "deducted", "spent", "paid", "payment", "purchase",
        "withdrawn", "charged", "debit", "sent to", "transferred to", "sent"
    )

    private val STRONG_DEBIT_KEYWORDS = listOf("debited", "deducted", "withdrawn")

    private val CREDIT_KEYWORDS = listOf(
        "credited", "received", "deposited", "refund", "cashback",
        "paid to your", "sent to your", "to your account", "to your a/c", "to your ac"
    )

    private val KNOWN_SENDERS = listOf(
        "hdfcbk", "sbiinb", "icicib", "axisbk", "kotakb", "yesbnk",
        "pnbsms", "bobsms", "indbnk", "paytm", "phonepe", "gpay",
        "amazon", "juspay", "razorpay", "upi", "idfcbk", "scbank"
    )

    // Words that signal a promotional/marketing SMS even if it contains an amount
    private val PROMO_EXCLUSION_KEYWORDS = listOf(
        "subscribe", "subscription", "recharge", "validity",
        "pack", "plan activated", "promo", "coupon", "offer code",
        "reply stop", "reply yes", "reply no", "to unsubscribe",
        "click here", "download our app", "install", "t&c apply",
        "to activate", "to avail", "limited time", "expires in",
        "get free", "unlimited calls", "unlimited data", "free sms"
    )

    // Genuine bank/payment SMS always reference an account or card
    private val ACCOUNT_REFERENCE_KEYWORDS = listOf(
        "a/c", "acct", "account", "ac no", "ac-", "xxxx",
        "ending with", "ending in", "card no", "wallet", "your upi"
    )

    fun isTransactionSms(sender: String, body: String): Boolean {
        val senderLower = sender.lowercase()
        val bodyLower = body.lowercase()
        val fromKnownSender = KNOWN_SENDERS.any { senderLower.contains(it) }
        val hasAmount = AMOUNT_PATTERNS.any { it.matcher(body).find() }
        val hasDebitKeyword = DEBIT_KEYWORDS.any { bodyLower.contains(it) }

        if (!hasAmount || !(fromKnownSender || hasDebitKeyword)) return false

        // Promotional SMS filter: if the message contains promo signals and no account
        // reference, and is not from a known bank/payment sender, reject it.
        if (!fromKnownSender) {
            val hasPromoKeyword = PROMO_EXCLUSION_KEYWORDS.any { bodyLower.contains(it) }
            val hasAccountRef = ACCOUNT_REFERENCE_KEYWORDS.any { bodyLower.contains(it) }
            if (hasPromoKeyword && !hasAccountRef) return false
        }

        return true
    }

    fun parse(sender: String, body: String): ParsedTransaction? {
        val amount = extractAmount(body) ?: return null
        val bodyLower = body.lowercase()
        // Require a debit keyword; skip credits
        if (!DEBIT_KEYWORDS.any { bodyLower.contains(it) }) return null
        // HDFC UPI SMS says "debited from a/c ... PhonePe/ref credited" in the same message.
        // Only reject on credit keywords when there is no strong explicit debit word.
        val hasStrongDebit = STRONG_DEBIT_KEYWORDS.any { bodyLower.contains(it) }
        if (!hasStrongDebit && CREDIT_KEYWORDS.any { bodyLower.contains(it) }) return null
        return ParsedTransaction(amount, extractDescription(sender, body))
    }

    private fun extractAmount(body: String): Double? {
        for (pattern in AMOUNT_PATTERNS) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                return matcher.group(1)?.replace(",", "")?.toDoubleOrNull()
            }
        }
        return null
    }

    fun isCreditCardSms(body: String): Boolean {
        val lower = body.lowercase()
        return lower.contains("credit card") ||
            lower.contains("credit a/c") ||
            lower.contains("cc a/c") ||
            lower.contains("card ending") ||
            lower.contains("card no.") ||
            lower.contains("card xx") ||
            lower.contains("credit acct")
    }

    fun detectPaymentMethod(body: String): String =
        if (isCreditCardSms(body)) "Credit Card" else "UPI"

    private fun extractDescription(sender: String, body: String): String {
        val upiPattern = Pattern.compile(
            """(?:to|at|merchant[:\s]+|UPI[:\s]+)([\w.\-@]{3,30})""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = upiPattern.matcher(body)
        if (matcher.find()) {
            val merchant = matcher.group(1)?.trim()
            if (!merchant.isNullOrBlank()) return merchant
        }
        return body.take(60).trim()
    }
}
