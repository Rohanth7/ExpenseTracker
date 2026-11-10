package com.example.expensetracker.sms

/**
 * Keyword matching shared by the SMS and notification parsers.
 *
 * Keywords match whole words only, so "won" doesn't match "won't", "win" doesn't match "window",
 * and "paid" doesn't match "prepaid". A keyword may be a phrase ("will be debited").
 */
internal object TextRules {
    private val cache = HashMap<String, Regex>()

    private fun wordRegex(keyword: String): Regex = synchronized(cache) {
        cache.getOrPut(keyword) {
            Regex("(?<![\\p{L}\\p{N}'’])" + Regex.escape(keyword) + "(?![\\p{L}\\p{N}'’])", RegexOption.IGNORE_CASE)
        }
    }

    fun containsWord(text: String, keyword: String): Boolean = wordRegex(keyword).containsMatchIn(text)

    fun containsAnyWord(text: String, keywords: Collection<String>): Boolean =
        keywords.any { containsWord(text, it) }

    /**
     * First match of the amount patterns, with thousands separators removed. An amount right
     * after "Avl Bal" / "Avl Lmt" / "balance" / "limit" (or right before "available") is the
     * account's balance or card limit, never the payment: "Avl Bal Rs 25,000 after debit of Rs 350"
     * is ₹350, and a "USD 12.99 spent … Avl Lmt: INR 1,23,456" charge has no rupee amount at all.
     */
    fun extractAmount(text: String, patterns: List<Regex>): Double? {
        for (pattern in patterns) {
            for (match in pattern.findAll(text)) {
                if (isBalance(text, match.range)) continue
                val value = match.groupValues[1].replace(",", "").toDoubleOrNull()
                if (value != null && value > 0) return value
            }
        }
        return null
    }

    private val BALANCE_BEFORE = Regex("""(?:\b(?:bal|balance|lmt|limit)|\bavl|\bavbl|\bavailable)(?:\.(?=\S))?\s*(?:is|of|:|-|=)?\s*$""", RegexOption.IGNORE_CASE)
    private val BALANCE_AFTER = Regex("""^\s*(?:is\s+)?(?:your\s+|the\s+)?(?:avl|avbl|available)\b""", RegexOption.IGNORE_CASE)

    private fun isBalance(text: String, range: IntRange): Boolean =
        BALANCE_BEFORE.containsMatchIn(text.substring(maxOf(0, range.first - 30), range.first)) ||
            BALANCE_AFTER.containsMatchIn(text.substring(range.last + 1))

    val AMOUNT_PATTERNS = listOf(
        Regex("""(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        // SBI: "A/C X1234 debited by 500.0 on date ..."
        Regex("""(?:debited|deducted|withdrawn)\s+(?:by|for|of)\s+([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""([\d,]+(?:\.\d{1,2})?)\s*(?:Rs\.?|INR|₹)""", RegexOption.IGNORE_CASE)
    )
}
