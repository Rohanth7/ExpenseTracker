package com.example.expensetracker.ui.screens.expenses

import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.data.repository.IncomeRepository
import kotlinx.coroutines.flow.first

data class TagSummary(val name: String, val count: Int, val totalSpent: Double)

/**
 * Tags are stored as one comma-separated string per expense or income ("goa,food"). A leading "#"
 * isn't part of the name, case doesn't matter, and a record has each tag at most once.
 */
object Tags {
    fun parse(raw: String): List<String> =
        raw.split(",").map { it.trim().trimStart('#').trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }

    /**
     * [raw] as it's stored: names trimmed, without their "#", each once (the first spelling wins).
     * The repositories save tags through this, so every screen stores them the same way; tags
     * stored before that read the same through [parse].
     */
    fun normalize(raw: String): String = parse(raw).joinToString(",")

    /** Whether a search for [query] finds one of [raw]'s tags. "#food" finds the tag "food". */
    fun matchesQuery(raw: String, query: String): Boolean {
        val q = query.trim().trimStart('#').trim()
        return q.isNotEmpty() && parse(raw).any { it.contains(q, ignoreCase = true) }
    }

    fun same(a: String?, b: String?): Boolean = a != null && b != null && a.equals(b, ignoreCase = true)

    /** A name the user typed for a tag, or null if it's blank or has a comma (it would become two tags). */
    fun cleanName(name: String): String? = name.trim().trimStart('#').trim().takeIf { it.isNotEmpty() && ',' !in it }

    /** [raw] plus [tags] it doesn't have yet. Commas in them (a loan's name) become spaces. */
    fun add(raw: String, vararg tags: String): String =
        join((parse(raw) + tags.map { it.replace(',', ' ').replace(Regex("\\s+"), " ") }).joinToString(","))

    /** [raw] with [old] renamed to [new]; if it already had [new], it keeps one. */
    fun rename(raw: String, old: String, new: String): String =
        join(parse(raw).joinToString(",") { if (it.equals(old, ignoreCase = true)) new else it })

    fun remove(raw: String, tag: String): String = parse(raw).filterNot { it.equals(tag, ignoreCase = true) }.joinToString(",")

    private fun has(raw: String, tag: String) = parse(raw).any { it.equals(tag, ignoreCase = true) }
    private fun join(raw: String) = parse(raw).joinToString(",")

    /** Spending per tag, most first. "Food" and "food" are one tag, shown as first seen. */
    fun summaries(expenses: List<Expense>): List<TagSummary> {
        val byTag = linkedMapOf<String, TagSummary>()
        expenses.forEach { expense ->
            parse(expense.tags).forEach { tag ->
                val s = byTag[tag.lowercase()] ?: TagSummary(tag, 0, 0.0)
                byTag[tag.lowercase()] = s.copy(count = s.count + 1, totalSpent = s.totalSpent + expense.amount)
            }
        }
        return byTag.values.sortedByDescending { it.totalSpent }
    }

    /** Renames a tag on every expense and income. Does nothing for a name [cleanName] refuses. */
    suspend fun renameEverywhere(expenses: ExpenseRepository, incomes: IncomeRepository, old: String, new: String) {
        val name = cleanName(new) ?: return
        expenses.allExpenses.first().filter { has(it.tags, old) }.forEach { expenses.update(it.copy(tags = rename(it.tags, old, name))) }
        incomes.allIncomes.first().filter { has(it.tags, old) }.forEach { incomes.update(it.copy(tags = rename(it.tags, old, name))) }
    }

    suspend fun removeEverywhere(expenses: ExpenseRepository, incomes: IncomeRepository, tag: String) {
        expenses.allExpenses.first().filter { has(it.tags, tag) }.forEach { expenses.update(it.copy(tags = remove(it.tags, tag))) }
        incomes.allIncomes.first().filter { has(it.tags, tag) }.forEach { incomes.update(it.copy(tags = remove(it.tags, tag))) }
    }
}
