# ExpenseTracker — Project Notes

Combined summary of earlier Claude Code sessions (May–Oct 2026). Use this as context when picking up work.

**Repo:** https://github.com/Rohanth7/ExpenseTracker (branch `main`)
**Last merged:** `8ddffbc` — Faster entry: calculator, optional category, duplicate, widget quick-add (#10)
**Database version:** 13 · **Backup format:** v2
**Status:** Phase 0 is done, plus subscription detection (#8), message rules (#9) and F1 faster entry (#10). In progress: the five features in `docs/superpowers/plans/2026-10-21-features-1-4-and-price-alerts.md` (F1 faster entry → F5 price alerts), one PR each, before Play Store launch work.

`.claude/settings.local.json` is Claude Code's local config — never commit it.

## Features

- **Onboarding** — first run: welcome → bank SMS → notification access → alerts → optional monthly income. Each permission is explained before Android asks; every step has "Not now". Adds 10 starter categories. "Reset everything" shows it again.
- **Auto-tracking** — parses bank SMS (`SmsReceiver`, `SmsParser`) and GPay/PhonePe/Paytm notifications (`TransactionNotificationListener`, `NotificationParser`). Credits, promos, OTPs, bill/statement reminders, "will be debited" notices, mandate registrations and failed payments are ignored; WhatsApp is not read. SMS + notification for the same transaction are deduped on amount within 3 minutes. With no categories a payment is saved as pending, never dropped. Settings → Auto-tracking card shows whether SMS and notification access are on and fixes each in one tap.
- **Capture notification** — three category buttons: the auto-detected category, then the most used in the last 60 days, then alphabetical (`QuickCategories`). Tapping the notification opens the full picker.
- **Overview (Dashboard)** — month navigation, budget warning (pending expenses count toward total), upcoming bills, EMI commitments, savings goals, Cash Flow Forecast (income − spent − bills still due − EMI still due = free to spend; current month only, when income is set). The 6-month trend chart was removed on purpose.
- **Expenses** — search, category filter chips, multi-tagging, edit expense, date picker. Manual entry (F1): labelled "Add expense" button; the amount field takes sums like `120+45` or `3×40` (`AmountCalculator`, with `+ − × ÷` chips that insert at the cursor and an "= ₹165" preview); category is optional ("Choose later (pending)") and starts on the last one used; Edit → **Duplicate** copies an expense dated now (no SMS text copied).
- **Subscriptions** (#8) — repeating payments detected from history (`SubscriptionDetector`): Overview card (top 3 + yearly total) and a Subscriptions page with "Remind me" (adds a bill) and "Not a subscription" (with Undo).
- **Message rules** (#9) — teach a bank message the parser reads wrong ("It's a payment" / "Not a payment"), from an expense or by pasting text; rules are checked before the built-in parser and included in backups. Long multi-part SMS are joined before parsing.
- **Statistics** — monthly view by default with yearly toggle, subcategory breakdown, smart insights (weekend vs weekday, no-spend days), auto-tracking stats.
- **Budgets** — per-category limits (Settings → Category Budgets, or the Categories tab), alerts at a customizable threshold (slider) and at 100%.
- **Settings** — Auto-tracking, Bills & Reminders (bills + loans/EMIs), Category Budgets, Cloud Backup (Back up now, Restore from Drive), Preferences (budget alerts, week start, privacy mode, App Lock, widget), Data & Backup (export, restore from file, reset; "Load mock data" in debug builds only).
- **Backup** — format v2 includes categories, expenses, bills, loans, savings goals, learned merchants, income and alert settings. Restore checks the file first, shows what it contains, and runs in one transaction. v1 files still restore. Captured SMS text is never included.
- **App Lock** — fingerprint, face or screen lock; re-locks after 30 s in the background (`AppLockState`); lock screen with Unlock button instead of closing the app. While locked the app's dialogs hide (still open, typing kept) and come back on unlock: screens open dialogs through `ui/components/AppDialogs.kt` (`AppAlertDialog`, `AppDatePickerDialog`, `AppModalBottomSheet`), never the Material ones directly (`AppDialogsUsageTest` fails the build otherwise).
- **Privacy** — captured SMS/notification text is deleted after 7 days (`CapturedTextRetention`); Android's automatic Google backup excludes all app data; privacy mode hides the app in recents from startup.
- **Other** — recurring bills auto-logged on their due day, bill reminders (WorkManager), Budget Pulse home-screen widget (its "+" opens the add-expense dialog, F1; launch intents go through `LaunchAction`), savings goals with deposit dialog, merchant learning with whole-word matching, new app icon, dark splash background (`#171511`).

## Notable bug fixes (don't regress)

- Notification tap: `FLAG_ACTIVITY_SINGLE_TOP` + `launchMode="singleTop"` + `onNewIntent`; PendingIntent request codes and notification ids masked with `and 0x7FFFFFFF`.
- Month end range uses `23:59:59.999`.
- Bill reminders wrap to next month when due day < today; short-month bill dates clamped.
- Loan progress subtracts a month when today is before the EMI due day (Dashboard and Settings `LoanRow`).
- PhonePe recipient phrasing ("sent you", "paid to you", …) treated as credit.
- Weekend insight counts today in the day denominator.
- Bottom nav "Overview" tab returns to Overview from nested screens.
- A recreated activity (rotation, Android restoring it) doesn't act on its launch intent again (`LaunchAction.onCreate`); before F1 a rotation after a notification tap reopened Categorize.

### Merged PRs (2026-10-15/16, each tested on the emulator against `main`)

- **#1 Code-review fixes** — migration 12 → 13 creates the missing `index_expenses_categoryId` (old DBs crashed on upgrade); forecast only subtracts bills/EMIs still due and skips loans that haven't started; both limit dialogs share `ui/util/LimitInput.kt` ("10,000" / "₹5000" accepted, invalid input blocks Save, decimals kept); recurring-bill crash dedupe; changing a limit re-arms alerts; `.claude/settings.local.json` untracked.
- **#2 Parser accuracy** — merchant names for HDFC, ICICI, SBI, Axis, Kotak ("Dear Customer" no longer → `mer`); SBI amounts without Rs; whole-word keywords; non-expense SMS ignored; only real payee names are learned, rules need whole words and ≥ 4 chars; broken rules removed once on upgrade (`MappingCleanup`).
- **#3 Onboarding** — see Features. Fixed: fresh installs silently dropped every payment; notification access couldn't be enabled; permission prompt on every launch.
- **#4 Backups** — see Features. Fixed: bills, loans, goals, learned rules and income lost on restore; non-atomic restore; Drive was upload-only and updates always failed (`HttpURLConnection` rejects `PATCH`); labels claimed "weekly"/"encrypted"; mock data shipped in release. All Google Sign-In use is in `DriveBackupClient`.
- **#5 App Lock** — cancelling closed the app; returning from background skipped the lock; lock could be enabled without a screen lock (lockout), now it needs a successful prompt first and turns itself off with a dialog if the phone's screen lock is removed; Android 9–10 use `BIOMETRIC_WEAK | DEVICE_CREDENTIAL`; privacy mode applied at startup.
- **#6 Smaller bugs** — capture notification showed only 2 categories (a "Categorize" button took the third slot); quick-action taps ignore the alert threshold; deleting a category leaves learned merchants and bills pointing at it (payments vanished), rules for missing categories are now ignored; auto-logged bills dated the 1st, now their due day (`RecurringSchedule`), duplicate check covers the whole month; forecast counts auto-logged bills until they're logged (`CashFlow`).
- **#7 Privacy** — see Features (7-day retention, no captured text in backups, Google auto-backup off).
- **#8 Subscriptions** — see Features. No schema change.
- **#9 Message rules** — see Features (`CustomRules`); also fixed long SMS being parsed in pieces. No schema change.
- **#10 F1 Faster entry** (2026-10-23) — see Features (sums in the amount field, optional category, Duplicate / Save & duplicate, widget "+"). Along the way: launch actions are held until App Lock is unlocked and until NavHost exists (cold-start crash), aren't replayed after rotation or from Recents, and a tap with no action doesn't drop a waiting one; the lock screen is a full-screen dialog window and asks for the unlock prompt from inside itself (Android 7–8.1 fingerprint dialog order); one amount parser for amounts, limits and income (`AmountCalculator`: Indian/Western grouping, decimal comma on comma-decimal phones, German "1.500", exact half-paisa rounding, "= ₹" preview when the text could be read two ways); restore forgets the last-used category. Reviewed with `/code-review` three times by the user and three times before hand-back.

### Open PRs
- **Dialogs over the lock screen** (`fix/dialogs-over-lock`) — security: a dialog that opened after the lock (restore preview when the file picker came back after the 30 s timeout) sat on top of it, and its Restore button worked without unlocking. App dialogs now hide while locked (see Features → App Lock).

### Known trade-offs

- A bill due today counts as "still due" even if already paid.
- Two auto-log bills with the same name, amount and month log only once.
- Auto-logged bills are only added when the app is opened (dated correctly, even if opened late).
- Existing expenses keep the old, broken descriptions from before #2; only learned rules were cleaned.

## Testing setup

- Android Studio (Homebrew install) with its bundled JDK:
  `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ANDROID_HOME="$HOME/Library/Android/sdk"`
- Build: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- Unit tests (JVM, 143 on `fix/dialogs-over-lock`, 142 on `main`): `./gradlew testDebugUnitTest` — parser, notification parser, merchant rules, custom rules, subscriptions, backup codec + rule files, app lock timing, quick categories, recurring schedule, cash flow, starter categories, amount calculator, expense copy, launch actions, dialogs go through `AppDialogs`.
- Device tests (21 on `fix/dialogs-over-lock`, 17 on `main`; need the emulator running): `./gradlew connectedDebugAndroidTest` — backup restore, category deletion, captured-text retention, restore forgets the last-used category, launch PendingIntents don't collide (`LaunchIntentsTest`), widget quick add from a cold start, not replayed from Recents, and from Categorize (`QuickAddLaunchTest`, Compose UI test), dialogs hide while locked and keep their text (`AppDialogsTest`). To keep the emulator's data, install and run them without the uninstall: `./gradlew installDebug installDebugAndroidTest` then `adb shell am instrument -w com.example.expensetracker.test/androidx.test.runner.AndroidJUnitRunner`. In ActivityScenario tests that call `startActivity` on MainActivity, launch with a plain `Intent(context, MainActivity::class.java)`: `onNewIntent` calls `setIntent`, and the scenario stops tracking an activity whose intent no longer matches its launch intent (`close()` then hangs at PAUSED). Note: this uninstalls the app afterwards, **on every connected emulator** — with two emulators running, set `ANDROID_SERIAL` so it doesn't wipe the other one.
- Compose UI tests need Espresso ≥ 3.6 on API 37: 3.5.1 calls `InputManager.getInstance()`, which Android 17 removed (bumped to 3.7.0 / AndroidX JUnit 1.3.0 on `feat/faster-entry`).
- Per-app language for locale bugs: `adb shell cmd locale set-app-locales com.example.expensetracker --locales de-DE` (reset with `--locales ""`).
- Emulator "Medium Phone API 37.0" (Android 17, Google Play image). The AVD itself is set to 1 core + software graphics, which crashes system_server; start it with
  `~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_37.0 -cores 4 -gpu host -memory 4096 -no-boot-anim`
  and run `adb shell svc power stayon true` so the screen doesn't sleep.
- Fake bank SMS: `adb emu sms send JD-ICICIB "ICICI Bank Acct XX123 debited for Rs 349.00 on 16-Oct-26; BIGBASKET credited. UPI:412345678901."`. GPay/PhonePe notifications need a real phone.
- Device PIN for App Lock tests: `adb shell locksettings set-pin 1234` / `clear --old 1234`. Android blocks screenshots of its PIN screen.
- Reading the app DB: copy `databases/expense_tracker.db` plus `-wal` and `-shm` with `adb shell "run-as com.example.expensetracker cat …"`, then `sqlite3` locally.
- Emulator gotchas (Oct 2026): when the Mac is heavily loaded, `adb shell` and `uiautomator dump` hang and Android reports "failed to complete startup" ANRs (and `connectedDebugAndroidTest` fails with "Process crashed") — wait for the load to drop and retry. `uiautomator dump` also hangs while a text field has focus; use screenshots + coordinates then. A force-stopped app's widget shows as a grey placeholder until the app runs again. Silence the emulator with `adb shell cmd audio set-ringer-mode SILENT`.

## Open items

- **Database encryption** (SQLCipher + key in Android Keystore, one-time migration of existing data) — deferred from Phase 0 to its own PR.
- **Not tested yet:** Google Drive end to end (needs an OAuth client registered for the final package name and signing key — Phase 1); GPay/PhonePe capture and quick actions on a real phone; fingerprint/face unlock (only PIN tested); Android 9–10 lock path; Android 7–8.1 fingerprint dialog order over the lock window (no emulator image here).
- **`READ_SMS`** is declared but never requested — use it for "import past transactions" or remove it before the Play SMS declaration.
- **Widget refresh on the main thread at startup**: `MainActivity.onCreate` → `WidgetUpdateHelper.update` → `AppWidgetManager.getAppWidgetIds` is a binder call on the main thread; with a slow system (Mac under load) it caused an "Input dispatching timed out" ANR on the emulator. Move it to a background dispatcher.
- **Add/Edit dialogs close on rotation** (`showAddDialog` etc. use `remember`, not `rememberSaveable`), losing what was typed.
- **Google Drive consent screen can open while locked** (`driveAuthIntent` is launched from a `LaunchedEffect`): it's Google's screen, no app data, but it appears over the lock.
- **Other amount fields still use `toDoubleOrNull`** (no ₹, grouping, sums or decimal comma): savings goal target and deposit, bill and loan amounts/EMI (Settings), dashboard quick inputs, the Expenses min/max filter. Pre-existing, outside F1; move them to `AmountField`/`AmountCalculator` in their own PR.
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
