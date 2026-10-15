package com.example.expensetracker.ui.screens.rules

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.db.entity.Expense
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.sms.CustomRules
import com.example.expensetracker.sms.ParsingRule
import com.example.expensetracker.sms.ParsingRuleStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class MessageRulesViewModel(
    private val store: ParsingRuleStore,
    private val expenseRepo: ExpenseRepository
) : ViewModel() {
    val rules: StateFlow<List<ParsingRule>> = store.rules

    /** The expense being corrected, when teaching starts from one. */
    var expense by mutableStateOf<Expense?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var lastDeleted by mutableStateOf<ParsingRule?>(null)
        private set

    fun clearMessage() { message = null; lastDeleted = null }

    fun loadExpense(id: Long) = viewModelScope.launch { expense = expenseRepo.getById(id) }

    /** Returns an error to show, or null when saved. */
    fun save(text: String, kind: ParsingRule.Kind, amount: IntRange?, payee: IntRange?, onSaved: () -> Unit): String? {
        val result = CustomRules.build(text, kind, amount, payee, UUID.randomUUID().toString(), System.currentTimeMillis())
        if (result is CustomRules.BuildResult.Failed) return result.reason
        val rule = (result as CustomRules.BuildResult.Ok).rule
        store.add(rule)
        val e = expense
        viewModelScope.launch {
            message = if (e != null) {
                // Apply the new reading to the expense it was taught from.
                if (kind == ParsingRule.Kind.PAYMENT) {
                    val m = CustomRules.match(listOf(rule), text) as? com.example.expensetracker.sms.RuleMatch.Payment
                    if (m != null) expenseRepo.update(e.copy(amount = m.amount, description = m.payee ?: e.description))
                    "Rule saved, and this expense was corrected"
                } else {
                    expenseRepo.delete(e)
                    "Rule saved. This message wasn't a payment, so its expense was removed"
                }
            } else "Rule saved. MyMoney will read messages like this one from now on"
            onSaved()
        }
        return null
    }

    fun delete(rule: ParsingRule) {
        store.delete(rule.id)
        lastDeleted = rule
        message = "Rule deleted"
    }

    fun undoDelete() { lastDeleted?.let { store.add(it) }; lastDeleted = null }

    companion object {
        fun factory(context: Context, expenseRepo: ExpenseRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MessageRulesViewModel(ParsingRuleStore.get(context), expenseRepo) as T
        }
    }
}
