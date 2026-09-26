package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.core.Analytics
import com.example.core.TR
import com.example.core.formatDate
import com.example.core.formatDateShort
import com.example.core.formatDateTime
import com.example.core.formatDuration
import com.example.core.formatDurationShort
import com.example.core.formatMonthYear
import com.example.core.formatTonnage
import com.example.core.startOfWeek
import com.example.core.kg
import com.example.core.trimNum
import com.example.data.RoutineDayEntity
import com.example.data.WorkoutEntity
import com.example.ui.AppViewModel
import com.example.ui.Routes
import com.example.ui.components.Badge
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.FitCard
import com.example.ui.components.GhostButton
import com.example.ui.components.OverlineText
import com.example.ui.components.AccentButton
import com.example.ui.components.RoundIconButton
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatTile
import com.example.ui.theme.Palette
import com.example.ui.theme.fit
import java.util.Calendar
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.input.KeyboardType
import com.example.ui.components.FitTextField
import com.example.data.WorkoutSetEntity
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import com.example.ui.components.DeloadBorderPurple
import com.example.ui.components.DeloadPurple
import com.example.ui.components.DeloadPurpleContainerDark
import com.example.ui.components.DeloadPurpleContainerLight

/* ================================== Geçmiş ================================== */

@Composable
fun HistoryScreen(vm: AppViewModel, nav: NavHostController) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val allSets by vm.allSets.collectAsStateWithLifecycle()
    val activeDeloadWeekStart by vm.settings.activeDeloadWeekStart.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<WorkoutEntity?>(null) }
    var editingWorkoutDate by remember { mutableStateOf<WorkoutEntity?>(null) }
    var showAddPastWorkout by remember { mutableStateOf(false) }

    val filtered = remember(workouts, allSets, query) {
        if (query.isBlank()) workouts
        else {
            val q = query.lowercase(TR)
            workouts.filter { w ->
                w.title.lowercase(TR).contains(q) ||
                    formatDate(w.startedAt).lowercase(TR).contains(q) ||
                    allSets.any { it.workoutId == w.id && it.exerciseName.lowercase(TR).contains(q) }
            }
        }
    }
    val groupedByMonth = remember(filtered) { filtered.groupBy { formatMonthYear(it.startedAt) } }
    val nowWeekStart = remember { startOfWeek(System.currentTimeMillis()) }
    val collapsedMonths = remember { mutableStateMapOf<String, Boolean>() }
    val collapsedWeeks = remember { mutableStateMapOf<String, Boolean>() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Antrenman geçmişi",
            subtitle = "${workouts.size} tamamlanan seans",
            onBack = { nav.popBackStack() }
        ) {
            RoundIconButton(
                icon = Icons.Default.Add,
                tint = MaterialTheme.fit.accent,
                size = 40.dp,
                background = MaterialTheme.fit.accent.copy(alpha = 0.12f)
            ) {
                showAddPastWorkout = true
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            SearchField(query, { query = it }, "Seans, tarih veya hareket ara…")
        }
        Spacer(Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            EmptyState(
                Icons.Default.History,
                if (workouts.isEmpty()) "Henüz kayıt yok" else "Sonuç bulunamadı",
                if (workouts.isEmpty()) "Tamamladığın antrenmanlar burada listelenir."
                else "Arama kriterlerine uyan bir seans yok."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                groupedByMonth.forEach { (month, monthList) ->
                    val isMonthCollapsed = collapsedMonths[month] == true

                    item(key = "month_$month") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
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
                                "${monthList.size} seans · " + formatTonnage(
                                    monthList.sumOf { Analytics.workoutVolume(it.id, allSets).toDouble() }.toFloat()
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.fit.muted
                            )
                        }
                    }

                    if (!isMonthCollapsed) {
                        val weekGroups = monthList.groupBy { startOfWeek(it.startedAt) }

                        weekGroups.forEach { (weekStartMillis, weekList) ->
                            val weekKey = "${month}_$weekStartMillis"
                            val isWeekCollapsed = collapsedWeeks[weekKey] == true
                            val weekEndMillis = weekStartMillis + 6 * 86_400_000L
                            val weekLabel = when (weekStartMillis) {
                                nowWeekStart -> "Bu Hafta (${formatDateShort(weekStartMillis)} - ${formatDateShort(weekEndMillis)})"
                                nowWeekStart - 7 * 86_400_000L -> "Geçen Hafta (${formatDateShort(weekStartMillis)} - ${formatDateShort(weekEndMillis)})"
                                else -> "${formatDateShort(weekStartMillis)} - ${formatDateShort(weekEndMillis)}"
                            }
                            val isWeekDeload = (activeDeloadWeekStart == weekStartMillis) || weekList.any { it.isDeload }

                            item(key = "week_$weekKey") {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp, bottom = 2.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isWeekDeload) DeloadPurple.copy(alpha = 0.08f) else Color.Transparent)
                                        .clickable { collapsedWeeks[weekKey] = !isWeekCollapsed }
                                        .padding(horizontal = 6.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (isWeekCollapsed) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = if (isWeekDeload) DeloadPurple else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            weekLabel,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (isWeekDeload) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = if (isWeekDeload) DeloadPurple else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                        )
                                        if (isWeekDeload) {
                                            Spacer(Modifier.width(6.dp))
                                            Badge("DELOAD", color = DeloadPurple)
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "${weekList.size} seans",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.fit.muted
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        // Haftayı Deload olarak belirten kutucuk
                                        Checkbox(
                                            checked = isWeekDeload,
                                            onCheckedChange = { checked ->
                                                if (weekStartMillis == nowWeekStart) {
                                                    vm.setDeloadWeekActive(checked)
                                                }
                                                weekList.forEach { vm.setWorkoutDeload(it.id, checked) }
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = DeloadPurple,
                                                checkmarkColor = Color.White
                                            ),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            if (!isWeekCollapsed) {
                                items(weekList, key = { it.id }) { w ->
                                    val workoutSets = allSets.filter { it.workoutId == w.id }
                                    val vol = Analytics.workoutVolume(w.id, allSets)
                                    val sets = workoutSets.count { Analytics.isEffectiveSet(it) }
                                    val exCount = if (workoutSets.isEmpty()) 0 else workoutSets.groupBy { it.exerciseOrder }.size

                                    val isDeloadCard = w.isDeload || isWeekDeload
                                    val isDarkTheme = MaterialTheme.colorScheme.background.run { (red + green + blue) < 1.5f }
                                    val cardContainer = if (isDeloadCard) {
                                        if (isDarkTheme) DeloadPurpleContainerDark else DeloadPurpleContainerLight
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                    val cardBorder = if (isDeloadCard) DeloadBorderPurple else MaterialTheme.fit.cardBorder

                                    FitCard(
                                        onClick = { nav.navigate("${Routes.WORKOUT_DETAIL}/${w.id}") },
                                        container = cardContainer,
                                        border = cardBorder
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        w.title,
                                                        style = MaterialTheme.typography.titleMedium.copy(
                                                            color = if (isDeloadCard) DeloadPurple else MaterialTheme.colorScheme.onSurface,
                                                            fontWeight = if (isDeloadCard) FontWeight.Bold else FontWeight.Normal
                                                        ),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    if (isDeloadCard) {
                                                        Spacer(Modifier.width(6.dp))
                                                        Badge("DELOAD", color = DeloadPurple)
                                                    }
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.CalendarToday,
                                                        contentDescription = null,
                                                        tint = if (isDeloadCard) DeloadPurple else MaterialTheme.fit.accent,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        formatDateTime(w.startedAt),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.fit.muted
                                                    )
                                                }
                                            }
                                            if (w.feeling > 0) {
                                                Text(feelingEmoji(w.feeling), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(end = 6.dp))
                                            }
                                            RoundIconButton(
                                                icon = Icons.Default.Healing,
                                                tint = if (w.isDeload) DeloadPurple else MaterialTheme.fit.muted,
                                                size = 36.dp,
                                                background = if (w.isDeload) DeloadPurple.copy(alpha = 0.18f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                            ) {
                                                vm.setWorkoutDeload(w.id, !w.isDeload)
                                            }
                                            Spacer(Modifier.width(6.dp))
                                            RoundIconButton(
                                                icon = Icons.Default.ContentCopy,
                                                tint = MaterialTheme.fit.accent,
                                                size = 36.dp,
                                                background = MaterialTheme.fit.accent.copy(alpha = 0.12f)
                                            ) {
                                                vm.duplicateWorkout(w.id) { newId ->
                                                    nav.navigate("${Routes.WORKOUT_DETAIL}/$newId")
                                                }
                                            }
                                            Spacer(Modifier.width(6.dp))
                                            RoundIconButton(
                                                icon = Icons.Default.Event,
                                                tint = MaterialTheme.fit.accent,
                                                size = 36.dp,
                                                background = MaterialTheme.fit.accent.copy(alpha = 0.12f)
                                            ) {
                                                editingWorkoutDate = w
                                            }
                                            Spacer(Modifier.width(6.dp))
                                            RoundIconButton(
                                                icon = Icons.Default.Delete,
                                                tint = MaterialTheme.colorScheme.error,
                                                size = 36.dp,
                                                background = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                            ) {
                                                toDelete = w
                                            }
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Badge(formatDurationShort(w.durationSeconds), MaterialTheme.fit.muted)
                                            Badge("$exCount hareket", MaterialTheme.fit.accent)
                                            Badge("$sets set", Palette.violet)
                                            if (vol > 0f) Badge(formatTonnage(vol), MaterialTheme.fit.gold)
                                        }
                                        if (w.notes.isNotBlank()) {
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                w.notes,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.fit.muted,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
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
    }

    editingWorkoutDate?.let { w ->
        EditWorkoutDateDialog(
            workout = w,
            onDismiss = { editingWorkoutDate = null },
            onSave = { newMillis ->
                vm.updateWorkoutDate(w.id, newMillis)
                editingWorkoutDate = null
            }
        )
    }

    if (showAddPastWorkout) {
        AddPastWorkoutDialog(
            vm = vm,
            onDismiss = { showAddPastWorkout = false },
            onCreated = { workoutId ->
                showAddPastWorkout = false
                nav.navigate("${Routes.WORKOUT_DETAIL}/$workoutId")
            }
        )
    }

    toDelete?.let { w ->
        ConfirmDialog(
            title = "Seansı sil",
            text = "\"${w.title}\" kaydı ve tüm setleri silinecek.",
            confirmLabel = "Sil",
            destructive = true,
            onConfirm = { vm.deleteWorkout(w); toDelete = null },
            onDismiss = { toDelete = null }
        )
    }
}

/* =============================== Seans detayı =============================== */

@Composable
fun WorkoutDetailScreen(vm: AppViewModel, nav: NavHostController, workoutId: Long) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val allSets by vm.allSets.collectAsStateWithLifecycle()
    val prs by vm.prs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var toDelete by remember { mutableStateOf(false) }
    var editingDate by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

    val w = workouts.firstOrNull { it.id == workoutId }
    if (w == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Kayıt bulunamadı", color = MaterialTheme.fit.muted)
        }
        return
    }

    val sets = remember(allSets, workoutId) {
        allSets.filter { it.workoutId == workoutId }.sortedWith(compareBy({ it.exerciseOrder }, { it.setNumber }))
    }
    val grouped = remember(sets) { sets.groupBy { it.exerciseOrder } }
    val volume = Analytics.workoutVolume(workoutId, allSets)
    val workoutPrs = prs.filter { it.workoutId == workoutId }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(w.title, formatDateTime(w.startedAt), onBack = { nav.popBackStack() }) {
            RoundIconButton(Icons.Default.Event, MaterialTheme.fit.accent, 40.dp, MaterialTheme.fit.accent.copy(alpha = 0.12f)) {
                editingDate = true
            }
            Spacer(Modifier.width(8.dp))
            RoundIconButton(
                icon = Icons.Default.ContentCopy,
                tint = MaterialTheme.fit.accent,
                size = 40.dp,
                background = MaterialTheme.fit.accent.copy(alpha = 0.12f)
            ) {
                vm.duplicateWorkout(workoutId) { newId ->
                    nav.navigate("${Routes.WORKOUT_DETAIL}/$newId") {
                        popUpTo("${Routes.WORKOUT_DETAIL}/$workoutId") { inclusive = true }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            RoundIconButton(Icons.Default.Share, MaterialTheme.fit.muted, 40.dp, MaterialTheme.fit.elevated) {
                shareText(context, buildShareText(w, grouped, volume))
            }
            Spacer(Modifier.width(8.dp))
            RoundIconButton(Icons.Default.Delete, MaterialTheme.fit.danger, 40.dp, MaterialTheme.fit.danger.copy(alpha = 0.12f)) {
                toDelete = true
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Süre", formatDuration(w.durationSeconds), Modifier.weight(1f))
                    StatTile("Hacim", formatTonnage(volume), Modifier.weight(1f))
                    StatTile("Set", "${sets.count { Analytics.isEffectiveSet(it) }}", Modifier.weight(1f))
                }
            }

            if (workoutPrs.isNotEmpty()) {
                item {
                    FitCard(container = MaterialTheme.fit.gold.copy(alpha = 0.10f), border = MaterialTheme.fit.gold.copy(alpha = 0.35f)) {
                        OverlineText("🏆 Bu seansta kırılan rekorlar", MaterialTheme.fit.gold)
                        Spacer(Modifier.height(8.dp))
                        workoutPrs.forEach { pr ->
                            Text(
                                "${pr.exerciseName} — " + when (pr.type) {
                                    com.example.data.PrEntity.TYPE_WEIGHT -> "${pr.weightKg.kg()} × ${pr.reps}"
                                    com.example.data.PrEntity.TYPE_E1RM -> "1RM ${pr.value.kg()}"
                                    else -> formatTonnage(pr.value)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (w.notes.isNotBlank() || w.feeling > 0) {
                item {
                    FitCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (w.feeling > 0) {
                                Text(feelingEmoji(w.feeling), style = MaterialTheme.typography.displaySmall)
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                OverlineText("Seans notu")
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    w.notes.ifBlank { "Not eklenmemiş" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (w.notes.isBlank()) MaterialTheme.fit.muted else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Hareketler", "${grouped.size} hareket") }

            grouped.forEach { (order, exSets) ->
                val firstSet = exSets.first()
                val exId = firstSet.exerciseId
                val ex = vm.exerciseById(exId)
                val isDuration = ex?.trackingType == com.example.data.ExerciseEntity.TRACK_DURATION || exSets.any { it.durationSeconds > 0 }

                item(key = "ex_${order}_$exId") {
                    FitCard(contentPadding = PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MuscleAvatar(ex?.muscleGroup ?: "Diğer", 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(firstSet.exerciseName, style = MaterialTheme.typography.titleSmall)
                                val summaryText = if (isDuration) {
                                    "${exSets.count { Analytics.isEffectiveSet(it) }} set · ${exSets.sumOf { it.durationSeconds }} sn"
                                } else {
                                    "${exSets.count { Analytics.isEffectiveSet(it) }} set · " +
                                        formatTonnage(exSets.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat())
                                }
                                Text(
                                    summaryText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.fit.muted
                                )
                            }
                            IconButton(onClick = { vm.removeExerciseFromWorkoutByOrder(workoutId, order) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Hareketi Sil", tint = MaterialTheme.fit.muted, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        exSets.forEach { s ->
                            SetDetailEditRow(
                                set = s,
                                exercise = ex,
                                onUpdate = { vm.updateSet(it) },
                                onDelete = { vm.deleteSet(s) }
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = { vm.addSetRowToWorkout(workoutId, order, exId) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("+ Set Ekle")
                        }
                    }
                }
            }

            item {
                GhostButton(
                    text = "Seansa Hareket Ekle",
                    icon = Icons.Default.Add,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showPicker = true }
                )
            }
        }
    }

    if (showPicker) {
        ExercisePickerDialog(
            vm = vm,
            title = "Seansa hareket ekle",
            excludeIds = grouped.values.flatMap { list -> list.map { it.exerciseId } }.toSet(),
            onPick = { ex ->
                vm.addExerciseToWorkout(workoutId, ex.id)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }

    if (editingDate) {
        EditWorkoutDateDialog(
            workout = w,
            onDismiss = { editingDate = false },
            onSave = { newMillis ->
                vm.updateWorkoutDate(w.id, newMillis)
                editingDate = false
            }
        )
    }

    if (toDelete) {
        ConfirmDialog(
            title = "Seansı sil",
            text = "Bu antrenman kaydı ve tüm setleri kalıcı olarak silinecek.",
            confirmLabel = "Sil",
            destructive = true,
            onConfirm = { vm.deleteWorkout(w); toDelete = false; nav.popBackStack() },
            onDismiss = { toDelete = false }
        )
    }
}

@Composable
private fun SetDetailEditRow(
    set: WorkoutSetEntity,
    exercise: com.example.data.ExerciseEntity?,
    onUpdate: (WorkoutSetEntity) -> Unit,
    onDelete: () -> Unit
) {
    val isDuration = exercise?.trackingType == com.example.data.ExerciseEntity.TRACK_DURATION || set.durationSeconds > 0
    val isRepsOnly = exercise?.trackingType == com.example.data.ExerciseEntity.TRACK_REPS

    var weightText by remember(set.weightKg) { mutableStateOf(if (set.weightKg > 0f) set.weightKg.trimNum() else "") }
    var repsText by remember(set.reps) { mutableStateOf(if (set.reps > 0) set.reps.toString() else "") }
    var durationText by remember(set.durationSeconds) { mutableStateOf(if (set.durationSeconds > 0) set.durationSeconds.toString() else "") }
    var rpeText by remember(set.rpe) { mutableStateOf(if (set.rpe > 0f) set.rpe.trimNum() else "") }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (set.isWarmup) Palette.warning.copy(alpha = 0.15f)
                    else MaterialTheme.fit.elevated
                )
                .clickable { onUpdate(set.copy(isWarmup = !set.isWarmup)) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (set.isWarmup) "W" else "${set.setNumber}",
                style = MaterialTheme.typography.labelSmall,
                color = if (set.isWarmup) Palette.warning else MaterialTheme.fit.muted
            )
        }

        if (isDuration) {
            FitTextField(
                value = durationText,
                onValueChange = { str ->
                    durationText = str
                    val d = str.toIntOrNull() ?: 0
                    onUpdate(set.copy(durationSeconds = d, isCompleted = true))
                },
                label = "sn",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1.2f)
            )
        } else {
            FitTextField(
                value = weightText,
                onValueChange = { str ->
                    weightText = str
                    val w = str.replace(',', '.').toFloatOrNull() ?: 0f
                    onUpdate(set.copy(weightKg = w, isCompleted = true))
                },
                label = "kg",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f)
            )
            FitTextField(
                value = repsText,
                onValueChange = { str ->
                    repsText = str
                    val r = str.toIntOrNull() ?: 0
                    onUpdate(set.copy(reps = r, isCompleted = true))
                },
                label = "Tekrar",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f)
            )
        }

        FitTextField(
            value = rpeText,
            onValueChange = { str ->
                rpeText = str
                val rpe = str.replace(',', '.').toFloatOrNull() ?: 0f
                onUpdate(set.copy(rpe = rpe, isCompleted = true))
            },
            label = "RPE",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(0.8f)
        )

        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Sil", tint = MaterialTheme.fit.muted, modifier = Modifier.size(16.dp))
        }
    }
}

/* ----------------------------- Diyaloglar -------------------------------- */

@Composable
private fun EditWorkoutDateDialog(
    workout: WorkoutEntity,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit
) {
    val context = LocalContext.current
    var selectedMillis by remember { mutableStateOf(workout.startedAt) }

    val setDateOffset = { days: Int ->
        val cal = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            add(Calendar.DAY_OF_YEAR, -days)
            val currentCal = Calendar.getInstance().apply { timeInMillis = selectedMillis }
            set(Calendar.HOUR_OF_DAY, currentCal.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, currentCal.get(Calendar.MINUTE))
        }
        selectedMillis = cal.timeInMillis
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Event,
                    contentDescription = null,
                    tint = MaterialTheme.fit.accent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Tarihi Değiştir", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "\"${workout.title}\" antrenmanının tarihini ve saatini güncelleyin:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.fit.muted
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.fit.elevated)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            OverlineText("Seçili Tarih", MaterialTheme.fit.accent)
                            Text(
                                formatDateTime(selectedMillis),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        RoundIconButton(
                            icon = Icons.Default.CalendarToday,
                            tint = MaterialTheme.fit.accent,
                            size = 38.dp,
                            background = MaterialTheme.fit.accent.copy(alpha = 0.15f)
                        ) {
                            showDateTimePicker(context, selectedMillis) { newMillis ->
                                selectedMillis = newMillis
                            }
                        }
                    }
                }

                Text("Hızlı Seçim", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PresetChip("Bugün", onClick = { setDateOffset(0) })
                    PresetChip("Dün", onClick = { setDateOffset(1) })
                    PresetChip("3 Gün Önce", onClick = { setDateOffset(3) })
                    PresetChip("1 Hafta", onClick = { setDateOffset(7) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selectedMillis) }) {
                Text("Kaydet", color = MaterialTheme.fit.accent, style = MaterialTheme.typography.titleMedium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç", color = MaterialTheme.fit.muted, style = MaterialTheme.typography.titleMedium)
            }
        }
    )
}

@Composable
private fun AddPastWorkoutDialog(
    vm: AppViewModel,
    onDismiss: () -> Unit,
    onCreated: (Long) -> Unit
) {
    val context = LocalContext.current
    val days by vm.allDays.collectAsStateWithLifecycle()
    var selectedDay by remember { mutableStateOf<RoutineDayEntity?>(null) }
    var selectedMillis by remember { mutableStateOf(System.currentTimeMillis() - 86_400_000L) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.fit.accent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Eski Antrenman Ekle", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Geçmiş bir güne ait tamamlanmış antrenman kaydı oluşturun:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.fit.muted)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.fit.elevated)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            OverlineText("Tarih & Saat", MaterialTheme.fit.accent)
                            Text(formatDateTime(selectedMillis), style = MaterialTheme.typography.titleSmall)
                        }
                        RoundIconButton(
                            icon = Icons.Default.CalendarToday,
                            tint = MaterialTheme.fit.accent,
                            size = 36.dp,
                            background = MaterialTheme.fit.accent.copy(alpha = 0.15f)
                        ) {
                            showDateTimePicker(context, selectedMillis) { newMillis ->
                                selectedMillis = newMillis
                            }
                        }
                    }
                }

                Text("Program Günü (İsteğe bağlı)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
                LazyColumn(
                    modifier = Modifier.height(140.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        SelectableRow(
                            title = "Serbest Antrenman",
                            selected = selectedDay == null,
                            onClick = { selectedDay = null }
                        )
                    }
                    items(days, key = { it.id }) { d ->
                        SelectableRow(
                            title = d.name,
                            selected = selectedDay?.id == d.id,
                            onClick = { selectedDay = d }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.createPastWorkout(selectedDay, selectedMillis, onCreated)
            }) {
                Text("Oluştur", color = MaterialTheme.fit.accent, style = MaterialTheme.typography.titleMedium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç", color = MaterialTheme.fit.muted, style = MaterialTheme.typography.titleMedium)
            }
        }
    )
}

@Composable
private fun PresetChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.fit.elevated)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SelectableRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.fit.accent.copy(alpha = 0.15f) else MaterialTheme.fit.elevated)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.fit.accent else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun showDateTimePicker(
    context: Context,
    initialMillis: Long,
    onSelected: (Long) -> Unit
) {
    val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
    val dpd = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month)
            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)

            val tpd = TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    cal.set(Calendar.MINUTE, minute)
                    cal.set(Calendar.SECOND, 0)
                    onSelected(cal.timeInMillis)
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true
            )
            tpd.setTitle("Saat seçin")
            tpd.show()
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    )
    dpd.setTitle("Tarih seçin")
    dpd.show()
}

/* --------------------------------- Yardımcı -------------------------------- */

fun feelingEmoji(v: Int) = when (v) {
    1 -> "😵"; 2 -> "🙁"; 3 -> "😐"; 4 -> "🙂"; 5 -> "🔥"; else -> ""
}

private fun buildShareText(
    w: WorkoutEntity,
    grouped: Map<*, List<com.example.data.WorkoutSetEntity>>,
    volume: Float
): String {
    val sb = StringBuilder()
    sb.append("🏋️ ${w.title}\n")
    sb.append("📅 ${formatDate(w.startedAt)}\n")
    sb.append("⏱ ${formatDuration(w.durationSeconds)}  ·  💪 ${formatTonnage(volume)}\n\n")
    grouped.forEach { (_, sets) ->
        sb.append("• ${sets.first().exerciseName}\n")
        sets.filter { it.isCompleted }.forEach { s ->
            sb.append("   ${s.setNumber}. ")
            sb.append(
                if (s.durationSeconds > 0) "${s.durationSeconds} sn"
                else if (s.weightKg > 0f) "${s.weightKg.trimNum()} kg × ${s.reps}"
                else "${s.reps} tekrar"
            )
            if (s.rpe > 0f) sb.append("  RPE ${s.rpe.trimNum()}")
            sb.append("\n")
        }
    }
    if (w.notes.isNotBlank()) sb.append("\n📝 ${w.notes}")
    sb.append("\n\nFitFlow ile kaydedildi")
    return sb.toString()
}

fun shareText(context: android.content.Context, text: String) {
    try {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Paylaş"))
    } catch (_: Throwable) {
    }
}
