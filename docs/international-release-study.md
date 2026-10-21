# International Release Study

*Research done 18 Oct 2026 (three passes: country fit, release process and Indian tax, willingness to pay and competition). Saved 21 Oct 2026. Official Apple / Google / regulator sources where possible; **†** = estimate, unverified or third-party; **[CA]** = needs a chartered accountant. Companions: `competitor-study.md`, `ios-release-study.md`, and the roadmap in `superpowers/plans/2026-10-16-user-review-gaps-roadmap.md`.*

---

## 1. Summary

- **Yes, an individual developer in India can sell worldwide on both stores, and the process is simple:** tick countries, set prices per country, fill in the tax forms. Apple and Google are the merchant of record and collect most foreign sales taxes.
- **Whether people pay depends on whether the core feature (reading bank SMS) works there.** It does in the **Gulf, Pakistan and Sri Lanka**. In the **US, UK and Europe** it barely matters, but privacy-focused buyers there pay **$15–20 lifetime** for simple trackers.
- **Apple: 79% of Indian developers' App Store earnings came from users outside India (2024).** For this app, that suggests the iPhone version's revenue would come mostly from Western privacy buyers, not Indian SMS users.
- **Before going abroad, build:** multi-currency, per-country bank-SMS formats, non-Indian number formatting, and per-country prices set by hand.
- **Plan:** India → Gulf (UAE + Saudi) and Indians living there → Pakistan and Sri Lanka → an opportunistic Western listing. Skip Africa and Southeast Asia for now.
- **Competitor to watch:** **FinArt** (Indian SMS tracker, 1M+ installs, 4.3★) is already expanding into the UAE, Sri Lanka, Indonesia and Africa.

---

## 2. Where the core feature fits

### Are transaction SMS alerts the default?

