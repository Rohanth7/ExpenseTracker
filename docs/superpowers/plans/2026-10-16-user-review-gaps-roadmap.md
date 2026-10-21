# User-Review Gaps Roadmap (from the MyMoney/Raha teardown)

> **For agentic workers:** this is a **roadmap of independent PRs**, not a single task list. Before starting a PR, write its detailed task plan (failing test → code → passing test → commit, with exact files and code) as `docs/superpowers/plans/<date>-<pr-slug>.md` using superpowers:writing-plans, then execute it with superpowers:subagent-driven-development or superpowers:executing-plans.

**Goal:** Close the gaps that users of the 10L+ "MyMoney" app (Ananta Raha) complain about or request, so our app beats it for Indian users. Evidence: `docs/mymoney-raha-teardown.md` (11,237 reviews) and `docs/competitor-study.md` §5a.

**Architecture:** Eight PRs (themes last, with the planned redesign), each shippable and testable on its own, ordered so that no PR depends on unmerged work. Three PRs change the Room schema (income, accounts, IOUs); they go in sequence, each with one migration and a backup-format bump. Everything else is schema-free.

**Tech stack:** Kotlin, Jetpack Compose (Material 3), Room 2.7, WorkManager, Glance/RemoteViews widget, JUnit (JVM) + AndroidX instrumented tests on the "Medium Phone API 37.0" emulator.

## Global constraints

