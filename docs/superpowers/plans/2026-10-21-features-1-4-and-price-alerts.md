# Features 1–4 + Subscription Price-Change Alerts: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task by task. Steps use checkbox (`- [ ]`) syntax for tracking. Each **Feature** below is one PR. Do them in order (F1 → F5); within a feature, do tasks in order.

**Goal:** Build five user-requested features before any paywall or Play Store launch work:
- **F1** Faster entry
- **F2** CSV export + restore on a new phone
- **F3** Salary-cycle months + custom date ranges
- **F4** Income entries
- **F5** Subscription price-change alerts

**Architecture:** Each feature is an independent PR on its own branch from the latest `main`. Pure logic goes into small Kotlin objects with JVM unit tests: `AmountCalculator`, `CsvExporter`, `BudgetPeriod`, `CashFlow`, `IncomeForecast`, the price-change part of `SubscriptionDetector`, and `PriceChangeAlerts`. UI changes stay inside the existing screens and use the existing components and colours, because a redesign is planned later. Only F4 changes the database schema (v13 → v14) and the backup format (v2 → v3).

**Tech stack:** Kotlin 2.2.10, Jetpack Compose (BOM 2026.02.01, Material 3), Room 2.7.1 (KSP), WorkManager 2.11.2, RemoteViews widget, Gson backups, JUnit 4.13.2 (JVM), AndroidX instrumented tests. minSdk 24, targetSdk 36. **kotlinx-datetime is not used**; dates use `java.util.Calendar` like the rest of the code.

**Why these features:** the evidence is in `docs/mymoney-raha-teardown.md` (11,237 reviews of the 10L+ "MyMoney" app) and `docs/competitor-study.md`. The broader roadmap is `docs/superpowers/plans/2026-10-16-user-review-gaps-roadmap.md`. Those files may only exist locally (uncommitted); this plan is self-contained.

## Global constraints

- **Local-first:** no account, no backend, no ads, no loans. Nothing new leaves the phone.
- **No paywall yet.** F5 is a future Pro feature, but it ships ungated now. Keep its entry point in **one** function (`PriceChangeAlerts.checkAfterCapture`) so gating is a one-line change later.
- **Owner decisions (fixed):**
  1. Income lives in its **own table**, not a type column on `expenses`.
  2. Account balances (a later feature) are **calculated** from opening balance + entries, never read from SMS.
  3. The month-start day is limited to **1–28**.
  4. The free/Pro split is decided later.
- **TDD:** write the failing test first, run it and watch it fail for the right reason, then implement. Never write production code before its test.
- **Don't regress:** month ends use `23:59:59.999`; PendingIntent request codes and notification ids are masked with `and 0x7FFFFFFF`; captured SMS text is kept 7 days and never put in backups (`BackupCodec.toJson` strips `rawSms`); the existing 92 JVM and 11 instrumented tests stay green.
- **One session at a time on this repo.** Before each PR: `git fetch && git checkout main && git pull`, then branch. Don't edit files outside a feature's list unless the task says so.
- **Every PR:** is checked on the emulator against `main` (with screenshots), updates `NOTES.md` (features, merged PRs, test counts), and is opened with `gh pr create` as **Rohanth7** (see "Working on this repo" at the end).

## File map

Paths are relative to `app/src/main/java/com/example/expensetracker/` unless shown with `app/`.

| File | Responsibility | Feature |
|---|---|---|
| `ui/util/AmountCalculator.kt` (new) | Evaluate "120+45", "3×40" safely | F1 |
| `ui/screens/expenses/ExpenseCopy.kt` (new) | `Expense.duplicateForNow(now)`, `defaultCategoryId(...)` | F1 |
| `LaunchAction.kt` (new) | What a launch intent asks for (categorize / quick add / open subscriptions) | F1, F5 |
| `data/export/CsvExporter.kt` (new) | CSV text for expenses (later incomes) | F2, F4 |
| `data/export/ExportRange.kt` (new) | Presets for the export range | F2, F3 |
| `data/period/BudgetPeriod.kt` (new) | The one place that answers "which dates are this cycle" | F3 |
| `data/period/MonthStartDay.kt` (new) | The month-start setting as a `StateFlow` | F3 |
| `ui/util/DateUtil.kt` (new) | Date-picker UTC ↔ local helpers | F3 |
| `data/db/entity/Income.kt`, `RecurringIncome.kt` (new) | Income tables | F4 |
| `data/db/dao/IncomeDao.kt`, `RecurringIncomeDao.kt` (new) | Income queries | F4 |
| `data/repository/IncomeRepository.kt` (new) | Income repository | F4 |
| `ui/screens/dashboard/IncomeForecast.kt` (new) | Which income the forecast uses | F4 |
| `notification/PriceChangeAlerts.kt` (new) | Detect a price change on capture and notify once | F5 |

---

# F1: Faster entry

**Branch:** `feat/faster-entry`. **Effort:** 3–5 days. **No schema change.**

**Why:** "Entering every spend is boring" and "too many taps" make up ~5–7% of MyMoney's 1–3★ reviews. "Duplicate record" was its most-thanked 2026 feature. Our manual entry was rated 3/5 in the audit.

**What exists today** (`ui/screens/expenses/ExpensesScreen.kt`):
- **FAB:** a custom 58dp `Box` (L89–108). It shows a Toast "Please add a category first" when there are no categories; icon `contentDescription = "Add expense"` (L106).
- **`AddExpenseDialog`** (L789–908): amount parsed with `toDoubleOrNull()` (L896); category defaults to `categories.firstOrNull()` and the confirm button is disabled with no categories (L898–900). Save calls `viewModel.addExpense(amount, description, categoryId, date, tags, paymentMethod)` (L348–356).
- **`EditExpenseDialog`** (L911–1013) has only Save and Cancel.
- **Row icons** (L764–770) have `contentDescription = null`.
- **ViewModel:** `ExpensesViewModel.addExpense(...)` (L172–177) accepts `categoryId = -1L` and skips the budget alert for it.
- **Widget:** `widget/BudgetPulseWidget.kt` (RemoteViews) has one click intent on `R.id.widget_root` (L147–156), with no extras. `MainActivity` reads only `NotificationHelper.EXTRA_EXPENSE_ID` (onCreate L83, onNewIntent L161–166). `AppNavGraph(initialExpenseId, onExpenseNavigated)` navigates in a `LaunchedEffect` (NavGraph L102–107).

### Task F1.1: `AmountCalculator`

**Files:**
- Create: `ui/util/AmountCalculator.kt`
- Test: `app/src/test/java/com/example/expensetracker/ui/util/AmountCalculatorTest.kt`

**Interfaces — Produces:** `AmountCalculator.evaluate(text: String): Double?` (positive, finite, rounded to 2 decimals, or null) and `AmountCalculator.isExpression(text: String): Boolean`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.expensetracker.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountCalculatorTest {
    private fun eval(s: String) = AmountCalculator.evaluate(s)

    @Test fun `plain numbers still work`() { assertEquals(250.0, eval("250")!!, 0.0); assertEquals(99.5, eval("99.50")!!, 0.0) }
    @Test fun `adds and subtracts`() { assertEquals(165.0, eval("120+45")!!, 0.0); assertEquals(70.0, eval("100-30")!!, 0.0) }
    @Test fun `multiplication before addition`() = assertEquals(60.0, eval("100-20*2")!!, 0.0)
    @Test fun `accepts × ÷ and the minus sign`() {
        assertEquals(120.0, eval("3×40")!!, 0.0); assertEquals(22.5, eval("90÷4")!!, 0.0); assertEquals(80.0, eval("100−20")!!, 0.0)
    }
    @Test fun `ignores rupee sign, Indian commas and spaces`() {
        assertEquals(1500.0, eval("1,200 + 300")!!, 0.0); assertEquals(250.0, eval("₹ 250")!!, 0.0)
    }
    @Test fun `rounds to paise`() { assertEquals(0.3, eval("0.1+0.2")!!, 0.0); assertEquals(33.33, eval("100/3")!!, 0.0) }
    @Test fun `rejects invalid input`() {
        listOf("", "abc", "120+", "+", "10/0", "50-80", "1..2", "12a").forEach { assertNull("'$it'", eval(it)) }
    }
    @Test fun `knows when text is an expression`() {
        assertTrue(AmountCalculator.isExpression("120+45")); assertTrue(AmountCalculator.isExpression("3×40"))
        assertFalse(AmountCalculator.isExpression("250")); assertFalse(AmountCalculator.isExpression("1,200"))
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./gradlew testDebugUnitTest --tests '*AmountCalculatorTest'`
Expected: compilation fails with "Unresolved reference: AmountCalculator".

- [ ] **Step 3: Implement**

```kotlin
package com.example.expensetracker.ui.util

/**
 * Evaluates simple amount expressions typed into the amount field ("120+45", "1,200 - 50", "3×40").
 * A tiny recursive-descent parser: no eval, no scripting engine. + − × ÷, normal precedence.
 */
object AmountCalculator {
    private const val OPERATORS = "+-*/×÷−xX"

    /** Positive, finite result rounded to 2 decimals, or null if the text isn't a valid amount. */
    fun evaluate(text: String): Double? {
        val s = text.replace("₹", "").replace(",", "").replace(" ", "")
            .replace('×', '*').replace('x', '*').replace('X', '*').replace('÷', '/').replace('−', '-')
        if (s.isEmpty()) return null
        val parser = Parser(s)
        val value = parser.expression() ?: return null
        if (!parser.atEnd() || !value.isFinite() || value <= 0) return null
        return Math.round(value * 100) / 100.0
    }

    /** True when the text contains an operator, so the UI can show "= ₹165" under the field. */
    fun isExpression(text: String): Boolean = text.trim().drop(1).any { it in OPERATORS }

    private class Parser(private val s: String) {
        private var i = 0
        fun atEnd() = i == s.length

        fun expression(): Double? {
            var left = term() ?: return null
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]
                val right = term() ?: return null
                left = if (op == '+') left + right else left - right
            }
            return left
        }

        private fun term(): Double? {
            var left = number() ?: return null
            while (i < s.length && (s[i] == '*' || s[i] == '/')) {
                val op = s[i++]
                val right = number() ?: return null
                if (op == '/' && right == 0.0) return null
                left = if (op == '*') left * right else left / right
            }
            return left
        }

        private fun number(): Double? {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            return s.substring(start, i).toDoubleOrNull()
        }
    }
}
```

- [ ] **Step 4: Run the test and watch it pass**

Run: `./gradlew testDebugUnitTest --tests '*AmountCalculatorTest'`. Expected: 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/expensetracker/ui/util/AmountCalculator.kt app/src/test/java/com/example/expensetracker/ui/util/AmountCalculatorTest.kt
git commit -m "Add AmountCalculator for expressions in the amount field"
```

### Task F1.2: Duplicate rules and default category

