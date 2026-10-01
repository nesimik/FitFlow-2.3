package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.core.LadderConfig
import com.example.core.LadderMode
import com.example.core.Ladders
import com.example.core.RepScheme
import com.example.core.formatDuration
import com.example.core.trimNum
import com.example.data.ExerciseEntity
import com.example.data.RoutineDayEntity
import com.example.data.RoutineItemEntity
import com.example.data.WorkoutEntity
import com.example.ui.AppViewModel
import com.example.ui.Routes
import com.example.ui.components.AccentButton
import com.example.ui.components.Badge
import com.example.ui.components.ChoiceChip
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.FinishSessionDialog
import com.example.ui.components.FitTextField
import com.example.ui.components.GhostButton
import com.example.ui.components.LabeledSwitch
import com.example.ui.components.OverlineText
import com.example.ui.components.RoundIconButton
import com.example.ui.theme.Palette
import com.example.ui.theme.fit
import com.example.ui.components.cardBackground
import com.example.ui.theme.mono

/* ==========================================================================
 * Program ekranı — açılır kapanır gün kartı (2.16)
 *
 * Kapalı: gün özeti + ilk 4 hareket. Açık: tüm hareketler; harekete dokununca alttan
 * ayar paneli (set, merdiven, dinlenme, başlangıç kg, süperset, not, video, alternatif,
 * sıralama, silme). Açık seans varsa kartta Devam et / Bitir.
 * ========================================================================== */

private val WEEKDAY_SHORT = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")

