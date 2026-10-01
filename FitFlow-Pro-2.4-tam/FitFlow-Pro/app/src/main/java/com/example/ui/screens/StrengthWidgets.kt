package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ExerciseProgressPoint
import com.example.core.LiftStandard
import com.example.core.LoadStatus
import com.example.core.MuscleLoad
import com.example.core.MuscleMap
import com.example.core.StrengthInsights
import com.example.core.formatDateShort
import com.example.core.kg
import com.example.core.trimNum
import com.example.ui.components.FitCard
import com.example.ui.components.FitTextField
import com.example.ui.components.MuscleColors
import com.example.ui.components.OverlineText
import com.example.ui.theme.Palette
import com.example.ui.theme.fit
import com.example.ui.theme.mono
import kotlin.math.abs
import kotlin.math.roundToInt

/* ==========================================================================
 * İlerleme › Güç / Kaslar sekmeleri için kartlar (2.28)
 * ========================================================================== */

private fun Float.tr(): String = trimNum().replace('.', ',')

@Composable
internal fun levelColor(idx: Int): Color = when (idx) {
    0 -> MaterialTheme.fit.muted
    1 -> MaterialTheme.fit.accent.copy(alpha = 0.7f)
    2 -> MaterialTheme.fit.accent
    3 -> MaterialTheme.fit.success
    4 -> Palette.violet
    else -> MaterialTheme.fit.gold
}

/* ------------------------------ 1. Güç özeti ------------------------------ */

@Composable
internal fun StrengthSummaryCard(profile: List<LiftStandard>, total: Float, bodyWeight: Float, change: Float?) {
    val gold = MaterialTheme.fit.gold
    val parts = com.example.core.ProgressAnalytics.totalParts(profile)
    val missing = listOfNotNull(
        "squat".takeIf { parts.none { it.liftKey == "squat" } },
        "bench press".takeIf { parts.none { it.liftKey == "bench" } },
        "deadlift / RDL".takeIf { parts.none { it.liftKey == "deadlift" || it.liftKey == "rdl" } }
    )
    FitCard(contentPadding = PaddingValues(16.dp)) {
        OverlineText("Güç özeti · ana kaldırışlar toplamı")
        Spacer(Modifier.height(6.dp))
        if (total > 0f) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(total.kg(), style = MaterialTheme.typography.displaySmall.mono().copy(fontWeight = FontWeight.SemiBold), color = gold)
                    Text(parts.joinToString(" + ") { shortName(it) } + " · tahmini 1RM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
                }
                if (bodyWeight > 0f) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${(total / bodyWeight).tr()}×", style = MaterialTheme.typography.headlineSmall.mono(), color = MaterialTheme.fit.accent)
                        Text("vücut ağırlığı", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // Döküm: her kaldırışın en iyi seti ve ondan hesaplanan 1RM
            parts.forEach { l ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "En iyi set: ${l.bestWeight.tr()} kg × ${l.bestReps}" + if (l.fromDumbbell) " (dambıl → barbell karşılığı)" else "",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted
                        )
                    }
                    Text(l.e1rm.kg(), style = MaterialTheme.typography.titleSmall.mono().copy(fontWeight = FontWeight.SemiBold), color = gold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "1RM: tek tekrarda kaldırabileceğin tahmini en yüksek ağırlık. Set ağırlıklarının toplamı değildir; her kaldırışın en iyi setinden hesaplanır." +
                    (if (missing.isNotEmpty()) " Eksik: ${missing.joinToString(", ")}." else ""),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted
            )
            if (change != null) {
                Spacer(Modifier.height(10.dp))
                val c = if (change >= 0f) MaterialTheme.fit.success else MaterialTheme.fit.warning
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(c.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (change >= 0f) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingFlat, null, tint = c, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Son 12 haftada ${if (change >= 0f) "+" else "−"}${abs(change).tr()} kg", style = MaterialTheme.typography.labelLarge, color = c)
                }
            }
        } else {
            Text("Toplam için squat, bench press ve deadlift (ya da RDL) kaydı gerekir.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
        }
    }
}

