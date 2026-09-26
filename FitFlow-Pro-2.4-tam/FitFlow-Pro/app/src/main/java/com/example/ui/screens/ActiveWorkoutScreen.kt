package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.KeyboardArrowDown
import com.example.ui.theme.mono
import com.example.ui.theme.overline
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.core.Calc
import com.example.core.LoadKind
import com.example.core.LoadingProfile
import com.example.core.Prescription
import com.example.core.ProgressAction
import com.example.core.formatDuration
import com.example.core.formatTonnage
import com.example.core.loadKindOf
import com.example.core.trimNum
import com.example.data.ExerciseEntity
import com.example.data.SessionExercise
import com.example.data.WorkoutSetEntity
import com.example.ui.AppViewModel
import com.example.ui.SessionReview
import com.example.ui.components.AccentButton
import com.example.ui.components.Badge
import com.example.ui.components.CheckCircle
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.FitCard
import com.example.ui.components.FitTextField
import com.example.ui.components.GhostButton
import com.example.ui.components.RoundIconButton
import com.example.ui.components.ThinProgress
import com.example.ui.theme.Palette
import com.example.ui.theme.fit
import kotlinx.coroutines.delay

/* ==========================================================================
 * Aktif seans ekranı
 *
 * Tasarım ilkeleri:
 *  - Seans, progresyon motorunun reçetesiyle hazır açılır: bir seti kaydetmek = ✓'e tek dokunuş.
 *  - Değer değiştirmek gerekirse set satırına dokun → büyük +/- butonlu editör (terli elle, tek elle).
 *  - Sıradaki set vurgulanır; gereksiz rozet ve ikonlar ana akıştan çıkarıldı (hepsi ⋮ menüsünde).
 * ========================================================================== */

