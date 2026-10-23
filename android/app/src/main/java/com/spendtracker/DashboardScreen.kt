package com.spendtracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DashboardScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        MonthBar(vm)
        val s = vm.state
        if (!vm.configured) {
            NotConfigured()
            return
        }
        if (s == null) return

        val sum = RewardMath.summarize(s)
        val pendingExpenses = s.expenses.count { !it.isPaid }
        val pendingBills = s.ccBills.count { !it.isPaid }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Spent", inr(sum.spend), Modifier.weight(1f))
                    StatCard("Returns", inr(sum.returns), Modifier.weight(1f))
                    StatCard("Return %", "%.2f%%".format(sum.pct), Modifier.weight(1f))
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Still to mark this month", style = MaterialTheme.typography.titleSmall)
                        Text("$pendingExpenses expenses and $pendingBills card bills not marked yet (cards with no dues stay here too)")
                    }
                }
            }
            item { Text("By payment method", style = MaterialTheme.typography.titleMedium) }
            if (sum.byMethod.isEmpty()) {
                item { Text("Nothing recorded for this month yet.") }
            }
            items(sum.byMethod) { row ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val rate = RewardMath.rateOf(s.rewards, row.method)
                        Column(Modifier.weight(1f)) {
                            Text(row.method, fontWeight = FontWeight.SemiBold)
                            if (rate != null) Text("rate ${pctOfRate(rate)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            Text(inr(row.spend))
                            Text("+${inr(row.returns)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (sum.byMethod.any { it.method in RewardMath.cappedMethods }) {
                item {
                    Text(
                        "HDFC Rewards Debit cashback on card bills is capped at ${inr(RewardMath.DEBIT_CAP)} per month, same as your sheet.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item { Text("Recent months (from your sheet)", style = MaterialTheme.typography.titleMedium) }
            items(s.history.filter { (it.spends ?: 0.0) > 0.0 }.takeLast(6).reversed()) { h ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(h.month)
                    Text("${inr(h.spends)}  →  ${inr(h.returns)}  (${h.pct?.let { "%.2f%%".format(it) } ?: "—"})")
                }
            }
        }
    }
}
