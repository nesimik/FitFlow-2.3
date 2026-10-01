package com.example.ui.screens

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import com.example.ui.components.Badge
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Rowing
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.SportsHandball
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.Equipment
import com.example.core.Muscles
import com.example.core.TR
import com.example.data.ExerciseEntity
import com.example.ui.AppViewModel
import com.example.ui.components.ChoiceChip
import com.example.ui.components.EmptyState
import com.example.ui.components.LocalMenuAction
import com.example.ui.components.RoundIconButton
import com.example.ui.theme.Palette
import com.example.ui.theme.fit

/** Alt sayfalarda kullanılan üst başlık. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val menuAction = LocalMenuAction.current
        if (onBack != null) {
            RoundIconButton(
                Icons.AutoMirrored.Filled.ArrowBack,
                MaterialTheme.colorScheme.onSurface,
                40.dp,
                MaterialTheme.fit.elevated
            ) { onBack() }
            Spacer(Modifier.width(12.dp))
        } else if (menuAction != null) {
            RoundIconButton(
                Icons.Default.Menu,
                MaterialTheme.colorScheme.onSurface,
                40.dp,
                MaterialTheme.fit.elevated
            ) { menuAction() }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted, maxLines = 1)
            }
        }
        actions()
    }
}

/** Kas grubuna göre spor simgesi (emoji yerine çizim ikon). */
fun muscleGroupIcon(group: String): androidx.compose.ui.graphics.vector.ImageVector = when (group) {
    Muscles.CHEST -> Icons.Default.FitnessCenter
    Muscles.BACK -> Icons.Default.Rowing
    Muscles.LEGS -> Icons.Default.DirectionsRun
    Muscles.SHOULDERS -> Icons.Default.SportsGymnastics
    Muscles.ARMS -> Icons.Default.SportsMma
    Muscles.CORE -> Icons.Default.SelfImprovement
    Muscles.CARDIO -> Icons.Default.DirectionsBike
    else -> Icons.Default.SportsHandball
}

/** Kas grubu rengiyle küçük yuvarlak simge. */
@Composable
fun MuscleAvatar(group: String, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val c = Palette.muscle(group)
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(c.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Text(Muscles.emoji(group), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.fit.muted) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.fit.muted, modifier = Modifier.size(19.dp)) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                Icon(
                    Icons.Default.Close, null, tint = MaterialTheme.fit.muted,
                    modifier = Modifier.size(19.dp).clickable { onValueChange("") }
                )
            }
        },
        shape = RoundedCornerShape(25.dp),
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.fit.accent.copy(alpha = 0.5f),
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.fit.elevated,
            unfocusedContainerColor = MaterialTheme.fit.elevated,
            cursorColor = MaterialTheme.fit.accent
        )
    )
}

/**
 * Tam ekran hareket seçici. Programa ya da aktif seansa hareket eklemek için kullanılır.
 */
@Composable
fun ExercisePickerDialog(
    vm: AppViewModel,
    title: String = "Hareket seç",
    excludeIds: Set<Long> = emptySet(),
    onPick: (ExerciseEntity) -> Unit,
    onDismiss: () -> Unit
) {
    ExerciseBrowserDialog(vm = vm, title = title, multi = false, excludeIds = excludeIds, onPick = onPick, onDismiss = onDismiss)
}

/* --------------------------- Hareket oluştur / düzenle --------------------------- */

fun autoDetectMuscleGroup(name: String): String? {
    val n = name.lowercase(TR).trim()
    if (n.isBlank()) return null
    fun has(vararg keys: String) = keys.any { n.contains(it) }

    return when {
        has("bench", "göğüs", "chest", "fly", "pec", "şınav", "push-up", "pushup", "pec deck", "butterfly", "dips") -> Muscles.CHEST
        has("pulldown", "pull-up", "pullup", "barfiks", "chin-up", "lat", "row", "kürek", "sırt", "back", "deadlift", "trapez", "shrug", "kanat") -> Muscles.BACK
        has("squat", "lunge", "bacak", "leg", "quad", "hamstring", "calves", "baldır", "kalça", "glute", "hip thrust", "rdl", "ön bacak", "arka bacak", "step up") -> Muscles.LEGS
        has("shoulder", "omuz", "overhead", "press", "lateral", "delt", "arnold", "military") -> Muscles.SHOULDERS
        has("curl", "biceps", "pazı", "triceps", "fransız", "pushdown", "skull", "kol", "arm", "wrist", "önkol", "arkakol", "hammer") -> Muscles.ARMS
        has("plank", "crunch", "karın", "abs", "leg raise", "mekik", "oblique", "russian twist") -> Muscles.CORE
        else -> null
    }
}

