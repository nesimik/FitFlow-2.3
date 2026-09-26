package com.example.ui.screens

import kotlin.math.roundToInt
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.border
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.ui.theme.mono
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.core.Analytics
import com.example.core.Calc
import com.example.core.MuscleMap
import com.example.core.ProgressAnalytics
import com.example.core.Muscles
import com.example.core.TR
import com.example.core.formatDate
import com.example.core.formatDateShort
import com.example.core.formatMonthYear
import com.example.core.kg
import com.example.core.trimNum
import com.example.data.ExerciseEntity
import com.example.ui.AppViewModel
import com.example.ui.Routes
import com.example.ui.components.Badge
import com.example.ui.components.BodyMuscleMapPair
import com.example.ui.components.MuscleChipRow
import com.example.ui.components.MusclePrimarySecondaryLegend
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.FitCard
import com.example.ui.components.KeyValueRow
import com.example.ui.components.LineChart
import com.example.ui.components.OverlineText
import com.example.ui.components.PillTabs
import com.example.ui.components.RoundIconButton
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatTile
import com.example.ui.theme.Palette
import com.example.ui.theme.fit

/* ================================= Kütüphane ================================= */

@Composable
fun LibraryScreen(vm: AppViewModel, nav: NavHostController) {
    val all by vm.exercises.collectAsStateWithLifecycle()
    val allSets by vm.allSets.collectAsStateWithLifecycle()
    val days by vm.routineDays.collectAsStateWithLifecycle()
    val allItems by vm.allItems.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("Hepsi") }
    var onlyFavorites by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }

    val programIds = remember(days, allItems) {
        val ids = days.map { it.id }.toSet()
        allItems.filter { it.dayId in ids }.map { it.exerciseId }.toSet()
    }
    var onlyProgram by remember(programIds.isNotEmpty()) { mutableStateOf(programIds.isNotEmpty()) }
    val stats = remember(allSets) { libraryStats(allSets) }

    // Arama bir kas / bölge adıysa ("kalça", "arka bacak", "pazu") o kası çalıştıran hareketler.
    val muscleKeys = remember(query) { com.example.core.MuscleSearch.muscles(query) }
    val searching = query.isNotBlank()

    val results: List<Pair<ExerciseEntity, Float?>> = remember(all, query, group, onlyFavorites, onlyProgram, programIds, muscleKeys) {
        val base = all.asSequence()
            .filter { group == "Hepsi" || it.muscleGroup == group }
            .filter { !onlyFavorites || it.isFavorite }
            .filter { searching || !onlyProgram || it.id in programIds }
            .toList()
        if (muscleKeys != null) {
            val scored = base.map { it to com.example.core.MuscleSearch.score(it.name, it.muscleGroup, it.secondaryMuscles, muscleKeys) }
                .filter { it.second >= com.example.core.MuscleSearch.MIN_SCORE }
                .sortedWith(compareByDescending<Pair<ExerciseEntity, Float>> { it.second }
                    .thenByDescending { it.first.id in programIds }
                    .thenBy { it.first.name })
            val q = com.example.core.MuscleSearch.fold(query)
            val byName = base.filter { e -> scored.none { it.first.id == e.id } && com.example.core.MuscleSearch.fold(e.name).contains(q) }
            scored.map { it.first to it.second as Float? } + byName.map { it to null }
        } else {
            val q = com.example.core.MuscleSearch.fold(query)
            base.filter { q.isBlank() || com.example.core.MuscleSearch.fold(it.name).contains(q) }
                .sortedWith(compareByDescending<ExerciseEntity> { it.id in programIds }
                    .thenByDescending { it.isFavorite }
                    .thenBy { it.name })
                .map { it to null }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Hareketler",
            subtitle = if (programIds.isNotEmpty()) "${programIds.size}'i programında" else null,
            onBack = if (nav.previousBackStackEntry != null) { { nav.popBackStack() } } else null
        ) {
            Text("${all.size}", style = MaterialTheme.typography.labelLarge.mono(), color = MaterialTheme.fit.muted)
            Spacer(Modifier.width(10.dp))
            RoundIconButton(Icons.Default.Add, MaterialTheme.colorScheme.onSurface, 40.dp, MaterialTheme.fit.elevated) { showCreate = true }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            SearchField(query, { query = it }, "Hareket veya kas: kalça, arka bacak…")
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                if (programIds.isNotEmpty()) {
                    com.example.ui.components.ChoiceChip("Programımda", onlyProgram && !searching, { onlyProgram = !onlyProgram })
                }
                com.example.ui.components.ChoiceChip("★ Favori", onlyFavorites, { onlyFavorites = !onlyFavorites }, color = MaterialTheme.fit.gold)
                (listOf("Hepsi") + Muscles.all).forEach {
                    com.example.ui.components.ChoiceChip(if (it == "Hepsi") "Tümü" else it, group == it, { group = it })
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (results.isEmpty()) {
            EmptyState(
                Icons.Default.FitnessCenter,
                "Hareket bulunamadı",
                "Filtreleri değiştir ya da kendi hareketini oluştur.",
                actionLabel = "Yeni hareket",
                onAction = { showCreate = true }
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 16.dp, bottom = 110.dp)
            ) {
                if (muscleKeys != null) {
                    item {
                        Text(
                            "${com.example.core.MuscleSearch.label(muscleKeys)} için hareketler · en çok çalıştırandan başlayarak",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.fit.muted,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                } else if (!searching && onlyProgram) {
                    item {
                        OverlineText("Programındakiler · son en iyi set", modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
                items(results, key = { it.first.id }) { (ex, score) ->
                    ExerciseRow(
                        ex = ex,
                        stat = stats[ex.id],
                        score = score,
                        highlight = muscleKeys,
                        onClick = { nav.navigate("${Routes.EXERCISE}/${ex.id}") },
                        onFavorite = { vm.toggleFavorite(ex) }
                    )
                }
            }
        }
    }

    if (showCreate) {
        ExerciseEditorDialog(
            initial = null,
            onSave = { vm.saveExercise(it); showCreate = false },
            onDismiss = { showCreate = false }
        )
    }
}

/** Kütüphane satırı için özet: son seansın en iyi seti ve ~8 haftalık tahmini 1RM değişimi. */
private data class LibStat(val lastBest: String, val trendPct: Int?)

private fun libraryStats(sets: List<com.example.data.WorkoutSetEntity>): Map<Long, LibStat> {
    val now = System.currentTimeMillis()
    val day = 86_400_000L
    return sets.filter { it.isCompleted && !it.isWarmup && it.reps > 0 }
        .groupBy { it.exerciseId }
        .mapValues { (_, list) ->
            val lastWorkout = list.maxByOrNull { it.performedAt }!!.workoutId
            val best = list.filter { it.workoutId == lastWorkout }.maxByOrNull { Calc.e1rm(it.weightKg, it.reps) }!!
            val recent = list.filter { it.performedAt >= now - 21 * day }.maxOfOrNull { Calc.e1rm(it.weightKg, it.reps) }
            val old = list.filter { it.performedAt in (now - 70 * day)..(now - 42 * day) }.maxOfOrNull { Calc.e1rm(it.weightKg, it.reps) }
            LibStat(
                lastBest = if (best.weightKg > 0f) "${best.weightKg.trimNum()} × ${best.reps}" else "${best.reps} tekrar",
                trendPct = if (recent != null && old != null && old > 0f) Math.round((recent - old) / old * 100f) else null
            )
        }
}

@Composable
private fun ExerciseRow(
    ex: ExerciseEntity,
    stat: LibStat?,
    score: Float?,
    highlight: List<String>?,
    onClick: () -> Unit,
    onFavorite: () -> Unit
) {
    val accent = MaterialTheme.fit.accent
    val activation = remember(ex.id, ex.name) { MuscleMap.resolve(ex.name, ex.muscleGroup, ex.secondaryMuscles) }
    val weights = remember(activation) { activation.weights() }
    val colors = remember(weights, accent) {
        weights.mapValues { (_, w) -> if (w >= 0.75f) accent else accent.copy(alpha = 0.45f) }
    }
    // Kasların çoğu arkadaysa (sırt, arka bacak) arka görünüm gösterilir.
    val back = remember(weights) {
        val front = weights.filterKeys { it in MuscleMap.frontVisible && it !in MuscleMap.backVisible }.values.sum()
        val rear = weights.filterKeys { it in MuscleMap.backVisible && it !in MuscleMap.frontVisible }.values.sum()
        rear > front
    }
    val musclesText = remember(activation) {
        (activation.primary + activation.secondary).distinct().take(3).joinToString(", ") { MuscleMap.label(it) }
    }
    Column(Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            com.example.ui.components.BodyMuscleMap(
                view = if (back) com.example.ui.components.BodyView.BACK else com.example.ui.components.BodyView.FRONT,
                colors = colors,
                modifier = Modifier.width(44.dp).height(60.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ex.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(
                    musclesText.ifBlank { ex.muscleGroup } + if (ex.isCustom) " · özel" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.fit.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                when {
                    score != null -> {
                        Text("%${(score * 100).roundToInt()}", style = MaterialTheme.typography.labelLarge.mono().copy(fontWeight = FontWeight.SemiBold), color = accent)
                        Text(
                            highlight?.let { com.example.core.MuscleSearch.label(it) }?.lowercase() ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.fit.muted,
                            maxLines = 1
                        )
                    }
                    stat != null -> {
                        Text(stat.lastBest.replace('.', ','), style = MaterialTheme.typography.labelLarge.mono().copy(fontWeight = FontWeight.SemiBold))
                        stat.trendPct?.let { t ->
                            Spacer(Modifier.height(3.dp))
                            Text(
                                (if (t > 0) "+" else if (t < 0) "−" else "±") + "%${kotlin.math.abs(t)}",
                                style = MaterialTheme.typography.labelMedium.mono(),
                                color = when {
                                    t > 0 -> MaterialTheme.fit.success
                                    t < 0 -> MaterialTheme.fit.danger
                                    else -> MaterialTheme.fit.muted
                                }
                            )
                        }
                    }
                    else -> Text("—", style = MaterialTheme.typography.labelLarge.mono(), color = MaterialTheme.fit.muted)
                }
            }
            if (ex.isFavorite) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.Star, null, tint = MaterialTheme.fit.gold, modifier = Modifier.size(16.dp).clickable { onFavorite() })
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)))
    }
}

@Composable
private fun DetailKpi(label: String, value: String, caption: String?, captionColor: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    FitCard(modifier, corner = 16.dp, contentPadding = PaddingValues(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.muted, maxLines = 1)
        Spacer(Modifier.height(5.dp))
        Text(value.replace('.', ','), style = MaterialTheme.typography.titleMedium.mono().copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
        if (caption != null) Text(caption, style = MaterialTheme.typography.labelSmall, color = captionColor, maxLines = 1)
    }
}

/* =============================== Hareket detayı ============================== */

@Composable
fun ExerciseDetailScreen(vm: AppViewModel, nav: NavHostController, exerciseId: Long) {
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val allSets by vm.allSets.collectAsStateWithLifecycle()
    val bodyWeight by vm.settings.weightKg.collectAsStateWithLifecycle()
    val days by vm.routineDays.collectAsStateWithLifecycle()
    val allItems by vm.allItems.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val ex = exercises.firstOrNull { it.id == exerciseId }
    var metric by remember { mutableIntStateOf(0) }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    if (ex == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Hareket bulunamadı", color = MaterialTheme.fit.muted)
        }
        return
    }

    val progress = remember(allSets, exerciseId) { vm.progressFor(exerciseId) }
    val collapsedMonths = remember { mutableStateMapOf<String, Boolean>() }
    val progressReversed = remember(progress) { progress.reversed() }
    val groupedByMonth = remember(progressReversed) {
        progressReversed.groupBy { formatMonthYear(it.dateMillis) }
    }
    val sets = remember(allSets, exerciseId) {
        allSets.filter { it.exerciseId == exerciseId && it.isCompleted && !it.isWarmup }
    }
    val bestWeight = sets.maxOfOrNull { it.weightKg } ?: 0f
    val bestE1rm = sets.maxOfOrNull { Calc.e1rm(it.weightKg, it.reps) } ?: 0f
    val totalVolume = sets.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat()
    val level = Calc.strengthLevel(ex.muscleGroup, Calc.strengthRatio(bestE1rm, bodyWeight))

    // Programdaki yeri ve sıradaki seansın reçetesi
    val programDay = remember(days, allItems, exerciseId) {
        days.firstOrNull { d -> allItems.any { it.dayId == d.id && it.exerciseId == exerciseId && !it.isWarmup } }
    }
    val nextRx = remember(programDay, allItems, allSets, workouts) {
        programDay?.let { d ->
            val item = allItems.first { it.dayId == d.id && it.exerciseId == exerciseId && !it.isWarmup }
            val name = item.customName.ifBlank { ex.name }
            vm.planFor(d.id).firstOrNull { it.first == name }?.second
        }
    }
    val bestSet = sets.maxByOrNull { Calc.e1rm(it.weightKg, it.reps) }
    val sessionCount = sets.map { it.workoutId }.distinct().size
    val trend = remember(allSets, exerciseId) { ProgressAnalytics.trendPct(allSets.filter { it.exerciseId == exerciseId }) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, MaterialTheme.colorScheme.onSurface, 40.dp, MaterialTheme.fit.elevated) { nav.popBackStack() }
            Spacer(Modifier.weight(1f))
            RoundIconButton(
                if (ex.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                if (ex.isFavorite) MaterialTheme.fit.gold else MaterialTheme.fit.muted,
                40.dp, MaterialTheme.fit.elevated
            ) { vm.toggleFavorite(ex) }
            Spacer(Modifier.width(8.dp))
            RoundIconButton(Icons.Default.Edit, MaterialTheme.fit.muted, 40.dp, MaterialTheme.fit.elevated) { showEdit = true }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            /* Başlık: grup · programda — ad — kaslar */
            item {
                val act = remember(ex.id, ex.name, ex.muscleGroup) { MuscleMap.resolve(ex.name, ex.muscleGroup, ex.secondaryMuscles) }
                Column {
                    OverlineText(
                        listOfNotNull(ex.muscleGroup, ex.equipment.takeIf { it.isNotBlank() }, if (programDay != null) "programında" else null)
                            .joinToString(" · ")
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(ex.name, style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold))
                    val m = (act.primary + act.secondary).distinct().joinToString(", ") { MuscleMap.label(it) }
                    if (m.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(m, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.fit.muted)
                    }
                }
            }
            if (nextRx != null && programDay != null) {
                item {
                    val c = actionColor(nextRx.action)
                    val acc = MaterialTheme.fit.accent
                    val shape = RoundedCornerShape(22.dp)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    0f to acc.copy(alpha = 0.14f),
                                    0.65f to MaterialTheme.colorScheme.surface
                                )
                            )
                            .border(1.dp, acc.copy(alpha = 0.22f), shape)
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OverlineText(
                                "Sıradaki seans" + if (programDay.weekday in 1..7) " · " + com.example.core.weekdayShort(programDay.weekday) else "",
                                acc,
                                Modifier.weight(1f)
                            )
                            Badge(nextRx.action.label, c)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(nextRx.headline.replace('.', ','), style = MaterialTheme.typography.displaySmall.mono().copy(fontWeight = FontWeight.SemiBold, fontSize = 28.sp))
                        Spacer(Modifier.height(6.dp))
                        Text(nextRx.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f))
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailKpi(
                        "Tahmini 1RM",
                        if (bestE1rm > 0f) bestE1rm.kg() else "—",
                        level.first.takeIf { bestE1rm > 0f },
                        MaterialTheme.fit.muted,
                        Modifier.weight(1f)
                    )
                    DetailKpi(
                        "En iyi set",
                        bestSet?.let { if (it.weightKg > 0f) "${it.weightKg.trimNum()}×${it.reps}" else "${it.reps}" } ?: "—",
                        bestSet?.let { formatDateShort(it.performedAt) },
                        MaterialTheme.fit.muted,
                        Modifier.weight(1f)
                    )
                    if (trend != 0f) {
                        DetailKpi(
                            "8 hafta",
                            (if (trend > 0f) "+" else "−") + "%" + kotlin.math.abs(trend).roundToInt(),
                            "$sessionCount seans",
                            MaterialTheme.fit.muted,
                            Modifier.weight(1f)
                        )
                    } else {
                        DetailKpi("Seans", "$sessionCount", null, MaterialTheme.fit.muted, Modifier.weight(1f))
                    }
                }
            }

            item {
                val activation = remember(ex.id, ex.name, ex.muscleGroup) {
                    MuscleMap.resolve(ex.name, ex.muscleGroup, ex.secondaryMuscles)
                }
                if (!activation.isEmpty) {
                    val accent = MaterialTheme.fit.accent
                    // Katkı oranları: renk yoğunluğu oranla orantılı; göğüs hareketlerinde üst/alt ayrı.
                    val contributions = remember(activation, ex.name) {
                        val w = LinkedHashMap(activation.weights())
                        w[MuscleMap.CHEST]?.let { c ->
                            val (u, l) = MuscleMap.chestSplit(ex.name)
                            w.remove(MuscleMap.CHEST)
                            w[MuscleMap.CHEST_LOWER] = c * l
                            w[MuscleMap.CHEST_UPPER] = c * u
                        }
                        w.entries.sortedByDescending { it.value }.map { it.key to it.value }
                    }
                    val mapColors = remember(contributions, accent) {
                        contributions.associate { (k, v) -> k to accent.copy(alpha = (0.25f + 0.75f * v).coerceIn(0f, 1f)) }
                    }
                    val view = remember(contributions) {
                        val front = contributions.filter { com.example.ui.components.bodyViewOf(it.first) == com.example.ui.components.BodyView.FRONT }.sumOf { it.second.toDouble() }
                        val rear = contributions.filter { com.example.ui.components.bodyViewOf(it.first) == com.example.ui.components.BodyView.BACK }.sumOf { it.second.toDouble() }
                        if (rear > front) com.example.ui.components.BodyView.BACK else com.example.ui.components.BodyView.FRONT
                    }
                    FitCard {
                        Text("Çalışan kaslar", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.example.ui.components.BodyMuscleMap(
                                view = view,
                                colors = mapColors,
                                modifier = Modifier.width(96.dp).height(165.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                                contributions.take(6).forEach { (k, v) ->
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(MuscleMap.label(k), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                                            Text("%${(v * 100).roundToInt()}", style = MaterialTheme.typography.labelMedium.mono(), color = MaterialTheme.fit.muted)
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.fit.elevated)) {
                                            Box(
                                                Modifier
                                                    .fillMaxWidth(v.coerceIn(0f, 1f))
                                                    .height(5.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(accent.copy(alpha = (0.35f + 0.65f * v).coerceIn(0f, 1f)))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (ex.videoUrl.isNotBlank()) {
                item {
                    FitCard(onClick = { openUrl(context, ex.videoUrl) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayCircle, null, tint = MaterialTheme.fit.danger, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Tekniği izle", style = MaterialTheme.typography.titleSmall)
                                Text("Videoyu tarayıcıda aç", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
                            }
                        }
                    }
                }
            }

            if (ex.instructions.isNotBlank() || ex.tips.isNotBlank()) {
                item {
                    FitCard {
                        OverlineText("Nasıl yapılır")
                        Spacer(Modifier.height(8.dp))
                        if (ex.instructions.isNotBlank()) {
                            Text(ex.instructions, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        if (ex.tips.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.fit.accent.copy(alpha = 0.08f))
                                    .padding(10.dp)
                            ) {
                                Text("💡 ${ex.tips}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
                            }
                        }
                        if (ex.secondaryMuscles.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            KeyValueRow("Yardımcı kaslar", ex.secondaryMuscles)
                        }
                    }
                }
            }

            if (progress.isNotEmpty()) {
                item {
                    Column {
                        SectionHeader("Gelişim", "${progress.size} seans kaydı")
                        Spacer(Modifier.height(10.dp))
                        PillTabs(listOf("1RM", "En ağır", "Hacim", "Tekrar"), metric) { metric = it }
                        Spacer(Modifier.height(12.dp))
                        FitCard {
                            LineChart(
                                values = progress.map {
                                    when (metric) {
                                        0 -> it.e1rm
                                        1 -> it.topWeight
                                        2 -> it.volume
                                        else -> it.totalReps.toFloat()
                                    }
                                },
                                labels = progress.map { formatDateShort(it.dateMillis) },
                                suffix = if (metric == 3) "" else " kg",
                                height = 180.dp
                            )
                        }
                    }
                }
            }

            item {
                val bestByRange = remember(allSets, exerciseId) {
                    ProgressAnalytics.bestSetsByRange(allSets.filter { it.exerciseId == exerciseId })
                }
                if (bestByRange.isNotEmpty()) {
                    FitCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                        OverlineText("Tekrar aralığına göre rekor", modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                        bestByRange.forEach { b ->
                            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)))
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(b.range, style = MaterialTheme.typography.labelLarge.mono(), color = MaterialTheme.fit.muted, modifier = Modifier.width(60.dp))
                                Text(
                                    "${b.weight.trimNum().replace('.', ',')} × ${b.reps}",
                                    style = MaterialTheme.typography.titleSmall.mono().copy(fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "1RM ${b.e1rm.trimNum().replace('.', ',')}",
                                    style = MaterialTheme.typography.labelMedium.mono(),
                                    color = Color(0xFF6B7483)
                                )
                            }
                        }
                    }
                }
            }

            if (progress.isNotEmpty()) {
                item { SectionHeader("Seans geçmişi") }
                groupedByMonth.forEach { (month, monthProgressList) ->
                    val isMonthCollapsed = collapsedMonths[month] == true

                    item(key = "month_$month") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { collapsedMonths[month] = !isMonthCollapsed }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isMonthCollapsed) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.fit.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                OverlineText(month, MaterialTheme.fit.accent)
                            }
                            Text(
                                "${monthProgressList.size} seans",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.fit.muted
                            )
                        }
                    }

                    if (!isMonthCollapsed) {
                        items(monthProgressList, key = { "p_${month}_${it.dateMillis}" }) { p ->
                            FitCard(contentPadding = PaddingValues(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(formatDate(p.dateMillis), style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "${p.sets} set · ${p.totalReps} tekrar · ${p.volume.trimNum()} kg hacim",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.fit.muted
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(p.topWeight.kg(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.fit.accent)
                                        Text("1RM ${p.e1rm.trimNum()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    EmptyState(
                        Icons.Default.FitnessCenter,
                        "Henüz kayıt yok",
                        "Bu hareketi bir antrenmanda yaptığında gelişim grafiğin burada oluşacak."
                    )
                }
            }

            if (ex.isCustom) {
                item {
                    com.example.ui.components.GhostButton(
                        "Hareketi sil", { showDelete = true },
                        Modifier.fillMaxWidth(), Icons.Default.Delete, MaterialTheme.fit.danger
                    )
                }
            }
        }
    }

    if (showEdit) {
        ExerciseEditorDialog(
            initial = ex,
            onSave = { vm.saveExercise(it); showEdit = false },
            onDismiss = { showEdit = false }
        )
    }

    if (showDelete) {
        ConfirmDialog(
            title = "Hareketi sil",
            text = "Bu hareket kütüphaneden silinecek. Geçmiş antrenman kayıtların korunur.",
            confirmLabel = "Sil",
            destructive = true,
            onConfirm = { vm.deleteExercise(ex); showDelete = false; nav.popBackStack() },
            onDismiss = { showDelete = false }
        )
    }
}
