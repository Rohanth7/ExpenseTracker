# ExpenseTracker — Project Notes

Combined summary of earlier Claude Code sessions (May–Oct 2026). Use this as context when picking up work.

**Repo:** https://github.com/Rohanth7/ExpenseTracker (branch `main`)
**Last feature commit:** `8d6d576` — Add Cash Flow Forecast section to Dashboard

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

## Open items

- **Splash icon** is still pixelated — waiting on a high-res icon from the user.
- **Notification keyword allowlist** (only treat as expense if it contains debit words like "debited", "paid", …) — discussed, user chose to leave it for now.
- **Feature ideas not built:** payment-method breakdown chart, merchant mapping management screen (`MerchantMapping` table exists, no UI), CSV/PDF export, weekly spending digest notification, no-spend streaks, net worth snapshot, year-over-year comparison, receipt photo attachment.
- **Sharing:** debug APK via `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`. No release signing config yet.