@Composable
fun ProgramDayCard(
    vm: AppViewModel,
    nav: NavHostController,
    day: RoutineDayEntity,
    color: Color,
    isToday: Boolean,
    items: List<RoutineItemEntity>,
    exerciseMap: Map<Long, ExerciseEntity>,
    ladderCfgs: Map<Long, LadderConfig>,
    activeWorkout: WorkoutEntity?,
    sessionDone: Int,
    sessionTotal: Int,
    sessionElapsed: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEditDay: () -> Unit,
    onDuplicate: () -> Unit,
    onReplaceFromHistory: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var sheetItemId by remember { mutableStateOf<Long?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var showSupersetPicker by remember { mutableStateOf(false) }
    var itemToCombine by remember { mutableStateOf<RoutineItemEntity?>(null) }
    var itemToDelete by remember { mutableStateOf<RoutineItemEntity?>(null) }
    var showFinish by remember { mutableStateOf(false) }
    var showBusy by remember { mutableStateOf(false) }
    var editorOpen by remember { mutableStateOf(false) }

    val running = activeWorkout?.takeIf { !it.isFinished }
    val isActiveDay = running != null && running.routineDayId == day.id
    val work = items.filter { !it.isWarmup }
    val deloadRec by vm.deloadRecommendation.collectAsStateWithLifecycle()
    val isDeload = deloadRec.isCurrentlyDeloadWeek
    // Deload haftasında set sayısı yarıya (yukarı yuvarlanır): 3→2, 4→2, 5→3
    fun setsOf(n: Int) = if (isDeload) maxOf(1, (n + 1) / 2) else n
    val sets = work.sumOf { setsOf(it.targetSets) }
    val minutes = (8 + items.sumOf { setsOf(it.targetSets) * (it.restSeconds + 40) } / 60) / 5 * 5
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "chev")
    val allSets by vm.allSets.collectAsStateWithLifecycle()
    val plan = remember(items, ladderCfgs, allSets, isDeload) { vm.planByItem(day.id) }

    fun start() {
        if (running != null && !isActiveDay) showBusy = true
        else vm.startWorkout(day) { id -> nav.navigate("${Routes.WORKOUT}/$id") }
    }

    val shape = RoundedCornerShape(22.dp)
    val borderColor = when {
        isActiveDay -> MaterialTheme.fit.success.copy(alpha = 0.5f)
        isToday -> MaterialTheme.fit.accent.copy(alpha = 0.3f)
        else -> MaterialTheme.fit.cardBorder
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(Modifier.cardBackground())
            .border(1.dp, borderColor, shape)
    ) {
        /* Başlık — dokununca aç/kapa */
        Row(
            Modifier.fillMaxWidth().clickable { onToggle() }.padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (isToday) MaterialTheme.fit.accent.copy(alpha = 0.14f) else MaterialTheme.fit.elevated),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (day.weekday in 1..7) WEEKDAY_SHORT[day.weekday - 1].uppercase(java.util.Locale("tr"))
                    else day.name.trim().take(1).uppercase(java.util.Locale("tr")),
                    style = MaterialTheme.typography.labelLarge.mono().copy(fontWeight = FontWeight.SemiBold),
                    color = when {
                        isToday -> MaterialTheme.fit.accent
                        day.weekday in 1..7 -> Color(0xFFC4CAD3)
                        else -> color
                    }
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(day.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(day.focus, "${work.size} hareket · $sets set · ~$minutes dk").filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (isDeload && !isActiveDay) { Badge("DELOAD", Palette.violet); Spacer(Modifier.width(4.dp)) }
            if (isActiveDay) Badge("DEVAM EDİYOR", MaterialTheme.fit.success)
            else if (isToday) Badge("BUGÜN", MaterialTheme.fit.accent)
            Box {
                RoundIconButton(Icons.Default.MoreVert, MaterialTheme.fit.muted, 34.dp, Color.Transparent) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Günü düzenle") }, onClick = { menu = false; editorOpen = true }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                    DropdownMenuItem(text = { Text("Geçmiş seans ile değiştir") }, onClick = { menu = false; onReplaceFromHistory() }, leadingIcon = { Icon(Icons.Default.History, null) })
                    DropdownMenuItem(text = { Text("Kopyala") }, onClick = { menu = false; onDuplicate() }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) })
                    DropdownMenuItem(text = { Text("Sil") }, onClick = { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.fit.danger) })
                }
            }
            Icon(Icons.Default.ExpandMore, null, tint = MaterialTheme.fit.muted, modifier = Modifier.rotate(chevron))
        }

        /* Açık seans şeridi (2.26: daha belirgin) */
        if (isActiveDay && running != null) {
            ActiveSessionStrip(
                elapsed = formatDuration(sessionElapsed),
                done = sessionDone,
                total = sessionTotal,
                onResume = { nav.navigate("${Routes.WORKOUT}/${running.id}") },
                onFinish = { showFinish = true }
            )
            Spacer(Modifier.height(8.dp))
        }

        /* Hareket listesi */
        val shown = if (expanded) items else work.take(4)
        Column(Modifier.padding(start = 16.dp, end = 12.dp, bottom = 12.dp)) {
            if (items.isEmpty()) {
                Text("Henüz hareket yok", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted, modifier = Modifier.padding(vertical = 6.dp))
            }
            shown.forEach { it ->
                val ex = exerciseMap[it.exerciseId]
                ProgramItemRow(
                    item = it,
                    ex = ex,
                    cfg = ladderCfgs[it.id],
                    rx = plan[it.id],
                    clickable = expanded,
                    onClick = { sheetItemId = it.id }
                )
            }
            if (!expanded && work.size > 4) {
                Text("+${work.size - 4} hareket · dokunup aç", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted, modifier = Modifier.padding(top = 6.dp))
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("Hareket", { showPicker = true }, Modifier.weight(1f), Icons.Default.Add, MaterialTheme.fit.accent)
                        GhostButton("Süperset", { showSupersetPicker = true }, Modifier.weight(1f), Icons.Default.Bolt, Palette.warning)
                    }
                    if (!isActiveDay) {
                        AccentButton("Bu günle başla", { start() }, Modifier.fillMaxWidth(), Icons.Default.PlayArrow)
                    }
                }
            }
        }
    }

    /* ------------------------------ Gün düzenleyici (2.34) ------------------------------ */
    if (editorOpen) {
        DayEditor(
            vm = vm,
            day = day,
            items = items,
            exerciseMap = exerciseMap,
            ladderCfgs = ladderCfgs,
            plan = plan,
            onOpenItem = { sheetItemId = it },
            onAdd = { showPicker = true },
            onAddSuperset = { showSupersetPicker = true },
            onClose = { editorOpen = false }
        )
    }

    /* ------------------------------ Paneller ------------------------------ */
    val sheetItem = sheetItemId?.let { id -> items.firstOrNull { it.id == id } }
    if (sheetItem != null) {
        val item = sheetItem
        ProgramItemSheet(
            vm = vm,
            item = item,
            ex = exerciseMap[item.exerciseId],
            cfg = ladderCfgs[item.id],
            rx = plan[item.id],
            isFirst = items.firstOrNull()?.id == item.id,
            isLast = items.lastOrNull()?.id == item.id,
            onRequestCombine = { itemToCombine = item },
            onDelete = { itemToDelete = item },
            onDismiss = { sheetItemId = null }
        )
    }

    if (showPicker) {
        ExercisePickerDialog(
            vm = vm,
            title = "${day.name} için hareket",
            onPick = { ex -> vm.addItem(day.id, ex.id); showPicker = false },
            onDismiss = { showPicker = false }
        )
    }
    if (showSupersetPicker) {
        SupersetPickerDialog(
            vm = vm,
            title = "${day.name} için süperset",
            onPickSuperset = { exList -> vm.addSupersetToDay(day.id, exList.map { it.id }); showSupersetPicker = false },
            onDismiss = { showSupersetPicker = false }
        )
    }
    itemToCombine?.let { cur ->
        CombineExerciseDialog(
            title = "Süperset olarak birleştir",
            sourceName = cur.customName.ifBlank { exerciseMap[cur.exerciseId]?.name ?: "" },
            candidates = items.filter { it.id != cur.id },
            candidateName = { it.customName.ifBlank { exerciseMap[it.exerciseId]?.name ?: "" } },
            candidateGroup = { exerciseMap[it.exerciseId]?.muscleGroup ?: "Diğer" },
            onCombineWith = { target -> vm.combineRoutineItems(day.id, cur.id, target.id); itemToCombine = null },
            onDismiss = { itemToCombine = null }
        )
    }
    itemToDelete?.let { i ->
        ConfirmDialog(
            title = "Hareketi çıkar",
            text = "Bu hareket \"${day.name}\" gününden çıkarılacak. Geçmiş kayıtların etkilenmez.",
            confirmLabel = "Çıkar",
            destructive = true,
            onConfirm = { vm.deleteItem(i); vm.setLadderConfig(i.id, null); itemToDelete = null; sheetItemId = null },
            onDismiss = { itemToDelete = null }
        )
    }
    if (showFinish && running != null) {
        FinishSessionDialog(
            title = running.title,
            summary = "${formatDuration(sessionElapsed)} · $sessionDone/$sessionTotal set tamamlandı",
            onSave = { showFinish = false; vm.requestFinishOnEnter(); nav.navigate("${Routes.WORKOUT}/${running.id}") },
            onDiscard = { showFinish = false; vm.discardWorkout {} },
            onDismiss = { showFinish = false }
        )
    }
    if (showBusy && running != null) {
        AlertDialog(
            onDismissRequest = { showBusy = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Açık bir seans var", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("\"${running.title}\" seansı hâlâ açık. Yeni bir gün başlatmadan önce ona devam et ya da bitir.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.fit.muted)
                    AccentButton("Seansa devam et", { showBusy = false; nav.navigate("${Routes.WORKOUT}/${running.id}") }, Modifier.fillMaxWidth(), Icons.Default.PlayArrow)
                    GhostButton("Seansı bitir", { showBusy = false; showFinish = true }, Modifier.fillMaxWidth(), Icons.Default.Stop, MaterialTheme.fit.danger)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showBusy = false }) { Text("Vazgeç", color = MaterialTheme.fit.muted) } }
        )
    }
}

