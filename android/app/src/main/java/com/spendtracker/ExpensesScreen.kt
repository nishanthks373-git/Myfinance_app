package com.spendtracker

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.unit.sp
import java.time.LocalDate

private fun rewardOf(e: Expense): Double =
    e.actual ?: RewardMath.expenseReward(e.amount ?: 0.0, e.rate ?: 0.0, e.adhoc)

@Composable
fun ExpensesScreen(vm: AppViewModel) {
    var editing by remember { mutableStateOf<Expense?>(null) }
    var adding by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(0) }   // 0 all, 1 paid, 2 pending

    Column(Modifier.fillMaxSize()) {
        MonthBar(vm)
        val s = vm.state
        if (!vm.configured) {
            NotConfigured()
        } else if (s != null) {
            // A month that hasn't started in the sheet yet shows last month's items as "pending".
            val all = if (s.expenses.isNotEmpty()) s.expenses
            else s.templateUtilities.map { Expense(-1, it, "", "", null, "", null, 0.0, null) }
            val paid = all.filter { it.isPaid }
            val pending = all.filter { !it.isPaid }
            val shown = when (filter) {
                1 -> paid
                2 -> pending
                else -> pending + paid
            }

            Box(Modifier.weight(1f)) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        AppCard(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                Stat("Paid", inr(paid.sumOf { it.amount ?: 0.0 }), Brand.Blue)
                                Stat("Rewards", inr(paid.sumOf { rewardOf(it) }), Brand.Green)
                                Stat("Pending", pending.size.toString(), Brand.Amber)
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("All ${all.size}", Brand.Indigo, filled = filter == 0, onClick = { filter = 0 })
                            Pill("Paid ${paid.size}", Brand.Green, filled = filter == 1, onClick = { filter = 1 })
                            Pill("Pending ${pending.size}", Brand.Amber, filled = filter == 2, onClick = { filter = 2 })
                        }
                    }
                    if (shown.isEmpty()) {
                        item {
                            Text("Nothing here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(shown) { e -> ExpenseRow(e) { editing = e } }
                }
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add payment") },
                )
            }
        }
    }

    editing?.let { ExpenseDialog(vm, it, isNew = false) { editing = null } }
    if (adding) ExpenseDialog(vm, null, isNew = true) { adding = false }
}

@Composable
private fun ExpenseRow(e: Expense, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(e.utility, Brand.colorFor(e.utility))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.utility.trim(), fontWeight = FontWeight.Bold)
                Text(
                    if (e.isPaid) "${e.paidBy} · ${e.paidOn} · ${e.mode}" else "Tap to mark paid",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (e.isPaid) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(inr(e.amount), fontWeight = FontWeight.SemiBold)
                    Text("+" + inr(rewardOf(e)), fontSize = 12.sp, color = Brand.Green, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Pill("Pending", Brand.Amber)
            }
        }
    }
}

@Composable
private fun ExpenseDialog(vm: AppViewModel, item: Expense?, isNew: Boolean, onDismiss: () -> Unit) {
    val s = vm.state ?: return
    val ctx = LocalContext.current
    val isPlaceholder = item != null && item.row < 0
    var utility by remember { mutableStateOf(item?.utility?.trim() ?: "") }
    var paidBy by remember { mutableStateOf(item?.paidBy?.ifBlank { null } ?: vm.defaultPaidBy()) }
    var date by remember {
        mutableStateOf(
            item?.paidOn?.takeIf { Regex("""\d{4}-\d{2}-\d{2}""").matches(it) } ?: vm.defaultDate().toString()
        )
    }
    var amountText by remember { mutableStateOf(item?.amount?.let { plain(it) } ?: "") }
    var mode by remember { mutableStateOf(item?.mode ?: "") }
    var adhocText by remember { mutableStateOf(item?.adhoc?.takeIf { it != 0.0 }?.let { plain(it) } ?: "") }
    var confirmClear by remember { mutableStateOf(false) }

    val amount = amountText.toDoubleOrNull()
    val adhoc = if (adhocText.isBlank()) 0.0 else adhocText.toDoubleOrNull()
    val rate = RewardMath.rateOf(s.rewards, mode)
    val valid = utility.isNotBlank() && amount != null && amount > 0.0 && mode.isNotBlank() &&
        paidBy.isNotBlank() && adhoc != null
    val methods = s.rewards.sortedByDescending { it.rate }.map { it.name }

    AlertDialog(
        onDismissRequest = { if (!vm.busy) onDismiss() },
        title = { Text(if (isNew) "Add payment" else if (item?.isPaid == true) "Edit payment" else "Mark payment") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isNew && !item?.utility.isNullOrBlank()) HistoryChart(vm, "expense", item!!.utility)
                OutlinedTextField(
                    value = utility, onValueChange = { utility = it }, label = { Text("Item") },
                    singleLine = true, readOnly = isPlaceholder, modifier = Modifier.fillMaxWidth(),
                )
                Picker("Paid by", paidBy, s.paidByOptions, { paidBy = it })
                OutlinedButton(
                    onClick = {
                        val d = runCatching { LocalDate.parse(date) }.getOrElse { vm.defaultDate() }
                        DatePickerDialog(
                            ctx, { _, y, m, day -> date = LocalDate.of(y, m + 1, day).toString() },
                            d.year, d.monthValue - 1, d.dayOfMonth,
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Paid on: $date") }
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it }, label = { Text("Amount (₹)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Picker("Paid with", mode, methods, { mode = it }) { name ->
                    val r = RewardMath.rateOf(s.rewards, name)
                    if (r == null) name else "$name (${pctOfRate(r)})"
                }
                OutlinedTextField(
                    value = adhocText, onValueChange = { adhocText = it }, label = { Text("Extra reward ₹ (optional)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (amount != null && rate != null) {
                    Text(
                        "Reward: ${inr(RewardMath.expenseReward(amount, rate, adhoc ?: 0.0))} at ${pctOfRate(rate)}",
                        color = Brand.Green, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        confirmButton = {
            PrimaryButton(if (vm.busy) "Saving…" else "Save to sheet", enabled = valid && !vm.busy) {
                vm.markExpense(
                    row = item?.row ?: -1, origUtility = item?.utility ?: "", utility = utility.trim(),
                    paidBy = paidBy, paidOn = date, amount = amount!!, mode = mode, adhoc = adhoc ?: 0.0,
                    createIfMissing = isNew,
                ) { ok -> if (ok) onDismiss() }
            }
        },
        dismissButton = {
            Row {
                if (item != null && item.isPaid && item.row > 0) {
                    TextButton(
                        onClick = {
                            if (!confirmClear) confirmClear = true
                            else vm.clearExpense(item.row, item.utility) { ok -> if (ok) onDismiss() }
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
