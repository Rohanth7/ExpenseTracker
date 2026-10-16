package com.example.expensetracker.data.backup

import com.example.expensetracker.data.db.entity.Bill
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.db.entity.Loan
import com.example.expensetracker.data.db.entity.MerchantMapping
import com.example.expensetracker.data.db.entity.RecurringTemplate
import com.example.expensetracker.data.db.entity.SavingsGoal
import com.example.expensetracker.sms.ParsingRule
import com.google.gson.Gson
import com.google.gson.JsonParseException

/** Settings that belong to the user's data rather than to this phone. */
data class BackupSettings(
    val monthlyIncome: Double? = null,
    val budgetAlertsEnabled: Boolean? = null,
    val budgetAlertThreshold: Int? = null,
    val weekStartsOnMonday: Boolean? = null
)

/**
 * Backup file contents.
 *
 * Version 1 files (before this change) only have categories, expenses and recurring templates.
 * Every list is nullable: a missing section means "not in this backup", and restore leaves that
 * part of the phone's data alone instead of wiping it.
 */
data class BackupData(
    val version: Int = BackupCodec.CURRENT_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val categories: List<Category>? = null,
    val expenses: List<Expense>? = null,
    val recurringTemplates: List<RecurringTemplate>? = null,
    val bills: List<Bill>? = null,
    val loans: List<Loan>? = null,
    val savingsGoals: List<SavingsGoal>? = null,
    val merchantMappings: List<MerchantMapping>? = null,
    val settings: BackupSettings? = null,
    val parsingRules: List<ParsingRule>? = null
) {
    /** Sections of the phone's data that restoring this backup will NOT touch. */
    val missingSections: List<String>
        get() = buildList {
            if (bills == null) add("bills")
            if (loans == null) add("loans")
            if (savingsGoals == null) add("savings goals")
            if (merchantMappings == null) add("learned categories")
            if (parsingRules == null) add("message rules")
        }
}

sealed class ParseResult {
    data class Ok(val data: BackupData) : ParseResult()
    data class Invalid(val reason: String) : ParseResult()
}

object BackupCodec {
    const val CURRENT_VERSION = 2
    private val gson = Gson()

    /** Captured SMS/notification text never goes into a backup; parsed amount and payee do. */
    fun toJson(data: BackupData): String =
        gson.toJson(data.copy(expenses = data.expenses?.map { it.copy(rawSms = null) }))

    /**
     * Parses and checks a backup before anything on the phone is touched. Gson ignores Kotlin
     * nullability, so a hand-edited or truncated file can contain nulls in non-null fields; those
     * are caught here rather than crashing halfway through a restore.
     */
    fun parse(json: String): ParseResult {
        // A file that starts like a backup but won't parse was cut short (e.g. an interrupted copy).
        val notBackup = if (json.trimStart().startsWith("{") && json.contains("\"categories\""))
            "The backup file is damaged or incomplete." else "This file isn't a MyMoney backup."
        val data = try {
            gson.fromJson(json, BackupData::class.java)
        } catch (e: JsonParseException) {
            return ParseResult.Invalid(notBackup)
        } catch (e: RuntimeException) {
            return ParseResult.Invalid(notBackup)
        } ?: return ParseResult.Invalid("The backup file is empty.")

        if (data.version > CURRENT_VERSION) {
            return ParseResult.Invalid("This backup was made by a newer version of MyMoney. Update the app first.")
        }
        if (data.categories == null || data.expenses == null) {
            return ParseResult.Invalid("This file isn't a MyMoney backup.")
        }
        problem(data)?.let { return ParseResult.Invalid("The backup file is damaged: $it.") }
        return ParseResult.Ok(data)
    }

    private fun Double?.ok() = this != null && isFinite()

    // Gson can leave null in fields Kotlin says are non-null, so cast to Any? before checking.
    @Suppress("USELESS_CAST", "SENSELESS_COMPARISON")
    private fun problem(d: BackupData): String? {
        d.categories!!.forEachIndexed { i, c ->
            if ((c as Any?) == null || (c.name as String?) == null) return "category ${i + 1} has no name"
        }
        d.expenses!!.forEachIndexed { i, e ->
            if ((e as Any?) == null || !(e.amount as Double?).ok() || (e.description as String?) == null)
                return "expense ${i + 1} is incomplete"
        }
        d.recurringTemplates?.forEachIndexed { i, t ->
            if ((t as Any?) == null || (t.name as String?) == null || !(t.amount as Double?).ok()) return "recurring item ${i + 1} is incomplete"
        }
        d.bills?.forEachIndexed { i, b ->
            if ((b as Any?) == null || (b.name as String?) == null || !(b.amount as Double?).ok()) return "bill ${i + 1} is incomplete"
        }
        d.loans?.forEachIndexed { i, l ->
            if ((l as Any?) == null || (l.name as String?) == null || !(l.monthlyEmi as Double?).ok()) return "loan ${i + 1} is incomplete"
        }
        d.savingsGoals?.forEachIndexed { i, g ->
            if ((g as Any?) == null || (g.name as String?) == null || !(g.targetAmount as Double?).ok()) return "savings goal ${i + 1} is incomplete"
        }
        d.parsingRules?.forEachIndexed { i, r ->
            if ((r as Any?) == null || (r.pattern as String?) == null || (r.kind as ParsingRule.Kind?) == null ||
                runCatching { Regex(r.pattern) }.isFailure) return "message rule ${i + 1} is damaged"
        }
        d.merchantMappings?.forEachIndexed { i, m ->
            if ((m as Any?) == null || (m.merchantName as String?) == null) return "learned category ${i + 1} is incomplete"
        }
        return null
    }
}