/** Kart içindeki tek hareket satırı: renk noktası, ad, "3 × 6-7-8" ya da "3 × 8–12". */
@Composable
private fun ProgramItemRow(
    item: RoutineItemEntity,
    ex: ExerciseEntity?,
    cfg: LadderConfig?,
    rx: com.example.core.Prescription?,
    clickable: Boolean,
    onClick: () -> Unit
) {
    val isDuration = ex?.trackingType == ExerciseEntity.TRACK_DURATION
    val spec = ex?.let { Ladders.resolve(cfg, it.name, it.muscleGroup, it.equipment, it.trackingType) }
    // Bir sonraki seansın önerisi varsa onu göster (ağırlık + hedef tekrarlar); yoksa programdaki hedef.
    val reps = when {
        isDuration -> "${item.targetSets} × ${item.repMin} sn"
        rx != null && rx.action != com.example.core.ProgressAction.FIRST -> rx.repTargets.joinToString("-")
        // Geçmiş yoksa da set sayısı reçeteden (deload'da yarıya inmiş hali)
        spec != null -> "${rx?.sets ?: item.targetSets} × ${spec.targets(0, rx?.sets ?: item.targetSets).joinToString("-")}"
        item.repMin == item.repMax -> "${rx?.sets ?: item.targetSets} × ${item.repMin}"
        else -> "${rx?.sets ?: item.targetSets} × ${item.repMin}–${item.repMax}"
    }
    val kg = when {
        isDuration -> 0f
        rx != null && rx.weight > 0f -> rx.weight
        else -> item.targetWeight
    }
    val right = if (kg > 0f) "${kg.trimNum()} kg · $reps" else reps
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).then(if (clickable) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.muscle(ex?.muscleGroup ?: "Diğer")))
        Spacer(Modifier.width(10.dp))
        Text(
            (if (item.supersetGroup > 0) "⚡ " else "") + item.customName.ifBlank { ex?.name ?: "?" },
            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            color = if (item.isWarmup) MaterialTheme.fit.muted else MaterialTheme.colorScheme.onSurface
        )
        if (item.isWarmup) { Badge("Isınma", Palette.warning); Spacer(Modifier.width(6.dp)) }
        if (cfg != null && !cfg.isDefault) { Text(if (cfg.reverse) "↓" else "✎", color = MaterialTheme.fit.accent, style = MaterialTheme.typography.labelMedium); Spacer(Modifier.width(6.dp)) }
        Text(right, style = MaterialTheme.typography.labelMedium.mono(), color = MaterialTheme.fit.muted)
    }
}

