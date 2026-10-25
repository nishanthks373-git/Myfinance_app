/**
 * Spend Tracker backend  (bound to the "Finances_NS_test" Google Sheet)
 *
 * Setup: Extensions > Apps Script > paste this file > set SECRET below >
 * Deploy > New deployment > Web app > Execute as: Me, Who has access: Anyone.
 * Copy the /exec URL into the Android app's Settings screen.
 * After any later edit: Deploy > Manage deployments > Edit (pencil) > Version: New version > Deploy.
 *
 * Writes only INPUT cells; your formula columns are never overwritten:
 *   Monthly Expenses : B Utility, C Paid By, D Paid On, E Amount, F Payment mode, H Adhoc return
 *   CC bill          : C Bill Amount, D Payment method
 *   Rewards          : B (rate per payment method), plus new rows for new methods
 *   Card list        : card fields (text cells), bill date / due date / limit table
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
        case 'state':           return json_(getState_(req.month));
        case 'itemHistory':     return json_(itemHistory_(req));
        case 'markExpense':     return json_(markExpense_(req));
        case 'clearExpense':    return json_(clearExpense_(req));
        case 'markCCBill':      return json_(markCCBill_(req));
        case 'clearBill':       return json_(clearBill_(req));
        case 'setRate':         return json_(setRate_(req));
        case 'updateCardField': return json_(updateCardField_(req));
        case 'updateDue':       return json_(updateDue_(req));
        default:                return json_({ ok: false, error: 'Unknown action: ' + req.action });
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

/** Throws unless `row` of sheet `sh` still holds (month, name) in columns A and B. */
function validateRow_(sh, row, month, name) {
  const last = lastDataRow_(sh, 1);
  if (!(row > 0) || row > last) throw new Error('That row no longer exists. Refresh and try again.');
  const r = sh.getRange(row, 1, 1, 2).getValues()[0];
  if (!(r[0] instanceof Date) || monthKey_(r[0]) !== month || String(r[1]).trim() !== String(name).trim()) {
    throw new Error('The sheet changed since the app last loaded. Refresh and try again.');
  }
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
    else {
      const row = r.slice(0, cardHeader.length).map(x => String(x));
      while (row.length < cardHeader.length) row.push('');
      cards.push(row);
    }
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

/** Copies the latest month's block of rows (incl. formulas) to a new month. */
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

/* ---------- expenses ---------- */

function markExpense_(req) {
  // req: month 'yyyy-MM', utility, paidBy, paidOn 'yyyy-MM-dd', amount, mode,
  //      adhoc?, row? (edit that exact row), origUtility? (row validation), createIfMissing?
  const sh = SpreadsheetApp.getActive().getSheetByName(EXP);
  if (!(req.amount > 0)) throw new Error('Amount must be greater than 0');
  if (!req.mode) throw new Error('Payment mode is required');
  if (!req.utility || !String(req.utility).trim()) throw new Error('Item name is required');

  ensureBlock_(sh, 9, req.month, 4, 6);                 // Paid On, Amount, Payment mode cleared (C = Paid By kept)

  const last = lastDataRow_(sh, 1);
  const a = sh.getRange(1, 1, last, 2).getValues();
  let target = -1;

  if (req.row > 0) {                                    // editing a specific row
    validateRow_(sh, req.row, req.month, req.origUtility || req.utility);
    target = req.row;
    const newName = String(req.utility).trim();
    if (newName !== String(a[target - 1][1]).trim()) sh.getRange(target, 2).setValue(newName);
  } else {
    for (let i = 0; i < a.length; i++) {
      if (a[i][0] instanceof Date && monthKey_(a[i][0]) === req.month && String(a[i][1]).trim() === String(req.utility).trim()) {
        target = i + 1;
        if (num_(sh.getRange(target, 5).getValue()) === null) break;   // prefer an unpaid row for this utility
      }
    }
    if (target === -1) {                                // ad-hoc new item: append after the month's last row, formulas copied
      if (!req.createIfMissing) throw new Error('No row for "' + req.utility + '" in ' + req.month);
      let lastOfMonth = -1;
      for (let i = 0; i < a.length; i++) if (a[i][0] instanceof Date && monthKey_(a[i][0]) === req.month) lastOfMonth = i + 1;
      sh.insertRowAfter(lastOfMonth);
      target = lastOfMonth + 1;
      sh.getRange(lastOfMonth, 1, 1, 9).copyTo(sh.getRange(target, 1, 1, 9));
      sh.getRange(target, 1).setValue(monthToDate_(req.month));
      sh.getRange(target, 2).setValue(String(req.utility).trim());
    }
  }

  sh.getRange(target, 3).setValue(req.paidBy || '');
  sh.getRange(target, 4).setValue(dayToDate_(req.paidOn));
  sh.getRange(target, 5).setValue(Number(req.amount));
  sh.getRange(target, 6).setValue(req.mode);
  if (req.adhoc != null) sh.getRange(target, 8).setValue(Number(req.adhoc));
  SpreadsheetApp.flush();
  return { ok: true, row: target };
}

function clearExpense_(req) {
  // req: month, row, utility. Clears Paid On / Amount / Payment mode only (row and formulas stay).
  const sh = SpreadsheetApp.getActive().getSheetByName(EXP);
  validateRow_(sh, req.row, req.month, req.utility);
  sh.getRange(req.row, 4, 1, 3).clearContent();
  SpreadsheetApp.flush();
  return { ok: true, row: req.row };
}

/* ---------- card bills ---------- */

function markCCBill_(req) {
  // req: month, card, amount, method, row? (edit that exact row)
  const sh = SpreadsheetApp.getActive().getSheetByName(BILL);
  if (req.amount == null || Number(req.amount) < 0) throw new Error('Bill amount is required');
  const zero = Number(req.amount) === 0;                // no bill this month: only the amount is written
  if (!zero && !req.method) throw new Error('Payment method is required');

  ensureBlock_(sh, 6, req.month, 3, 4);                 // Bill Amount + Payment method cleared

  const last = lastDataRow_(sh, 1);
  const a = sh.getRange(1, 1, last, 2).getValues();
  let target = -1;
  if (req.row > 0) {
    validateRow_(sh, req.row, req.month, req.card);
    target = req.row;
  } else {
    for (let i = 0; i < a.length; i++) {
      if (a[i][0] instanceof Date && monthKey_(a[i][0]) === req.month && String(a[i][1]).trim() === req.card) { target = i + 1; break; }
    }
    if (target === -1) {                                // card not in that month's block yet: append with formulas
      let lastOfMonth = -1;
      for (let i = 0; i < a.length; i++) if (a[i][0] instanceof Date && monthKey_(a[i][0]) === req.month) lastOfMonth = i + 1;
      sh.insertRowAfter(lastOfMonth);
      target = lastOfMonth + 1;
      sh.getRange(lastOfMonth, 1, 1, 6).copyTo(sh.getRange(target, 1, 1, 6));
      sh.getRange(target, 1).setValue(monthToDate_(req.month));
      sh.getRange(target, 2).setValue(req.card);
    }
  }

  sh.getRange(target, 3).setValue(Number(req.amount));
  if (zero) sh.getRange(target, 4).clearContent();
  else sh.getRange(target, 4).setValue(req.method);
  SpreadsheetApp.flush();
  return { ok: true, row: target };
}

function clearBill_(req) {
  // req: month, row, card. Clears Bill Amount + Payment method only.
  const sh = SpreadsheetApp.getActive().getSheetByName(BILL);
  validateRow_(sh, req.row, req.month, req.card);
  sh.getRange(req.row, 3, 1, 2).clearContent();
  SpreadsheetApp.flush();
  return { ok: true, row: req.row };
}

/* ---------- payment-method rates (Rewards tab) ---------- */

/**
 * Sets the rate of a payment method. NOTE: every row in Monthly Expenses and CC bill looks this
 * value up live (INDEX/MATCH), so changing it also changes past months' returns.
 * With create:true, appends a NEW method instead (no history is affected).
 */
function setRate_(req) {
  const sh = SpreadsheetApp.getActive().getSheetByName(REW);
  const name = String(req.name || '').trim();
  const rate = Number(req.rate);
  if (!name) throw new Error('Method name is required');
  if (isNaN(rate) || rate < 0 || rate > 1) throw new Error('Rate must be between 0% and 100%');

  const last = lastDataRow_(sh, 1);
  const names = sh.getRange(1, 1, last, 1).getValues();
  let target = -1;
  for (let i = 0; i < names.length; i++) if (String(names[i][0]).trim().toLowerCase() === name.toLowerCase()) { target = i + 1; break; }

  if (req.create) {
    if (target !== -1) throw new Error('A method called "' + name + '" already exists');
    const row = last + 1;
    if (last >= 2) sh.getRange(last, 1, 1, 2).copyTo(sh.getRange(row, 1, 1, 2));
    sh.getRange(row, 1).setValue(name);
    sh.getRange(row, 2).setValue(rate);
    target = row;
  } else {
    if (target === -1) throw new Error('Method "' + name + '" not found in the Rewards tab');
    sh.getRange(target, 2).setValue(rate);
  }
  SpreadsheetApp.flush();
  return { ok: true, row: target };
}

/* ---------- Card list tab ---------- */

function updateCardField_(req) {
  // req: card, field (column header, e.g. 'Online spends'), value (text)
  const sh = SpreadsheetApp.getActive().getSheetByName(CARDS);
  const vals = sh.getDataRange().getValues();
  const header = vals[0].map(String);
  const col = header.indexOf(String(req.field));
  if (col < 1) throw new Error('Unknown field: ' + req.field);          // col 0 is the card name: not editable here
  const value = req.value == null ? '' : String(req.value);
  if (value.length > 500) throw new Error('Value is too long');

  let target = -1;
  for (let i = 1; i < vals.length; i++) {
    const first = String(vals[i][0]).trim();
    if (first === 'Credit Card') break;                                  // stop at the bill/due table
    if (first === String(req.card).trim()) { target = i + 1; break; }
  }
  if (target === -1) throw new Error('Card not found: ' + req.card);

  const cell = sh.getRange(target, col + 1);
  if (value === '') cell.clearContent(); else cell.setValue(value);
  SpreadsheetApp.flush();
  return { ok: true, row: target };
}

function updateDue_(req) {
  // req: card, billDate?, dueDate?, limit? (number | null to clear)
  const sh = SpreadsheetApp.getActive().getSheetByName(CARDS);
  const vals = sh.getDataRange().getValues();
  let inDues = false, target = -1;
  for (let i = 1; i < vals.length; i++) {
    const first = String(vals[i][0]).trim();
    if (first === 'Credit Card') { inDues = true; continue; }
    if (inDues && first === String(req.card).trim()) { target = i + 1; break; }
  }
  if (target === -1) throw new Error('Card not found in the due-date table: ' + req.card);

  if (req.billDate !== undefined) sh.getRange(target, 2).setValue(String(req.billDate));
  if (req.dueDate !== undefined) sh.getRange(target, 3).setValue(String(req.dueDate));
  if (req.limit !== undefined) {
    if (req.limit === null || req.limit === '') sh.getRange(target, 4).clearContent();
    else {
      const lim = Number(req.limit);
      if (isNaN(lim) || lim < 0) throw new Error('Card limit must be a number');
      sh.getRange(target, 4).setValue(lim);
    }
  }
  SpreadsheetApp.flush();
  return { ok: true, row: target };
}


/* ---------- per-item history (expense or card bill, last 12 months up to `month`) ---------- */

// req: kind ('expense' | 'bill'), name, month ('yyyy-MM')
function itemHistory_(req) {
  const ss = SpreadsheetApp.getActive();
  const want = String(req.name || '').trim().toLowerCase();
  const end = String(req.month).split('-');
  const keys = [];
  for (let k = 11; k >= 0; k--) {
    const d = new Date(Number(end[0]), Number(end[1]) - 1 - k, 1, 12, 0, 0);
    keys.push(Utilities.formatDate(d, tz_(), 'yyyy-MM'));
  }
  const acc = {};
  keys.forEach(k => acc[k] = { month: k, amount: null, reward: null, mode: '' });

  const isBill = req.kind === 'bill';
  const sh = ss.getSheetByName(isBill ? BILL : EXP);
  const last = lastDataRow_(sh, 1);
  const vals = sh.getRange(1, 1, last, isBill ? 6 : 9).getValues();
  for (let i = 0; i < vals.length; i++) {
    const r = vals[i];
    if (!(r[0] instanceof Date)) continue;
    const mk = monthKey_(r[0]);
    if (!acc[mk]) continue;
    const name = String(r[1]).trim().toLowerCase();
    if (name !== want) continue;
    const amount = num_(isBill ? r[2] : r[4]);
    const mode = String(isBill ? r[3] : r[5]);
    if (amount === null || mode === '') continue;          // not paid in that month
    const reward = num_(isBill ? r[5] : r[8]);
    const a = acc[mk];
    a.amount = (a.amount || 0) + amount;
    a.reward = (a.reward || 0) + (reward || 0);
    a.mode = a.mode && a.mode !== mode ? a.mode + ', ' + mode : mode;
  }
  return { ok: true, name: req.name, points: keys.map(k => acc[k]) };
}
