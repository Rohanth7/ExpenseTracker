package com.example.expensetracker.data.budget

import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense

/**
 * One monthly limit and what has been spent against it. [parent] is set for a subcategory's own
 * limit, which counts that subcategory only; a top-level category's counts its subcategories too.
 */
data class Budget(val category: Category, val parent: Category?, val spent: Double) {
    val limit: Double get() = category.monthlyLimit

    /** "Food", or "Food › Coffee" for a subcategory. */
    val name: String get() = Budgets.name(category, parent)
}

/** Which spending counts against which limit: the one rule for alerts, the Overview and its notification centre. */
object Budgets {
    fun name(category: Category, parent: Category?): String =
        if (parent != null) "${parent.name} › ${category.name}" else category.name

    /** The categories whose expenses count against [category]'s limit. */
    fun countedIds(category: Category, categories: List<Category>): List<Long> =
        if (category.parentId != null) listOf(category.id)
        else listOf(category.id) + categories.filter { it.parentId == category.id }.map { it.id }

    /** The limits an expense filed under [categoryId] counts against: the category's own, then its parent's. */
    fun limitsFor(categoryId: Long, categories: List<Category>): List<Category> {
        val category = categories.find { it.id == categoryId } ?: return emptyList()
        val parent = category.parentId?.let { id -> categories.find { it.id == id } }
        return listOfNotNull(category, parent).filter { it.monthlyLimit > 0 }
    }

    /**
     * Every limit that is set, with [expenses] (one budget month's) counted against it. Top-level
     * categories by spending, most first, each followed by its subcategories that have a limit of
     * their own.
     */
    fun of(categories: List<Category>, expenses: List<Expense>): List<Budget> {
        val spentBy = expenses.groupBy { it.categoryId }.mapValues { (_, list) -> list.sumOf { it.amount } }
        fun spent(category: Category) = countedIds(category, categories).sumOf { spentBy[it] ?: 0.0 }
        return categories.filter { it.parentId == null }
            .map { it to spent(it) }
            .sortedByDescending { it.second }
            .flatMap { (top, topSpent) ->
                val own = if (top.monthlyLimit > 0) listOf(Budget(top, null, topSpent)) else emptyList()
                own + categories.filter { it.parentId == top.id && it.monthlyLimit > 0 }
                    .map { Budget(it, top, spent(it)) }
                    .sortedByDescending { it.spent }
            }
    }
}
