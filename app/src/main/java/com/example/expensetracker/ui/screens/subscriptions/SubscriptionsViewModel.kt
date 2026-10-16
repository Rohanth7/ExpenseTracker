package com.example.expensetracker.ui.screens.subscriptions

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.db.entity.Bill
import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.repository.BillRepository
import com.example.expensetracker.data.repository.CategoryRepository
import com.example.expensetracker.data.repository.ExpenseRepository
import com.example.expensetracker.data.subscriptions.Cadence
import com.example.expensetracker.data.subscriptions.DismissedSubscriptions
import com.example.expensetracker.data.subscriptions.Subscription
import com.example.expensetracker.data.subscriptions.SubscriptionDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class SubscriptionsViewModel(
    private val appContext: Context,
    expenseRepo: ExpenseRepository,
    categoryRepo: CategoryRepository,
    private val billRepo: BillRepository
) : ViewModel() {

    /** null until the first result, so the screen doesn't flash "none found". */
    val subscriptions: StateFlow<List<Subscription>?> = combine(
        expenseRepo.allExpenses, billRepo.allBills, DismissedSubscriptions.flow(appContext)
    ) { expenses, bills, dismissed ->
        SubscriptionDetector.detect(expenses, System.currentTimeMillis(), dismissed, bills.map { it.name })
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val categories: StateFlow<List<Category>> = categoryRepo.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var message by mutableStateOf<String?>(null)
        private set
    /** The last dismissed payee, so the snackbar can offer Undo. */
    var lastDismissed by mutableStateOf<Subscription?>(null)
        private set

    fun clearMessage() { message = null; lastDismissed = null }

    /** Bills repeat monthly, so only monthly subscriptions can become one. */
    fun canTrackAsBill(s: Subscription) = s.cadence == Cadence.MONTHLY

    fun trackAsBill(s: Subscription) = viewModelScope.launch {
        if (!canTrackAsBill(s)) return@launch
        val dueDay = Calendar.getInstance().apply { timeInMillis = s.nextExpected }.get(Calendar.DAY_OF_MONTH)
        billRepo.insert(Bill(name = s.name, amount = s.amount, dueDayOfMonth = dueDay, reminderDays = 3, categoryId = s.categoryId))
        message = "${s.name} added to Bills & Reminders. You'll get a reminder 3 days before it's due."
    }

    fun dismiss(s: Subscription) {
        DismissedSubscriptions.dismiss(appContext, s.key)
        lastDismissed = s
        message = "${s.name} won't be shown as a subscription"
    }

    fun undoDismiss() {
        lastDismissed?.let { DismissedSubscriptions.restore(appContext, it.key) }
        lastDismissed = null
    }

    companion object {
        fun factory(context: Context, expenseRepo: ExpenseRepository, categoryRepo: CategoryRepository, billRepo: BillRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SubscriptionsViewModel(context.applicationContext, expenseRepo, categoryRepo, billRepo) as T
            }
    }
}
