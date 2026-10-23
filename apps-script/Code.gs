/**
 * Spend Tracker backend  (bound to the "Finances_NS_test" Google Sheet)
 *
 * Setup: Extensions > Apps Script > paste this file > set SECRET below >
 * Deploy > New deployment > Web app > Execute as: Me, Who has access: Anyone.
 * Copy the /exec URL into the Android app's Settings screen.
 *
 * This script ONLY writes the input columns:
 *   Monthly Expenses : C (Paid By), D (Paid On), E (Amount), F (Payment mode), H (Adhoc return %)
 *   CC bill          : C (Bill Amount), D (Payment method)
 * Return % and Actual Return columns are your existing sheet formulas and are never overwritten.
 */

const SECRET = 'CHANGE-ME-TO-A-LONG-RANDOM-STRING';

const EXP = 'Monthly Expenses';
const BILL = 'CC bill';
const REW = 'Rewards';
const CARDS = 'Card list';

function doGet() {
  return json_({ ok: true, message: 'Spend Tracker backend is running. Use POST.' });
}

function doPost(e) {
  try {
    const req = JSON.parse(e.postData.contents);
    if (req.token !== SECRET) return json_({ ok: false, error: 'Unauthorized' });

    const lock = LockService.getScriptLock();
    lock.waitLock(20000);
    try {
      switch (req.action) {
        case 'state':       return json_(getState_(req.month));
        case 'markExpense': return json_(markExpense_(req));
        case 'markCCBill':  return json_(markCCBill_(req));
        default:            return json_({ ok: false, error: 'Unknown action: ' + req.action });
      }
    } finally {
      lock.releaseLock();
    }
  } catch (err) {
    return json_({ ok: false, error: String(err && err.message || err) });
  }
}

/* ---------- helpers ---------- */

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

function tz_() { return SpreadsheetApp.getActive().getSpreadsheetTimeZone(); }

function monthKey_(v) {               // Date | string -> 'yyyy-MM' (or '')
  if (v instanceof Date) return Utilities.formatDate(v, tz_(), 'yyyy-MM');
  const m = String(v).match(/^(\d{4})[\/-](\d{1,2})/);
  return m ? m[1] + '-' + ('0' + m[2]).slice(-2) : '';
}

function dayStr_(v) {                 // Date | string -> 'yyyy-MM-dd' (or '')
  if (v instanceof Date) return Utilities.formatDate(v, tz_(), 'yyyy-MM-dd');
  return v === '' || v == null ? '' : String(v);
}

function monthToDate_(key) {          // 'yyyy-MM' -> Date (1st of month, noon to dodge DST edges)
  const p = key.split('-');
  return new Date(Number(p[0]), Number(p[1]) - 1, 1, 12, 0, 0);
}

function dayToDate_(s) {              // 'yyyy-MM-dd' -> Date
  const p = s.split('-');
  return new Date(Number(p[0]), Number(p[1]) - 1, Number(p[2]), 12, 0, 0);
}

function num_(v) { return (v === '' || v == null || isNaN(Number(v))) ? null : Number(v); }

function lastDataRow_(sh, col) {      // last non-empty row in column `col`
  const vals = sh.getRange(1, col, sh.getMaxRows(), 1).getValues();
  for (let i = vals.length - 1; i >= 0; i--) if (vals[i][0] !== '') return i + 1;
  return 1;
}

/* ---------- state ---------- */

