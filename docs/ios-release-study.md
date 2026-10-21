# iOS / App Store Release Study

*18 Oct 2026. Three research passes: India iOS vs Android revenue, automatic capture on iPhone, and App Store pricing/policy. Official Apple sources where possible; **†** = unverified or third-party. Companion to `competitor-study.md` (§5c pricing) and the roadmap in `superpowers/plans/2026-10-16-user-review-gaps-roadmap.md`.*

---

## 1. Summary

- **The plan works on the App Store with changes**, but **the App Store won't be the main income in India.** "Revenue mostly comes from the App Store" is true globally (≈70/30), not in India, where ~94% of phones in use are Android.
- **Estimated iOS revenue: roughly 10–35% on top of Android revenue** (estimate, not a published figure). iOS pays for its own developer fee (~₹8,700/yr) only once Android reaches about **25–30k installs a year**.
- **Automatic capture is weaker on iPhone.** No app can read SMS or other apps' notifications. The workaround (an Apple Shortcuts automation the user sets up once) gives roughly **70–80% of the Android experience** for users who complete setup.
- **Pricing changes:** the 14-day Pro trial is *officially supported* on iOS (a ₹0 "14-day Trial" item tied to the Apple ID, so reinstalling can't reset it). Indian iPhone users accept higher prices: **₹299 founder → ₹399–499 lifetime**. Pro is a **separate purchase per platform** (no server to share it).
- **Cost and effort:** Apple Developer Program ~₹8,700/yr, a Mac (Xcode 26 is required for uploads since 28 Apr 2026), a real iPhone, and roughly **6–10 weeks** for one developer using Kotlin Multiplatform.
- **Recommendation:** launch Android first; start iOS once Android reaches ~25k installs a year (or earlier for reach rather than revenue).

---

## 2. Is the App Store where the money is? (India)

| | Globally | India |
|---|---|---|
| Consumer spend split | App Store ≈ $117.6B vs Google Play ≈ $49.2B in 2025 (≈70/30) | No free public split; Google Play very likely larger (estimate) |
| Share of phones in use | — | **iOS 6.05%**, Android 93.89% (StatCounter, Sep 2026) |
| Share of new phones sold | — | iPhone **9%** in 2025 (record, ~14M units; 28% by value), **7%** in Q2 2026 (Counterpoint) |
| Download → paid (day 35) | iOS **2.6%** vs Android **0.9%** (RevenueCat 2026); trial → paid equal (~32.5%) | India + SEA **1.4%** overall; no platform split |
| Revenue per download | — | ~**$0.03** for all of India (Sensor Tower) |

- Indian in-app spending passed **$1B in 2025**; Q1 2026 >$300M (+33%), non-games >$200M (+44%).
- **Who buys iPhones in India:** ~**75% of iPhone sales are in metro cities** (Delhi, Mumbai, Bengaluru ≈ a third); **1 in 4** buyers use EMI. Reachable audience for a finance app: an estimated 10–20M metro, salaried iPhone users (estimate).
- **iOS India prices for comparable apps:** Money Lover lifetime **₹1,999** (842 ratings, a small audience); Fold Plus ₹349/mo, ₹799/qtr, ₹999/yr; FinArt ₹199/mo, ₹1,999/yr†.
- Secondary claims (unverified): Indian App Store revenue grew 18.6% in 2024 vs 7.3% for Play; iOS ≈ 25% of Indian in-app revenue.

**Estimate framework (all estimates):**
- iOS installs ≈ **4–9% of Android installs** (device share; a finance app may sit at the higher end).
- iOS paid conversion ≈ **2–3× Android**.
- iOS revenue ≈ install ratio × 2–3 ≈ **0.1–0.35× Android revenue**.
- Worked example: 10,000 Android installs × 1% × ₹249 ≈ ₹21k net → about 600 iOS installs × 2.5% ≈ ₹3.2k net.
- Break-even on the developer fee: ~40 iOS Pro sales a year (~1,600 iOS installs) ≈ 25–30k Android-equivalent installs a year. A Mac and your time cost extra.

**Android lesson from this research:** the iOS/Android conversion gap comes from fewer Android users ever reaching the paywall (trial → paid is equal). Show the Pro offer on day 1 on Android too (the onboarding trial explainer already does this).

---

## 3. Automatic capture on iPhone

### What iOS allows

| Question | Answer |
|---|---|
| Read SMS/iMessage content? | **No.** No API in iOS 18/26/27. FinArt: "iOS doesn't allow apps to read SMS in the background." |
| Read other apps' notifications (GPay, PhonePe, Paytm)? | **No.** iOS has no equivalent of Android's notification listener. |
| SMS filter extension (`ILMessageFilterExtension`)? | **No.** Sees only unknown senders, can't write to the shared App Group, can only classify; its network option needs a server and would likely be rejected for this use. |
| FinanceKit (Wallet transactions)? | **Not in India.** US and UK only, with an Apple entitlement. |
| Account Aggregator? | **Not feasible.** The data user must be RBI/SEBI/IRDAI/PFRDA-regulated and receives data on its servers. |
| Gmail API? | Restricted scope → yearly CASA assessment (~$540–5,400+). Avoid. |

### Ranked capture options

| # | Option | Friction per transaction | Reliability |
|---|---|---|---|
| 1 | **Shortcuts "Message" automation → our App Intent, parsed on the phone.** Filter by keywords ("debited", "spent", "Rs"), set to run without confirmation and "Allow Running When Locked". The intent saves the expense and posts a local notification with category buttons (recreating the Android capture notification). | Zero after a 2–3 minute one-time setup | Medium-high when unlocked; weaker when locked; plain SMS only (not iMessage/RCS); sender filters often miss bank IDs, so use keywords; may need more than one automation† |
| 2 | **Quick-add surfaces:** interactive widgets (iOS 17+), Control Center control (iOS 18+), Lock Screen widget, Action button / Back Tap, Siri ("log ₹200 auto") via App Shortcuts | ~2–5 s | Always works; depends on habit |
| 3 | **Share sheet / screenshot reading** of UPI success screens with Apple's Vision OCR (optionally Apple's on-device model on supported phones†) | ~5 s | High |
| 4 | **Bank-statement PDF import** (PDFKit, password-protected PDFs) to catch misses monthly | Monthly | High |
| 5 | Shortcuts "Email" automation for card-alert emails in Apple Mail | Setup only | † whether it passes the body |
| 6 | Shortcuts "Transaction" (Apple Pay tap) automation | Zero | Works, but Apple Pay in India (launched 30 Sep 2026) is **Axis Bank credit cards only**: no UPI, no RuPay |

