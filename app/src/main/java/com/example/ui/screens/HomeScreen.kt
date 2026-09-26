package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.core.Analytics
import com.example.core.MuscleMap
import com.example.core.MuscleRecovery
import com.example.core.Prescription
import com.example.core.ProgressAction
import com.example.core.RecoveryState
import com.example.core.formatTime
import com.example.core.formatTonnage
import com.example.core.formatWeekday
import com.example.core.startOfWeek
import com.example.core.todayWeekday
import com.example.core.weekdayName
import com.example.data.RoutineDayEntity
import com.example.data.RoutineItemEntity
import com.example.data.WorkoutEntity
import com.example.data.WorkoutSetEntity
import com.example.ui.AppViewModel
import com.example.ui.Routes
import com.example.ui.components.BodyMuscleMapPair
import com.example.ui.components.DeloadAlertBox
import com.example.ui.components.DeloadDesignDialog
import com.example.ui.components.FitCard
import com.example.ui.components.MuscleColors
import com.example.ui.components.RoundIconButton
import com.example.ui.theme.Palette
import com.example.ui.theme.onColorFor
import com.example.ui.theme.fit
import com.example.ui.theme.mono
import com.example.ui.theme.overline
import java.util.Calendar

/* ==========================================================================
 * Bugün (ana ekran)
 *
 * Tek bir soruyu cevaplar: "bugün ne yapacağım?"
 *   1. Bugün kartı   — antrenman günü: hedefler + başlat; dinlenme günü: sıradaki seans
 *                      ve toparlanma çakışması; devam eden seans: seansa dön
 *   2. Bu hafta      — 7 günlük şerit
 *   3. Toparlanma    — kas haritası + henüz hazır olmayan kaslar
 *   4. Son antrenman — tek satırlık özet
 * Haftalık istatistikler, hacim trendi ve kas dengesi İlerleme ekranındadır.
 * ========================================================================== */

