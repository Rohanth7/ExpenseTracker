package com.example.expensetracker.sms

import com.example.expensetracker.data.db.dao.ExpenseDao

/** Whether a captured payment was already saved: shared by the SMS and payment-app capture paths. */
object CaptureDuplicates {
    private val CAPTURE_SOURCES = setOf("SMS", "CC-SMS", "UPI")

    suspend fun isDuplicate(dao: ExpenseDao, amount: Double, source: String, payee: String, content: String, now: Long): Boolean {
        // Cross-source, within 3 minutes. Another capture of the same amount is the same payment:
        // a UPI notification and the bank's SMS arrive within seconds and name the payee
        // differently. An expense added by hand (or an auto-logged bill) is only if it names the
        // same payee: a ₹149 YouTube added by hand used to swallow a ₹149 Crunchyroll SMS.
        val others = dao.getRecentByAmountFromOtherSource(amount, source, now - 3 * 60 * 1000L)
        if (others.any { it.source in CAPTURE_SOURCES || samePayee(it.description, payee) }) return true
        // Same-source: the message or notification fired twice (30-second window).
        if (dao.getRecentByAmountAndContentFromSameSource(amount, source, content, now - 30 * 1000L) > 0) return true
        return false
    }

    /**
     * Whether two descriptions name the same payee: every word of the shorter is a word of the
     * longer ("Netflix" and "NETFLIX ENTERTAINMENT"), ignoring case, punctuation and a payment
     * app's "GPay → " prefix. Whole words only, so "Uber" isn't "UBEREATS".
     */
    fun samePayee(a: String, b: String): Boolean {
        val wordsA = words(a)
        val wordsB = words(b)
        if (wordsA.isEmpty() || wordsB.isEmpty()) return false
        val (shorter, longer) = if (wordsA.size <= wordsB.size) wordsA to wordsB else wordsB to wordsA
        // "Ok" or "Pay" alone says too little to merge two payments on.
        if (shorter.joinToString("").length < 3) return false
        return longer.containsAll(shorter)
    }

    private fun words(description: String): List<String> =
        description.substringAfter("→").lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotEmpty() }
}