@Composable
fun ActiveWorkoutScreen(vm: AppViewModel, nav: NavHostController) {
    val workout by vm.activeWorkout.collectAsStateWithLifecycle()
    val exercises by vm.sessionExercises.collectAsStateWithLifecycle()
    val elapsed by vm.elapsedSeconds.collectAsStateWithLifecycle()
    val bar by vm.settings.barWeight.collectAsStateWithLifecycle()
    val barStep by vm.settings.increment.collectAsStateWithLifecycle()
    val dbStep by vm.settings.dumbbellStep.collectAsStateWithLifecycle()
    val machineStep by vm.settings.machineStep.collectAsStateWithLifecycle()
    val profile = remember(bar, barStep, dbStep, machineStep) { LoadingProfile(bar, barStep, dbStep, machineStep) }
    val context = LocalContext.current

    var showPicker by remember { mutableStateOf(false) }
    var showSupersetPicker by remember { mutableStateOf(false) }
    var exerciseToCombine by remember { mutableStateOf<SessionExercise?>(null) }
    var showFinish by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showTips by remember { mutableStateOf<SessionExercise?>(null) }
    /** Düzenlenen set: (hareket sırası, set id). Güncel veri her seferinde listeden okunur. */
    var editing by remember { mutableStateOf<Pair<Int, Long>?>(null) }

    val listState = rememberLazyListState()
    var highlightedId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        vm.scrollToExerciseEvent.collect { targetId ->
            val index = exercises.indexOfFirst { it.exerciseId == targetId }
            if (index >= 0) {
                listState.animateScrollToItem(index)
                highlightedId = targetId
                delay(2000)
                if (highlightedId == targetId) {
                    highlightedId = null
                }
            }
        }
    }

    if (workout == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Aktif seans yok", color = MaterialTheme.fit.muted)
        }
        return
    }

    BackHandler { nav.popBackStack() }

    val totalSets = exercises.sumOf { it.totalSets }
    val doneSets = exercises.sumOf { it.completedSets }
    val volume = exercises.sumOf { it.volume.toDouble() }.toFloat()
    // Sıradaki set: ilk tamamlanmamış set (üstten aşağı).
    val nextSetId = exercises.asSequence().flatMap { it.sets.asSequence() }.firstOrNull { !it.isCompleted }?.id

    Column(Modifier.fillMaxSize()) {
        SessionTopBar(
            title = workout!!.title,
            isDeload = workout!!.isDeload,
            elapsed = elapsed,
            volume = volume,
            done = doneSets,
            total = totalSets,
            onMinimize = { nav.popBackStack() },
            onFinish = { showFinish = true },
            onDiscard = { showDiscard = true }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding()
                .imePadding(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(exercises, key = { _, e -> "${e.exerciseId}_${e.order}" }) { index, se ->
                ExerciseLogCard(
                    se = se,
                    number = index + 1,
                    profile = profile,
                    nextSetId = nextSetId,
                    fallbackSuggestion = if (se.prescription == null) vm.suggestionFor(se) else "",
                    isHighlighted = highlightedId == se.exerciseId,
                    onToggleSet = { set ->
                        if (se.trackingType == ExerciseEntity.TRACK_DURATION) {
                            vm.toggleTimedSetDone(set, set.durationSeconds, se.restSeconds)
                        } else {
                            vm.toggleSetDone(set, se.restSeconds)
                        }
                    },
                    onEditSet = { set -> editing = se.order to set.id },
                    onUpdateSet = vm::updateSet,
                    onDeleteSet = vm::deleteSet,
                    onAddSet = { vm.addSetRow(se.order, se.exerciseId) },
                    onRemoveExercise = { vm.removeExerciseFromSession(se.order) },
                    onToggleWarmup = { vm.toggleExerciseWarmup(se.order, !se.isWarmup) },
                    onRequestCombine = { exerciseToCombine = se },
                    onSeparateSuperset = { vm.setSessionExerciseSuperset(se.order, 0) },
                    onDissolveSuperset = { group -> vm.dissolveSessionSuperset(group) },
                    onStartRest = { vm.startRest(se.restSeconds, se.name, se.exerciseId) },
                    onShowTips = { showTips = se },
                    onRenameExercise = { newName ->
                        if (newName.isNotBlank()) {
                            vm.renameSessionExercise(se.order, newName)
                        }
                    }
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton(
                        "Hareket ekle",
                        { showPicker = true },
                        Modifier.weight(1f),
                        Icons.Default.Add,
                        MaterialTheme.fit.accent
                    )
                    GhostButton(
                        "Süperset ekle",
                        { showSupersetPicker = true },
                        Modifier.weight(1f),
                        Icons.Default.Bolt,
                        Palette.warning
                    )
                }
            }

            if (exercises.isEmpty()) {
                item {
                    Text(
                        "Bu seansta henüz hareket yok. Yukarıdaki butonla ekleyebilirsin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.fit.muted,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        }
    }

    /* ------------------------------ Set editörü ------------------------------ */
    editing?.let { (order, setId) ->
        val se = exercises.firstOrNull { it.order == order }
        val set = se?.sets?.firstOrNull { it.id == setId }
        if (se == null || set == null) {
            LaunchedEffect(order, setId) { editing = null }
        } else {
            SetEditorSheet(
                se = se,
                set = set,
                profile = profile,
                onDismiss = { editing = null },
                onSave = { updated -> vm.updateSet(updated); editing = null },
                onSaveAndComplete = { updated ->
                    if (updated.isCompleted) vm.updateSet(updated)
                    else if (se.trackingType == ExerciseEntity.TRACK_DURATION) {
                        vm.updateSet(updated)
                        vm.toggleTimedSetDone(updated, updated.durationSeconds, se.restSeconds)
                    } else vm.toggleSetDone(updated, se.restSeconds)
                    editing = null
                }
            )
        }
    }

    if (showPicker) {
        ExercisePickerDialog(
            vm = vm,
            title = "Seansa hareket ekle",
            excludeIds = exercises.map { it.exerciseId }.toSet(),
            onPick = { ex ->
                vm.addExerciseToSession(ex.id)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }

    if (showSupersetPicker) {
        SupersetPickerDialog(
            vm = vm,
            title = "Seansa Süperset Ekle",
            onPickSuperset = { exList ->
                vm.addSupersetToWorkout(exList.map { it.id })
                showSupersetPicker = false
            },
            onDismiss = { showSupersetPicker = false }
        )
    }

    if (exerciseToCombine != null) {
        val current = exerciseToCombine!!
        CombineExerciseDialog(
            title = "Süperset Olarak Birleştir",
            sourceName = current.name,
            candidates = exercises.filter { it.order != current.order },
            candidateName = { it.name },
            candidateGroup = { it.muscleGroup },
            onCombineWith = { target ->
                vm.combineSessionExercises(current.order, target.order)
                exerciseToCombine = null
            },
            onDismiss = { exerciseToCombine = null }
        )
    }

    if (showDiscard) {
        ConfirmDialog(
            title = "Seansı kaydetmeden sonlandır",
            text = "Bu antrenman kaydedilmeden silinecek. Emin misin?",
            confirmLabel = "Kaydetmeden Çık",
            destructive = true,
            onConfirm = {
                showDiscard = false
                vm.discardWorkout { nav.popBackStack() }
            },
            onDismiss = { showDiscard = false }
        )
    }

    if (showFinish) {
        val review = remember(exercises) { vm.sessionReview() }
        FinishDialog(
            doneSets = doneSets,
            totalSets = totalSets,
            elapsed = elapsed,
            review = review,
            onDismiss = { showFinish = false },
            onFinish = { notes, feeling ->
                showFinish = false
                vm.finishWorkout(notes, feeling) { nav.popBackStack() }
            }
        )
    }

    showTips?.let { se ->
        AlertDialog(
            onDismissRequest = { showTips = null },
            shape = RoundedCornerShape(22.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(se.name, style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        se.prescription?.let { "${it.headline} · ${it.reason}" } ?: vm.suggestionFor(se),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.fit.accent
                    )
                    val ex = vm.exerciseById(se.exerciseId)
                    if (!ex?.instructions.isNullOrBlank()) {
                        Text(ex!!.instructions, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.fit.muted)
                    }
                    if (!ex?.tips.isNullOrBlank()) {
                        Text("İpucu: ${ex!!.tips}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
                    }
                    if (se.previous.isNotEmpty()) {
                        Text("Önceki seans", style = MaterialTheme.typography.titleSmall)
                        se.previous.forEach {
                            Text(
                                "${it.setNumber}. set  ·  ${it.weightKg.trimNum()} kg × ${it.reps}" +
                                    if (it.rpe > 0f) "  ·  RPE ${it.rpe.trimNum()}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.fit.muted
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTips = null }) {
                    Text("Kapat", color = MaterialTheme.fit.accent)
                }
            }
        )
    }
}

/* ------------------------------- Üst çubuk --------------------------------- */

@Composable
private fun SessionTopBar(
    title: String,
    isDeload: Boolean,
    elapsed: Int,
    volume: Float,
    done: Int,
    total: Int,
    onMinimize: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButton(Icons.Default.KeyboardArrowDown, MaterialTheme.colorScheme.onSurface, 40.dp, MaterialTheme.fit.elevated) { onMinimize() }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isDeload) Badge("Deload", Palette.violet)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${formatDuration(elapsed)} · $done/$total set · ${formatTonnage(volume)}",
                    style = MaterialTheme.typography.labelMedium.mono(),
                    color = MaterialTheme.fit.muted,
                    maxLines = 1
                )
            }
            Box {
                RoundIconButton(Icons.Default.MoreVert, MaterialTheme.fit.muted, 40.dp, Color.Transparent) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Kaydetmeden sil") },
                        onClick = { menu = false; onDiscard() },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.fit.danger) }
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.fit.success,
                modifier = Modifier.clickable { onFinish() }
            ) {
                Text(
                    "Bitir",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF062017),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
                )
            }
        }
        // İnce ilerleme çizgisi
        Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.surface)) {
            Box(
                Modifier
                    .fillMaxWidth(if (total == 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(MaterialTheme.fit.accent)
            )
        }
    }
}

