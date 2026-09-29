package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.LoadStatus
import com.example.core.MuscleMap
import com.example.core.label
import com.example.core.trimNum
import com.example.ui.AppViewModel
import com.example.ui.components.BodyMuscleMap
import com.example.ui.components.BodyView
import com.example.ui.components.ChoiceChip
import com.example.ui.components.FitCard
import com.example.ui.components.MuscleColors
import com.example.ui.components.OverlineText
import com.example.ui.components.RoundIconButton
import com.example.ui.theme.Palette
import com.example.ui.theme.fit
import com.example.ui.theme.mono

/* ==========================================================================
 * Program kas haritası (2.22)
 *
 * Planlanan programın hangi kası ne kadar çalıştırdığı: haftalık etkin set (birincil 1.0,
 * destek ~0.5, tanımlı katkı oranlarıyla) ve önerilen aralığa göre durum. Tam program ya da
 * tek gün; haftalık hacim ya da birincil/destek görünümü; kasa dokununca katkı veren hareketler.
 * ========================================================================== */

private data class Contribution(val exercise: String, val day: String, val sets: Int, val share: Float) {
    val effective: Float get() = sets * share
}

@Composable
fun ProgramMuscleMapScreen(vm: AppViewModel, onDismiss: () -> Unit) {
    val routine by vm.activeRoutine.collectAsStateWithLifecycle()
    val days by vm.routineDays.collectAsStateWithLifecycle()
    val allItems by vm.allItems.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()

    var dayId by remember { mutableStateOf<Long?>(null) }   // null = tüm program
    var mode by remember { mutableStateOf(0) }              // 0 = haftalık hacim, 1 = birincil/destek
    var selected by remember { mutableStateOf<String?>(null) }
    var big by remember { mutableStateOf(false) }
    var bigView by remember { mutableStateOf(BodyView.FRONT) }

    val exMap = remember(exercises) { exercises.associateBy { it.id } }
    val dayName = remember(days) { days.associate { it.id to it.name } }

    // Kas → katkılar
    val contributions: Map<String, List<Contribution>> = remember(dayId, days, allItems, exMap) {
        val ids = if (dayId == null) days.map { it.id }.toSet() else setOf(dayId!!)
        val acc = HashMap<String, MutableList<Contribution>>()
        allItems.filter { it.dayId in ids && !it.isWarmup }.forEach { item ->
            val ex = exMap[item.exerciseId] ?: return@forEach
            val name = item.customName.ifBlank { ex.name }
            MuscleMap.resolve(name, ex.muscleGroup, ex.secondaryMuscles).weights().forEach { (m, f) ->
                acc.getOrPut(m) { mutableListOf() } += Contribution(name, dayName[item.dayId] ?: "", item.targetSets, f)
            }
        }
        acc.mapValues { (_, l) -> l.sortedByDescending { it.effective } }
    }
    val sets: Map<String, Float> = remember(contributions) {
        contributions.mapValues { (_, l) -> l.sumOf { it.effective.toDouble() }.toFloat() }
    }
    val primaryOf: Map<String, Boolean> = remember(contributions) {
        contributions.mapValues { (_, l) -> l.any { it.share >= 0.75f } }
    }
    val weekly = dayId == null

    fun statusOf(k: String): LoadStatus {
        val v = sets[k] ?: 0f
        val t = MuscleMap.weeklyTarget(k)
        return com.example.core.MuscleLoad(k, v, 0f, -1, t).status
    }

    val accent = MaterialTheme.fit.accent
    val colors: Map<String, Color> = remember(sets, mode, weekly, accent) {
        if (mode == 1) {
            sets.filter { it.value > 0f }.mapValues { (k, _) -> if (primaryOf[k] == true) accent else Palette.warning }
        } else if (weekly) {
            MuscleMap.all.mapNotNull { k -> MuscleColors.forStatus(statusOf(k))?.let { k to it } }.toMap()
        } else {
            // Tek gün: o günün set yoğunluğu (hedef haftalık olduğu için durum değil yoğunluk)
            sets.filter { it.value > 0f }.mapValues { (_, v) -> accent.copy(alpha = (0.35f + v / 8f).coerceAtMost(1f)) }
        }
    }

    val ranked = remember(sets, weekly) {
        MuscleMap.all.map { k -> k to (sets[k] ?: 0f) }
            .sortedWith(
                if (weekly) compareBy<Pair<String, Float>> { (k, v) -> v / MuscleMap.weeklyTarget(k).first.coerceAtLeast(1) }
                else compareByDescending { it.second }
            )
    }
    val inRange = if (weekly) MuscleMap.all.count { statusOf(it) == LoadStatus.OPTIMAL } else 0
    val under = if (weekly) MuscleMap.all.count { statusOf(it) in listOf(LoadStatus.NONE, LoadStatus.LOW, LoadStatus.BELOW) } else 0
    val over = if (weekly) MuscleMap.all.count { statusOf(it) in listOf(LoadStatus.HIGH, LoadStatus.EXCESSIVE) } else 0
    val totalSets = remember(dayId, days, allItems) {
        val ids = if (dayId == null) days.map { it.id }.toSet() else setOf(dayId!!)
        allItems.filter { it.dayId in ids && !it.isWarmup }.sumOf { it.targetSets }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            ScreenHeader("Kas haritası", routine?.name ?: "Program", onBack = onDismiss) {
                RoundIconButton(
                    if (big) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                    MaterialTheme.fit.accent, 40.dp
                ) { big = !big }
            }

            /* Kapsam seçimi */
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChoiceChip("Tüm program", dayId == null, { dayId = null; selected = null })
                days.forEach { d -> ChoiceChip(d.name, dayId == d.id, { dayId = d.id; selected = null }) }
            }
            Spacer(Modifier.height(10.dp))

            if (big) {
                /* Büyük tek görünüm (2.25): kaydırılabilir liste. Harita sabit yükseklikte, detay altında;
                 * kasa dokununca liste detaya kayar. Dialog'da gezinme çubuğu boşluğu bildirilmediği için
                 * altta geniş boşluk bırakılır. */
                val bigState = rememberLazyListState()
                val mapH = (LocalConfiguration.current.screenHeightDp * 0.66f).dp
                LaunchedEffect(selected) {
                    if (selected != null) bigState.animateScrollToItem(1)
                }
                LazyColumn(
                    state = bigState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "bigmap") {
                        Box(
                            Modifier.fillMaxWidth().height(mapH)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.10f), Color.Transparent)))
                        ) {
                            BodyMuscleMap(
                                view = bigView,
                                colors = colors,
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                selected = selected,
                                onMuscleTap = { selected = if (selected == it) null else it }
                            )
                            selected?.let { k ->
                                SelectedPill(k, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) { selected = null }
                            }
                            Row(
                                Modifier.align(Alignment.TopCenter).padding(top = 10.dp).clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface).padding(3.dp)
                            ) {
                                listOf(BodyView.FRONT to "Ön", BodyView.BACK to "Arka").forEach { (v, l) ->
                                    Text(
                                        l, style = MaterialTheme.typography.labelLarge,
                                        color = if (bigView == v) MaterialTheme.colorScheme.onSurface else MaterialTheme.fit.muted,
                                        modifier = Modifier.clip(RoundedCornerShape(9.dp))
                                            .background(if (bigView == v) MaterialTheme.fit.elevated else Color.Transparent)
                                            .clickable { bigView = v }.padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                    item(key = "bigdetail") {
                        val k = selected
                        if (k != null) {
                            MuscleDetail(k, sets[k] ?: 0f, weekly, if (weekly) statusOf(k) else null, contributions[k].orEmpty(),
                                compact = false, onClose = { selected = null })
                        } else {
                            Text(
                                "Ayrıntı için bir kasa dokun",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    /* Özet */
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (weekly) {
                                SummaryTile("$inRange", "hedefte", MuscleColors.optimal, Modifier.weight(1f))
                                SummaryTile("$under", "eksik", MuscleColors.below, Modifier.weight(1f))
                                SummaryTile("$over", "fazla", MuscleColors.high, Modifier.weight(1f))
                            }
                            SummaryTile("$totalSets", if (weekly) "set / hafta" else "set", MaterialTheme.fit.accent, Modifier.weight(1f))
                        }
                    }
                    /* Harita */
                    item {
                        FitCard(contentPadding = PaddingValues(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Row(
                                    Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.background).padding(3.dp)
                                ) {
                                    listOf("Haftalık hacim", "Birincil / destek").forEachIndexed { i, l ->
                                        Text(
                                            if (i == 0 && !weekly) "Set yoğunluğu" else l,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (mode == i) MaterialTheme.colorScheme.onSurface else MaterialTheme.fit.muted,
                                            modifier = Modifier.clip(RoundedCornerShape(9.dp))
                                                .background(if (mode == i) MaterialTheme.fit.elevated else Color.Transparent)
                                                .clickable { mode = i }.padding(horizontal = 11.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                    .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.08f), Color.Transparent)))
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(BodyView.FRONT, BodyView.BACK).forEach { v ->
                                        BodyMuscleMap(
                                            view = v,
                                            colors = colors,
                                            modifier = Modifier.weight(1f).height(340.dp),
                                            selected = selected,
                                            onMuscleTap = { selected = if (selected == it) null else it }
                                        )
                                    }
                                }
                                selected?.let { k ->
                                    SelectedPill(k, Modifier.align(Alignment.TopEnd).padding(6.dp)) { selected = null }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Legend(mode, weekly)
                        }
                    }
                    /* Seçili kas */
                    selected?.let { k ->
                        item {
                            MuscleDetail(k, sets[k] ?: 0f, weekly, if (weekly) statusOf(k) else null, contributions[k].orEmpty(),
                                compact = false, onClose = { selected = null })
                        }
                    }
                    /* Sıralı liste */
                    item {
                        OverlineText(if (weekly) "Kaslar · en eksikten" else "Bu günde çalışan kaslar")
                    }
                    items(ranked.filter { weekly || it.second > 0f }, key = { it.first }) { (k, v) ->
                        MuscleRow(k, v, weekly, if (weekly) statusOf(k) else null, selected == k) {
                            selected = if (selected == k) null else k
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryTile(value: String, label: String, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge.mono().copy(fontWeight = FontWeight.SemiBold), color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted, maxLines = 1)
    }
}

@Composable
private fun Legend(mode: Int, weekly: Boolean) {
    val items: List<Pair<Color, String>> = when {
        mode == 1 -> listOf(MaterialTheme.fit.accent to "Birincil", Palette.warning to "Destek")
        weekly -> MuscleColors.loadLegend
        else -> listOf(MaterialTheme.fit.accent.copy(alpha = 0.4f) to "Az set", MaterialTheme.fit.accent to "Çok set")
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
        items.forEach { (c, l) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                Spacer(Modifier.width(4.dp))
                Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
            }
        }
    }
}

@Composable
private fun MuscleRow(key: String, value: Float, weekly: Boolean, status: LoadStatus?, selected: Boolean, onClick: () -> Unit) {
    val target = MuscleMap.weeklyTarget(key)
    val color = status?.let { MuscleColors.forStatus(it) } ?: if (value > 0f) MaterialTheme.fit.accent else MaterialTheme.fit.muted
    val scale = if (weekly) maxOf(target.last * 1.4f, value, 1f) else maxOf(value, 8f)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (selected) color.copy(alpha = 0.10f) else Color.Transparent)
            .clickable { onClick() }.padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(MuscleMap.label(key), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
            if (status != null) Text(status.label(), style = MaterialTheme.typography.labelSmall, color = color)
            Spacer(Modifier.width(8.dp))
            Text(value.trimNum().replace('.', ','), style = MaterialTheme.typography.labelLarge.mono(), color = MaterialTheme.colorScheme.onSurface)
            if (weekly) Text(" / ${target.first}–${target.last}", style = MaterialTheme.typography.labelSmall.mono(), color = MaterialTheme.fit.muted)
        }
        Spacer(Modifier.height(6.dp))
        val track = MaterialTheme.fit.elevated
        val band = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = r)
            if (weekly) {
                val x0 = target.first / scale * size.width
                val x1 = target.last / scale * size.width
                drawRoundRect(band, topLeft = androidx.compose.ui.geometry.Offset(x0, 0f),
                    size = androidx.compose.ui.geometry.Size(x1 - x0, size.height), cornerRadius = r)
            }
            val w = (value / scale).coerceIn(0f, 1f) * size.width
            if (w > 0f) drawRoundRect(color, size = androidx.compose.ui.geometry.Size(maxOf(w, size.height), size.height), cornerRadius = r)
        }
    }
}

