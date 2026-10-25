package com.spendtracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun billReward(b: CcBill): Double =
    b.actual ?: RewardMath.billReward(b.amount ?: 0.0, b.rate ?: 0.0)

@Composable
fun BillsScreen(vm: AppViewModel) {
    var editing by remember { mutableStateOf<CcBill?>(null) }
    var filter by remember { mutableStateOf(0) }   // 0 all, 1 paid, 2 pending

    Column(Modifier.fillMaxSize()) {
        MonthBar(vm)
        val s = vm.state
        if (!vm.configured) {
            NotConfigured()
        } else if (s != null) {
            val all = if (s.ccBills.isNotEmpty()) s.ccBills
            else s.templateCards.map { CcBill(-1, it, null, "", null, null) }
            val paid = all.filter { it.isPaid }
            val pending = all.filter { !it.isPaid }
            val shown = when (filter) {
                1 -> paid
                2 -> pending
                else -> pending + paid
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            Stat("Bills paid", inr(paid.sumOf { it.amount ?: 0.0 }), Brand.Blue)
                            Stat("Rewards", inr(paid.sumOf { billReward(it) }), Brand.Green)
                            Stat("Not paid", pending.size.toString(), Brand.Amber)
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("All ${all.size}", Brand.Indigo, filled = filter == 0, onClick = { filter = 0 })
                        Pill("Paid ${paid.size}", Brand.Green, filled = filter == 1, onClick = { filter = 1 })
                        Pill("Not paid ${pending.size}", Brand.Amber, filled = filter == 2, onClick = { filter = 2 })
                    }
                }
                item {
                    Text(
                        "Pay each card's bill from the method that earns the most. Rates show in the picker.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(shown) { b ->
                    val due = s.dues.firstOrNull { it.card == b.card }
                    AppCard(Modifier.fillMaxWidth(), onClick = { editing = b }) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(b.card, Brand.colorFor(b.card))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(b.card, fontWeight = FontWeight.Bold)
                                Text(
                                    if (b.isPaid) "Paid via ${b.method}" else "Tap to mark paid",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (due != null) {
                                    Text(
                                        "Bill ${due.billDate} · due ${due.dueDate}",
                                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (b.isPaid) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(inr(b.amount), fontWeight = FontWeight.SemiBold)
                                    Text("+" + inr(billReward(b)), fontSize = 12.sp, color = Brand.Green, fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                Pill("Not paid", Brand.Amber)
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { BillDialog(vm, it) { editing = null } }
}

@Composable
private fun BillDialog(vm: AppViewModel, item: CcBill, onDismiss: () -> Unit) {
    val s = vm.state ?: return
    var amountText by remember { mutableStateOf(item.amount?.takeIf { it > 0.0 || item.isPaid }?.let { plain(it) } ?: "") }
    var method by remember { mutableStateOf(item.method) }
    var confirmClear by remember { mutableStateOf(false) }

    val amount = amountText.toDoubleOrNull()
    val rate = RewardMath.rateOf(s.rewards, method)
    val isZero = amount != null && amount == 0.0
    val valid = amount != null && amount >= 0.0 && (isZero || method.isNotBlank())
    val methods = s.rewards.sortedByDescending { it.rate }.map { it.name }

    AlertDialog(
        onDismissRequest = { if (!vm.busy) onDismiss() },
        title = { Text(item.card) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryChart(vm, "bill", item.card)
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it }, label = { Text("Bill amount (₹)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Picker("Paid with", if (isZero) "" else method, methods, { method = it }, enabled = !isZero) { name ->
                    val r = RewardMath.rateOf(s.rewards, name)
                    if (r == null) name else "$name (${pctOfRate(r)})"
                }
                if (isZero) {
                    Text(
                        "No bill this month: only the amount (0) is saved and it leaves the pending list.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (amount != null && rate != null && !isZero) {
                    Text(
                        "Reward: ${inr(RewardMath.billReward(amount, rate))} at ${pctOfRate(rate)}",
                        color = Brand.Green, fontWeight = FontWeight.SemiBold,
                    )
                }
                if (!isZero && method in RewardMath.cappedMethods) {
                    Text(
                        "Capped at ${inr(RewardMath.DEBIT_CAP)} per month for this method (applied in the monthly total).",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            PrimaryButton(if (vm.busy) "Saving…" else "Save to sheet", enabled = valid && !vm.busy) {
                vm.markBill(item.row, item.card, amount!!, if (isZero) "" else method) { ok -> if (ok) onDismiss() }
            }
        },
        dismissButton = {
            Row {
                if (item.isPaid && item.row > 0) {
                    TextButton(
                        onClick = {
                            if (!confirmClear) confirmClear = true
                            else vm.clearBill(item.row, item.card) { ok -> if (ok) onDismiss() }
                        },
                        enabled = !vm.busy,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(if (confirmClear) "Tap again to clear" else "Clear") }
                }
                TextButton(onClick = onDismiss, enabled = !vm.busy) { Text("Cancel") }
            }
        },
    )
}
