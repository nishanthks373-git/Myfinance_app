package com.spendtracker

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CardsScreen(vm: AppViewModel) {
    val s = vm.state
    var tab by remember { mutableStateOf(0) }            // 0 rates, 1 cards, 2 due dates
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<String?>(null) }
    var editRate by remember { mutableStateOf<Reward?>(null) }
    var addMethod by remember { mutableStateOf(false) }
    var editField by remember { mutableStateOf<Pair<String, String>?>(null) }   // card to header
    var editDue by remember { mutableStateOf<Due?>(null) }

    Column(Modifier.fillMaxSize()) {
        if (!vm.configured) {
            NotConfigured()
            return
        }
        if (s == null) return

        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            listOf("Rates", "Cards", "Due dates").forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title, fontWeight = FontWeight.SemiBold) })
            }
        }

        if (tab != 0) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, label = { Text(if (tab == 1) "Search a card or category (movies, fuel, lounge…)" else "Search cards") },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }

        Box(Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (tab) {
                    0 -> {
                        item {
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer).padding(12.dp),
                            ) {
                                Text(
                                    "Tap a method to change its return %. Every row in your sheet looks the rate up " +
                                        "from here, so changing it also changes past months. To start a new rate " +
                                        "only from now on, add a new method instead.",
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        items(s.rewards.sortedByDescending { it.rate }) { r ->
                            AppCard(Modifier.fillMaxWidth(), onClick = { editRate = r }) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(r.name, Brand.colorFor(r.name))
                                    Spacer(Modifier.width(12.dp))
                                    Text(r.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                    Pill(pctOfRate(r.rate), Brand.rateColor(r.rate), filled = true)
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Default.Edit, contentDescription = "Edit rate", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    1 -> {
                        val terms = CardSearch.terms(query)
                        val found = s.cards.mapNotNull { row ->
                            val nameHit = row.firstOrNull()?.contains(query.trim(), ignoreCase = true) == true
                            val hits = CardSearch.matches(s.cardHeader, row, terms)
                            if (query.isBlank() || nameHit || hits.isNotEmpty()) Triple(row, hits, nameHit) else null
                        }
                        if (query.isNotBlank() && found.isEmpty()) {
                            item {
                                Text(
                                    "No card in your list mentions \"${query.trim()}\". Add it to the card's fields (or its Additional column) and it will show up here.",
                                    modifier = Modifier.padding(16.dp), fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(found) { (row, fieldHits, _) ->
                            val name = row.firstOrNull().orEmpty()
                            val due = s.dues.firstOrNull { it.card == name }
                            val open = expanded == name
                            AppCard(
                                Modifier.fillMaxWidth().animateContentSize(),
                                onClick = { expanded = if (open) null else name },
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Avatar(name, Brand.colorFor(name))
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(name, fontWeight = FontWeight.Bold)
                                            val fee = row.getOrNull(1).orEmpty()
                                            if (fee.isNotBlank()) Text("Annual fee: $fee", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (due != null) {
                                                Text(
                                                    "Bill ${due.billDate} · due ${due.dueDate}",
                                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        Text(if (open) "▴" else "▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (!open && query.isNotBlank() && fieldHits.isNotEmpty()) {
                                        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            fieldHits.forEach { (h, v) ->
                                                Column(
                                                    Modifier.fillMaxWidth()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(Brand.Indigo.copy(alpha = 0.08f))
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                ) {
                                                    Text(h, fontSize = 12.sp, color = Brand.Indigo, fontWeight = FontWeight.SemiBold)
                                                    Text(v, fontSize = 14.sp)
                                                }
                                            }
                                        }
                                    }
                                    if (open) {
                                        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            for (i in 1 until s.cardHeader.size) {
                                                val header = s.cardHeader[i]
                                                val v = row.getOrNull(i).orEmpty()
                                                Row(
                                                    Modifier.fillMaxWidth()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .clickable { editField = name to header }
                                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(header, fontSize = 12.sp, color = Brand.Indigo, fontWeight = FontWeight.SemiBold)
                                                        Text(
                                                            if (v.isBlank()) "—" else v, fontSize = 14.sp,
                                                            color = if (v.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                                        )
                                                    }
                                                    Icon(Icons.Default.Edit, contentDescription = "Edit $header", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        val dues = s.dues.filter { it.card.contains(query, ignoreCase = true) }
                        items(dues) { d ->
                            AppCard(Modifier.fillMaxWidth(), onClick = { editDue = d }) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(d.card, Brand.colorFor(d.card))
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(d.card, fontWeight = FontWeight.Bold)
                                        Text("Bill ${d.billDate}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Due ${d.dueDate}", fontSize = 12.sp, color = Brand.Orange, fontWeight = FontWeight.SemiBold)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(if (d.limit != null) inr(d.limit) else "—", fontWeight = FontWeight.SemiBold)
                                        Text("limit", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { addMethod = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add method") },
                )
            }
        }
    }

    editRate?.let { RateDialog(vm, it) { editRate = null } }
    if (addMethod) RateDialog(vm, null) { addMethod = false }
    editField?.let { (card, header) ->
        val current = s?.let { st ->
            st.cards.firstOrNull { it.firstOrNull() == card }?.getOrNull(st.cardHeader.indexOf(header))
        }.orEmpty()
        FieldDialog(vm, card, header, current) { editField = null }
    }
    editDue?.let { DueDialog(vm, it) { editDue = null } }
}

@Composable
private fun RateDialog(vm: AppViewModel, existing: Reward?, onDismiss: () -> Unit) {
    val s = vm.state ?: return
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var pctText by remember { mutableStateOf(existing?.let { ratePctText(it.rate) } ?: "") }
    val pct = pctText.toDoubleOrNull()
    val nameTaken = existing == null && s.rewards.any { it.name.equals(name.trim(), ignoreCase = true) }
    val valid = name.isNotBlank() && pct != null && pct >= 0.0 && pct <= 100.0 && !nameTaken

    AlertDialog(
        onDismissRequest = { if (!vm.busy) onDismiss() },
        title = { Text(if (existing == null) "Add payment method" else "Edit return %") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Method name") },
                    singleLine = true, readOnly = existing != null, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pctText, onValueChange = { pctText = it }, label = { Text("Return % (e.g. 2.5)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (existing != null) {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.errorContainer).padding(12.dp),
                    ) {
                        Text(
                            "This changes the return % of every row in your sheet that uses \"${existing.name}\", " +
                                "including past months.",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                if (nameTaken) Text("A method with this name already exists.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        },
        confirmButton = {
            PrimaryButton(
                if (vm.busy) "Saving…" else if (existing == null) "Add to sheet" else "Update in sheet",
                enabled = valid && !vm.busy,
            ) {
                vm.setRate(name.trim(), pct!! / 100.0, existing == null) { ok -> if (ok) onDismiss() }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.busy) { Text("Cancel") } },
    )
}

@Composable
private fun FieldDialog(vm: AppViewModel, card: String, header: String, current: String, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = { if (!vm.busy) onDismiss() },
        title = { Text(card) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value, onValueChange = { value = it }, label = { Text(header) },
                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                )
                Text(
                    "Saved as typed, like editing the cell yourself. Leave empty to clear it.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            PrimaryButton(if (vm.busy) "Saving…" else "Update in sheet", enabled = !vm.busy && value.length <= 500) {
                vm.editCardField(card, header, value.trim()) { ok -> if (ok) onDismiss() }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.busy) { Text("Cancel") } },
    )
}

@Composable
private fun DueDialog(vm: AppViewModel, due: Due, onDismiss: () -> Unit) {
    var billDate by remember { mutableStateOf(due.billDate) }
    var dueDate by remember { mutableStateOf(due.dueDate) }
    var limitText by remember { mutableStateOf(due.limit?.let { plain(it) } ?: "") }
    val limit = if (limitText.isBlank()) null else limitText.toDoubleOrNull()
    val valid = limitText.isBlank() || limit != null

    AlertDialog(
        onDismissRequest = { if (!vm.busy) onDismiss() },
        title = { Text(due.card) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = billDate, onValueChange = { billDate = it }, label = { Text("Bill date (e.g. 3rd)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dueDate, onValueChange = { dueDate = it }, label = { Text("Due date (e.g. 18th or 2nd next month)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = limitText, onValueChange = { limitText = it }, label = { Text("Card limit (₹)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            PrimaryButton(if (vm.busy) "Saving…" else "Update in sheet", enabled = valid && !vm.busy) {
                vm.editDue(due.card, billDate.trim(), dueDate.trim(), limit) { ok -> if (ok) onDismiss() }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.busy) { Text("Cancel") } },
    )
}
