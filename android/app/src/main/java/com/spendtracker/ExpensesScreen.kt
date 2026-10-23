package com.spendtracker

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun ExpensesScreen(vm: AppViewModel) {
    var editing by remember { mutableStateOf<Expense?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        MonthBar(vm)
        val s = vm.state
        if (!vm.configured) {
            NotConfigured()
        } else if (s != null) {
            // A month that hasn't started in the sheet yet shows last month's items as "pending".
            val items = if (s.expenses.isNotEmpty()) s.expenses
            else s.templateUtilities.map { Expense(-1, it, "", "", null, "", null, 0.0, null) }
            val paidTotal = items.sumOf { it.amount ?: 0.0 }

            Box(Modifier.weight(1f)) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            "Paid ${inr(paidTotal)}  ·  ${items.count { !it.isPaid }} pending",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    items(items) { e -> ExpenseRow(e) { editing = e } }
                }
                FloatingActionButton(
                    onClick = { adding = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                ) { Icon(Icons.Default.Add, contentDescription = "Add payment") }
            }
        }
    }

    editing?.let { ExpenseDialog(vm, it, isNew = false) { editing = null } }
    if (adding) ExpenseDialog(vm, null, isNew = true) { adding = false }
}

@Composable
private fun ExpenseRow(e: Expense, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(e.utility, fontWeight = FontWeight.SemiBold)
                Text(
                    if (e.isPaid) "${e.paidBy} · ${e.paidOn} · ${e.mode}" else "Pending – tap to mark paid",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (e.isPaid) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(inr(e.amount))
                    val r = e.actual ?: RewardMath.expenseReward(e.amount ?: 0.0, e.rate ?: 0.0, e.adhoc)
                    Text("+${inr(r)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ExpenseDialog(vm: AppViewModel, item: Expense?, isNew: Boolean, onDismiss: () -> Unit) {
    val s = vm.state ?: return
    val ctx = LocalContext.current
    var utility by remember { mutableStateOf(item?.utility ?: "") }
    var paidBy by remember { mutableStateOf(item?.paidBy?.ifBlank { null } ?: vm.defaultPaidBy()) }
    var date by remember {
        mutableStateOf(
            item?.paidOn?.takeIf { Regex("""\d{4}-\d{2}-\d{2}""").matches(it) } ?: vm.defaultDate().toString()
        )
    }
    var amountText by remember { mutableStateOf(item?.amount?.let { plain(it) } ?: "") }
    var mode by remember { mutableStateOf(item?.mode ?: "") }

    val amount = amountText.toDoubleOrNull()
    val rate = RewardMath.rateOf(s.rewards, mode)
    val valid = utility.isNotBlank() && amount != null && amount > 0.0 && mode.isNotBlank() && paidBy.isNotBlank()
    val methods = s.rewards.sortedByDescending { it.rate }.map { it.name }

    AlertDialog(
        onDismissRequest = { if (!vm.busy) onDismiss() },
        title = { Text(if (isNew) "Add payment" else "Mark payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = utility, onValueChange = { utility = it }, label = { Text("Item") },
                    singleLine = true, readOnly = !isNew, modifier = Modifier.fillMaxWidth(),
                )
                Picker("Paid by", paidBy, s.paidByOptions, { paidBy = it })
                OutlinedButton(
                    onClick = {
                        val d = LocalDate.parse(date)
                        DatePickerDialog(
                            ctx, { _, y, m, day -> date = LocalDate.of(y, m + 1, day).toString() },
                            d.year, d.monthValue - 1, d.dayOfMonth,
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Paid on: $date") }
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it }, label = { Text("Amount (₹)") },
                    singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Picker("Paid with", mode, methods, { mode = it }) { name ->
                    val r = RewardMath.rateOf(s.rewards, name)
                    if (r == null) name else "$name (${pctOfRate(r)})"
                }
                if (amount != null && rate != null) {
                    Text(
                        "Reward: ${inr(RewardMath.expenseReward(amount, rate))} at ${pctOfRate(rate)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (item?.isPaid == true) {
                    Text("Already recorded – saving overwrites it.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            PrimaryButton(if (vm.busy) "Saving…" else "Save to sheet", enabled = valid && !vm.busy) {
                vm.markExpense(utility.trim(), paidBy, date, amount!!, mode, isNew) { ok -> if (ok) onDismiss() }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.busy) { Text("Cancel") } },
    )
}