private fun shortName(l: LiftStandard) = when (l.liftKey) {
    "squat" -> "Squat"; "bench" -> "Bench"; "deadlift" -> "Deadlift"; "rdl" -> "RDL"; else -> l.displayName
}

/* ------------------------------ 2–3. 1RM kartı ------------------------------ */

private val PERIODS = listOf("1A" to 30, "3A" to 90, "6A" to 180, "Tümü" to 0)

@Composable
internal fun E1rmCard(points: List<ExerciseProgressPoint>, goal: Float?, onGoal: () -> Unit) {
    var period by remember { mutableIntStateOf(3) }
    val now = System.currentTimeMillis()
    // Rekor noktaları tüm geçmişe göre (koşan en iyi aşıldığında)
    val prFlags = remember(points) {
        var best = 0f
        points.map { p -> (p.e1rm > best + 0.01f).also { if (it) best = p.e1rm } }
    }
    val days = PERIODS[period].second
    val idx = points.indices.filter { days == 0 || now - points[it].dateMillis <= days * StrengthInsights.DAY_MS }
    val shown = idx.map { points[it] }
    val prs = idx.map { prFlags[it] }
    val trend = remember(shown) { StrengthInsights.linearTrend(shown.map { it.dateMillis to it.e1rm }) }
    var sel by remember(shown) { mutableIntStateOf(shown.lastIndex) }
    val accent = MaterialTheme.fit.accent
    val gold = MaterialTheme.fit.gold
    val best = points.maxOfOrNull { it.e1rm } ?: 0f

    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val p = shown.getOrNull(sel)
                Text(p?.e1rm?.kg() ?: "—", style = MaterialTheme.typography.headlineMedium.mono(), color = if (p != null && prs.getOrElse(sel) { false }) gold else accent)
                Text(
                    p?.let { formatDateShort(it.dateMillis) + if (prs.getOrElse(sel) { false }) " · rekor" else "" } ?: "Bu dönemde kayıt yok",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.muted
                )
            }
            Row(Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.fit.elevated).padding(2.dp)) {
                PERIODS.forEachIndexed { i, (l, _) ->
                    Text(
                        l, style = MaterialTheme.typography.labelMedium,
                        color = if (period == i) MaterialTheme.colorScheme.onSurface else MaterialTheme.fit.muted,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (period == i) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { period = i }.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (shown.size < 2) {
            Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                Text("Bu dönemde grafik için yeterli seans yok", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
            }
        } else {
            val grid = MaterialTheme.fit.cardBorder
            val vals = shown.map { it.e1rm }
            val showGoal = goal != null && goal <= vals.max() * 1.3f
            val hiRaw = maxOf(vals.max(), if (showGoal) goal!! else 0f)
            val loRaw = vals.min()
            val span = (hiRaw - loRaw).coerceAtLeast(hiRaw * 0.05f).coerceAtLeast(1f)
            val lo = loRaw - span * 0.15f; val hi = hiRaw + span * 0.15f
            val t0 = shown.first().dateMillis; val t1 = shown.last().dateMillis
            val tSpan = (t1 - t0).coerceAtLeast(1L).toFloat()
            Row {
                Canvas(
                    Modifier.weight(1f).height(170.dp).pointerInput(shown) {
                        detectTapGestures { o ->
                            val t = t0 + (o.x / size.width) * tSpan
                            sel = shown.indices.minByOrNull { abs(shown[it].dateMillis - t) } ?: sel
                        }
                    }
                ) {
                    fun x(t: Long) = (t - t0) / tSpan * size.width
                    fun y(v: Float) = size.height - (v - lo) / (hi - lo) * size.height
                    repeat(4) { i ->
                        val yy = size.height * i / 3f
                        drawLine(grid, Offset(0f, yy), Offset(size.width, yy), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f)))
                    }
                    val path = Path()
                    shown.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.dateMillis), y(p.e1rm)) else path.lineTo(x(p.dateMillis), y(p.e1rm)) }
                    val area = Path().apply { addPath(path); lineTo(x(t1), size.height); lineTo(x(t0), size.height); close() }
                    drawPath(area, Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent)))
                    drawPath(path, accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                    trend?.let { tr ->
                        drawLine(
                            accent.copy(alpha = 0.55f), Offset(x(t0), y(tr.at(t0))), Offset(x(t1), y(tr.at(t1))), 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                        )
                    }
                    if (showGoal) {
                        drawLine(gold.copy(alpha = 0.8f), Offset(0f, y(goal!!)), Offset(size.width, y(goal)), 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
                    }
                    shown.forEachIndexed { i, p ->
                        val c = Offset(x(p.dateMillis), y(p.e1rm))
                        if (prs[i]) drawCircle(gold, 4.5.dp.toPx(), c) else drawCircle(accent, 2.5.dp.toPx(), c)
                        if (i == sel) {
                            drawCircle(Color.White, 6.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                        }
                    }
                }
                Column(Modifier.height(170.dp).padding(start = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(hi.roundToInt().toString(), style = MaterialTheme.typography.labelSmall.mono(), color = MaterialTheme.fit.muted)
                    Text(lo.roundToInt().toString(), style = MaterialTheme.typography.labelSmall.mono(), color = MaterialTheme.fit.muted)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(gold)); Spacer(Modifier.width(4.dp))
                Text("rekor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.width(14.dp).height(2.dp).background(accent.copy(alpha = 0.55f))); Spacer(Modifier.width(4.dp))
                Text("eğilim", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
                if (showGoal) {
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.width(14.dp).height(2.dp).background(gold)); Spacer(Modifier.width(4.dp))
                    Text("hedef", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallStat("Seans", "${shown.size}", Modifier.weight(1f))
            SmallStat("En iyi", best.kg(), Modifier.weight(1f))
            SmallStat(
                "Eğilim",
                trend?.let { (if (it.slopePerWeek >= 0f) "+" else "−") + abs(it.slopePerWeek).tr() + " kg/hf" } ?: "—",
                Modifier.weight(1f),
                trend?.let { if (it.slopePerWeek > 0.05f) MaterialTheme.fit.success else if (it.slopePerWeek < -0.05f) MaterialTheme.fit.warning else null }
            )
        }
        Spacer(Modifier.height(12.dp))
        GoalSection(best, goal, trend, onGoal)
    }
}

@Composable
private fun GoalSection(best: Float, goal: Float?, trend: StrengthInsights.Trend?, onGoal: () -> Unit) {
    val gold = MaterialTheme.fit.gold
    val now = System.currentTimeMillis()
    if (goal != null) {
        val left = goal - best
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(gold.copy(alpha = 0.08f))
                .border(1.dp, gold.copy(alpha = 0.3f), RoundedCornerShape(14.dp)).clickable { onGoal() }.padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Flag, null, tint = gold, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Hedef ${goal.kg()}", style = MaterialTheme.typography.labelLarge, color = gold, modifier = Modifier.weight(1f))
                Text("${(best / goal * 100).roundToInt().coerceAtMost(100)}%", style = MaterialTheme.typography.labelLarge.mono(), color = gold)
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(gold.copy(alpha = 0.18f))) {
                Box(Modifier.fillMaxWidth((best / goal).coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(gold))
            }
            Spacer(Modifier.height(8.dp))
            val wk = trend?.weeksTo(goal, now)
            Text(
                when {
                    left <= 0f -> "Hedefe ulaştın! Yeni bir hedef koymak için dokun."
                    wk == null -> "${left.tr()} kg kaldı. Eğilim şu an düz; ilerleme başlayınca tahmini süre çıkar."
                    else -> "${left.tr()} kg kaldı · bu hızla ~${ceilWeeks(wk)} hafta (${formatDateShort(now + (wk * StrengthInsights.WEEK_MS).toLong())})"
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted
            )
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                trend?.takeIf { it.slopePerWeek > 0.05f }?.let { "Bu hızla 8 haftada ~${it.at(now + 8 * StrengthInsights.WEEK_MS).roundToInt()} kg" }
                    ?: "Hedef koy, ulaşma süresini tahmin edelim.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted, modifier = Modifier.weight(1f)
            )
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(gold.copy(alpha = 0.14f)).clickable { onGoal() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Flag, null, tint = gold, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Hedef koy", style = MaterialTheme.typography.labelLarge, color = gold)
            }
        }
    }
}

private fun ceilWeeks(w: Float): Int = kotlin.math.ceil(w.toDouble()).toInt().coerceAtLeast(1)

@Composable
private fun SmallStat(label: String, value: String, modifier: Modifier, color: Color? = null) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.fit.elevated.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
        Text(value, style = MaterialTheme.typography.titleSmall.mono(), color = color ?: MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
internal fun GoalDialog(name: String, best: Float, current: Float?, onSave: (Float?) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current?.trimNum() ?: "") }
    fun r(v: Float) = (kotlin.math.round(v / 2.5f) * 2.5f)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("1RM hedefi", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text("$name · şu an en iyi ${best.kg()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
                Spacer(Modifier.height(12.dp))
                FitTextField(text, { text = it }, "Hedef (kg)", keyboardType = KeyboardType.Decimal)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1.05f to "+5%", 1.10f to "+10%", 1.20f to "+20%").forEach { (f, l) ->
                        Text(
                            "$l · ${r(best * f).trimNum()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.accent,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.fit.accent.copy(alpha = 0.12f))
                                .clickable { text = r(best * f).trimNum() }.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.replace(',', '.').toFloatOrNull()) }) { Text("Kaydet", color = MaterialTheme.fit.accent) }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onSave(null) }) { Text("Hedefi kaldır", color = MaterialTheme.fit.danger) }
                TextButton(onClick = onDismiss) { Text("Vazgeç", color = MaterialTheme.fit.muted) }
            }
        }
    )
}