function getState_(month) {
  const ss = SpreadsheetApp.getActive();

  // Payment methods and rates
  const rewVals = ss.getSheetByName(REW).getDataRange().getValues();
  const rewards = rewVals
    .filter(r => String(r[0]).trim() !== '' && num_(r[1]) !== null)
    .map(r => ({ name: String(r[0]).trim(), rate: Number(r[1]) }));

  // Monthly Expenses
  const es = ss.getSheetByName(EXP);
  const eLast = lastDataRow_(es, 1);
  const eVals = es.getRange(1, 1, eLast, 9).getValues();
  const expenses = [];
  const paidBy = {};
  const utilitySeen = {};
  const utilities = [];
  const months = {};
  const expByMonth = {};
  for (let i = 0; i < eVals.length; i++) {
    const r = eVals[i];
    const mk = monthKey_(r[0]);
    if (!mk || !(r[0] instanceof Date)) continue;       // skips headers / blank rows
    months[mk] = true;
    const util = String(r[1]).trim();
    (expByMonth[mk] = expByMonth[mk] || []).push(util);
    if (r[2] !== '') paidBy[String(r[2]).trim()] = true;
    if (util && !utilitySeen[util]) { utilitySeen[util] = true; utilities.push(util); }
    if (mk === month) {
      expenses.push({
        row: i + 1, utility: util, paidBy: String(r[2]), paidOn: dayStr_(r[3]),
        amount: num_(r[4]), mode: String(r[5]), rate: num_(r[6]), adhoc: num_(r[7]) || 0,
        actual: num_(r[8]),
      });
    }
  }

  // CC bill
  const bs = ss.getSheetByName(BILL);
  const bLast = lastDataRow_(bs, 1);
  const bVals = bs.getRange(1, 1, bLast, 6).getValues();
  const ccBills = [];
  const billMonths = {};
  const billByMonth = {};
  for (let i = 0; i < bVals.length; i++) {
    const r = bVals[i];
    const mk = monthKey_(r[0]);
    if (!mk || !(r[0] instanceof Date)) continue;
    billMonths[mk] = true;
    (billByMonth[mk] = billByMonth[mk] || []).push(String(r[1]).trim());
    if (mk === month) {
      ccBills.push({
        row: i + 1, card: String(r[1]), amount: num_(r[2]), method: String(r[3]),
        rate: num_(r[4]), actual: num_(r[5]),
      });
    }
  }

  // Card list + bill/due dates
  const cv = ss.getSheetByName(CARDS).getDataRange().getValues();
  const cardHeader = cv[0].map(String);
  const cards = [];
  const dues = [];
  let inDues = false;
  for (let i = 1; i < cv.length; i++) {
    const r = cv[i];
    const first = String(r[0]).trim();
    if (first === 'Credit Card') { inDues = true; continue; }
    if (first === '') continue;
    if (inDues) dues.push({ card: first, billDate: String(r[1]), dueDate: String(r[2]), limit: num_(r[3]) });
    else cards.push(r.slice(0, cardHeader.length).map(x => String(x)));
  }

  // Monthly Returns (sheet-calculated history)
  const rs = ss.getSheetByName('Monthly Returns');
  const rv = rs.getDataRange().getValues();
  const history = [];
  for (let i = 1; i < rv.length; i++) {
    if (!(rv[i][0] instanceof Date)) continue;
    history.push({ month: monthKey_(rv[i][0]), spends: num_(rv[i][1]), returns: num_(rv[i][2]), pct: num_(rv[i][3]) });
  }

  const latestExp = Object.keys(expByMonth).sort().pop();
  const latestBill = Object.keys(billByMonth).sort().pop();

  return {
    ok: true, month: month,
    templateUtilities: latestExp ? expByMonth[latestExp] : [],
    templateCards: latestBill ? billByMonth[latestBill] : [],
    rewards: rewards,
    expenses: expenses,
    ccBills: ccBills,
    paidByOptions: Object.keys(paidBy),
    utilities: utilities,
    cardHeader: cardHeader, cards: cards, dues: dues,
    history: history,
    monthHasExpenseRows: !!months[month],
    monthHasBillRows: !!billMonths[month],
  };
}

/* ---------- new-month scaffolding ---------- */

/** Copies the latest month's block of rows (A:I, incl. formulas) to a new month. */
function ensureBlock_(sh, ncols, month, resetFrom, resetTo) {
  const last = lastDataRow_(sh, 1);
  const vals = sh.getRange(1, 1, last, 1).getValues();
  let latestKey = '', latestTs = -1;
  for (let i = 0; i < vals.length; i++) {
    if (vals[i][0] instanceof Date) {
      const mk = monthKey_(vals[i][0]);
      if (mk === month) return;                         // already present
      const ts = vals[i][0].getTime();
      if (ts > latestTs) { latestTs = ts; latestKey = mk; }
    }
  }
  if (!latestKey) throw new Error('No template rows found in ' + sh.getName());

  const rows = [];
  for (let i = 0; i < vals.length; i++) if (monthKey_(vals[i][0]) === latestKey && vals[i][0] instanceof Date) rows.push(i + 1);
  const first = rows[0], count = rows.length;
  const dest = last + 1;
  if (dest + count - 1 > sh.getMaxRows()) sh.insertRowsAfter(sh.getMaxRows(), count + 20);

  sh.getRange(first, 1, count, ncols).copyTo(sh.getRange(dest, 1, count, ncols));
  sh.getRange(dest, 1, count, 1).setValue(monthToDate_(month));
  // clear user-entered inputs so the new month starts "unpaid"
  sh.getRange(dest, resetFrom, count, resetTo - resetFrom + 1).clearContent();
}