Setup must be excellent: a short video, an iCloud link to a ready-made shortcut, and a "send yourself a test SMS" check that confirms it works. An app cannot create the automation itself.

### What competitors do on iOS

| App | Capture | Setup | Notes |
|---|---|---|---|
| FinArt | Shortcuts SMS, Apple Pay trigger, email, PDF statements | ~10-minute guided setup | 4.0 from 33 ratings; its privacy label lists emails and text messages as collected and linked to the user (server processing) |
| Xyra | Shortcut POSTs each SMS to a personal server URL | 5 steps | Its docs warn about locked phones and SMS-only |
| Finvot | Shortcut passes SMS to its App Intent, then server-side AI | Keyword filter, Run Immediately | Docs include "doesn't trigger with real SMS" troubleshooting |
| Fold | Account Aggregator | Bank consent | 4.5 from 1.2k ratings |
| Axio Money (iOS) | Manual + widgets, Siri, Back Tap | — | No automatic capture listed |

**Our advantage:** all three Shortcuts-based rivals send SMS to their servers. Ours would parse on the phone, so the privacy label can honestly be **"Data Not Collected"**.

**Verdict:** about 70–80% of the Android capture experience for users who complete setup; UPI notifications can't be captured (bank SMS covers most UPI debits); no backfill of old SMS. Lean on fast manual entry and statement import.

---

## 4. App Store pricing and policy

### What changes vs the Android plan

