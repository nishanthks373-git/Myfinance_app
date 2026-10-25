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

- **Home**: month's returns, spend and return %, per-method breakdown (tap a row to expand), and a
  return-% bar chart by month (tap a bar for that month's spent, returns and return %). Applies the ₹2,000/month cap on HDFC Rewards Debit / Debit2 card-bill
  cashback, as your Monthly Returns tab does.
- **Expenses**: the month's items with All / Paid / Pending filters. Tap any item to mark it paid or edit
  it (item name, paid by, date, amount, method, extra reward); the dialog opens with a 12-month chart of that item (tap a bar for that month); Clear undoes a payment. On an unpaid item, "Not needed this month" removes it from the pending list (and the reminder) without writing anything to the sheet; it's remembered on the phone. + adds an ad-hoc item.
- **Card bills**: each card's bill for the month. Tap to mark, edit or clear.
- **Cards**: three tabs (on the Cards tab, search by card or category such as "movies", "fuel" or "lounge" to see which cards give a benefit there). Rates (tap to edit a method's return %, or add a method), Cards (tap a card, then
  any field to edit it), Due dates (bill date, due date, limit).
- **Settings**: connection details, and the daily reminder (on/off, hour, test button). The reminder lists unpaid
  expenses and card bills; a card bill is included only from its bill date (Cards > Due dates) onwards.

## What gets written to the sheet

| Action | Tab | Cells written |
|---|---|---|
| Mark / edit expense | Monthly Expenses | B item, C Paid By, D Paid On, E Amount, F Payment mode, H extra reward |
| Clear expense | Monthly Expenses | D, E, F emptied (row and formulas stay) |
| Mark / edit card bill | CC bill | C Bill Amount, D Payment method |
| Clear card bill | CC bill | C, D emptied |
| Edit / add method rate | Rewards | B rate of that method, or a new row |
| Edit card field | Card list | that one cell |
| Edit due dates | Card list (bottom table) | bill date, due date, limit |

**Important:** editing a method's rate on the Rewards tab changes every row that uses it, including past
months, because your sheet looks the rate up live. To change a rate going forward only, add a new method
(for example "HSBC Live+ 5%") and use that one from now on.

Starting a new month: the first payment you mark copies the previous month's block of rows
(with formulas) into the sheet, then fills in that payment.

## Known limits

- Rows whose Date cell is stored as text rather than a real date are skipped by the script.
- The app needs internet; there is no offline queue yet.
- Build config (Kotlin 1.9.24, AGP 8.5.2, Compose BOM 2024.06) was written without being compiled in
  the authoring environment. If Android Studio offers an upgrade, accepting it is fine.
- Command-line build (needs the Android SDK installed): in `android/`, run `gradlew.bat assembleDebug`
  on Windows (or `./gradlew assembleDebug`). The APK lands in `app/build/outputs/apk/debug/`.
