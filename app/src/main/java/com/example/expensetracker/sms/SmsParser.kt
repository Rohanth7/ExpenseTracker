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
        "withdrawn", "charged", "debit", "sent to", "transferred to"
    )

    private val CREDIT_KEYWORDS = listOf(
        "credited", "received", "deposited", "refund", "cashback",
        "paid to your", "sent to your", "to your account", "to your a/c", "to your ac"
    )

    private val KNOWN_SENDERS = listOf(
        "hdfcbk", "sbiinb", "icicib", "axisbk", "kotakb", "yesbnk",
        "pnbsms", "bobsms", "indbnk", "paytm", "phonepe", "gpay",
        "amazon", "juspay", "razorpay", "upi", "idfcbk", "scbank"
    )

    fun isTransactionSms(sender: String, body: String): Boolean {
        val senderLower = sender.lowercase()
        val bodyLower = body.lowercase()
        val fromKnownSender = KNOWN_SENDERS.any { senderLower.contains(it) }
        val hasAmount = AMOUNT_PATTERNS.any { it.matcher(body).find() }
        val hasDebitKeyword = DEBIT_KEYWORDS.any { bodyLower.contains(it) }
        return hasAmount && (fromKnownSender || hasDebitKeyword)
    }

    fun parse(sender: String, body: String): ParsedTransaction? {
        val amount = extractAmount(body) ?: return null
        val bodyLower = body.lowercase()
        // Require a debit keyword; skip credits
        if (!DEBIT_KEYWORDS.any { bodyLower.contains(it) }) return null
        if (CREDIT_KEYWORDS.any { bodyLower.contains(it) }) return null
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
