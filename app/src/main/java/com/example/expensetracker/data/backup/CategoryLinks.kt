package com.example.expensetracker.data.backup

import com.example.expensetracker.data.db.entity.Category

/**
 * Where a category id from before a restore points afterwards. A restore replaces the categories
 * with the file's, ids and all, so an id kept by something the file doesn't have (the phone's
 * bills and learned merchants, with a v1 or v2 file) may now be another category. The same
 * category is the one with the same name under the same parent, whatever its case.
 */
internal class CategoryLinks(before: List<Category>, after: List<Category>) {
    private fun keys(categories: List<Category>): Map<Long, String> {
        val names = categories.associate { it.id to it.name.trim().lowercase() }
        return categories.associate { it.id to "${it.parentId?.let(names::get).orEmpty()}/${names.getValue(it.id)}" }
    }

    private val keyBefore = keys(before)
    // Two with one name: the first, as a picker would list it
    private val idAfter = keys(after).entries.groupBy({ it.value }, { it.key }).mapValues { it.value.min() }

    /** The restored category that [oldId] meant, or null if the file has none like it. */
    fun moved(oldId: Long): Long? = keyBefore[oldId]?.let(idAfter::get)
}