/* ------------------------------ Hareket paneli ----------------------------- */

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ProgramItemSheet(
    vm: AppViewModel,
    item: RoutineItemEntity,
    ex: ExerciseEntity?,
    cfg: LadderConfig?,
    rx: com.example.core.Prescription?,
    isFirst: Boolean,
    isLast: Boolean,
    onRequestCombine: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isDuration = ex?.trackingType == ExerciseEntity.TRACK_DURATION
    val baseName = ex?.name ?: "Bilinmeyen hareket"
    var name by remember(item.id) { mutableStateOf(item.customName.ifBlank { baseName }) }
    var showAlt by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    val history = remember(item.exerciseId) { vm.itemHistory(item.exerciseId) }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            /* Başlık + geçmiş */
            Row(verticalAlignment = Alignment.CenterVertically) {
                MuscleAvatar(ex?.muscleGroup ?: "Diğer", 42.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name.ifBlank { baseName }, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            history.first?.let { "Son: $it" } ?: "Henüz kayıt yok",
                            history.second.takeIf { it > 0f }?.let { "1RM ~${it.trimNum()} kg" }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted
                    )
                }
            }

            /* Bir sonraki seans önerisi + başlangıç ağırlığı */
            if (!isDuration) {
                val hasHistory = rx != null && rx.action != com.example.core.ProgressAction.FIRST
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.fit.accent.copy(alpha = 0.10f)).padding(12.dp)
                ) {
                    OverlineText("Sıradaki seans", color = MaterialTheme.fit.accent)
                    Spacer(Modifier.height(4.dp))
                    if (hasHistory && rx != null) {
                        Text(rx.headline, style = MaterialTheme.typography.titleLarge.mono().copy(fontWeight = FontWeight.SemiBold))
                        Text(rx.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
                    } else {
                        Text("Henüz bu günde kayıt yok — ilk seans ağırlığını gir:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
                        Spacer(Modifier.height(6.dp))
                        NumberField("Başlangıç ağırlığı (kg)", item.targetWeight.takeIf { it > 0f }?.trimNum() ?: "") {
                            vm.updateItem(item.copy(targetWeight = it.replace(',', '.').toFloatOrNull() ?: 0f))
                        }
                    }
                }
            }

            /* Set + dinlenme */
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Stepper("Set", item.targetSets, 1, 1, 20, "") { vm.updateItem(item.copy(targetSets = it)) } }
                Box(Modifier.weight(1f)) { Stepper("Dinlenme", item.restSeconds, 15, 0, 600, " sn") { vm.updateItem(item.copy(restSeconds = it)) } }
            }

            /* Tekrar: merdiven ya da aralık */
            if (isDuration) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { Stepper("Min (sn)", item.repMin, 5, 5, item.repMax, "") { vm.updateItem(item.copy(repMin = it)) } }
                    Box(Modifier.weight(1f)) { Stepper("Maks (sn)", item.repMax, 5, item.repMin, 600, "") { vm.updateItem(item.copy(repMax = it)) } }
                }
            } else if (ex != null) {
                LadderEditor(item, ex, cfg ?: LadderConfig(), onConfig = { vm.setLadderConfig(item.id, it) }, onUpdateItem = { vm.updateItem(it) })
            }

            /* Hızlı eylemler */
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Alternatif", { showAlt = true }, Modifier.weight(1f), Icons.Default.SwapHoriz, MaterialTheme.fit.accent)
                GhostButton(if (item.supersetGroup > 0) "SS'den ayır" else "Süperset", {
                    if (item.supersetGroup > 0) vm.setRoutineItemSuperset(item.dayId, item.id, 0) else { onDismiss(); onRequestCombine() }
                }, Modifier.weight(1f), Icons.Default.Bolt, Palette.warning)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ChoiceChip(if (item.isWarmup) "Isınma hareketi" else "Ana hareket", item.isWarmup, { vm.toggleItemWarmup(item) }, color = Palette.warning)
                Spacer(Modifier.weight(1f))
                if (!isFirst) RoundIconButton(Icons.Default.ArrowUpward, MaterialTheme.fit.muted, 38.dp, MaterialTheme.fit.elevated) { vm.moveItem(item.dayId, item.id, true) }
                if (!isLast) RoundIconButton(Icons.Default.ArrowDownward, MaterialTheme.fit.muted, 38.dp, MaterialTheme.fit.elevated) { vm.moveItem(item.dayId, item.id, false) }
                RoundIconButton(Icons.Default.Delete, MaterialTheme.fit.danger, 38.dp, MaterialTheme.fit.danger.copy(alpha = 0.12f)) { onDelete() }
            }

            /* Diğer */
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { showMore = !showMore }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Diğer ayarlar", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.fit.muted, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ExpandMore, null, tint = MaterialTheme.fit.muted, modifier = Modifier.rotate(if (showMore) 180f else 0f))
            }
            AnimatedVisibility(showMore) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FitTextField(name, { name = it; vm.updateItem(item.copy(customName = if (it == baseName) "" else it)) }, "Hareket adı (bu günde)")
                    FitTextField(item.note, { vm.updateItem(item.copy(note = it)) }, "Not (tempo, kavrama…)")
                    if (ex != null) {
                        FitTextField(ex.videoUrl, { vm.saveExercise(ex.copy(videoUrl = it.trim())) }, "Video linki")
                        GhostButton("Videoyu aç", {
                            val url = ex.videoUrl.ifBlank { "https://www.youtube.com/results?search_query=${android.net.Uri.encode("${name.ifBlank { baseName }} egzersizi")}" }
                            openUrl(context, url)
                        }, Modifier.fillMaxWidth(), Icons.Default.PlayCircle)
                    }
                }
            }
        }
    }

    if (showAlt) {
        val options = remember(item.id, item.exerciseId) { vm.alternativesForItem(item) }
        AlertDialog(
            onDismissRequest = { showAlt = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Column {
                    Text("Kalıcı olarak değiştir", style = MaterialTheme.typography.titleLarge)
                    Text("${name.ifBlank { baseName }} yerine, bu günde", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
                }
            },
            text = {
                if (options.isEmpty()) Text("Uygun alternatif bulunamadı.", color = MaterialTheme.fit.muted)
                else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { o ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.fit.elevated)
                                .clickable { vm.swapItemExercise(item, o.exercise.id, o.weightHint); name = o.exercise.name; showAlt = false }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(o.exercise.name, style = MaterialTheme.typography.titleSmall)
                            Text(o.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAlt = false }) { Text("Vazgeç", color = MaterialTheme.fit.muted) } }
        )
    }
}

