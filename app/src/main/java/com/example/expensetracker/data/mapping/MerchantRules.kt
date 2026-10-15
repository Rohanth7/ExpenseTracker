package com.example.expensetracker.data.mapping

import com.example.expensetracker.data.db.entity.MerchantMapping

/**
 * Rules for learned "merchant → category" mappings: which names are worth learning, how a
 * learned name matches a new transaction, and which existing mappings are broken.
 */
object MerchantRules {
    private const val MIN_LENGTH = 4

    // Descriptions the parsers use when they couldn't find a payee. Learning these would send
    // every unknown payment from that app or bank into one category.
    private val FALLBACK_NAMES = setOf(
        "upi", "bank debit", "gpay", "phonepe", "paytm", "bhim", "amazon pay", "cred",
        "mobikwik", "freecharge",
        // Footer words the old regex picked up as payees ("Not you? SMS BLOCKUPI Cust ID ...")
        "cust", "customer", "ref", "refno", "report", "dispute", "your", "vpa"
    )

    private val RAW_TEXT_SIGNS = Regex(
        """(?:Rs\.?\s*\d|INR\s*\d|₹\s*\d|(?<![\p{L}\p{N}])(?:debited|credited|a/c|acct|avl|bal|ref|refno|txn)(?![\p{L}\p{N}]))""",
        RegexOption.IGNORE_CASE
    )

    /** Whether a transaction description is a real payee name that can be learned. */
    fun isLearnable(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.length < MIN_LENGTH) return false
        if (!trimmed.any { it.isLetter() }) return false
        val lower = trimmed.lowercase()
        if (lower in FALLBACK_NAMES || lower.endsWith(" debit")) return false
        // Old notification fallback: "GPay: <start of the notification text>"
        if (Regex("""^[\w ]{2,15}: """).containsMatchIn(trimmed)) return false
        // Old SMS fallback stored the first 60 characters of the message.
        if (RAW_TEXT_SIGNS.containsMatchIn(trimmed)) return false
        return true
    }

    private fun containsWord(text: String, word: String): Boolean =
        Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(word) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)

    /**
     * Finds the category for [merchant]. An exact match (ignoring case) wins. Otherwise a learned
     * name must appear as whole words in the merchant, or the merchant in the learned name, and
     * the shorter side must be at least 4 characters. The longest matching name wins, so
     * "Swiggy Instamart" beats "Swiggy".
     */
    fun findCategoryId(merchant: String, mappings: List<MerchantMapping>): Long? {
        val m = merchant.trim()
        if (m.isEmpty()) return null
        mappings.firstOrNull { it.merchantName.trim().equals(m, ignoreCase = true) }?.let { return it.categoryId }
        return mappings
            .filter { mapping ->
                val name = mapping.merchantName.trim()
                val shorter = if (name.length <= m.length) name else m
                shorter.length >= MIN_LENGTH && (containsWord(m, name) || containsWord(name, m))
            }
            .maxByOrNull { it.merchantName.length }
            ?.categoryId
    }

    /**
     * Learned names that are broken: either not learnable at all, or a fragment of a word in the
     * SMS they were learned from ("mer" out of "Customer").
     *
     * [smsBodiesByName] maps a learned name to the stored SMS or notification text of expenses
     * with that description.
     */
    fun findBroken(mappings: List<MerchantMapping>, smsBodiesByName: Map<String, List<String>>): List<String> =
        mappings.map { it.merchantName }.filter { name ->
            if (!isLearnable(name)) return@filter true
            // Notification descriptions look like "GPay → Swiggy"; only the payee is in the text.
            val payee = name.substringAfter(" → ").trim()
            if (payee.length < MIN_LENGTH) return@filter true
            val bodies = smsBodiesByName[name].orEmpty().filter { it.contains(payee, ignoreCase = true) }
            bodies.isNotEmpty() && bodies.none { containsWord(it, payee) }
        }
}
