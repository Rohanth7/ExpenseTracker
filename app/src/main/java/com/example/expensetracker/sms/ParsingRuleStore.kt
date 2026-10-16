package com.example.expensetracker.sms

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user's message rules, kept as a small JSON list in app settings (no database table, so
 * no schema migration). Cached in memory: the SMS receiver reads it on every message.
 */
class ParsingRuleStore(context: Context, fileName: String = "parsing_rules") {
    private val prefs = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val state = MutableStateFlow(load())

    val rules: StateFlow<List<ParsingRule>> = state.asStateFlow()

    private fun load(): List<ParsingRule> = runCatching {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        gson.fromJson<List<ParsingRule>>(json, object : TypeToken<List<ParsingRule>>() {}.type).orEmpty()
            // Gson ignores Kotlin nullability; drop anything damaged.
            .filter { (it.pattern as String?) != null && (it.kind as ParsingRule.Kind?) != null }
    }.getOrDefault(emptyList())

    @Synchronized fun add(rule: ParsingRule) = save(listOf(rule) + state.value.filter { it.id != rule.id })
    @Synchronized fun delete(id: String) = save(state.value.filter { it.id != id })
    @Synchronized fun replaceAll(rules: List<ParsingRule>) = save(rules)

    private fun save(rules: List<ParsingRule>) {
        prefs.edit().putString(KEY, gson.toJson(rules)).apply()
        state.value = rules
    }

    companion object {
        private const val KEY = "rules"
        @Volatile private var instance: ParsingRuleStore? = null
        fun get(context: Context): ParsingRuleStore = instance ?: synchronized(this) {
            instance ?: ParsingRuleStore(context.applicationContext).also { instance = it }
        }
    }
}
