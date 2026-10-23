package com.spendtracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val inrFormat: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
    maximumFractionDigits = 2
    minimumFractionDigits = 0
}

fun inr(v: Double?): String = if (v == null) "—" else "₹" + inrFormat.format(v)

/** 0.025 -> "2.5%", 0.1 -> "10%" */
fun pctOfRate(rate: Double): String {
    val p = rate * 100.0
    return if (p == Math.floor(p)) "${p.toInt()}%" else "${"%.2f".format(p).trimEnd('0').trimEnd('.')}%"
}

fun plain(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

fun monthTitle(m: YearMonth): String =
    m.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + m.year

@Composable
fun MonthBar(vm: AppViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.changeMonth(-1) }) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
            Text(monthTitle(vm.month), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { vm.changeMonth(1) }) { Text("›", style = MaterialTheme.typography.headlineMedium) }
        }
        if (vm.loading) {
            CircularProgressIndicator(Modifier.padding(12.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
        }
    }
}

@Composable
fun NotConfigured() {
    Box(Modifier.fillMaxWidth().padding(24.dp)) {
        Text(
            "Connect your sheet first: open Settings, paste the Apps Script web app URL and your secret.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Dropdown selector: shows the current value on a button, options in a menu. */
@Composable
fun Picker(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    display: (String) -> String = { it },
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (value.isBlank()) label else "$label: ${display(value)}",
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text("▾")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(display(opt)) },
                    onClick = { onSelect(opt); open = false },
                )
            }
        }
    }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled) { Text(text) }
}