/* ---------------------------- Hareket kayıt kartı --------------------------- */

@Composable
private fun ExerciseLogCard(
    se: SessionExercise,
    number: Int,
    profile: LoadingProfile,
    nextSetId: Long?,
    fallbackSuggestion: String,
    isHighlighted: Boolean = false,
    onToggleSet: (WorkoutSetEntity) -> Unit,
    onEditSet: (WorkoutSetEntity) -> Unit,
    onUpdateSet: (WorkoutSetEntity) -> Unit,
    onDeleteSet: (WorkoutSetEntity) -> Unit,
    onAddSet: () -> Unit,
    onRemoveExercise: () -> Unit,
    onToggleWarmup: () -> Unit,
    onRequestCombine: () -> Unit,
    onSeparateSuperset: () -> Unit,
    onDissolveSuperset: (Int) -> Unit,
    onStartRest: () -> Unit,
    onShowTips: () -> Unit,
    onRenameExercise: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember(se.name) { mutableStateOf(se.name) }
    val isDuration = se.trackingType == ExerciseEntity.TRACK_DURATION
    val isRepsOnly = se.trackingType == ExerciseEntity.TRACK_REPS
    val isInSuperset = se.supersetGroup > 0

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Hareketi Yeniden Adlandır") },
            text = {
                FitTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = "Yeni Hareket Adı"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRenameDialog = false
                        if (renameText.isNotBlank()) {
                            onRenameExercise?.invoke(renameText)
                        }
                    }
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    val isCurrent = nextSetId != null && se.sets.any { it.id == nextSetId }
    FitCard(
        corner = 20.dp,
        border = when {
            isHighlighted -> MaterialTheme.fit.accent
            isCurrent -> MaterialTheme.fit.accent.copy(alpha = 0.3f)
            isInSuperset -> Palette.warning.copy(alpha = 0.45f)
            else -> MaterialTheme.fit.cardBorder
        },
        contentPadding = PaddingValues(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 8.dp)
    ) {
        /* Başlık: sıra numarası + isim + hedef. Diğer her şey ⋮ menüsünde. */
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            se.isDone -> MaterialTheme.fit.success
                            isCurrent -> MaterialTheme.fit.accent.copy(alpha = 0.15f)
                            else -> MaterialTheme.fit.elevated
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$number",
                    style = MaterialTheme.typography.labelLarge.mono().copy(fontWeight = FontWeight.SemiBold),
                    color = when {
                        se.isDone -> Color(0xFF062017)
                        isCurrent -> MaterialTheme.fit.accent
                        else -> MaterialTheme.fit.muted
                    }
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    se.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isInSuperset) Badge("SS ${se.supersetGroup}", Palette.warning)
                    if (se.isWarmup) Badge("Isınma", Palette.warning)
                    Text(
                        if (isDuration) "Dinlenme ${se.restSeconds} sn"
                        else "${se.targetRepMin}-${se.targetRepMax} tekrar · ${se.restSeconds} sn",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.fit.muted,
                        maxLines = 1
                    )
                }
            }
            Text(
                "${se.completedSets}/${se.totalSets}",
                style = MaterialTheme.typography.labelLarge.mono(),
                color = if (se.isDone) MaterialTheme.fit.success else MaterialTheme.fit.muted
            )
            Box {
                RoundIconButton(Icons.Default.MoreVert, MaterialTheme.fit.muted, 40.dp, Color.Transparent) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Teknik & öneri") },
                        onClick = { menu = false; onShowTips() },
                        leadingIcon = { Icon(Icons.Default.Lightbulb, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Videoyu aç") },
                        onClick = {
                            menu = false
                            val targetUrl = if (se.videoUrl.isNotBlank()) se.videoUrl
                            else "https://www.youtube.com/results?search_query=${android.net.Uri.encode("${se.name} egzersizi")}"
                            openUrl(context, targetUrl)
                        },
                        leadingIcon = { Icon(Icons.Default.PlayCircle, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Dinlenmeyi başlat") },
                        onClick = { menu = false; onStartRest() },
                        leadingIcon = { Icon(Icons.Default.Timer, null) }
                    )
                    if (isInSuperset) {
                        DropdownMenuItem(
                            text = { Text("Süpersetten ayır") },
                            onClick = { menu = false; onSeparateSuperset() },
                            leadingIcon = { Icon(Icons.Default.LinkOff, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Tüm süperseti dağıt") },
                            onClick = { menu = false; onDissolveSuperset(se.supersetGroup) },
                            leadingIcon = { Icon(Icons.Default.Bolt, null, tint = Palette.warning) }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Başka hareketle süperset yap") },
                            onClick = { menu = false; onRequestCombine() },
                            leadingIcon = { Icon(Icons.Default.Link, null) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (se.isWarmup) "Ana harekete çevir" else "Isınma hareketine çevir") },
                        onClick = { menu = false; onToggleWarmup() },
                        leadingIcon = { Icon(Icons.Default.Lightbulb, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Yeniden adlandır") },
                        onClick = { menu = false; showRenameDialog = true },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Hareketi çıkar") },
                        onClick = { menu = false; onRemoveExercise() },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.fit.danger) }
                    )
                }
            }
        }

        /* Bugünün reçetesi */
        val rx = se.prescription
        if (rx != null && !se.isDone) {
            Spacer(Modifier.height(10.dp))
            PrescriptionPanel(rx)
        } else if (rx == null && fallbackSuggestion.isNotBlank() && !se.isDone) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.fit.elevated)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.fit.accent, modifier = Modifier.size(15.dp))
                Text(fallbackSuggestion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
            }
        }

        /* Barbell ısınma rampası + plaka dizilimi */
        val workWeight = se.sets.firstOrNull { !it.isWarmup }?.weightKg ?: 0f
        if (!se.isWarmup && !isDuration && !se.isDone && loadKindOf(se.equipment) == LoadKind.BARBELL &&
            workWeight >= profile.barKg + 10f && se.completedSets == 0
        ) {
            Spacer(Modifier.height(8.dp))
            WarmupRamp(workWeight, profile.barKg)
        }

        Spacer(Modifier.height(10.dp))

        // Sütun başlıkları
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
            HeaderCell("SET", Modifier.width(36.dp))
            HeaderCell("ÖNCEKİ", Modifier.weight(1.3f))
            if (isDuration) {
                HeaderCell("SÜRE", Modifier.weight(1.6f))
            } else {
                if (!isRepsOnly) HeaderCell("KG", Modifier.weight(1f))
                HeaderCell("TEKRAR", Modifier.weight(1f))
            }
            HeaderCell("RPE", Modifier.weight(0.8f))
            Spacer(Modifier.width(48.dp))
        }
        Spacer(Modifier.height(2.dp))

        se.sets.forEach { set ->
            val prevSet = se.previous.firstOrNull { it.setNumber == set.setNumber } ?: se.previous.lastOrNull()
            SetRow(
                set = set,
                previous = prevSet,
                isNext = set.id == nextSetId,
                isDuration = isDuration,
                isRepsOnly = isRepsOnly,
                onToggle = {
                    val resolved = set.copy(
                        weightKg = if (set.weightKg > 0f) set.weightKg else (prevSet?.weightKg ?: set.weightKg),
                        reps = if (set.reps > 0) set.reps else (prevSet?.reps ?: set.reps),
                        durationSeconds = if (set.durationSeconds > 0) set.durationSeconds else (prevSet?.durationSeconds ?: set.durationSeconds)
                    )
                    onToggleSet(resolved)
                },
                onEdit = { onEditSet(set) },
                onUpdate = onUpdateSet,
                onDelete = { onDeleteSet(set) }
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onAddSet() }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Add, null, tint = MaterialTheme.fit.muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Set ekle", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.fit.muted)
        }
    }
}