/* ------------------------------ 4. Tekrar tablosu ------------------------------ */

@Composable
internal fun RepMaxCard(name: String, e1rm: Float, rows: List<StrengthInsights.RepMaxRow>) {
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Tekrar–ağırlık tablosu", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                Text("$name · tahmini 1RM ${e1rm.kg()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            listOf("Tekrar" to 0.8f, "Tahmini" to 1.2f, "%1RM" to 0.8f, "En iyin" to 1.2f).forEach { (h, w) ->
                Text(h, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted, modifier = Modifier.weight(w),
                    textAlign = if (h == "Tekrar") TextAlign.Start else TextAlign.End)
            }
        }
        rows.forEachIndexed { i, r ->
            val beat = r.bestActual != null && r.bestActual >= r.predicted
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (i % 2 == 0) MaterialTheme.fit.elevated.copy(alpha = 0.45f) else Color.Transparent)
                    .padding(horizontal = 6.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${r.reps}", style = MaterialTheme.typography.bodyMedium.mono().copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(0.8f))
                Text(r.predicted.kg(), style = MaterialTheme.typography.bodyMedium.mono(), modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                Text("${r.pct}%", style = MaterialTheme.typography.labelMedium.mono(), color = MaterialTheme.fit.muted, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                Text(
                    r.bestActual?.kg() ?: "—", style = MaterialTheme.typography.bodyMedium.mono(),
                    color = if (beat) MaterialTheme.fit.success else MaterialTheme.fit.muted,
                    modifier = Modifier.weight(1.2f), textAlign = TextAlign.End
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "En iyin: o tekrar sayısı ve üzerinde kaldırdığın en yüksek ağırlık. Yeşil = tahmini seviyeye ulaşmışsın. Tahminler salondaki plaka adımına yuvarlanır.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted
        )
    }
}

/* ------------------------------ 5. Plato ------------------------------ */

@Composable
internal fun PlateauCard(list: List<StrengthInsights.Plateau>, onOpen: (Long) -> Unit) {
    val warn = MaterialTheme.fit.warning
    FitCard(border = warn.copy(alpha = 0.3f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PauseCircle, null, tint = warn, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Duraklayan hareketler", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Text("${list.size}", style = MaterialTheme.typography.labelLarge.mono(), color = warn)
        }
        list.take(4).forEach { p ->
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(p.lift.exerciseId) }.padding(vertical = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.lift.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${p.weeks} hafta", style = MaterialTheme.typography.labelMedium, color = warn)
                }
                Text(
                    "En iyi ${p.lift.bestE1rm.kg()} · son ${p.lift.latestE1rm.kg()} · ${p.lift.sessionsSinceBest} seanstır rekor yok",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    p.suggestion, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(warn.copy(alpha = 0.08f)).padding(10.dp)
                )
            }
        }
    }
}

