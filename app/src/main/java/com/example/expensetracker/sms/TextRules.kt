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

    /** First match of the amount patterns, with thousands separators removed. */
    fun extractAmount(text: String, patterns: List<Regex>): Double? {
        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            val value = match.groupValues[1].replace(",", "").toDoubleOrNull()
            if (value != null && value > 0) return value
        }
        return null
    }

    val AMOUNT_PATTERNS = listOf(
        Regex("""(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        // SBI: "A/C X1234 debited by 500.0 on date ..."
        Regex("""(?:debited|deducted|withdrawn)\s+(?:by|for|of)\s+([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""([\d,]+(?:\.\d{1,2})?)\s*(?:Rs\.?|INR|₹)""", RegexOption.IGNORE_CASE)
    )
}