@Composable
fun HomeScreen(vm: AppViewModel, nav: NavHostController) {
    val name by vm.settings.userName.collectAsStateWithLifecycle()
    val stats by vm.dashboard.collectAsStateWithLifecycle()
    val days by vm.routineDays.collectAsStateWithLifecycle()
    val routine by vm.activeRoutine.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val active by vm.activeWorkout.collectAsStateWithLifecycle()
    val allItems by vm.allItems.collectAsStateWithLifecycle()
    val deloadRecommendation by vm.deloadRecommendation.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val allSets by vm.allSets.collectAsStateWithLifecycle()
    val prs by vm.prs.collectAsStateWithLifecycle()

    var showDeloadDesignDialog by remember { mutableStateOf(false) }
    var showCancelActiveDialog by remember { mutableStateOf(false) }
    val hasPreDeloadBackup = remember(vm.settings) { vm.settings.getPreDeloadBackup().isNotBlank() }

    val today = todayWeekday()
    val plannedWeekdays = remember(days) { days.map { it.weekday }.filter { it in 1..7 }.toSet() }
    val todayDay = remember(days, today) { days.firstOrNull { it.weekday == today } }
    val doneToday = remember(workouts, todayDay) {
        val dayStart = startOfDay(System.currentTimeMillis())
        workouts.any { it.isFinished && it.startedAt >= dayStart && (todayDay == null || it.routineDayId == todayDay.id) }
    }
    val todayWorkout = remember(workouts) {
        val dayStart = startOfDay(System.currentTimeMillis())
        workouts.filter { it.isFinished && it.startedAt >= dayStart }.maxByOrNull { it.startedAt }
    }
    // Bugün planlı ve henüz yapılmadıysa antrenman günü; değilse sıradaki planlı gün.
    val isTrainingDay = todayDay != null && !doneToday
    val nextDay = remember(days, today, workouts, isTrainingDay) {
        if (isTrainingDay) todayDay else nextPlannedDay(days, workouts, today)
    }
    val recovery = remember(allSets, exercises) { vm.recoveryNow() }
    val plan = remember(nextDay, allItems, allSets, workouts, deloadRecommendation) {
        nextDay?.let { vm.planFor(it.id) } ?: emptyList()
    }

    /* ------------------------------- Diyaloglar ------------------------------ */
    if (showCancelActiveDialog) {
        AlertDialog(
            onDismissRequest = { showCancelActiveDialog = false },
            title = { Text("Devam eden seansı sonlandır") },
            text = { Text("Aktif antrenman seansını kapatmak istediğine emin misin?") },
            confirmButton = {
                TextButton(onClick = { showCancelActiveDialog = false; vm.cancelActiveWorkout() }) {
                    Text("Evet, sonlandır", color = MaterialTheme.fit.danger)
                }
            },
            dismissButton = { TextButton(onClick = { showCancelActiveDialog = false }) { Text("Vazgeç") } }
        )
    }
    if (showDeloadDesignDialog) {
        DeloadDesignDialog(
            routineName = routine?.name ?: "Aktif program",
            routineDays = days,
            allItems = allItems,
            exercises = exercises,
            allSets = allSets,
            workouts = workouts,
            onDismiss = { showDeloadDesignDialog = false },
            onApplyDeload = { reducePct, customAdjustments ->
                vm.applyDeloadToActiveRoutine(
                    reduceWeightsPercent = reducePct,
                    customAdjustments = customAdjustments
                ) { showDeloadDesignDialog = false }
            }
        )
    }

    fun goTab(route: String) = nav.navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { HomeHeader(name, stats.streakWeeks) { nav.navigate(Routes.PROFILE) } }

        if (deloadRecommendation.shouldDeloadNow || deloadRecommendation.isCurrentlyDeloadWeek) {
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    DeloadAlertBox(
                        recommendation = deloadRecommendation,
                        hasBackup = hasPreDeloadBackup,
                        onToggleDeloadWeek = { on -> vm.setDeloadWeekActive(on) },
                        onOpenDesignDialog = { showDeloadDesignDialog = true },
                        onRestoreOriginalRoutine = { vm.restorePreDeloadRoutine() }
                    )
                }
            }
        }

        /* 1) Bugün kartı */
        item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                val act = active
                when {
                    act != null -> ResumeHero(
                        title = act.title,
                        onResume = { nav.navigate("${Routes.WORKOUT}/${act.id}") },
                        onCancel = { showCancelActiveDialog = true }
                    )
                    nextDay == null -> EmptyHero { goTab(Routes.ROUTINES) }
                    isTrainingDay -> TrainingHero(
                        day = nextDay,
                        items = allItems.filter { it.dayId == nextDay.id },
                        plan = plan,
                        cycleLabel = cycleLabel(deloadRecommendation.currentCycleWeek, deloadRecommendation.recommendedWeek),
                        onStart = { vm.startWorkout(nextDay) { id -> nav.navigate("${Routes.WORKOUT}/$id") } }
                    )
                    doneToday && todayWorkout != null -> DoneHero(
                        workout = todayWorkout,
                        sets = allSets,
                        previous = workouts.filter {
                            it.isFinished && it.id != todayWorkout.id && it.startedAt < todayWorkout.startedAt &&
                                todayWorkout.routineDayId != null && it.routineDayId == todayWorkout.routineDayId
                        }.maxByOrNull { it.startedAt },
                        prNames = prs.filter { it.workoutId == todayWorkout.id }
                            .mapNotNull { pr -> exercises.firstOrNull { it.id == pr.exerciseId }?.name }
                            .distinct(),
                        next = nextDay,
                        onOpen = { nav.navigate("${Routes.WORKOUT_DETAIL}/${todayWorkout.id}") }
                    )
                    else -> RestHero(
                        day = nextDay,
                        items = allItems.filter { it.dayId == nextDay.id },
                        plan = plan,
                        conflicts = recoveryConflicts(plan, recovery, nextSessionMillis(nextDay, workouts)),
                        onFree = { vm.startWorkout(null) { id -> nav.navigate("${Routes.WORKOUT}/$id") } },
                        onStartNow = { vm.startWorkout(nextDay) { id -> nav.navigate("${Routes.WORKOUT}/$id") } }
                    )
                }
            }
        }

        /* 2) Bu hafta */
        item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                WeekCard(workouts = workouts, sets = allSets, planned = plannedWeekdays, today = today)
            }
        }

        /* 3) Toparlanma */
        if (recovery.isNotEmpty()) {
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    RecoveryCard(
                        recovery = recovery,
                        todayConflicts = if (isTrainingDay) recoveryConflicts(plan, recovery, System.currentTimeMillis()) else null,
                        onClick = { vm.setStatsTab(1); goTab(Routes.STATS) }
                    )
                }
            }
        }

        /* 4) Son antrenman */
        val last = workouts.filter { it.isFinished }.maxByOrNull { it.startedAt }
        if (last != null) {
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    LastWorkoutCard(
                        workout = last,
                        previous = workouts.filter {
                            it.isFinished && it.id != last.id && it.startedAt < last.startedAt &&
                                last.routineDayId != null && it.routineDayId == last.routineDayId
                        }.maxByOrNull { it.startedAt },
                        sets = allSets,
                        prCount = prs.count { it.workoutId == last.id },
                        onOpen = { nav.navigate("${Routes.WORKOUT_DETAIL}/${last.id}") },
                        onHistory = { nav.navigate(Routes.HISTORY) }
                    )
                }
            }
        }
    }
}

