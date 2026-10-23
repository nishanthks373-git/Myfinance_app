package com.spendtracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val inrFormat: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
    maximumFractionDigits = 2
    minimumFractionDigits = 0
}

fun inr(v: Double?): String = if (v == null) "—" else "₹" + inrFormat.format(v)

/** 4 decimals max, no trailing zeros, always a dot: 2.5, 10, 0.5 */
fun trimNum(v: Double): String = String.format(Locale.US, "%.4f", v).trimEnd('0').trimEnd('.')

/** 0.025 -> "2.5%", 0.1 -> "10%" */
fun pctOfRate(rate: Double): String = trimNum(rate * 100.0) + "%"

/** 0.025 -> "2.5" (for editing) */
fun ratePctText(rate: Double): String = trimNum(rate * 100.0)

fun plain(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

fun monthTitle(m: YearMonth): String =
    m.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + m.year

fun shortMonth(key: String): String =
    runCatching { YearMonth.parse(key).month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }.getOrDefault(key)

fun initials(name: String): String {
    val words = name.trim().split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

@Composable
fun MonthBar(vm: AppViewModel, tint: Color = MaterialTheme.colorScheme.onBackground) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.changeMonth(-1) }) {
                Text("‹", color = tint, style = MaterialTheme.typography.headlineMedium)
            }
            Text(monthTitle(vm.month), color = tint, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { vm.changeMonth(1) }) {
                Text("›", color = tint, style = MaterialTheme.typography.headlineMedium)
            }
        }
        if (vm.loading) {
            CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), color = tint, strokeWidth = 2.dp)
        } else {
            IconButton(onClick = { vm.refresh() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = tint)
            }
        }
    }
}

@Composable
fun NotConfigured() {
    AppCard(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            "Connect your sheet first: open Settings, paste the Apps Script web app URL and your secret.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val m = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Card(
        modifier = m,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        content = content,
    )
}

@Composable
fun Avatar(label: String, color: Color, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials(label), color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun Pill(text: String, color: Color, filled: Boolean = false, onClick: (() -> Unit)? = null) {
    val base = Modifier.clip(RoundedCornerShape(50)).background(if (filled) color else color.copy(alpha = 0.14f))
    val m = if (onClick != null) base.clickable(onClick = onClick) else base
    Box(m.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, color = if (filled) Color.White else color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A coloured header band used on Home. */
@Composable
fun GradientBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.linearGradient(listOf(Brand.Indigo, Brand.Violet, Brand.Pink))),
        content = content,
    )
}

@Composable
fun HeroPill(text: String) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.22f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Stat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
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