| Market | Status | Evidence |
|---|---|---|
| **Pakistan** | **Required.** Since Jan 2019 banks and microfinance banks must send free, immediate SMS and email alerts for all digital transactions | [Nation](https://www.nation.com.pk/30-Nov-2018/no-charges-for-sms-alerts-from-jan-sbp), [Dawn 2022](https://www.dawn.com/news/1670058), [BR 2025](https://www.brecorder.com/news/amp/40336992) |
| **Saudi Arabia** | **Required and on by default** (opt-out in writing). Since 2020 SAMA standardizes the message fields across 20+ transaction types, so parsing is easier | [SAMA SMS](https://rulebook.sama.gov.sa/en/instant-sms-notification-service), [SAMA standardization](https://rulebook.sama.gov.sa/en/standardization-notification-elements-sent-financial-institutions-customers) |
| **UAE** | **Required** (free SMS for every transaction, CBUAE). Risk: Emirates NBD said in late 2025 it would move small-amount alerts, then card alerts, to app push | [CBUAE rulebook](https://rulebook.centralbank.ae/en/rulebook/consumer-protection-standards), [Gulf News](https://gulfnews.com/business/banking/emirates-nbd-to-replace-sms-otps-with-enbd-x-app-authentication-1.500291590) |
| **Bangladesh** | Required for card/ATM transactions; bKash and Nagad confirm by SMS | [BB 2013](https://www.bb.org.bd/mediaroom/circulars/psd/sep022013psd02.pdf), [BB 2016](https://www.bb.org.bd/mediaroom/circulars/psd/feb142016psd01e.pdf) |
| **Kenya, Ghana, Uganda, Tanzania** | De facto: the mobile-money SMS is the transaction record | [PesaSense](https://pesasense.co.ke/), [Ghana MoMo](https://dev.to/theboylexis/-how-i-parse-15-momo-sms-formats-with-95-accuracy-47hg) |
| **Nepal, Sri Lanka** | Common; Sri Lanka's central bank only encourages it | [smspasal](https://smspasal.com/blog/how-banks-in-nepal-use-sms/), [CBSL](https://www.cbsl.gov.lk/en/news/adopting-safe-and-secure-electronic-payment-practices) |
| **Nigeria** | Weakening: SMS alerts cost ₦4–6 each; customers move to free email/push | [BusinessDay](https://businessday.ng/news/article/bank-sms-charge-increase-customers-opt-for-e-mails/) |
| **Vietnam, Indonesia** | Moving to in-app alerts (Vietcombank stops small-amount SMS; BCA ending SMS for some customers from Feb 2026) | [VNS](https://vietnamnews.vn/economy/1638688/banks-adjust-sms-banking-fees-encourage-switch-to-apps-for-notifications.html), [BCA](https://www.bca.co.id/en/informasi/news-and-features/2025/11/19/07/19/informasi-penutupan-layanan-sms-notifikasi) |
| **Malaysia, Philippines** | SMS OTPs banned; transaction alerts likely app-first too† | [Malaysiakini](https://www.malaysiakini.com/news/637086), [GMA](https://www.gmanetwork.com/news/money/economy/992561/no-more-sms-email-otps-for-high-risk-financial-transactions-starting-june-25/story/) |
| **US, UK, EU, AU, CA** | Bank-sync apps dominate (UK open banking 16.5M users, Dec 2025; US apps on Plaid at $13–15/month); SMS alerts opt-in | [PaymentExpert](https://paymentexpert.com/2025/12/17/uk-open-banking-16-m-users-vrps/), [CNBC](https://www.cnbc.com/select/best-budgeting-apps/) |

### Android share (StatCounter, Sep 2026 unless noted)
Kenya 95.2% · Bangladesh 93.5% · Pakistan 92.8% (Apr 2026) · Sri Lanka 84.9% · Egypt 83.4% · Nigeria 81.9% · Nepal 75.0%† · UAE 73.3%† · **Saudi Arabia 48.4% (iOS 51.6%)**, so most Saudi users would depend on the iPhone Shortcuts workaround.

### Spending signals
- **Gulf:** in-app revenue across the six Gulf states reached $700M in Q2 2025 (+20% YoY; UAE +26%); finance-app downloads +30% ([Sensor Tower](https://sensortower.com/blog/middle-east-app-growth-report)).
- **Pakistan:** 2.3B Play downloads in 2025; carriers (Jazz, Telenor, Zong) let users pay for Play purchases from phone credit, up to about Rs2,000/month.
- **Bangladesh:** Play doesn't accept bKash/Nagad, so selling Pro needs a card or gift card.

### Indians living abroad
- 35.4M overseas Indians; NRIs: UAE 3.55M, Saudi 2.46M, US 2.08M, Canada 1.02M, UK 0.37M ([MEA](https://www.mea.gov.in/images/attach/NRIs-and-PIOs_1.pdf)).
- Indian banks partly still text them (HDFC ₹0.20 + GST per SMS or free email; ICICI ₹0.15 per SMS; some banks send nothing to foreign numbers, and foreign carriers can block Indian short codes).
- Many Gulf NRIs keep an Indian SIM in a dual-SIM phone†, so the India parser already works for their Indian accounts. Adding UAE/Saudi bank formats and a ₹ + AED/SAR view gives a **two-country tracker no local rival offers**. Rough reach: 0.6–1.2M Gulf NRIs†.

### Adapting per market
- **Bank formats:** every country needs its own templates and upkeep (one developer needed 15+ templates across 3 telcos for Ghana). Saudi is easiest (SAMA-standardized).
- **Language:** Pakistan, Bangladesh, Kenya and Sri Lanka bank SMS are mostly English†. Gulf messages are Arabic and/or English, possibly with Arabic-Indic digits.
- **Formats:** drop Indian lakh grouping (1,00,000) outside India; handle `Rs.`, `PKR`, `AED`, `SAR`, `KES`, `Ksh`, `Tk`.
- **Google Play SMS policy** is global: "SMS-based money management" is an allowed exception everywhere, with the Permissions Declaration Form and a demo video ([Play](https://support.google.com/googleplay/android-developer/answer/10208820)). *(One research pass suggested avoiding `READ_SMS`; that contradicts the official policy and is wrong.)*

---

## 3. Will they pay?

### Regional benchmarks

| Region | Download → paid (day 35, median) | Year-1 revenue per payer | Median annual subscription | Suggested lifetime Pro |
|---|---|---|---|---|
| North America | 2.6–2.8% | $32 | $39.99–40.99 | $14.99–19.99 |
| Western Europe / UK | ~2.0% | $25 | $44 | €12.99–17.99 / £11.99–14.99 |
| Middle East | n/a† | n/a | ~$30 | AED/SAR 29–49 (~$8–12) |
| India / SE Asia | 0.7–1.4% (sources conflict) | $14 | $18.32 | PKR 799–1,499; LKR ~1,500; BDT 299–499 |
| Africa | ≤ IN/SEA† | n/a | ~$30 (MEA bucket) | KES 499–999; NGN 2,500–4,500† |

Sources: [RevenueCat 2026](https://www.revenuecat.com/state-of-subscription-apps), [Adapty 2026](https://adapty.io/state-of-in-app-subscriptions/), [RevenueCat lifetime guide](https://www.revenuecat.com/blog/growth/lifetime-subscriptions). Local prices are PPP-style estimates†. RevenueCat also reports refunds of 7.7% in IN/SEA vs 3.4% in North America.

### Competitors in the SMS-alert markets

| Market | Global apps there | Local SMS / mobile-money trackers | Read |
|---|---|---|---|
| Pakistan | Money Manager (top in PK App Store), Spendee | MyHisaab 10K+ ($3.49), FinArt, Kharcha Book (iOS) | Thin; no strong local leader |
| UAE / Saudi | Money Manager, Wallet, Spendee | FinArt, Laffu 50+, [Masareef](https://apps.apple.com/us/app/masareef-auto-expense-tracker/id6806735875), Expensly, Say | Many launched 2025–26, none at scale |
| Sri Lanka | — | [Kiwi Money](https://www.kiwi-money.com/) 10K+ (3.9★, $2.99–199.99), Money Master, SpendSense LK | Small, contested |
| Bangladesh | Cashew, Wallet, Money Manager | "bKash Budget" 100+, Hisaab Kitaab 100+ | Nearly empty, low willingness to pay |
| Kenya | Money Manager | PesaSense 1K+, CountPesa 5K+, Mpesa Ledger 10K+, Shmoney, Finjira, FlowWise | Crowded and fragmented |
| Nigeria | Money Manager, PiggyVest/Kuda | FinArt, Auritrack | FX and payment friction |
| Indonesia / Philippines | Money Manager, Money Lover, Sepran, Money+ | none notable | Wallet notifications rather than SMS |

**US/UK/EU:** demand for privacy-first, no-bank-linking trackers is real (Cashew 500K+ installs at 4.8★ and $20 lifetime; Money Manager, Monefy 10M+; 1Money 5M+; Spending Tracker, Money Tracker 1M–5M+), but the category is saturated and SMS isn't a differentiator there.

**Apps from emerging markets that sold abroad:** Money Lover (Vietnam; biggest user bases in the US and Italy), Bluecoins (Philippines solo developer), Realbyte Money Manager (Korea), Monefy (Ukraine). [Apple India ecosystem study](https://www.apple.com/newsroom/pdfs/2025/04/Apple-Ecosystem-In-India.pdf): 79% of Indian developers' App Store earnings came from users abroad (2024).

### What users abroad expect that this app lacks (inferred†)
1. Bank sync (the #1 Western expectation; irrelevant in PK, BD, KE, NG).
2. **Multi-currency** (critical for Gulf expats, remittance users, travellers).
3. Shared / household budgets.
4. Receipt scanning.
5. Local language (Arabic for the Gulf, Bahasa for Indonesia).
6. Export / backup (expected by privacy users; already built).

### Verdict by region

| Region | SMS capture works? | Will they pay? | Lifetime price | Verdict |
|---|---|---|---|---|
| **UAE / Saudi Arabia** | ✅ Mandatory alerts; Saudi standardized | ✅ High spending, tiny rivals | ~AED/SAR 39 (~$10) | **Best target** |
| **Indians in the Gulf** | ✅ Indian SIM keeps working | ✅ | Gulf price | **Natural extra market** (₹ + AED/SAR) |
| **Pakistan, Sri Lanka** | ✅ Pakistan mandatory; mostly English | ⚠️ Low prices | ~$3–5 | Good second step |
| **US / UK / Europe** | ❌ Bank-sync culture | ✅ $15–20 normal | $15–20 | Opportunistic privacy-first listing |
| **Kenya, Nigeria** | ✅ | ❌ Low; crowded (Kenya) | <$3 | Skip for now |
| **Bangladesh, Nepal** | ✅ | ❌ Hard to pay on Play (BD) | — | Installs, not revenue |
| **Indonesia, Philippines, Vietnam** | ❌ Moving to in-app alerts | ⚠️ | — | Skip unless wallet notification capture is proven |

---

## 4. How to release abroad

### App Store
1. **Tax and banking** (App Store Connect → Business): tax questionnaire → **W-8BEN** (non-US individual; without it US sales are withheld at 30%; India–US treaty Article 12 caps it at 15%, or possibly 0% as business profits — check the first statement†). Add an Indian bank account; payouts in INR.
2. **Small Business Program:** enrol for 15% commission.
3. **Merchant of record:** Apple collects and remits VAT/GST/sales tax in 70+ countries, including India, the EU and the UK ([taxes](https://developer.apple.com/help/app-store-connect/making-payments-to-apple/understanding-taxes/)). No foreign VAT registration for you.
4. **Pricing:** Apple auto-equalizes from a base storefront across ~175 storefronts. **Set prices by hand** in target countries, or ₹299 becomes about $3.50 in the US.
5. **Availability:** Pricing and Availability → Countries or Regions; untick unwanted storefronts.
6. **EU DSA trader status:** declare a status. Selling IAPs in the EU makes you a trader, and Apple publishes your address (P.O. box allowed), phone and email ([Apple DSA](https://developer.apple.com/help/app-store-connect/manage-compliance-information/manage-european-union-digital-services-act-trader-requirements)). To avoid that, untick the 27 EU storefronts and declare non-trader.
7. **Export compliance:** HTTPS/OS encryption only → `ITSAppUsesNonExemptEncryption = NO`; France's ANSSI declaration doesn't apply ([Apple](https://developer.apple.com/help/app-store-connect/manage-app-information/overview-of-export-compliance)).
8. **Privacy:** privacy policy URL and label ("Data Not Collected" if nothing reaches you).

### Google Play
1. **Developer verification:** required since 30 Sep 2026 for Brazil, Indonesia, Singapore and Thailand; global in 2027 ([blog](https://android-developers.googleblog.com/2026/03/android-developer-verification-rolling-out-to-all-developers.html)).
2. **Payments profile:** Indian bank account and US tax info†; enrol in the 15% service-fee tier.
3. **Tax:** Google collects VAT/GST in the EU, UK, US states, Australia, Japan, Korea and many more. For India-based developers it deducts TDS and GST TCS on Indian sales ([Play tax](https://support.google.com/googleplay/android-developer/answer/138000)).
4. **Pricing and countries:** set prices per country (don't rely on auto-conversion); Production → Countries/regions.
5. **EEA:** contact email and phone shown on Play; payments-profile address shown for monetizing developers† ([EEA](https://support.google.com/googleplay/android-developer/answer/14659200)).
6. **App content:** financial-features declaration ("no financial features" or "Other"; licence rules only cover loan apps, [help](https://support.google.com/googleplay/android-developer/answer/13849271)); privacy policy; Data safety; SMS declaration (global).
7. **Drive scope:** keep `drive.appdata` (non-sensitive); the full `drive` scope triggers a paid security assessment.

### Country restrictions

| Country | App Store | Play | Action |
|---|---|---|---|
| China mainland | Needs an MIIT ICP filing (in practice a Chinese entity) | Play not available | **Exclude** |
| Russia, Belarus | Payment problems since 2022† | Paid apps / IAP suspended† | **Exclude** |
| EU (27) | DSA trader disclosure | Contact details shown | Accept disclosure or exclude |
| Brazil, Indonesia, Singapore, Thailand | — | Developer verification now | Verify |
| South Korea, Vietnam | Extra licences only for games | Same | Nothing for a ledger |
| Iran, Cuba, North Korea, Syria, Crimea | No storefront | No storefront | n/a |
| Japan | Seller-info page (特商法) may be needed† | — | Optional |

### Privacy and regulation
- **GDPR / UK GDPR:** with no personal data reaching you, obligations are minimal; still publish a privacy policy (data stays on the device, Drive backup goes to the user's own account, Apple/Google handle purchases; not directed at children). No DPO or EU representative needed.
- **CCPA:** doesn't apply ($25M+ revenue or selling data thresholds).
- **Financial regulation:** a personal ledger with no money movement, advice or credit is not a regulated financial service in the EU, UAE, Pakistan or Kenya†. Never add loans or credit.

### Indian tax and banking (all [CA])
1. **GST:** foreign IAP sales are zero-rated exports (OIDAR; place of supply = recipient). **Below ₹20 lakh** aggregate turnover, no registration needed. **At ₹20 lakh+**, register and file an **LUT (RFD-11) every year** to export without IGST. Open questions: whether the recipient is Apple/Google or the end user, and how Play TCS interacts with Apple remitting GST on Indian sales.
2. **Income tax:** business income. From 1 Apr 2026 the Income-tax Act 2025 section 58 replaces 44AD/44ADA (old 44AD: 6% deemed profit on digital receipts up to ₹3 crore). Claim US withholding credit with Form 67; check TDS in Form 26AS.
3. **Foreign exchange (FEMA):** under the FEMA Export Regulations 2026 (from 1 Oct 2026), proceeds must be realised within 9 months, with a monthly EDF filed with your bank† ([taxguru](https://taxguru.in/rbi/fema-export-import-regulations-2026-key-effective-1-october-2026.html)). Ask the bank for e-FIRA/eBRC per payout and the right purpose code (probably P0807†).
4. Keep monthly Apple/Google reports as export evidence.
5. **Hire a CA** before receipts approach ₹20 lakh, or now for the FEMA reporting.

---

## 5. What to build before going abroad
1. **Multi-currency** (expense currency + home currency, a ₹ + AED/SAR view for NRIs; manual or periodically updated exchange rates, no server).
2. **Per-country bank-SMS formats** with a test corpus per country (start with UAE and Saudi; then Pakistan, Sri Lanka). Users' "teach a message" rules (#9) help cover gaps.
3. **Locale-aware number and currency formatting** (no lakh grouping outside India; currency strings).
4. **Per-country pricing** set by hand on both stores.
5. **Arabic interface for the Gulf** later (bank SMS there are often in English).
6. Store listings per region: Gulf/NRI positioning ("Track your Indian and UAE accounts in one place"), privacy-first positioning for the West.

---

## 6. Recommended plan
1. **India first** (Android launch, the roadmap PRs).
2. **Gulf (UAE + Saudi) and Indians living there:** multi-currency + Gulf bank formats; ~$8–12 lifetime.
3. **Pakistan and Sri Lanka:** ~$3–5 lifetime.
4. **Western countries:** list as a "privacy-first tracker, no bank linking" at $15–20 lifetime on both stores; low cost to try, but SMS won't sell it there.
5. **Skip Africa and Southeast Asia** until wallet-notification capture is proven.

**Open questions:** whether UAE banks keep moving alerts to app push; how many Gulf NRIs keep an Indian SIM; real per-country revenue per download (needs a paid Sensor Tower/Appfigures account); whether Apple applies 15% or 0% US withholding.
