# MyMoney: Competitor Study and Pricing Recommendation

*Date: 16 Oct 2026. Covers 24 apps. Store data comes from Play India / App Store India pages checked in Oct 2026. Where the price check disagreed with the first research pass, the price check wins. Any claim marked **†** is unverified: it comes from a third-party source or a search snippet, or a page we could not fetch. USD figures are converted at about ₹88/$.*

*Revised 16 Oct 2026 after a second price check (free-vs-paid limits, the name clash, App Store India prices) and a review against the evidence and the code. Also updated for PRs #8 (subscription detection) and #9 (user-taught message rules), merged the same morning.*

---

## 1. Executive summary

- **The market is split in two.** Free trackers in India are paid for by loans, cards or broking (axio, CRED, Jupiter, INDmoney; ET Money has already dropped expense tracking). Paid trackers mostly do manual entry or bank sync that works badly in India (Monefy, Wallet, Money Lover, Spendee, Toshl, YNAB). The open space is **automatic, private, ad-free and loan-free**. Only FinArt (hard paywall after a 5-day trial), PennyWise AI (1K+ installs) and, partly, Bluecoins (auto-log only in its one-time Premium, and it shows ads) are there today.
- **The biggest threat is axio (ex-Walnut, 10M+, now owned by Amazon), and it is decaying.** It has had no update since 15 Jul 2026. 2026 reviews report a bank's SMS going undetected since Aug, duplicate captures and broken export. Its money comes from loans at 14–35% p.a. (the personal-loan block says "APR from 18%"). Users who want to leave axio are the most likely first MyMoney users.
- **Capture reliability matters most, for free and paid users alike.** Across free and paid apps the angriest reviews are about capture or sync breaking (axio, FinArt, Bluecoins, Wallet, Toshl, Monarch). MyMoney's notification capture has never been tested on a real phone. **Test it on real devices before launch.**
- **The name "MyMoney" has to change before launch.** *"MyMoney—Track Expense & Budget"* (`com.raha.app.mymoney.free`) already has **10L+ installs and 4.8★ from 67.8K reviews**, is pitched as offline and ad-free (close to this app's own pitch), and its developer sells **"MyMoney Pro" for ₹499**. Launching our own Pro under the same name next to it would look like a knock-off and split search traffic. Six more near-identical "MyMoney" apps are on Play India.
- **The other launch blockers are mostly not code**, and the slowest one is a calendar wait:
  - the Play SMS Permissions Declaration (exception "SMS-based money management", which needs a demo video);
  - the package ID (`applicationId` is still `com.example.expensetracker`, which Play rejects, and it can never change after the first upload);
  - release signing, R8 with Gson keep rules, moving Drive to the new authorization API before registering the OAuth client, crash reporting;
  - a **closed test with 12 testers for 14 days** before production access, the longest-lead item.
- **Recommended model (revised 18 Oct 2026 after a second round of pricing research, §5c): free core forever + a 14-day "reverse trial" of Pro for every new user (no card) + a one-time Pro lifetime unlock: ₹199 founder price for 60 days, then ₹299.** After the trial users drop back to the free tier; nothing they created is lost. **Rejected:** a 30-day trial then pay-to-keep-using (Play has no trials for one-time products, only ~3–6% of finance-app users remain at day 30, and locking people out of their own history is the angriest kind of review), paid-upfront, subscriptions at launch, usage caps on entries, referral unlocks (need a server).
- **All automatic capture, manual entry and data export stay free.** That covers SMS and notification capture, message rules, budgets, bills, subscriptions and renewal reminders, forecast, CSV/JSON export, Drive backup, app lock, and no ads for anyone. Pro sells extra power: PDF/Excel reports and custom periods, an advanced rules engine, statement import, splits/IOUs, receipts, on-device AI questions, multi-currency, extra themes and widgets.
- **₹199–299 sits low in the market without being a throwaway price.** ₹100 would net only about ₹72–85, and the evidence shows cheap unlocks don't win enough extra buyers to make up for it. Comparable prices: Bluecoins ₹390, Bishinews Pro ₹500, PennyWise ₹699/yr or ₹2,000 lifetime, Wallet and Money Lover lifetime ₹1,999, FinArt ₹1,999/yr on iOS, Fold ₹999/yr, Monefy Premium ₹3,599+ (iOS India; duration not shown). Only Realbyte (₹99) and Bluecoins (₹390) are cheaper one-time unlocks, and the clashing "MyMoney Pro" sells for ₹499.
- **Expect pocket money in year 1, not a salary.** Base case: 25K installs × 1.5% paying × about ₹186 net ≈ **₹0.7 lakh/yr**. 100K installs at 3% ≈ ₹5.6 lakh. Keep costs at zero, treat revenue as validation, and let Play price experiments (₹149–399) set the final price.
- **Ship Play Billing in Phase 1 as a ₹99 "Supporter" purchase** (which turns into Pro later), so payments are tested before Pro exists. The reverse trial and paywall start when Pro launches (end of Phase 2); existing users get the 14-day trial then too. PennyWise's Play billing has been unavailable since about Sep 2026 after a payments-verification failure ("Transaction Declined [OR-FGEMF-20]"); it now sells Pro outside Play. Bluecoins paying users report Premium reverting and ads coming back (cause not confirmed).
- **Never take back something that was free.** ET Money dropped its free tracker; 1Money† moved buyers onto a subscription and later reversed it; Monefy, which locks basics behind Premium, sits at 4.0. Decide the free/Pro split before the Play launch and keep it.
- **Top 5 improvements:** (1) real-device capture validation across 20+ banks and every payment app the app watches; (2) Play launch blockers (new name, package ID, SMS declaration, signing, 14-day closed test); (3) inbox backfill of the last 90 days of SMS; (4) tools to report a missed transaction and review duplicates; (5) a labelled manual-entry flow that doesn't force a category, plus free CSV export.

---

## 2. MyMoney today

### 2.1 Feature table (owner's audit; 5 = matches a polished competitor)

| Area | Feature | Quality | Note |
|---|---|---|---|
| Onboarding | Permission explanations, "Not now" on every step, 10 starter categories | 4 | Good. Add an "import last 90 days" step when backfill ships |
| Capture | Bank SMS: HDFC, ICICI, SBI, Axis, Kotak; filters OTPs, reminders, mandates, failed payments; long multipart SMS joined (#9); 21 SMS-parser tests (92 JVM tests in total) | 3.5 | No inbox backfill (READ_SMS is declared but unused, a Play-policy risk). Smaller banks, cooperative banks and credit-card SMS are untested |
| Capture | Payment-app notifications (GPay, PhonePe, Paytm, BHIM, CRED, MobiKwik, Freecharge + 7 bank apps); deduped with SMS within 3 min | 3 | **Never tested on a real phone.** Notification text varies with app version and OEM |
| Capture | Capture notification with 3 smart category buttons | 4 | A differentiator: one tap categorises without opening the app |
| Capture | Merchant learning (whole words, ≥4 characters, removed when their category is deleted) | 3.5 | No screen to view or edit learned merchant rules |
| Capture | **Teach a message** (#9): mark the amount/payee or "not a payment" on a misread SMS; rules are checked before the parser and included in backups (Settings → Auto-tracking → Message rules) | 3.5 | New; not yet tested on a device in this study |
| Entry | Manual entry | 3 | Add button has no label and a category is required. Every user hits this |
| Budgets | Category budgets, alerts at a custom threshold and at 100% | 3.5 | Matches what axio and PennyWise give free |
| Bills | Bills, recurring auto-log, loans/EMIs with progress | 3.5 | Auto-log only runs when the app is opened. A bill due today counts as unpaid |
| Bills | **Subscriptions** (#8): detected from payment history; Overview card; page with next date and yearly cost; free "Remind me" turns one into a bill with a reminder 3 days before | 3.5 | New; not yet tested on a device in this study. AutoPay/e-mandate listing and price-change alerts not built |
| Dashboard | Cash Flow Forecast (free to spend), safe-to-spend today, insights | 4 | A differentiator. The safe-to-spend idea is what PocketGuard is praised for |
| Statistics | Monthly/yearly, subcategories, rule-based insights | 3 | No custom date range, no period comparison |
| Goals | Savings goals with deposits | 3 | Fine for now |
| Backup | JSON v2 (complete, atomic restore) + Google Drive auto-backup/restore | 3.5 | Drive restore untested until an OAuth client is registered. No CSV/PDF |
| Security | App lock (re-locks after 30 s), privacy mode, captured text deleted after 7 days, Google auto-backup off | 4 | Database is not encrypted yet (deferred) |
| Widget | Budget Pulse home-screen widget | 3 | One widget |

### 2.2 Top weak spots (in order)

1. **No real-device testing**, especially of notification capture, which is half the core promise.
2. **No inbox backfill.** A new user sees an empty app until the next payment, and the unused READ_SMS permission is hard to justify in the Play declaration.
3. **Manual entry friction**: an unlabelled button and a required category.
4. **No CSV/PDF export** and no **date-range search**. Both are basic expectations in 2026, and axio's broken export is a top complaint.
5. **No accounts or balances, and only basic credit-card handling.** Each expense has a payment-method tag (UPI / Credit Card / Cash) and Statistics splits by it, but paying a card bill counts as spending on top of the card spends it covers, the same double-counting complaint Bishinews gets.
6. **Dark theme only, English only, weak accessibility, Settings sub-page lost on rotation, no tablet layout.**
7. **No monetisation plumbing**: no Play Billing, no feature flags.
8. **Release hygiene**: the `com.example.*` application ID, no release signing or R8, Drive OAuth not registered, and the "MyMoney" name clash.

---

## 3. Competitor landscape

### 3.1 All 24 apps

Legend: AA = RBI Account Aggregator. † = unverified. Ratings are Play India unless noted. L = lakh, Cr = crore.

| # | App | Capture | Free tier | Paid tier | Price (INR) | Model | Play India rating / installs |
|---|---|---|---|---|---|---|---|
| 1 | **axio** (ex-Walnut; Amazon) | Bank/card/wallet SMS on device; no notification capture | Whole tracker: SMS auto-tracking, budgets, bill and card-due reminders, tags, notes, receipts, search | None | ₹0. Loans at 14–35% p.a. (personal-loan block: APR from 18%), processing fee up to 2% + GST, late fee ₹500 or 3%. **No Play IAP** (corrected) | Free, paid for by lending (Pay Later, loans, FDs) | 4.46 (2.66L) · 1Cr+ · **last update 15 Jul 2026** |
| 2 | **Fold** | AA (banks, cards, MFs, stocks, EPF, NPS) + Gmail; account required | Unlimited primary-number accounts; 5 extra refreshes/mo + 1 MF refresh†; no "Contains ads" label (corrected) | Plus: 15 refreshes + 5 MF refreshes†, web app, Radar AI, filtered export, CIBIL | ₹349/mo, ₹799/qtr, ₹999/yr, "Believer" lifetime ₹24,999 | Subscription + lifetime | 4.57 (1.4K) · 1L+ · iOS 4.53 (1,228) |
| 3 | **FinArt** | SMS, email, bank-app notifications, PDF/CSV import; no sign-up; Drive backup | 5-day full trial (confirmed); afterwards probably manual only† | Auto-tracking (SMS, email, Wallet), PDF statement import, family sync, web app, multi-currency, insight budgets, unlimited custom categories, PDF export (App Store list; corrected) | iOS ₹199/mo, ₹1,999/yr. Android prices only shown in-app (IAP ₹10–9,900). Lifetime plans exist (price not public) | Hard paywall after trial; subscription + lifetime | 4.45 (6.2K) · 10L+ · iOS 3.9 (32) |
| 4 | **PennyWise AI** | Bank SMS on device (157–160 banks), on-device LLM, PDF import; open source (AGPL) | Capture, budgets, subscription detection, AI chat, limited rules; no ads | Pro: unlimited rules, PDF imports, CSV export, duplicate-account merge, scheduled backups | ₹149/mo, ₹699/yr, ₹2,000 lifetime (GST incl.), sold as web licence keys; Play billing "currently unavailable" | Freemium; subscription + lifetime | 4.49 (164) · 1K+ |
| 5 | **CRED Money** | AA + card linking; CRED account, 750+ score gate† | Everything: balances, search, recurring payments, duplicate-spend alerts | None | ₹0 (2024 pledge not to monetise†). **No IAP label** (corrected) | Free, paid for by cross-sell (cards, loans, FDs, gold) | 4.76 (29.97L) · 5Cr+ (whole CRED app) |
| 6 | **Jupiter** (Money tab) | AA + SMS read (per Play listing) | Money tab: monthly view, budgets, bills, net worth | "PRO" is a banking tier, not tracking | ₹0. PRO: ₹10K minimum average balance or ₹999 fee† | Free, paid for by bank, card and loans (APR up to 65.91%) | 4.31 (6.14L) · 1Cr+ |
| 7 | **INDmoney** | AA, depositories, SMS† and Gmail scanning | Net worth + expense analysis | None confirmed ("Money Manager Plus ₹2,500–5,000" is a review-site claim†) | ₹0 | Free, paid for by broking, F&O and US-stock FX markup | 4.57 (4.63L) · 1Cr+ |
| 8 | **ET Money** | **None now** (used to parse SMS) | No expense tracking (investments only) | Genius advisory | Genius ₹249/mo (2022 price†) | n/a: tracker removed | 4.43 (2.33L) · 1Cr+ |
| 9 | **Cashew** | Manual + template-based notification/SMS scanning (foreground only†) + Tasker receiver; open source | Nearly everything; on-device, no ads, Drive backup | Pro: more than 1 budget/goal/loan, past budget periods, colour picker. The paywall has an **"Unlock for free"** button (26-second wait), so Pro is honour-system; licence per platform (corrected, from its source code) | IAP ₹150–1,950; monthly, yearly and lifetime (about US$20 lifetime†) | Freemium; subscription + lifetime | **4.9** (27.9K) · 5L+ · iOS 4.86 (622) |
| 10 | **Bluecoins** | Manual; notification/SMS auto-log is Premium-only | Full features with ads; no transaction/account/category caps (only a favourite-accounts card, 6 vs 24) (corrected) | Premium: no ads, PDF/Excel/HTML reports, projections, 5 themes, cloud sync, SMS/notification auto-log | ₹390–950 one-time (higher items look like donations†) | Ads + one-time | 4.3 (40.9K) · 10L+ · ads |
| 11 | **Money Manager** (Realbyte) | Manual only | Full bookkeeping with ads; 15 assets | Separate "Remove Ads" app (unlimited assets, no ads, PC editing); cloud sync is a separate Data Sync subscription, $2.49/mo or $19.99/yr† | ₹99 Remove Ads app (which itself shows ads and has IAP ₹219–1,799; corrected). iOS ₹219/mo, ₹1,799/yr (confirmed). Free app IAP ₹99–1,800 | Ads + one-time + subscription | 4.6 (4.67L) · 1Cr+ · ads |
| 12 | **Expense Manager** (Bishinews) | Manual + bank/card SMS | Everything (splits, receipts, CSV/PDF, Drive sync, Hindi) with ads | Separate Pro app, ad-free | ₹500 one-time (Pro app 4.5 from 2,081 ratings) | Ads + paid app | 4.4 (63.4K) · 50L+ · last update 4 May 2026 |
| 13 | **Monefy** | Manual only | Quick entry, charts; **no "Contains ads" label** (corrected) | Premium: contents not officially listed (custom categories, passcode, sync, multi-currency per reviews†) | iOS India Premium ₹3,599 / ₹5,900 (duration not shown); Play IAP ₹149–3,900. The ₹2,600/yr review figure is dropped. Separate Pro app **no longer listed** (corrected) | Premium (model not stated) | 4.0 (1.95L) · 1Cr+ · Editors' Choice |
| 14 | **YNAB** | Bank import outside India only; manual/file in India | 34-day trial, no permanent free tier | Everything | $14.99/mo (≈₹1,320), $109/yr (≈₹9,600); IAP ₹1,350–10,800 | Subscription | 3.5 (25.4K) · 10L+ |
| 15 | **Monarch** | US/CA bank aggregation | 7-day trial (card needed) | Core; Plus | Core $14.99/mo, $99.99/yr (≈₹8,800); Plus $199.99/yr; IAP ₹1,400–22,400 | Subscription | no rating shown · 5L+ |
| 16 | **Copilot Money** | Bank aggregation; Apple devices only | None (1-month trial) | Everything | $13/mo, $95/yr (≈₹8,400)† (pricing page unverifiable) | Subscription | Not on Play |
| 17 | **Rocket Money** | Plaid (US) | Subscription tracking, 3 budgets | Premium (pay what you want), Premium+ | $7–14/mo†, Premium+ $15/mo†, negotiation fee 35–60%†; IAP range not tied to this app | Freemium + success fee | no rating or install count shown |
| 18 | **PocketGuard** | Bank aggregation; safe-to-spend number | Core features free, per its own Play description (updated 20 Aug 2026; the "free tier removed" claim is third-party only, corrected) | Plus: unlimited budgets and more | $12.99/mo, $74.99/yr (≈₹6,600) (confirmed); Play IAP ₹360–7,700; lifetime $149.99† | Freemium subscription (+ lifetime†) | no rating shown · 1L+ |
| 19 | **Goodbudget** | Manual envelopes; US-only bank sync | 20 envelopes, 1 account, no ads | Premium: unlimited, 5 devices | $10/mo, $80/yr (unverifiable); iOS India "Plus" ₹449–719/mo, ₹3,749–6,300/yr (confirmed) | Freemium subscription | **2.8** (19.2K) · 10L+ |
| 20 | **Spendee** | Manual + EU/US bank sync; now needs internet to open | 1 wallet, 1 budget | Plus; Premium (bank sync) | Plus $14.99/yr, Premium $35.99/yr; iOS India lifetime ₹4,999 (confirmed; other INR items have no duration shown) | Freemium subscription (+ lifetime on iOS) | 4.3 (63.7K) · 10L+ |
| 21 | **Wallet** (BudgetBakers) | Manual + bank sync (EU-centric; flaky in India) | Manual tracking, basic reports | Premium: bank sync, advanced budgets | iOS India ₹199 (monthly†), ₹999/₹1,499 (yearly†), lifetime ₹1,999, 3-year ₹4,999 | Freemium subscription + lifetime | 4.7 (3.88L) · 1Cr+ |
| 22 | **Money Lover** | Manual + paid "Linked Wallet" bank sync; no SMS | 1 wallet, limited budgets, ads | Premium (lifetime); Linked Wallet (subscription) | Lifetime ₹1,999 (iOS India, confirmed); Linked Wallet ₹219–249/mo, ₹2,099–2,149/yr (confirmed) | Ads + lifetime + subscription add-on | 4.2 (2.08L) · 50L+ · ads |
| 23 | **Toshl** | Manual; Medici adds bank import | 2 accounts, 2 budgets | Pro; Medici (bank import) | Pro $19.99/yr (iOS India ₹1,549/yr, ₹219/mo, confirmed); Medici $39.99/yr (₹3,099/yr, ₹389/mo, confirmed); 30-day trial that **needs payment details** | Freemium subscription | 4.5 (31.7K) · 10L+ |
| 24 | **1Money** | Manual only | All features free with ads (since Dec 2025†) | Legacy Premium | IAP ₹65–36,900 | Ads | 3.8 (1.28L) · 50L+ · last update 25 Feb 2026 |

### 3.2 Corrections applied (from the price check)

- **axio:** the "₹150–₹10,800 Play IAP" range belonged to a similar-app card, not axio. axio has **no Play in-app purchases**; that claim is dropped. Added: a personal-loan block with "Interest/APR from 18%", processing fee 2% + GST, late fee ₹500 or 3%.
- **CRED:** there is **no** "In-app purchases" label and no "Contains ads" label on Play India.
- **Monefy:** the separate `com.monefy.app.pro` app is **not listed** on Play India.
- **Money Manager (Realbyte):** the ₹99 Remove Ads app (`com.realbyteapps.moneya`) still shows "Contains ads" and has IAP of ₹219–1,799. The iOS ₹219/month and ₹1,799/year subscriptions are now **directly confirmed**.
- **Fold iOS:** 4.53 from 1,228 ratings; v2.59.0 released 14 Oct 2026.
- **Expense Manager Pro:** 4.5 from 2,081 ratings, updated 2 Oct 2026.
- **PocketGuard:** $12.99/mo, $74.99/yr and the 7-day trial are confirmed in the Play description. The **$149.99 lifetime is not verified**.
- **Toshl:** the 30-day trial needs payment details up front and charges when it ends.
- **Rocket Money:** the ₹390–5,900 IAP range could not be tied to its own listing.
- **Unverifiable:** Copilot ($13/$95), Goodbudget ($10/$80) and Rocket Money ($7–14 / $15 / 35–60% fee). The official pages did not render.
- **Out-of-date figures in the India-pricing research:** "FinArt ≈ ₹800/yr" was a 2024 review figure; iOS now shows ₹1,999/yr. "Money Manager Remove Ads ≈ $5.99" is the list price, but Play India charges ₹99. "Bluecoins ≈ $8 / Money Lover $4.99" are old global prices. Today it is ₹390+ for Bluecoins and ₹1,999 lifetime for Money Lover on iOS India. This report uses the newer figures.

**Second price check (16 Oct 2026):**
- **Name clash confirmed:** "MyMoney—Track Expense & Budget" (`com.raha.app.mymoney.free`, Ananta Raha): 10L+ installs, 4.8 from 67.8K reviews, in-app purchases, no ads label, updated 1 Aug 2026, offline with local backup. Same developer: **"MyMoney Pro - Expense & Budget"** (`com.raha.app.mymoney.pro`), paid app at **₹499**, 10K+ installs, 4.6. Six more near-identical names on Play India: myMoney Expense Tracker Budget (Iced Tea Labs, 10K+), My Money Bank, My Money - Expenses Tracker, MyMoney–Budget Expense Tracker, MyMoney - AI Finance Manager, MyMoney - Finance Tracker.
- **Cashew:** Pro = more than 1 budget/goal/loan, past budget periods, colour picker, with an "Unlock for free" option (26-second wait). Per-platform licence and ₹150–1,950 IAP confirmed.
- **Bluecoins:** the "15 transactions / 13 accounts / 30 categories" caps are not on the official page; free is essentially unlimited apart from ads. SMS/notification auto-log is Premium-only (confirmed).
- **Money Manager (Realbyte):** free version limited to 15 assets (confirmed); cloud sync is a separate subscription ($2.49/mo, $19.99/yr, help-centre snippet†).
- **Monefy:** no ads label on Play India; iOS India Premium ₹3,599 / ₹5,900 (duration not shown); Premium contents not officially listed.
- **Fold:** no ads label; free 5 extra refreshes + 1 MF refresh a month vs Plus 15 + 5 (help-centre snippet†).
- **FinArt:** 5-day trial and iOS ₹199/mo, ₹1,999/yr confirmed; paid list corrected (no "splits" or "2 devices").
- **PocketGuard:** its own listing says core features are free; the "free tier removed" claim is dropped. $149.99 lifetime still †.
- **1Money:** "all features free now" appears only in APK-mirror changelogs; still †.
- **App Store India prices confirmed** for Goodbudget, Money Lover, Toshl, Wallet (lifetime and 3-year), Spendee (lifetime).

### 3.3 What users praise and complain about, across apps

**Praise (what to copy)**
- **Capture that really is automatic.** It is axio's whole appeal ("best tracker") and Cashew users' top request. Users "hate manual entry".
- **Privacy with no login.** Praised for FinArt, PennyWise and Cashew. Fold gets similar trust because AA is regulated.
- **A clean, calm UI and fast entry.** Fold, Cashew, Monefy, Copilot.
- **One number that answers "can I spend?"** PocketGuard's "In My Pocket" (MyMoney's safe-to-spend is the same idea) and YNAB's method.
- **Lifetime value.** 1Money lifetime buyers called it "totally worth it"; Money Lover's lifetime Premium is called good value. Bluecoins buyers are loyal.

**Complaints (what to avoid), ranked by how often they come up**
1. **Capture or sync breaks**: missed banks, duplicates, disconnects (axio, FinArt paying user, Bluecoins "garbage values", Wallet India, Toshl Medici, Monarch). Paying users are angriest: "I paid the app, not my bank."
2. **Upsell, loans and ads crowding the tracker** (CRED card pushes, Jupiter "push for buying a credit card", axio, INDmoney as a "lead-gen funnel").
3. **Paywall betrayal**: free features moved behind payment (ET Money; 1Money†), basics locked (Monefy Premium, ₹3,599+ on iOS India), undisclosed short trials and annual-only plans (FinArt), repeated price rises (YNAB), billing surprises (Rocket Money).
4. **Paid status lost**: ads return after paying (Bluecoins, Money Manager).
5. **Backup, restore and data loss** (Goodbudget Aug 2026, 1Money, Monefy, Realbyte "No Data Available").
6. **Credit-card logic**: double counting (Bishinews) and unclear payment source for UPI-on-credit-card (Fold).
7. **Forced online**: Spendee no longer opens offline.
8. **Neglect and support silence**: axio (no update in 3 months), Toshl (reviews call it abandoned, though it was updated Oct 2026), Wallet support not responding.
9. **Licence does not carry across platforms** (Cashew Android ↔ iOS).

---

## 4. Feature gap matrix (11 most relevant competitors + MyMoney)

Legend: **Y** = yes (free) · **Pro** = paid only · **P** = partial or buggy · **N** = no · **?** = not documented in our research.

### 4.1 Capture, trust and model

| App | Bank SMS | UPI-app notifications | No account | No ads | No loans/cross-sell | iOS | Lifetime option |
|---|---|---|---|---|---|---|---|
| **MyMoney** | Y (5 banks tested) | Y (untested on device) | Y | Y | Y | N (Phase 4) | Planned ₹199 → ₹299 |
| axio | Y | N | ? | Y (no label) | **N** | N | n/a (free) |
| Fold | N (AA + Gmail) | N | N | Y (no ads label) | Y | Y | ₹24,999 |
| FinArt | Pro | Pro (bank apps) | Y | Y | Y | Y | Y (price hidden) |
| PennyWise AI | Y | N | Y | Y | Y | N | ₹2,000 |
| CRED Money | N (AA) | N | N | N (card pushes) | **N** | Y | n/a (free) |
| Cashew | P (templates) | P (foreground†) | Y | Y | Y | Y | Y (~US$20†) |
| Bluecoins | Pro | Pro | Y | N | Y | N | ₹390+ |
| Money Manager (Realbyte) | N | N | Y | N | Y | Y | ₹99 (separate app) |
| Expense Manager (Bishinews) | Y | N | Y | N | Y | N | ₹500 (separate app) |
| Wallet (BudgetBakers) | N | N | N | ? | Y | Y | ₹1,999 |
| Money Lover | N | N | N | N | Y | Y | ₹1,999 |

### 4.2 Features

| App | Budgets | Bills / EMIs | Forecast / safe-to-spend | CSV export | PDF / reports | Accounts + balances | Credit-card handling | Splits | Receipts | Statement import | Subscription detection | Multi-device sync | AI Q&A |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **MyMoney** | Y | Y | **Y** | N | N | N | P (payment-method tag; card bill counted as spend) | N | N | N | **Y** (#8) | N (Drive backup) | N |
| axio | Y | Y | ? | P (broken) | ? | P (SMS balance, buggy) | Y (dues) | ? | Y | ? | ? | ? | N |
| Fold | ? | ? | ? | Pro | ? | Y | Y | ? | ? | N/A (AA) | ? | Y (web Pro) | Pro |
| FinArt | ? | ? | ? | ? | ? | ? | ? | Pro | ? | Pro | ? | Pro | N |
| PennyWise AI | Y | ? | ? | Pro | ? | P | ? | N | ? | Pro | Y | N | Y (on-device) |
| CRED Money | ? | Y | ? | ? | Y (card statements) | Y | Y | ? | ? | N/A | Y | Y | ? |
| Cashew | Y (more Pro) | Y | ? | Y | ? | ? | ? | ? | ? | P (Sheets) | P (reminders) | P (Drive) | P (Gemini entry) |
| Bluecoins | Y | Y | Pro (projections) | P | Pro | Y | Y | ? | ? | ? | ? | Pro | N |
| Money Manager | Y | P (recurring) | ? | Y (Excel) | ? | Y (15 free) | Y | ? | ? | ? | N | P | N |
| Expense Manager | Y | Y | ? | Y | Y | Y | P (double counts) | Y | Y | ? | ? | Y | N |
| Wallet | Y | ? | ? | Y | Pro | Y | ? | ? | ? | Y (file) | ? | Y | ? |
| Money Lover | P (limited) | Pro | ? | Pro | ? | Y | Pro (credit wallets) | ? | ? | ? | ? | Y | ? |

**What the matrix shows.** Among apps with no account and no ads, MyMoney is the only one with **both** SMS and UPI-notification capture **plus** a cash-flow forecast. The clearest gaps against direct rivals (axio, PennyWise, Bishinews, FinArt) are **CSV export, accounts/credit-card handling, splits, receipts and statement import**. On-device AI is what PennyWise uses to stand out; MyMoney now matches it on subscription detection (#8) and user-taught message rules (#9).

---

## 5. Recommendations

### 5a. Improvements ranked by user impact

Effort for a solo dev: **S** ≤ 2 days · **M** ≈ 1–2 weeks · **L** > 2 weeks · **XL** = months.

Phases (as in NOTES.md): **0** new-user experience (done) · **1** Play launch · **2** catch up · **3** stand out and earn · **4** iOS. Deliberate changes from NOTES: splits and receipts move from Phase 2 to 3 (Pro features); PDF reports, advanced rules and extra themes/widgets are in Phase 2 so Pro has three features at launch; custom rules (#9) and subscription detection (#8) are already built.

| Rank | Improvement | User impact | Evidence | Effort | Phase | Tier |
|---|---|---|---|---|---|---|
| 1 | **Real-device capture validation**: test every watched payment app (GPay, PhonePe, Paytm, BHIM, CRED, MobiKwik, Freecharge and the 7 bank apps) on 2–3 OEM phones (Samsung, Xiaomi, OnePlus), or stop watching any that won't be tested; build an SMS sample corpus for 20+ banks (add PNB, BoB, Canara, Union, IDFC, Yes, IndusInd, AU, Federal, credit cards, RuPay-on-UPI); turn every sample into a parser test | Very high | Capture failure is the #1 complaint across categories (axio, FinArt, Bluecoins) | M | 1 | Free |
| 2 | **Play launch blockers**: **rename** the app and the Pro product (clash with the 10L+ `com.raha.app.mymoney.free` and its ₹499 "MyMoney Pro"); choose the final `applicationId`; SMS Permissions Declaration with the "SMS-based money management" exception and a demo video; privacy policy; Data safety form; release signing; R8 with Gson keep rules; crash reporting that matches the Data safety form; move Drive to the new authorization API, register the OAuth client and test restore; closed test with 12 testers for 14 days | Very high (no launch without it) | Play SMS policy; name clash confirmed on Play India | M + 14 calendar days | 1 | n/a |
| 3 | **Inbox backfill**: on onboarding, offer to import the last 90 days of SMS, with dedupe and a review screen. This also justifies READ_SMS (or remove READ_SMS) | Very high: value on day 1 instead of an empty app | axio offers inbox rescans (users say they don't fix missed banks); an unused READ_SMS weakens the Play declaration | M | 1 | Free |
| 4 | **Missed and duplicate tools**: a "Not captured?" list of transaction-like SMS that were skipped, and duplicate review. Users can already teach a misread message (#9); the gap is seeing what was skipped entirely | High: keeps trust when parsing fails | axio's missed bank since Aug 2026 and duplicates; FinArt paid user missing 6 months | M | 1–2 | Free |
| 5 | **Manual entry fix**: labelled "+ Add expense" button, amount first, category optional (defaults to "Uncategorised"), remember the last category | High: every user | Owner audit 3/5; Monefy is praised for the fastest entry | S | 1 | Free |
| 6 | **CSV export** (all data, date-range filter) **plus "Import from MyMoney" CSV** | High | axio "export no longer works"; Money Lover locks Excel/CSV export behind Premium; the 10L+ MyMoney (Raha) app has no cloud, so its users are locked in (see `mymoney-raha-teardown.md`) | S | 1 | Free |
| 7 | **Play Billing plumbing + feature flags**: one-time non-consumable products, handle PENDING (UPI) purchases, acknowledge within 3 days, `queryPurchasesAsync` on every start, cached entitlement, "Restore purchase" button. Ship a ₹99 "Supporter" product to test it | High (business) | PennyWise lost Play billing; Bluecoins paid status reverted | M | 1 | n/a |
| 8 | **Merchant-rules screen**: view, edit and delete learned merchant→category rules, and optionally recategorise past expenses (message rules already have a screen since #9) | High | Owner audit; PennyWise sells "unlimited rules" | S–M | 1–2 | Free (basic) |
| 9 | **Accessibility + rotation bug**: TalkBack labels, font scaling, contrast; keep the Settings sub-page on rotation | Medium-high | Owner audit; Play's pre-launch report is expected to flag these | S–M | 1 | Free |
| 10 | **Auto-log recurring bills in the background** (WorkManager), not only when the app is opened; let a bill be marked paid today | Medium | Known trade-off in NOTES | S | 1 | Free |
| 11 | **Accounts with balances + credit-card handling**: payment source per transaction (bank, card, UPI-on-card); card bill payment treated as a transfer, not spend; statement-cycle view | High | Bishinews double-counting; Fold user wants the source shown; axio balance bugs; MyMoney (Raha) card-payment bug review with 96 upvotes | L | 2 | Free |
| 12 | **Light, system and AMOLED-black themes** | Medium | Dark-only is an accessibility and preference gap; ~7% of MyMoney (Raha) requests ask for themes / true black | M | 2 | Free |
| 13 | **Date-range search and filters** (amount, merchant, account, tag) | Medium | Realbyte users ask for custom date ranges | S–M | 2 | Free |
| 14 | **Database encryption (SQLCipher)** | Medium (trust, backs up the pitch) | Privacy positioning. Deferred by the owner (16 Oct 2026); main trade-off: phone-to-phone transfer stops carrying data | M | Deferred | Free |
| 15 | **AutoPay / e-mandate list and price-change alerts**: subscription detection, yearly cost and renewal reminders are already built (#8). Remaining: list active UPI AutoPay mandates from mandate SMS, and alert when a subscription's price changes | Medium | Subscription tracking is free in PennyWise, CRED and Rocket Money | S–M | 2 | Free mandate list; **Pro** price-change alerts |
| 16 | **PDF/Excel reports + custom-period statistics** (compare months, year in review) | Medium | Bluecoins and Bishinews have reports; Fold Plus has export | M | 2–3 | **Pro** |
| 17 | **Hindi + 1–2 regional languages** | Medium (reach) | Bishinews has Hindi; reaches beyond metro users | M | 2–3 | Free |
| 18 | **Splits / IOUs** | Medium | Requested in PennyWise reviews; FinArt Pro and Bishinews have it | M–L | 3 | **Pro** |
| 19 | **Statement import (PDF/CSV)** with saved column mapping | Medium | PennyWise and FinArt Pro; FinArt's manual mapping is a complaint | L | 3 | **Pro** (first import free) |
| 20 | **Advanced rules engine**: amount ranges, account-based rules, auto-tag, auto-split (building on #9's message rules) | Medium | PennyWise Pro "unlimited custom rules" | M | 2 | **Pro** |
| 21 | **Receipt photos** | Low-medium | axio and Bishinews have it | M | 3 | **Pro** |
| 22 | **On-device AI questions** ("how much on Swiggy this quarter?"). The model is downloaded only after the user opts in, as an on-demand Play Asset Delivery pack (aim for ≤ 500 MB), with no MyMoney server involved | Low-medium, but a differentiator | PennyWise (Qwen 2.5 on device, hundreds of MB); FinArt criticised for "no AI" | L | 3 | **Pro** |
| 23 | Multi-currency (travel) | Low | Cashew, Bluecoins | M | 3 | **Pro** |
| 24 | Extra widgets, themes and icon packs | Low | Bluecoins premium themes; Money Lover icon packs | S–M | 2 | **Pro** |
| 25 | Tablet layout | Low | Owner audit | M | 3 | Free |
| 26a | **Income entries + custom month start** (salary on the 25th → budgets and stats run 25th–24th); enables recurring salary | Medium-high | ~2–5% of MyMoney (Raha) requests; common for Indian salaried users; our monthly income is only a setting today | M | 2 | Free |
| 26b | **Lend / borrow with people** (simple IOUs, separate from loans/EMIs) | Medium | ~3% of MyMoney (Raha) requests | S–M | 2 | Free |
| 26c | **Small entry wins**: duplicate an expense, calculator on the amount field, widget quick-add, 3/6-month and all-time views | Medium (each small) | MyMoney (Raha) has all four; "duplicate record" was its most-thanked 2026 feature | S each | 1–2 | Free |
| 26 | iOS (Kotlin Multiplatform, Apple Shortcuts capture) | Medium (market) | Cashew's cross-platform complaint; Copilot has no Android | XL | 4 | Separate purchase |

### 5b. Free tier and why

**Free forever, for everyone, with no ads:**
- All capture: bank SMS, payment-app notifications, inbox backfill, the capture notification with smart buttons, merchant learning, message rules (#9) and the merchant-rules screen.
- Manual entry, unlimited categories, unlimited history, tags, search and date-range filters.
- Category budgets and alerts, bills, recurring bills, loans/EMIs, savings goals.
- Dashboard: Cash Flow Forecast, safe-to-spend, insights; monthly and yearly statistics.
- Accounts, balances and credit-card handling (Phase 2); subscriptions, yearly cost and renewal reminders (#8); the AutoPay/mandate list.
- CSV and JSON export, Drive backup and restore.
- App lock, privacy mode, database encryption (when it ships), light and dark themes, accessibility, Hindi and regional languages, one home-screen widget.

**Why:**
- **Competitors give all of this away.** axio gives the whole tracker free (SMS, budgets, bills, receipts). CRED Money, Jupiter and INDmoney are free. PennyWise's free tier includes capture, budgets, subscription detection and AI chat. Cashew's free tier is nearly complete, its Pro can even be unlocked free by waiting 26 seconds, and it holds 4.9★. A paywalled core cannot beat a free axio for most users.
- **Locking basics backfires in India.** Monefy keeps basics behind Premium (₹3,599+ on iOS India) and gets 1★ reviews and a 4.0 rating (4.6 on iOS). FinArt charges for custom categories and Cashew for more than one budget; keeping both free is a clear advantage. Under a freemium model, the free tier is how users discover the app and how it earns reviews.
- **Even with 0.7–3% conversion (0.7% is a subscription-app median whose IN/SEA group appears to be grouped by developer region, so treat it as rough), the other 97% of users are worth having.** They bring ratings, word of mouth and "Walnut alternative" search traffic, and with no backend they cost nothing.

### 5c. Paid tier: Pro (to be named with the new app name)

*Revised 18 Oct 2026. This replaces the earlier "₹249 → ₹399, no trial" recommendation after a second round of research on trials, alternative models, India pricing and what Google Play allows without a server.*

**Pro features** (power, polish and convenience; nothing that holds data hostage):
- PDF/Excel reports, comparison statistics, year in review
- Advanced rules engine: amount and account rules, auto-tags, auto-splits
- Bank-statement import (PDF), with the first import free. (CSV import and "Switch from MyMoney" stay free: they are a switching path.)
- Receipt photos
- On-device AI questions (Phase 3)
- Multi-currency
- Subscription price-change alerts (renewal reminders stay free)
- Extra widgets, accent and AMOLED themes, icon packs (the light theme stays free for accessibility)
- All future Pro features. **Lifetime means lifetime**; never repackage it (1Money lesson).

Lend/borrow with people, custom date ranges, salary-cycle months, income and accounts are **free** (see `docs/superpowers/plans/2026-10-16-user-review-gaps-roadmap.md`).

**Recommendation: free core forever + 14-day reverse trial + one-time lifetime Pro.**

| Product / mechanism | Price | How | When |
|---|---|---|---|
| **Supporter** | **₹99** | One-time non-consumable. Supporters get full Pro when it launches; **withdrawn from sale on Pro launch day** so it can't become a cheap back door | Phase 1 (Play launch), to test billing and UPI end to end |
| **Reverse trial** | Free | Every new user gets **all Pro features for 14 days, no card**. Day 15: back to the free tier. Anything created with Pro (receipts, past reports) stays visible. Trial start stored in Google Block Store plus local storage, so it survives a reinstall for most users | From Pro launch (end of Phase 2); existing users get it on that day too |
| **Pro: founder price** | **₹199** | Lifetime non-consumable with a Play **Discount offer** that has real start/end dates | First 60 days after Pro launches |
| **Pro** | **₹299** | Same product | Standard price from then on |
| Price experiment | ₹149 / ₹299 / ₹399 | Play Console price experiment (India), once traffic allows | After launch |
| Founding members | Free | Play promo codes (500 per quarter for one-time products) for closed-test testers and early users. **Never tied to a rating or review** | Launch |
| Tip jar (optional) | ₹49 / ₹99 / ₹199 | Consumables called "Support the developer" (never "donation"); unlocks nothing | Any time; low effort, small money |
| Google Play Pass | — | Apply via "express interest" once ratings are good; Pass members get Pro automatically and you're paid from Google's pool. Acceptance not guaranteed | After launch |
| Subscription | **None** | — | Reconsider only if a feature with real running cost appears, or as a non-renewing prepaid plan if data shows the upfront price is the barrier |

**Paywall moments** (ask when the user has seen value, not with nags):
1. Onboarding: a skippable screen explaining the 14 days of Pro.
2. Trial end (day 15).
3. The first month-end report.
4. Tapping a Pro feature (show a preview).
5. A one-time discount when someone dismisses the paywall.

Always show the real price from Play (`formattedPrice`) and never hide it behind "Start trial". Write a Hindi version of the paywall: localisation was the change most often found to raise revenue (Adapty: 62% of tests).

**Why not "30 days free, then pay ₹100 to keep using"** (the owner's original idea):
1. **Play can't run it.** Free trials exist only for subscriptions, and a subscription that becomes a one-time benefit breaks policy. An app-built trial is reset by a new account or by turning off Google backup.
2. **Few users are left by day 30.** Finance apps keep about 3–6% of users at day 30 on average (AppsFlyer 4.5%, Statista 5.8%; the best apps 15–22%). Revenue estimate: 25K installs × 5–15% left × 15–30% paying × ₹72 ≈ **₹0.1–0.8 lakh**, no better than freemium.
3. **It holds people's own history hostage.** That is the core grievance behind Money Lover and Monefy's worst reviews. Even YNAB keeps export open after its trial. You'd have to keep the app readable anyway.
4. **Free SMS trackers are one tap away** (PennyWise, Vrid, axio).
5. **SMS permission:** the Play declaration is safest when SMS capture is a free core feature.

**Other models considered**

| Model | Verdict | Reason |
|---|---|---|
| Paid upfront | ❌ | ~97% of Play apps are free; a price on install kills downloads |
| Usage caps on entries | ❌ | Breaks the free auto-capture promise. Cap only things users create (e.g. extra budgets) if ever needed |
| Subscription at launch | ❌ | No server for renewal/cancel events; Indians prefer paying once |
| Referral unlocks | ❌ | The referrer can't be rewarded without a server |
| Student pricing | ❌ | Verification needs a server; regional pricing covers it |
| ₹29 month pass (Play "Rent") | ⏸ | Allowed, but the rental expiry is only exposed server-side; test carefully before relying on it |
| Tip jar / Supporter | ✅ optional | Easy; one case earned about $0.02 per download, so it can't carry the app |
| Play Pass | ✅ later | Live in India (₹99/mo for users); keeps IAP for everyone else |

**Honest caveat on reverse trials:** a 2026 benchmark of ~200 products (Kyle Poyar / ChartMogul) found reverse trials *not statistically different* from plain freemium or trials. The one mobile case (RevenueCat) went from 0.4% to 4.5%. The main benefit here is that everyone gets to try Pro with nothing taken away; expect only a modest lift in buyers.

**Evidence of what users pay for:**
- **Ad removal**: Realbyte ₹99, Bishinews ₹500, Bluecoins.
- **Automatic capture**: FinArt, Bluecoins auto-log. MyMoney gives this away free on purpose, as its edge over Bluecoins.
- **Power features**: reports and exports, statement import and unlimited rules (PennyWise Pro), more budgets (Cashew), sync (Bluecoins, Money Lover Linked Wallet).
- **Privacy and no upsell**: Fold Plus sells "if you don't pay, you're the product"; FinArt's "no lending" pitch. Our no-account, no-cloud design makes that argument stronger.

**Revenue sanity check**

Assumptions:
- Net per sale (Play fee 15% after enrolling; GST taken out first, the conservative case): ₹299 → **₹215**, ₹199 → **₹143**, ₹99 → **₹71**. An Indian developer who isn't GST-registered keeps more (₹254 / ₹169 / ₹84) but may owe reverse-charge GST on the fee. **Confirm with a CA.**
- Blended net ≈ **₹186** per payer (40% at the founder price, 60% at ₹299).
- The table covers the **first 12 months after Pro launches**. 0.7% is the RevenueCat IN/SEA day-35 median for subscription apps; 2.2% is the global freemium median. Any lift from the reverse trial or the lower price is unproven.
- No tips, Play Pass income or refunds counted. Costs: $25 one-off Play fee; no servers.

| Scenario | Installs (12 months after Pro launch) | Paying | Payers | Net revenue / yr |
|---|---|---|---|---|
| Low (organic only) | 5,000 | 0.7% | 35 | ≈ ₹6.5K |
| **Base** | **25,000** | **1.5%** | **375** | **≈ ₹0.7 lakh** |
| High ("Walnut alternative" traction) | 100,000 | 3% | 3,000 | ≈ ₹5.6 lakh |

**Conclusion:** revenue depends mainly on installs and ratings, so the free tier, reviews and the privacy story matter more than the price point. Lock in now the one thing that's hard to change later: **the core stays free forever**. Tune the price with experiments after launch.

**Sources for the revision:** Play one-time products and offers https://support.google.com/googleplay/android-developer/answer/16430488 · purchase options https://developer.android.com/google/play/billing/one-time-product-multi-purchase-options-offers · subscriptions policy https://support.google.com/googleplay/android-developer/answer/9900533 · Block Store https://developer.android.com/identity/block-store · promo codes https://support.google.com/googleplay/android-developer/answer/6321495 · price experiments https://support.google.com/googleplay/android-developer/answer/13343030 · Play Pass India https://blog.google/intl/en-in/products/platforms/google-play-pass-now-india-enjoy-1000-apps-and-games-without-ads-or-app-purchases/ · RevenueCat State of Subscription Apps 2026 https://www.revenuecat.com/state-of-subscription-apps · Adapty paywalls 2026 https://adapty.io/blog/high-performing-paywall-2026/ · reverse trials https://amplitude.com/blog/reverse-trial and https://substack.com/@kylepoyar/note/c-209940962 · retention benchmarks https://uxcam.com/blog/mobile-app-retention-benchmarks/ · Ludo King ₹9 pack https://naavik.co/digest/ludo-king-takes-over-the-indian-mobile-games-market/ · Fold pricing https://fold.money/blog/price-of-free-software

### 5d. Never paywall, and why

| Never paywall | Why |
|---|---|
| **Automatic capture** (SMS, notifications, backfill) | It is the core promise and the reason to leave axio. Bluecoins and FinArt paywall it; MyMoney beats them by not doing so |
| **Your data out**: CSV/JSON export, Drive backup and restore, delete everything | Holding data hostage destroys the trust pitch. axio's broken export is a top complaint, and Money Lover locks CSV/Excel export behind Premium |
| **Fixing mistakes**: edit or delete expenses, recategorise, message rules (#9), the merchant-rules screen, the duplicate and missed-transaction tools | Charging people to fix the app's own parsing errors is the "I paid the app, not my bank" anger (Toshl) |
| **Security and privacy**: app lock, privacy mode, encryption, text retention | Paywalling safety looks like extortion and contradicts the positioning |
| **Accessibility**: light theme, font scaling, TalkBack, languages | Exclusion, not a premium feature |
| **Anything that was ever free** | ET Money ("charge you to even track external portfolios"), 1Money† (lifetime → subscription, later reversed; rating 3.8), Monefy (basics locked; 4.0) |
| **Ads, loans and partner offers, in any tier** | It's the brand. CRED, Jupiter and axio users resent upsells, and Bluecoins and Money Manager paying users report ads returning. No "Contains ads" label, ever |

---

## 6. India pricing and payments notes

- **Play Billing in India accepts UPI** (since 2019), cards, net banking, carrier billing and gift cards. **UPI purchases can arrive as PENDING.** Handle `Purchase.PurchaseState.PENDING`, and grant Pro only when the state is PURCHASED. Acknowledge within 3 days, or Play refunds the purchase automatically.
- **UPI AutoPay for Play subscriptions** has existed since 15 Nov 2022 (the first charge is approved in the UPI app, later ones are debited automatically). It only matters if a subscription is ever added. RBI's e-mandate rules (2021) push successful Indian subscriptions towards annual or quarterly plans; we skip monthly and weekly plans.
- **Minimum price is ₹10. INR prices are set by hand** in the Play Console (don't rely on automatic USD conversion). Use India-style endings (₹99, ₹199, ₹299).
- **Fees and tax:** the 15% service fee applies to the first $1M/yr; confirm the enrolment/account-group setup in the Console. Play India prices shown to buyers include tax. Whether Google remits GST for you is **unverified**; check the Console's tax settings before setting prices. Set up the **payments profile and merchant verification before launch** (PennyWise's Play billing has been unavailable since about Sep 2026 after "Transaction Declined [OR-FGEMF-20]"; it now sells Pro outside Play).
- **Entitlement without a backend:**
  - Run `queryPurchasesAsync` on every cold start and on "Restore purchase".
  - Cache the result in encrypted preferences so Pro works offline. If a check fails, keep the last known state; **never re-lock a paying user because of a network error** (Bluecoins lesson).
  - Accept that some piracy will happen. Don't build DRM; a one-time unlock that's cheap compared with the effort of cracking it is defence enough.
- **Cross-platform:** a Play purchase can't be verified on iOS without a backend. Plan for **separate purchases per platform** and say so on the paywall and listing (Cashew users complain when it isn't clear).
- **Spending trend:** Indian IAP revenue is about $1B (2025) and heading to $1.25B (2026). Q2 2026 was +35% YoY, and 68% of H1 2026 revenue was non-gaming. But revenue per download is about $0.03 (vs $4.60 in the US). Payment friction is falling, but willingness to pay is still low. Price small.
- **Reference prices Indians already pay:** Truecaller ₹899/yr†, Google One Lite ₹589/yr, Basic ₹1,300/yr. Expense trackers: ₹99 (Realbyte), ₹390 (Bluecoins), ₹500 (Bishinews), ₹699/yr or ₹2,000 lifetime (PennyWise), ₹999/yr (Fold), ₹1,999 lifetime (Wallet, Money Lover), ₹1,999/yr (FinArt iOS).
- **Listing copy:** lead with "no account, no ads, no loans, never shared with lenders". Pick a crash reporter whose data collection matches the Data safety form, since crash reports do leave the phone. Note that Drive backup does put data in the *user's own* Google Drive. Word the claim as **"never sent to our servers — we don't have any"** rather than "never leaves your phone", so it stays true.

---

## 7. Risks and open questions

| Risk / question | Severity | Mitigation / next step |
|---|---|---|
| **Play rejects the SMS permission declaration** | Critical | Make SMS tracking the advertised core feature; record a clear demo video; actually use READ_SMS (backfill) or remove it. Fallback: a notification-only free tier (weaker for users without UPI-app notifications) |
| `applicationId` is `com.example.expensetracker` | Critical (Play blocks `com.example`) | Choose the final package ID **before** the first upload; it can never change afterwards |
| Name clash with "MyMoney—Track Expense & Budget" (`com.raha.app.mymoney.free`, 10L+, 4.8★ from 67.8K) and its ₹499 "MyMoney Pro" | **Critical** | Rename the app and the Pro product before the first upload; check trademarks and Play search for the new name |
| Closed-testing requirement (12 testers for 14 days) delays launch | High | Recruit testers early and start the 14-day clock as soon as a signed build exists |
| Notification capture fails on real phones / OEM battery killers | High | Real-device test plan (rank 1); "keep alive" guidance for Xiaomi, OnePlus and Samsung; a health indicator in Settings (already partly there) |
| Parsing goes stale as banks change SMS formats | High (axio's failure mode) | Corpus-driven tests; "Report this format" flow; ship parser updates on a regular schedule (visible updates also count against "abandoned" perceptions) |
| Drive restore untested (no OAuth client) | High | Register the OAuth client and test restore on a fresh device before saying "backup" on the listing |
| Purchase verification glitches re-lock paying users | High | Cached entitlement; never downgrade on error; a "Restore purchase" button; tests for PENDING and refunds |
| Free AA trackers (CRED, Fold free, Jupiter) improve | Medium | Compete on no account, no consent pull, no upsell, and capture of UPI payments made from wallet-style apps |
| axio fixes its parsing / Amazon invests | Medium | Lead on privacy and no loans; Amazon ownership makes the "no lender" pitch sharper |
| Low conversion (≈0.7%) | Medium | Keep costs at zero; treat Pro as validation; grow installs through "Walnut/axio alternative" positioning |
| GST and the 15% fee tier not configured as assumed | Medium | Confirm in the Play Console before fixing prices; net figures above assume both |
| Database not encrypted while marketing privacy | Medium | Deferred by the owner; until it ships, don't claim "encrypted" (the old label was already removed). If it ships, exclude the database from phone-to-phone transfer (the Keystore key can't move) |
| iOS: no SMS access, Shortcuts capture is clunky; licences don't carry across | Medium | Price iOS separately; set expectations; consider a one-time cross-platform code only if a backend ever exists |
| DPDP Act 2023 obligations for an app with no backend | Low (unverified) | Local-first minimises scope; get a short legal read before launch |
| **Open questions** | — | What do FinArt's Android ₹ prices look like (only shown in the app)? Are Cashew's exact INR plans and free budget cap known? Has PennyWise's Play billing been restored? Is the PocketGuard lifetime price still on offer? What exactly does Monefy Premium unlock, and is it a subscription? Does axio still push loan pop-ups in the app? |

---

## 8. Sources

**MyMoney (internal)**
- `/Users/rohanth/Desktop/ExpenseTracker/NOTES.md`; `app/build.gradle.kts` (`applicationId = "com.example.expensetracker"`); `app/src/main/AndroidManifest.xml` (READ_SMS, RECEIVE_SMS declared); owner's quality audit (Oct 2026).

**axio**
- https://play.google.com/store/apps/details?id=com.daamitt.walnut.app&hl=en_IN&gl=IN
- https://www.business-standard.com/industry/news/amazon-completes-acquisition-axio-rbi-approval-125090401170_1.html
- https://www.crowdfundinsider.com/2025/09/250024-amazon-completes-acquisition-of-digital-lending-startup-axio/
- https://apps.apple.com/in/app/axio-money-expense-tracker/id6780629197 (unrelated app)

**Fold**
- https://play.google.com/store/apps/details?id=money.fold.marble&hl=en_IN&gl=IN · https://apps.apple.com/in/app/fold-money/id1626994334 · https://help.fold.money/hc/en-us/articles/17262017781522-Is-Fold-a-free-app · https://itunes.apple.com/search?term=fold+money&country=in&entity=software

**FinArt**
- https://play.google.com/store/apps/details?id=com.finart&hl=en_IN&gl=IN · https://finart.app/faq.html · https://apps.apple.com/in/app/finart-ai-expense-tracker/id6748001842 · https://finart.app/best-expense-tracker-apps-india/

**PennyWise AI**
- https://play.google.com/store/apps/details?id=com.pennywiseai.tracker&hl=en_IN&gl=IN · https://github.com/sarim2000/pennywiseai-tracker · https://pennywise.zynth.dev/pro

**CRED**
- https://play.google.com/store/apps/details?id=com.dreamplug.androidapp&hl=en_IN&gl=IN · https://apps.apple.com/in/app/cred-credit-cards-bills-upi/id1428580080 · https://techcrunch.com/2024/07/24/cred-launches-personal-finance-manager-for-indias-affluent/ · https://www.business-standard.com/finance/news/cred-launches-product-to-track-expenses-view-total-bank-account-balances-124072400997_1.html · https://en.wikipedia.org/wiki/Cred_(company)

**Jupiter**
- https://play.google.com/store/apps/details?id=money.jupiter&hl=en_IN&gl=IN · https://apps.apple.com/in/app/jupiter-money/id1507748747 · https://jupiter.money/money/ · https://community.jupiter.money/t/money-tab-improvements-cash-flows-more/48147 · https://community.jupiter.money/t/revamped-pro-experience/42279

**INDmoney**
- https://play.google.com/store/apps/details?id=in.indwealth&hl=en_IN&gl=IN · https://apps.apple.com/in/app/indmoney-stocks-mutual-funds/id1450178837 · https://www.indmoney.com/features/track-savings-account · https://www.indmoney.com/privacy-policy · https://foliyo.ai/guides/mf-platforms/indmoney-review/

**ET Money**
- https://play.google.com/store/apps/details?id=com.smartspends&hl=en_IN&gl=IN · https://theprint.in/ani-press-releases/etmoney-opens-complete-access-to-genius-a-premium-membership-service-that-delivers-personalized-investing-intelligence-for-winning-investment-outcomes/825733/ · https://x.com/ETMONEY/status/1491287596424908805 · https://www.quora.com/Is-the-Money-Manager-Expense-tracker-feature-not-available-in-their-ET-Money-iOS-version

**Cashew**
- https://play.google.com/store/apps/details?id=com.budget.tracker_app&hl=en_IN&gl=IN · https://apps.apple.com/in/app/cashew-expense-budget-tracker/id6463662930 · https://itunes.apple.com/lookup?id=6463662930&country=in · https://cashewapp.web.app/faq.html · https://github.com/jameskokoska/Cashew/issues/205

**Bluecoins**
- https://play.google.com/store/apps/details?id=com.rammigsoftware.bluecoins&hl=en_IN&gl=IN · https://www.bluecoinsapp.com/settings/premium/ · https://www.bluecoinsapp.com/versions/ · https://www.bluecoinsapp.com/settings/sms/

**Money Manager (Realbyte)**
- https://play.google.com/store/apps/details?id=com.realbyteapps.moneymanagerfree&hl=en_IN&gl=IN · https://play.google.com/store/apps/details?id=com.realbyteapps.moneya&hl=en_IN&gl=IN · https://apps.apple.com/in/app/id560481810 · https://apps.apple.com/in/app/money-manager-remove-ads/id564730202 · https://itunes.apple.com/lookup?id=560481810,564730202&country=in · https://www.appbrain.com/app/money-manager-remove-ads/com.realbyteapps.moneya

**Expense Manager (Bishinews)**
- https://play.google.com/store/apps/details?id=com.expensemanager&hl=en_IN&gl=IN · https://play.google.com/store/apps/details?id=com.expensemanager.pro&hl=en_IN&gl=IN

**Monefy**
- https://play.google.com/store/apps/details?id=com.monefy.app.lite&hl=en_IN&gl=IN · https://play.google.com/store/apps/details?id=com.monefy.app.pro&hl=en_IN&gl=IN (not found) · https://apps.apple.com/in/app/monefy-money-tracker/id1212024409 · https://itunes.apple.com/lookup?id=1212024409&country=in

**YNAB**
- https://www.ynab.com/pricing · https://play.google.com/store/apps/details?id=com.youneedabudget.evergreen.app&hl=en_IN&gl=IN · https://itunes.apple.com/lookup?id=1010865877&country=in · https://apps.apple.com/us/app/id1010865877 · https://getfinny.app/blog/ynab-pricing-2026

**Monarch Money**
- https://www.monarch.com/pricing · https://play.google.com/store/apps/details?id=com.monarchmoney.mobile&hl=en_IN&gl=IN · https://apps.apple.com/us/app/id1459319842 · https://www.trustpilot.com/review/www.monarchmoney.com

**Copilot Money**
- https://copilot.money/pricing/ · https://help.copilot.money/en/articles/6860727-managing-your-copilot-subscription · https://help.copilot.money/en/articles/11780342-copilot-money-for-web · https://apps.apple.com/us/app/id1447330651 · https://thecollegeinvestor.com/41976/copilot-review/

**Rocket Money**
- https://www.rocketmoney.com/learn/personal-finance/how-much-does-rocket-money-cost · https://help.rocketmoney.com/en/articles/2217739-how-much-does-rocket-money-cost · https://play.google.com/store/apps/details?id=com.truebill&hl=en_IN&gl=IN · https://apps.apple.com/us/app/id1130616675 · https://www.bbb.org/us/md/silver-spring/profile/billing-services/rocket-money-inc-0241-236043013/complaints

**PocketGuard**
- https://pocketguard.com/pricing/ · https://play.google.com/store/apps/details?id=com.pocketguard.android.app&hl=en_IN&gl=IN · https://apps.apple.com/us/app/id949414211 · https://getfinny.app/blog/pocketguard-pricing-2026 · https://checkthat.ai/brands/pocketguard/reviews

**Goodbudget**
- https://play.google.com/store/apps/details?id=com.dayspringtech.envelopes&hl=en_IN&gl=IN · https://apps.apple.com/in/app/goodbudget-budget-planner/id471112395 · https://goodbudget.com/blog/2024/08/changes-to-goodbudgets-plan-offerings/ · https://www.nerdwallet.com/finance/learn/best-budget-apps

**Spendee**
- https://www.spendee.com/pricing · https://help.spendee.com/article/203-what-is-spendee-plus · https://help.spendee.com/article/202-what-is-spendee-premium · https://play.google.com/store/apps/details?id=com.cleevio.spendee&hl=en_IN&gl=IN · https://apps.apple.com/in/app/spendee-see-where-your-money/id635861140

**Wallet by BudgetBakers**
- https://play.google.com/store/apps/details?id=com.droid4you.application.wallet&hl=en_IN&gl=IN · https://apps.apple.com/in/app/wallet-daily-budget-profit/id1032467659 · https://support.budgetbakers.com/hc/en-us/articles/7151349344018-Everything-about-Premium · https://apps.apple.com/us/app/wallet-daily-budget-profit/id1032467659

**Money Lover**
- https://play.google.com/store/apps/details?id=com.bookmark.money&hl=en_IN&gl=IN · https://apps.apple.com/in/app/money-lover-money-manager/id486312413 · https://apps.apple.com/us/app/money-lover-money-manager/id486312413 · https://note.moneylover.me/what-are-the-difference-between-premium-and-basic-version/ · https://moneylover.zendesk.com/hc/en-us/articles/34879970115353-Your-Passport-to-Premium-Dive-into-14-Days-of-Exclusive-Benefits

**Toshl Finance**
- https://play.google.com/store/apps/details?id=com.thirdframestudios.android.expensoor&hl=en_IN&gl=IN · https://apps.apple.com/in/app/toshl-finance-best-budget/id921590251 · https://developer.toshl.com/docs/free/ · https://toshl.com/blog/free-30-day-trial-with-toshl-pro-and-medici/ · https://toshl.com/pricing/

**1Money**
- https://play.google.com/store/apps/details?id=org.pixelrush.moneyiq&hl=en_IN&gl=IN · https://1money-expense-tracker-money-manager-budget.soft112.com/

**Second price check (name clash and corrections)**
- https://play.google.com/store/apps/details?id=com.raha.app.mymoney.free&hl=en_IN&gl=IN · https://play.google.com/store/apps/details?id=com.raha.app.mymoney.pro&hl=en_IN&gl=IN
- https://github.com/jameskokoska/Cashew/blob/main/budget/lib/pages/premiumPage.dart
- https://help.realbyteapps.com/hc/en-us/articles/10669850586649
- https://play.google.com/store/apps/details?id=com.pocketguard.android.app&hl=en_IN&gl=IN

**India pricing, payments and market**
- Truecaller: https://www.softwr.com/pricing/truecaller · https://www.techjockey.com/detail/truecaller
- Google One India: https://www.deccanherald.com/technology/google-rolls-out-google-one-lite-plan-in-india-3185868 · https://jitendra.co/google-one-plans-india/
- India app spending: https://techcrunch.com/2026/07/31/india-is-starting-to-pay-for-apps-not-just-download-them/ · https://finance.yahoo.com/sectors/technology/articles/india-app-market-booming-global-043845256.html
- Play UPI and UPI AutoPay: https://blog.google/intl/en-in/products/platforms/now-pay-for-subscriptions-via-upi-on-google-play/ · https://inc42.com/buzz/google-play-introduces-upi-autopay-for-subscription-based-purchases-in-india/
- ₹10 minimum and manual INR pricing: https://www.androidpolice.com/2015/07/31/google-play-reduces-the-minimum-app-and-in-app-purchase-price-in-india-from-rs-50-to-rs-10-0-156/ · https://pricepush.app/blog/google-play-iap-pricing-by-country
- Conversion benchmarks: https://www.revenuecat.com/state-of-subscription-apps · https://adapty.io/state-of-in-app-subscriptions-report/
- One-time vs subscription in India: https://rizevault.razorpay.com/p/the-psychology-of-subscriptions-in · https://www.prnewswire.com/in/news-releases/overloaded-89-of-indian-consumers-think-there-are-too-many-subscription-services-to-choose-from-now-301937411.html
- User sentiment (Walnut/axio and others): https://www.trackmyrupee.com/blog/trackmyrupee-vs-walnut-axio-vs-money-manager-which-expense-tracker-is-best-for-indians-in-2026/ · https://themoneytrack.com/vs/walnut · https://medium.com/@sumitkhannacs/walnut-app-alternative-a9428b42e09c (title/snippet only)
- Play SMS/Call Log permission policy: https://support.google.com/googleplay/android-developer/answer/10208820?hl=en
