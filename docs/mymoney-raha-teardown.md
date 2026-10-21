# Competitor teardown: "MyMoney" by Ananta Raha

*16 Oct 2026. Based on both Play listings, the "What's new" notes, the Data safety pages, the privacy policy, and **11,237 Google Play reviews from 2025–2026**. Our-app column checked against the code on `main` (after PRs #1–#9). Anything marked † is unverified.*

This is the app whose name we share. It is the main reason we have to rename before launch (see `competitor-study.md`, sections 1 and 7).

---

## 1. Product facts

| | Free (`com.raha.app.mymoney.free`) | Pro (`com.raha.app.mymoney.pro`) |
|---|---|---|
| Installs | 10L+ (scraper count about 2.65M) | 10K+ (about 46.6K) |
| Rating | 4.77 from 67,769 ratings | 4.62 from 6,568 ratings |
| Price | Free. **No ads and no in-app purchases** (Play shows neither label) | ₹499 one-time, no subscription |
| Version / last update | 6.6, 1 Aug 2026 | 6.6-pro, 1 Aug 2026 |
| Released | Nov 2019 | Nov 2019 |

- **Developer:** Ananta Raha, a solo developer in Jhenaidah, Bangladesh. Contact is a Gmail address.
- **Website:** `anantaraha.github.io/mymoney` is empty apart from a privacy policy (last updated Jan 2022). The FAQ exists only inside the app.
- **Platforms:** Android phone only. No iOS version found; reviewers ask for iOS, desktop and web.
- **Languages:** English only† (from the listing and reviews asking for other languages).
- **Data safety:** no data shared. Collects device IDs, crash logs and diagnostics through Firebase Crashlytics; encrypted in transit; users can't request deletion. Internet is used only for crash reports.
- **Release history** (estimated from the first review seen on each version†):

| Period | Releases |
|---|---|
| Sep 2023 – Aug 2024 | 5.1 → 5.6 |
| Mar 2025 | 5.7, 5.8 |
| Mar 2025 – Mar 2026 | **No updates for 12 months** |
| Mar – Aug 2026 | 5.9 (UI redesign), 6.1, 6.2, 6.3–6.5, 6.6: seven releases in five months |

### Free features
- Income and expense records with unlimited custom categories. Custom icons (limited set), currency symbol and decimal places.
- Unlimited accounts (wallet, cards, savings) with transfers between them.
- Monthly budgets per category.
- Charts: pie chart, cash-flow chart, account bar chart. Daily, weekly and monthly views.
- Calculator on the entry screen, search, optional carry-over of the monthly balance.
- Home-screen widget showing the balance, with quick add. Daily reminders to log spending.
- Fully offline. Manual local backup and restore, CSV/"worksheet" export.
- Recent additions: notes shown in the records list (v6.2, May 2026), **duplicate record** (v6.5/6.6, Jul 2026), Android 17 target (v6.6).

### What Pro adds (nothing removed; neither version has ads)
- More icons and multiple themes.
- **Passcode lock.**
- Smarter input from the widget.
- **3-month, 6-month and yearly views.**
- PDF export† (inferred from a "PDF export issues (Pro)" changelog line).

### What neither version has
- **Recurring transactions.** The in-app FAQ reportedly says they will never be added† (three reviewers quote it).
- Cloud backup or sync, and any login.
- Subcategories, multi-currency, receipts, CSV import, SMS capture, credit-card cycles, debt tracking, custom month or date ranges.

---

## 2. Review sample

| | Free | Pro |
|---|---|---|
| Unique reviews scraped | 12,494 (newest back to Oct 2024, plus most-relevant) | 2,949 (full history back to 2019) |
| Analysed (1 Jan 2025 – 15 Oct 2026) | 10,159, of which 83% are 5★ and 339 are 1–3★ | 1,078, of which 98 are 1–3★ |

- **Combined:** 11,237 reviews. 1,700 are complaints or requests (1–3★, or containing "wish", "please add", "missing" and similar).
- **Method:** keyword theme-tagging, plus reading every 1–3★ review (437) and the 250 most-upvoted 4–5★ reviews that make requests.
- Shares below are approximate percentages of those 1,700 reviews unless stated. About half of all reviews are very short ("good app").

---

## 3. What users say

### 3a. Feature requests, ranked

| # | Request | Share of the 1,700 | Quote |
|---|---|---|---|
| 1 | **Cloud backup, sync or login** (Google Drive, auto-backup, multiple devices) | ~10% (169) | "I lost all the past data as my phone got reset" |
| 2 | **Recurring transactions** | ~7.5% (127); 11% of 1–3★ | "Keep the 5 bucks… THINK about automated data entry" (1★ Pro) |
| 3 | More icons and themes; true black / AMOLED dark mode | ~7% | "still no simple amoled black & white themes" |
| 4 | Receipt or bill photo | ~4% | "please add camera photo" |
| 5 | CSV import, bank-statement import, PDF export | ~3.5% | "cannot import .CSV file from banking institution" |
| 6 | Subcategories | ~3% | "only major feature… missing is subcategories" (5★, 19 upvotes) |
| 7 | Debt and lending tracking | ~3% | "debt management… loans advances" |
| 8 | Custom month start / budget period (salary on the 25th, bi-weekly pay) | ~2–5% (keyword count likely undercounts) | "my salary cycle is 25th to 24th" |
| 9 | Custom date range or all-time view | ~2% | "What if I want to see my last 13 days?" |
| 10 | Payee field; payee or notes shown in lists | ~2% | "There's no payee field, so that goes in notes" |
| 11 | Credit-card billing cycle and payments | ~1.5% | Paying a card "is ADDED to your credit card balance" (96 upvotes) |
| 12 | SMS or UPI auto-capture | ~1.4%; 2.3% of 1–3★ | "Wish it cld jst read SMS… track basic UPI" |
| 13 | iOS, desktop or web | ~2% | "I switched to iOS… all my Android data lost" |
| 14 | Family or shared entry | ~1.3% | "I can't share the app with my wife" |
| 15 | Savings goals, bill reminders, mark-as-cleared / reconcile | ~1–2% each | |

### 3b. Complaints, ranked (share of the 437 reviews rated 1–3★)

1. **No recurring entries** (~11%), made worse by the FAQ's "never": "Tells you IN AN FAQ AFTERWARDS that it will NEVER be added."
2. **Data loss with no cloud or auto-backup** (~10% for backup and sync; ~4% explicitly report losing data): "3 year data get vanish at once."
3. **Features locked in Pro, or Pro seen as poor value** (~6%). Yearly view, icons and passcode are paid: "yearly not available for free"; "The paid version has little to offer."
4. **Confusing setup, no tutorial, too many taps** (~5–7%). An account must be chosen every time and there is no default: "Entering every spend is boring… heck with it."
5. **Missing currencies / no multi-currency** (~4%). Several say GBP is missing†.
6. **Wrong balances** (~2–4%): opening balance ignored, future-dated entries deducted early, carry-over not working, credit-card payments handled wrongly.
7. **Bugs** (~3%): export or restore failing, a startup crash after the 16 Apr 2026 update, entries saved to today instead of the chosen date.
8. **Backlash against the 2026 redesign** (~2%): "Transfer option removed"; "please go back to older UI."
9. **No support response** (~1.6%): "paid customer… emailed a month ago no response."

### 3c. Praise (9,820 free and 980 Pro reviews rated 4–5★)

- **Simple and easy to use:** about 31% of 4–5★ reviews.
- **Clean, attractive UI:** about 8–11%.
- **Generous free tier with no ads:** "free" about 5%, "no ads" 4%. "free AND no ads… from the bottom of my heart, thank you."
- **Budgets per category:** 7–12%. **Custom categories:** 5–11%.
- **Charts and analysis:** 3–6%.
- **Multiple accounts and transfers:** 2–4%.
- **Offline, no bank linking, no sign-up:** about 1%, but strongly felt.
- **Calculator on entry, one-time Pro price, daily reminders:** smaller mentions.

### 3d. Developer behaviour
- **No replies to reviews** in 2025–2026; the last replies were in 2021 (free) and Jan 2023 (Pro). Some users report emails going unanswered, and no features are promised publicly.
- **But he ships from reviews.** The 2026 burst delivered notes in the list and duplicate record, both long-standing requests, and reviewers noticed: "previous comments were taken into consideration"; "Most awaited feature, Finally, duplicate record." Duplicate record is positioned as the stand-in for recurring transactions.

---

## 4. Comparison with our app

Checked against the code on `main` after PRs #1–#9. ✅ = we have it · ⚠️ = partly · ❌ = no.

| Their request or complaint | Us | What to do |
|---|---|---|
| Manual entry is tedious; wants SMS/UPI capture | ✅ **Core feature** | Lead marketing with "never type an expense again" |
| No recurring transactions ("never") | ✅ Auto-logged recurring bills | Position against their "never". Recurring *income* needs income entries (below) |
| No cloud backup → data loss | ✅ Daily Drive auto-backup + restore (untested until the OAuth client is registered) | Register OAuth and test restore before claiming it; prompt for restore on a new phone |
| Basics paywalled (yearly view, passcode) | ✅ Both free | Highlight it |
| Confusing setup, too many taps | ✅ Onboarding (#3) + one-tap categorising from the capture notification | Keep entry to one tap |
| Subcategories | ✅ | — |
| Savings goals, bill reminders, forecast | ✅ | Ahead of them |
| Payee / where-spent | ✅ Merchant learning | Show the merchant in every list |
| Accounts with balances, transfers, credit-card logic | ❌ (payment-method tag only) | **Build** — their most fragile area (96-upvote card bug) |
| CSV import/export, PDF | ❌ JSON only | **Build CSV export + "Import from MyMoney" CSV** — a switching path for locked-in users |
| Income entries | ❌ (monthly income is a setting) | **Build** income records (needed for recurring salary and real cash flow) |
| Custom month start (salary cycle) | ❌ | **Build** — cheap, high value for Indian salaried users |
| Themes: light, AMOLED black | ❌ Dark only | Add light + true black |
| Custom date range / 3–6 month / all-time views | ❌ Monthly + yearly | Add a date-range picker to Statistics and search |
| Receipt photo | ❌ | Medium priority; store locally |
| Debts / lending with people | ⚠️ Loans/EMIs only | Add simple "lent to / borrowed from a person" |
| Duplicate record | ❌ | Cheap |
| Calculator on the amount field | ❌ | Cheap |
| Widget quick add | ❌ (Budget Pulse widget, no add) | Cheap |
| Multi-currency | ❌ | Low priority for India |
| iOS, desktop, family sharing, languages | ❌ | Neither app has these; low priority |

### Lessons (what to avoid)
- Never announce that a feature will "never" be added.
- Don't ship a redesign that moves core buttons (their v5.9 backlash).
- Don't paywall basics like the yearly view or the app lock.
- Answer reviews; they haven't since 2023, so it's an easy way to stand out.

### The name
A well-loved app with the same name, the same offline pitch and a ₹499 "MyMoney Pro" already exists. We need our own name for the app and for its Pro tier.

---

## 5. Unverified
- PDF export being Pro-only (inferred from the changelog).
- GBP being missing.
- The exact wording of the in-app FAQ (known only from reviews).
- Whether the 3/6-month or yearly views were ever free (one reviewer says they were removed from the free app).
- English-only UI.
- Release dates (estimated from the first review on each version).

## 6. Sources
- https://play.google.com/store/apps/details?id=com.raha.app.mymoney.free&hl=en_IN&gl=IN
- https://play.google.com/store/apps/details?id=com.raha.app.mymoney.pro&hl=en_IN&gl=IN
- https://play.google.com/store/apps/datasafety?id=com.raha.app.mymoney.free (and `.pro`)
- https://anantaraha.github.io/mymoney/privacy.html
- iTunes Search API and the developer's GitHub (`anantaraha`), checked for an iOS version or website; neither has one.
- Raw review data (local, not in the repo): `/tmp/mymoney-raha/*.json`.
