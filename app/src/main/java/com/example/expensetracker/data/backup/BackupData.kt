package com.example.expensetracker.data.backup

import com.example.expensetracker.data.db.entity.Bill
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.db.entity.Income
import com.example.expensetracker.data.db.entity.Loan
import com.example.expensetracker.data.db.entity.MerchantMapping
import com.example.expensetracker.data.db.entity.RecurringIncome
import com.example.expensetracker.data.db.entity.RecurringIncomeMonth
import com.example.expensetracker.data.db.entity.RecurringTemplate
import com.example.expensetracker.data.db.entity.SavingsGoal
import com.example.expensetracker.data.recurring.BillMonth
import com.example.expensetracker.sms.ParsingRule
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.StringReader

/** Settings that belong to the user's data rather than to this phone. */
data class BackupSettings(
    val monthlyIncome: Double? = null,
    val budgetAlertsEnabled: Boolean? = null,
    val budgetAlertThreshold: Int? = null,
    val weekStartsOnMonday: Boolean? = null,
    /** Day the budget month starts (1–28). Optional, so older v2 files still read; null leaves it unchanged. */
    val monthStartDay: Int? = null,
    /** Subscription price alerts on or off. Optional like [monthStartDay]: older files leave it unchanged. */
    val priceAlertsEnabled: Boolean? = null
)

/**
 * Backup file contents.
 *
 * Version 1 files (before this change) only have categories, expenses and recurring templates.
 * Version 3 adds income records and recurring income; restoring a v2 file leaves both alone.
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
    val parsingRules: List<ParsingRule>? = null,
    val incomes: List<Income>? = null,
    val recurringIncomes: List<RecurringIncome>? = null,
    /** Months RecurringManager has handled, so a salary deleted on purpose stays deleted (restored with [recurringIncomes]). */
    val recurringIncomeMonths: List<RecurringIncomeMonth>? = null,
    /**
     * Months auto-log bills were handled in, so a bill's expense deleted on purpose stays deleted
     * (restored with [bills]). Optional: a file without it restores as "no month handled".
     */
    val billMonths: List<BillMonth>? = null
) {
    /** Sections of the phone's data that restoring this backup will NOT touch. */
    val missingSections: List<String>
        get() = buildList {
            if (bills == null) add("bills")
            if (loans == null) add("loans")
            if (savingsGoals == null) add("savings goals")
            if (merchantMappings == null) add("learned categories")
            if (parsingRules == null) add("message rules")
            if (incomes == null) add("income records")
            if (recurringIncomes == null) add("recurring income")
        }

    /**
     * Anything the user entered (starter categories, learned categories and settings don't count).
     * A reset phone's snapshot has none.
     */
    val hasRecords: Boolean
        get() = listOf(expenses, incomes, bills, loans, savingsGoals, recurringIncomes, recurringTemplates).any { !it.isNullOrEmpty() }
}

sealed class ParseResult {
    data class Ok(val data: BackupData) : ParseResult()
    data class Invalid(val reason: String) : ParseResult()
}

object BackupCodec {
    const val CURRENT_VERSION = 3
    private val gson = GsonBuilder().registerTypeAdapterFactory(EntityDefaults).create()

    /**
     * Whether an automatic backup may replace the backup [existing] (a file's text, null if there
     * is none) with [next]: never one with records by one without, as after "Reset everything".
     */
    fun mayReplace(existing: String?, next: BackupData): Boolean {
        if (next.hasRecords || existing == null) return true
        val old = parse(existing) as? ParseResult.Ok ?: return true
        return !old.data.hasRecords
    }

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
    private fun Double?.atLeastZero() = ok() && this!! >= 0
    private val COLOUR = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")

