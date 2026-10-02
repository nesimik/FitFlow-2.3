package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.core.Calc
import com.example.core.PlateSolver
import com.example.core.formatDateShort
import com.example.core.kg
import com.example.core.trimNum
import com.example.data.WorkoutSetEntity
import com.example.ui.AppViewModel
import com.example.ui.components.ChoiceChip
import com.example.ui.components.FitCard
import com.example.ui.components.FitTextField
import com.example.ui.components.OverlineText
import com.example.ui.theme.Palette
import com.example.ui.theme.fit
import com.example.ui.theme.mono
import kotlin.math.roundToInt

@Composable
fun ToolsScreen(vm: AppViewModel, nav: NavHostController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Her aracın rememberSaveable durumu sekme değişince korunur (SaveableStateProvider).
    val stateHolder = rememberSaveableStateHolder()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Hesaplayıcılar", "Antrenman planlaman için pratik araçlar", onBack = { nav.popBackStack() })
        Column(Modifier.padding(horizontal = 16.dp)) {
            TkSegmented(listOf("1RM", "Plaka", "Isınma", "Vücut"), tab) { tab = it }
        }
        Spacer(Modifier.height(14.dp))
        stateHolder.SaveableStateProvider("tool_$tab") {
            when (tab) {
                0 -> OneRmTool(vm)
                1 -> PlateTool(vm)
                2 -> WarmupTool(vm)
                else -> BodyTool(vm)
            }
        }
    }
}

private val toolListPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)

/* ---------------------------------- 1RM ------------------------------------ */

/** Bir hareketin en iyi tahmini 1RM'i veren efektif seti. */
private data class LiftBest(
    val exerciseId: Long,
    val name: String,
    val weightKg: Float,
    val reps: Int,
    val e1rm: Float,
    val performedAt: Long,
    val lastAt: Long
)

private fun bestLifts(sets: List<WorkoutSetEntity>): List<LiftBest> =
    sets.asSequence()
        .filter { it.isCompleted && !it.isWarmup && it.weightKg > 0f && it.reps in 1..12 }
        .groupBy { it.exerciseId }
        .mapNotNull { (id, list) ->
            val best = list.maxByOrNull { Calc.e1rm(it.weightKg, it.reps) } ?: return@mapNotNull null
            val newest = list.maxByOrNull { it.performedAt } ?: best
            LiftBest(
                exerciseId = id,
                name = newest.exerciseName,
                weightKg = best.weightKg,
                reps = best.reps,
                e1rm = Calc.e1rm(best.weightKg, best.reps),
                performedAt = best.performedAt,
                lastAt = newest.performedAt
            )
        }
        .sortedByDescending { it.lastAt }