/* ---------- writes ---------- */

function markExpense_(req) {
  // req: month 'yyyy-MM', utility, paidBy, paidOn 'yyyy-MM-dd', amount, mode, adhoc?, createIfMissing?
  const sh = SpreadsheetApp.getActive().getSheetByName(EXP);
  if (!(req.amount > 0)) throw new Error('Amount must be greater than 0');
  if (!req.mode) throw new Error('Payment mode is required');

  ensureBlock_(sh, 9, req.month, 4, 6);                 // Paid On, Amount, Payment mode cleared (C = Paid By kept)

  const last = lastDataRow_(sh, 1);
  const a = sh.getRange(1, 1, last, 2).getValues();
  let target = -1;
  for (let i = 0; i < a.length; i++) {
    if (a[i][0] instanceof Date && monthKey_(a[i][0]) === req.month && String(a[i][1]).trim() === req.utility) {
      target = i + 1;
      if (num_(sh.getRange(target, 5).getValue()) === null) break;     // prefer an unpaid row for this utility
    }
  }

  if (target === -1) {                                  // ad-hoc new item: append after the month's last row, formulas copied
    if (!req.createIfMissing) throw new Error('No row for "' + req.utility + '" in ' + req.month);
    let lastOfMonth = -1;
    for (let i = 0; i < a.length; i++) if (a[i][0] instanceof Date && monthKey_(a[i][0]) === req.month) lastOfMonth = i + 1;
    sh.insertRowAfter(lastOfMonth);
    target = lastOfMonth + 1;
    sh.getRange(lastOfMonth, 1, 1, 9).copyTo(sh.getRange(target, 1, 1, 9));
    sh.getRange(target, 1).setValue(monthToDate_(req.month));
    sh.getRange(target, 2).setValue(req.utility);
  }

  sh.getRange(target, 3).setValue(req.paidBy || '');
  sh.getRange(target, 4).setValue(dayToDate_(req.paidOn));
  sh.getRange(target, 5).setValue(Number(req.amount));
  sh.getRange(target, 6).setValue(req.mode);
  if (req.adhoc != null) sh.getRange(target, 8).setValue(Number(req.adhoc));
  SpreadsheetApp.flush();
  return { ok: true, row: target };
}

function markCCBill_(req) {
  // req: month 'yyyy-MM', card, amount, method
  const sh = SpreadsheetApp.getActive().getSheetByName(BILL);
  if (req.amount == null || Number(req.amount) < 0) throw new Error('Bill amount is required');
  if (!req.method) throw new Error('Payment method is required');

  ensureBlock_(sh, 6, req.month, 3, 4);                 // Bill Amount + Payment method cleared

  const last = lastDataRow_(sh, 1);
  const a = sh.getRange(1, 1, last, 2).getValues();
  let target = -1;
  for (let i = 0; i < a.length; i++) {
    if (a[i][0] instanceof Date && monthKey_(a[i][0]) === req.month && String(a[i][1]).trim() === req.card) { target = i + 1; break; }
  }
  if (target === -1) {                                  // card not in that month's block yet: append with formulas
    let lastOfMonth = -1;
    for (let i = 0; i < a.length; i++) if (a[i][0] instanceof Date && monthKey_(a[i][0]) === req.month) lastOfMonth = i + 1;
    sh.insertRowAfter(lastOfMonth);
    target = lastOfMonth + 1;
    sh.getRange(lastOfMonth, 1, 1, 6).copyTo(sh.getRange(target, 1, 1, 6));
    sh.getRange(target, 1).setValue(monthToDate_(req.month));
    sh.getRange(target, 2).setValue(req.card);
  }

  sh.getRange(target, 3).setValue(Number(req.amount));
  sh.getRange(target, 4).setValue(req.method);
  SpreadsheetApp.flush();
  return { ok: true, row: target };
}
