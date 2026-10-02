package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import com.example.ui.components.KeyValueRow
import com.example.ui.components.OverlineText
import com.example.ui.components.PillTabs
import com.example.ui.theme.Palette
import com.example.ui.theme.fit

@Composable
fun ToolsScreen(vm: AppViewModel, nav: NavHostController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Her aracın rememberSaveable durumu sekme değişince korunur (SaveableStateProvider).
    val stateHolder = rememberSaveableStateHolder()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Hesaplayıcılar", "Antrenman planlaman için pratik araçlar", onBack = { nav.popBackStack() })
        Column(Modifier.padding(horizontal = 16.dp)) {
            PillTabs(listOf("1RM", "Plaka", "Isınma", "Vücut"), tab) { tab = it }
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            FitCard {
                if (lifts.isNotEmpty()) {
                    OverlineText("Hareketlerinden doldur")
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
                            style = MaterialTheme.typography.bodySmall,
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
            FitCard(container = MaterialTheme.fit.accent.copy(alpha = 0.10f), border = MaterialTheme.fit.accent.copy(alpha = 0.3f)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    OverlineText("TAHMİNİ 1 TEKRAR MAKSİMUM", MaterialTheme.fit.accent)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (e1rm > 0f) e1rm.kg() else "—",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.fit.accent
                    )
                    Text(
                        "4 formülün ortalaması",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.fit.muted
                    )
                }
            }
        }
        if (e1rm > 0f) {
            item {
                FitCard {
                    OverlineText("Formüllere göre")
                    Spacer(Modifier.height(8.dp))
                    KeyValueRow("Epley", Calc.epley(w, r).kg())
                    KeyValueRow("Brzycki", Calc.brzycki(w, r).kg())
                    KeyValueRow("Lombardi", Calc.lombardi(w, r).kg())
                    KeyValueRow("O'Conner", Calc.oconner(w, r).kg())
                }
            }
            item {
                FitCard {
                    OverlineText("Yüzde tablosu")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Antrenman ağırlığını planlarken kullan. Hipertrofi genelde %65-80, güç %85+ aralığında çalışılır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.fit.muted
                    )
                    Spacer(Modifier.height(10.dp))
                    Calc.percentTable(e1rm).forEach { (pct, value) ->
                        val repsLabel = when {
                            pct >= 95 -> "1-2 tekrar"
                            pct >= 90 -> "3-4 tekrar"
                            pct >= 85 -> "5-6 tekrar"
                            pct >= 80 -> "7-8 tekrar"
                            pct >= 75 -> "9-10 tekrar"
                            pct >= 70 -> "11-12 tekrar"
                            else -> "13+ tekrar"
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("%$pct", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(52.dp), color = MaterialTheme.fit.accent)
                            Text(value.kg(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(repsLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
                        }
                    }
                }
            }
        }
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            FitCard {
                FitTextField(target, { target = it }, "Hedef toplam ağırlık (kg)", keyboardType = KeyboardType.Decimal)
                Spacer(Modifier.height(12.dp))
                OverlineText("Bar ağırlığı")
                Spacer(Modifier.height(7.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    barOptions.forEach { v ->
                        ChoiceChip("${v.trimNum()} kg", bar == v, { bar = v })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Plakaların: " + userPlates.filter { it > 0f }.sortedDescending().joinToString(" · ") { it.trimNum() } + " kg (Ayarlar › Ekipman)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.fit.muted
                )
            }
        }
        item {
            FitCard {
                OverlineText("Her iki tarafa takılacak")
                Spacer(Modifier.height(12.dp))
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
                        if (result.exact == null) {
                            val options = listOfNotNull(result.below?.totalKg, result.above?.totalKg).distinct()
                            Text(
                                "Tam kurulamaz — en yakın: " + options.joinToString(" / ") { "${it.trimNum()} kg" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.fit.warning
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        if (shown == null || shown.perSide.isEmpty()) {
                            Text(
                                "Sadece bar — plaka gerekmiyor.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.fit.muted
                            )
                        } else {
                            PlateStack(shown.perSide)
                            Spacer(Modifier.height(14.dp))
                            KeyValueRow("Tek taraf", shown.perSide.joinToString(" + ") { it.trimNum() } + " = " + shown.perSide.sum().kg())
                            KeyValueRow("Ulaşılan ağırlık", shown.totalKg.kg(), if (result.exact != null) MaterialTheme.fit.success else MaterialTheme.fit.warning)
                        }
                        if (result.exact == null) {
                            val other = listOfNotNull(result.below, result.above).firstOrNull { it != shown }
                            if (other != null) {
                                KeyValueRow(
                                    "Alternatif ${other.totalKg.trimNum()} kg",
                                    if (other.perSide.isEmpty()) "sadece bar" else other.perSide.joinToString(" + ") { it.trimNum() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlateStack(plates: List<Float>) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        plates.forEach { p ->
            Box(
                Modifier
                    .width(30.dp)
                    .height((34 + p * 2.4f).dp.coerceAtMost(110.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .background(plateColor(p)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    p.trimNum(),
                    style = MaterialTheme.typography.labelSmall,
                    color = androidx.compose.ui.graphics.Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun plateColor(p: Float) = when (p) {
    25f -> Palette.danger
    20f -> Palette.info
    15f -> Palette.warning
    10f -> Palette.success
    5f -> Palette.violet
    else -> androidx.compose.ui.graphics.Color(0xFF64748B)
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            FitCard {
                FitTextField(working, { working = it }, "Çalışma ağırlığın (kg)", keyboardType = KeyboardType.Decimal)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Barbell (${barDefault.trimNum()} kg bar)", barbell, { barbell = true })
                    ChoiceChip("Diğer", !barbell, { barbell = false })
                }
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
                OverlineText("Isınma piramidi")
                Spacer(Modifier.height(10.dp))
                scheme.forEachIndexed { i, (weight, reps) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.fit.accent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.accent)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(weight.kg(), style = MaterialTheme.typography.titleMedium)
                            if (barbell) {
                                val perSide = PlateSolver.solve(weight, barDefault, userPlates).exact?.perSide
                                if (perSide != null) {
                                    Text(
                                        if (perSide.isEmpty()) "sadece bar" else "her yana: " + perSide.joinToString(" + ") { it.trimNum() },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.fit.muted
                                    )
                                }
                            }
                        }
                        Text("$reps tekrar", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.fit.muted)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.fit.success.copy(alpha = 0.10f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Çalışma seti", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(w.kg(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.fit.success)
                }
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

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            FitCard {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FitTextField(h, { h = it }, "Boy (cm)", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                    FitTextField(w, { w = it }, "Kilo (kg)", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    FitTextField(a, { a = it.filter { c -> c.isDigit() } }, "Yaş", Modifier.weight(1f), keyboardType = KeyboardType.Number)
                    Column(Modifier.weight(1f)) {
                        OverlineText("Cinsiyet")
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ChoiceChip("Erkek", male, { male = true })
                            ChoiceChip("Kadın", !male, { male = false })
                        }
                    }
                }
            }
        }
        item {
            FitCard {
                OverlineText("Vücut kitle indeksi")
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(bmi.trimNum(), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.fit.accent)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        Calc.bmiCategory(bmi),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.fit.muted,
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "VKİ kas kütlesini hesaba katmaz. Düzenli ağırlık çalışan biri için yanıltıcı olabilir; ölçüm ve fotoğraf takibi daha güvenilirdir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.fit.muted
                )
            }
        }
        item {
            FitCard {
                OverlineText("Günlük kalori ihtiyacı")
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
                KeyValueRow("Bazal metabolizma (BMR)", "${bmr.toInt()} kcal")
                KeyValueRow("Günlük ihtiyaç (TDEE)", "${tdee.toInt()} kcal", MaterialTheme.fit.accent)
                KeyValueRow("Kas alımı (+%10)", "${(tdee * 1.1f).toInt()} kcal")
                KeyValueRow("Yağ kaybı (−%15)", "${(tdee * 0.85f).toInt()} kcal")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Protein hedefi olarak vücut ağırlığının kilogramı başına 1,6–2,2 g yaygın bir öneridir (≈ ${(wv * 1.8f).toInt()} g/gün).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.fit.muted
                )
            }
        }
    }
}
