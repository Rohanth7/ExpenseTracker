package com.example.expensetracker.ui.util

import java.math.BigDecimal

// Shared by every monthly-limit field (Categories dialog, Settings → Category Budgets) so they
// pre-fill and parse identically.

/** Pre-fill text for an existing limit. Keeps decimals so saving without edits doesn't change the value. */
fun formatLimitInput(limit: Double): String =
    if (limit > 0) BigDecimal.valueOf(limit).stripTrailingZeros().toPlainString() else ""

/** Blank → 0.0 (no limit). Ignores ₹, commas and spaces. Returns null for anything else unparseable. */
fun parseLimitInput(text: String): Double? {
    val cleaned = text.replace("₹", "").replace(",", "").trim()
    if (cleaned.isEmpty()) return 0.0
    return cleaned.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
}
