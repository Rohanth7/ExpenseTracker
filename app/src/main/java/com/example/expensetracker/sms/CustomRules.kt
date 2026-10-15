package com.example.expensetracker.sms

/**
 * Message rules the user teaches: "messages like this one are a payment of <amount> to <payee>",
 * or "messages like this one aren't payments". They're checked before the built-in parser.
 *
 * A rule is a pattern built from one example: the bank's fixed wording must match, while numbers,
 * dates, month names and spacing may differ. The example itself isn't kept.
 */
data class ParsingRule(
    val id: String,
    val kind: Kind,
    val pattern: String,
    /** Regex group numbers; 0 when not used. */
    val amountGroup: Int = 0,
    val payeeGroup: Int = 0,
    /** The example with digits hidden and the marked parts named, for the rules list. */
    val label: String,
    val createdAt: Long
) {
    enum class Kind { PAYMENT, IGNORE }
}

sealed class RuleMatch {
    data class Payment(val amount: Double, val payee: String?) : RuleMatch()
    object Ignore : RuleMatch()
}

object CustomRules {
    private const val AMOUNT = """(\d[\d,]*(?:\.\d{1,2})?)"""
    private val NUMBER = Regex("""\d[\d,]*(?:\.\d{1,2})?""")
    private val MONTH = Regex("""(?i)^(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*""")