- Local-first: no account, no backend, no ads, no loans. Nothing new leaves the phone.
- PRs 1–8 are **free**. Only the Pro features listed under PR 9 are paid (after a 14-day reverse trial). Never paywall anything that was free (study §5d).
- TDD: write the failing test first and watch it fail for the right reason (as in PRs #6 and #7).
- Every PR is checked on the emulator against `main` before it opens, with screenshots, as in earlier PRs. Emulator start: `~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_37.0 -cores 4 -gpu host -memory 4096 -no-boot-anim`.
- Schema changes: one Room migration per PR (`AppDatabase` is at **version 13**), plus a migration test. The first schema PR (PR 4) turns on `exportSchema = true` and adds `MigrationTestHelper` tests.
- Backup: any new table or column goes into `BackupData` with a format bump (`BackupCodec.CURRENT_VERSION` is **2**). Older backups still restore, and their missing sections are left alone (the rule from PR #4).
- Never store raw SMS longer than 7 days (PR #7). New SMS parsing reads text at capture time only.
- **Coordination:** another session has been merging into this repo (PRs #8, #9). Before each PR: `git fetch && git checkout main && git pull`, branch from the latest `main`, and keep each PR inside its listed files where possible.
- Update `NOTES.md` (features, merged PRs, test counts) in each PR.

## File map (shared pieces introduced here)

| New file | Responsibility | Introduced in |
|---|---|---|
| `data/period/BudgetPeriod.kt` | The one place that answers "which date range is 'this month'/'last month'/'this cycle'", given the month-start day | PR 3 |
| `ui/util/AmountCalculator.kt` | Evaluate "120+45*2" style input safely (no `eval`) | PR 1 |
| `data/export/CsvExporter.kt` | Write expenses (and later income, accounts, transfers) to CSV | PR 2 |
| `data/db/entity/Income.kt` + `IncomeDao.kt` | Income records | PR 4 |
| `data/db/entity/Account.kt`, `Transfer.kt` + DAOs | Accounts, balances, transfers | PR 5 |
| `data/db/entity/Debt.kt` + `DebtDao.kt` | Lent to / borrowed from a person | PR 6 |
| `ui/theme/Palette.kt` | Dark / Light / AMOLED colour sets behind a CompositionLocal | PR 7 |
| `data/import/MyMoneyCsvImporter.kt`, `CsvImport.kt` | Import a MyMoney (Raha) CSV export, and generic CSV with column mapping | PR 8 |

Paths are under `app/src/main/java/com/example/expensetracker/`.

---

## PR 1: Faster entry (small wins)

**Why:** "Entering every spend is boring" and "too many taps" are ~5–7% of MyMoney's 1–3★ reviews. Duplicate record was its most-thanked 2026 feature. Our own audit rates manual entry 3/5.

**Scope:**
1. **Labelled add button:** the Expenses FAB gets text "+ Add expense" and a content description (it currently has none).
2. **Category optional:** saving without a category files the expense as pending (`categoryId = -1`) instead of blocking. Manual add currently requires a category (`ExpensesScreen.kt`, add-expense dialog).
3. **Remember last category** for manual entry (a preference, used as the dialog's default).
4. **Calculator on the amount field:** accept `+ - × ÷` and evaluate on save ("120+45" → 165). New `AmountCalculator.evaluate(text): Double?`, with no `eval` and no scripting engine; a small recursive-descent parser.
5. **Duplicate an expense:** a "Duplicate" action on the expense edit screen. It creates a copy dated now, with `source = "Manual"` and no raw text.
6. **Widget quick-add:** a "+" on the Budget Pulse widget that opens the add-expense dialog directly (deep-link intent extra handled in `MainActivity`/`NavGraph`).

**Files:** `ui/screens/expenses/ExpensesScreen.kt`, `ExpensesViewModel.kt`, `ui/util/AmountCalculator.kt` (new), `data/preferences/PreferencesManager.kt`, `widget/BudgetPulseWidget.kt` (+ its layout), `MainActivity.kt`, `ui/navigation/NavGraph.kt`.

**Tests:**
- JVM `AmountCalculatorTest`: precedence, decimals, Indian commas ("1,200+300"), division by zero → null, garbage → null, a bare number unchanged.
- JVM test for the "duplicate" copy rules (pure function `Expense.duplicateForNow(now)`).

**Emulator check:** add with no category → appears as pending; "120+45" saves 165; Duplicate creates a second row dated now; the widget "+" opens the add dialog; TalkBack reads the FAB.

**Effort:** S–M (3–5 days). **No schema change.**

---

## PR 2: CSV export (free)

**Why:** "export no longer works" is a top axio complaint; MyMoney (Raha) has free CSV export and users rely on it; Money Lover paywalls it. It is a trust feature and must stay free (study §5d).

**Scope:**
- Settings → Data & Backup → **Export CSV**, with a date-range choice (this month / last month / this year / all / custom).
- Columns: `date, amount, description, category, subcategory, payment method, tags, source`. ISO dates (`yyyy-MM-dd`), plain numbers (no ₹, no thousands separators), UTF-8 with a BOM so Excel opens it correctly. Text is quoted per RFC 4180 (commas, quotes, newlines).
- Written through the Storage Access Framework, like the JSON export.
- Later PRs add `type` (expense/income) and `account` columns. The exporter is written so that adding columns is one line each.
- **Restore offer on a new phone:** the onboarding welcome screen gets "Moving from another phone? Restore a backup" (file restore now; Drive once the OAuth client is registered). MyMoney's #2 complaint is data lost on a phone change.

**Files:** `data/export/CsvExporter.kt` (new), `ui/screens/settings/SettingsScreen.kt`, `SettingsViewModel.kt`.

**Tests:** JVM `CsvExporterTest`: quoting of commas, quotes and newlines; BOM present; ISO dates in local time; subcategory columns; empty data writes only the header.

**Emulator check:** export, pull the file, open it with Python's `csv` module and check the rows match the database.

**Effort:** S (1–2 days). **No schema change.**

---

## PR 3: Salary-cycle months, custom date ranges, more views

**Why:** "my salary cycle is 25th to 24th" (~2–5% of requests); custom date range and 3/6-month/all-time views (~2%), which MyMoney keeps in Pro.

**Scope:**
1. **`BudgetPeriod`**, one shared helper:
   ```kotlin
   data class DateRange(val start: Long, val endInclusive: Long)
   object BudgetPeriod {
       /** The cycle containing [now]. monthStartDay 1..28; 1 = calendar month. */
       fun current(now: Long, monthStartDay: Int): DateRange
       /** offset 0 = current cycle, -1 = previous, … */
       fun forOffset(now: Long, monthStartDay: Int, offset: Int): DateRange
       fun lastNMonths(now: Long, monthStartDay: Int, n: Int): DateRange
       fun daysLeft(now: Long, monthStartDay: Int): Int
   }
   ```
   Days 29–31 are not offered, because they don't exist in every month. Ends use `23:59:59.999`, the existing "don't regress" rule.
2. **Replace the 7 hand-written month ranges** with `BudgetPeriod`: `DashboardViewModel` (`monthRangeForOffset`, days-left, forecast), `StatisticsViewModel`, `SettingsScreen` (category spent), `widget/BudgetPulseRepository`, `ui/widgets/ExpenseWidget`, `notification/BudgetAlertHelper` (alert month key), `data/recurring/RecurringManager` (month of auto-log).
3. **Setting:** Preferences → "Month starts on" (1–28, default 1), explained as "e.g. the day your salary arrives".
4. **Statistics:** add 3-month, 6-month, all-time and **custom range** (date-range picker) to the existing monthly/yearly toggle. The `ExpensesViewModel` date-range filter already exists in code but has no UI; wire a range chip into Expenses search too.

**Files:** `data/period/BudgetPeriod.kt` (new) and the 7 call sites above, plus `PreferencesManager.kt`, `StatisticsScreen.kt`, `StatisticsViewModel.kt`, `ExpensesScreen.kt`.

**Tests:**
- JVM `BudgetPeriodTest`: start day 1 (calendar month); start 25 when today is the 24th, 25th and 26th; February with start 28; the year boundary (25 Dec → 24 Jan); `daysLeft`; `lastNMonths`.
- Existing `CashFlowTest`/`RecurringScheduleTest` stay green; add one test each for a non-1 start day.
- Budget alerts: a key test showing a cycle change re-arms alerts.

**Emulator check:** set start day 25 → Dashboard "this month" and the forecast run 25th–24th; Statistics custom range 1–13 Oct matches a DB query; the widget shows the same total as the Dashboard.

**Effort:** M (1–2 weeks); most of it is careful replacement at 7 call sites. **No schema change.**

---

## PR 4: Income entries (schema v14, backup v3)

**Why:** MyMoney's core is income + expense records; recurring salary needs income; our "monthly income" is only a number in Settings.

**Design decision (recommended): a separate `incomes` table, not a `type` column on `expenses`.** Every existing query, sum, budget and alert assumes `expenses` rows are spending. A type column would mean auditing every query, and one missed query would silently break budgets. A separate table keeps the existing paths untouched.

**Scope:**
- `Income(id, amount, description, date, source = "Manual", tags, isRecurring, accountId = null)`. `accountId` is reserved for PR 5; add it now so PR 5 doesn't need a second migration.
- `MIGRATION_13_14` creates `incomes`. Turn on `exportSchema = true` and add a `MigrationTestHelper` test (13 → 14), using the v2-fixture approach from PR #1 as the model.
- **Recurring income** (salary): reuse the bill machinery. A `Bill` with a new `isIncome` flag would touch bills; instead use a small `RecurringIncome` handled by `RecurringManager` with the same "log on due day, dated that day, month-wide duplicate check" rules from PR #6.
- **Monthly income setting becomes "expected income".** The forecast uses *logged income this cycle* if there is any, otherwise the expected figure (so nothing changes for existing users).
- UI: an "Income" option in the add sheet; income rows in Expenses (green, "+₹"); Statistics shows income vs spend per cycle.
- Backup v3 adds `incomes`. A v2 backup restores and leaves incomes alone. CSV export gains `type = income` rows.
- **Not in scope:** auto-capturing salary credits from SMS (credits are filtered out today). That would be a later, opt-in step.

**Tests:**
- Migration test 13 → 14 (existing data intact, table created).
- JVM: forecast uses logged income when present, expected income otherwise.
- `BackupCodecTest`: v3 round trip; v2 still restores.
- Device: recurring salary logs once on its day.

**Effort:** M–L (2 weeks).

---

## PR 5: Accounts, balances, transfers and credit cards (schema v15, backup v4)

**Why:** MyMoney users rely on accounts and transfers, and its balance maths is its most fragile area (a card-payment bug review with 96 upvotes). Bishinews double-counts card bills. This is our biggest gap (study rank 11).

**Design:**
- `Account(id, name, kind = BANK | CARD | CASH | WALLET, openingBalance, openingDate, last4 = null, cardDueDay = null, statementDay = null, isArchived)`.
- `expenses.accountId` (nullable; null = "unassigned") and `incomes.accountId` (column added in PR 4).
- `Transfer(id, fromAccountId, toAccountId, amount, date, note)`.
- **Balance = opening + incomes − expenses ± transfers after `openingDate`.** It is calculated, never stored. Future-dated entries don't count until their date (a MyMoney bug).
- **Credit-card bill payment is a transfer** (bank → card), never spending. That fixes the double-counting. Card spends reduce the card's balance (a liability shown as "due").
- **Auto-assign from SMS:** match `XX1234` / "card ending 1234" in the captured text to `Account.last4` at capture time. Card-payment SMS ("payment received towards your card") create a transfer instead of an expense.
- `paymentMethod` stays as it is (UPI / Credit Card / Cash) for compatibility.
- `MIGRATION_14_15` adds `accounts`, `transfers` and `expenses.accountId`.

**UI:** an Accounts screen (list with balances, add/edit, archive); account picker in add/edit (defaults to the last used); a transfer action; card view with "due", statement day and due date; an optional Dashboard card "Balances".

**Tests:**
- JVM `BalanceCalculatorTest`: opening balance; future-dated entries ignored; transfers both sides; card payment doesn't change total spend; archived accounts excluded from the net total.
- JVM parser tests: `last4` extraction from the bank formats in `SmsParserTest`; card-payment SMS → transfer.
- Migration test 14 → 15. Backup v4 round trip and v3 compatibility.

**Effort:** L (3–4 weeks). This should become **two PRs**: 5a accounts + balances + transfers (manual), 5b SMS auto-assign + card bill-as-transfer.

---

## PR 6: Lend / borrow with people (schema v16, backup v5)

**Why:** ~3% of MyMoney requests; our loans/EMIs only cover bank loans.

**Scope:** `Debt(id, person, direction = LENT | BORROWED, amount, date, dueDate = null, settledAmount, note)`; a "People" list with who owes whom and net per person; partial settlements; an optional reminder on the due date (reuse `BillReminderHelper`'s WorkManager pattern). Lending money is **not** counted as spending, and getting it back is not income. Optionally links to an account (a transfer-like movement) once PR 5 exists.

**Tests:** JVM net-per-person maths and settlement edge cases (overpay, zero); migration 15 → 16; backup round trip.

**Effort:** S–M (1 week).

---

## PR 7: Light, system and AMOLED-black themes (last, with the redesign)

**Timing:** the owner is planning a new app design, so themes are done **last, as part of that redesign**. The new design system should define Dark, Light and Black palettes from the start, rather than retrofitting today's colours. Until then, PRs 1–8 reuse existing components and colours and avoid visual polish that the redesign would replace.

**Why:** ~7% of MyMoney requests ask for themes or true black; dark-only is also an accessibility gap.

**Design (avoids touching ~910 call sites):** keep the names in `ui/theme/Color.kt` (`Canvas`, `Paper`, `Ink`, `Muted`, `Jade`, …) but change each from a fixed `val` to a **composable getter that reads the current palette**:
```kotlin
val Canvas: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.canvas
```
`Palette.kt` defines `DarkPalette` (today's values), `LightPalette` and `AmoledPalette` (pure black `Canvas`/`Deep`, `Paper` at ~#121212). `ExpenseTrackerTheme(mode)` provides the palette and a matching Material `colorScheme`, and the system bars follow it. Any use outside a composable (widgets, notifications) becomes a compile error, so the compiler finds every one; those switch to the dark palette explicitly or follow the system setting. Setting: Preferences → Theme: System / Light / Dark / Black.

**Tests:** JVM contrast test that every text/background pair in each palette meets WCAG AA (≥ 4.5:1 for body text); screenshot check on the emulator for Overview, Expenses, Statistics and Settings in all three themes.

**Effort:** M (1–2 weeks), mostly picking light colours that work and fixing a few hard-coded `Color(0x…)` uses.

---

## PR 8: Import, including "Switch from MyMoney" (after PRs 4–5)

**Why:** MyMoney (Raha) users have no cloud and are locked in. A one-tap import is a switching path. A generic CSV import also covers bank statements (~3.5% of requests).

**Prerequisite (blocking):** a **real MyMoney CSV export**. Install the app on the emulator (needs a Google account on the emulator) or ask a user; record the exact columns, date format, how income/expense, accounts and transfers appear, and the encoding. Save a sanitised sample under `app/src/test/resources/import/mymoney-sample.csv`.

**Scope:** Settings → Data & Backup → **Import** → "From MyMoney" (fixed mapping) or "Other CSV" (pick the column for date/amount/description/category/type and save the mapping). Preview screen with counts and the first rows; duplicate detection against existing data (same date + amount + description); creates missing categories and accounts; everything in one transaction (like restore in PR #4).

**Tests:** JVM parser tests on the real sample and on edge cases (other date formats, negative amounts as income, BOM, quoted commas); device test that an import is atomic and re-importing the same file adds nothing.

**Effort:** M (1–2 weeks) once the sample exists.

---

## PR 9: Payments and Pro (pricing decided 18 Oct 2026, see `docs/competitor-study.md` §5c)

**Model:** free core forever + a 14-day reverse trial of Pro for every new user (no card) + one-time lifetime Pro (₹199 founder price for 60 days, then ₹299). No subscription, no ads, no hard trial wall.

### PR 9a: Payments plumbing + ₹99 Supporter (Phase 1, before launch)

**Scope:**
- Play Billing Library: one non-consumable `pro_lifetime` and one `supporter` product. `enablePendingPurchases(...enableOneTimeProducts())` for UPI; grant only on `PURCHASED`; acknowledge within 3 days; `queryPurchasesAsync` on billing connect and in `onResume`.
- `Entitlement` class: `isPro` = owns `pro_lifetime` or `supporter`, OR the trial is active. Cached locally so Pro works offline; **never downgrade a paying user because a check failed** (keep the last known state; re-check when online). Verify purchase signatures on device with the app's licence key.
- "Restore purchase" in Settings. Promo-code redemption handled through the same purchase listener.
- A simple "Support the app" screen selling the ₹99 Supporter, with its promise ("you'll get Pro free when it launches").
- `PlayBillingClient` behind an interface so tests use a fake.

**Tests:** JVM `EntitlementTest` (owned product → Pro; pending → not Pro; cached Pro survives an offline/failed check; refund → re-check removes it). Device test with Play's licence-tester accounts and static test product IDs (`android.test.purchased`, `android.test.canceled`) on the emulator.

**Effort:** M (1–2 weeks). Needs the final package ID and a Play Console app with the products created (Phase 1 blocker list).

### PR 9b: Pro launch: reverse trial, gating, paywall (end of Phase 2)

**Prerequisite:** at least three Pro features exist (PDF/Excel reports, advanced rules engine, extra themes/widgets).

**Scope:**
- **Reverse trial:** `TrialClock` records the first-run time in Google **Block Store** (`setShouldBackupToCloud(true)`) and in local storage; on start it reads the earliest. 14 days of full Pro; day 15 back to free. Existing users get their 14 days on the day Pro launches. Disclose it in the listing and the privacy policy / Data safety form.
- **Gating:** one `ProFeature` enum and `requirePro(feature)` check; anything created with Pro stays visible after the trial (only creating new Pro items locks).
- **Paywall moments:** onboarding explainer (skippable), trial end, first month-end report, tapping a Pro feature (preview), and a one-time discount after dismissal. Live `formattedPrice` from Play, never hard-coded. English and Hindi copy.
- **Founder price:** a Play Discount offer on `pro_lifetime` with real start/end dates (60 days); the in-app countdown uses the same dates.
- **Supporter:** withdrawn from sale the same day; supporters already count as Pro.
- After launch: Play price experiment ₹149 / ₹299 / ₹399; apply to Play Pass once ratings are good.

**Tests:** JVM `TrialClockTest` (earliest stored date wins; day 14 vs 15 boundary; clock moved backwards doesn't extend the trial beyond 14 days from the stored start); `ProGateTest` (trial active / expired / owned; created items stay readable). Emulator: fresh install shows the explainer; set the clock forward 15 days → Pro features lock, data intact; buy with a licence tester → unlocked; reinstall with backup on → trial not reset.

**Effort:** M (1–2 weeks).

### Pro features (each its own PR, after the free features)
PDF/Excel reports + comparison stats + year in review · advanced rules engine · bank-statement PDF import · receipt photos · extra themes/icons/widgets (with the redesign) · subscription price-change alerts · multi-currency · on-device AI questions (Phase 3).

## Phase 4: iPhone / App Store (see `docs/ios-release-study.md`)

**When:** after the Android launch, once Android reaches **~25k installs a year** (where iOS starts to pay for its ~₹8,700/yr developer fee), or earlier if iPhone users are wanted for reach. Expected iOS revenue: roughly **10–35% on top of Android** (estimate); India is ~94% Android.

**Prerequisites:** a Mac with Xcode 26 (required for uploads since 28 Apr 2026), a real iPhone for testing the Shortcuts automation, Apple Developer Program (enrol through the Apple Developer app as an individual), Small Business Program enrolment (15%).

### iOS-1: Kotlin Multiplatform groundwork (in the Android repo)
- Move parsers, Room database, repositories and view-models into a shared KMP module; Room with `BundledSQLiteDriver` (DAOs suspend/Flow). Keep the Android app working and its tests green after each step.
- Swap `java.util.Calendar` and Android-only APIs in shared code for `kotlinx-datetime` / expect-actual; move `BudgetPeriod`, `CashFlow`, `RecurringSchedule`, parsers and `Entitlement` logic into shared code with their JVM tests.
- **Effort:** M–L (2–3 weeks). Worth starting only when iOS is committed to.

### iOS-2: iPhone app MVP
- Compose Multiplatform UI for the shared screens; App Group container for the database so widgets and intents can read it.
- **Capture:** an App Intent "Log bank SMS" that takes message text, runs the shared parser on the phone, saves the expense and posts a local notification with category buttons. Guided setup: video, iCloud link to a ready-made shortcut (Message trigger, keywords "debited/spent/Rs", run without confirmation, allow when locked), and a "send yourself a test SMS" check.
- **Quick add:** interactive widget, Control Center control, Lock Screen widget, Siri App Shortcuts ("log ₹200 auto"). Native SwiftUI/WidgetKit.
- **Catch misses:** share sheet + Vision OCR for UPI success screenshots; bank-statement PDF import (same parser as the Android Pro feature).
- **Privacy:** no analytics or crash SDKs that send data off the phone, so the label is "Data Not Collected"; privacy manifest.
- **Effort:** L (4–6 weeks).

### iOS-3: Payments on iOS
- StoreKit 2 (native Swift or a KMP wrapper): `pro_lifetime` non-consumable; a ₹0 non-consumable **"14-day Trial"** started with one tap in onboarding (show length, what stops working, full price first); `Transaction.updates` listener from launch; `currentEntitlements` at every start; "Restore purchases" (`AppStore.sync()`); cached entitlement for offline.
- Price: **₹299 founder** via a price schedule, then **₹399–499**; offer codes for founding members and reviewers (never tied to a rating).
- Pro is a **separate purchase per platform**; say so on the paywall and listing.
- Decide Family Sharing before enabling it (it can't be turned off).
- **Effort:** M (1–2 weeks).

### iOS-4: Release
- TestFlight (external testers need Beta App Review); listing that leads with India/UPI, on-device only, no ads/loans (to avoid "spam" rejections in a crowded category); age-rating questionnaire; India storefront first, skip EU storefronts (avoids DSA trader disclosure).

**Total:** roughly **6–10 weeks** for one developer, plus waiting time for App Review.

## Phase 5: International (see `docs/international-release-study.md`)

**When:** after the India launch is stable. Order: **Gulf (UAE + Saudi) and Indians living there → Pakistan and Sri Lanka → an opportunistic Western listing.** Skip Africa and Southeast Asia until wallet-notification capture is proven.

### INT-1: Multi-currency (schema change, backup bump)
- Each expense/income stores its currency; one home currency for totals. Exchange rates entered by the user or updated periodically (no server; cache the last rate). A ₹ + AED/SAR dual view for NRIs.
- Tests: conversion and rounding, totals across currencies, migration, backup round trip.
- **Effort:** M–L (2–3 weeks).

### INT-2: Locale-aware formatting
- Drop Indian lakh grouping outside India; currency symbols and strings (`AED`, `SAR`, `PKR`, `LKR`, `Rs.`); Arabic-Indic digits in parsing.
- **Effort:** S–M (1 week).

### INT-3: Gulf bank-SMS formats
- Per-bank templates for major UAE and Saudi banks (Saudi formats are SAMA-standardized), each from real sanitised samples turned into parser tests. Keep the India parser working for NRIs' Indian SIM in a dual-SIM phone.
- **Effort:** M per country (1–2 weeks), plus ongoing upkeep as banks change formats.

### INT-4: Pakistan and Sri Lanka bank-SMS formats
- Same approach; mostly English messages.
- **Effort:** M per country.

### INT-5: Store setup per region
- Per-country prices set by hand (Gulf ~AED/SAR 39 lifetime; Pakistan/Sri Lanka ~$3–5; West $15–20); exclude China mainland, Russia, Belarus; decide on EU storefronts (DSA trader disclosure); region-specific listings (Gulf/NRI: "Track your Indian and UAE accounts in one place"; West: privacy-first, no bank linking).
- Tax/admin: W-8BEN, payments profiles, and a CA for GST (₹20 lakh threshold, LUT) and FEMA reporting.
- **Effort:** S (days), plus CA time.

### INT-6: Arabic interface (later)
- Only after the Gulf shows traction; Gulf bank SMS are often in English.

## Later / out of scope here

- **Receipt photos:** a Pro feature (see PR 9 list). Store locally in app storage, excluded from Google auto-backup, included in Drive backup only if the user opts in (size).
- **Multi-currency:** low demand in India; planned for the international phase (INT-1).
- **Desktop, family sharing, languages:** separate phases. (iOS is planned above as Phase 4.)

## Order and timeline (solo dev, rough)

| Order | PR | Depends on | Effort | Schema |
|---|---|---|---|---|
| 1 | PR 1 Faster entry | — | S–M | — |
| 2 | PR 2 CSV export | — | S | — |
| 3 | PR 3 Salary-cycle months + ranges | — | M | — |
| 3b | PR 9a Payments plumbing + ₹99 Supporter | Final package ID, Play Console products | M | — |
| 4 | PR 4 Income | PR 3 (cycle-aware forecast) | M–L | v14 |
| 5 | PR 5a/5b Accounts + cards | PR 4 | L | v15 |
| 6 | PR 6 Lend / borrow | PR 5 optional | S–M | v16 |
| 7 | PR 8 Import | PRs 4–5 + real sample | M | — |
| 8 | Pro features (reports, rules engine, extra widgets…) | PR 9a | M each | — |
| 9 | PR 9b Pro launch: reverse trial + paywall | PR 9a + 3 Pro features | M | — |
| 10 | PR 7 Themes | **The app redesign** (done together; extra themes are Pro, light theme free) | M | — |

About **10–14 weeks** in total for one developer. PRs 1–3 (about 3 weeks) already answer the most common complaints and fit the Phase 1 launch. PRs 4–6 and 8 are Phase 2. Themes (PR 7) come last, with the redesign.

## Decisions needed from the owner

1. **Income as a separate table** (recommended) rather than a type column on expenses.
2. **Balances calculated** from opening balance + entries (recommended), not taken from the "Avl Bal" in SMS (which PR #7 stops storing). The SMS balance could later be shown as a check.
3. **Month start limited to days 1–28.**
4. **All of PRs 1–8 free.** Pro features and pricing as in PR 9 (decided 18 Oct 2026).
5. **Who builds it:** one session at a time on this repo, to avoid the parallel-merge surprises from #8/#9.