/* ------------------------------ Reçete paneli ------------------------------ */

@Composable
internal fun actionColor(action: ProgressAction): Color = when (action) {
    ProgressAction.INCREASE -> MaterialTheme.fit.success
    ProgressAction.REPS -> MaterialTheme.fit.accent
    ProgressAction.HOLD -> MaterialTheme.fit.muted
    ProgressAction.DECREASE -> Palette.warning
    ProgressAction.DELOAD -> Palette.violet
    ProgressAction.FIRST -> MaterialTheme.fit.muted
}

internal fun actionIcon(action: ProgressAction): ImageVector = when (action) {
    ProgressAction.INCREASE, ProgressAction.REPS -> Icons.Default.TrendingUp
    ProgressAction.DECREASE, ProgressAction.DELOAD -> Icons.Default.TrendingDown
    else -> Icons.Default.TrendingFlat
}

@Composable
private fun PrescriptionPanel(rx: Prescription) {
    val color = actionColor(rx.action)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(actionIcon(rx.action), null, tint = color, modifier = Modifier.size(18.dp))
            Text(
                "BUGÜN",
                style = MaterialTheme.typography.labelSmall.overline(),
                color = color
            )
            Text(
                rx.headline,
                style = MaterialTheme.typography.titleMedium.mono().copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Badge(rx.action.label, color)
        }
        Spacer(Modifier.height(4.dp))
        Text(rx.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
        if (rx.isPlateau || rx.regressing) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Warning, null, tint = Palette.warning, modifier = Modifier.size(14.dp))
                Text(
                    if (rx.regressing) "Son iki seans en iyi performansının altında."
                    else "Plato: ${rx.stalledSessions} seanstır yeni zirve yok.",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.warning
                )
            }
        }
    }
}

