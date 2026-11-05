package com.example.expensetracker.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * The item a dialog is open for (the expense being edited, the bill to delete), kept across
 * rotation and Android closing the app. Only its id is saved; the item is looked up in [items],
 * so it's null when nothing is open, and once the item is gone (deleted elsewhere), which closes
 * the dialog rather than editing something that no longer exists.
 */
@Composable
fun <T : Any> rememberSaveableItem(items: List<T>, id: (T) -> Long): MutableState<T?> {
    val savedId = rememberSaveable { mutableStateOf<Long?>(null) }
    return object : MutableState<T?> {
        override var value: T?
            get() = savedId.value?.let { wanted -> items.firstOrNull { id(it) == wanted } }
            set(item) { savedId.value = item?.let(id) }
        override fun component1(): T? = value
        override fun component2(): (T?) -> Unit = { value = it }
    }
}