/* ------------------------------ Merdiven ayarı ----------------------------- */

@Composable
private fun LadderEditor(
    item: RoutineItemEntity,
    ex: ExerciseEntity,
    cfg: LadderConfig,
    onConfig: (LadderConfig) -> Unit,
    onUpdateItem: (RoutineItemEntity) -> Unit
) {
    val auto = remember(ex.id) { RepScheme.classify(ex.name, ex.muscleGroup, ex.equipment, ex.trackingType) }
    val spec = cfg.resolve(auto)
    val sets = item.targetSets.coerceAtLeast(1)

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.fit.elevated).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OverlineText("Tekrar merdiveni")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LadderMode.values().forEach { m ->
                ChoiceChip(m.label, cfg.mode == m, {
                    onConfig(
                        when (m) {
                            LadderMode.TYPE -> cfg.copy(mode = m, type = cfg.type ?: auto ?: RepScheme.SEMI)
                            LadderMode.CUSTOM -> if (cfg.mode == LadderMode.CUSTOM) cfg else {
                                val s = spec ?: auto?.let { a -> cfg.copy(type = a).resolve(a) }
                                cfg.copy(mode = m, base = s?.base ?: 8, steps = s?.steps ?: 4, spread = s?.spread ?: 2)
                            }
                            else -> cfg.copy(mode = m)
                        }
                    )
                })
            }
        }

        when (cfg.mode) {
            LadderMode.TYPE -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RepScheme.values().forEach { t -> ChoiceChip(t.label, cfg.type == t, { onConfig(cfg.copy(type = t)) }) }
            }
            LadderMode.CUSTOM -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Stepper("İlk basamak (ilk set)", cfg.base, 1, 1, 40, " tekrar") { onConfig(cfg.copy(base = it)) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { Stepper("Basamak", cfg.steps, 1, 1, 12, "") { onConfig(cfg.copy(steps = it)) } }
                    Box(Modifier.weight(1f)) { Stepper("Setler arası fark", cfg.spread, 1, 0, 10, "") { onConfig(cfg.copy(spread = it)) } }
                }
            }
            else -> Unit
        }

        if (spec != null) {
            LabeledSwitch("Ters merdiven", "Setler azalan: ${spec.copy(reverse = true).targets(0, sets).joinToString("-")} (aynı ağırlıkta yorgunluğa uygun)", cfg.reverse) {
                onConfig(cfg.copy(reverse = it))
            }
            val first = spec.targets(0, sets).joinToString("-")
            val mid = if (spec.lastStep >= 2) " → ${spec.targets(1, sets).joinToString("-")} → …" else ""
            val last = if (spec.lastStep >= 1) " → ${spec.targets(spec.lastStep, sets).joinToString("-")}" else ""
            Text(
                "${spec.label}: $first$mid$last",
                style = MaterialTheme.typography.titleSmall.mono(), color = MaterialTheme.fit.accent
            )
            Text(
                "Her seans tüm hedefler tutarsa bir üst basamağa çıkılır; son basamak tamamlanınca ağırlık artar ve ilk basamağa dönülür. Seans önerileri bu ayarı kullanır.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted
            )
        } else {
            Text(
                if (cfg.mode == LadderMode.OFF) "Merdiven kapalı: aşağıdaki aralıkla klasik çift progresyon. Tüm setler üst sınıra ulaşınca ağırlık artar."
                else "Bu hareket için otomatik merdiven yok; aralık kullanılır. İstersen \"Tür seç\" ya da \"Özel\" ile merdiven tanımlayabilirsin.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Stepper("Min tekrar", item.repMin, 1, 1, item.repMax, "") { onUpdateItem(item.copy(repMin = it)) } }
                Box(Modifier.weight(1f)) { Stepper("Maks tekrar", item.repMax, 1, item.repMin, 100, "") { onUpdateItem(item.copy(repMax = it)) } }
            }
        }
    }
}

