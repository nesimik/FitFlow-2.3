package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.Advice
import com.example.core.AdviceAction
import com.example.core.AdviceKind
import com.example.core.AdviceSeverity
import com.example.core.BalanceRatio
import com.example.core.ProgramAdvisor
import com.example.core.ProgramReview
import com.example.core.trimNum
import com.example.ui.AppViewModel
import com.example.ui.components.EmptyState
import com.example.ui.components.FitCard
import com.example.ui.components.OverlineText
import com.example.ui.theme.fit
import com.example.ui.theme.mono
import kotlin.math.ln

/* ==========================================================================
 * Akıllı öneriler (2.25)
 *
 * Program puanı → denge oranları → öncelik sırasına göre uygulanabilir öneriler.
 * Her öneri tek dokunuşla programa işlenir (set artır / azalt, hareket ekle).
 * ========================================================================== */

@Composable
fun ProgramAdvisorScreen(vm: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val routine by vm.activeRoutine.collectAsStateWithLifecycle()
    val days by vm.routineDays.collectAsStateWithLifecycle()
    val allItems by vm.allItems.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val allSets by vm.allSets.collectAsStateWithLifecycle()

    val doneBefore = remember(allSets) { allSets.map { it.exerciseId }.toSet() }
    val review: ProgramReview = remember(days, allItems, exercises, doneBefore) {
        ProgramAdvisor.review(days, allItems, exercises, doneBefore)
    }
    val hidden = remember { mutableStateListOf<String>() }
    var showAll by remember { mutableStateOf(false) }
    val visible = review.advice.filter { it.id !in hidden }
    val shown = if (showAll) visible else visible.take(4)
    val hasProgram = routine != null && allItems.any { it.dayId in days.map { d -> d.id } }

    fun apply(a: Advice) {
        when (val act = a.action) {
            is AdviceAction.SetSets -> {
                val cur = allItems.firstOrNull { it.id == act.item.id } ?: act.item
                vm.updateItem(cur.copy(targetSets = act.newSets))
                Toast.makeText(context, "Set sayısı ${act.newSets} olarak güncellendi", Toast.LENGTH_SHORT).show()
            }
            is AdviceAction.AddExercise -> {
                vm.addItemWithSets(act.dayId, act.exercise.id, act.sets)
                val dn = days.firstOrNull { it.id == act.dayId }?.name ?: ""
                Toast.makeText(context, "${act.exercise.name} · $dn gününe eklendi", Toast.LENGTH_SHORT).show()
            }
            null -> {}
        }
        hidden += a.id
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            ScreenHeader("Akıllı öneriler", routine?.name ?: "Program", onBack = onDismiss)

            if (!hasProgram) {
                EmptyState(
                    icon = Icons.Default.AutoAwesome,
                    title = "İncelenecek program yok",
                    text = "Aktif programına gün ve hareket ekle; hacim, denge ve sıklık analizi burada görünecek."
                )
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "score") { ScoreCard(review, visible.size) }
                item(key = "balance") { BalanceCard(review.balances) }

                item(key = "head") {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OverlineText("Öneriler · öncelik sırasıyla", modifier = Modifier.weight(1f))
                        if (hidden.isNotEmpty()) {
                            Text(
                                "Gizlenenleri göster", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.accent,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { hidden.clear() }.padding(6.dp)
                            )
                        }
                    }
                }

                if (visible.isEmpty()) {
                    item(key = "empty") {
                        FitCard(
                            container = MaterialTheme.fit.success.copy(alpha = 0.08f),
                            border = MaterialTheme.fit.success.copy(alpha = 0.3f)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.fit.success, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Yapılacak bir şey yok", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.fit.success)
                                    Text(
                                        "Program hacim, denge ve sıklık açısından sağlam görünüyor.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted
                                    )
                                }
                            }
                        }
                    }
                }

                items(shown, key = { it.id }) { a ->
                    AdviceCard(a, onApply = { apply(a) }, onHide = { hidden += a.id })
                }

                if (visible.size > 4) {
                    item(key = "more") {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .border(1.dp, MaterialTheme.fit.cardBorder, RoundedCornerShape(14.dp))
                                .clickable { showAll = !showAll }.padding(12.dp),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (showAll) "Daha az göster" else "Tümünü göster (${visible.size - 4} öneri daha)",
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.fit.accent
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(if (showAll) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.fit.accent, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                item(key = "note") {
                    Text(
                        "Hesap: bir hareketin birincil kası 1 set, destek kasları katkı oranı kadar sayılır (ör. bench press → göğüs 1, arka kol 0,5). " +
                            "Önerilen aralıklar orta seviye bir sporcu için haftalık etkin settir.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp)
                    )
                }
            }
        }
    }
}

/* ------------------------------ Puan kartı ------------------------------ */

@Composable
private fun scoreColor(score: Int): Color = when {
    score >= 80 -> MaterialTheme.fit.success
    score >= 60 -> MaterialTheme.fit.accent
    score >= 45 -> MaterialTheme.fit.warning
    else -> MaterialTheme.fit.danger
}

