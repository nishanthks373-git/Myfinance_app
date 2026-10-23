package com.spendtracker

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DashboardScreen(vm: AppViewModel, onNavigate: (Int) -> Unit) {
    val s = vm.state
    var expanded by remember { mutableStateOf<String?>(null) }
    val sum = if (s != null) RewardMath.summarize(s) else null

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            GradientBox {
                MonthBar(vm, tint = Color.White)
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp, top = 4.dp)) {
                    Text("Returns this month", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                    Text(
                        if (sum != null) inr(sum.returns) else "—",
                        color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroPill("Spent " + (if (sum != null) inr(sum.spend) else "—"))
                        HeroPill((if (sum != null) String.format(java.util.Locale.US, "%.2f", sum.pct) else "—") + "% return")
                    }
                }
            }
        }

        if (!vm.configured) {
            item {
                AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = { onNavigate(4) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Connect your sheet", fontWeight = FontWeight.Bold)
                        Text("Open Settings and paste your Apps Script web app URL and secret.")
                        Pill("Open Settings", Brand.Indigo, filled = true, onClick = { onNavigate(4) })
                    }
                }
            }
            return@LazyColumn
        }
        if (s == null || sum == null) return@LazyColumn

        item {
            val pendingExpenses = s.expenses.count { !it.isPaid }
            val pendingBills = s.ccBills.count { !it.isPaid }
            AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Still to mark this month", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (pendingExpenses + pendingBills == 0) {
                            Pill("All caught up", Brand.Green)
                        } else {
                            Pill("$pendingExpenses expenses", Brand.Amber, onClick = { onNavigate(1) })
                            Pill("$pendingBills card bills", Brand.Orange, onClick = { onNavigate(2) })
                        }
                    }
                    Text(
                        "Tap a chip to jump there. Cards with no dues stay in the bills count.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Box(Modifier.padding(horizontal = 16.dp)) { SectionTitle("By payment method") }
        }
        if (sum.byMethod.isEmpty()) {
            item {
                Text(
                    "Nothing recorded for this month yet.",
                    Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val maxSpend = sum.byMethod.maxOfOrNull { it.spend } ?: 1.0
        items(sum.byMethod) { row ->
            val color = Brand.colorFor(row.method)
            val rate = RewardMath.rateOf(s.rewards, row.method)
            val open = expanded == row.method
            AppCard(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).animateContentSize(),
                onClick = { expanded = if (open) null else row.method },
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(row.method, color)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.method, fontWeight = FontWeight.Bold)
                            if (rate != null) Text("rate ${pctOfRate(rate)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(inr(row.spend), fontWeight = FontWeight.SemiBold)
                            Text("+" + inr(row.returns), fontSize = 12.sp, color = Brand.Green, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    LinearProgressIndicator(
                        progress = { (row.spend / maxSpend).toFloat().coerceIn(0.02f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                        color = color,
                        trackColor = color.copy(alpha = 0.15f),
                    )
                    if (open) {
                        val eff = if (row.spend > 0) row.returns / row.spend * 100.0 else 0.0
                        Text("Effective return this month: ${trimNum(eff)}%", fontSize = 13.sp)
                        if (row.method in RewardMath.cappedMethods) {
                            Text(
                                "Card-bill cashback on this method is capped at ${inr(RewardMath.DEBIT_CAP)} per month, same as your sheet.",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        val months = s.history.filter { (it.spends ?: 0.0) > 0.0 }.takeLast(6)
        if (months.isNotEmpty()) {
            item {
                Box(Modifier.padding(horizontal = 16.dp)) { SectionTitle("Return % by month") }
            }
            item {
                AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp).height(150.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        months.forEach { h ->
                            val pct = h.pct ?: 0.0
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                Text(trimNum(Math.round(pct * 10.0) / 10.0) + "%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    Modifier
                                        .width(30.dp)
                                        .height((pct * 18).dp.coerceIn(6.dp, 90.dp))
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Brush.verticalGradient(listOf(Brand.Pink, Brand.Indigo))),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(shortMonth(h.month), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