/* ------------------------------ 6. Isı haritası ------------------------------ */

@Composable
internal fun MuscleHeatmapCard(grid: StrengthInsights.MuscleGrid) {
    var pick by remember(grid) { mutableStateOf<Pair<String, Int>?>(null) }
    val rows = MuscleMap.all.filter { k -> grid.rows[k].orEmpty().any { it > 0f } || k !in setOf(MuscleMap.ADDUCTORS, MuscleMap.FOREARM, MuscleMap.OBLIQUES) }
    val empty = MaterialTheme.fit.elevated
    FitCard {
        Text("Kas × hafta", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
        Text(
            pick?.let { (k, i) ->
                "${MuscleMap.label(k)} · ${formatDateShort(grid.weekStarts[i])} haftası: ${grid.rows[k].orEmpty().getOrElse(i) { 0f }.tr()} etkin set (hedef ${MuscleMap.weeklyTarget(k).first}–${MuscleMap.weeklyTarget(k).last})"
            } ?: "Son 8 haftada kas başına etkin set · hücreye dokun",
            style = MaterialTheme.typography.labelSmall, color = if (pick != null) MaterialTheme.fit.accent else MaterialTheme.fit.muted
        )
        Spacer(Modifier.height(10.dp))
        rows.forEach { k ->
            val vals = grid.rows[k].orEmpty()
            val t = MuscleMap.weeklyTarget(k)
            Row(Modifier.fillMaxWidth().padding(vertical = 1.5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(MuscleMap.label(k), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(78.dp))
                vals.forEachIndexed { i, v ->
                    val c = MuscleColors.forStatus(MuscleLoad(k, v, 0f, -1, t).status)
                    val selected = pick == (k to i)
                    Box(
                        Modifier.weight(1f).height(20.dp).padding(horizontal = 1.5.dp).clip(RoundedCornerShape(4.dp))
                            .background(c?.copy(alpha = if (v <= 0f) 0f else 0.85f) ?: empty)
                            .then(if (v <= 0f) Modifier.background(empty) else Modifier)
                            .then(if (selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(4.dp)) else Modifier)
                            .clickable { pick = if (selected) null else k to i },
                        contentAlignment = Alignment.Center
                    ) {
                        if (v >= 1f) Text(v.roundToInt().toString(), style = MaterialTheme.typography.labelSmall.mono(), color = Color.White.copy(alpha = 0.9f))
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(78.dp))
            grid.weekStarts.forEachIndexed { i, w ->
                Text(
                    if (i % 2 == 1 || i == grid.weekStarts.lastIndex) (if (i == grid.weekStarts.lastIndex) "Bu" else formatDateShort(w).substringBefore(' ')) else "",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            MuscleColors.loadLegend.forEach { (c, l) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(c)); Spacer(Modifier.width(4.dp))
                    Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
                }
            }
        }
    }
}

/* ------------------------------ 7. Program uyumu ------------------------------ */

@Composable
internal fun AdherenceCard(a: StrengthInsights.Adherence) {
    val c = when {
        a.score >= 85 -> MaterialTheme.fit.success
        a.score >= 65 -> MaterialTheme.fit.accent
        else -> MaterialTheme.fit.warning
    }
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Program uyumu", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                Text("Son ${a.weeks} hafta · plan ↔ yapılan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
            }
            Text("%${a.score}", style = MaterialTheme.typography.headlineSmall.mono().copy(fontWeight = FontWeight.SemiBold), color = c)
        }
        Spacer(Modifier.height(12.dp))
        AdhBar("Seans", a.sessionsDone, a.sessionsPlanned, a.sessionPct)
        Spacer(Modifier.height(8.dp))
        AdhBar("Set", a.setsDone, a.setsPlanned, a.setPct)
        if (a.skipped.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            OverlineText("Sık atlanan hareketler")
            a.skipped.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${s.day} · ${s.done}/${s.of} seans", style = MaterialTheme.typography.labelSmall.mono(), color = MaterialTheme.fit.warning)
                }
            }
        }
        if (a.gaps.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            OverlineText("Plana göre geride kalan kaslar")
            a.gaps.forEach { g ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(MuscleMap.label(g.key), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("${g.actual.tr()} / ${g.planned.tr()} set·hf", style = MaterialTheme.typography.labelSmall.mono(), color = MaterialTheme.fit.muted)
                }
            }
        }
        if (a.skipped.isEmpty() && a.gaps.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Programına sadık kalıyorsun; atlanan hareket ya da geride kalan kas yok.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
        }
    }
}