@Composable
private fun WarmupRamp(workWeight: Float, barKg: Float) {
    val ramp = Calc.warmupScheme(workWeight, barKg).filter { it.first < workWeight }
    val plates = Calc.plates(workWeight, barKg)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.fit.elevated)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (ramp.isNotEmpty()) {
            Text(
                "Isınma: " + ramp.joinToString("  ·  ") { "${it.first.trimNum()}×${it.second}" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.fit.muted
            )
        }
        if (plates.isNotEmpty()) {
            Text(
                "Taraf başına: " + plates.joinToString(" + ") { it.trimNum() } + "  (bar ${barKg.trimNum()})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.mono(),
        color = Color(0xFF6B7483),
        textAlign = TextAlign.Center,
        modifier = modifier,
        maxLines = 1
    )
}

/* --------------------------------- Set satırı -------------------------------- */

@Composable
private fun SetRow(
    set: WorkoutSetEntity,
    previous: WorkoutSetEntity?,
    isNext: Boolean,
    isDuration: Boolean,
    isRepsOnly: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onUpdate: (WorkoutSetEntity) -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val accent = MaterialTheme.fit.accent
    val bg = when {
        set.isCompleted -> MaterialTheme.fit.success.copy(alpha = 0.10f)
        isNext -> accent.copy(alpha = 0.07f)
        else -> Color.Transparent
    }

    // Değeri boş kalmış setleri önceki seansla doldur (reçete yoksa / serbest antrenmanda).
    LaunchedEffect(set.id, previous) {
        if (previous != null && !set.isCompleted) {
            var needsSync = false
            var newWeight = set.weightKg
            var newReps = set.reps
            var newDur = set.durationSeconds
            if (set.weightKg <= 0f && previous.weightKg > 0f) { newWeight = previous.weightKg; needsSync = true }
            if (set.reps <= 0 && previous.reps > 0) { newReps = previous.reps; needsSync = true }
            if (set.durationSeconds <= 0 && previous.durationSeconds > 0) { newDur = previous.durationSeconds; needsSync = true }
            if (needsSync) onUpdate(set.copy(weightKg = newWeight, reps = newReps, durationSeconds = newDur))
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(if (isNext) Modifier.border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp)) else Modifier)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (set.isWarmup) Palette.warning.copy(alpha = 0.16f) else MaterialTheme.fit.elevated)
                    .clickable { menu = true },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (set.isWarmup) "W" else "${set.setNumber}",
                    style = MaterialTheme.typography.labelLarge.mono().copy(fontWeight = FontWeight.SemiBold),
                    color = when {
                        set.isWarmup -> Palette.warning
                        set.isCompleted -> MaterialTheme.fit.success
                        isNext -> accent
                        else -> MaterialTheme.fit.muted
                    }
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (set.isWarmup) "Çalışma seti yap" else "Isınma seti yap") },
                    onClick = { menu = false; onUpdate(set.copy(isWarmup = !set.isWarmup)) }
                )
                DropdownMenuItem(
                    text = { Text("Seti sil") },
                    onClick = { menu = false; onDelete() },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.fit.danger) }
                )
            }
        }

        Text(
            previous?.let {
                if (isDuration) "${it.durationSeconds} sn"
                else if (it.weightKg > 0f) "${it.weightKg.trimNum()}×${it.reps}"
                else "${it.reps}"
            } ?: "—",
            style = MaterialTheme.typography.bodySmall.mono(),
            color = Color(0xFF6B7483),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1.3f)
        )

        // Değer hücreleri: dokununca büyük butonlu editör açılır.
        if (isDuration) {
            ValueCell(if (set.durationSeconds > 0) "${set.durationSeconds} sn" else "—", Modifier.weight(1.6f), onEdit)
        } else {
            if (!isRepsOnly) ValueCell(if (set.weightKg > 0f) set.weightKg.trimNum() else "—", Modifier.weight(1f), onEdit)
            ValueCell(if (set.reps > 0) "${set.reps}" else "—", Modifier.weight(1f), onEdit)
        }
        ValueCell(
            if (set.rpe > 0f) set.rpe.trimNum() else "–",
            Modifier.weight(0.8f),
            onEdit,
            muted = set.rpe <= 0f
        )

        Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
            CheckCircle(set.isCompleted, onToggle, size = 42.dp)
        }
    }
}