@Composable
private fun MuscleDetail(
    key: String, value: Float, weekly: Boolean, status: LoadStatus?, list: List<Contribution>, compact: Boolean,
    onClose: () -> Unit
) {
    val target = MuscleMap.weeklyTarget(key)
    val color = status?.let { MuscleColors.forStatus(it) } ?: MaterialTheme.fit.accent
    FitCard(border = color.copy(alpha = 0.45f), contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(MuscleMap.label(key), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${value.trimNum().replace('.', ',')} set", style = MaterialTheme.typography.titleSmall.mono(), color = color)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.fit.elevated).clickable { onClose() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Close, "Kapat", tint = MaterialTheme.fit.muted, modifier = Modifier.size(16.dp)) }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                !weekly -> "Bu günün etkin seti"
                status == LoadStatus.NONE -> "Programda bu kası çalıştıran hareket yok. Önerilen: haftada ${target.first}–${target.last} etkin set."
                status == LoadStatus.LOW || status == LoadStatus.BELOW -> "Önerilen ${target.first}–${target.last}. Yaklaşık ${(target.first - value).coerceAtLeast(1f).trimNum()} set eksik."
                status == LoadStatus.OPTIMAL -> "Önerilen aralıkta (${target.first}–${target.last})."
                else -> "Önerilen ${target.first}–${target.last}; üst sınırın üzerinde. Toparlanmayı izle."
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted
        )
        if (list.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            (if (compact) list.take(3) else list).forEach { c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(width = 3.dp, height = 16.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (c.share >= 0.75f) MaterialTheme.fit.accent else Palette.warning)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.exercise, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${c.day} · ${c.sets} set · " + if (c.share >= 0.75f) "birincil" else "destek %${(c.share * 100).toInt()}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted
                        )
                    }
                    Text("+${c.effective.trimNum().replace('.', ',')}", style = MaterialTheme.typography.labelLarge.mono(), color = MaterialTheme.fit.muted)
                }
            }
            if (compact && list.size > 3) {
                Text("+${list.size - 3} hareket daha", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
            }
        }
    }
}


/** Harita üstünde seçili kasın adı ve kapatma düğmesi. */
@Composable
private fun SelectedPill(key: String, modifier: Modifier, onClear: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.fit.accent.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .clickable { onClear() }.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(MuscleMap.label(key), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.fit.accent)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Default.Close, "Seçimi kaldır", tint = MaterialTheme.fit.muted, modifier = Modifier.size(16.dp))
    }
}
