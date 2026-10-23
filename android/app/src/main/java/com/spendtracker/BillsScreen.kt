package com.spendtracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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

@Composable
fun BillsScreen(vm: AppViewModel) {
    var editing by remember { mutableStateOf<CcBill?>(null) }

    Column(Modifier.fillMaxSize()) {
        MonthBar(vm)
        val s = vm.state
        if (!vm.configured) {
            NotConfigured()
        } else if (s != null) {
            val items = if (s.ccBills.isNotEmpty()) s.ccBills
            else s.templateCards.map { CcBill(-1, it, null, "", null, null) }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        "Pay each card's bill from the account that earns the most. Rates are shown in the picker.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                items(items) { b ->
                    val due = s.dues.firstOrNull { it.card == b.card }
                    Card(Modifier.fillMaxWidth().clickable { editing = b }) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(b.card, fontWeight = FontWeight.SemiBold)
                                val status = when {
                                    b.isPaid -> "Paid via ${b.method}"
                                    else -> "Pending – tap to mark paid"
                                }
                                Text(status, style = MaterialTheme.typography.bodySmall)
                                if (due != null) {
                                    Text(
                                        "Bill ${due.billDate} · due ${due.dueDate}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            if (b.isPaid) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(inr(b.amount))
                                    val r = b.actual ?: RewardMath.billReward(b.amount ?: 0.0, b.rate ?: 0.0)
                                    Text("+${inr(r)}", style = MaterialTheme.typography.bodySmall)
                                }
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
    var amountText by remember { mutableStateOf(item.amount?.takeIf { it > 0.0 }?.let { plain(it) } ?: "") }
    var method by remember { mutableStateOf(item.method) }

    val amount = amountText.toDoubleOrNull()
    val rate = RewardMath.rateOf(s.rewards, method)
    val valid = amount != null && amount >= 0.0 && method.isNotBlank()
    val methods = s.rewards.sortedByDescending { it.rate }.map { it.name }

    AlertDialog(
        onDismissRequest = { if (!vm.busy) onDismiss() },
        title = { Text(item.card) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it }, label = { Text("Bill amount (₹)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Picker("Paid with", method, methods, { method = it }) { name ->
                    val r = RewardMath.rateOf(s.rewards, name)
                    if (r == null) name else "$name (${pctOfRate(r)})"
                }
                if (amount != null && rate != null) {
                    Text(
                        "Reward: ${inr(RewardMath.billReward(amount, rate))} at ${pctOfRate(rate)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (method in RewardMath.cappedMethods) {
                    Text(
                        "Capped at ${inr(RewardMath.DEBIT_CAP)} per month for this method (applied in the monthly total).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            PrimaryButton(if (vm.busy) "Saving…" else "Save to sheet", enabled = valid && !vm.busy) {
                vm.markBill(item.card, amount!!, method) { ok -> if (ok) onDismiss() }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.busy) { Text("Cancel") } },
    )
}