    fun match(rules: List<ParsingRule>, body: String): RuleMatch? {
        val text = body.trim()
        for (rule in rules) {
            val m = runCatching { Regex(rule.pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)) }
                .getOrNull()?.matchEntire(text) ?: continue
            return when (rule.kind) {
                ParsingRule.Kind.IGNORE -> RuleMatch.Ignore
                ParsingRule.Kind.PAYMENT -> {
                    val amount = m.groupValues.getOrNull(rule.amountGroup)?.replace(",", "")?.toDoubleOrNull()
                        ?.takeIf { it > 0 } ?: continue
                    val payee = if (rule.payeeGroup > 0) m.groupValues.getOrNull(rule.payeeGroup)?.trim()?.takeIf { it.isNotEmpty() } else null
                    RuleMatch.Payment(amount, payee)
                }
            }
        }
        return null
    }

    /** The number inside a tapped word, e.g. "500.00" in "Rs.500.00", as a range in [text]. */
    fun amountRangeIn(text: String, wordRange: IntRange): IntRange? {
        val m = NUMBER.find(text.substring(wordRange.first, wordRange.last + 1)) ?: return null
        return (wordRange.first + m.range.first)..(wordRange.first + m.range.last)
    }

    /** [wordRange] without trailing punctuation, so "SWIGGY." marks "SWIGGY". */
    fun trimPayee(text: String, wordRange: IntRange): IntRange {
        var end = wordRange.last
        while (end > wordRange.first && text[end] in ".,;:()") end--
        var start = wordRange.first
        while (start < end && text[start] in "(") start++
        return start..end
    }

    sealed class BuildResult {
        data class Ok(val rule: ParsingRule) : BuildResult()
        data class Failed(val reason: String) : BuildResult()
    }

    /**
     * Builds a rule from [example]. For a payment, [amount] is required and [payee] optional;
     * both are character ranges in [example]. Fails if the parts overlap, the amount isn't a
     * number, or the pattern doesn't read the example back the same way.
     */
    fun build(
        example: String,
        kind: ParsingRule.Kind,
        amount: IntRange? = null,
        payee: IntRange? = null,
        id: String,
        now: Long
    ): BuildResult {
        val text = example.trim()
        val shift = example.indexOf(text)
        val a = amount?.let { (it.first - shift)..(it.last - shift) }
        val p = payee?.let { (it.first - shift)..(it.last - shift) }
        if (text.isEmpty()) return BuildResult.Failed("The message is empty.")

        if (kind == ParsingRule.Kind.IGNORE) {
            val rule = ParsingRule(id, kind, "^" + generalize(text) + "$", label = label(text, null, null), createdAt = now)
            return if (match(listOf(rule), text) == RuleMatch.Ignore) BuildResult.Ok(rule)
            else BuildResult.Failed("Couldn't make a rule from this message.")
        }

        if (a == null) return BuildResult.Failed("Tap the amount first.")
        if (a.first < 0 || a.last >= text.length) return BuildResult.Failed("Tap the amount first.")
        val amountText = text.substring(a)
        val expected = amountText.replace(",", "").toDoubleOrNull()
        if (!NUMBER.matches(amountText) || expected == null || expected <= 0) return BuildResult.Failed("The amount must be a number.")
        if (p != null && (p.first < 0 || p.last >= text.length || p.first > p.last)) return BuildResult.Failed("Tap the payee again.")
        if (p != null && (p.first <= a.last && a.first <= p.last)) return BuildResult.Failed("The amount and payee can't overlap.")

        val parts = listOfNotNull(a to "amount", p?.let { it to "payee" }).sortedBy { it.first.first }
        val regex = StringBuilder("^")
        var pos = 0
        var amountGroup = 0
        var payeeGroup = 0
        parts.forEachIndexed { index, (range, name) ->
            regex.append(generalize(text.substring(pos, range.first)))
            if (name == "amount") {
                regex.append(AMOUNT); amountGroup = index + 1
            } else {
                // Lazy unless it's the very end of the message.
                regex.append(if (range.last == text.lastIndex) "(.+)" else "(.+?)"); payeeGroup = index + 1
            }
            pos = range.last + 1
            // Right after the payee, keep the separator but let the rest of that word vary: in
            // "UPI/DR/123/CROMA RETAIL/YESB" the payee's bank code changes with the payee.
            if (name == "payee" && pos < text.length && !text[pos].isWhitespace()) {
                regex.append(generalize(text[pos].toString()))
                pos++
                val wordEnd = (pos until text.length).firstOrNull { text[it].isWhitespace() } ?: text.length
                if (wordEnd > pos) { regex.append("""\S*"""); pos = wordEnd }
            }
        }
        regex.append(generalize(text.substring(pos))).append("$")

        val rule = ParsingRule(id, kind, regex.toString(), amountGroup, payeeGroup, label(text, a, p), now)
        val check = match(listOf(rule), text) as? RuleMatch.Payment
        val expectedPayee = p?.let { text.substring(it).trim() }
        return if (check != null && check.amount == expected && check.payee == expectedPayee) BuildResult.Ok(rule)
        else BuildResult.Failed("Couldn't make a rule from this message. Try marking a different part.")
    }

    /** Literal text → pattern: digits, month names and spacing may vary; everything else must match. */
    internal fun generalize(literal: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < literal.length) {
            val c = literal[i]
            when {
                c.isDigit() -> {
                    // A whole number, including separators: "20,511.75" and "1,18,062.25" are both
                    // one balance. A separator only counts when a digit follows it.
                    while (i < literal.length && (literal[i].isDigit() ||
                            (literal[i] in ",." && i + 1 < literal.length && literal[i + 1].isDigit()))) i++
                    out.append("""\d[\d,.]*"""); continue
                }
                c.isWhitespace() -> {
                    while (i < literal.length && literal[i].isWhitespace()) i++
                    out.append("""\s+"""); continue
                }
                c.isLetter() && (i == 0 || !literal[i - 1].isLetter()) -> {
                    val month = MONTH.find(literal.substring(i))
                    val wordEnd = (i until literal.length).firstOrNull { !literal[it].isLetter() } ?: literal.length
                    if (month != null && month.range.last + 1 + i == wordEnd) {
                        out.append("[a-z]{3,9}"); i = wordEnd; continue
                    }
                    out.append(Regex.escape(c.toString()).removePrefix("\\Q").removeSuffix("\\E"))
                }
                c in "\\^$.|?*+()[]{}" -> out.append('\\').append(c)
                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }

    private fun label(text: String, amount: IntRange?, payee: IntRange?): String {
        val marks = listOfNotNull(amount?.let { it to "<amount>" }, payee?.let { it to "<payee>" }).sortedByDescending { it.first.first }
        var s = text
        marks.forEach { (r, tag) -> s = s.replaceRange(r, tag) }
        s = s.replace(Regex("""\d+"""), "#").replace(Regex("""\s+"""), " ")
        return if (s.length > 90) s.take(89) + "…" else s
    }
}
