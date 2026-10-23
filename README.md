# Spend Tracker

Android app that logs payments into your Google Sheet (`Finances_NS_test`) and shows the
rewards/returns for the card or method you paid with.

```
Android app  --HTTPS POST-->  Apps Script web app  -->  your Google Sheet
 (Compose)                    (apps-script/Code.gs)      Monthly Expenses / CC bill
```

The app never touches the formula columns. It writes only the inputs; your sheet formulas
(Return %, Actual Returns, Monthly Returns) keep doing the calculating, and the app reads the
results back.

## 1. Set up the Apps Script (about 3 minutes)

1. Open the sheet > **Extensions > Apps Script**.
2. Replace the default code with the contents of `apps-script/Code.gs`.
3. Change `SECRET` (top of the file) to a long random string. Keep it; the app needs the same value.
4. **Deploy > New deployment > Web app**. Execute as: **Me**. Who has access: **Anyone**.
5. Authorize when asked, then copy the **Web app URL** (ends in `/exec`).

"Anyone" access is protected by your SECRET: requests without it are rejected.
After any later edit to the script, use **Deploy > Manage deployments > Edit > New version**.

## 2. Build the Android app

1. Install Android Studio. Open the `android/` folder as a project and let Gradle sync.
2. Plug in your phone (USB debugging on) and press Run, or use
   **Build > Build APK(s)** and install the APK from `app/build/outputs/apk/debug/`.
3. In the app open **Settings**, paste the Web app URL and the SECRET, then "Save and test connection".

## Screens

- **Home**: month's spend, total returns, return %, and a per-method breakdown. Applies the
  ₹2,000/month cap on HDFC Rewards Debit / Debit2 card-bill cashback, as your Monthly Returns tab does.
- **Expenses**: the month's items. Tap one to mark it paid (paid by, date, amount, method) with a live
  reward preview. The + button adds an ad-hoc item.
- **Card bills**: each card's bill for the month; mark it paid with an amount and the method you used.
- **Cards**: payment-method rates and your card list (rates per category, fees, bill and due dates).
- **Settings**: connection details.

## What gets written to the sheet

| Action | Tab | Columns written |
|---|---|---|
| Mark expense | Monthly Expenses | C Paid By, D Paid On, E Amount, F Payment mode |
| Mark card bill | CC bill | C Bill Amount, D Payment method |

Starting a new month: the first payment you mark copies the previous month's block of rows
(with formulas) into the sheet, then fills in that payment.

## Known limits

- Rows whose Date cell is stored as text rather than a real date are skipped by the script.
- The app needs internet; there is no offline queue yet.
- Build config (Kotlin 1.9.24, AGP 8.5.2, Compose BOM 2024.06) was written without being compiled in
  the authoring environment. If Android Studio offers an upgrade, accepting it is fine.
- Command-line build (needs the Android SDK installed): in `android/`, run `gradlew.bat assembleDebug`
  on Windows (or `./gradlew assembleDebug`). The APK lands in `app/build/outputs/apk/debug/`.
