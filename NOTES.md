# ExpenseTracker — Project Notes

Combined summary of earlier Claude Code sessions (May–Oct 2026). Use this as context when picking up work.

**Repo:** https://github.com/Rohanth7/ExpenseTracker (branch `main`)
**Last commit:** `93f1f5e` — Fix forecast double counting, migration crash, and budget limit edits (#1)
**Database version:** 13

`.claude/settings.local.json` is Claude Code's local config — never commit it.

## Features

- **Auto-tracking** — parses bank SMS (`SmsReceiver`, `SmsParser`) and GPay/PhonePe notifications (`TransactionNotificationListener`, `NotificationParser`). Credits and promos are filtered out; SMS + notification for the same transaction are deduped on amount within a 3-minute window across sources. A notification prompts the user to categorize.
- **Overview (Dashboard)** — month navigation, budget warning (pending/uncategorized expenses count toward total), upcoming bills, EMI commitments, savings goals, Cash Flow Forecast (income − spent − bills − EMI = free to spend; current month only, when income is set). The 6-month trend chart was removed on purpose.
- **Expenses** — search, category filter chips, multi-tagging, edit expense, date picker.
- **Statistics** — monthly view by default with yearly toggle, subcategory breakdown, smart insights (weekend vs weekday, no-spend days), auto-tracking stats.
- **Budgets** — per-category limits (Settings → Category Budgets), alerts at a customizable threshold (slider) and at 100%.
- **Settings** — hub with sub-pages: Bills & Reminders (bills + loans/EMIs), Preferences (budget alerts, week start, privacy, biometric, widget), Category Budgets, Data & Backup (JSON export/import, Google Drive backup, auto cloud backup, load mock data, reset).
- **Other** — recurring expenses/bills, bill reminders (WorkManager), biometric lock, Budget Pulse home-screen widget, savings goals with deposit dialog, merchant fuzzy matching / smart auto-categorization, new app icon, dark splash background (`#171511`).

## Notable bug fixes (don't regress)

- Notification tap: `FLAG_ACTIVITY_SINGLE_TOP` + `launchMode="singleTop"` + `onNewIntent`; PendingIntent request codes masked with `and 0x7FFFFFFF`.
- Month end range uses `23:59:59.999`.
- Bill reminders wrap to next month when due day < today; short-month bill dates clamped.
- Loan progress subtracts a month when today is before the EMI due day (Dashboard and Settings `LoanRow`).
- PhonePe recipient phrasing ("sent you", "paid to you", …) treated as credit.
- Restore deletes recurring templates before importing; malformed backup JSON returns null instead of crashing.
- Recurring bills not duplicated after a crash; destructive DB migration fallback removed.
- Weekend insight counts today in the day denominator.
- Bottom nav "Overview" tab returns to Overview from nested screens.

### Code-review fixes (PR #1, merged 2026-10-15, tested on emulator)

- Migration 12 → 13 creates the missing `index_expenses_categoryId`; upgrading from old DB versions no longer crashes.
- Cash Flow Forecast only subtracts bills/EMIs due today or later ("Bills still due", "EMI still due"); loans starting after this month are excluded from the forecast but still listed in the EMI card.
- Both limit dialogs share `ui/util/LimitInput.kt`: accepts "10,000" / "₹5000", blank removes the limit, invalid input shows an error and blocks Save, decimals are kept.
- Recurring bills check the DB for an existing entry for the month before inserting (crash-safe), so `RecurringManager` is back to `apply()`.
- Budget alert flag keys include the limit amount, so changing a limit re-arms that month's alerts.
- `.claude/settings.local.json` is untracked and in `.gitignore` (older commits still contain it).
- Known trade-offs: a bill due today counts as "still due" even if already paid; two auto-log bills with the same name, amount and month log only once.

## Testing setup

- Android Studio (Homebrew install) with its bundled JDK; build with `./gradlew assembleDebug`.
- Emulator "Medium Phone API 37.0" (Android 17, Google Play image). Configured for 4 CPU cores and host GPU; with 1 core / software graphics it takes minutes to boot.
- Fake bank SMS: `adb emu sms send <sender> "<text>"`. GPay/PhonePe notifications need a real phone.

## Open items

- **Splash icon** is still pixelated — waiting on a high-res icon from the user.
- **Notification keyword allowlist** (only treat as expense if it contains debit words like "debited", "paid", …) — discussed, user chose to leave it for now.
- **Feature ideas not built:** payment-method breakdown chart, merchant mapping management screen (`MerchantMapping` table exists, no UI), CSV/PDF export, weekly spending digest notification, no-spend streaks, net worth snapshot, year-over-year comparison, receipt photo attachment.
- **Sharing:** debug APK via `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`. No release signing config yet.

## Release plan (from 2026-10-15 audit + competitor and store research)

Positioning: automatic expense tracking for India with no account, no ads, no loans, data stays on the phone.

**Phase 0 — make it work for a new user (next)**
1. Onboarding + default categories (fresh install currently drops every captured transaction).
2. Wire up the notification-access settings screen (code exists, never called).
3. Fix SMS merchant regex ("Customer" → `mer`; HDFC/ICICI don't match) and stop learning wrong names as rules.
4. Stop monitoring WhatsApp; filter OTP, bill-due reminder and "will be debited" SMS.
5. Complete backups (bills, loans, goals, income), make Drive restore work, make restore atomic.
6. Biometric lock: no lockout without a device screen lock; re-lock on return.
7. Hide "Load Mock Data" in release builds (it wipes real data).
8. Smaller bugs: deleted categories leave learned rules behind; quick-action notifications ignore the threshold slider and show 2 of 3 category buttons; auto-logged bills dated the 1st instead of due day.
9. Privacy: DB not encrypted; every expense stores the full raw SMS.
10. Real tests, starting with an SMS parser suite from real samples.

**Phase 1 — Play Store launch:** final app name + package ID (permanent; app is "MyMoney" in one place and "Expense Tracker" in another), release signing, R8 with Gson keep rules, privacy policy, permission explanation screens, use or remove `READ_SMS`, SMS Permissions Declaration + demo video, Data safety form, migrate Drive to the new authorization API, crash reporting, CSV export, $25 fee, closed test with 12 testers for 14 days, keep a no-SMS fallback build ready.

**Phase 2 — catch up:** CSV/PDF export, accounts with balances + credit cards, splits/IOUs, date-range search, receipt photos, light theme, a "missed SMS" inbox.

**Phase 3 — stand out and earn:** subscription/UPI AutoPay detection, PDF bank-statement import, custom parsing rules, on-device AI questions, one-time Pro at ₹299–499.

**Phase 4 — iOS:** Kotlin Multiplatform + Compose Multiplatform (est. 4–8 weeks), capture via Apple Shortcuts (iOS can't read SMS), $99/year developer account.

**Open decisions:** app name/package ID; one-time Pro vs. subscription.