@Composable
private fun ScoreCard(r: ProgramReview, adviceCount: Int) {
    val color = scoreColor(r.score)
    val anim by animateFloatAsState(r.score / 100f, tween(700), label = "score")
    val track = MaterialTheme.fit.elevated
    FitCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = 9.dp.toPx()
                    val inset = sw / 2
                    val sz = Size(size.width - sw, size.height - sw)
                    drawArc(track, 135f, 270f, false, Offset(inset, inset), sz, style = Stroke(sw, cap = StrokeCap.Round))
                    drawArc(color, 135f, 270f * anim, false, Offset(inset, inset), sz, style = Stroke(sw, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${r.score}", style = MaterialTheme.typography.headlineMedium.mono().copy(fontWeight = FontWeight.SemiBold), color = color)
                    Text("/ 100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                OverlineText("Program puanı")
                Spacer(Modifier.height(2.dp))
                Text(r.verdict, style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("${r.inRange}/${r.totalMuscles}", "kas hedefte", Modifier.weight(1f))
            MiniStat("${r.weeklySets}", "set / hafta", Modifier.weight(1f))
            MiniStat("$adviceCount", "öneri", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.fit.elevated.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium.mono().copy(fontWeight = FontWeight.SemiBold))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted, maxLines = 1)
    }
}

/* ------------------------------ Denge kartı ------------------------------ */

@Composable
private fun BalanceCard(list: List<BalanceRatio>) {
    FitCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Balance, null, tint = MaterialTheme.fit.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Kas dengesi", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${list.count { it.ok }}/${list.size} dengede", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.muted)
        }
        list.forEach { b ->
            Spacer(Modifier.height(14.dp))
            BalanceRow(b)
        }
    }
}

@Composable
private fun BalanceRow(b: BalanceRatio) {
    val empty = b.leftSets + b.rightSets <= 0f
    val color = when {
        empty -> MaterialTheme.fit.muted
        b.ok -> MaterialTheme.fit.success
        else -> MaterialTheme.fit.warning
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(b.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            if (empty) "—" else "${b.ratio.coerceAtMost(9f).trimNum().replace('.', ',')} : 1",
            style = MaterialTheme.typography.labelLarge.mono(), color = color
        )
    }
    Spacer(Modifier.height(6.dp))
    // Logaritmik eksen: 1:4 … 4:1, ortada 1:1. Yeşil bant ideal aralık.
    val track = MaterialTheme.fit.elevated
    val band = MaterialTheme.fit.success.copy(alpha = 0.22f)
    val mid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    Canvas(Modifier.fillMaxWidth().height(12.dp)) {
        val maxL = ln(4f)
        fun x(r: Float) = ((ln(r.coerceIn(0.25f, 4f)) / maxL + 1f) / 2f) * size.width
        val h = 8.dp.toPx(); val y = (size.height - h) / 2
        val cr = CornerRadius(h / 2)
        drawRoundRect(track, Offset(0f, y), Size(size.width, h), cr)
        val x0 = x(b.idealLo); val x1 = x(b.idealHi)
        drawRoundRect(band, Offset(x0, y), Size(x1 - x0, h), cr)
        drawLine(mid, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.dp.toPx())
        if (!empty) {
            drawCircle(color, 6.dp.toPx(), Offset(x(b.ratio), size.height / 2))
            drawCircle(Color.White.copy(alpha = 0.9f), 2.5.dp.toPx(), Offset(x(b.ratio), size.height / 2))
        }
    }
    Spacer(Modifier.height(4.dp))
    Row {
        Text("${b.left} ${b.leftSets.trimNum().replace('.', ',')}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted, modifier = Modifier.weight(1f))
        Text("${b.right} ${b.rightSets.trimNum().replace('.', ',')}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
    }
}

/* ------------------------------ Öneri kartı ------------------------------ */

private fun AdviceKind.icon(): ImageVector = when (this) {
    AdviceKind.ADD_SETS -> Icons.Default.Add
    AdviceKind.ADD_EXERCISE -> Icons.Default.FitnessCenter
    AdviceKind.REDUCE -> Icons.Default.Remove
    AdviceKind.BALANCE -> Icons.Default.Balance
    AdviceKind.FREQUENCY -> Icons.Default.CalendarMonth
    AdviceKind.DAY_LOAD -> Icons.Default.Timer
}

@Composable
private fun AdviceCard(a: Advice, onApply: () -> Unit, onHide: () -> Unit) {
    val sevColor = when (a.severity) {
        AdviceSeverity.HIGH -> MaterialTheme.fit.danger
        AdviceSeverity.MEDIUM -> MaterialTheme.fit.warning
        AdviceSeverity.LOW -> MaterialTheme.fit.accent
    }
    val sevLabel = when (a.severity) {
        AdviceSeverity.HIGH -> "Yüksek öncelik"
        AdviceSeverity.MEDIUM -> "Orta öncelik"
        AdviceSeverity.LOW -> "Düşük öncelik"
    }
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.fit.cardBorder, RoundedCornerShape(20.dp))
            .animateContentSize()
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(sevColor))
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(sevColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) { Icon(a.kind.icon(), null, tint = sevColor, modifier = Modifier.size(16.dp)) }
                Spacer(Modifier.width(10.dp))
                Text(a.kind.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text(sevLabel, style = MaterialTheme.typography.labelSmall, color = sevColor)
            }
            Spacer(Modifier.height(10.dp))
            Text(a.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(4.dp))
            Text(a.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
            a.impact?.let {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.fit.elevated.copy(alpha = 0.7f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(it, style = MaterialTheme.typography.labelMedium.mono(), color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (a.action != null) {
                    Row(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.fit.accent)
                            .clickable { onApply() }.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, null, tint = MaterialTheme.fit.onAccent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Uygula", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.fit.onAccent)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.fit.muted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Programda elle düzenle", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.muted)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Gizle", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.fit.muted,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onHide() }.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
    }
}