@Composable
private fun ValueCell(text: String, modifier: Modifier, onClick: () -> Unit, muted: Boolean = false) {
    Box(
        modifier
            .padding(horizontal = 3.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.fit.elevated)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium.mono().copy(fontWeight = FontWeight.SemiBold),
            color = if (muted) MaterialTheme.fit.muted.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/* ------------------------------- Set editörü --------------------------------- */

private val RPE_OPTIONS = listOf(0f, 6f, 7f, 7.5f, 8f, 8.5f, 9f, 9.5f, 10f)

private fun rpeMeaning(rpe: Float): String = when {
    rpe <= 0f -> "RPE girilmedi — progresyon için önerilir."
    rpe >= 10f -> "Tükeniş: bir tekrar daha yapılamazdı."
    rpe >= 9.5f -> "Belki bir tekrar daha."
    rpe >= 9f -> "Bir tekrar daha yapabilirdin."
    rpe >= 8.5f -> "1-2 tekrar daha yapabilirdin."
    rpe >= 8f -> "İki tekrar daha yapabilirdin."
    rpe >= 7.5f -> "2-3 tekrar daha yapabilirdin."
    rpe >= 7f -> "Üç tekrar daha yapabilirdin."
    else -> "Dört veya daha fazla tekrar kalmıştı."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetEditorSheet(
    se: SessionExercise,
    set: WorkoutSetEntity,
    profile: LoadingProfile,
    onDismiss: () -> Unit,
    onSave: (WorkoutSetEntity) -> Unit,
    onSaveAndComplete: (WorkoutSetEntity) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDuration = se.trackingType == ExerciseEntity.TRACK_DURATION
    val isRepsOnly = se.trackingType == ExerciseEntity.TRACK_REPS
    val kind = loadKindOf(se.equipment)
    val step = profile.step(kind).takeIf { it > 0f } ?: 1f

    var weightText by remember(set.id) { mutableStateOf(if (set.weightKg > 0f) set.weightKg.trimNum() else "") }
    var repsText by remember(set.id) { mutableStateOf(if (set.reps > 0) set.reps.toString() else "") }
    var durText by remember(set.id) { mutableStateOf(if (set.durationSeconds > 0) set.durationSeconds.toString() else "") }
    var rpe by remember(set.id) { mutableStateOf(set.rpe) }

    val weight = weightText.replace(',', '.').toFloatOrNull() ?: 0f
    val reps = repsText.toIntOrNull() ?: 0
    val dur = durText.toIntOrNull() ?: 0

    fun build(): WorkoutSetEntity = set.copy(
        weightKg = if (isDuration || isRepsOnly) set.weightKg else weight,
        reps = if (isDuration) set.reps else reps,
        durationSeconds = if (isDuration) dur else set.durationSeconds,
        rpe = rpe
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    "${se.name} · ${if (set.isWarmup) "Isınma seti" else "${set.setNumber}. set"}",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val prev = se.previous.firstOrNull { it.setNumber == set.setNumber } ?: se.previous.lastOrNull()
                val rx = se.prescription
                val info = buildList {
                    if (rx != null && !set.isWarmup) {
                        val r = rx.repsFor((set.setNumber - 1).coerceAtLeast(0))
                        add(if (rx.weight > 0f) "Hedef ${rx.weight.trimNum()} × $r" else "Hedef $r tekrar")
                    }
                    if (prev != null) {
                        add(
                            "Önceki " + (if (isDuration) "${prev.durationSeconds} sn"
                            else if (prev.weightKg > 0f) "${prev.weightKg.trimNum()} × ${prev.reps}" else "${prev.reps} tekrar") +
                                (if (prev.rpe > 0f) " @${prev.rpe.trimNum()}" else "")
                        )
                    }
                }
                if (info.isNotEmpty()) {
                    Text(info.joinToString("   ·   "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.fit.muted)
                }
            }

            if (isDuration) {
                StepperBlock(
                    label = "SÜRE (sn)",
                    text = durText,
                    onText = { durText = it.filter(Char::isDigit).take(4) },
                    onMinus = { durText = (dur - 5).coerceAtLeast(0).toString() },
                    onPlus = { durText = (dur + 5).toString() },
                    decimal = false
                )
            } else {
                if (!isRepsOnly) {
                    StepperBlock(
                        label = "AĞIRLIK (kg)" + if (kind == LoadKind.DUMBBELL) " · tek dambıl" else "",
                        text = weightText,
                        onText = { weightText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) },
                        onMinus = { weightText = (weight - step).coerceAtLeast(0f).trimNum() },
                        onPlus = { weightText = (weight + step).trimNum() },
                        decimal = true,
                        minusLabel = "−${step.trimNum()}",
                        plusLabel = "+${step.trimNum()}"
                    )
                    if (kind == LoadKind.BARBELL && weight > profile.barKg) {
                        val plates = Calc.plates(weight, profile.barKg)
                        val achievable = profile.barKg + plates.sum() * 2f
                        Text(
                            "Taraf başına: " + plates.joinToString(" + ") { it.trimNum() } +
                                if (kotlin.math.abs(achievable - weight) > 0.01f) "  (plakayla ${achievable.trimNum()} kg)" else "",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.fit.muted
                        )
                    }
                }
                StepperBlock(
                    label = "TEKRAR",
                    text = repsText,
                    onText = { repsText = it.filter(Char::isDigit).take(3) },
                    onMinus = { repsText = (reps - 1).coerceAtLeast(0).toString() },
                    onPlus = { repsText = (reps + 1).toString() },
                    decimal = false
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("RPE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.fit.muted)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RPE_OPTIONS.forEach { v ->
                        val selected = kotlin.math.abs(rpe - v) < 0.01f
                        val c = if (v >= 9.5f) MaterialTheme.fit.danger else if (v >= 9f) Palette.warning else MaterialTheme.fit.accent
                        Box(
                            Modifier
                                .height(46.dp)
                                .width(if (v == 0f) 46.dp else 52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) c else MaterialTheme.fit.elevated)
                                .clickable { rpe = v },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (v == 0f) "–" else v.trimNum(),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Text(rpeMeaning(rpe), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("Kaydet", { onSave(build()) }, Modifier.weight(1f))
                AccentButton(
                    if (set.isCompleted) "Güncelle" else "Tamamla",
                    { onSaveAndComplete(build()) },
                    Modifier.weight(1.4f),
                    icon = Icons.Default.Check,
                    color = MaterialTheme.fit.success
                )
            }
        }
    }
}

@Composable
private fun StepperBlock(
    label: String,
    text: String,
    onText: (String) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    decimal: Boolean,
    minusLabel: String? = null,
    plusLabel: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.fit.muted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StepButton(Icons.Default.Remove, minusLabel, onMinus)
            Box(
                Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.fit.elevated),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = onText,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    ),
                    cursorBrush = SolidColor(MaterialTheme.fit.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.Center) {
                            if (text.isEmpty()) {
                                Text(
                                    "0",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.fit.muted.copy(alpha = 0.4f),
                                    textAlign = TextAlign.Center
                                )
                            }
                            inner()
                        }
                    }
                )
            }
            StepButton(Icons.Default.Add, plusLabel, onPlus)
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String?, onClick: () -> Unit) {
    Box(
        Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.fit.accent.copy(alpha = 0.14f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.fit.accent)
        } else {
            Icon(icon, null, tint = MaterialTheme.fit.accent, modifier = Modifier.size(28.dp))
        }
    }
}