/* --------------------------------- Başlık ---------------------------------- */

@Composable
private fun HomeHeader(name: String, streakWeeks: Int, onProfile: () -> Unit) {
    Row(
        Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                formatWeekday(System.currentTimeMillis()) + " · " + dayMonth(System.currentTimeMillis()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.fit.muted
            )
            Spacer(Modifier.height(3.dp))
            Text(
                greeting() + if (name.isNotBlank()) ", ${name.trim()}" else "",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (streakWeeks > 0) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Palette.gold.copy(alpha = 0.12f))
                    .padding(start = 9.dp, end = 11.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.LocalFireDepartment, null, tint = Palette.gold, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("$streakWeeks hf", style = MaterialTheme.typography.labelLarge, color = Palette.gold, maxLines = 1)
            }
            Spacer(Modifier.width(10.dp))
        }
        ProfileAvatar(name, 40.dp, onProfile)
    }
}

/** İsmin baş harfleriyle yuvarlak avatar; tasarımda ⚙ yerine Profil'i açar. */
@Composable
fun ProfileAvatar(name: String, size: androidx.compose.ui.unit.Dp, onClick: (() -> Unit)? = null) {
    val dark = MaterialTheme.fit.isDark
    val base = Modifier
        .size(size)
        .clip(androidx.compose.foundation.shape.CircleShape)
        .background(
            Brush.linearGradient(
                if (dark) listOf(Color(0xFF2B3442), Color(0xFF1A2029))
                else listOf(Color(0xFFE3E8EF), Color(0xFFCCD4DE))
            )
        )
        .border(1.dp, Color.White.copy(alpha = if (dark) 0.10f else 0.6f), androidx.compose.foundation.shape.CircleShape)
    Box(
        if (onClick != null) base.clickable { onClick() } else base,
        contentAlignment = Alignment.Center
    ) {
        Text(
            initialsOf(name),
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.34f).sp
            ),
            color = if (dark) Color(0xFFDDE3EA) else Color(0xFF26303C)
        )
    }
}

/** "Nesimi Kaya" → "NK", "Nesimi" → "NE". */
fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val tr = java.util.Locale("tr")
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase(tr)
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase(tr)
    }
}

/* ------------------------------- Bugün kartları ------------------------------ */

@Composable
private fun HeroFrame(accent: Color, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    0f to accent.copy(alpha = 0.16f),
                    0.4f to accent.copy(alpha = 0.04f),
                    0.75f to MaterialTheme.colorScheme.surface
                )
            )
            .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(26.dp))
            .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 18.dp),
        content = content
    )
}

@Composable
private fun Overline(text: String, color: Color) {
    Text(text.uppercase(java.util.Locale("tr")), style = MaterialTheme.typography.labelSmall.overline(), color = color)
}

