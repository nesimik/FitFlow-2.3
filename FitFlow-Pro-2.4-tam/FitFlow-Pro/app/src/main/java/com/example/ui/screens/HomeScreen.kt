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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.MonitorWeight
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
import com.example.core.trimNum
import com.example.core.label
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
import com.example.ui.components.DeloadCard
import com.example.ui.components.FitCard
import com.example.ui.components.MuscleColors
import com.example.ui.components.RoundIconButton
import com.example.ui.theme.Palette
import com.example.ui.theme.onColorFor
import com.example.ui.theme.fit
import com.example.ui.components.cardBackground
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
    val weekLoads by vm.weeklyMuscleLoads.collectAsStateWithLifecycle()
    val weekDetail by vm.weeklyDetailLoads.collectAsStateWithLifecycle()
    val progressIds by vm.progressExerciseIds.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val updateDismissed by vm.updateDismissed.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showCancelActiveDialog by remember { mutableStateOf(false) }
    var hasPreDeloadBackup by remember { mutableStateOf(vm.settings.getPreDeloadBackup().isNotBlank()) }

    val today = todayWeekday()
    val plannedWeekdays = remember(days) { days.map { it.weekday }.filter { it in 1..7 }.toSet() }
    // 2.32: haftanın gününe bağlı olmayan (A/B dönüşümlü) programlarda "bugün" sıradaki gündür.
    val rotation = days.isNotEmpty() && days.none { it.weekday in 1..7 }
    val todayDay = remember(days, today, workouts, rotation) {
        if (!rotation) days.firstOrNull { it.weekday == today }
        else {
            val dayStart = startOfDay(System.currentTimeMillis())
            val doneId = workouts.filter { it.isFinished && it.startedAt >= dayStart }.maxByOrNull { it.startedAt }?.routineDayId
            days.firstOrNull { it.id == doneId } ?: nextPlannedDay(days, workouts, today)
        }
    }
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
        val act = active
        if (act == null) showCancelActiveDialog = false
        else com.example.ui.components.FinishSessionDialog(
            title = act.title,
            summary = null,
            onSave = {
                showCancelActiveDialog = false
                vm.requestFinishOnEnter()
                nav.navigate("${Routes.WORKOUT}/${act.id}")
            },
            onDiscard = { showCancelActiveDialog = false; vm.discardWorkout {} },
            onDismiss = { showCancelActiveDialog = false }
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

        /* Güncelleme (2.30) */
        if (update != null && !updateDismissed) {
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    UpdateBanner(update!!, onInstall = { com.example.ui.AppUpdate.download(context, update!!.apkUrl) }, onLater = { vm.dismissUpdate() })
                }
            }
        }

        if (deloadRecommendation.shouldDeloadNow || deloadRecommendation.isCurrentlyDeloadWeek || hasPreDeloadBackup) {
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    DeloadCard(
                        recommendation = deloadRecommendation,
                        hasBackup = hasPreDeloadBackup,
                        onStartDeload = { vm.startDeloadWeek() },
                        onEndDeload = { vm.endDeloadWeek() },
                        onDismissChange = { vm.setDeloadDismissed(it) },
                        onRestoreBackup = { vm.restoreRoutineBackupOnly { hasPreDeloadBackup = false } }
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
                        cycleLabel = cycleLabel(deloadRecommendation),
                        onStart = { vm.startWorkout(nextDay) { id -> nav.navigate("${Routes.WORKOUT}/$id") } }
                    )
                    doneToday && todayWorkout != null -> DoneHero(
                        workout = todayWorkout,
                        sets = allSets,
                        // Deload seansı önceki haftayla kıyaslanmaz; kıyas her zaman son normal seansla.
                        previous = if (todayWorkout.isDeload) null else workouts.filter {
                            it.isFinished && !it.isDeload && it.id != todayWorkout.id && it.startedAt < todayWorkout.startedAt &&
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

        /* 1b) Hızlı erişim */
        item {
            QuickAccessRow(
                onFree = { vm.startWorkout(null) { id -> nav.navigate("${Routes.WORKOUT}/$id") } },
                onMeasure = { nav.navigate(Routes.BODY) },
                onHistory = { nav.navigate(Routes.HISTORY) },
                onTools = { nav.navigate(Routes.TOOLS) }
            )
        }

        /* 2) Bu hafta */
        item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                WeekCard(workouts = workouts, sets = allSets, planned = plannedWeekdays, today = today)
            }
        }

        /* 3) Toparlanma / Kas dengesi */
        if (recovery.isNotEmpty() || weekLoads.isNotEmpty()) {
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    BodyStatusCard(
                        recovery = recovery,
                        loads = weekLoads,
                        detail = weekDetail,
                        todayConflicts = if (isTrainingDay) recoveryConflicts(plan, recovery, System.currentTimeMillis()) else null,
                        onOpen = { vm.setStatsTab(1); goTab(Routes.STATS) }
                    )
                }
            }
        }

        /* 4) Dört haftalık özet (2.27) */
        if (workouts.any { it.isFinished }) {
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    SummaryCard(workouts = workouts, sets = allSets, prs = prs, visibleIds = progressIds, onOpen = { vm.setStatsTab(0); goTab(Routes.STATS) })
                }
            }
        }

        /* 5) Son antrenmanlar */
        val recent = workouts.filter { it.isFinished }.sortedByDescending { it.startedAt }.take(3)
        if (recent.isNotEmpty()) {
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    RecentWorkoutsCard(
                        recent = recent,
                        all = workouts,
                        sets = allSets,
                        prCountOf = { id -> prs.count { it.workoutId == id } },
                        onOpen = { id -> nav.navigate("${Routes.WORKOUT_DETAIL}/$id") },
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
        Text(dayMeta(items, plan.any { it.second.action == ProgressAction.DELOAD }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
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
            listOf(day.name, day.focus).filter { it.isNotBlank() }.joinToString(" · ") + " · " + dayMeta(items, plan.any { it.second.action == ProgressAction.DELOAD }),
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
    val vol = mine.sumOf { it.load.toDouble() }.toFloat()
    val prevVol = previous?.let { p ->
        sets.filter { it.workoutId == p.id && Analytics.isEffectiveSet(it) }.sumOf { it.load.toDouble() }.toFloat()
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
/** "HAFTA 3/5 · RIR 1-2" — blok içindeki yer ve hedef zorluk. */
private fun cycleLabel(rec: com.example.core.DeloadAdvisor.DeloadRecommendation): String? {
    if (rec.currentCycleWeek <= 0) return null
    val effort = com.example.core.TrainingBlock.effort(rec.currentCycleWeek, rec.loadWeeks, rec.isCurrentlyDeloadWeek)
    return if (rec.isCurrentlyDeloadWeek) "DELOAD · ${effort.rir}"
    else "HAFTA ${rec.currentCycleWeek.coerceAtMost(rec.loadWeeks)}/${rec.loadWeeks} · ${effort.rir}"
}

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
        .sumOf { it.load.toDouble() }.toFloat()
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
    val colors = remember(recovery) { recovery.filterValues { it.state != RecoveryState.FRESH }.mapValues { (_, r) -> MuscleColors.forReadiness(r.readiness) } }
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
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(MuscleColors.forReadiness(r.readiness).copy(alpha = 1f)))
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

/* ------------------------------- Hızlı erişim ------------------------------- */

@Composable
private fun QuickAccessRow(onFree: () -> Unit, onMeasure: () -> Unit, onHistory: () -> Unit, onTools: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickTile(Icons.Default.Bolt, "Serbest", MaterialTheme.fit.accent, Modifier.weight(1f), onFree)
        QuickTile(Icons.Default.MonitorWeight, "Ölçüm", MuscleColors.fresh, Modifier.weight(1f), onMeasure)
        QuickTile(Icons.Default.History, "Geçmiş", Color(0xFFA5B4FC), Modifier.weight(1f), onHistory)
        QuickTile(Icons.Default.Calculate, "Hesapla", Palette.gold, Modifier.weight(1f), onTools)
    }
}

@Composable
private fun QuickTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .then(Modifier.cardBackground())
            .border(1.dp, MaterialTheme.fit.cardBorder, shape)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

/* ------------------------- Toparlanma / Kas dengesi ------------------------- */

@Composable
private fun BodyStatusCard(
    recovery: Map<String, MuscleRecovery>,
    loads: List<com.example.core.MuscleLoad>,
    detail: List<com.example.core.MuscleLoad>,
    todayConflicts: List<String>?,
    onOpen: () -> Unit
) {
    var tab by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(if (recovery.isEmpty()) 1 else 0) }
    val recColors = remember(recovery) { recovery.filterValues { it.state != RecoveryState.FRESH }.mapValues { (_, r) -> MuscleColors.forReadiness(r.readiness) } }
    val loadColors = remember(loads, detail) {
        (loads + detail).mapNotNull { l -> MuscleColors.forStatus(l.status)?.let { l.key to it } }.toMap()
    }
    FitCard(onClick = onOpen, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .padding(3.dp)
            ) {
                listOf("Toparlanma", "Kas dengesi").forEachIndexed { i, label ->
                    val on = tab == i
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.fit.muted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (on) MaterialTheme.fit.elevated else Color.Transparent)
                            .clickable { tab = i }
                            .padding(horizontal = 11.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (tab == 0) {
                when {
                    todayConflicts == null -> Unit
                    todayConflicts.isEmpty() -> Text("Engel yok", style = MaterialTheme.typography.labelMedium, color = MuscleColors.fresh)
                    else -> Text("⚠ ${todayConflicts.size} kas", style = MaterialTheme.typography.labelMedium, color = MuscleColors.recovering)
                }
            } else {
                Text("bu hafta · etkin set", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.muted)
            }
        }
        Spacer(Modifier.height(10.dp))
        var sel by remember { mutableStateOf<String?>(null) }
        BodyMuscleMapPair(
            colors = if (tab == 0) recColors else loadColors,
            height = 220.dp,
            showLabels = false,
            selected = sel,
            onMuscleTap = { key -> sel = if (sel == key) null else key }
        )
        Spacer(Modifier.height(10.dp))
        val selKey = sel
        if (selKey != null) {
            MuscleInfoPanel(
                key = selKey,
                recovery = if (tab == 0) recovery[selKey] else null,
                load = if (tab == 1) (loads + detail).firstOrNull { it.key == selKey } else null,
                recoveryMode = tab == 0
            )
        } else if (tab == 0) {
            val notReady = recovery.values.filter { it.state != RecoveryState.FRESH }.sortedBy { it.readiness }
            if (todayConflicts != null && todayConflicts.isNotEmpty()) {
                Text(
                    "Bugünkü programda: ${todayConflicts.joinToString(", ")} henüz tam toparlanmadı.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MuscleColors.recovering
                )
                Spacer(Modifier.height(6.dp))
            }
            if (notReady.isEmpty()) {
                Text("Tüm kasların toparlanmış görünüyor.", style = MaterialTheme.typography.bodySmall, color = MuscleColors.fresh)
            } else {
                notReady.take(3).forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(MuscleColors.forReadiness(r.readiness).copy(alpha = 1f)))
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
        } else {
            // Kas dengesi: en eksik 3 kas + aşırı yüklenen varsa uyarı
            val weakest = loads.filter { it.target.first > 0 && (it.status == com.example.core.LoadStatus.LOW || it.status == com.example.core.LoadStatus.BELOW || it.status == com.example.core.LoadStatus.NONE) }
                .sortedBy { it.effectiveSets / it.target.first }
                .take(3)
            val over = loads.filter { it.status == com.example.core.LoadStatus.EXCESSIVE }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
            ) {
                MuscleColors.loadLegend.forEach { (c, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(c))
                        Spacer(Modifier.width(4.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (weakest.isEmpty()) {
                Text("Bu hafta tüm kaslar hedef aralığa yakın.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.success)
            } else {
                weakest.forEach { l ->
                    val c = MuscleColors.forStatus(l.status) ?: MaterialTheme.fit.muted
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(MuscleMap.label(l.key), style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.width(84.dp))
                        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.fit.elevated)) {
                            Box(
                                Modifier
                                    .fillMaxWidth((l.effectiveSets / l.target.first).coerceIn(0f, 1f))
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(c)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${l.effectiveSets.trimNum().replace('.', ',')} / ${l.target.first}",
                            style = MaterialTheme.typography.labelMedium.mono(),
                            color = MaterialTheme.fit.muted
                        )
                    }
                }
            }
            if (over.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Fazla yüklenen: " + over.joinToString(", ") { MuscleMap.label(it.key) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MuscleColors.excessive
                )
            }
        }
    }
}

/* ------------------------------ Son antrenmanlar ----------------------------- */

@Composable
private fun RecentWorkoutsCard(
    recent: List<WorkoutEntity>,
    all: List<WorkoutEntity>,
    sets: List<WorkoutSetEntity>,
    prCountOf: (Long) -> Int,
    onOpen: (Long) -> Unit,
    onHistory: () -> Unit
) {
    FitCard(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Son antrenmanlar", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Text(
                "Tüm geçmiş →",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.fit.accent,
                modifier = Modifier.clickable { onHistory() }
            )
        }
        Spacer(Modifier.height(6.dp))
        recent.forEachIndexed { i, w ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)))
            val mine = sets.filter { it.workoutId == w.id && Analytics.isEffectiveSet(it) }
            val vol = mine.sumOf { it.load.toDouble() }.toFloat()
            val prev = if (w.isDeload) null else all.filter {
                it.isFinished && !it.isDeload && it.id != w.id && it.startedAt < w.startedAt && w.routineDayId != null && it.routineDayId == w.routineDayId
            }.maxByOrNull { it.startedAt }
            val prevVol = prev?.let { p ->
                sets.filter { it.workoutId == p.id && Analytics.isEffectiveSet(it) }.sumOf { it.load.toDouble() }.toFloat()
            } ?: 0f
            val delta = if (prevVol > 0f) Math.round((vol - prevVol) / prevVol * 100f) else null
            val minutes = (w.durationSeconds.takeIf { it > 0 }
                ?: w.finishedAt?.let { ((it - w.startedAt) / 1000L).toInt() } ?: 0) / 60
            val prCount = prCountOf(w.id)
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(w.id) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier.width(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.fit.elevated).padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        SHORT_DAYS[weekdayOf(w.startedAt) - 1].uppercase(java.util.Locale("tr")),
                        style = MaterialTheme.typography.labelSmall.mono(),
                        color = MaterialTheme.fit.muted
                    )
                    Text(
                        Calendar.getInstance().apply { timeInMillis = w.startedAt }.get(Calendar.DAY_OF_MONTH).toString(),
                        style = MaterialTheme.typography.titleSmall.mono().copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(w.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row {
                        Text(
                            listOfNotNull(if (minutes > 0) "$minutes dk" else null, "${mine.size} set", formatTonnage(vol)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.fit.muted,
                            maxLines = 1
                        )
                        if (delta != null) {
                            Text(
                                " · ${if (delta >= 0) "+" else "−"}%${kotlin.math.abs(delta)}",
                                style = MaterialTheme.typography.bodySmall.mono(),
                                color = if (delta >= 0) MaterialTheme.fit.success else MaterialTheme.fit.warning,
                                maxLines = 1
                            )
                        }
                    }
                }
                if (prCount > 0) {
                    Text(
                        "$prCount PR",
                        style = MaterialTheme.typography.labelMedium.mono().copy(fontWeight = FontWeight.Bold),
                        color = Palette.gold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Palette.gold.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
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
    val vol = mine.sumOf { it.load.toDouble() }.toFloat()
    val prevVol = previous?.let { p ->
        sets.filter { it.workoutId == p.id && Analytics.isEffectiveSet(it) }.sumOf { it.load.toDouble() }.toFloat()
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
private fun dayMeta(items: List<RoutineItemEntity>, deload: Boolean = false): String {
    fun n(i: RoutineItemEntity) = if (deload) maxOf(1, (i.targetSets + 1) / 2) else i.targetSets
    val work = items.filter { !it.isWarmup }
    val sets = work.sumOf { n(it) }
    val minutes = 8 + items.sumOf { n(it) * (it.restSeconds + 40) } / 60
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

/** Haritada dokunulan kasın bilgisi (toparlanma ya da haftalık yük). */
@Composable
private fun MuscleInfoPanel(
    key: String,
    recovery: MuscleRecovery?,
    load: com.example.core.MuscleLoad?,
    recoveryMode: Boolean
) {
    val color = when {
        recoveryMode && recovery != null && recovery.state != RecoveryState.FRESH -> MuscleColors.forReadiness(recovery.readiness).copy(alpha = 1f)
        recoveryMode -> MuscleColors.fresh
        load != null -> MuscleColors.forStatus(load.status) ?: MaterialTheme.fit.muted
        else -> MaterialTheme.fit.muted
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(color))
            Spacer(Modifier.width(8.dp))
            Text(MuscleMap.label(key), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (recoveryMode) {
                val pct = ((recovery?.readiness ?: 1f) * 100).toInt().coerceAtMost(100)
                Text("%$pct hazır", style = MaterialTheme.typography.labelLarge.mono(), color = color)
            } else if (load != null) {
                Text("${load.effectiveSets.trimNum().replace('.', ',')} set", style = MaterialTheme.typography.labelLarge.mono(), color = color)
            }
        }
        Spacer(Modifier.height(4.dp))
        val line = if (recoveryMode) {
            when {
                recovery == null || recovery.lastTrainedAt <= 0L -> "Son günlerde çalışılmadı · dinç"
                recovery.state == RecoveryState.FRESH -> "Dinç · son çalışma ${agoText(recovery.lastTrainedAt)}"
                else -> "Tam hazır: ${readyText(recovery.readyAt)} · son çalışma ${agoText(recovery.lastTrainedAt)} · ${recovery.recentSets.trimNum().replace('.', ',')} etkin set"
            }
        } else {
            if (load == null) "Bu hafta çalışılmadı"
            else "Hedef ${load.target.first}–${load.target.last} etkin set · ${load.status.label()}"
        }
        Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
    }
}

private fun agoText(at: Long): String {
    val days = Math.round((startOfDay(System.currentTimeMillis()) - startOfDay(at)) / 86_400_000.0).toInt()
    return when {
        days <= 0 -> "bugün"
        days == 1 -> "dün"
        else -> "$days gün önce"
    }
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


/* ------------------------------ Dört haftalık özet ------------------------------ */

private const val WEEK_MS = 7L * 24 * 3600 * 1000

/**
 * Son 28 gün, önceki 28 günle karşılaştırmalı: antrenman, hacim, rekor, ortalama süre;
 * 8 haftalık hacim çubukları ve dönemin en çok gelişen hareketi.
 */
@Composable
private fun SummaryCard(
    workouts: List<WorkoutEntity>,
    sets: List<WorkoutSetEntity>,
    prs: List<com.example.data.PrEntity>,
    visibleIds: Set<Long>?,
    onOpen: () -> Unit
) {
    val data = remember(workouts, sets, prs, visibleIds) {
        val now = System.currentTimeMillis()
        val weekStart = startOfWeek(now)
        val from = now - 4 * WEEK_MS
        val prevFrom = now - 8 * WEEK_MS
        val done = workouts.filter { it.isFinished }
        val cur = done.filter { it.startedAt >= from }
        val prev = done.filter { it.startedAt in prevFrom until from }
        val byW = sets.filter { Analytics.isEffectiveSet(it) }.groupBy { it.workoutId }
        fun vol(ws: List<WorkoutEntity>) = ws.sumOf { w -> byW[w.id].orEmpty().sumOf { it.load.toDouble() } }.toFloat()
        fun mins(ws: List<WorkoutEntity>) = ws.map { w ->
            (w.durationSeconds.takeIf { it > 0 } ?: w.finishedAt?.let { ((it - w.startedAt) / 1000L).toInt() } ?: 0) / 60
        }.filter { it > 0 }.let { if (it.isEmpty()) 0 else it.average().toInt() }
        val curIds = cur.map { it.id }.toSet()
        val prevIds = prev.map { it.id }.toSet()
        // 8 haftalık hacim (bu hafta dahil)
        val weeks = (7 downTo 0).map { i ->
            val a = weekStart - i * WEEK_MS
            vol(done.filter { it.startedAt >= a && it.startedAt < a + WEEK_MS })
        }
        // En çok gelişen hareket: dönem en iyi e1RM − önceki dönem en iyi e1RM
        fun best(ids: Set<Long>) = sets.asSequence()
            .filter { it.workoutId in ids && Analytics.isEffectiveSet(it) && it.weightKg > 0f && it.reps in 1..12 }
            .filter { visibleIds == null || it.exerciseId in visibleIds }
            .groupBy { it.exerciseId }
            .mapValues { (_, l) -> l.maxOf { com.example.core.Calc.e1rm(it.weightKg, it.reps) } to l.first().exerciseName }
        val bc = best(curIds); val bp = best(prevIds)
        val top = bc.mapNotNull { (id, v) -> bp[id]?.let { p -> Triple(v.second, v.first - p.first, v.first) } }
            .filter { it.second >= 1f }.maxByOrNull { it.second / it.third }
        SummaryData(
            cur.size, prev.size, vol(cur), vol(prev),
            prs.count { it.workoutId in curIds }, mins(cur), weeks, top?.first, top?.second
        )
    }
    val accent = MaterialTheme.fit.accent
    FitCard(onClick = onOpen, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Son 4 hafta", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Text("önceki 4 haftaya göre", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
            }
            val dv = pct(data.volume, data.prevVolume)
            if (dv != null) DeltaPill(dv)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            SummaryMetric("${data.workouts}", "antrenman", pct(data.workouts.toFloat(), data.prevWorkouts.toFloat()), Modifier.weight(1f))
            SummaryDivider()
            SummaryMetric(formatTonnage(data.volume), "hacim", null, Modifier.weight(1f))
            SummaryDivider()
            SummaryMetric("${data.prCount}", "rekor", null, Modifier.weight(1f), if (data.prCount > 0) Palette.gold else null)
            SummaryDivider()
            SummaryMetric(if (data.avgMin > 0) "${data.avgMin}" else "—", "dk / seans", null, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        // 8 haftalık hacim çubukları
        val maxV = (data.weeks.maxOrNull() ?: 0f).coerceAtLeast(1f)
        val muted = MaterialTheme.fit.elevated
        Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            data.weeks.forEachIndexed { i, v ->
                val last = i == data.weeks.lastIndex
                Box(
                    Modifier.weight(1f).fillMaxHeight((v / maxV).coerceIn(0.06f, 1f))
                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                        .background(
                            when {
                                last -> accent
                                i >= data.weeks.size - 4 -> accent.copy(alpha = 0.45f)
                                else -> muted
                            }
                        )
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("8 hafta önce", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted, modifier = Modifier.weight(1f))
            Text("bu hafta", style = MaterialTheme.typography.labelSmall, color = accent)
        }
        if (data.topName != null && data.topGain != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.fit.success.copy(alpha = 0.10f))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = MaterialTheme.fit.success, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "En çok gelişen: ${data.topName}",
                    style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Text("1RM +${data.topGain.trimNum()} kg", style = MaterialTheme.typography.labelMedium.mono(), color = MaterialTheme.fit.success)
            }
        }
    }
}

private data class SummaryData(
    val workouts: Int, val prevWorkouts: Int, val volume: Float, val prevVolume: Float,
    val prCount: Int, val avgMin: Int, val weeks: List<Float>, val topName: String?, val topGain: Float?
)

private fun pct(cur: Float, prev: Float): Int? = if (prev <= 0f) null else Math.round((cur - prev) / prev * 100f)

@Composable
private fun DeltaPill(d: Int) {
    val c = if (d >= 0) MaterialTheme.fit.success else MaterialTheme.fit.warning
    Text(
        "hacim ${if (d >= 0) "+" else "−"}%${kotlin.math.abs(d)}",
        style = MaterialTheme.typography.labelMedium.mono().copy(fontWeight = FontWeight.SemiBold), color = c,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun SummaryMetric(value: String, label: String, delta: Int?, modifier: Modifier, color: Color? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium.mono().copy(fontWeight = FontWeight.SemiBold), color = color ?: MaterialTheme.colorScheme.onSurface, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted, maxLines = 1)
        if (delta != null && delta != 0) {
            Text(
                "${if (delta > 0) "+" else "−"}%${kotlin.math.abs(delta)}",
                style = MaterialTheme.typography.labelSmall.mono(),
                color = if (delta > 0) MaterialTheme.fit.success else MaterialTheme.fit.warning
            )
        }
    }
}

@Composable
private fun SummaryDivider() {
    Box(Modifier.width(1.dp).height(34.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
}


/* ------------------------------ Güncelleme bandı ------------------------------ */

@Composable
private fun UpdateBanner(info: com.example.ui.AppUpdate.Info, onInstall: () -> Unit, onLater: () -> Unit) {
    val accent = MaterialTheme.fit.accent
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.06f))))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SystemUpdate, null, tint = accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("FitFlow ${info.version} hazır", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                Text("Yüklü: ${com.example.BuildConfig.VERSION_NAME} · verilerin korunur", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
            }
        }
        val notes = info.notes.lineSequence().firstOrNull { it.isNotBlank() }
        if (notes != null) {
            Spacer(Modifier.height(8.dp))
            Text(notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(12.dp)).background(accent).clickable { onInstall() },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Download, null, tint = MaterialTheme.fit.onAccent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("İndir ve kur", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.fit.onAccent)
            }
            Text(
                "Sonra", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.fit.muted,
                modifier = Modifier.height(42.dp).clip(RoundedCornerShape(12.dp)).clickable { onLater() }.padding(horizontal = 16.dp, vertical = 11.dp)
            )
        }
    }
}