| Part of the plan | Android | iPhone |
|---|---|---|
| Free core forever | ✅ | ✅ |
| 14-day Pro trial | App-built timer stored with Google Block Store (resettable by some users) | **Officially supported** (Guideline 3.1.1): a ₹0 non-consumable named "14-day Trial". User taps once to start; tied to the Apple ID, so reinstalling can't reset it. Must show the length, what stops working and the full price before it starts. The free tier remaining afterwards is allowed |
| Founder price | Play Discount offer with dates | **Price schedule** (founder price now, standard price on a set date). Intro/promotional offers exist only for subscriptions |
| Price | ₹199 → ₹299 | **₹299 → ₹399–499** (iPhone rivals charge ₹999–1,999) |
| Codes | Play promo codes (500/quarter) | **Offer codes** for one-time purchases: up to 10 active offers, 1M codes/quarter, in-app redemption on iOS 16.3+ |
| Checking purchases without a server | Signature check + cache | **Easier:** StoreKit 2 verifies signed transactions on the phone; `Transaction.currentEntitlements` excludes refunded/revoked items; listen to `Transaction.updates` from launch |
| Fee | 15% after enrolling | 15% via the **Small Business Program** (enrol; applies from 15 days after the fiscal month of approval) |
| GST | Depends on your registration (ask a CA) | Apple collects and remits Indian tax |
| Net per sale (18% GST included in price†) | ₹199 → ₹143, ₹299 → ₹215 | ₹199 → **₹143**, ₹299 → **₹215**, ₹399 → ~₹287, ₹499 → ~₹359 |
| Cross-platform Pro | — | **Separate purchase per platform.** Guideline 3.1.3(b) allows it in principle, but proving an Android purchase needs a server; a home-made unlock code breaks the license-key rule |
| Must add | — | "Restore purchases" button (`AppStore.sync()`); privacy manifest (`PrivacyInfo.xcprivacy`); decide on **Family Sharing** (cannot be turned off once on); age-rating questionnaire |
| Tip jar | Consumables | Consumables (no UPI tips outside IAP) |

### App Review risks
- **4.3 Spam:** crowded categories get "indistinguishable" rejections. Lead with what's different: India/UPI focus, on-device only, no ads or loans.
- **4.2 Minimum functionality:** low risk for a full ledger.
- **2.5.11 / Shortcuts:** intents the app actually handles are fine. Make the SMS automation optional and explain it clearly.
- **5.1.1(ix) regulated industries:** not applicable (a personal ledger provides no financial service). Never add loans or credit.
- **Privacy:** "Data Not Collected" is valid only if nothing leaves the phone — no analytics or crash SDKs that send data off-device. iCloud private sync probably still "not collected"†.
- **Account deletion:** not applicable (no accounts).
- **EU (DSA trader status):** only if listed in EU storefronts; leave them out to avoid publishing contact details.

### Costs and logistics
- Apple Developer Program **US$99/yr (~₹8,700)**†. Individuals in India enrol only through the Apple Developer app; your legal name is shown as the seller. Organisations need a D-U-N-S number, a legal entity, a domain email and a website.
- **A Mac is required:** uploads must use **Xcode 26 / iOS 26 SDK** since 28 Apr 2026. Cloud CI can build, but a **real iPhone** is needed to test the Shortcuts automation.
- TestFlight is free; external testers need Beta App Review.

---

## 5. Building it

- **Compose Multiplatform on iOS is stable** (since 1.8.0, May 2025). **Room supports iOS** through Kotlin Multiplatform (BundledSQLiteDriver + KSP; DAO methods must be suspend or Flow on iOS). Check the current stable Room version before choosing†.
- **Shared:** SMS/message parsers, database, repositories, most screens.
- **Android-only:** SMS receiver, notification listener, WorkManager jobs.
- **Native Swift:** WidgetKit widgets and controls (SwiftUI), App Intents, any share extension, StoreKit 2. The app and its extensions share the database through an App Group; widgets have tight memory limits, so read from a light Swift layer†.
- **Effort:** roughly **6–10 weeks** for one developer to a polished first version (estimate).

---

## 6. Recommendation

1. **Launch Android first** and finish the roadmap PRs.
2. **Show the Pro offer on day 1 on Android** (already in the onboarding plan).
3. **Start iOS once Android reaches ~25k installs a year**, or earlier if iPhone users are wanted for reach rather than revenue.
4. **iPhone version 1:** Shortcuts SMS capture with excellent guided setup; quick-add widgets, Siri and Control Center; screenshot and statement import; Apple's official 14-day trial; lifetime Pro at **₹299 founder → ₹399–499**, sold separately from Android.
5. **Watch Android revenue per install for one or two quarters** before committing; there is no public India store split for finance apps.

---

## 7. Sources

