# Play Console answers for Thriftpot

Drafts for every form Play asks for before the first release, based on what the code does as of the release-signing PR. If the app changes (a crash reporter, an SDK, a new permission), revisit the Data safety and SMS sections.

Sources: [Data safety help](https://support.google.com/googleplay/android-developer/answer/10787469), [SMS and Call Log policy](https://support.google.com/googleplay/android-developer/answer/10208820), [Permissions Declaration Form](https://support.google.com/googleplay/android-developer/answer/9214102).

## 1. Release build

- Upload an Android App Bundle: `./gradlew bundleRelease`, output `app/build/outputs/bundle/release/app-release.aab`.
- It's signed with the **upload key** in `~/.thriftpot/thriftpot-upload.jks`. The passwords are in `~/.thriftpot/keystore.properties`. Back both up to a password manager now. A lost upload key can be reset through Play support, but it takes days.
- When Play asks, accept **Play App Signing**, which lets Google keep the app signing key. Don't upload your own app signing key.
- Upload `app/build/outputs/mapping/release/mapping.txt` with each release if Play doesn't pick it up from the bundle. Crash reports are only readable with it.
- Raise `versionCode` in `app/build.gradle.kts` for every upload.

## 2. Privacy policy

`docs/store/privacy-policy.md`. Before you publish, fill in the three placeholders:
- `[DEVELOPER NAME]`: the name on your Play developer account.
- `[SUPPORT EMAIL]`: a support email.
- `[DATE OF FIRST RELEASE]`: the release date.

Play needs a public URL that isn't a PDF. Options:
- A page on thriftpot.app once the domain is registered (best).
- GitHub Pages from this repo (works today; the URL shows the GitHub username).
- A free Google Sites page.

## 3. SMS Permissions Declaration (Policy > App content > Sensitive permissions)

**Permission:** `RECEIVE_SMS` only. The app does not request `READ_SMS`, `SEND_SMS` or any call log permission.

**Core functionality:** select **"SMS-based money management"**.

**Why the app needs it.** Paste this into the description box:

> Thriftpot is an expense tracker whose core feature is logging the user's spending automatically from the debit SMS their bank sends. When a new SMS arrives, the app checks it on the device. If it is a debit message from a bank or payment service, the app records the amount, payee and date as an expense, then shows a notification so the user can pick its category with one tap. OTPs, promotional messages and personal messages are ignored. The app does not read the SMS inbox or history, never sends SMS, and has no server: message content never leaves the device and is deleted from it after 7 days. Before Android asks for the permission, an onboarding screen explains what the app reads and shows an example, with a "Not now" option. Without the permission, the user can still add expenses by hand.

**Instructions for app review.** Paste this:

> No login or account is needed. Open the app, tap "Get started", then "Allow SMS access", and allow it. Skip the other steps. Then send the test device an SMS like: "Rs.512.00 debited from a/c **4821 on 26-11-26 to VPA swiggy@icici (UPI Ref No 432918774512)". A notification "New expense: ₹512" appears with category buttons, and the expense shows under Activity. On an emulator: adb emu sms send HDFCBK "<message>".

**Access:** "All functionality is available without special access."

**Video.** The demo video is the `store/sms-demo-video.mp4` file next to this repo, recorded from the release build on an Android 17 emulator. It shows, in order:
- the first launch;
- the screen that explains SMS access, with an example;
- Android's permission prompt;
- an incoming bank SMS;
- the "New expense: ₹512" notification with its category buttons;
- picking "Food & Dining";
- the logged expense on Home and in Activity.

Upload it to YouTube as **Unlisted** and paste the link. YouTube is Google's preferred format; a Drive link to the MP4 also works. Re-record it on a real phone if you can, which looks more convincing to a reviewer.

**Risk.** This exception is granted at Google's discretion. If it's refused, the fallback is a build without `RECEIVE_SMS` that relies on notification capture and manual entry.

## 4. Data safety (Policy > App content > Data safety)

What the app sends anywhere:
- **Google Drive backup.** Optional, off until the user connects an account. Sends expenses, income, categories, bills, loans, goals, message rules and settings to the user's own Drive app folder over HTTPS. SMS text is excluded.
- Nothing else. There are no analytics, ads or crash-reporting SDKs. The only network code is `DriveBackupClient`, and `INTERNET` is used only for it.

Play defines "collect" as transmitting data off the device, "irrespective of whether data is transmitted to you or a third-party server". Its only exemptions are on-device processing and end-to-end encryption. The Drive backup isn't end-to-end encrypted, so **declare it**. Declaring it is the safe choice: an under-declared form is a policy violation, an over-declared one isn't.

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | Yes |
| Is all of the user data collected by your app encrypted in transit? | Yes |
| Do you provide a way for users to request that their data is deleted? | Yes. The user deletes it on the phone (Reset everything) and in Drive (Settings > Manage apps > Thriftpot > Delete hidden app data). The privacy policy explains both. |

Data types to tick:

| Data type | Collected | Shared | Optional? | Purpose |
|---|---|---|---|---|
| Financial info > Purchase history | Yes | No | Optional (users can choose) | App functionality (backup) |
| Financial info > Other financial info (income, loans, budgets) | Yes | No | Optional | App functionality (backup) |

Leave everything else unticked. That includes:
- **Messages > SMS:** read on the device and never transmitted.
- **Personal info > Email:** Google sign-in stays on the device.
- **App activity, Device IDs, Crash logs:** the app collects none of these; Google Play's own vitals are Google's, not yours.

**Processed ephemerally?** No. It's stored in Drive.

## 5. Other App content forms

| Form | Answer |
|---|---|
| Ads | No, the app does not contain ads |
| App access | All functionality is available without special access |
| Content rating questionnaire | Category: "All other app types". Answer No to every content question (no violence, sexual content, gambling, user-generated content or sharing between users). The app doesn't let users talk to each other, share location or buy digital goods. |
| Target audience | 18 and over |
| News app | No |
| Health apps | No health features |
| Government app | No |
| Financial features | Choose the option that says the app provides no financial services (it doesn't lend, pay, invest or hold money; it only records spending). If the form offers a "personal finance / budgeting" choice, pick that instead. This is a guess: check the current form wording. |
| Data deletion URL | The privacy policy URL, which explains both deletion steps. Play only requires a deletion URL for apps with accounts. |

## 6. Store listing (Grow > Store presence > Main store listing)

**App name** (30 max): `Thriftpot: Expense Tracker`

**Short description** (80 max):

> Spending logged from bank SMS and UPI. No account, no ads, stays on your phone.

**Full description** (4000 max):

> Thriftpot logs your spending for you. When your bank texts you about a debit, or Google Pay, PhonePe or Paytm shows a payment, Thriftpot adds it as an expense and asks for its category with one tap from the notification. No typing, no spreadsheets.
>
> Your money, your phone. There's no account to make, no ads, and no loans. Thriftpot has no servers, so your data is never sent to us. We don't have anywhere to send it. Backups go to your own Google Drive only if you turn them on.
>
> WHAT IT DOES
> • Reads debit SMS from Indian banks as they arrive, and payment notifications from GPay, PhonePe, Paytm, BHIM, Amazon Pay, CRED and more
> • Ignores OTPs, offers and personal messages
> • Sorts payments into categories, and learns your payees as you go
> • Teach it a bank message it reads wrong, and it remembers
> • Category budgets with alerts before you go over
> • Bills and EMI reminders, with bills logged on their due day
> • Spots your subscriptions, and tells you when one gets more expensive
> • Income, salary day and a "free to spend" forecast for the month
> • Savings goals
> • Monthly, yearly and custom-range insights
> • Budget month that starts on your salary day
> • A home screen widget
> • App Lock with fingerprint or face, and privacy mode
> • Export to CSV for Excel or Google Sheets, back up to a file or your Google Drive
>
> Meet the pot. Thriftpot's clay pot smiles while you're within budget, and gets sadder as you get close to your limit.
>
> PRIVACY
> SMS and notifications are read on your phone, and their text is deleted after 7 days. Nothing is uploaded unless you turn on Google Drive backup, which saves to a hidden folder in your own Drive.

**Category:** Finance. **Tags:** Budget, Expense tracker, Personal finance.

**Contact details:** a support email (required). Add the website once thriftpot.app is live.

**Graphics still needed:**
- **App icon:** 512 × 512 PNG, rendered from the pot mascot (see `tools/render_icons.py`).
- **Feature graphic:** 1024 × 500.
- **Phone screenshots:** at least 2, and 4 to 8 are better. Take them from the release build with realistic data, following the approved Marigold store screenshot concepts.

## 7. Before production: closed testing

A new personal developer account must run a closed test with at least 12 testers, all opted in, for 14 days in a row before it can apply for production access. Start it as soon as the forms above are accepted.
