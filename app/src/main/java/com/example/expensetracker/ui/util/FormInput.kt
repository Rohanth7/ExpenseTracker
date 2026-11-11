package com.example.expensetracker.ui.util

import java.text.BreakIterator

/**
 * For a one-character field (an icon) whose text was [old] and is now [typed]: [typed] if it's one
 * character as a person sees it, else the last character added, before or after [old], so typing
 * an emoji replaces the old one. Counted by grapheme, not UTF-16 unit: 🇮🇳 is 4 units and 👨‍👩‍👧 is
 * 8, but each is one character.
 */
fun oneCharacter(old: String, typed: String): String {
    val added = when {
        old.isEmpty() -> typed
        typed.startsWith(old) -> typed.removePrefix(old)
        typed.endsWith(old) -> typed.removeSuffix(old)
        else -> typed
    }
    val graphemes = BreakIterator.getCharacterInstance().apply { setText(added) }
    val end = graphemes.last()
    val start = graphemes.previous()
    return if (start == BreakIterator.DONE) typed else added.substring(start, end)
}

/** A whole number typed in a field (a day of the month, a tenure), or null if it isn't one in [range]. */
fun wholeNumberIn(text: String, range: IntRange): Int? = text.trim().toIntOrNull()?.takeIf { it in range }

/** Long enough for any real name, short enough for a chip or a picker row. */
const val MAX_CATEGORY_NAME_LENGTH = 40

/**
 * Why [name] can't be saved as a category name, or null if it can. [siblings] are the other names
 * under the same parent (top-level categories, or one category's subcategories): two that differ
 * only by case or outer spaces can't be told apart in a picker.
 */
fun categoryNameError(name: String, siblings: List<String>): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> "Enter a name"
        trimmed.length > MAX_CATEGORY_NAME_LENGTH -> "Use $MAX_CATEGORY_NAME_LENGTH characters or fewer"
        siblings.any { it.trim().equals(trimmed, ignoreCase = true) } -> "There's already one with this name"
        else -> null
    }
}