@Composable
private fun OneRmTool(vm: AppViewModel) {
    val allSets by vm.allSets.collectAsStateWithLifecycle()
    val lifts = remember(allSets) { bestLifts(allSets) }

    var weight by rememberSaveable { mutableStateOf("60") }
    var reps by rememberSaveable { mutableStateOf("8") }
    var selectedEx by rememberSaveable { mutableStateOf(-1L) }

    val w = weight.replace(',', '.').toFloatOrNull() ?: 0f
    val r = reps.toIntOrNull() ?: 0
    val e1rm = Calc.e1rm(w, r)
    val selectedLift = lifts.firstOrNull { it.exerciseId == selectedEx }

    LazyColumn(
        contentPadding = toolListPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            FitCard {
                TkCardTitle("1RM hesaplayıcı", "Bir setin ağırlık ve tekrarından tek tekrarlık maksimumunu tahmin eder.")
                Spacer(Modifier.height(12.dp))
                if (lifts.isNotEmpty()) {
                    Text("Hareketlerinden doldur", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.muted)
                    Spacer(Modifier.height(7.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        lifts.take(20).forEach { l ->
                            ChoiceChip(l.name, selectedEx == l.exerciseId, {
                                selectedEx = l.exerciseId
                                weight = l.weightKg.trimNum()
                                reps = l.reps.toString()
                            })
                        }
                    }
                    if (selectedLift != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "En iyi set: ${selectedLift.weightKg.trimNum()} kg × ${selectedLift.reps} · ${formatDateShort(selectedLift.performedAt)}",
                            style = MaterialTheme.typography.labelMedium.mono(),
                            color = MaterialTheme.fit.muted
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FitTextField(weight, { weight = it }, "Ağırlık (kg)", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                    FitTextField(reps, { reps = it.filter { c -> c.isDigit() } }, "Tekrar", Modifier.weight(1f), keyboardType = KeyboardType.Number)
                }
            }
        }
        item {
            FitCard {
                OverlineText("Tahmini 1 tekrar maksimum", MaterialTheme.fit.accent)
                Spacer(Modifier.height(6.dp))
                if (e1rm > 0f) {
                    TkHero(e1rm.trimNum(), "kg", MaterialTheme.fit.accent, "4 formülün ortalaması · ${w.trimNum()} kg × $r")
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TkStat("Epley", Calc.epley(w, r).kg(), Modifier.weight(1f))
                        TkStat("Brzycki", Calc.brzycki(w, r).kg(), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TkStat("Lombardi", Calc.lombardi(w, r).kg(), Modifier.weight(1f))
                        TkStat("O'Conner", Calc.oconner(w, r).kg(), Modifier.weight(1f))
                    }
                } else {
                    TkHero("—", color = MaterialTheme.fit.muted, caption = "Ağırlık ve tekrar gir")
                }
            }
        }
        if (e1rm > 0f) {
            item {
                FitCard {
                    TkCardTitle(
                        "Yüzde tablosu",
                        "Antrenman ağırlığını planlarken kullan. Hipertrofi genelde %65-80, güç %85+ aralığında çalışılır."
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("%1RM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted, modifier = Modifier.weight(0.8f))
                        Text("Ağırlık", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                        Text("Tekrar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                    }
                    Calc.percentTable(e1rm).forEachIndexed { i, (pct, value) ->
                        val repsLabel = when {
                            pct >= 95 -> "1-2"
                            pct >= 90 -> "3-4"
                            pct >= 85 -> "5-6"
                            pct >= 80 -> "7-8"
                            pct >= 75 -> "9-10"
                            pct >= 70 -> "11-12"
                            else -> "13+"
                        }
                        val zoneColor = when {
                            pct >= 85 -> MaterialTheme.fit.danger
                            pct >= 65 -> MaterialTheme.fit.accent
                            else -> MaterialTheme.fit.success
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (i % 2 == 0) MaterialTheme.fit.elevated.copy(alpha = 0.45f) else Color.Transparent)
                                .padding(horizontal = 6.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(Modifier.weight(0.8f), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(3.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(zoneColor))
                                Spacer(Modifier.width(8.dp))
                                Text("%$pct", style = MaterialTheme.typography.bodyMedium.mono().copy(fontWeight = FontWeight.SemiBold), color = zoneColor)
                            }
                            Text(
                                value.kg(),
                                style = MaterialTheme.typography.bodyMedium.mono().copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.weight(1.2f),
                                textAlign = TextAlign.End
                            )
                            Text(
                                repsLabel,
                                style = MaterialTheme.typography.labelMedium.mono(),
                                color = MaterialTheme.fit.muted,
                                modifier = Modifier.weight(1.2f),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ZoneLegend(MaterialTheme.fit.danger, "güç")
                        ZoneLegend(MaterialTheme.fit.accent, "hipertrofi")
                        ZoneLegend(MaterialTheme.fit.success, "dayanıklılık / ısınma")
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
    }
}

/* --------------------------------- Plaka ----------------------------------- */

@Composable
private fun PlateTool(vm: AppViewModel) {
    val barDefault by vm.settings.barWeight.collectAsStateWithLifecycle()
    val userPlates by vm.settings.plates.collectAsStateWithLifecycle()
    var target by rememberSaveable { mutableStateOf("60") }
    var bar by rememberSaveable { mutableStateOf(barDefault) }

    val total = target.replace(',', '.').toFloatOrNull() ?: 0f
    val result = remember(total, bar, userPlates) { PlateSolver.solve(total, bar, userPlates) }
    val barOptions = remember(barDefault) { (listOf(10f, 15f, 20f, 25f) + barDefault).filter { it > 0f }.distinct().sorted() }
    val shown = result.best

    LazyColumn(
        contentPadding = toolListPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            FitCard {
                TkCardTitle("Plaka hesaplayıcı", "Hedef ağırlık için bara her iki yana takılacak plakaları bulur.")
                Spacer(Modifier.height(12.dp))
                FitTextField(target, { target = it }, "Hedef toplam ağırlık (kg)", keyboardType = KeyboardType.Decimal)
                Spacer(Modifier.height(12.dp))
                Text("Bar ağırlığı", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.muted)
                Spacer(Modifier.height(7.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    barOptions.forEach { v ->
                        ChoiceChip("${v.trimNum()} kg", bar == v, { bar = v })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Plakaların · Ayarlar › Ekipman", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.muted)
                Spacer(Modifier.height(6.dp))
                PlateChips(userPlates.filter { it > 0f }.distinct().sortedDescending())
            }
        }
        item {
            FitCard {
                OverlineText("Her iki tarafa takılacak")
                Spacer(Modifier.height(8.dp))
                when {
                    total <= 0f -> Text(
                        "Hedef ağırlığı gir.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.fit.muted
                    )
                    result.belowBar -> Text(
                        "Hedef, bar ağırlığından (${bar.trimNum()} kg) düşük. Bu barla en az ${bar.trimNum()} kg kurulabilir.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.fit.warning
                    )
                    else -> {
                        val exact = result.exact != null
                        if (shown != null) {
                            TkHero(
                                shown.totalKg.trimNum(), "kg",
                                if (exact) MaterialTheme.fit.accent else MaterialTheme.fit.warning,
                                if (exact) "Ulaşılan ağırlık · tam kurulum" else "Ulaşılan ağırlık · hedef ${total.trimNum()} kg"
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        if (!exact) {
                            val options = listOfNotNull(result.below?.totalKg, result.above?.totalKg).distinct()
                            Text(
                                "Tam kurulamaz — en yakın: " + options.joinToString(" / ") { "${it.trimNum()} kg" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.fit.warning,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.fit.warning.copy(alpha = 0.08f))
                                    .padding(10.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        if (shown == null || shown.perSide.isEmpty()) {
                            Text(
                                "Sadece bar — plaka gerekmiyor.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.fit.muted
                            )
                        } else {
                            PlateStack(shown.perSide)
                            Spacer(Modifier.height(10.dp))
                            PlateChips(shown.perSide)
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TkStat("Tek taraf", shown.perSide.sum().kg(), Modifier.weight(1f))
                                TkStat("Plaka", "${shown.perSide.size} × 2", Modifier.weight(1f))
                                TkStat("Bar", bar.kg(), Modifier.weight(1f))
                            }
                        }
                        if (!exact) {
                            val other = listOfNotNull(result.below, result.above).firstOrNull { it != shown }
                            if (other != null) {
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.fit.elevated.copy(alpha = 0.6f))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Alternatif", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
                                        Text(
                                            if (other.perSide.isEmpty()) "sadece bar" else "her yana " + other.perSide.joinToString(" + ") { it.trimNum() },
                                            style = MaterialTheme.typography.labelMedium.mono(),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        other.totalKg.kg(),
                                        style = MaterialTheme.typography.titleSmall.mono().copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.fit.accent
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

/** Bar ucu + plakalar: yükseklik plakanın ağırlığıyla orantılı. */
@Composable
private fun PlateStack(plates: List<Float>) {
    val sleeve = MaterialTheme.fit.muted.copy(alpha = 0.5f)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.fit.elevated.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bar gövdesi ve yaka
            Box(Modifier.width(22.dp).height(8.dp).clip(RoundedCornerShape(2.dp)).background(sleeve))
            Box(Modifier.width(6.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(sleeve))
            Spacer(Modifier.width(4.dp))
            plates.forEach { p ->
                val c = plateColor(p)
                Box(
                    Modifier
                        .padding(end = 3.dp)
                        .width(26.dp)
                        .height((34 + p * 2.4f).dp.coerceAtMost(110.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .background(c),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        p.trimNum(),
                        style = MaterialTheme.typography.labelSmall.mono(),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
            Box(Modifier.width(28.dp).height(8.dp).clip(RoundedCornerShape(2.dp)).background(sleeve))
        }
    }
}

/** Plaka çipleri: aynı plakalar "2× 20" şeklinde gruplanır, renk plaka boyutuna göre. */
@Composable
private fun PlateChips(plates: List<Float>) {
    if (plates.isEmpty()) {
        Text("Plaka tanımlı değil", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
        return
    }
    val grouped = plates.groupBy { it }.entries.sortedByDescending { it.key }
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        grouped.forEach { (p, list) ->
            val c = plateColor(p)
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(c))
                Spacer(Modifier.width(5.dp))
                Text(
                    (if (list.size > 1) "${list.size}× " else "") + p.trimNum(),
                    style = MaterialTheme.typography.labelMedium.mono().copy(fontWeight = FontWeight.SemiBold),
                    color = c
                )
            }
        }
    }
}

@Composable
private fun plateColor(p: Float): Color = when (p) {
    25f -> Palette.danger
    20f -> Palette.info
    15f -> Palette.warning
    10f -> Palette.success
    5f -> Palette.violet
    else -> MaterialTheme.fit.muted
}

/* --------------------------------- Isınma ---------------------------------- */

@Composable
private fun WarmupTool(vm: AppViewModel) {
    val barDefault by vm.settings.barWeight.collectAsStateWithLifecycle()
    val userPlates by vm.settings.plates.collectAsStateWithLifecycle()
    var working by rememberSaveable { mutableStateOf("80") }
    var barbell by rememberSaveable { mutableStateOf(true) }
    val w = working.replace(',', '.').toFloatOrNull() ?: 0f

    // Barbell: her ısınma ağırlığı plakalarınla kurulabilecek en yakın değere yuvarlanır.
    val scheme = remember(w, barDefault, userPlates, barbell) {
        val base = Calc.warmupScheme(w, barDefault)
        if (!barbell) base
        else {
            val rounded = base.map { (weight, reps) ->
                PlateSolver.nearestTotal(weight, barDefault, userPlates) to reps
            }.distinctBy { it.first }
            val belowWorking = rounded.filter { it.first < w - 0.001f || w <= barDefault }
            belowWorking.ifEmpty { listOf(barDefault to 10) }
        }
    }

    LazyColumn(
        contentPadding = toolListPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            FitCard {
                TkCardTitle("Isınma piramidi", "Çalışma setine kadar kademeli ısınma setleri önerir.")
                Spacer(Modifier.height(12.dp))
                FitTextField(working, { working = it }, "Çalışma ağırlığın (kg)", keyboardType = KeyboardType.Decimal)
                Spacer(Modifier.height(10.dp))
                TkSegmented(listOf("Barbell · ${barDefault.trimNum()} kg", "Diğer"), if (barbell) 0 else 1) { barbell = it == 0 }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Bileşik hareketlerde (squat, bench, deadlift) sakatlanmayı önlemek ve sinir sistemini hazırlamak için kademeli ısınma önerilir." +
                        if (barbell) " Ağırlıklar plakalarınla kurulabilecek en yakın değere yuvarlanır." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.fit.muted
                )
            }
        }
        item {
            FitCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OverlineText("Setler", modifier = Modifier.weight(1f))
                    Text("${scheme.size} ısınma + 1 çalışma", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
                }
                Spacer(Modifier.height(8.dp))
                scheme.forEachIndexed { i, (weight, reps) ->
                    if (i > 0) Spacer(Modifier.height(6.dp))
                    val strength = 0.35f + 0.65f * (i + 1) / (scheme.size + 1).toFloat()
                    val perSide = if (barbell) PlateSolver.solve(weight, barDefault, userPlates).exact?.perSide else null
                    WarmupRow(
                        index = "${i + 1}",
                        weightText = weight.kg(),
                        sub = perSide?.let { if (it.isEmpty()) "sadece bar" else "her yana: " + it.joinToString(" + ") { p -> p.trimNum() } },
                        pct = if (w > 0f) (weight / w * 100f).roundToInt() else null,
                        reps = "$reps tekrar",
                        stripe = MaterialTheme.fit.accent.copy(alpha = strength),
                        valueColor = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(6.dp))
                WarmupRow(
                    index = "Ç",
                    weightText = w.kg(),
                    sub = "Çalışma seti",
                    pct = null,
                    reps = "",
                    stripe = MaterialTheme.fit.success,
                    valueColor = MaterialTheme.fit.success,
                    highlight = true
                )
            }
        }
    }
}

@Composable
private fun WarmupRow(
    index: String,
    weightText: String,
    sub: String?,
    pct: Int?,
    reps: String,
    stripe: Color,
    valueColor: Color,
    highlight: Boolean = false
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlight) MaterialTheme.fit.success.copy(alpha = 0.10f) else MaterialTheme.fit.elevated.copy(alpha = 0.45f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(stripe))
        Spacer(Modifier.width(12.dp))
        Text(
            index,
            style = MaterialTheme.typography.labelLarge.mono(),
            color = MaterialTheme.fit.muted,
            modifier = Modifier.width(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(weightText, style = MaterialTheme.typography.titleMedium.mono().copy(fontWeight = FontWeight.SemiBold), color = valueColor)
            if (!sub.isNullOrBlank()) {
                Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.padding(end = 12.dp), horizontalAlignment = Alignment.End) {
            if (reps.isNotBlank()) {
                Text(reps, style = MaterialTheme.typography.labelLarge.mono(), color = MaterialTheme.colorScheme.onSurface)
            }
            if (pct != null) {
                Text("%$pct", style = MaterialTheme.typography.labelSmall.mono(), color = MaterialTheme.fit.muted)
            }
        }
    }
}

/* --------------------------------- Vücut ----------------------------------- */

@Composable
private fun BodyTool(vm: AppViewModel) {
    val heightDefault by vm.settings.heightCm.collectAsStateWithLifecycle()
    val weightDefault by vm.settings.weightKg.collectAsStateWithLifecycle()
    val ageDefault by vm.settings.age.collectAsStateWithLifecycle()
    val maleDefault by vm.settings.isMale.collectAsStateWithLifecycle()

    var h by rememberSaveable { mutableStateOf(heightDefault.trimNum()) }
    var w by rememberSaveable { mutableStateOf(weightDefault.trimNum()) }
    var a by rememberSaveable { mutableStateOf(ageDefault.toString()) }
    var male by rememberSaveable { mutableStateOf(maleDefault) }
    var activity by rememberSaveable { mutableIntStateOf(1) }

    val hv = h.replace(',', '.').toFloatOrNull() ?: 0f
    val wv = w.replace(',', '.').toFloatOrNull() ?: 0f
    val av = a.toIntOrNull() ?: 0
    val bmi = Calc.bmi(wv, hv)
    val bmr = Calc.bmr(wv, hv, av, male)
    val factors = listOf(1.2f, 1.375f, 1.55f, 1.725f, 1.9f)
    val labels = listOf("Hareketsiz", "Hafif", "Orta", "Aktif", "Çok aktif")
    val tdee = Calc.tdee(bmr, factors[activity.coerceIn(0, factors.lastIndex)])

    val zones = listOf(
        TkZone(18.5f, Palette.info, "Zayıf"),
        TkZone(25f, MaterialTheme.fit.success, "Normal"),
        TkZone(30f, MaterialTheme.fit.warning, "Fazla"),
        TkZone(40f, MaterialTheme.fit.danger, "Obez")
    )
    val bmiColor = when {
        bmi <= 0f -> MaterialTheme.fit.muted
        bmi < 18.5f -> Palette.info
        bmi < 25f -> MaterialTheme.fit.success
        bmi < 30f -> MaterialTheme.fit.warning
        else -> MaterialTheme.fit.danger
    }

    LazyColumn(
        contentPadding = toolListPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            FitCard {
                TkCardTitle("Vücut bilgilerin", "Varsayılanlar profilinden gelir; burada değiştirmek profili etkilemez.")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FitTextField(h, { h = it }, "Boy (cm)", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                    FitTextField(w, { w = it }, "Kilo (kg)", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    FitTextField(a, { a = it.filter { c -> c.isDigit() } }, "Yaş", Modifier.weight(1f), keyboardType = KeyboardType.Number)
                    TkSegmented(listOf("Erkek", "Kadın"), if (male) 0 else 1, Modifier.weight(1f)) { male = it == 0 }
                }
            }
        }
        item {
            FitCard {
                TkCardTitle("Vücut kitle indeksi")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        TkHero(if (bmi > 0f) bmi.trimNum() else "—", color = bmiColor)
                    }
                    if (bmi > 0f) {
                        TkChip(Calc.bmiCategory(bmi), bmiColor, Modifier.padding(bottom = 6.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                TkRangeBar(bmi, 15f, 40f, zones)
                Spacer(Modifier.height(10.dp))
                Text(
                    "VKİ kas kütlesini hesaba katmaz. Düzenli ağırlık çalışan biri için yanıltıcı olabilir; ölçüm ve fotoğraf takibi daha güvenilirdir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.fit.muted
                )
            }
        }
        item {
            FitCard {
                TkCardTitle("Günlük kalori ihtiyacı", "Mifflin–St Jeor bazal metabolizma × aktivite katsayısı")
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    labels.forEachIndexed { i, l ->
                        ChoiceChip(l, activity == i, { activity = i })
                    }
                }
                Spacer(Modifier.height(14.dp))
                TkHero(
                    if (tdee > 0f) "${tdee.toInt()}" else "—", "kcal", MaterialTheme.fit.accent,
                    "Günlük ihtiyaç (TDEE) · ×${factors[activity.coerceIn(0, factors.lastIndex)].trimNum()}"
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TkStat("BMR", "${bmr.toInt()} kcal", Modifier.weight(1f))
                    TkStat("Kas alımı +%10", "${(tdee * 1.1f).toInt()} kcal", Modifier.weight(1f), MaterialTheme.fit.success)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TkStat("Yağ kaybı −%15", "${(tdee * 0.85f).toInt()} kcal", Modifier.weight(1f), MaterialTheme.fit.warning)
                    TkStat("Protein ≈", "${(wv * 1.8f).toInt()} g/gün", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Protein hedefi olarak vücut ağırlığının kilogramı başına 1,6–2,2 g yaygın bir öneridir (≈ ${(wv * 1.8f).toInt()} g/gün).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.fit.muted
                )
            }
        }
    }
}