@Composable
private fun TrainingHero(
    day: RoutineDayEntity,
    items: List<RoutineItemEntity>,
    plan: List<Pair<String, Prescription>>,
    cycleLabel: String?,
    onStart: () -> Unit
) {
    val accent = MaterialTheme.fit.accent
    HeroFrame(accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Overline("Bugün · ${day.name}", accent) }
            if (cycleLabel != null) {
                Text(cycleLabel, style = MaterialTheme.typography.labelMedium.mono(), color = MaterialTheme.fit.muted)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            day.focus.ifBlank { day.name },
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(dayMeta(items), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
        if (plan.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            plan.forEachIndexed { i, (n, rx) -> PlanRow(n, rx, divider = i > 0) }
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Antrenmanı başlat", accent, onStart)
    }
}

@Composable
private fun RestHero(
    day: RoutineDayEntity,
    items: List<RoutineItemEntity>,
    plan: List<Pair<String, Prescription>>,
    conflicts: List<String>,
    onFree: () -> Unit,
    onStartNow: () -> Unit
) {
    val tone = Color(0xFFA5B4FC)
    HeroFrame(tone) {
        Overline("Bugün · dinlenme günü", tone)
        Spacer(Modifier.height(8.dp))
        Text(
            "Sıradaki: " + if (day.weekday in 1..7) weekdayName(day.weekday) else day.name,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
        )
        Text(
            listOf(day.name, day.focus).filter { it.isNotBlank() }.joinToString(" · ") + " · " + dayMeta(items),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.fit.muted,
            maxLines = 2
        )
        if (plan.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            plan.take(2).forEachIndexed { i, (n, rx) -> PlanRow(n, rx, divider = i > 0) }
            if (plan.size > 2) {
                Text(
                    "+${plan.size - 2} hareket",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.fit.muted,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        if (conflicts.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            WarningNote(
                conflicts.joinToString(", ") + " o seansa kadar tam toparlanmayabilir. " +
                    "İlgili hareketlerde son setleri RPE 8'de bırakmayı düşünebilirsin."
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Serbest", Modifier.weight(1f), onFree)
            Box(Modifier.weight(1.6f)) { PrimaryButton("Şimdi başla", MaterialTheme.fit.accent, onStartNow) }
        }
    }
}

@Composable
private fun ResumeHero(title: String, onResume: () -> Unit, onCancel: () -> Unit) {
    val c = MaterialTheme.fit.success
    HeroFrame(c) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline("Devam eden seans", c)
            Spacer(Modifier.weight(1f))
            RoundIconButton(Icons.Default.Close, MaterialTheme.fit.muted, 32.dp, MaterialTheme.fit.elevated) { onCancel() }
        }
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), maxLines = 2)
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Seansa dön", c, onResume)
    }
}

@Composable
private fun EmptyHero(onOpenProgram: () -> Unit) {
    val accent = MaterialTheme.fit.accent
    HeroFrame(accent) {
        Overline("Başlarken", accent)
        Spacer(Modifier.height(4.dp))
        Text("Henüz program yok", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Text(
            "Bir program oluştur; her seans hedef ağırlık ve tekrarlarla hazır açılsın.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.fit.muted
        )
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Program oluştur", accent, onOpenProgram)
    }
}

@Composable
private fun DoneHero(
    workout: WorkoutEntity,
    sets: List<WorkoutSetEntity>,
    previous: WorkoutEntity?,
    prNames: List<String>,
    next: RoutineDayEntity?,
    onOpen: () -> Unit
) {
    val c = MaterialTheme.fit.success
    val mine = sets.filter { it.workoutId == workout.id && Analytics.isEffectiveSet(it) }
    val vol = mine.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat()
    val prevVol = previous?.let { p ->
        sets.filter { it.workoutId == p.id && Analytics.isEffectiveSet(it) }.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat()
    } ?: 0f
    val delta = if (prevVol > 0f) Math.round((vol - prevVol) / prevVol * 100f) else null
    val minutes = (workout.durationSeconds.takeIf { it > 0 }
        ?: workout.finishedAt?.let { ((it - workout.startedAt) / 1000L).toInt() } ?: 0) / 60
    Box(Modifier.clickable { onOpen() }) {
        HeroFrame(c) {
            Overline("Bugün · tamamlandı", c)
            Spacer(Modifier.height(8.dp))
            Text(
                "${workout.title} bitti",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(if (minutes > 0) "$minutes dk" else null, "${mine.size} set", formatTonnage(vol)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.fit.muted
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DoneTile(
                    if (delta == null) "—" else "${if (delta >= 0) "+" else ""}$delta%",
                    "hacim",
                    if (delta != null && delta < 0) MaterialTheme.fit.warning else MaterialTheme.colorScheme.onSurface,
                    Modifier.weight(1f)
                )
                DoneTile(
                    "${prNames.size} PR",
                    prNames.firstOrNull() ?: "rekor",
                    if (prNames.isNotEmpty()) Palette.gold else MaterialTheme.colorScheme.onSurface,
                    Modifier.weight(1f)
                )
                DoneTile(
                    next?.takeIf { it.weekday in 1..7 }?.let { SHORT_DAYS[it.weekday - 1] } ?: "—",
                    "sıradaki",
                    MaterialTheme.colorScheme.onSurface,
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DoneTile(value: String, label: String, valueColor: Color, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .padding(10.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium.mono().copy(fontWeight = FontWeight.SemiBold), color = valueColor, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Mezosiklus haftası: "HAFTA 3 / 5". Bilgi yoksa null. */
private fun cycleLabel(week: Int, length: Int): String? =
    if (week > 0 && length > 0) "HAFTA ${week.coerceAtMost(length)} / $length" else null

@Composable
private fun PlanRow(name: String, rx: Prescription, divider: Boolean) {
    if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)))
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            actionArrow(rx.action),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = actionColor(rx.action),
            modifier = Modifier.width(22.dp)
        )
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (rx.action == ProgressAction.FIRST) "ilk kayıt" else rx.headline,
            style = if (rx.action == ProgressAction.FIRST) MaterialTheme.typography.labelLarge
            else MaterialTheme.typography.titleSmall.mono().copy(fontWeight = FontWeight.SemiBold),
            color = if (rx.action == ProgressAction.FIRST) MaterialTheme.fit.muted else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun PrimaryButton(text: String, color: Color, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = color,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            Modifier.padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val on = onColorFor(color)
            Icon(Icons.Default.PlayArrow, null, tint = on, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, color = on, maxLines = 1)
        }
    }
}

@Composable
private fun SecondaryButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.fit.elevated)
            .clickable { onClick() }
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
private fun WarningNote(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MuscleColors.recovering.copy(alpha = 0.12f))
            .border(1.dp, MuscleColors.recovering.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Warning, null, tint = MuscleColors.recovering, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MuscleColors.recovering, modifier = Modifier.weight(1f))
    }
}

/* ---------------------------------- Bu hafta --------------------------------- */

@Composable
private fun WeekCard(workouts: List<WorkoutEntity>, sets: List<WorkoutSetEntity>, planned: Set<Int>, today: Int) {
    val weekStart = startOfWeek(System.currentTimeMillis())
    val weekWorkouts = workouts.filter { it.isFinished && it.startedAt >= weekStart }
    val done = weekWorkouts.map { weekdayOf(it.startedAt) }.toSet()
    val ids = weekWorkouts.map { it.id }.toSet()
    val volume = sets.filter { it.workoutId in ids && Analytics.isEffectiveSet(it) }
        .sumOf { (it.weightKg * it.reps).toDouble() }.toFloat()
    val goal = planned.size.takeIf { it > 0 } ?: 3

    FitCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Bu hafta", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            Text(
                "${weekWorkouts.size} / $goal antrenman · ${formatTonnage(volume)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.fit.muted
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (1..7).forEach { d ->
                val isDone = d in done
                val isToday = d == today
                val isPlanned = d in planned
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        SHORT_DAYS[d - 1].uppercase(java.util.Locale("tr")),
                        style = MaterialTheme.typography.labelSmall.mono(),
                        color = if (isToday) MaterialTheme.fit.accent else MaterialTheme.fit.muted
                    )
                    Spacer(Modifier.height(5.dp))
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(shape)
                            .background(
                                when {
                                    isDone -> MaterialTheme.fit.success
                                    isPlanned -> Color.Transparent
                                    else -> MaterialTheme.fit.elevated
                                }
                            )
                            .then(
                                when {
                                    isDone -> Modifier
                                    isToday -> Modifier.border(2.dp, MaterialTheme.fit.accent, shape)
                                    isPlanned -> Modifier.border(1.5.dp, MaterialTheme.fit.muted.copy(alpha = 0.5f), shape)
                                    else -> Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isDone -> Icon(Icons.Default.Check, null, tint = Color(0xFF062017), modifier = Modifier.size(19.dp))
                            isToday -> Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.fit.accent))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.fit.elevated)) {
            Box(
                Modifier
                    .fillMaxWidth((weekWorkouts.size.toFloat() / goal).coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.fit.accent)
            )
        }
    }
}

