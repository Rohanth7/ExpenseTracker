package com.example.expensetracker.data.export

import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.db.entity.Income
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Spreadsheet-ready CSV (RFC 4180, UTF-8 with a BOM so Excel shows ₹ and Hindi correctly).
 * The columns are fixed. Expenses and incomes (type = income, no category or payment method)
 * share one list, oldest first.
 */
object CsvExporter {
    const val BOM = "﻿"
    val HEADER = listOf("date", "time", "type", "amount", "description", "category", "subcategory", "payment_method", "tags", "source")

    fun expensesToCsv(expenses: List<Expense>, categories: List<Category>, zone: TimeZone = TimeZone.getDefault(), incomes: List<Income> = emptyList()): String =
        buildString { write(this, expenses, categories, zone, incomes) }

    /** Writes row by row, so a long history goes straight to the file without a copy in memory. */
    fun write(out: Appendable, expenses: List<Expense>, categories: List<Category>, zone: TimeZone = TimeZone.getDefault(), incomes: List<Income> = emptyList()) {
        val byId = categories.associateBy { it.id }
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }
        val time = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = zone }
        fun row(date: Long, type: String, amount: Double, texts: List<String>) {
            val fields = listOf(day.format(date), time.format(date), type, money(amount)) + texts.map { text(it) }
            out.append(fields.joinToString(",") { quote(it) }).append("\r\n")
        }
        out.append(BOM).append(HEADER.joinToString(",")).append("\r\n")
        // Both lists sorted, then merged by date (an expense first when the times are equal).
        val exps = expenses.sortedBy { it.date }
        val incs = incomes.sortedBy { it.date }
        var i = 0; var j = 0
        while (i < exps.size || j < incs.size) {
            if (j >= incs.size || (i < exps.size && exps[i].date <= incs[j].date)) {
                val e = exps[i++]
                val (category, subcategory) = names(e.categoryId, byId)
                row(e.date, "expense", e.amount, listOf(e.description, category, subcategory, e.paymentMethod, e.tags.trim(), e.source))
            } else {
                val n = incs[j++]
                row(n.date, "income", n.amount, listOf(n.description, "", "", "", n.tags.trim(), n.source))
            }
        }
    }

    /** (category, subcategory): a subcategory is shown under its parent; unknown or -1 is "Pending". */
    internal fun names(categoryId: Long, byId: Map<Long, Category>): Pair<String, String> {
        val c = byId[categoryId] ?: return "Pending" to ""
        val parent = c.parentId?.let { byId[it] }
        return if (parent != null) parent.name to c.name else c.name to ""
    }

    /** Two decimals; empty if the amount isn't a finite number (BigDecimal would throw). */
    internal fun money(amount: Double): String =
        if (amount.isFinite()) BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).toPlainString() else ""

    /**
     * Payee names come from bank SMS and notifications, which anyone can send. Excel and Sheets run
     * a cell starting with = + - @ (or a tab or CR) as a formula, so those get a leading apostrophe.
     */
    internal fun text(field: String): String =
        if (field.isNotEmpty() && field[0] in "=+-@\t\r") "'$field" else field

    internal fun quote(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + field.replace("\"", "\"\"") + "\"" else field
}
