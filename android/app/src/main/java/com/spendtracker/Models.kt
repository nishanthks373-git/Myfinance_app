package com.spendtracker

data class Reward(val name: String, val rate: Double)

/** One row of the "Monthly Expenses" tab. row == -1 means a placeholder for a month not started in the sheet yet. */
data class Expense(
    val row: Int,
    val utility: String,
    val paidBy: String,
    val paidOn: String,        // yyyy-MM-dd or ""
    val amount: Double?,
    val mode: String,
    val rate: Double?,
    val adhoc: Double,
    val actual: Double?,
) {
    val isPaid: Boolean get() = amount != null && mode.isNotBlank()
}

/** One row of the "CC bill" tab. */
data class CcBill(
    val row: Int,
    val card: String,
    val amount: Double?,
    val method: String,
    val rate: Double?,
    val actual: Double?,
) {
    val isPaid: Boolean get() = method.isNotBlank() && (amount ?: 0.0) > 0.0
}

data class Due(val card: String, val billDate: String, val dueDate: String, val limit: Double?)

data class MonthHistory(val month: String, val spends: Double?, val returns: Double?, val pct: Double?)

data class SheetState(
    val month: String,
    val rewards: List<Reward>,
    val expenses: List<Expense>,
    val ccBills: List<CcBill>,
    val templateUtilities: List<String>,
    val templateCards: List<String>,
    val paidByOptions: List<String>,
    val utilities: List<String>,
    val cardHeader: List<String>,
    val cards: List<List<String>>,
    val dues: List<Due>,
    val history: List<MonthHistory>,
)

/** One month of an expense / card bill. amount == null means not paid that month. */
data class ItemPoint(val month: String, val amount: Double?, val reward: Double?, val mode: String)
