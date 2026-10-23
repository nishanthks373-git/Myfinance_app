package com.spendtracker

import kotlin.math.floor
import kotlin.math.min

data class MethodRow(val method: String, val spend: Double, val returns: Double)

data class Summary(val spend: Double, val returns: Double, val byMethod: List<MethodRow>) {
    val pct: Double get() = if (spend > 0) returns / spend * 100.0 else 0.0
}

/**
 * Mirrors the sheet's own formulas so the app and the sheet always agree:
 *  - Monthly Expenses : Actual Returns = Amount * Return% + Adhoc%       (I = E*G+H)
 *  - CC bill          : Actual Return  = FLOOR(Amount/100) * Return% * 100
 *  - Monthly Returns  : bill returns paid via "HDFC Rewards Debit" and "HDFC Rewards Debit2"
 *                       are each capped at 2000 per month.
 */
object RewardMath {
    const val DEBIT_CAP = 2000.0
    val cappedMethods = setOf("HDFC Rewards Debit", "HDFC Rewards Debit2")

    fun expenseReward(amount: Double, rate: Double, adhoc: Double = 0.0): Double = amount * rate + adhoc

    fun billReward(amount: Double, rate: Double): Double = floor(amount / 100.0) * rate * 100.0

    fun rateOf(rewards: List<Reward>, method: String): Double? = rewards.firstOrNull { it.name == method }?.rate

    fun summarize(s: SheetState): Summary {
        val spend = LinkedHashMap<String, Double>()
        val ret = LinkedHashMap<String, Double>()
        val billRet = LinkedHashMap<String, Double>()
        var total = 0.0

        for (e in s.expenses) {
            val a = e.amount ?: continue
            val m = e.mode.ifBlank { "(no method)" }
            total += a
            spend[m] = (spend[m] ?: 0.0) + a
            val r = e.actual ?: expenseReward(a, e.rate ?: rateOf(s.rewards, e.mode) ?: 0.0, e.adhoc)
            ret[m] = (ret[m] ?: 0.0) + r
        }
        for (b in s.ccBills) {
            val a = b.amount ?: continue
            if (a <= 0.0) continue
            val m = b.method.ifBlank { "(no method)" }
            total += a
            spend[m] = (spend[m] ?: 0.0) + a
            val r = b.actual ?: billReward(a, b.rate ?: rateOf(s.rewards, b.method) ?: 0.0)
            billRet[m] = (billRet[m] ?: 0.0) + r
        }
        for ((m, v) in billRet) {
            val counted = if (m in cappedMethods) min(DEBIT_CAP, v) else v
            ret[m] = (ret[m] ?: 0.0) + counted
        }

        val rows = spend.keys.map { MethodRow(it, spend[it] ?: 0.0, ret[it] ?: 0.0) }
            .sortedByDescending { it.spend }
        return Summary(total, rows.sumOf { it.returns }, rows)
    }
}