@Composable
fun ExerciseEditorDialog(
    initial: ExerciseEntity?,
    onSave: (ExerciseEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var group by remember { mutableStateOf(initial?.muscleGroup ?: Muscles.CHEST) }
    var userOverriddenGroup by remember { mutableStateOf(initial != null) }
    var autoDetectedGroup by remember { mutableStateOf<String?>(null) }

    var equipment by remember { mutableStateOf(initial?.equipment ?: "Barbell") }
    var track by remember { mutableStateOf(initial?.trackingType ?: ExerciseEntity.TRACK_WEIGHT_REPS) }
    var video by remember { mutableStateOf(initial?.videoUrl ?: "") }
    var desc by remember { mutableStateOf(initial?.instructions ?: "") }
    var rest by remember { mutableStateOf((initial?.defaultRestSeconds ?: 90).toString()) }

    val onNameChange: (String) -> Unit = { newName ->
        name = newName
        if (!userOverriddenGroup) {
            val detected = autoDetectMuscleGroup(newName)
            if (detected != null) {
                group = detected
                autoDetectedGroup = detected
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader(
                    if (initial == null) "Yeni hareket" else "Hareketi düzenle",
                    onBack = onDismiss
                )

                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    item {
                        com.example.ui.components.FitTextField(name, onNameChange, "Hareket adı", placeholder = "Örn: Incline Dumbbell Press")
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.example.ui.components.OverlineText("Kas grubu")
                            if (autoDetectedGroup != null && !userOverriddenGroup) {
                                Spacer(Modifier.width(8.dp))
                                Badge("⚡ Otomatik algılandı: $group", MaterialTheme.fit.accent)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Muscles.all.forEach { m ->
                                ChoiceChip(
                                    m,
                                    group == m,
                                    {
                                        group = m
                                        userOverriddenGroup = true
                                    },
                                    color = Palette.muscle(m)
                                )
                            }
                        }
                    }
                    item {
                        com.example.ui.components.OverlineText("Ekipman")
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Equipment.all.forEach { ChoiceChip(it, equipment == it, { equipment = it }) }
                        }
                    }
                    item {
                        com.example.ui.components.OverlineText("Nasıl ölçülsün?")
                        Spacer(Modifier.height(6.dp))
                        val options = listOf(
                            ExerciseEntity.TRACK_WEIGHT_REPS to "Ağırlık × Tekrar",
                            ExerciseEntity.TRACK_REPS to "Sadece tekrar",
                            ExerciseEntity.TRACK_DURATION to "Süre",
                            ExerciseEntity.TRACK_DISTANCE to "Mesafe"
                        )
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            options.forEach { (key, label) ->
                                ChoiceChip(label, track == key, { track = key })
                            }
                        }
                    }
                    item {
                        com.example.ui.components.FitTextField(
                            rest, { rest = it.filter { c -> c.isDigit() } },
                            "Varsayılan dinlenme (sn)",
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    }
                    item {
                        com.example.ui.components.FitTextField(video, { video = it }, "Video bağlantısı (isteğe bağlı)")
                    }
                    item {
                        com.example.ui.components.FitTextField(
                            desc, { desc = it }, "Açıklama / teknik notu",
                            singleLine = false, minLines = 3
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 12.dp,
                    tonalElevation = 6.dp
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        com.example.ui.components.GhostButton("Vazgeç", onDismiss, Modifier.weight(1f))
                        com.example.ui.components.AccentButton(
                            "Kaydet",
                            {
                                if (name.isNotBlank()) {
                                    onSave(
                                        (initial ?: ExerciseEntity(name = name, muscleGroup = group, isCustom = true)).copy(
                                            name = name.trim(),
                                            muscleGroup = group,
                                            equipment = equipment,
                                            trackingType = track,
                                            videoUrl = video.trim(),
                                            instructions = desc.trim(),
                                            defaultRestSeconds = rest.toIntOrNull() ?: 90,
                                            isCustom = initial?.isCustom ?: true
                                        )
                                    )
                                }
                            },
                            Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

fun openUrl(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    try {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Throwable) {
    }
}

/**
 * Birden fazla hareket seçerek süperset oluşturma diyaloğu.
 */
@Composable
fun SupersetPickerDialog(
    vm: AppViewModel,
    title: String = "Süperset Oluştur",
    onPickSuperset: (List<ExerciseEntity>) -> Unit,
    onDismiss: () -> Unit
) {
    ExerciseBrowserDialog(vm = vm, title = title, multi = true, onPickMany = onPickSuperset, onDismiss = onDismiss)
}

/**
 * Mevcut bir hareketi başka bir hareketle süperset olarak birleştirme diyaloğu.
 */
@Composable
fun <T> CombineExerciseDialog(
    title: String = "Süperset Olarak Birleştir",
    sourceName: String,
    candidates: List<T>,
    candidateName: (T) -> String,
    candidateGroup: (T) -> String,
    onCombineWith: (T) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "'$sourceName' hareketini hangisiyle birleştirmek istiyorsun?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.fit.muted
                        )
                    }
                    RoundIconButton(Icons.Default.Close, MaterialTheme.fit.muted, 32.dp, Color.Transparent, onDismiss)
                }

                if (candidates.isEmpty()) {
                    Text(
                        "Birleştirilebilecek başka hareket bulunamadı.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.fit.muted,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(candidates) { item ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.fit.elevated,
                                modifier = Modifier.fillMaxWidth().clickable { onCombineWith(item) }
                            ) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    MuscleAvatar(candidateGroup(item), 32.dp)
                                    Column(Modifier.weight(1f)) {
                                        Text(candidateName(item), style = MaterialTheme.typography.titleSmall)
                                        Text(candidateGroup(item), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
                                    }
                                    Icon(Icons.Default.Add, null, tint = MaterialTheme.fit.accent)
                                }
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    com.example.ui.components.GhostButton("İptal", onDismiss)
                }
            }
        }
    }
}