@Composable
private fun AdhBar(label: String, done: Int, of: Int, pct: Int) {
    val c = if (pct >= 85) MaterialTheme.fit.success else if (pct >= 65) MaterialTheme.fit.accent else MaterialTheme.fit.warning
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(44.dp))
        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.fit.elevated)) {
            Box(Modifier.fillMaxWidth(pct / 100f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c))
        }
        Spacer(Modifier.width(10.dp))
        Text("$done/$of", style = MaterialTheme.typography.labelMedium.mono(), color = MaterialTheme.fit.muted, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
    }
}

/* ------------------------------ 8. Kasın güç göstergesi ------------------------------ */

@Composable
internal fun MuscleStrengthRow(m: StrengthInsights.MuscleStrength, onOpen: () -> Unit) {
    val accent = MaterialTheme.fit.accent
    val ch = m.changePct
    val c = when {
        ch == null -> MaterialTheme.fit.muted
        ch > 1f -> MaterialTheme.fit.success
        ch < -1f -> MaterialTheme.fit.warning
        else -> MaterialTheme.fit.muted
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.fit.elevated.copy(alpha = 0.6f))
            .clickable { onOpen() }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Güç göstergesi · 8 hafta", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
            Text(m.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "1RM ${m.e1rm.kg()}" + (ch?.let { " · ${if (it >= 0f) "+" else "−"}%${abs(it).roundToInt()}" } ?: ""),
                style = MaterialTheme.typography.labelMedium.mono(), color = c
            )
        }
        if (m.points.size >= 2) {
            val mn = m.points.min(); val mx = m.points.max(); val sp = (mx - mn).coerceAtLeast(1f)
            Canvas(Modifier.width(72.dp).height(30.dp)) {
                val path = Path()
                m.points.forEachIndexed { i, v ->
                    val x = i / (m.points.size - 1f) * size.width
                    val y = size.height - (v - mn) / sp * size.height
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

@Suppress("unused")
private fun statusOf(k: String, v: Float): LoadStatus = MuscleLoad(k, v, 0f, -1, MuscleMap.weeklyTarget(k)).status


/* ------------------------------ Güç seviyesi (2.29) ------------------------------ */

/**
 * Tüm ana hareketler tek kartta: her satırda 6 eşit dilimli seviye merdiveni
 * (Başlangıç → Elit), bulunduğun nokta ve bir sonraki seviyeye kalan kilo.
 */
@Composable
internal fun StrengthLevelsCard(profile: List<LiftStandard>, bodyWeight: Float, onOpen: (Long) -> Unit) {
    val levels = LiftStandard.LEVELS
    val overallPos = profile.map { it.position }.average().toFloat()
    val overallIdx = overallPos.toInt().coerceIn(0, levels.lastIndex)
    val oc = levelColor(overallIdx)
    FitCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Güç seviyesi", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    "Vücut ağırlığına göre" + if (bodyWeight > 0f) " · ${bodyWeight.tr()} kg" else "",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Genel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
                Text(
                    levels[overallIdx], style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = oc,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(oc.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        // Seviye başlıkları
        Row(Modifier.fillMaxWidth()) {
            levels.forEachIndexed { i, l ->
                Text(
                    l.replace("Çok İleri", "Ç. İleri").replace("Başlangıç", "Başl."),
                    style = MaterialTheme.typography.labelSmall, color = levelColor(i),
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        profile.forEachIndexed { n, lift ->
            if (n > 0) Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)))
            else Spacer(Modifier.height(6.dp))
            LevelLadderRow(lift, onOpen)
        }
    }
}

@Composable
private fun LevelLadderRow(lift: LiftStandard, onOpen: (Long) -> Unit) {
    val c = levelColor(lift.levelIndex)
    val pos = lift.position
    val segs = LiftStandard.LEVELS.size
    val segColors = (0 until segs).map { levelColor(it) }
    val empty = MaterialTheme.fit.elevated
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(lift.exerciseId) }) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(lift.displayName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(lift.e1rm.kg(), style = MaterialTheme.typography.titleSmall.mono().copy(fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.width(6.dp))
            Text("${lift.bodyweightRatio.tr()}×", style = MaterialTheme.typography.labelMedium.mono(), color = MaterialTheme.fit.muted)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val gap = 3.dp.toPx()
            val h = 10.dp.toPx()
            val top = (size.height - h) / 2f
            val segW = (size.width - gap * (segs - 1)) / segs
            for (i in 0 until segs) {
                val x = i * (segW + gap)
                val r = androidx.compose.ui.geometry.CornerRadius(h / 2f)
                drawRoundRect(empty, Offset(x, top), androidx.compose.ui.geometry.Size(segW, h), r)
                val fill = when {
                    pos >= i + 1 -> 1f
                    pos > i -> pos - i
                    else -> 0f
                }
                if (fill > 0f) drawRoundRect(segColors[i].copy(alpha = if (i == lift.levelIndex) 1f else 0.55f), Offset(x, top),
                    androidx.compose.ui.geometry.Size(maxOf(segW * fill, h), h), r)
            }
            // İşaretçi
            val seg = pos.toInt().coerceIn(0, segs - 1)
            val mx = seg * (segW + gap) + segW * (pos - seg).coerceIn(0f, 1f)
            drawCircle(onSurface, 7.dp.toPx(), Offset(mx, size.height / 2f))
            drawCircle(c, 4.5.dp.toPx(), Offset(mx, size.height / 2f))
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(lift.level, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = c)
            if (lift.bestReps > 0) Text("  · ${lift.bestWeight.tr()}×${lift.bestReps}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
            Spacer(Modifier.weight(1f))
            Text(
                if (lift.nextLevel != null && lift.nextLevelWeight > lift.e1rm)
                    "${lift.nextLevel}: +${(lift.nextLevelWeight - lift.e1rm).tr()} kg"
                else "En üst seviye",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted
            )
        }
    }
}