/* ------------------------------ Küçük parçalar ----------------------------- */

@Composable
private fun Stepper(label: String, value: Int, step: Int, min: Int, max: Int, suffix: String, onChange: (Int) -> Unit) {
    Column {
        Text(label.uppercase(java.util.Locale("tr")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StepKey("−") { onChange((value - step).coerceIn(min, max)) }
            Box(
                Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) { Text("$value$suffix", style = MaterialTheme.typography.titleMedium.mono(), maxLines = 1) }
            StepKey("+") { onChange((value + step).coerceIn(min, max)) }
        }
    }
}

@Composable
private fun StepKey(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.fit.accent.copy(alpha = 0.14f)).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(symbol, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.fit.accent) }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Column {
        OverlineText(label)
        Spacer(Modifier.height(5.dp))
        androidx.compose.material3.OutlinedTextField(
            value = text,
            onValueChange = { v -> val f = v.filter { it.isDigit() || it == '.' || it == ',' }.take(6); text = f; onChange(f) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleSmall,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.fit.accent,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.fit.accent
            )
        )
    }
}


/** Program gün kartında açık seans şeridi: nabız noktası, süre, ilerleme, Devam et / Bitir. */
@Composable
private fun ActiveSessionStrip(elapsed: String, done: Int, total: Int, onResume: () -> Unit, onFinish: () -> Unit) {
    val green = MaterialTheme.fit.success
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(900), androidx.compose.animation.core.RepeatMode.Reverse
        ), label = "dot"
    )
    val progress = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(18.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(green.copy(alpha = 0.26f), green.copy(alpha = 0.08f))))
            .border(1.5.dp, green.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(green.copy(alpha = pulse)))
            Spacer(Modifier.width(8.dp))
            Text(
                "SEANS DEVAM EDİYOR",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                color = green, modifier = Modifier.weight(1f)
            )
            Text(elapsed, style = MaterialTheme.typography.titleLarge.mono().copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(green.copy(alpha = 0.18f))) {
                Box(Modifier.fillMaxWidth(progress).height(8.dp).clip(RoundedCornerShape(4.dp)).background(green))
            }
            Spacer(Modifier.width(10.dp))
            Text("$done/$total set", style = MaterialTheme.typography.labelMedium.mono(), color = MaterialTheme.fit.muted)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(13.dp)).background(green).clickable { onResume() },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Devam et", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
            Row(
                Modifier.height(44.dp).clip(RoundedCornerShape(13.dp))
                    .border(1.dp, MaterialTheme.fit.danger.copy(alpha = 0.6f), RoundedCornerShape(13.dp))
                    .clickable { onFinish() }.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Stop, null, tint = MaterialTheme.fit.danger, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Bitir", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.fit.danger)
            }
        }
    }
}


