package com.example.expensetracker.ui.util

import java.math.BigDecimal

// Shared by amount fields where 0 has a meaning (monthly limits, income, filters, bills and loan
// totals) so they pre-fill and parse identically.

/**
 * Pre-fill text for an existing limit, to the paisa (as saving stores it) so saving without edits
 * doesn't change it. Never three decimals: "1.125" reads as 1,125 on a comma-decimal phone.
 */
fun formatLimitInput(limit: Double): String =
    if (limit > 0) BigDecimal(AmountCalculator.forEditing(limit)).stripTrailingZeros().toPlainString() else ""

private val ZERO = Regex("""^₹?\s*0+([.,]0*)?$""")

/**
 * Blank or 0 → 0.0 (no limit). Anything else is read exactly like the amount field (₹, grouping,
 * the phone's decimal comma, even sums), so "1,5" is the same number as an expense or a limit.
 * Null if unreadable.
 */
fun parseLimitInput(text: String, decimalComma: Boolean = AmountCalculator.usesDecimalComma()): Double? {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || ZERO.matches(trimmed)) return 0.0
    return AmountCalculator.evaluate(trimmed, decimalComma)
}


/** Like [parseLimitInput], but nothing typed isn't an answer: for bills, loan totals and income, where 0 is allowed. */
fun parseAmountOrZero(text: String, decimalComma: Boolean = AmountCalculator.usesDecimalComma()): Double? =
    if (text.isBlank()) null else parseLimitInput(text, decimalComma)
