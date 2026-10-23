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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CardsScreen(vm: AppViewModel) {
    val s = vm.state
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        if (!vm.configured) {
            NotConfigured()
            return
        }
        if (s == null) return

        OutlinedTextField(
            value = query, onValueChange = { query = it }, label = { Text("Search cards") },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        val cards = s.cards.filter { it.firstOrNull()?.contains(query, ignoreCase = true) == true }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("Payment method rates (for paying card bills)", style = MaterialTheme.typography.titleSmall)
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        s.rewards.sortedByDescending { it.rate }.forEach {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(it.name)
                                Text(pctOfRate(it.rate))
                            }
                        }
                    }
                }
            }
            item { Text("Cards", style = MaterialTheme.typography.titleSmall) }
            items(cards) { row ->
                val name = row.firstOrNull().orEmpty()
                val due = s.dues.firstOrNull { it.card == name }
                Card(Modifier.fillMaxWidth().clickable { expanded = if (expanded == name) null else name }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(name, fontWeight = FontWeight.SemiBold)
                        val fee = row.getOrNull(1).orEmpty()
                        if (fee.isNotBlank()) Text("Annual fee: $fee", style = MaterialTheme.typography.bodySmall)
                        if (due != null) {
                            Text(
                                "Bill ${due.billDate} · due ${due.dueDate}" +
                                    (due.limit?.let { " · limit ${inr(it)}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (expanded == name) {
                            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (i in 2 until row.size) {
                                    val v = row[i]
                                    if (v.isBlank()) continue
                                    Text(
                                        "${s.cardHeader.getOrNull(i).orEmpty()}: $v",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
