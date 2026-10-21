package com.example.expensetracker.ui.util

/**
 * Evaluates simple amount expressions typed into the amount field ("120+45", "1,200 - 50", "3×40").
 * A tiny recursive-descent parser: no eval, no scripting engine. + − × ÷, normal precedence.
 */
object AmountCalculator {
    private const val OPERATORS = "+-*/×÷−xX"

    /** Positive, finite result rounded to 2 decimals, or null if the text isn't a valid amount. */
    fun evaluate(text: String): Double? {
        val s = text.replace("₹", "").replace(",", "").replace(" ", "")
            .replace('×', '*').replace('x', '*').replace('X', '*').replace('÷', '/').replace('−', '-')
        if (s.isEmpty()) return null
        val parser = Parser(s)
        val value = parser.expression() ?: return null
        if (!parser.atEnd() || !value.isFinite() || value <= 0) return null
        return Math.round(value * 100) / 100.0
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

    private class Parser(private val s: String) {
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
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            return s.substring(start, i).toDoubleOrNull()
        }
    }
}