/* -------------------------------- Toparlanma -------------------------------- */

@Composable
private fun RecoveryCard(recovery: Map<String, MuscleRecovery>, todayConflicts: List<String>?, onClick: () -> Unit) {
    val colors = remember(recovery) { recovery.mapValues { (_, r) -> MuscleColors.forRecovery(r.state) } }
    val notReady = recovery.values.filter { it.state != RecoveryState.FRESH }.sortedBy { it.readiness }
    FitCard(onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Toparlanma", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            when {
                todayConflicts == null -> Unit
                todayConflicts.isEmpty() -> Text("Bugünkü programa engel yok", style = MaterialTheme.typography.labelMedium, color = MuscleColors.fresh)
                else -> Text("⚠ ${todayConflicts.joinToString(", ")}", style = MaterialTheme.typography.labelMedium, color = MuscleColors.recovering, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(8.dp))
        BodyMuscleMapPair(colors = colors, height = 220.dp, showLabels = false)
        Spacer(Modifier.height(8.dp))
        if (notReady.isEmpty()) {
            Text("Tüm kasların toparlanmış görünüyor.", style = MaterialTheme.typography.bodySmall, color = MuscleColors.fresh)
        } else {
            notReady.take(3).forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(MuscleColors.forRecovery(r.state)))
                    Spacer(Modifier.width(8.dp))
                    Text(MuscleMap.label(r.key), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(
                        "%${(r.readiness * 100).toInt()} · ${readyText(r.readyAt)}",
                        style = MaterialTheme.typography.labelMedium.mono(),
                        color = MaterialTheme.fit.muted
                    )
                }
            }
        }
    }
}