**Files:**
- Create: `ui/screens/expenses/ExpenseCopy.kt`
- Test: `app/src/test/java/com/example/expensetracker/ui/screens/expenses/ExpenseCopyTest.kt`

**Interfaces — Produces:** `fun Expense.duplicateForNow(now: Long): Expense`, `fun defaultCategoryId(categories: List<Category>, lastUsedId: Long): Long`.

- [ ] **Step 1: Failing test**

```kotlin
package com.example.expensetracker.ui.screens.expenses

import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpenseCopyTest {
    private val original = Expense(id = 42, amount = 180.0, description = "SWIGGY", categoryId = 3, date = 1_000L,
        source = "SMS", rawSms = "Rs 180 debited…", tags = "lunch", paymentMethod = "Credit Card")

    @Test fun `a duplicate is a new manual expense dated now`() {
        val copy = original.duplicateForNow(now = 9_999L)
        assertEquals(0L, copy.id); assertEquals(9_999L, copy.date); assertEquals("Manual", copy.source); assertNull(copy.rawSms)
    }
    @Test fun `a duplicate keeps what the user typed`() {
        val copy = original.duplicateForNow(9_999L)
        assertEquals(180.0, copy.amount, 0.0); assertEquals("SWIGGY", copy.description); assertEquals(3L, copy.categoryId)
        assertEquals("lunch", copy.tags); assertEquals("Credit Card", copy.paymentMethod)
    }
    @Test fun `last used category is the default when it still exists`() {
        val cats = listOf(Category(id = 1, name = "Food"), Category(id = 2, name = "Travel"))
        assertEquals(2L, defaultCategoryId(cats, lastUsedId = 2))
    }
    @Test fun `otherwise the default is pending, not the first category`() {
        val cats = listOf(Category(id = 1, name = "Food"))
        assertEquals(-1L, defaultCategoryId(cats, lastUsedId = 99)); assertEquals(-1L, defaultCategoryId(emptyList(), -1))
    }
}
```

- [ ] **Step 2: Run it and watch it fail.** `./gradlew testDebugUnitTest --tests '*ExpenseCopyTest'` → unresolved references.

- [ ] **Step 3: Implement**

```kotlin
package com.example.expensetracker.ui.screens.expenses

import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense

/** A copy for "Duplicate": a new manual entry dated now. Captured SMS text is never copied. */
fun Expense.duplicateForNow(now: Long): Expense = copy(id = 0, date = now, source = "Manual", rawSms = null)

/** The add dialog's starting category: the last one used if it still exists, else pending (-1). */
fun defaultCategoryId(categories: List<Category>, lastUsedId: Long): Long =
    if (categories.any { it.id == lastUsedId }) lastUsedId else -1L
```

- [ ] **Step 4: Run and pass.** Expected: 4 tests pass.
- [ ] **Step 5: Commit** — `git commit -m "Add duplicate and default-category rules for manual entry"` (with both files).

### Task F1.3: Use them in the add/edit dialogs; category optional

**Files:**
- Modify: `ui/screens/expenses/ExpensesScreen.kt` (FAB L89–108; `AddExpenseDialog` L789–908; `EditExpenseDialog` L911–1013; row icons L764–770; save wiring L348–356)
- Modify: `ui/screens/expenses/ExpensesViewModel.kt` (L172–189)
- Modify: `data/preferences/PreferencesManager.kt`

**Interfaces:**
- Consumes: `AmountCalculator`, `duplicateForNow`, `defaultCategoryId`.
- Produces: `PreferencesManager.lastManualCategoryId: Long` (key `last_manual_category_id`, default `-1L`); `ExpensesViewModel.duplicateExpense(expense: Expense)`.

- [ ] **Step 1: Preference.** Add to `PreferencesManager`, following the existing pattern:

```kotlin
var lastManualCategoryId: Long
    get() = prefs.getLong("last_manual_category_id", -1L)
    set(value) = prefs.edit().putLong("last_manual_category_id", value).apply()
```

- [ ] **Step 2: ViewModel.** In `addExpense(...)`, after the insert, set `prefs.lastManualCategoryId = categoryId` when `categoryId != -1L`. Add:

```kotlin
fun duplicateExpense(expense: Expense) = viewModelScope.launch {
    val copy = expense.duplicateForNow(System.currentTimeMillis())
    expenseRepo.insert(copy)
    if (copy.categoryId != -1L && prefs.budgetAlertsEnabled)
        BudgetAlertHelper.checkAndNotify(appContext, copy.categoryId, categoryRepo, expenseRepo, prefs.budgetAlertThreshold)
}
```

Expose `val lastManualCategoryId get() = prefs.lastManualCategoryId` for the screen.