/* ------------------------------ Bitirme akışı ------------------------------ */

@Composable
private fun FinishDialog(
    doneSets: Int,
    totalSets: Int,
    elapsed: Int,
    review: SessionReview,
    onDismiss: () -> Unit,
    onFinish: (String, Int) -> Unit
) {
    var notes by remember { mutableStateOf("") }
    var feeling by remember { mutableStateOf(0) }
    val faces = listOf("😵" to 1, "🙁" to 2, "😐" to 3, "🙂" to 4, "🔥" to 5)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Antrenmanı bitir", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryPill("Süre", formatDuration(elapsed), Modifier.weight(1f))
                    SummaryPill("Set", "$doneSets/$totalSets", Modifier.weight(1f))
                    SummaryPill("Hacim", formatTonnage(review.volume), Modifier.weight(1f))
                }
                review.volumeDeltaPct?.let { pct ->
                    val c = if (pct >= 0) MaterialTheme.fit.success else Palette.warning
                    Text(
                        "Önceki aynı güne göre hacim: ${if (pct >= 0) "+" else ""}$pct%",
                        style = MaterialTheme.typography.labelLarge,
                        color = c
                    )
                }
                if (doneSets < totalSets) {
                    Text(
                        "${totalSets - doneSets} set işaretlenmedi; bunlar kaydedilmeyecek.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.warning
                    )
                }
                if (review.next.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Bir sonraki seans", style = MaterialTheme.typography.titleSmall)
                        review.next.forEach { (name, rx) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    actionIcon(rx.action), null,
                                    tint = actionColor(rx.action),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    name,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    rx.headline,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = actionColor(rx.action)
                                )
                            }
                        }
                    }
                }
                Column {
                    Text("Nasıl geçti?", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        faces.forEach { (emoji, value) ->
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (feeling == value) MaterialTheme.fit.accent.copy(alpha = 0.2f)
                                        else MaterialTheme.fit.elevated
                                    )
                                    .border(
                                        1.dp,
                                        if (feeling == value) MaterialTheme.fit.accent else Color.Transparent,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable { feeling = value },
                                contentAlignment = Alignment.Center
                            ) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
                        }
                    }
                }
                FitTextField(notes, { notes = it }, "Seans notu (isteğe bağlı)", singleLine = false, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(onClick = { onFinish(notes, feeling) }) {
                Text("Bitir ve kaydet", color = MaterialTheme.fit.success, style = MaterialTheme.typography.titleMedium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç", color = MaterialTheme.fit.muted) }
        }
    )
}

@Composable
private fun SummaryPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.fit.elevated)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}