/* ------------------------------ Gün düzenleyici ------------------------------ */

/**
 * Tam sayfa gün düzenleyici: ad, odak, haftanın günü + hareket listesi (sıra, silme/geri al).
 * Harekete dokununca programdaki hareket paneli açılır (tüm ayarlar).
 */
@Composable
private fun DayEditor(
    vm: AppViewModel,
    day: RoutineDayEntity,
    items: List<RoutineItemEntity>,
    exerciseMap: Map<Long, ExerciseEntity>,
    ladderCfgs: Map<Long, LadderConfig>,
    plan: Map<Long, com.example.core.Prescription>,
    onOpenItem: (Long) -> Unit,
    onAdd: () -> Unit,
    onAddSuperset: () -> Unit,
    onClose: () -> Unit
) {
    var name by remember(day.id) { mutableStateOf(day.name) }
    var focus by remember(day.id) { mutableStateOf(day.focus) }
    var weekday by remember(day.id) { mutableStateOf(day.weekday) }
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val sorted = items.sortedBy { it.orderIndex }

    fun saveAndClose() {
        if (name.isNotBlank() && (name.trim() != day.name || focus.trim() != day.focus || weekday != day.weekday)) {
            vm.updateDay(day.copy(name = name.trim(), focus = focus.trim(), weekday = weekday))
        }
        onClose()
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = { saveAndClose() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader("Günü düzenle", day.name, onBack = { saveAndClose() }) {
                    Text(
                        "Bitti", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.fit.accent,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { saveAndClose() }.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                androidx.compose.foundation.lazy.LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "head") {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).cardBackground()
                                .border(1.dp, MaterialTheme.fit.cardBorder, RoundedCornerShape(20.dp)).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FitTextField(name, { name = it }, "Gün adı", placeholder = "Örn: A Günü / Push")
                            FitTextField(focus, { focus = it }, "Odak", placeholder = "Örn: Göğüs, Omuz, Triceps")
                            OverlineText("Haftanın günü")
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ChoiceChip("Serbest", weekday == 0, { weekday = 0 })
                                (1..7).forEach { d -> ChoiceChip(WEEKDAY_SHORT[d - 1], weekday == d, { weekday = d }) }
                            }
                        }
                    }
                    item(key = "listhead") {
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            OverlineText("Hareketler · ${sorted.size}", modifier = Modifier.weight(1f))
                            Text("dokun: ayarlar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
                        }
                    }
                    if (sorted.isEmpty()) item(key = "empty") {
                        Text("Bu günde hareket yok. Aşağıdan ekleyebilirsin.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
                    }
                    sorted.forEachIndexed { i, it ->
                        item(key = "it_${it.id}") {
                            val ex = exerciseMap[it.exerciseId]
                            val rx = plan[it.id]
                            val kg = rx?.weight?.takeIf { w -> w > 0f } ?: it.targetWeight
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, if (it.supersetGroup > 0) Palette.warning.copy(alpha = 0.4f) else MaterialTheme.fit.cardBorder, RoundedCornerShape(16.dp))
                                    .clickable { onOpenItem(it.id) }.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${i + 1}", style = MaterialTheme.typography.labelLarge.mono(), color = MaterialTheme.fit.muted, modifier = Modifier.width(22.dp))
                                Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.muscle(ex?.muscleGroup ?: "Diğer")))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        (if (it.supersetGroup > 0) "⚡ " else "") + it.customName.ifBlank { ex?.name ?: "?" },
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        listOfNotNull(
                                            if (it.isWarmup) "Isınma" else null,
                                            "${it.targetSets} × ${if (it.repMin == it.repMax) "${it.repMin}" else "${it.repMin}–${it.repMax}"}",
                                            if (kg > 0f) "${kg.trimNum()} kg" else null,
                                            "${it.restSeconds} sn"
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted, maxLines = 1
                                    )
                                }
                                RoundIconButton(Icons.Default.KeyboardArrowUp, if (i > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.fit.muted.copy(alpha = 0.3f), 34.dp, Color.Transparent) {
                                    if (i > 0) vm.moveItem(day.id, it.id, true)
                                }
                                RoundIconButton(Icons.Default.KeyboardArrowDown, if (i < sorted.lastIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.fit.muted.copy(alpha = 0.3f), 34.dp, Color.Transparent) {
                                    if (i < sorted.lastIndex) vm.moveItem(day.id, it.id, false)
                                }
                                RoundIconButton(Icons.Default.Delete, MaterialTheme.fit.danger, 34.dp, Color.Transparent) {
                                    val cfg = ladderCfgs[it.id]
                                    val removed = it
                                    vm.deleteItem(removed); vm.setLadderConfig(removed.id, null)
                                    scope.launch {
                                        snackbar.currentSnackbarData?.dismiss()
                                        val r = snackbar.showSnackbar("${removed.customName.ifBlank { ex?.name ?: "Hareket" }} silindi", actionLabel = "Geri al",
                                            duration = androidx.compose.material3.SnackbarDuration.Long)
                                        if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) vm.restoreItem(removed, cfg)
                                    }
                                }
                            }
                        }
                    }
                    item(key = "add") {
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton("Hareket ekle", onAdd, Modifier.weight(1f), Icons.Default.Add, MaterialTheme.fit.accent)
                            GhostButton("Süperset ekle", onAddSuperset, Modifier.weight(1f), Icons.Default.Bolt, Palette.warning)
                        }
                    }
                }
            }
            androidx.compose.material3.SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp))
        }
    }
}