- [ ] **Step 3: Amount field (both dialogs).**
  - Replace `amount.toDoubleOrNull()` (L896 and the edit dialog's equivalent) with `AmountCalculator.evaluate(amount)`.
  - Keep the "Enter a valid amount" error for null.
  - Under the field, when `AmountCalculator.isExpression(amount)` and the result isn't null, show `Text("= ₹${formatted}", color = Muted, fontSize = 12.sp)`.
  - Add a one-line row of four small `AssistChip`s, `+ − × ÷`, under the field. Each appends its symbol to `amount`, because the decimal keyboard has no operator keys.
  - Keep `KeyboardType.Decimal`.

- [ ] **Step 4: Category optional.**
  - In `AddExpenseDialog`, add a first dropdown entry **"Choose later (pending)"** with id `-1L`.
  - Initialise `selectedCategoryId` with `defaultCategoryId(categories, lastManualCategoryId)` (pass it in as a parameter).
  - Remove the `-1 → categories.first()` fallback (L898–900) and the `enabled = categories.isNotEmpty()` condition.
  - Remove the Toast branch from the FAB, so the dialog opens even with no categories.
  - Pending expenses already open `CategorizeScreen` when tapped.

- [ ] **Step 5: Duplicate action.** In `EditExpenseDialog`, put a `TextButton("Duplicate")` before Cancel in the `dismissButton` slot. It calls a new `onDuplicate: () -> Unit` parameter. At the call site: `viewModel.duplicateExpense(editingExpense!!); editingExpense = null`, then show a snackbar "Duplicated — dated today" (the screen's existing snackbar host, or a Toast if there is none).

- [ ] **Step 6: Labels and accessibility.**
  - FAB: change the `Box` to a `Row` with the icon plus `Text("Add expense", color = Deep, fontWeight = SemiBold)`, horizontal padding 18.dp, same shape and colours.
  - Row icons (L764–770): `contentDescription = "Edit"` and `"Delete"`.

- [ ] **Step 7: Build and run all unit tests.** Run: `./gradlew assembleDebug testDebugUnitTest`. Expected: BUILD SUCCESSFUL, 104 tests (92 + 12) pass.

- [ ] **Step 8: Commit** — `git commit -m "Calculator, optional category, last category and Duplicate in manual entry"`.

### Task F1.4: Quick-add from the widget

**Files:**
- Create: `LaunchAction.kt`
- Modify: `widget/BudgetPulseWidget.kt` (L147–156), `app/src/main/res/layout/budget_pulse_widget_layout.xml`, `MainActivity.kt` (L46, L83, L161–166, L93–100), `ui/navigation/NavGraph.kt` (L58–107, expenses route), `ui/screens/expenses/ExpensesScreen.kt`

**Interfaces — Produces:**

```kotlin
package com.example.expensetracker

/** What a launch intent asks the app to do. F5 adds OpenSubscriptions. */
sealed class LaunchAction {
    data class Categorize(val expenseId: Long) : LaunchAction()
    object QuickAdd : LaunchAction()
    object OpenSubscriptions : LaunchAction()

    companion object {
        const val EXTRA_ACTION = "launch_action"
        const val QUICK_ADD = "quick_add"
        const val OPEN_SUBSCRIPTIONS = "open_subscriptions"

        /** Pure so it can be unit-tested: pass intent extras as plain values. */
        fun from(action: String?, expenseId: Long): LaunchAction? = when {
            expenseId != -1L -> Categorize(expenseId)
            action == QUICK_ADD -> QuickAdd
            action == OPEN_SUBSCRIPTIONS -> OpenSubscriptions
            else -> null
        }
    }
}
```

- [ ] **Step 1: Failing test** `app/src/test/java/com/example/expensetracker/LaunchActionTest.kt`:

```kotlin
package com.example.expensetracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchActionTest {
    @Test fun `a notification expense id wins`() = assertEquals(LaunchAction.Categorize(7), LaunchAction.from(LaunchAction.QUICK_ADD, 7))
    @Test fun `quick add from the widget`() = assertEquals(LaunchAction.QuickAdd, LaunchAction.from(LaunchAction.QUICK_ADD, -1))
    @Test fun `open subscriptions`() = assertEquals(LaunchAction.OpenSubscriptions, LaunchAction.from(LaunchAction.OPEN_SUBSCRIPTIONS, -1))
    @Test fun `plain launch does nothing`() = assertNull(LaunchAction.from(null, -1))
}
```

- [ ] **Step 2: Run, fail. Step 3: create `LaunchAction.kt` as above. Step 4: run, pass.**

- [ ] **Step 5: MainActivity.**
  - Replace `pendingExpenseId` with `private var pendingAction by mutableStateOf<LaunchAction?>(null)`.
  - In `onCreate` and `onNewIntent`: `pendingAction = LaunchAction.from(intent?.getStringExtra(LaunchAction.EXTRA_ACTION), intent?.getLongExtra(NotificationHelper.EXTRA_EXPENSE_ID, -1L) ?: -1L)`.
  - Pass `AppNavGraph(launchAction = pendingAction, onLaunchHandled = { pendingAction = null })`.

- [ ] **Step 6: NavGraph.**
  - Change the signature to `AppNavGraph(launchAction: LaunchAction? = null, onLaunchHandled: () -> Unit = {})`.
  - Extend the expenses route to `expenses?start={start}&end={end}&add={add}` with a Boolean `add` argument (default false).
  - `Screen.Expenses.createRoute(start, end, add = false)`.
  - In the `LaunchedEffect(launchAction)`:
    - `Categorize` → existing navigation.
    - `QuickAdd` → `navController.navigate(Screen.Expenses.createRoute(add = true))`.
    - `OpenSubscriptions` → `navController.navigate(Screen.Subscriptions.route)`.
    - Then `onLaunchHandled()`.
  - Pass `openAddOnStart = add` to `ExpensesScreen`. Its initial `showAddDialog` is `remember { mutableStateOf(openAddOnStart) }`.
  - Keep the bottom-nav behaviour unchanged.

- [ ] **Step 7: Widget.**
  - Add a 36dp `ImageView` `@+id/widget_add` (system `@android:drawable/ic_input_add`, tinted with the widget accent, `android:contentDescription="Add expense"`) at the top-right of `budget_pulse_widget_layout.xml`.
  - In `BudgetPulseWidget.kt`, build:

```kotlin
val addPi = PendingIntent.getActivity(
    context, 1,
    Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(LaunchAction.EXTRA_ACTION, LaunchAction.QUICK_ADD)
    },
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)
views.setOnClickPendingIntent(R.id.widget_add, addPi)
```

  - Request code `1` differs from the root intent (`0`), so the two don't overwrite each other.
  - Also set the root click and the add click on the placeholder and disabled layouts, so the widget is never dead.
  - Note: the app lock (if on) shows first, then the dialog. That is intended.

- [ ] **Step 8:** `./gradlew assembleDebug testDebugUnitTest` → all pass.
- [ ] **Step 9: Commit** — `git commit -m "Quick-add from the home-screen widget"`.

### Task F1.5: Emulator check and PR

- [ ] Install `main`, add the Budget Pulse widget, then install this branch over it.
- [ ] Check each of these:
  1. Add with "Choose later" → appears as pending; tapping it opens Categorize.
  2. Type `120+45` → "= ₹165" shown; saved as 165.
  3. The `×` chip appends `×`; `3×40` saves 120.
  4. Edit → Duplicate → a second row dated today; budget alert behaves.
  5. Add with Food → reopen the dialog → Food preselected.
  6. Widget "+" opens the add dialog (with app lock off and on); a widget tap elsewhere opens the app as before.
  7. Fresh install with no categories → the FAB opens the dialog (no Toast).
- [ ] Screenshots of 2, 4 and 6.
- [ ] Update `NOTES.md`, then open the PR "Faster entry: calculator, optional category, duplicate, widget quick-add".

---

# F2: CSV export + restore on a new phone

**Branch:** `feat/csv-export-restore-offer`. **Effort:** 1–2 days. **No schema change.**

**Why:** "export no longer works" is a top axio complaint; MyMoney has free CSV export; data lost on a phone change is MyMoney's #2 complaint. Export must stay free.

**What exists:**
- `SettingsScreen` Data & Backup (`DataSubPage`, L746) has "Export backup" (JSON via `CreateDocument("application/json")`, L102) and "Restore from backup" (`OpenDocument`, L115). The view-model flow is `requestImport(uri)` → `pendingRestore` → `RestoreConfirmDialog` → `confirmRestore()`.
- `BackupManager.readFromUri(uri): ParseResult` and `restore(data)` exist.
- `OnboardingScreen(onFinish: (Double?) -> Unit)` with steps `Welcome, Sms, Notifications, Alerts, Income`. `MainActivity.finishOnboarding(prefs, income)` seeds categories if empty.

### Task F2.1: `CsvExporter`

**Files:**
- Create: `data/export/CsvExporter.kt`
- Test: `app/src/test/java/com/example/expensetracker/data/export/CsvExporterTest.kt`

**Interfaces — Produces:** `CsvExporter.HEADER: List<String>`, `CsvExporter.expensesToCsv(expenses: List<Expense>, categories: List<Category>, zone: TimeZone = TimeZone.getDefault()): String`. F4 adds `incomes` to the same function.

Columns, fixed from now on: `date,time,type,amount,description,category,subcategory,payment_method,tags,source`. `type` is `expense` for now; F4 adds `income`.

- [ ] **Step 1: Failing test**

```kotlin
package com.example.expensetracker.data.export

import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CsvExporterTest {
    private val ist = TimeZone.getTimeZone("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int, h: Int = 9, min: Int = 30) =
        Calendar.getInstance(ist).apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis
    private val food = Category(id = 1, name = "Food")
    private val swiggy = Category(id = 2, name = "Swiggy", parentId = 1)
    private fun lines(csv: String) = csv.removePrefix(CsvExporter.BOM).split("\r\n").filter { it.isNotEmpty() }

    @Test fun `starts with a BOM and the header`() {
        val csv = CsvExporter.expensesToCsv(emptyList(), emptyList(), ist)
        assertTrue(csv.startsWith(CsvExporter.BOM))
        assertEquals(listOf("date,time,type,amount,description,category,subcategory,payment_method,tags,source"), lines(csv))
    }
    @Test fun `one row per expense, oldest first, in local time`() {
        val csv = CsvExporter.expensesToCsv(listOf(
            Expense(amount = 1500.0, description = "Rent", categoryId = 1, date = at(2026, 10, 5, 23, 45), source = "Recurring"),
            Expense(amount = 99.5, description = "Tea", categoryId = 1, date = at(2026, 10, 1)),
        ), listOf(food), ist)
        assertEquals("2026-10-01,09:30,expense,99.50,Tea,Food,,UPI,,Manual", lines(csv)[1])
        assertEquals("2026-10-05,23:45,expense,1500.00,Rent,Food,,UPI,,Recurring", lines(csv)[2])
    }
    @Test fun `subcategories split into category and subcategory`() {
        val csv = CsvExporter.expensesToCsv(listOf(Expense(amount = 250.0, description = "Lunch", categoryId = 2, date = at(2026, 10, 2))), listOf(food, swiggy), ist)
        assertTrue(lines(csv)[1].contains(",Food,Swiggy,"))
    }
    @Test fun `pending expenses say Pending`() {
        val csv = CsvExporter.expensesToCsv(listOf(Expense(amount = 10.0, description = "X", categoryId = -1, date = at(2026, 10, 2))), emptyList(), ist)
        assertTrue(lines(csv)[1].contains(",Pending,,"))
    }
    @Test fun `commas, quotes and newlines are quoted`() {
        val csv = CsvExporter.expensesToCsv(listOf(Expense(amount = 10.0, description = "Dinner, \"Taj\"\nhotel", categoryId = -1, date = at(2026, 10, 2))), emptyList(), ist)
        assertTrue(csv.contains("\"Dinner, \"\"Taj\"\"\nhotel\""))
    }
}
```

- [ ] **Step 2: Run, fail.**

- [ ] **Step 3: Implement**

```kotlin
package com.example.expensetracker.data.export

import com.example.expensetracker.data.db.entity.Category
import com.example.expensetracker.data.db.entity.Expense
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Spreadsheet-ready CSV (RFC 4180, UTF-8 with BOM so Excel reads ₹ and Hindi correctly). */
object CsvExporter {
    const val BOM = "﻿"
    val HEADER = listOf("date", "time", "type", "amount", "description", "category", "subcategory", "payment_method", "tags", "source")

    fun expensesToCsv(expenses: List<Expense>, categories: List<Category>, zone: TimeZone = TimeZone.getDefault()): String {
        val byId = categories.associateBy { it.id }
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }
        val time = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = zone }
        val sb = StringBuilder(BOM).append(HEADER.joinToString(",")).append("\r\n")
        expenses.sortedBy { it.date }.forEach { e ->
            val (cat, sub) = names(e.categoryId, byId)
            val row = listOf(day.format(e.date), time.format(e.date), "expense", money(e.amount), e.description,
                cat, sub, e.paymentMethod, e.tags.trim(), e.source)
            sb.append(row.joinToString(",") { quote(it) }).append("\r\n")
        }
        return sb.toString()
    }

    internal fun names(categoryId: Long, byId: Map<Long, Category>): Pair<String, String> {
        val c = byId[categoryId] ?: return "Pending" to ""
        val parent = c.parentId?.let { byId[it] }
        return if (parent != null) parent.name to c.name else c.name to ""
    }

    internal fun money(amount: Double): String = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).toPlainString()

    internal fun quote(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + field.replace("\"", "\"\"") + "\"" else field
}
```

- [ ] **Step 4: Run, pass (5 tests).**
- [ ] **Step 5: Commit** — `git commit -m "Add CsvExporter"`.

### Task F2.2: Export CSV screen flow

**Files:**
- Create: `data/export/ExportRange.kt`
- Modify: `ui/screens/settings/SettingsScreen.kt` (`DataSubPage` L746, launchers ~L102–115), `ui/screens/settings/SettingsViewModel.kt`

**Interfaces — Produces:**

```kotlin
package com.example.expensetracker.data.export

enum class ExportPreset(val label: String) { THIS_MONTH("This month"), LAST_MONTH("Last month"), THIS_YEAR("This year"), ALL("All time"), CUSTOM("Choose dates") }

/** Calendar-month presets now; F3 Task 3.4 switches THIS_MONTH/LAST_MONTH to BudgetPeriod. Returns null for ALL. */
object ExportRange {
    fun forPreset(preset: ExportPreset, now: Long, custom: Pair<Long, Long>? = null): Pair<Long, Long>?
}
```

  Implement `forPreset` with `Calendar`:
  - This month: day 1 00:00:00.000 → last day 23:59:59.999.
  - Last month: the same, one month back.
  - This year: 1 Jan → 31 Dec 23:59:59.999.
  - `CUSTOM` returns `custom`.

- [ ] **Step 1: Failing test** `ExportRangeTest`: this month on 16 Oct 2026 → 1 Oct 00:00:00.000 to 31 Oct 23:59:59.999; last month in January → December of the previous year; all time → null. **Steps 2–4:** fail → implement → pass.

- [ ] **Step 5: ViewModel.**

```kotlin
fun exportCsvTo(uri: Uri, range: Pair<Long, Long>?) = viewModelScope.launch {
    val expenses = expenseRepo.allExpenses.first().filter { range == null || it.date in range.first..range.second }
    val csv = CsvExporter.expensesToCsv(expenses, categoryRepo.getAll())
    val ok = withContext(Dispatchers.IO) {
        runCatching { appContext.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) } != null }.getOrDefault(false)
    }
    snackbarMessage = if (ok) "Exported ${expenses.size} expenses" else "Couldn't save the CSV"
}
```

- [ ] **Step 6: UI.**
  - In `DataSubPage` add a `SettingRow` **"Export CSV"** ("Open in Excel or Google Sheets") under "Export backup".
  - Tapping it opens an `AlertDialog` with radio rows for the `ExportPreset` values. `CUSTOM` opens the date-range picker; until F3 adds `DateUtil`, use `DatePickerDialog` twice for start and end.
  - On confirm, launch `rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv"))` with the file name `mymoney-expenses-<preset or yyyy-MM-dd_to_yyyy-MM-dd>.csv`, then call `viewModel.exportCsvTo(uri, range)`.

- [ ] **Step 7:** Build and run tests, then **commit** — `git commit -m "Export expenses to CSV with a date range"`.

### Task F2.3: "Moving from another phone?" during setup

**Files:** Modify `ui/screens/onboarding/OnboardingScreen.kt` (Welcome step L101–111, buttons L170, L188–189), `MainActivity.kt` (L66–72, `finishOnboarding` L182–186).

**Interfaces:**
- Produces: `OnboardingScreen(onFinish: (Double?) -> Unit, onRestoreBackup: suspend (Uri) -> RestoreOutcome)`.
- Produces, in `OnboardingScreen.kt`: `sealed class RestoreOutcome { data class Restored(val summary: String, val hasIncome: Boolean) : RestoreOutcome(); data class Failed(val reason: String) : RestoreOutcome() }`.

- [ ] **Step 1: UI.**
  - On the Welcome step, add `SecondaryButton("Moving from another phone? Restore a backup")` under "Get started". It launches `OpenDocument()` with `arrayOf("application/json", "*/*")`.
  - On a URI: show a progress state, call `onRestoreBackup(uri)`.
    - `Restored` → show `"Restored: <summary>"` and continue to the **Sms** step (permissions are still needed on a new phone). Skip the Income step when `hasIncome`.
    - `Failed` → show the reason under the button and stay on Welcome.
  - Drive restore is not offered here yet. It needs the OAuth client, a launch task; add a TODO comment that references this plan.

- [ ] **Step 2: MainActivity.** Implement `onRestoreBackup`:

```kotlin
suspend fun restoreFromBackup(uri: Uri): RestoreOutcome {
    val manager = BackupManager(this)
    return when (val r = manager.readFromUri(uri)) {
        is ParseResult.Invalid -> RestoreOutcome.Failed(r.reason)
        is ParseResult.Ok -> runCatching {
            manager.restore(r.data)
            val d = r.data
            RestoreOutcome.Restored(
                summary = "${d.expenses?.size ?: 0} expenses, ${d.categories?.size ?: 0} categories, ${d.bills?.size ?: 0} bills",
                hasIncome = (d.settings?.monthlyIncome ?: 0.0) > 0
            )
        }.getOrElse { RestoreOutcome.Failed("The restore didn't finish. Nothing was changed.") }
    }
}
```

  `finishOnboarding` already skips seeding when categories exist, and must **not** overwrite a restored income with null. Keep `if (monthlyIncome != null) prefs.monthlyIncome = it`.

- [ ] **Step 3: Emulator check.**
  1. Export a JSON backup from a phone with data.
  2. Clear app data.
  3. Choose "Restore a backup" on Welcome → the summary shows → the SMS step follows → finish → the data is there and no starter categories were added on top.
  4. A truncated file → "The backup file is damaged or incomplete" and you stay on Welcome.
  5. CSV export: "This month" → pull the file → `python3 -c "import csv,sys; print(list(csv.reader(open(sys.argv[1], encoding='utf-8-sig')))[:3])" file.csv` matches the DB.
- [ ] **Step 4: Commit and PR** — "CSV export and restore during setup". Update `NOTES.md`.

---

# F3: Salary-cycle months + custom date ranges

**Branch:** `feat/salary-cycle-and-ranges`. **Effort:** 1–2 weeks. **No schema change.**

**Why:** "my salary cycle is 25th to 24th" is ~2–5% of MyMoney requests. Custom ranges and 3/6-month/all-time views are ~2%, and MyMoney keeps them in Pro.

**Scope rule:**
- **Budget periods follow the cycle:** Dashboard spend, forecast and safe-to-spend; Statistics monthly view; the widget; budget alerts.
- **Calendar-based things stay calendar-based:** recurring bills and EMIs keep their **calendar due days** (`RecurringManager` and `RecurringSchedule` are unchanged), the annual Statistics view, and the 12-month bar chart.
- With start day 1, everything behaves exactly as today.

**Call sites to change** (all use `java.util.Calendar` today):

| File | Lines | What |
|---|---|---|
| `ui/screens/dashboard/DashboardViewModel.kt` | L100, L114–117, L121–127, L129–130, L131–138, L160–166, L272–278, L387–400 | month range, previous month, days left, safe-to-spend, upcoming bills, EMI still due, insights day counts, `monthRangeForOffset`, `monthLabelFull` |
| `ui/screens/dashboard/CashFlow.kt` | whole | `billsStillDue(bills, today, daysInMonth)` |
| `ui/screens/statistics/StatisticsViewModel.kt` | L215–221, L251–276, L352–356 | auto-track "this month", monthly view, previous month, monthly insight |
| `widget/BudgetPulseRepository.kt` | L22–55 | current and previous range, day counts |
| `notification/BudgetAlertHelper.kt` | L43–51, L62 | range and `monthKey` |
| `ui/widgets/ExpenseWidget.kt` | L31–39 | **not registered in the manifest**; leave unchanged |

### Task F3.1: `BudgetPeriod`

**Files:**
- Create: `data/period/BudgetPeriod.kt`
- Test: `app/src/test/java/com/example/expensetracker/data/period/BudgetPeriodTest.kt`

**Interfaces — Produces:**

```kotlin
data class DateRange(val start: Long, val endInclusive: Long) { operator fun contains(t: Long) = t in start..endInclusive }
object BudgetPeriod {
    fun current(now: Long, monthStartDay: Int): DateRange
    fun forOffset(now: Long, monthStartDay: Int, offset: Int): DateRange   // 0 = current cycle, -1 = previous
    fun lastNMonths(now: Long, monthStartDay: Int, n: Int): DateRange      // n cycles ending with the current one
    fun daysLeft(now: Long, monthStartDay: Int): Int                       // today counts as day 1
    fun dayOfCycle(now: Long, monthStartDay: Int): Int                     // 1 on the cycle's first day
    fun daysInCycle(range: DateRange): Int
    fun label(range: DateRange, monthStartDay: Int): String                // "Oct 2026" or "25 Sep – 24 Oct"
}
```

- [ ] **Step 1: Failing test**

```kotlin
package com.example.expensetracker.data.period

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class BudgetPeriodTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, 0) }.timeInMillis
    private fun start(y: Int, m: Int, d: Int) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, 0, 0, 0) }.timeInMillis
    private fun end(y: Int, m: Int, d: Int) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, 23, 59, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis

    @Test fun `start day 1 is the calendar month`() =
        assertEquals(DateRange(start(2026, 10, 1), end(2026, 10, 31)), BudgetPeriod.current(at(2026, 10, 16), 1))
    @Test fun `the day before the start day belongs to the previous cycle`() =
        assertEquals(DateRange(start(2026, 9, 25), end(2026, 10, 24)), BudgetPeriod.current(at(2026, 10, 24), 25))
    @Test fun `the start day begins a new cycle`() {
        assertEquals(DateRange(start(2026, 10, 25), end(2026, 11, 24)), BudgetPeriod.current(at(2026, 10, 25), 25))
        assertEquals(DateRange(start(2026, 10, 25), end(2026, 11, 24)), BudgetPeriod.current(at(2026, 10, 26), 25))
    }
    @Test fun `cycles cross the year boundary`() =
        assertEquals(DateRange(start(2026, 12, 25), end(2027, 1, 24)), BudgetPeriod.current(at(2027, 1, 3), 25))
    @Test fun `February with start day 28`() =
        assertEquals(DateRange(start(2027, 2, 28), end(2027, 3, 27)), BudgetPeriod.current(at(2027, 3, 1), 28))
    @Test fun `offsets move whole cycles`() =
        assertEquals(DateRange(start(2026, 8, 25), end(2026, 9, 24)), BudgetPeriod.forOffset(at(2026, 10, 1), 25, -1))
    @Test fun `start days outside 1 to 28 are clamped`() =
        assertEquals(BudgetPeriod.current(at(2026, 10, 16), 28), BudgetPeriod.current(at(2026, 10, 16), 31))
    @Test fun `days left counts today`() {
        assertEquals(16, BudgetPeriod.daysLeft(at(2026, 10, 16), 1))  // 16..31
        assertEquals(1, BudgetPeriod.daysLeft(at(2026, 10, 24), 25))  // last day of the cycle
    }
    @Test fun `day of cycle and cycle length`() {
        assertEquals(22, BudgetPeriod.dayOfCycle(at(2026, 10, 16), 25))  // 25 Sep is day 1
        assertEquals(30, BudgetPeriod.daysInCycle(BudgetPeriod.current(at(2026, 10, 16), 25)))  // 25 Sep – 24 Oct
    }
    @Test fun `last three months ends with the current cycle`() =
        assertEquals(DateRange(start(2026, 8, 1), end(2026, 10, 31)), BudgetPeriod.lastNMonths(at(2026, 10, 16), 1, 3))
    @Test fun `labels`() {
        assertEquals("Oct 2026", BudgetPeriod.label(BudgetPeriod.current(at(2026, 10, 16), 1), 1))
        assertEquals("25 Sep – 24 Oct", BudgetPeriod.label(BudgetPeriod.current(at(2026, 10, 16), 25), 25))
    }
}
```

- [ ] **Step 2: Run, fail.**

- [ ] **Step 3: Implement**

```kotlin
package com.example.expensetracker.data.period

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class DateRange(val start: Long, val endInclusive: Long) {
    operator fun contains(t: Long) = t in start..endInclusive
}

/**
 * The single source of truth for "this month". With a salary cycle (e.g. day 25) a month runs
 * 25th → 24th. Start days are limited to 1–28 because 29–31 don't exist in every month.
 * Recurring bills and EMIs keep their calendar due days; they don't use this.
 */
object BudgetPeriod {
    const val MIN_START_DAY = 1
    const val MAX_START_DAY = 28
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    fun current(now: Long, monthStartDay: Int): DateRange = forOffset(now, monthStartDay, 0)

    fun forOffset(now: Long, monthStartDay: Int, offset: Int): DateRange {
        val day = monthStartDay.coerceIn(MIN_START_DAY, MAX_START_DAY)
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        if (cal.get(Calendar.DAY_OF_MONTH) < day) cal.add(Calendar.MONTH, -1)
        cal.set(Calendar.DAY_OF_MONTH, day)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.MONTH, offset)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        cal.add(Calendar.MILLISECOND, -1)
        return DateRange(start, cal.timeInMillis)
    }

    fun lastNMonths(now: Long, monthStartDay: Int, n: Int): DateRange =
        DateRange(forOffset(now, monthStartDay, -(n - 1)).start, current(now, monthStartDay).endInclusive)

    fun daysLeft(now: Long, monthStartDay: Int): Int {
        val end = current(now, monthStartDay).endInclusive
        return (Math.round((startOfDay(end) - startOfDay(now)).toDouble() / DAY_MS) + 1).toInt()
    }

    fun dayOfCycle(now: Long, monthStartDay: Int): Int =
        (Math.round((startOfDay(now) - current(now, monthStartDay).start).toDouble() / DAY_MS) + 1).toInt()

    fun daysInCycle(range: DateRange): Int =
        (Math.round((startOfDay(range.endInclusive) - range.start).toDouble() / DAY_MS) + 1).toInt()

    fun label(range: DateRange, monthStartDay: Int, locale: Locale = Locale.ENGLISH): String =
        if (monthStartDay.coerceIn(MIN_START_DAY, MAX_START_DAY) == 1) SimpleDateFormat("MMM yyyy", locale).format(range.start)
        else SimpleDateFormat("d MMM", locale).let { "${it.format(range.start)} – ${it.format(range.endInclusive)}" }

    private fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
```

- [ ] **Step 4: Run, pass (11 tests). Step 5: commit** — `git commit -m "Add BudgetPeriod for salary-cycle months"`.

### Task F3.2: The setting

**Files:** Create `data/period/MonthStartDay.kt`; modify `ui/screens/settings/SettingsScreen.kt` (Preferences sub-page), `SettingsViewModel.kt`, `data/backup/BackupData.kt` (`BackupSettings`), `data/backup/BackupManager.kt` (`snapshot` L72 area, `restore` L101 area).

**Interfaces — Produces:** `MonthStartDay.flow(context): StateFlow<Int>`, `MonthStartDay.get(context): Int`, `MonthStartDay.set(context, day: Int)`. This follows the `DismissedSubscriptions` pattern: a StateFlow backed by `expense_tracker_prefs`, key `month_start_day`, default 1, clamped to 1–28.

```kotlin
package com.example.expensetracker.data.period

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** "My month starts on the Nth" as a live value, so every screen recomputes when it changes. */
object MonthStartDay {
    private const val PREFS = "expense_tracker_prefs"
    private const val KEY = "month_start_day"
    @Volatile private var state: MutableStateFlow<Int>? = null

    fun flow(context: Context): StateFlow<Int> = synchronized(this) {
        state ?: MutableStateFlow(read(context)).also { state = it }
    }
    fun get(context: Context): Int = flow(context).value
    fun set(context: Context, day: Int) {
        val d = day.coerceIn(BudgetPeriod.MIN_START_DAY, BudgetPeriod.MAX_START_DAY)
        prefs(context).edit().putInt(KEY, d).apply()
        (flow(context) as MutableStateFlow<Int>).value = d
    }
    private fun read(context: Context) = prefs(context).getInt(KEY, 1).coerceIn(BudgetPeriod.MIN_START_DAY, BudgetPeriod.MAX_START_DAY)
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
```

- [ ] **Step 1: Backup.**
  - Add `val monthStartDay: Int? = null` to `BackupSettings`. This is an optional field, so the backup version stays 2.
  - `snapshot` writes `MonthStartDay.get(context)`.
  - `restore` applies it when non-null.
  - **Failing test first** in `BackupCodecTest`: a round trip keeps `monthStartDay = 25`; a v2 backup without the field parses with null.
- [ ] **Step 2: UI.**
  - Preferences sub-page: a row **"Month starts on"** whose value reads "1st (calendar month)" or "25th".
  - Tapping it opens a dialog listing 1–28, with the text "Pick the day your salary usually arrives. Budgets, the forecast and monthly statistics will run from that day."
  - `SettingsViewModel.setMonthStartDay(day)` calls `MonthStartDay.set(appContext, day)` and then `WidgetUpdateHelper.update(appContext)`.
- [ ] **Step 3: Commit** — `git commit -m "Month-start-day setting (1–28), included in backups"`.

### Task F3.3: Cycle-aware forecast (`CashFlow`)

**Files:** modify `ui/screens/dashboard/CashFlow.kt`; **replace** `app/src/test/.../ui/screens/dashboard/CashFlowTest.kt`.

**Interfaces:**
- Produces: `CashFlow.dueDatesInRange(dueDayOfMonth: Int, from: Long, to: Long): List<Long>` (midnight of each clamped due date in [from, to]).
- Produces: `CashFlow.billsStillDue(bills: List<Bill>, now: Long, cycle: DateRange): Double`.
- Produces: `CashFlow.emiStillDue(loans: List<Loan>, now: Long, cycle: DateRange): Double`.
- The old `billsStillDue(bills, today, daysInMonth)` is removed.

Semantics, unchanged from today for start day 1:
- A reminder bill counts if a due date falls on or after today's start, within the cycle.
- An auto-logged bill counts only if its due date is **after today** (today's is logged and already in spent).
- EMIs follow the reminder rule, and only for loans with `startDate <= cycle.endInclusive`.

- [ ] **Step 1: New test file**

```kotlin
package com.example.expensetracker.ui.screens.dashboard

import com.example.expensetracker.data.db.entity.Bill
import com.example.expensetracker.data.period.BudgetPeriod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class CashFlowTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, 0) }.timeInMillis
    private fun bill(amount: Double, dueDay: Int, autoLog: Boolean = false, enabled: Boolean = true) =
        Bill(name = "b$amount", amount = amount, dueDayOfMonth = dueDay, autoLog = autoLog, isEnabled = enabled)
    private fun still(bills: List<Bill>, now: Long, startDay: Int = 1) = CashFlow.billsStillDue(bills, now, BudgetPeriod.current(now, startDay))

    @Test fun `reminder bills count until the end of their due day`() =
        assertEquals(600.0, still(listOf(bill(100.0, 15), bill(200.0, 16), bill(400.0, 20)), at(2026, 10, 16)), 0.0)
    @Test fun `auto-logged bills count until they are logged on their due day`() =
        assertEquals(22000.0, still(listOf(bill(22000.0, 25, autoLog = true)), at(2026, 10, 16)), 0.0)
    @Test fun `an auto-logged bill due today is already in spent`() =
        assertEquals(0.0, still(listOf(bill(22000.0, 16, autoLog = true), bill(999.0, 1, autoLog = true)), at(2026, 10, 16)), 0.0)
    @Test fun `disabled bills are ignored`() = assertEquals(0.0, still(listOf(bill(500.0, 28, enabled = false)), at(2026, 10, 16)), 0.0)
    @Test fun `day 31 is the last day of a 30-day month`() = assertEquals(300.0, still(listOf(bill(300.0, 31)), at(2026, 11, 30)), 0.0)
    @Test fun `in a 25th-to-24th cycle, a bill due on the 5th of next month still counts`() =
        assertEquals(999.0, still(listOf(bill(999.0, 5)), at(2026, 10, 26), startDay = 25), 0.0)   // cycle 25 Oct – 24 Nov
    @Test fun `in a 25th-to-24th cycle, a bill due on the 26th this month already passed`() =
        assertEquals(0.0, still(listOf(bill(999.0, 26)), at(2026, 10, 27), startDay = 25), 0.0)
}
```

- [ ] **Step 2: Run, fail. Step 3: implement**

```kotlin
package com.example.expensetracker.ui.screens.dashboard

import com.example.expensetracker.data.db.entity.Bill
import com.example.expensetracker.data.db.entity.Loan
import com.example.expensetracker.data.period.DateRange
import java.util.Calendar

object CashFlow {
    /** Midnight of each due date (day clamped to the month's length) between [from] and [to]. */
    fun dueDatesInRange(dueDayOfMonth: Int, from: Long, to: Long): List<Long> {
        val out = mutableListOf<Long>()
        val cal = Calendar.getInstance().apply { timeInMillis = from; set(Calendar.DAY_OF_MONTH, 1) }
        repeat(3) {  // a cycle touches at most 2 calendar months; 3 is a safe bound
            val due = (cal.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, dueDayOfMonth.coerceIn(1, getActualMaximum(Calendar.DAY_OF_MONTH)))
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            if (due in from..to) out += due
            cal.add(Calendar.MONTH, 1)
        }
        return out
    }

    /** Reminder bills count from today's start; auto-logged bills only after today (today's is logged and in spent). */
    fun billsStillDue(bills: List<Bill>, now: Long, cycle: DateRange): Double {
        val todayStart = startOfDay(now)
        val tomorrowStart = todayStart + 24 * 60 * 60 * 1000L
        return bills.filter { it.isEnabled }.sumOf { b ->
            val from = if (b.autoLog) tomorrowStart else todayStart
            if (from > cycle.endInclusive) 0.0 else dueDatesInRange(b.dueDayOfMonth, from, cycle.endInclusive).size * b.amount
        }
    }

    /** EMIs still to come this cycle; loans that start after the cycle don't count. */
    fun emiStillDue(loans: List<Loan>, now: Long, cycle: DateRange): Double {
        val todayStart = startOfDay(now)
        return loans.filter { it.isActive && it.startDate <= cycle.endInclusive }
            .sumOf { dueDatesInRange(it.dueDayOfMonth, todayStart, cycle.endInclusive).size * it.monthlyEmi }
    }

    private fun startOfDay(t: Long) = Calendar.getInstance().apply {
        timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
```

  Check: `dueDatesInRange` starts on day 1 of `from`'s month. For a due day in that first month that falls before `from`, the date is filtered out by `due in from..to`. Correct.

- [ ] **Step 4: Run, pass. Step 5: commit** — `git commit -m "Make the cash-flow forecast cycle-aware"`.

### Task F3.4: Switch every call site to `BudgetPeriod`

**Files:** the call-site table above, plus `data/export/ExportRange.kt` (F2).

- [ ] **Dashboard.**
  - Inject `monthStartDay` as a flow: combine `MonthStartDay.flow(context)` into the existing `combine(...)`.
  - Replace `monthRangeForOffset(offset)` with `BudgetPeriod.forOffset(now, startDay, offset)`, and the previous month with `forOffset(…, offset - 1)`.
  - `prevDays = BudgetPeriod.daysInCycle(prev)`; `daysLeft = BudgetPeriod.daysLeft(now, startDay)`.
  - `billsStillDue = CashFlow.billsStillDue(bills, now, current)`; `emiStillDue = CashFlow.emiStillDue(loans, now, current)`.
  - Insights `dayOfMonth` → `BudgetPeriod.dayOfCycle(now, startDay)`. Weekday/weekend counting iterates the days from `current.start` to today.
  - `monthLabelFull(offset)` → `BudgetPeriod.label(range, startDay)`.
  - Upcoming bills (L131–138) keep calendar logic.
- [ ] **Statistics** (monthly view L251–276 and the monthly insight L352–356; auto-track "this month" L215–221) → `BudgetPeriod`. The annual view and the 12-month chart stay calendar-based.
- [ ] **Widget** `BudgetPulseRepository.load()`: current and previous ranges from `BudgetPeriod`; `dayOfMonth` → `dayOfCycle`; `daysInMonth` → `daysInCycle`; `monthLabel` → `label`.
- [ ] **Budget alerts** `BudgetAlertHelper`: `start`/`end` from `BudgetPeriod.current(now, MonthStartDay.get(context))`. Change `monthKey` to `"${SimpleDateFormat("yyyyMMdd", Locale.US).format(range.start)}_${budgetCategory.id}_${monthlyLimit}"`. Accepted side effect: on upgrade, each category may alert once more this cycle.
- [ ] **ExportRange:** `THIS_MONTH`/`LAST_MONTH` use `BudgetPeriod.current/forOffset(-1)` with `MonthStartDay`. Update `ExportRangeTest` with a start-day-25 case.
- [ ] **Regression test** (device or JVM, whichever fits): with start day 1, Dashboard totals for October equal today's `main` values. Use the existing mock data.
- [ ] `./gradlew assembleDebug testDebugUnitTest connectedDebugAndroidTest` → all green.
- [ ] **Commit** — `git commit -m "Use BudgetPeriod everywhere a budget month is computed"`.

### Task F3.5: Custom ranges in Statistics and Expenses

**Files:** create `ui/util/DateUtil.kt`; modify `ui/screens/statistics/StatisticsViewModel.kt` (`ViewMode` L15; monthly block L251–276), `StatisticsScreen.kt` (`ViewModeToggle` L495, header L105–134), `ui/screens/expenses/ExpensesScreen.kt` (`AdvancedFiltersDialog` L417), `ExpensesViewModel.kt` (`setDateRange` L108, `clearDateRange` L109).

**Interfaces — Produces:**

```kotlin
// ui/util/DateUtil.kt
/** Material date pickers return UTC midnight; convert to local day start / end. */
fun utcMidnightToLocalDayStart(utcMillis: Long): Long
fun utcMidnightToLocalDayEnd(utcMillis: Long): Long   // 23:59:59.999 local

// StatisticsViewModel
enum class ViewMode { MONTHLY, ANNUAL, RANGE }
sealed class StatsRange { data class LastMonths(val n: Int) : StatsRange(); object AllTime : StatsRange(); data class Custom(val start: Long, val end: Long) : StatsRange() }
fun setRange(range: StatsRange)   // also sets ViewMode.RANGE
```

- [ ] **Step 1: Failing tests** `DateUtilTest`: 2026-10-01 UTC midnight → 1 Oct 00:00 local start and 23:59:59.999 local end. (The test JVM uses the default zone; assert with `Calendar` fields, not raw millis.) Implement with `Calendar.getInstance(TimeZone.getTimeZone("UTC"))` to read Y/M/D, then set them on a local `Calendar`.

- [ ] **Step 2: ViewModel refactor.**
  - Extract the monthly-view computation (L251–276) into `private fun periodStats(expenses, categories, range: DateRange, previous: DateRange?): PeriodStats`. `PeriodStats` holds exactly the fields the monthly UI already reads.
  - MONTHLY calls it with the current cycle and the previous cycle.
  - RANGE calls it with:
    - `LastMonths(n)` → `BudgetPeriod.lastNMonths`, previous = the same length just before;
    - `AllTime` → from the earliest expense to now, previous = null;
    - `Custom` → as given, previous = the same length just before.
  - **Write a JVM test first** for a pure helper `previousRangeOfSameLength(range: DateRange): DateRange`.

- [ ] **Step 3: UI.**
  - `ViewModeToggle` gets a third pill, **"Range"**.
  - Tapping it opens a bottom sheet with "Last 3 months", "Last 6 months", "All time" and "Choose dates". "Choose dates" uses the Material 3 `DateRangePicker` (`rememberDateRangePickerState()`); convert with `DateUtil`.
  - The header shows the range label, and the arrows are hidden in RANGE mode. With no previous range (all time) the comparison shows nothing.

- [ ] **Step 4: Expenses date filter.**
  - In `AdvancedFiltersDialog` add a **"Dates"** row: the same presets as F2 plus "Choose dates" → `viewModel.setDateRange(start, end)`.
  - Show an active-filter chip "1 Oct – 13 Oct ✕" above the list; ✕ calls `clearDateRange()`.
  - The VM already filters on `_dateRange` (L30, L69).

- [ ] **Step 5: Emulator check.**
  1. Start day 25: on 24 Oct the Dashboard shows "25 Sep – 24 Oct"; on 25 Oct a new cycle starts.
  2. The forecast includes a bill due on the 5th of next month.
  3. The widget total equals the Dashboard total.
  4. Statistics "Choose dates" 1–13 Oct total equals a DB query (`/tmp/dbq.sh`-style read).
  5. Expenses date chip filters and clears.
  6. Start day 1: everything identical to `main` (screenshot comparison).
- [ ] **Step 6: Commit and PR** — "Salary-cycle months, custom date ranges, more statistics views". Update `NOTES.md`.

---

# F4: Income entries (schema v14, backup v3)

**Branch:** `feat/income`. **Effort:** ~2 weeks. **Depends on F3** (cycle-aware forecast).

**Why:** MyMoney's core is income + expense records; recurring salary needs income; today "monthly income" is only a number (`PreferencesManager.monthlyIncome`, key `monthly_income`, Double stored as Long bits).

**Decision (fixed):** a separate `incomes` table plus a `recurring_incomes` table. Every existing query, sum, budget and alert keeps meaning "spending".

**What exists:**
- `AppDatabase` version 13, `exportSchema = false`, 12 migrations (`MIGRATION_1_2` … `MIGRATION_12_13`), built in `getInstance` (L156–165).
- `BackupCodec.CURRENT_VERSION = 2`.
- `RecurringManager.applyIfNeeded` handles auto-log bills (calendar due days, `bill_applied_<Y>_<M>_<id>` pref plus a month-wide DB check).
- `DashboardViewModel` reads income from `_income = MutableStateFlow(prefs.monthlyIncome)` (L89). `IncomeDialog` is in `DashboardScreen.kt` (L991).
- `BudgetPulseRepository` uses `prefs.monthlyIncome` (L33).
- Bills UI: `BillsSubPage` (SettingsScreen L512).

### Task F4.1: Entities, DAOs, migration 13 → 14

**Files:**
- Create: `data/db/entity/Income.kt`, `data/db/entity/RecurringIncome.kt`, `data/db/dao/IncomeDao.kt`, `data/db/dao/RecurringIncomeDao.kt`, `data/repository/IncomeRepository.kt`
- Modify: `data/db/AppDatabase.kt`, `app/build.gradle.kts` (KSP room schema location, `room-testing`)
- Test: `app/src/androidTest/java/com/example/expensetracker/data/IncomeMigrationTest.kt`, plus the asset `app/src/androidTest/assets/v13_schema.sql`

**Interfaces — Produces:**

```kotlin
@Entity(tableName = "incomes")
data class Income(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val description: String,
    val date: Long = System.currentTimeMillis(),
    val source: String = "Manual",          // "Manual" | "Recurring"
    val tags: String = "",
    val recurringIncomeId: Long? = null,    // set when logged by RecurringManager
    val accountId: Long? = null             // reserved for the accounts feature; always null for now
)

@Entity(tableName = "recurring_incomes")
data class RecurringIncome(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                       // e.g. "Salary"
    val amount: Double,
    val dayOfMonth: Int,                    // calendar day 1–31, clamped like bills
    val isEnabled: Boolean = true
)

@Dao interface IncomeDao {
    @Query("SELECT * FROM incomes ORDER BY date DESC") fun getAll(): Flow<List<Income>>
    @Query("SELECT * FROM incomes") suspend fun getAllOnce(): List<Income>
    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM incomes WHERE date BETWEEN :start AND :end") suspend fun sumInRange(start: Long, end: Long): Double
    @Query("SELECT COUNT(*) FROM incomes WHERE recurringIncomeId = :recurringId AND date BETWEEN :start AND :end") suspend fun countRecurringInRange(recurringId: Long, start: Long, end: Long): Int
    @Insert suspend fun insert(income: Income): Long
    @Insert suspend fun insertAll(incomes: List<Income>)
    @Update suspend fun update(income: Income)
    @Delete suspend fun delete(income: Income)
    @Query("DELETE FROM incomes") suspend fun deleteAll()
}

@Dao interface RecurringIncomeDao {
    @Query("SELECT * FROM recurring_incomes ORDER BY dayOfMonth") fun getAll(): Flow<List<RecurringIncome>>
    @Query("SELECT * FROM recurring_incomes WHERE isEnabled = 1") suspend fun getEnabled(): List<RecurringIncome>
    @Insert suspend fun insert(r: RecurringIncome): Long
    @Insert suspend fun insertAll(r: List<RecurringIncome>)
    @Update suspend fun update(r: RecurringIncome)
    @Delete suspend fun delete(r: RecurringIncome)
    @Query("DELETE FROM recurring_incomes") suspend fun deleteAll()
}
```

`IncomeRepository(incomeDao, recurringDao, context)` exposes `allIncomes`, `allRecurring` and the mutations, and calls `WidgetUpdateHelper.update(context)` after writes (like `ExpenseRepository`).

- [ ] **Step 1: v13 schema fixture.**
  1. Install the current `main` build on the emulator and open it once.
  2. Pull `databases/expense_tracker.db` (+ `-wal`, `-shm`) with `adb shell "run-as com.example.expensetracker cat databases/expense_tracker.db"`.
  3. Run `sqlite3 v13.db .schema` and save the output as `app/src/androidTest/assets/v13_schema.sql`. Leave out `room_master_table`, `sqlite_sequence` and `android_metadata` (Android creates the last two itself, and re-creating them fails); **keep** the `CREATE INDEX` lines. This is how PR #1 tested the v2 → v13 path. There are no exported Room schemas before v14.

- [ ] **Step 2: Failing migration test**

```kotlin
@RunWith(AndroidJUnit4::class)
class IncomeMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    @Test fun v13DatabaseUpgradesWithDataIntact() {
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            context.assets.open("v13_schema.sql").bufferedReader().readText().split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { db.execSQL(it) }
            db.execSQL("INSERT INTO categories (name, emoji, colorHex, monthlyLimit, parentId) VALUES ('Food', '🍔', '#E57373', 0, NULL)")
            db.execSQL("INSERT INTO expenses (amount, description, categoryId, date, source, rawSms, tags, paymentMethod) VALUES (450, 'SWIGGY', 1, 1000, 'SMS', NULL, '', 'UPI')")
            db.version = 13
        }
        val room = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*AppDatabase.ALL_MIGRATIONS).build()
        runBlocking {
            assertEquals(1, room.expenseDao().getAll().first().size)
            room.incomeDao().insert(Income(amount = 50000.0, description = "Salary", date = 2000))
            assertEquals(50000.0, room.incomeDao().sumInRange(0, 3000), 0.0)
            room.recurringIncomeDao().insert(RecurringIncome(name = "Salary", amount = 50000.0, dayOfMonth = 1))
        }
        room.close()
    }
}
```

  (Opening runs the migrations and Room's schema validation; a wrong `CREATE TABLE` fails with "Migration didn't properly handle".)

- [ ] **Step 3: Run, fail.** Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.expensetracker.data.IncomeMigrationTest`. Expected: compile errors (no `Income`, no `ALL_MIGRATIONS`).

- [ ] **Step 4: Implement.**
  - Create the entities, DAOs and repository.
  - In `AppDatabase`: add the two entities, `version = 14`, `exportSchema = true`, and the `abstract fun incomeDao()` / `recurringIncomeDao()`.
  - Add `MIGRATION_13_14`:

```kotlin
private val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `incomes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount` REAL NOT NULL, `description` TEXT NOT NULL, `date` INTEGER NOT NULL, `source` TEXT NOT NULL, `tags` TEXT NOT NULL, `recurringIncomeId` INTEGER, `accountId` INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `recurring_incomes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `amount` REAL NOT NULL, `dayOfMonth` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL)")
    }
}
```

  - Expose `val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, …, MIGRATION_12_13, MIGRATION_13_14)` in the companion and use it in `getInstance`.
  - In `app/build.gradle.kts`: `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`; add `androidTestImplementation("androidx.room:room-testing:2.7.1")` (also in `libs.versions.toml`); commit the generated `app/schemas/.../14.json`.

- [ ] **Step 5: Run, pass.** Also run the full suites. **Commit** — `git commit -m "Add income tables (schema v14) with a migration test"`.

### Task F4.2: Recurring income in `RecurringManager`

**Files:** modify `data/recurring/RecurringManager.kt`; test (device) `app/src/androidTest/.../data/RecurringIncomeTest.kt`.

**Interfaces — Produces:** `RecurringManager.applyRecurringIncome(db: AppDatabase, prefs: SharedPreferences, now: Calendar)`, called from `applyIfNeeded`.

Rules, the same as auto-log bills:
- Log on the calendar day (clamped), dated midnight of that day, via `RecurringSchedule.dueDateIfReached(dayOfMonth, now)`.
- Skip if the pref `income_applied_<Y>_<M>_<id>` is set **or** `incomeDao().countRecurringInRange(id, monthStart, monthEnd) > 0`.
- Insert `Income(amount, description = name, date = dueDate, source = "Recurring", recurringIncomeId = id)`, then set the pref.

- [ ] **Step 1: Failing device test** (in-memory DB, the pattern of `CategoryDeletionTest`):
  - On the 16th, a salary on day 1 is logged once, dated the 1st.
  - Running twice still gives 1.
  - A salary on day 25 is not logged on the 16th.
  - A disabled one is not logged.

  Make the logic testable by extracting `suspend fun applyRecurringIncome(db, prefs, now)`.
- [ ] **Steps 2–4:** fail → implement → pass. **Step 5: commit** — `git commit -m "Log recurring income on its day"`.

### Task F4.3: Which income the forecast uses

**Files:** create `ui/screens/dashboard/IncomeForecast.kt`; test `IncomeForecastTest.kt`.

**Interfaces — Produces:** `IncomeForecast.forCycle(loggedThisCycle: Double, recurringStillDue: Double, expectedMonthly: Double): Double` and `IncomeForecast.recurringStillDue(recurring: List<RecurringIncome>, now: Long, cycle: DateRange): Double`.

Rule:
- If anything is logged or still expected from recurring income this cycle, the forecast uses `logged + recurringStillDue`.
- Otherwise it falls back to the "Expected monthly income" setting.
- So existing users see no change until they log income.

- [ ] **Step 1: Failing test**

```kotlin
class IncomeForecastTest {
    @Test fun `uses logged income when there is some`() = assertEquals(52000.0, IncomeForecast.forCycle(52000.0, 0.0, 40000.0), 0.0)
    @Test fun `adds recurring income still to come`() = assertEquals(55000.0, IncomeForecast.forCycle(5000.0, 50000.0, 40000.0), 0.0)
    @Test fun `falls back to the expected figure`() = assertEquals(40000.0, IncomeForecast.forCycle(0.0, 0.0, 40000.0), 0.0)
    @Test fun `nothing set means zero`() = assertEquals(0.0, IncomeForecast.forCycle(0.0, 0.0, 0.0), 0.0)
}
```

  Plus `recurringStillDue` cases (reuse `CashFlow.dueDatesInRange` with the reminder-bill rule: a due date from tomorrow's start, because today's is logged on open).
- [ ] **Steps 2–5:** implement → pass → commit (`git commit -m "IncomeForecast: logged and recurring income before the expected figure"`).

### Task F4.4: Screens

**Files:** modify `ui/screens/expenses/ExpensesScreen.kt`, `ExpensesViewModel.kt`, `ui/screens/dashboard/DashboardViewModel.kt`, `DashboardScreen.kt`, `ui/screens/statistics/StatisticsViewModel.kt`, `StatisticsScreen.kt`, `ui/screens/settings/SettingsScreen.kt` (`BillsSubPage` L512), `SettingsViewModel.kt`, `widget/BudgetPulseRepository.kt`, `ui/navigation/NavGraph.kt` (repository wiring), `data/export/CsvExporter.kt`.

- [ ] **Expenses screen.**
  - A segmented control at the top: **Spending | Income**.
  - **Income** lists incomes, grouped by day like expenses (reuse `groupByDay`), with green "+₹" amounts and Edit / Delete / Duplicate.
  - The FAB adds the selected type. `AddExpenseDialog` gets a type switch at the top. Income hides category and payment-method fields and keeps amount (with the calculator), description, tags and date.
  - `ExpensesViewModel` gains `incomes: StateFlow<List<Income>>`, `addIncome(...)`, `updateIncome(...)`, `deleteIncome(...)`, `duplicateIncome(...)`.
- [ ] **Dashboard.**
  - `DashboardUiState` gains `incomeThisCycle: Double`, `incomeIsLogged: Boolean`.
  - The forecast uses `IncomeForecast.forCycle(...)`.
  - The hero text says "x% of ₹… income" from the forecast income.
  - `IncomeDialog` becomes **"Expected monthly income"** with a hint: "Used until you log income. Add your salary as recurring income in Settings → Bills & Reminders."
- [ ] **Statistics.** The monthly and range views show "Income ₹X · Spent ₹Y · Saved ₹Z" (saved can be negative, shown in coral).
- [ ] **Settings.** `BillsSubPage` gets a **"Recurring income"** section with add/edit/delete dialogs (name, amount with the calculator, day 1–31, enabled). It mirrors `AddBillDialog` (L900) and `EditBillDialog` (L1006) without the category and reminder fields. Reset everything also deletes incomes and recurring incomes. Mock data (debug only) adds a "Salary" recurring income.
- [ ] **Widget.** `BudgetPulseRepository` uses the same forecast income rule.
- [ ] **CSV.** `CsvExporter.expensesToCsv(expenses, categories, incomes = emptyList(), zone)` adds income rows with `type = income`, an empty category and payment method, and `source`. Add a test case first.
- [ ] Build plus all tests. **Commit** — `git commit -m "Income screens, recurring income settings, forecast and CSV"`.

### Task F4.5: Backup v3

**Files:** modify `data/backup/BackupData.kt`, `data/backup/BackupManager.kt`; tests `BackupCodecTest` (JVM) and `BackupRestoreTest` (device).

- [ ] **Step 1: Failing tests.**
  - v3 round trip with `incomes` and `recurringIncomes`.
  - A **v2 backup still restores** and leaves the phone's incomes alone (they're "missing sections").
  - A v4 backup is refused.
  - Device: restore an income backup in one transaction; a restore that fails halfway leaves incomes unchanged.
- [ ] **Step 2: Implement.**
  - `CURRENT_VERSION = 3`.
  - Add `incomes: List<Income>? = null` and `recurringIncomes: List<RecurringIncome>? = null` to `BackupData`; extend `missingSections` and `problem()` (null and non-finite amounts).
  - `snapshot()` includes both.
  - `restore()` deletes and re-inserts each non-null section inside the existing `withTransaction`.
- [ ] **Step 3:** Pass. **Commit** — `git commit -m "Backup format v3 with income"`.

### Task F4.6: Emulator check and PR

- [ ] **Upgrade:** install `main` with mock data, then install this branch over it → the app opens, data intact, income = expected figure, forecast unchanged.
- [ ] Add a recurring Salary ₹50,000 on day 1 → it is logged as income dated the 1st. The forecast uses ₹50,000 + extra logged income; the expected figure is ignored.
- [ ] Log a freelance income → the Income list, Statistics "Saved" and the CSV `type=income` row are all correct.
- [ ] Back up → reset → restore → incomes back. Restore an old v2 file → incomes untouched.
- [ ] Screenshots. Update `NOTES.md` (DB v14, backup v3). PR "Income entries and recurring salary".

---

# F5: Subscription price-change alerts

**Branch:** `feat/subscription-price-alerts`. **Effort:** 3–5 days. **No schema change.** Can be done any time after F1 (it uses `LaunchAction`).

**Why:** subscription and AutoPay price creep is Rocket Money's core value, and no Indian SMS tracker owns it. Planned as Pro later; built **ungated** now.

**What exists** (`data/subscriptions/SubscriptionDetector.kt`):
- `Subscription(key, name, amount /* most recent */, cadence, lastPaid, nextExpected, payments, categoryId)`, with `yearlyCost`.
- `detect(expenses, now, dismissedKeys, billNames)`:
  1. groups by `keyFor(description)`;
  2. `clusterByAmount` with `AMOUNT_TOLERANCE = 0.10` (relative to the cluster's first amount after sorting by amount);
  3. `fit` (merge within `SAME_PAYMENT_DAYS = 2`; cadence gaps; `minPayments`; 2-payment cadences need `STRICT_AMOUNT_TOLERANCE = 0.02`);
  4. drops items with `now > next + GRACE_DAYS (10)`;
  5. keeps the cluster with the most payments.
- **Price changes are not tracked**: a rise under 10% just shows the latest price; a rise over 10% splits into another cluster and is lost.
- Detection runs **only in view-models** (`DashboardViewModel` L194–196, `SubscriptionsViewModel` L36–40).
- Channels today: `expense_categorization`, `budget_alerts`, `bill_reminders`.
- Tests: `SubscriptionDetectorTest` (12 tests; helpers `date(y,m,d)`, `pay(desc, amount, y, m, d, source="SMS", cat=4)`, `monthly(desc, amount, day, months=7..10)`, `now = date(2026,10,16)`).

### Task F5.1: Detect price changes

**Files:** modify `data/subscriptions/SubscriptionDetector.kt`; extend `app/src/test/.../data/subscriptions/SubscriptionDetectorTest.kt`.

**Interfaces — Produces:** `Subscription` gains `val previousAmount: Double? = null` and `val priceChangedOn: Long? = null`, plus a computed `val priceChange: Double? get() = previousAmount?.let { amount - it }`.

Algorithm (add after the best cluster for a key is chosen):
1. **Within the cluster:** sort its merged payments by date. If the latest amount differs from the one before by **more than ₹1 and more than 0.5%**, set `previousAmount` to the earlier amount and `priceChangedOn` to the latest date.
2. **Across clusters (rises or drops over 10%):**
   - Among this key's payments **not** in the cluster, take the earliest one dated after the cluster's last payment whose gap from it fits the cadence (`minGapDays..maxGapDays`). Treat it as the new price.
   - Repeat for any later payments at cadence with the same new amount (±2%).
   - Then set `amount` = the new price, `lastPaid` = the latest of those, `nextExpected` from there, `payments` += how many, `previousAmount` = the cluster's last amount, `priceChangedOn` = the first new-price date.
3. A payment from the same payee **off cadence** (e.g. a one-off ₹299 purchase 10 days after the monthly ₹99) never counts as a price change.

- [ ] **Step 1: Failing tests** (add to `SubscriptionDetectorTest`; adapt the helper calls to the file's exact signatures):

```kotlin
@Test fun `a small rise is reported as a price change`() {
    val subs = SubscriptionDetector.detect(monthly("NETFLIX", 499.0, 5, months = 6..9) + pay("NETFLIX", 549.0, 2026, 10, 5), now)
    val s = subs.single()
    assertEquals(549.0, s.amount, 0.0); assertEquals(499.0, s.previousAmount!!, 0.0); assertEquals(date(2026, 10, 5), s.priceChangedOn)
}
@Test fun `a big rise keeps the subscription and reports the change`() {
    val subs = SubscriptionDetector.detect(monthly("SPOTIFY", 119.0, 3, months = 5..9) + pay("SPOTIFY", 199.0, 2026, 10, 3), now)
    val s = subs.single()
    assertEquals(199.0, s.amount, 0.0); assertEquals(119.0, s.previousAmount!!, 0.0)
}
@Test fun `a price drop is reported too`() {
    val s = SubscriptionDetector.detect(monthly("HOTSTAR", 649.0, 8, months = 6..9) + pay("HOTSTAR", 499.0, 2026, 10, 8), now).single()
    assertEquals(-150.0, s.priceChange!!, 0.0)
}
@Test fun `a steady price has no change`() = assertNull(SubscriptionDetector.detect(monthly("NETFLIX", 499.0, 5, months = 6..10), now).single().previousAmount)
@Test fun `an off-cadence purchase from the same payee is not a price change`() {
    val s = SubscriptionDetector.detect(monthly("APPLE", 99.0, 10, months = 6..9) + pay("APPLE", 299.0, 2026, 9, 20), now).single()
    assertEquals(99.0, s.amount, 0.0); assertNull(s.previousAmount)
}
```

- [ ] **Step 2: Run, fail. Step 3: implement as above** (keep it inside the detector; the existing 12 tests must stay green). **Step 4: pass. Step 5: commit** — `git commit -m "Detect subscription price changes"`.

### Task F5.2: Alert once when the new price is captured

**Files:**
- Create: `notification/PriceChangeAlerts.kt`
- Modify: `ExpenseTrackerApp.kt` (create the channel), `sms/SmsReceiver.kt` and `notification/TransactionNotificationListener.kt` (after insert), `ui/screens/expenses/ExpensesViewModel.kt` (`addExpense`), `data/db/dao/ExpenseDao.kt`, `data/preferences/PreferencesManager.kt`, `ui/screens/settings/SettingsScreen.kt` (Preferences toggle)
- Test: `app/src/test/.../notification/PriceChangeAlertsTest.kt`

**Interfaces — Produces:**

```kotlin
object PriceChangeAlerts {
    const val CHANNEL_ID = "subscription_alerts"
    fun createChannel(context: Context)
    /** Pure: the alert text for a change, e.g. "Netflix price went up" / "₹499 → ₹549 a month · ₹600 more a year". */
    fun text(s: Subscription): Pair<String, String>
    /** Pure: the dedupe key for one change. */
    fun alertKey(s: Subscription): String = "price_alert_${s.key}_${s.priceChangedOn}"
    /** The single entry point (paywall gating goes here later). Call after a new expense is saved. */
    suspend fun checkAfterCapture(context: Context, db: AppDatabase, expense: Expense)
}
```

Add `@Query("SELECT * FROM expenses WHERE date >= :since") suspend fun getSince(since: Long): List<Expense>` to `ExpenseDao`, and to `PreferencesManager`: `priceAlertsEnabled: Boolean` (key `price_alerts_enabled`, default `true`).

`checkAfterCapture`:
1. Return if `!prefs.priceAlertsEnabled` or `expense.amount <= 0`.
2. Load `getSince(now - 400 days)`, the dismissed keys (`DismissedSubscriptions`), and bill names (`billDao`).
3. Run `detect(...)`; find `s` with `s.key == SubscriptionDetector.keyFor(expense.description)`.
4. If `s?.previousAmount != null && s.priceChangedOn` is within 2 days of `expense.date`, and `alertKey(s)` hasn't been stored in `expense_tracker_prefs`:
   - post a notification on `CHANNEL_ID` (id = `(300_000 + s.key.hashCode()) and 0x7FFFFFFF`);
   - its content intent opens `MainActivity` with `LaunchAction.EXTRA_ACTION = LaunchAction.OPEN_SUBSCRIPTIONS` (request code from the same id, `FLAG_IMMUTABLE`);
   - store the key.

- [ ] **Step 1: Failing tests** for `text()`:
  - A rise: `"Netflix price went up"`, `"₹499 → ₹549 a month · ₹600 more a year"`.
  - A drop: `"Hotstar price went down"`, `"₹649 → ₹499 a month · ₹1,800 less a year"`.
  - Yearly cadence wording `"a year"`.
  - Names are title-cased from the key; amounts use Indian grouping (`NumberFormat.getNumberInstance(Locale("en", "IN"))`, no decimals unless needed).
  - Also: `alertKey` differs for two different change dates.
- [ ] **Step 2: Run, fail. Step 3: implement.**
  - Create the channel in `ExpenseTrackerApp.onCreate` next to the others ("Subscription price changes", `IMPORTANCE_DEFAULT`).
  - Call `PriceChangeAlerts.checkAfterCapture(...)` right after the insert in both capture paths, wrapped in `runCatching`, and after `addExpense`/`duplicateExpense` insert.
  - Add a Preferences toggle **"Subscription price alerts"** ("Tell me when a subscription's price changes").
- [ ] **Step 4: Pass, build. Step 5: commit** — `git commit -m "Notify once when a subscription's price changes"`.

### Task F5.3: Show it in the app

**Files:** `ui/screens/subscriptions/SubscriptionsScreen.kt` (`SubscriptionCard` L99), `ui/screens/dashboard/DashboardScreen.kt` (Overview card L546–580).

- [ ] `SubscriptionCard`: when `s.previousAmount != null`, show a chip **"↑ ₹50 since 5 Oct"** (coral for a rise, jade for a drop, using `formatShortDate`). The yearly cost uses the new amount.
- [ ] Overview subscriptions card: a small "price up" marker on rows with a rise.
- [ ] `MainActivity`/`NavGraph`: `LaunchAction.OpenSubscriptions` already navigates (F1 Task 1.4). Verify that tapping the notification lands on Subscriptions.
- [ ] **Commit** — `git commit -m "Show subscription price changes on the cards"`.

### Task F5.4: Emulator check and PR

- [ ] Seed four monthly NETFLIX ₹499 SMS-source expenses (Jun–Sep, day 5) with a DB inject. Recipe: stop the app; copy the DB and its `-wal`/`-shm` with `run-as … cat`; in `sqlite3` run `PRAGMA wal_checkpoint(TRUNCATE)`, insert, then `PRAGMA journal_mode=DELETE`; push back; remove the `-wal`/`-shm` files.
- [ ] Send an SMS for the October charge: `adb emu sms send JD-ICICIB "ICICI Bank Acct XX123 debited for Rs 549.00 on 05-Oct-26; NETFLIX credited. UPI:412345678901."` → the notification "Netflix price went up / ₹499 → ₹549 a month · ₹600 more a year" appears once. Tapping it opens Subscriptions with the chip "↑ ₹50 since 5 Oct".
- [ ] Send the same SMS again (a different UPI ref) → no second alert.
- [ ] Turn the toggle off → no alert for a new change.
- [ ] A ₹299 one-off NETFLIX purchase on the 15th → no alert.
- [ ] Screenshots. Update `NOTES.md`. PR "Subscription price-change alerts".

---

# Working on this repo (for the implementing session)

- **Repo:** `~/Desktop/ExpenseTracker` → https://github.com/Rohanth7/ExpenseTracker (`main`).
- **Build and test** with Android Studio's bundled JDK:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew assembleDebug testDebugUnitTest            # JVM tests (92 today)
./gradlew connectedDebugAndroidTest                  # device tests (11 today); needs the emulator; uninstalls the app afterwards
```

- **Emulator:** start it with these flags (the saved AVD is 1 core + software graphics and crashes `system_server`):

```bash
nohup ~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_37.0 -cores 4 -gpu host -memory 4096 -no-boot-anim > /tmp/emulator.log 2>&1 & disown
A=~/Library/Android/sdk/platform-tools/adb; $A wait-for-device; $A shell svc power stayon true
```

- **Useful adb:**
  - Grant permissions: `$A shell pm grant com.example.expensetracker android.permission.{POST_NOTIFICATIONS,RECEIVE_SMS,READ_SMS}`.
  - Fake SMS: `$A emu sms send <sender> "<text>"`.
  - Read the DB: copy `databases/expense_tracker.db` + `-wal` + `-shm` via `$A shell "run-as com.example.expensetracker cat …"`, then use `sqlite3` locally.
  - Notifications: `$A shell dumpsys notification --noredact | grep -A40 pkg=com.example.expensetracker`.
  - If adb shows `offline`: `$A kill-server && $A start-server`.
- **GitHub:**
  - The active `gh` account may be `sairohanth`, which has no push access.
  - Push with the Rohanth7 token: `TOKEN=$(gh auth token -u Rohanth7); git -c credential.helper= -c "credential.helper=!f() { echo username=x-access-token; echo password=$TOKEN; }; f" push -u origin <branch>`.
  - Open PRs with `GH_TOKEN=$TOKEN gh pr create -R Rohanth7/ExpenseTracker --base main ...`.
- **Commits and PR bodies:**
  - Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
  - PR bodies end with `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.
  - PR bodies follow the earlier PRs: the bugs/why, what changes, tests (with counts), an emulator results table (`main` vs this PR), and "Not covered".
- **Merge:** squash-merge after the owner approves; then `git checkout main && git pull`.

## Self-review notes

- **Coverage:**
  - F1: calculator, optional category, last category, duplicate, labelled FAB, widget quick-add.
  - F2: CSV with range presets, restore offer during setup.
  - F3: `BudgetPeriod`, setting + backup, cycle-aware forecast, all call sites, Statistics ranges, Expenses date filter.
  - F4: tables + migration test, recurring income, forecast rule, screens, CSV rows, backup v3.
  - F5: detection, one-time alert on capture, UI, setting.
- **Consistent names:** `DateRange` (F3) is used by `CashFlow`, `IncomeForecast` and Statistics. `LaunchAction` (F1) is used by F5. `CsvExporter.HEADER` is fixed in F2 and F4 only adds rows. `AppDatabase.ALL_MIGRATIONS` is introduced in F4.1.
- **Known trade-offs:**
  - Budget alerts may fire once more per category in the first cycle after F3 (new key format).
  - The annual Statistics view stays on calendar months.
  - Price-change detection needs at least the cadence's minimum payments at the old price.
