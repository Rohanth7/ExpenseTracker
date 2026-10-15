package com.example.expensetracker.sms

data class ParsedTransaction(
    val amount: Double,
    val description: String
)

object SmsParser {
    private val DEBIT_KEYWORDS = listOf(
        "debited", "deducted", "spent", "paid", "payment", "purchase",
        "withdrawn", "charged", "debit", "sent to", "transferred to", "sent"
    )

    // Past-tense words that confirm money already left the account.
    private val STRONG_DEBIT_KEYWORDS = listOf("debited", "deducted", "withdrawn", "spent")

    private val CREDIT_KEYWORDS = listOf(
        "credited", "received", "deposited", "refund", "refunded", "cashback",
        "paid to your", "sent to your", "to your account", "to your a/c", "to your ac"
    )

    private val KNOWN_SENDERS = listOf(
        "hdfcbk", "sbiinb", "sbiupi", "sbipsg", "sbicrd", "icicib", "icicit", "axisbk", "kotakb", "yesbnk",
        "pnbsms", "bobsms", "bobtxn", "indbnk", "paytm", "phonepe", "gpay",
        "amazon", "juspay", "razorpay", "upi", "idfcbk", "idfcfb", "scbank"
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

    // Genuine bank/payment SMS always reference an account or card. Matched as substrings
    // because masked numbers look like "XXXX1234".
    private val ACCOUNT_REFERENCE_KEYWORDS = listOf(
        "a/c", "acct", "account", "ac no", "ac-", "xxxx",
        "ending with", "ending in", "card no", "wallet", "your upi"
    )

    // Always rejected: money hasn't moved yet, or the payment didn't go through.
    private val NOT_YET_DEBITED = listOf(
        "will be debited", "will be deducted", "will be charged", "to be debited",
        "would be debited", "shall be debited", "is scheduled", "scheduled for", "will be auto-debited",
        "failed", "declined", "unsuccessful", "could not be processed", "insufficient"
    )

    // Always rejected: bill and card-statement reminders.
    private val REMINDER_KEYWORDS = listOf(
        "is due", "due date", "due on", "due by", "bill due", "overdue", "payment due",
        "payment reminder", "upcoming payment", "upcoming bill", "pay now", "pay before",
        "last date", "due amount", "amt due", "amount due", "min due", "total due"
    )

    // Rejected unless a past-tense debit word is present. An autopay that actually ran says
    // "Rs 199 debited ... towards e-mandate", which should still be logged.
    private val SOFT_EXCLUSIONS = listOf(
        "otp", "one time password", "verification code",
        "mandate", "e-mandate", "autopay", "auto pay", "standing instruction", "registered", "registration",
        "collect request", "requested", "has requested", "request from", "approve"
    )

    fun isTransactionSms(sender: String, body: String): Boolean {
        val senderLower = sender.lowercase()
        val bodyLower = body.lowercase()
        val fromKnownSender = KNOWN_SENDERS.any { senderLower.contains(it) }
        val hasAmount = TextRules.extractAmount(body, TextRules.AMOUNT_PATTERNS) != null
        val hasDebitKeyword = TextRules.containsAnyWord(body, DEBIT_KEYWORDS)

        if (!hasAmount || !(fromKnownSender || hasDebitKeyword)) return false

        // Promotional SMS filter: if the message contains promo signals and no account
        // reference, and is not from a known bank/payment sender, reject it.
        if (!fromKnownSender) {
            val hasPromoKeyword = TextRules.containsAnyWord(body, PROMO_EXCLUSION_KEYWORDS)
            val hasAccountRef = ACCOUNT_REFERENCE_KEYWORDS.any { bodyLower.contains(it) }
            if (hasPromoKeyword && !hasAccountRef) return false
        }

        return true
    }

    fun parse(sender: String, body: String): ParsedTransaction? {
        val amount = TextRules.extractAmount(body, TextRules.AMOUNT_PATTERNS) ?: return null
        // Require a debit keyword; skip credits
        if (!TextRules.containsAnyWord(body, DEBIT_KEYWORDS)) return null
        if (TextRules.containsAnyWord(body, NOT_YET_DEBITED)) return null
        if (TextRules.containsAnyWord(body, REMINDER_KEYWORDS)) return null

        val hasStrongDebit = TextRules.containsAnyWord(body, STRONG_DEBIT_KEYWORDS)
        if (!hasStrongDebit && TextRules.containsAnyWord(body, SOFT_EXCLUSIONS)) return null
        // HDFC UPI SMS says "debited from a/c ... PhonePe/ref credited" in the same message.
        // Only reject on credit keywords when there is no strong explicit debit word.
        if (!hasStrongDebit && TextRules.containsAnyWord(body, CREDIT_KEYWORDS)) return null

        val description = MerchantExtractor.extract(body) ?: MerchantExtractor.fallbackDescription(sender)
        return ParsedTransaction(amount, description)
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
}