/* ------------------------------- Son antrenman ------------------------------- */

@Composable
private fun LastWorkoutCard(
    workout: WorkoutEntity,
    previous: WorkoutEntity?,
    sets: List<WorkoutSetEntity>,
    prCount: Int,
    onOpen: () -> Unit,
    onHistory: () -> Unit
) {
    val mine = sets.filter { it.workoutId == workout.id && Analytics.isEffectiveSet(it) }
    val vol = mine.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat()
    val prevVol = previous?.let { p ->
        sets.filter { it.workoutId == p.id && Analytics.isEffectiveSet(it) }.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat()
    } ?: 0f
    val delta = if (prevVol > 0f) Math.round((vol - prevVol) / prevVol * 100f) else null
    val minutes = (workout.durationSeconds.takeIf { it > 0 }
        ?: workout.finishedAt?.let { ((it - workout.startedAt) / 1000L).toInt() } ?: 0) / 60

    FitCard(onClick = onOpen, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.fit.elevated),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.History, null, tint = MaterialTheme.fit.muted, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    workout.title + " · " + formatWeekday(workout.startedAt),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row {
                    Text(
                        listOfNotNull(
                            if (minutes > 0) "$minutes dk" else null,
                            "${mine.size} set",
                            formatTonnage(vol)
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.fit.muted
                    )
                    if (delta != null) {
                        Text(
                            " · ${if (delta >= 0) "+" else ""}%$delta hacim",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (delta >= 0) MaterialTheme.fit.success else MaterialTheme.fit.warning
                        )
                    }
                }
            }
            if (prCount > 0) {
                Surface(shape = RoundedCornerShape(8.dp), color = Palette.gold.copy(alpha = 0.15f)) {
                    Text(
                        "$prCount PR",
                        style = MaterialTheme.typography.labelMedium.mono().copy(fontWeight = FontWeight.Bold),
                        color = Palette.gold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Tüm geçmiş →",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.fit.accent,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().clickable { onHistory() }
        )
    }
}

/* --------------------------------- Yardımcılar ------------------------------- */

private val SHORT_DAYS = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")

private fun actionArrow(a: ProgressAction): String = when (a) {
    ProgressAction.INCREASE -> "↑"
    ProgressAction.REPS -> "↗"
    ProgressAction.DECREASE, ProgressAction.DELOAD -> "↓"
    else -> "→"
}

/** Planlanan günün seans sayısı, set toplamı ve tahmini süresi. */
private fun dayMeta(items: List<RoutineItemEntity>): String {
    val work = items.filter { !it.isWarmup }
    val sets = work.sumOf { it.targetSets }
    val minutes = 8 + items.sumOf { it.targetSets * (it.restSeconds + 40) } / 60
    return "${work.size} hareket · $sets set · ~${(minutes / 5) * 5} dk"
}

/** Program gününün ana kaslarından, verilen anda henüz toparlanmamış olanlar. */
private fun recoveryConflicts(
    plan: List<Pair<String, Prescription>>,
    recovery: Map<String, MuscleRecovery>,
    atMillis: Long
): List<String> {
    val primary = plan.flatMap { (n, _) ->
        MuscleMap.resolve(n).weights().filterValues { it >= 0.75f }.keys
    }.toSet()
    return primary.mapNotNull { m ->
        val r = recovery[m] ?: return@mapNotNull null
        val notReady = if (atMillis <= System.currentTimeMillis() + 60_000L) r.state != RecoveryState.FRESH
        else r.readyAt > atMillis
        if (notReady) MuscleMap.label(m) else null
    }.sorted()
}

/** Sıradaki planlı gün: bugünden sonraki ilk haftalık gün; hiç gün atanmamışsa en uzun süredir yapılmayan. */
private fun nextPlannedDay(days: List<RoutineDayEntity>, workouts: List<WorkoutEntity>, today: Int): RoutineDayEntity? {
    if (days.isEmpty()) return null
    val withDay = days.filter { it.weekday in 1..7 }
    if (withDay.isNotEmpty()) {
        return withDay.minByOrNull { ((it.weekday - today + 7) % 7).let { d -> if (d == 0) 7 else d } }
    }
    val lastByDay = workouts.filter { it.routineDayId != null }
        .groupBy { it.routineDayId!! }
        .mapValues { e -> e.value.maxOf { it.startedAt } }
    return days.minByOrNull { lastByDay[it.id] ?: 0L }
}

/** Sıradaki seansın tahmini başlangıcı: o günün, son antrenmanların tipik saatinde. */
private fun nextSessionMillis(day: RoutineDayEntity, workouts: List<WorkoutEntity>): Long {
    val cal = Calendar.getInstance()
    val hour = workouts.filter { it.isFinished }.sortedByDescending { it.startedAt }.take(6)
        .map { Calendar.getInstance().apply { timeInMillis = it.startedAt }.get(Calendar.HOUR_OF_DAY) }
        .sorted().let { if (it.isEmpty()) 18 else it[it.size / 2] }
    val today = todayWeekday()
    val ahead = if (day.weekday in 1..7) ((day.weekday - today + 7) % 7).let { if (it == 0) 7 else it } else 1
    cal.add(Calendar.DAY_OF_YEAR, ahead)
    cal.set(Calendar.HOUR_OF_DAY, hour)
    cal.set(Calendar.MINUTE, 0)
    return cal.timeInMillis
}

private fun readyText(at: Long): String {
    if (at <= 0L) return "şimdi"
    val dayDiff = Math.round((startOfDay(at) - startOfDay(System.currentTimeMillis())) / 86_400_000.0).toInt()
    return when (dayDiff) {
        0 -> "bugün ${formatTime(at)}"
        1 -> "yarın ${formatTime(at)}"
        else -> formatWeekday(at) + " " + formatTime(at)
    }
}

private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Pazartesi = 1 … Pazar = 7 */
private fun weekdayOf(millis: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
}

private fun dayMonth(millis: Long): String = com.example.core.formatDate(millis).split(" ").take(2).joinToString(" ")

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Günaydın"
    in 12..17 -> "İyi günler"
    in 18..22 -> "İyi akşamlar"
    else -> "İyi geceler"
}