    // Gson can leave null in fields Kotlin says are non-null, so cast to Any? before checking.
    @Suppress("USELESS_CAST", "SENSELESS_COMPARISON")
    private fun problem(d: BackupData): String? {
        // Ranges are the ones the app's own screens allow, so a file the app wrote always passes.
        d.categories!!.forEachIndexed { i, c ->
            if ((c as Any?) == null || (c.name as String?) == null) return "category ${i + 1} has no name"
            if (!c.monthlyLimit.atLeastZero() || !COLOUR.matches(c.colorHex)) return "category ${i + 1} has an invalid limit or colour"
        }
        d.expenses!!.forEachIndexed { i, e ->
            if ((e as Any?) == null || !(e.amount as Double?).ok() || (e.description as String?) == null)
                return "expense ${i + 1} is incomplete"
            if (e.date <= 0) return "expense ${i + 1} has no date"
        }
        d.recurringTemplates?.forEachIndexed { i, t ->
            if ((t as Any?) == null || (t.name as String?) == null || !(t.amount as Double?).ok()) return "recurring item ${i + 1} is incomplete"
        }
        d.bills?.forEachIndexed { i, b ->
            // 0 passes: bills could be saved with it before, and those files must still restore.
            if ((b as Any?) == null || (b.name as String?) == null || !(b.amount as Double?).atLeastZero()) return "bill ${i + 1} is incomplete"
            if (b.dueDayOfMonth !in 1..31 || b.reminderDays !in 0..14) return "bill ${i + 1} has an invalid due day or reminder"
        }
        d.loans?.forEachIndexed { i, l ->
            if ((l as Any?) == null || (l.name as String?) == null || !(l.monthlyEmi as Double?).ok() || !(l.totalAmount as Double?).ok()) return "loan ${i + 1} is incomplete"
            if (l.tenureMonths < 1 || l.dueDayOfMonth !in 1..31) return "loan ${i + 1} has an invalid tenure or due day"
            if (l.startDate <= 0) return "loan ${i + 1} has no start date"
        }
        d.savingsGoals?.forEachIndexed { i, g ->
            if ((g as Any?) == null || (g.name as String?) == null || !(g.targetAmount as Double?).ok()) return "savings goal ${i + 1} is incomplete"
            if (g.targetAmount <= 0 || !COLOUR.matches(g.colorHex)) return "savings goal ${i + 1} has an invalid target or colour"
        }
        d.parsingRules?.forEachIndexed { i, r ->
            if ((r as Any?) == null || (r.pattern as String?) == null || (r.kind as ParsingRule.Kind?) == null ||
                runCatching { Regex(r.pattern) }.isFailure) return "message rule ${i + 1} is damaged"
        }
        d.incomes?.forEachIndexed { i, n ->
            if ((n as Any?) == null || !(n.amount as Double?).ok() || (n.description as String?) == null ||
                (n.source as String?) == null || (n.tags as String?) == null) return "income ${i + 1} is incomplete"
            if (n.date <= 0) return "income ${i + 1} has no date"
        }
        d.recurringIncomes?.forEachIndexed { i, r ->
            if ((r as Any?) == null || (r.name as String?) == null || !(r.amount as Double?).ok() || r.dayOfMonth !in 1..31)
                return "recurring income ${i + 1} is incomplete"
        }
        d.recurringIncomeMonths?.forEachIndexed { i, m ->
            if ((m as Any?) == null || m.month !in 1..12) return "recurring income month ${i + 1} is incomplete"
        }
        d.billMonths?.forEachIndexed { i, m ->
            if ((m as Any?) == null || m.month !in 1..12) return "bill month ${i + 1} is incomplete"
        }
        d.merchantMappings?.forEachIndexed { i, m ->
            if ((m as Any?) == null || (m.merchantName as String?) == null) return "learned category ${i + 1} is incomplete"
        }
        d.settings?.let { s ->
            if (s.budgetAlertThreshold != null && s.budgetAlertThreshold !in 50..95) return "the budget alert threshold is out of range"
            if (s.monthlyIncome != null && !s.monthlyIncome.atLeastZero()) return "the expected income is invalid"
        }
        return null
    }
}

/**
 * Gson builds entities without calling their constructors, so a field missing from an older or
 * hand-made file would be null (or 0), not the entity's default. This fills those in from a
 * default instance. Fields without a default (a name, an amount, a date) stay missing, for
 * [BackupCodec.parse] to refuse.
 */
private object EntityDefaults : TypeAdapterFactory {
    private class Defaults(val instance: Any, vararg val required: String)

    private val defaults: Map<Class<*>, Defaults> = mapOf(
        Category::class.java to Defaults(Category(name = ""), "name"),
        Expense::class.java to Defaults(Expense(amount = 0.0, description = "", date = 0), "amount", "description", "date"),
        RecurringTemplate::class.java to Defaults(RecurringTemplate(name = "", amount = 0.0), "name", "amount"),
        Bill::class.java to Defaults(Bill(name = "", amount = 0.0, dueDayOfMonth = 0), "name", "amount", "dueDayOfMonth"),
        Loan::class.java to Defaults(Loan(name = "", totalAmount = 0.0, monthlyEmi = 0.0, tenureMonths = 0, startDate = 0),
            "name", "totalAmount", "monthlyEmi", "tenureMonths", "startDate"),
        SavingsGoal::class.java to Defaults(SavingsGoal(name = "", targetAmount = 0.0), "name", "targetAmount"),
        Income::class.java to Defaults(Income(amount = 0.0, description = "", date = 0), "amount", "description", "date"),
        RecurringIncome::class.java to Defaults(RecurringIncome(name = "", amount = 0.0, dayOfMonth = 0), "name", "amount", "dayOfMonth")
    )

    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        val d = defaults[type.rawType] ?: return null
        val delegate = gson.getDelegateAdapter(this, type)
        val elements = gson.getAdapter(JsonElement::class.java)
        @Suppress("UNCHECKED_CAST")
        val filled = delegate.toJsonTree(d.instance as T).asJsonObject
        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter, value: T) = delegate.write(out, value)
            override fun read(reader: JsonReader): T? {
                val json = elements.read(reader)
                if (json.isJsonObject) {
                    val o = json.asJsonObject
                    filled.entrySet().forEach { (key, value) ->
                        if (o.get(key)?.isJsonNull == false) return@forEach
                        if (key !in d.required) o.add(key, value)
                        // A missing amount would be read as 0.0 (a primitive can't be null), which
                        // passes the checks; NaN doesn't. Missing whole numbers read as 0, which
                        // the range checks refuse.
                        else if ((value as? JsonPrimitive)?.takeIf { it.isNumber }?.asNumber is Double) o.add(key, JsonPrimitive(Double.NaN))
                    }
                }
                // Read leniently like the file itself, so a NaN amount reaches the checks.
                return delegate.read(JsonReader(StringReader(json.toString())).apply { isLenient = true })
            }
        }
    }
}