**Market**
- StatCounter India mobile OS share: https://gs.statcounter.com/os-market-share/mobile/india
- iPhone 2025 India record (Counterpoint via TechCrunch): https://techcrunch.com/2026/01/23/apple-iphone-just-had-its-best-year-in-india-as-the-smartphone-market-stays-broadly-flat
- Q2 2026 iPhone shipments: https://www.mactech.com/2026/07/17/indias-iphone-shipments-in-india-dipped-3-year-over-year-in-quarter-two-with-its-market-share-reaching-7/
- India app market (Sensor Tower): https://techcrunch.com/2026/04/22/indias-app-market-is-booming-but-global-platforms-are-capturing-most-of-the-gains/ · https://sensortower.com/blog/india-mobile-market-q1-2026
- Global store split: https://www.getpanto.ai/blog/google-play-statistics
- Android paywall gap: https://www.revenuecat.com/blog/engineering/android-paywall-gap · https://www.revenuecat.com/state-of-subscription-apps
- iPhone buyers by city / EMI: https://www.indiatvnews.com/technology/news/25-of-iphone-buyers-opt-for-emi-75-of-sales-come-from-metro-cities-report-2025-09-10-1007615
- Money Lover iOS India: https://apps.apple.com/in/app/money-lover-expense-tracker/id486312413 · Fold iOS: https://apps.apple.com/in/app/fold-money/id1626994334
- Secondary/unverified: https://www.synapseindia.com/article/ios-vs-android-app-roi-comparison · https://www.mirava.io/blog/apple-app-store-price-tiers-explained

**Capture**
- Shortcuts automations (iOS 27 guide): https://support.apple.com/en-bh/guide/shortcuts/apdfbdbd7123/10.0/ios/27 · communication triggers: https://support.apple.com/guide/shortcuts/communication-triggers-apdd711f9dff/ios · transaction trigger: https://support.apple.com/guide/shortcuts/transaction-trigger-apd65c67538a/ios
- Apple Pay India launch: https://www.apple.com/in/newsroom/2026/09/apple-pay-launches-in-india/ · https://techcrunch.com/2026/09/29/apple-pay-set-to-launch-in-india-with-axis-bank-today-sources-say/
- FinanceKit: https://developer.apple.com/financekit/
- Account Aggregator FIU rules: https://sahamati.org.in/fiu/
- Gmail CASA cost: https://deepstrike.io/blog/google-casa-security-assessment-2025
- Competitors: https://apps.apple.com/in/app/finart-ai-expense-tracker/id6748001842 · https://xyratrack.in/docs/iphone-automation · https://finvot.app/en/docs/sms · https://apps.apple.com/in/app/fold-money/id1626994334?see-all=reviews
- Compose Multiplatform iOS stable: https://blog.jetbrains.com/kotlin/2025/05/kodees-kotlin-roundup-compose-multiplatform-for-ios-is-stable/ · Room KMP: https://developer.android.com/kotlin/multiplatform/room
- Xcode 26 requirement: https://developer.apple.com/news/upcoming-requirements/

**App Store policy and pricing**
- App Review Guidelines (3.1.1 trials, 3.1.3(b), 4.3, 5.1.1): https://developer.apple.com/app-store/review/guidelines/
- StoreKit entitlements / updates: https://developer.apple.com/documentation/storekit/transaction/currententitlements · https://developer.apple.com/documentation/storekit/transaction/updates
- Family Sharing for IAP: https://developer.apple.com/help/app-store-connect/configure-in-app-purchase-settings/turn-on-family-sharing-for-in-app-purchases
- IAP pricing and offers: https://developer.apple.com/help/app-store-connect/reference/pricing-and-availability/in-app-purchase-and-subscriptions-pricing-and-availability/
- Offer codes for IAP: https://developer.apple.com/help/app-store-connect/manage-in-app-purchases/create-offer-codes-for-in-app-purchases/
- Small Business Program: https://developer.apple.com/app-store/small-business-program/
- India tax / equalization levy: https://developer.apple.com/news/?id=rob1vlg0 · https://developer.apple.com/help/app-store-connect/reference/reporting/payment-information/
- App privacy details: https://developer.apple.com/app-store/app-privacy-details/
- Program enrollment: https://developer.apple.com/help/account/membership/program-enrollment/
