package com.spendtracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth

/**
 * Last 12 months of one expense or card bill, with the viewed month highlighted.
 * Tap a bar to see that month's amount, reward and payment method.
 */
@Composable
fun HistoryChart(vm: AppViewModel, kind: String, name: String) {
    val current = vm.month.toString()
    var points by remember(name, current) { mutableStateOf<List<ItemPoint>?>(null) }
    var error by remember(name, current) { mutableStateOf<String?>(null) }
    var picked by remember(name, current) { mutableStateOf(current) }

    LaunchedEffect(name, current) {
        try {
            points = Api.itemHistory(vm.scriptUrl, vm.token, current, kind, name)
        } catch (e: Exception) {
            error = if (e.message?.contains("Unknown action") == true)
                "Paste the latest Code.gs into Apps Script and redeploy to see history."
            else e.message ?: "Could not load history"
        }
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Brand.Indigo.copy(alpha = 0.07f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Past 12 months", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Brand.Indigo)
        val pts = points
        when {
            error != null -> Text(error!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            pts == null -> Text("Loading…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            pts.none { it.amount != null } -> Text("No earlier payments found for this item.", fontSize = 12.sp)
            else -> {
                val max = (pts.mapNotNull { it.amount }.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
                Row(
                    Modifier.fillMaxWidth().height(92.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    pts.forEach { p ->
                        val isPicked = p.month == picked
                        val isCurrent = p.month == current
                        val frac = ((p.amount ?: 0.0) / max).toFloat()
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).clickable { picked = p.month },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            val brush = when {
                                p.amount == null -> SolidColor(Color.Gray.copy(alpha = 0.25f))
                                isCurrent -> Brush.verticalGradient(listOf(Brand.Pink, Brand.Indigo))
                                else -> SolidColor(Brand.Indigo.copy(alpha = 0.55f))
                            }
                            Box(
                                Modifier.fillMaxWidth()
                                    .height((frac * 66f).coerceAtLeast(if (p.amount != null) 4f else 2f).dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(brush),
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                shortMonth(p.month).take(1), fontSize = 10.sp,
                                fontWeight = if (isPicked) FontWeight.Bold else FontWeight.Normal,
                                color = if (isPicked) Brand.Indigo else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                val sel = pts.firstOrNull { it.month == picked }
                val title = runCatching { monthTitle(YearMonth.parse(picked)) }.getOrDefault(picked)
                if (sel?.amount != null) {
                    Text(
                        "$title: ${inr(sel.amount)} · reward ${inr(sel.reward ?: 0.0)}" +
                            if (sel.mode.isNotBlank()) " · ${sel.mode}" else "",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text("$title: not paid", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val paid = pts.mapNotNull { it.amount }
                Text(
                    "Average ${inr(paid.average())} · highest ${inr(paid.max())} · paid ${paid.size} of 12 months",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
