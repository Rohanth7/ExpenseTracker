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

    // "Received" alone isn't a credit: "Ramesh received your money" is a payment. These say the
    // money came to the user.
    private val CREDIT_KEYWORDS = listOf(
        "credited", "added", "refund", "cashback",
        "you received", "you've received", "you have received", "sent you", "paid you", "paid to you",
        "to your account", "to your wallet", "money received", "amount received",
        "transfer received", "credit received"
    )
    private val RECEIVED_FROM = Regex("""\breceived\s+(?:(?:Rs\.?|INR|₹)\s*[\d,]+(?:\.\d{1,2})?\s+)?from\b""", RegexOption.IGNORE_CASE)

    // A payment that went through: "Paid ₹250 to Swiggy", "₹500 paid to Ramesh", "Sent ₹99 to X".
    // Checked first, so a reward line ("You won a scratch card!") or an app tip ("Pay bills before
    // the due date") under it doesn't drop it. Never "… to you".
    private val PAID_TO = Regex(
        """(?:\b(?:paid|sent)\s+(?:Rs\.?|INR|₹)\s*[\d,]+(?:\.\d{1,2})?|(?:Rs\.?|INR|₹)\s*[\d,]+(?:\.\d{1,2})?\s+(?:paid|sent))\s+to\s+(?!you\b|your\b)""",
        RegexOption.IGNORE_CASE
    )
    private val COMPLETED_KEYWORDS = listOf("payment successful", "payment done", "payment complete", "successfully paid", "paid successfully")

    // Never a completed payment, whatever else the notification says (the payee's own name aside).
    private val NOT_COMPLETED = listOf(
        "will be debited", "will be deducted", "scheduled", "failed", "declined", "unsuccessful",
        "pending", "processing"
    )

    // Where a payee's name after "paid ₹X to" ends: its sentence, a dash, or a word no name has.
    private val PAYEE_END = Regex(
        """[.!?,;:\n]|\s[-–—|]\s|\s(?:is|was|has|have|will|via|using|from|on|at|for|but|and)\s""", RegexOption.IGNORE_CASE
    )

    /**
     * [part] (the title, or one line of the text) without the names it says were paid: "Paid ₹200
     * to Pending Dues Store" is a completed payment to a shop called that, not a pending one.
     */
    private fun withoutPayees(part: String): String {
        val kept = StringBuilder()
        var from = 0
        PAID_TO.findAll(part).forEach { paidTo ->
            val nameStart = paidTo.range.last + 1
            if (nameStart < from) return@forEach
            kept.append(part, from, nameStart)
            from = PAYEE_END.find(part, nameStart)?.range?.first ?: part.length
            // A status word that ends the name isn't part of it: "Paid ₹500 to Ramesh Kumar
            // failed" failed. Inside a name ("Pending Dues Store") it has more of the name after it.
            val name = part.substring(nameStart, from).trimEnd()
            val lastWord = name.substringAfterLast(' ')
            if (TextRules.containsAnyWord(lastWord, NOT_COMPLETED)) from = nameStart + name.length - lastWord.length
        }
        return kept.append(part, from, part.length).toString()
    }

    // Reminders, offers and requests; ignored only when the notification isn't a completed payment.
    private val EXCLUDE_KEYWORDS = listOf(
        "is due", "due date", "due on", "due by", "bill due", "overdue",
        "payment due", "payment reminder", "upcoming payment", "upcoming bill",
        "reminder", "pay now", "pay before", "last date", "due amount",
        "promo", "discount", "win", "won",
        "expiring", "expires soon",
        "requested", "request", "collect request", "approve",
        "otp", "verification code", "mandate", "autopay set up", "autopay setup"
    )

    // WhatsApp is deliberately not monitored: chat messages like "I paid ₹500" were logged as
    // expenses, and reading personal chats is a privacy problem.
    fun isMonitored(packageName: String) = packageName in UPI_PACKAGES || packageName in BANK_PACKAGES

    fun parse(title: String, body: String, packageName: String): ParsedTransaction? {
        val fullText = "$title $body"
        // Title and lines apart, so a name ends where its line does.
        if ((body.lines() + title).any { TextRules.containsAnyWord(withoutPayees(it), NOT_COMPLETED) }) return null

        val paidTo = PAID_TO.containsMatchIn(fullText)
        val completed = paidTo || TextRules.containsAnyWord(fullText, COMPLETED_KEYWORDS)
        // Reject bill reminders, offers, and promotions
        if (!completed && TextRules.containsAnyWord(fullText, EXCLUDE_KEYWORDS)) return null

        // Must have a confirmed debit keyword — amount alone is not enough
        val isDebit = TextRules.containsAnyWord(fullText, DEBIT_KEYWORDS)
        if (!isDebit) return null

        // Skip credits. "Paid ₹250 to Swiggy … ₹10 cashback" is still the ₹250 payment.
        val isCredit = TextRules.containsAnyWord(fullText, CREDIT_KEYWORDS) || RECEIVED_FROM.containsMatchIn(fullText)
        if (isCredit && !paidTo) return null

        val amount = TextRules.extractAmount(fullText, TextRules.AMOUNT_PATTERNS) ?: return null
        val description = describe(packageName, title, merchant(title, body), body)
        return ParsedTransaction(amount, description)
    }

    /**
     * The payee, from a line that says who was paid first ("₹500 paid to Ramesh" over "Ramesh
     * received your money", "Paid ₹430 to Dominos" over "Tap to view details"), then the text
     * before the title as before.
     */
    private fun merchant(title: String, body: String): String? {
        val lines = (body.lines() + title).filter { it.isNotBlank() }.sortedByDescending { PAID_TO.containsMatchIn(it) }
        return lines.firstNotNullOfOrNull { MerchantExtractor.extract(it) }
    }

    /** "GPay → Swiggy": the app's name, then [payee] if known (also for a message rule's payee). */
    fun describe(packageName: String, title: String, payee: String?, body: String = ""): String {
        val appName = when (packageName) {
            "com.google.android.apps.nbu.paisa.user" -> "GPay"
            "com.phonepe.app" -> "PhonePe"
            "net.one97.paytm" -> "Paytm"
            "in.org.npci.upiapp" -> "BHIM"
            "in.amazon.mShop.android.shopping" -> "Amazon Pay"
            "com.dreamplug.androidapp" -> "CRED"
            else -> title.ifBlank { "UPI" }
        }

        if (payee != null) return "$appName → $payee"

        return if (body.isNotBlank()) "$appName: ${body.take(50)}" else appName
    }
}
