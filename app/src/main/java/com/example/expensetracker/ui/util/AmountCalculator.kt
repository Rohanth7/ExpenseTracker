package com.example.expensetracker.ui.util

/**
 * Evaluates simple amount expressions typed into the amount field ("120+45", "1,200 - 50", "3×40").
 * A tiny recursive-descent parser: no eval, no scripting engine. + − × ÷, normal precedence.
 */
object AmountCalculator {
    private const val OPERATORS = "+-*/×÷−xX"

    /** ₹1 lakh crore. Anything above is a typo, and it keeps rounding clear of Long overflow. */
    private const val MAX_AMOUNT = 1e12

    /** "1,200", "1,20,000" (Indian) or "1,234,567" (Western), optionally with decimals. */
    private val GROUPED = Regex("""^(\d{1,3}(,\d{3})+|\d{1,2}(,\d{2})*,\d{3})(\.\d+)?$""")

    /** "12,50" or "99,9": a comma typed as the decimal point (comma-decimal keyboards and locales). */
    private val DECIMAL_COMMA = Regex("""^\d+,\d{1,2}$""")

    /** "1.234,50": dots grouping thousands, then a decimal comma (comma-decimal phones). */
    private val DOT_GROUPED_DECIMAL_COMMA = Regex("""^\d{1,3}(\.\d{3})+,\d+$""")

    /** True where the phone's language writes 1,5 for one and a half (German, French, Indonesian…). */
    fun usesDecimalComma(locale: java.util.Locale = java.util.Locale.getDefault()): Boolean =
        java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator == ','

    /** Show "= ₹…" for sums, and whenever a comma was typed, so a comma read the wrong way is visible. */
    fun needsPreview(text: String): Boolean = isExpression(text) || ',' in text

    /**
     * Positive, finite result rounded to 2 decimals, or null if the text isn't a valid amount.
     * On a comma-decimal phone ([decimalComma]) a comma is the decimal point; elsewhere it groups thousands.
     */
    fun evaluate(text: String, decimalComma: Boolean = usesDecimalComma()): Double? {
        val s = text.replace("₹", "").replace(" ", "")
            .replace('×', '*').replace('x', '*').replace('X', '*').replace('÷', '/').replace('−', '-')
        if (s.isEmpty()) return null
        val parser = Parser(s, decimalComma)
        val value = parser.expression() ?: return null
        if (!parser.atEnd() || !value.isFinite() || value > MAX_AMOUNT) return null
        // Round before the zero check, so "0.004" is refused rather than saved as ₹0.
        val rounded = Math.round(value * 100) / 100.0
        return if (rounded > 0) rounded else null
    }

    /** The amount as the edit dialog shows it: "180.00", never "180,00" whatever the phone's language. */
    fun forEditing(amount: Double): String =
        java.math.BigDecimal.valueOf(amount).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()

    /** A number as typed, with its commas resolved; null if the commas make it ambiguous. */
    private fun parseNumber(token: String, decimalComma: Boolean): Double? = when {
        ',' !in token -> token.toDoubleOrNull()
        decimalComma -> when {
            '.' in token -> if (DOT_GROUPED_DECIMAL_COMMA.matches(token)) token.replace(".", "").replace(',', '.').toDoubleOrNull() else null
            token.count { it == ',' } == 1 && !token.endsWith(',') && !token.startsWith(',') -> token.replace(',', '.').toDoubleOrNull()
            else -> null
        }
        GROUPED.matches(token) -> token.replace(",", "").toDoubleOrNull()
        DECIMAL_COMMA.matches(token) -> token.replace(',', '.').toDoubleOrNull()
        else -> null
    }

    /** True when the text contains an operator, so the UI can show "= ₹165" under the field. */
    fun isExpression(text: String): Boolean = text.trim().drop(1).any { it in OPERATORS }

    /**
     * Puts [symbol] where the cursor is (replacing any selection) and returns the new text and the
     * cursor position just after the symbol, so the next digit typed lands after it.
     */
    fun insertAt(text: String, selectionStart: Int, selectionEnd: Int, symbol: String): Pair<String, Int> {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        return text.replaceRange(start, end, symbol) to start + symbol.length
    }

    private class Parser(private val s: String, private val decimalComma: Boolean) {
        private var i = 0
        fun atEnd() = i == s.length

        fun expression(): Double? {
            var left = term() ?: return null
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]
                val right = term() ?: return null
                left = if (op == '+') left + right else left - right
            }
            return left
        }

        private fun term(): Double? {
            var left = number() ?: return null
            while (i < s.length && (s[i] == '*' || s[i] == '/')) {
                val op = s[i++]
                val right = number() ?: return null
                if (op == '/' && right == 0.0) return null
                left = if (op == '*') left * right else left / right
            }
            return left
        }

        private fun number(): Double? {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == ',')) i++
            return parseNumber(s.substring(start, i), decimalComma)
        }
    }
}
