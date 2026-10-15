# ExpenseTracker — Project Notes

Combined summary of earlier Claude Code sessions (May–Oct 2026). Use this as context when picking up work.

**Repo:** https://github.com/Rohanth7/ExpenseTracker (branch `main`)
**Last commit:** `ee2c973` — Keep captured SMS text 7 days, leave it out of backups, stop Google auto-backup (#7)
**Database version:** 13 · **Backup format:** v2
**Status:** Phase 0 (make it work for a new user) is done. Next: Phase 1 (Play Store launch), plus the deferred database-encryption PR.

`.claude/settings.local.json` is Claude Code's local config — never commit it.

## Features

- **Onboarding** — first run: welcome → bank SMS → notification access → alerts → optional monthly income. Each permission is explained before Android asks; every step has "Not now". Adds 10 starter categories. "Reset everything" shows it again.
- **Auto-tracking** — parses bank SMS (`SmsReceiver`, `SmsParser`) and GPay/PhonePe/Paytm notifications (`TransactionNotificationListener`, `NotificationParser`). Credits, promos, OTPs, bill/statement reminders, "will be debited" notices, mandate registrations and failed payments are ignored; WhatsApp is not read. SMS + notification for the same transaction are deduped on amount within 3 minutes. With no categories a payment is saved as pending, never dropped. Settings → Auto-tracking card shows whether SMS and notification access are on and fixes each in one tap.
- **Capture notification** — three category buttons: the auto-detected category, then the most used in the last 60 days, then alphabetical (`QuickCategories`). Tapping the notification opens the full picker.
- **Overview (Dashboard)** — month navigation, budget warning (pending expenses count toward total), upcoming bills, EMI commitments, savings goals, Cash Flow Forecast (income − spent − bills still due − EMI still due = free to spend; current month only, when income is set). The 6-month trend chart was removed on purpose.
- **Expenses** — search, category filter chips, multi-tagging, edit expense, date picker.
- **Statistics** — monthly view by default with yearly toggle, subcategory breakdown, smart insights (weekend vs weekday, no-spend days), auto-tracking stats.
- **Budgets** — per-category limits (Settings → Category Budgets, or the Categories tab), alerts at a customizable threshold (slider) and at 100%.
- **Settings** — Auto-tracking, Bills & Reminders (bills + loans/EMIs), Category Budgets, Cloud Backup (Back up now, Restore from Drive), Preferences (budget alerts, week start, privacy mode, App Lock, widget), Data & Backup (export, restore from file, reset; "Load mock data" in debug builds only).
- **Backup** — format v2 includes categories, expenses, bills, loans, savings goals, learned merchants, income and alert settings. Restore checks the file first, shows what it contains, and runs in one transaction. v1 files still restore. Captured SMS text is never included.
- **App Lock** — fingerprint, face or screen lock; re-locks after 30 s in the background (`AppLockState`); lock screen with Unlock button instead of closing the app.
- **Privacy** — captured SMS/notification text is deleted after 7 days (`CapturedTextRetention`); Android's automatic Google backup excludes all app data; privacy mode hides the app in recents from startup.
- **Other** — recurring bills auto-logged on their due day, bill reminders (WorkManager), Budget Pulse home-screen widget, savings goals with deposit dialog, merchant learning with whole-word matching, new app icon, dark splash background (`#171511`).

## Notable bug fixes (don't regress)

- Notification tap: `FLAG_ACTIVITY_SINGLE_TOP` + `launchMode="singleTop"` + `onNewIntent`; PendingIntent request codes and notification ids masked with `and 0x7FFFFFFF`.
- Month end range uses `23:59:59.999`.
- Bill reminders wrap to next month when due day < today; short-month bill dates clamped.
- Loan progress subtracts a month when today is before the EMI due day (Dashboard and Settings `LoanRow`).
- PhonePe recipient phrasing ("sent you", "paid to you", …) treated as credit.
- Weekend insight counts today in the day denominator.
- Bottom nav "Overview" tab returns to Overview from nested screens.

### Merged PRs (2026-10-15/16, each tested on the emulator against `main`)

- **#1 Code-review fixes** — migration 12 → 13 creates the missing `index_expenses_categoryId` (old DBs crashed on upgrade); forecast only subtracts bills/EMIs still due and skips loans that haven't started; both limit dialogs share `ui/util/LimitInput.kt` ("10,000" / "₹5000" accepted, invalid input blocks Save, decimals kept); recurring-bill crash dedupe; changing a limit re-arms alerts; `.claude/settings.local.json` untracked.
- **#2 Parser accuracy** — merchant names for HDFC, ICICI, SBI, Axis, Kotak ("Dear Customer" no longer → `mer`); SBI amounts without Rs; whole-word keywords; non-expense SMS ignored; only real payee names are learned, rules need whole words and ≥ 4 chars; broken rules removed once on upgrade (`MappingCleanup`).
- **#3 Onboarding** — see Features. Fixed: fresh installs silently dropped every payment; notification access couldn't be enabled; permission prompt on every launch.
- **#4 Backups** — see Features. Fixed: bills, loans, goals, learned rules and income lost on restore; non-atomic restore; Drive was upload-only and updates always failed (`HttpURLConnection` rejects `PATCH`); labels claimed "weekly"/"encrypted"; mock data shipped in release. All Google Sign-In use is in `DriveBackupClient`.
- **#5 App Lock** — cancelling closed the app; returning from background skipped the lock; lock could be enabled without a screen lock (lockout), now it needs a successful prompt first and turns itself off with a dialog if the phone's screen lock is removed; Android 9–10 use `BIOMETRIC_WEAK | DEVICE_CREDENTIAL`; privacy mode applied at startup.
- **#6 Smaller bugs** — capture notification showed only 2 categories (a "Categorize" button took the third slot); quick-action taps ignore the alert threshold; deleting a category leaves learned merchants and bills pointing at it (payments vanished), rules for missing categories are now ignored; auto-logged bills dated the 1st, now their due day (`RecurringSchedule`), duplicate check covers the whole month; forecast counts auto-logged bills until they're logged (`CashFlow`).
- **#7 Privacy** — see Features (7-day retention, no captured text in backups, Google auto-backup off).

### Known trade-offs

- A bill due today counts as "still due" even if already paid.
- Two auto-log bills with the same name, amount and month log only once.
- Auto-logged bills are only added when the app is opened (dated correctly, even if opened late).
- Existing expenses keep the old, broken descriptions from before #2; only learned rules were cleaned.

## Testing setup

- Android Studio (Homebrew install) with its bundled JDK:
  `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ANDROID_HOME="$HOME/Library/Android/sdk"`
- Build: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- Unit tests (JVM, 63): `./gradlew testDebugUnitTest` — parser, notification parser, merchant rules, backup codec + rule files, app lock timing, quick categories, recurring schedule, cash flow, starter categories.
- Device tests (11, in-memory DBs, need the emulator running): `./gradlew connectedDebugAndroidTest` — backup restore, category deletion, captured-text retention. Note: this uninstalls the app afterwards.
- Emulator "Medium Phone API 37.0" (Android 17, Google Play image). The AVD itself is set to 1 core + software graphics, which crashes system_server; start it with
  `~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_37.0 -cores 4 -gpu host -memory 4096 -no-boot-anim`
  and run `adb shell svc power stayon true` so the screen doesn't sleep.
- Fake bank SMS: `adb emu sms send JD-ICICIB "ICICI Bank Acct XX123 debited for Rs 349.00 on 16-Oct-26; BIGBASKET credited. UPI:412345678901."`. GPay/PhonePe notifications need a real phone.
- Device PIN for App Lock tests: `adb shell locksettings set-pin 1234` / `clear --old 1234`. Android blocks screenshots of its PIN screen.
- Reading the app DB: copy `databases/expense_tracker.db` plus `-wal` and `-shm` with `adb shell "run-as com.example.expensetracker cat …"`, then `sqlite3` locally.

## Open items

- **Database encryption** (SQLCipher + key in Android Keystore, one-time migration of existing data) — deferred from Phase 0 to its own PR.
- **Not tested yet:** Google Drive end to end (needs an OAuth client registered for the final package name and signing key — Phase 1); GPay/PhonePe capture and quick actions on a real phone; fingerprint/face unlock (only PIN tested); Android 9–10 lock path.
- **`READ_SMS`** is declared but never requested — use it for "import past transactions" or remove it before the Play SMS declaration.
- **Settings** loses its open sub-page on rotation (`currentPage` isn't saved).
- **Dead code:** `MerchantMappingRepository.getCategoryId` (duplicate of `MappingHelper.getCategoryId`, no callers); `ui/widgets/ExpenseWidget.kt` (not registered in the manifest).
- **Splash icon** is still pixelated — waiting on a high-res icon from the user.
- **Notification keyword allowlist** (only treat as expense if it contains debit words like "debited", "paid", …) — discussed, user chose to leave it for now.
- **Feature ideas not built:** payment-method breakdown chart, merchant mapping management screen (`MerchantMapping` table exists, no UI), CSV/PDF export, weekly spending digest notification, no-spend streaks, net worth snapshot, year-over-year comparison, receipt photo attachment.
- **Sharing:** debug APK only. No release signing config yet.

## Release plan (from 2026-10-15 audit + competitor and store research)

Positioning: automatic expense tracking for India with no account, no ads, no loans, data stays on the phone.

**Phase 0 — make it work for a new user: done** (PRs #2–#7). Only database encryption is left, as a separate PR.

**Phase 1 — Play Store launch (next):** final app name + package ID (permanent; currently `com.example.expensetracker`, which Play rejects; the app calls itself "MyMoney"), release signing, R8 with Gson keep rules, privacy policy, use or remove `READ_SMS`, SMS Permissions Declaration + demo video, Data safety form, migrate Drive to the new authorization API + register the OAuth client, crash reporting, CSV export, $25 fee, closed test with 12 testers for 14 days, keep a no-SMS fallback build ready.

**Phase 2 — catch up:** CSV/PDF export, accounts with balances + credit cards, splits/IOUs, date-range search, receipt photos, light theme, a "missed SMS" inbox.

**Phase 3 — stand out and earn:** subscription/UPI AutoPay detection, PDF bank-statement import, custom parsing rules, on-device AI questions, one-time Pro at ₹299–499.

**Phase 4 — iOS:** Kotlin Multiplatform + Compose Multiplatform (est. 4–8 weeks), capture via Apple Shortcuts (iOS can't read SMS), $99/year developer account.

**Open decisions:** app name/package ID; one-time Pro vs. subscription; whether backups should be password-protected.
