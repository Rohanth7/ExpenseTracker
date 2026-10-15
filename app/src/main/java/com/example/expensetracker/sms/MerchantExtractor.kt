package com.example.expensetracker.sms

/**
 * Pulls the payee out of a bank SMS or payment-app notification.
 *
 * Bank-specific formats are tried first, then a UPI ID anywhere in the text, then a generic
 * "to/at <name>" match. Every pattern needs a whole word before the name, so "Customer" no longer
 * yields "mer" the way the old "(?:to|at)..." regex did.
 */
object MerchantExtractor {
    private val IC = RegexOption.IGNORE_CASE

    // Words that end a merchant name: dates, references, balances and footer text.
    private const val STOP =
        """(?=\s+(?:on|ref|refno|ref\s*no|upi|via|using|avl|avbl|bal|balance|not|dated|date|txn|from|info|value)\b|\s*[.;,(\n\r]|\s*-\s|\s*$)"""

    private val PATTERNS: List<Regex> = listOf(
        // Axis: "UPI/P2M/123456789012/SWIGGY"
        Regex("""\bUPI/P2[AM]/\d+/([^/\n\r]{2,40})""", IC),
        // ICICI UPI: "...debited for Rs 500.00 on 12-Oct-24; SWIGGY credited."
        Regex(""";\s*([^;\n\r]{2,40}?)\s+credited\b""", IC),
        // SBI UPI: "trf to SWIGGY Refno 123..."
        Regex("""\btrf\s+to\s+(.{2,40}?)$STOP""", IC),
        // HDFC UPI multi-line: "To SWIGGY" on its own line
        Regex("""(?:^|[\n\r])\s*To\s*:?\s+([^\n\r]{2,40}?)\s*(?=[\n\r]|$)""", IC),
        // ICICI card: "spent using ICICI Bank Card XX1234 on 12-Oct-24 on AMAZON."
        Regex("""\bon\s+\d{1,2}[-/ ]\w{3}[-/ ]\d{2,4}\s+(?:on|at)\s+(.{2,40}?)$STOP""", IC),
        // UPI ID anywhere: "to VPA swiggy@icici"
        Regex("""\b([a-z0-9][a-z0-9.\-_]{1,48}@[a-z][a-z0-9]{1,20})\b""", IC),
        // Generic: "paid to X", "sent to X", "at X", "towards X"
        Regex("""\b(?:paid\s+to|sent\s+to|transferred\s+to|to|at|towards|merchant\s*:?)\s+(?:VPA\s+)?(.{2,40}?)$STOP""", IC)
    )

    // Candidates that are clearly not a payee.
    private val REJECT_WORDS = setOf(
        "you", "your", "the", "a/c", "ac", "acct", "account", "card", "bank", "report", "dispute",
        "block", "call", "us", "sms", "this", "it", "be", "rs", "inr", "upi", "avoid", "customer"
    )
    private val REJECT_PREFIX = Regex("""^(?:your|rs\.?|inr|₹|a/c|ac\b|acct|account|card|\d)""", IC)

    fun extract(text: String): String? {
        for (pattern in PATTERNS) {
            for (match in pattern.findAll(text)) {
                val candidate = clean(match.groupValues[1]) ?: continue
                return candidate
            }
        }
        return null
    }

    private fun clean(raw: String): String? {
        val name = raw.replace(Regex("""\s+"""), " ").trim().trimEnd('.', ',', ';', ':', '-', ')').trim()
        if (name.length < 2) return null
        if (name.lowercase() in REJECT_WORDS) return null
        // UPI IDs can start with a digit (phone-number VPAs); other names can't.
        if (!name.contains('@') && REJECT_PREFIX.containsMatchIn(name)) return null
        if (!name.any { it.isLetter() }) return null
        return name.take(40)
    }

    private val BANK_SENDERS = linkedMapOf(
        "hdfc" to "HDFC Bank", "sbi" to "SBI", "icici" to "ICICI Bank", "axis" to "Axis Bank",
        "kotak" to "Kotak Bank", "yesbnk" to "Yes Bank", "pnb" to "PNB", "bob" to "Bank of Baroda",
        "idfc" to "IDFC First Bank", "indbnk" to "Indian Bank", "scbank" to "Standard Chartered"
    )

    /** Description used when no payee can be found, e.g. "HDFC Bank debit". */
    fun fallbackDescription(sender: String): String {
        val lower = sender.lowercase()
        val bank = BANK_SENDERS.entries.firstOrNull { lower.contains(it.key) }?.value
        return if (bank != null) "$bank debit" else "Bank debit"
    }
}
