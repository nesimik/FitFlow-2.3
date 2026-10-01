package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.ui.components.RoundIconButton
import com.example.ui.components.Sparkline
import com.example.ui.theme.mono
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.core.Calc
import com.example.core.formatDurationShort
import com.example.core.formatTonnage
import com.example.core.trimNum
import com.example.ui.AppViewModel
import com.example.ui.Backup
import com.example.ui.Routes
import com.example.ui.components.AccentButton
import com.example.ui.components.Badge
import com.example.ui.components.ChoiceChip
import com.example.ui.components.FitCard
import com.example.ui.components.FitTextField
import com.example.ui.components.GhostButton
import com.example.ui.components.KeyValueRow
import com.example.ui.components.LabeledSwitch
import com.example.ui.components.OverlineText
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatTile
import com.example.ui.theme.Palette
import com.example.ui.theme.fit
import com.example.ui.theme.parseHex

/* ================================== Profil ================================== */

@Composable
fun ProfileScreen(vm: AppViewModel, nav: NavHostController) {
    val name by vm.settings.userName.collectAsStateWithLifecycle()
    val height by vm.settings.heightCm.collectAsStateWithLifecycle()
    val weight by vm.settings.weightKg.collectAsStateWithLifecycle()
    val age by vm.settings.age.collectAsStateWithLifecycle()
    val isMale by vm.settings.isMale.collectAsStateWithLifecycle()
    val goal by vm.settings.weeklyGoal.collectAsStateWithLifecycle()
    val stats by vm.dashboard.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showEdit by remember { mutableStateOf(false) }
    val bmi = Calc.bmi(weight, height)

    var showClearConfirm by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf<String?>(null) }

    val prs by vm.prs.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val metrics by vm.bodyMetrics.collectAsStateWithLifecycle()
    val allSets by vm.allSets.collectAsStateWithLifecycle()
    val themeMode by vm.settings.themeMode.collectAsStateWithLifecycle()
    val accentHex by vm.settings.accent.collectAsStateWithLifecycle()
    val targetWeight by vm.settings.targetWeightKg.collectAsStateWithLifecycle()
    val goalLiftName by vm.settings.goalLiftName.collectAsStateWithLifecycle()
    val goalLiftKg by vm.settings.goalLiftKg.collectAsStateWithLifecycle()
    var showGoals by remember { mutableStateOf(false) }

    // Vücut: son 12 haftanın kilo ölçümleri (eskiden yeniye)
    val weightSeries = remember(metrics) {
        val from = System.currentTimeMillis() - 84L * 86_400_000L
        metrics.filter { it.weightKg > 0f && it.dateMillis >= from }.sortedBy { it.dateMillis }
    }
    val latestWeight = metrics.filter { it.weightKg > 0f }.maxByOrNull { it.dateMillis }?.weightKg ?: weight
    val weightDelta = if (weightSeries.size >= 2) weightSeries.last().weightKg - weightSeries.first().weightKg else null
    val latestFat = metrics.filter { it.bodyFatPct > 0f }.maxByOrNull { it.dateMillis }?.bodyFatPct
    val bmiNow = Calc.bmi(latestWeight, height)
    val firstMillis = workouts.minOfOrNull { it.startedAt }
    val months = firstMillis?.let { ((System.currentTimeMillis() - it) / (30L * 86_400_000L)).toInt() }
    val liftBest = remember(allSets, goalLiftName) { best1rmFor(goalLiftName, allSets) }
    val startWeight = metrics.filter { it.weightKg > 0f }.minByOrNull { it.dateMillis }?.weightKg ?: latestWeight

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        /* Üst çubuk: geri · Profil · düzenle */
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                if (nav.previousBackStackEntry != null) {
                    RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, MaterialTheme.colorScheme.onSurface, 40.dp, MaterialTheme.fit.elevated) { nav.popBackStack() }
                } else Spacer(Modifier.size(40.dp))
                Text(
                    "Profil",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(top = 10.dp)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundIconButton(Icons.Default.Edit, MaterialTheme.colorScheme.onSurface, 40.dp, MaterialTheme.fit.elevated) { showEdit = true }
                    // Gece / gündüz: tek dokunuşla koyu ↔ açık tema
                    val isDark = MaterialTheme.fit.isDark
                    RoundIconButton(
                        if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                        if (isDark) MaterialTheme.fit.gold else MaterialTheme.colorScheme.onSurface,
                        40.dp, MaterialTheme.fit.elevated
                    ) { vm.settings.setThemeMode(if (isDark) "light" else "dark") }
                }
            }
        }

        /* Avatar + ad */
        item {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(94.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.fit.accent.copy(alpha = 0.07f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.border(1.dp, MaterialTheme.fit.accent.copy(alpha = 0.35f), CircleShape)) {
                        ProfileAvatar(name, 84.dp) { showEdit = true }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(name, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(
                        "$age yaş",
                        when {
                            months == null -> null
                            months < 1 -> "bu ay başladı"
                            else -> "$months aydır kayıtta"
                        }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.fit.muted
                )
            }
        }

        /* seans · toplam hacim · rekor */
        item {
            FitCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), corner = 20.dp, contentPadding = PaddingValues(vertical = 14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ProfileStat("${stats.totalWorkouts}", null, "seans", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(36.dp).background(MaterialTheme.fit.cardBorder))
                    val tonnage = formatTonnage(stats.totalVolume).split(" ")
                    ProfileStat(tonnage.first(), tonnage.getOrNull(1), "toplam hacim", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(36.dp).background(MaterialTheme.fit.cardBorder))
                    ProfileStat("${prs.size}", null, "rekor", Palette.gold, Modifier.weight(1f))
                }
            }
        }

        /* Vücut */
        item {
            FitCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Vücut", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                    Text(
                        "Ölçüm ekle",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.fit.accent,
                        modifier = Modifier.clickable { nav.navigate(Routes.BODY) }
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("Kilo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.muted)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(latestWeight.trComma(), style = MaterialTheme.typography.headlineLarge.mono().copy(fontWeight = FontWeight.SemiBold))
                            Text(" kg", style = MaterialTheme.typography.bodyMedium.mono(), color = MaterialTheme.fit.muted, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        if (weightDelta != null) {
                            Text(
                                (if (weightDelta > 0f) "+" else if (weightDelta < 0f) "−" else "") +
                                    "${kotlin.math.abs(weightDelta).trComma()} kg · 12 hafta",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (targetWeight > 0f && (targetWeight - latestWeight) * weightDelta < 0f) MaterialTheme.fit.warning
                                else MaterialTheme.fit.success
                            )
                        }
                    }
                    if (weightSeries.size >= 2) {
                        Sparkline(weightSeries.map { it.weightKg }, Modifier.width(150.dp), height = 52.dp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BodyTile("Yağ oranı", latestFat?.let { "%" + it.trComma() } ?: "—", Modifier.weight(1f))
                    BodyTile("Boy", "${height.trimNum()} cm", Modifier.weight(1f))
                    BodyTile("VKİ", bmiNow.trComma(), Modifier.weight(1f))
                }
            }
        }

        /* Ayar satırları */
        item {
            FitCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                SettingRow(Icons.Default.MonitorWeight, "Vücut ölçümleri", "${metrics.size}") { nav.navigate(Routes.BODY) }
                SettingRow(Icons.Default.History, "Antrenman geçmişi", "${stats.totalWorkouts}") { nav.navigate(Routes.HISTORY) }
                SettingRow(Icons.Default.EditNote, "Notlar", "") { nav.navigate(Routes.NOTES) }
                SettingRow(Icons.Default.Calculate, "Hesaplayıcılar", "1RM, plaka") { nav.navigate(Routes.TOOLS) }
                SettingRow(Icons.Default.Settings, "Ayarlar", "görünüm, antrenman, ekipman…", divider = false) { nav.navigate(Routes.SETTINGS) }
            }
        }

        /* Yedekleme */
        item {
            FitCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                SettingRow(Icons.Default.Share, "Verileri dışa aktar", "yedek") { showExportDialog = true }
                SettingRow(Icons.Default.ContentCopy, "Yedekten / eski sürümden yükle", "") { showImportDialog = true }
                SettingRow(Icons.Default.DeleteOutline, "Tüm antrenman geçmişini temizle", "", tint = MaterialTheme.fit.danger, divider = false) { showClearConfirm = true }
            }
        }

        item {
            Text(
                "Tüm veri cihazda · FitFlow 2.7",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF4B5361),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showGoals) {
        GoalsDialog(
            targetWeight = targetWeight,
            liftName = goalLiftName,
            liftKg = goalLiftKg,
            onSave = { tw, ln, lk ->
                vm.settings.setGoals(tw, ln, lk)
                showGoals = false
            },
            onDismiss = { showGoals = false }
        )
    }

    if (importMessage != null) {
        AlertDialog(
            onDismissRequest = { importMessage = null },
            shape = RoundedCornerShape(22.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Veri İçe Aktarma", style = MaterialTheme.typography.titleLarge) },
            text = { Text(importMessage ?: "", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { importMessage = null }) {
                    Text("Tamam", color = MaterialTheme.fit.accent, style = MaterialTheme.typography.titleMedium)
                }
            }
        )
    }

    if (showImportDialog) {
        ImportDataDialog(
            onImportJson = { jsonStr ->
                showImportDialog = false
                vm.importBackupJson(jsonStr) { result ->
                    importMessage = result.message
                }
            },
            onImportLegacy = {
                showImportDialog = false
                vm.importLegacyDatabase { success ->
                    importMessage = if (success) "Eski FitFlow veritabanı başarıyla aktarıldı." else "Cihazda eski veritabanı dosyası bulunamadı veya aktarılamadı."
                }
            },
            onDismiss = { showImportDialog = false }
        )
    }

    if (showExportDialog) {
        ExportDataDialog(
            vm = vm,
            onDismiss = { showExportDialog = false }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Geçmiş verileri temizle", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Tüm antrenman kayıtları, set verileri ve rekorlar silinecektir. Bu işlem geri alınamaz. Onaylıyor musunuz?", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAllWorkoutHistory()
                    showClearConfirm = false
                }) { Text("Sıfırla & Temizle", color = MaterialTheme.fit.danger, style = MaterialTheme.typography.titleMedium) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Vazgeç", color = MaterialTheme.fit.muted) }
            }
        )
    }

    if (showEdit) {
        ProfileDialog(
            name, height, weight, age, isMale, goal,
            onSave = { n, h, w, a, m, g ->
                vm.settings.setProfile(n, h, w, a, m)
                vm.settings.setWeeklyGoal(g)
                showEdit = false
            },
            onDismiss = { showEdit = false }
        )
    }
}

/* ----------------------------- Profil parçaları ----------------------------- */

/** Türkçe ondalık virgül: 78.4 → "78,4" */
private fun Float.trComma(): String = trimNum().replace('.', ',')

/** Adı eşleşen hareketin tamamlanmış setlerinden en iyi tahmini 1RM. */
private fun best1rmFor(name: String, sets: List<com.example.data.WorkoutSetEntity>): Float? {
    val key = name.trim().lowercase(java.util.Locale("tr")).replace('ı', 'i')
    if (key.isEmpty()) return null
    return sets.asSequence()
        .filter { !it.isWarmup && it.weightKg > 0f && it.reps in 1..12 }
        .filter { it.exerciseName.lowercase(java.util.Locale("tr")).replace('ı', 'i') == key }
        .map { Calc.e1rm(it.weightKg, it.reps) }
        .maxOrNull()
}

@Composable
private fun ProfileStat(value: String, unit: String?, label: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value.replace('.', ','), style = MaterialTheme.typography.titleLarge.mono().copy(fontWeight = FontWeight.SemiBold), color = color)
            if (unit != null) {
                Text(" $unit", style = MaterialTheme.typography.labelMedium.mono(), color = MaterialTheme.fit.muted, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.muted)
    }
}

@Composable
private fun BodyTile(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.fit.elevated)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.muted, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleSmall.mono().copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

@Composable
internal fun SettingRow(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color = MaterialTheme.fit.muted,
    divider: Boolean = true,
    onClick: () -> Unit
) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (tint == MaterialTheme.fit.muted) MaterialTheme.colorScheme.onSurface else tint,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            if (value.isNotEmpty()) {
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.fit.muted, maxLines = 1)
                Spacer(Modifier.width(4.dp))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color(0xFF4B5361), modifier = Modifier.size(20.dp))
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)))
    }
}

@Composable
private fun GoalsDialog(
    targetWeight: Float,
    liftName: String,
    liftKg: Float,
    onSave: (Float, String, Float) -> Unit,
    onDismiss: () -> Unit
) {
    var tw by remember { mutableStateOf(if (targetWeight > 0f) targetWeight.trimNum() else "") }
    var ln by remember { mutableStateOf(liftName) }
    var lk by remember { mutableStateOf(if (liftKg > 0f) liftKg.trimNum() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Hedefler", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FitTextField(tw, { tw = it }, "Hedef kilo (kg) — boş = yok", keyboardType = KeyboardType.Decimal)
                FitTextField(ln, { ln = it }, "Güç hedefi hareketi (ör. Bench Press)")
                FitTextField(lk, { lk = it }, "Hedef 1RM (kg) — boş = yok", keyboardType = KeyboardType.Decimal)
                Text(
                    "Hareket adı kütüphanedeki adla aynı olmalı; ilerleme tahmini 1RM'den hesaplanır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.fit.muted
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    tw.replace(',', '.').toFloatOrNull()?.coerceAtLeast(0f) ?: 0f,
                    ln.trim().ifBlank { "Bench Press" },
                    lk.replace(',', '.').toFloatOrNull()?.coerceAtLeast(0f) ?: 0f
                )
            }) { Text("Kaydet", color = MaterialTheme.fit.accent, style = MaterialTheme.typography.titleMedium) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç", color = MaterialTheme.fit.muted) } }
    )
}

@Composable
private fun ProfileDialog(
    name: String, height: Float, weight: Float, age: Int, isMale: Boolean, goal: Int,
    onSave: (String, Float, Float, Int, Boolean, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var n by remember { mutableStateOf(name) }
    var h by remember { mutableStateOf(height.trimNum()) }
    var w by remember { mutableStateOf(weight.trimNum()) }
    var a by remember { mutableStateOf(age.toString()) }
    var male by remember { mutableStateOf(isMale) }
    var g by remember { mutableStateOf(goal) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Profil bilgileri", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FitTextField(n, { n = it }, "Ad")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FitTextField(h, { h = it }, "Boy (cm)", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                    FitTextField(w, { w = it }, "Kilo (kg)", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                }
                FitTextField(a, { a = it.filter { c -> c.isDigit() } }, "Yaş", keyboardType = KeyboardType.Number)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Erkek", male, { male = true })
                    ChoiceChip("Kadın", !male, { male = false })
                }
                Column {
                    OverlineText("Haftalık antrenman hedefi: $g")
                    Slider(
                        value = g.toFloat(),
                        onValueChange = { g = it.toInt() },
                        valueRange = 1f..7f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.fit.accent,
                            activeTrackColor = MaterialTheme.fit.accent
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    n.ifBlank { "Sporcu" },
                    h.replace(',', '.').toFloatOrNull() ?: height,
                    w.replace(',', '.').toFloatOrNull() ?: weight,
                    a.toIntOrNull() ?: age,
                    male, g
                )
            }) { Text("Kaydet", color = MaterialTheme.fit.accent, style = MaterialTheme.typography.titleMedium) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç", color = MaterialTheme.fit.muted) } }
    )
}

/* ================================== Ayarlar ================================= */

@Composable
internal fun ImportDataDialog(
    onImportJson: (String) -> Unit,
    onImportLegacy: () -> Unit,
    onDismiss: () -> Unit
) {
    var showPasteField by remember { mutableStateOf(false) }
    var jsonText by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current

    val jsonFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().readText()
                }
                if (!content.isNullOrBlank()) {
                    onImportJson(content)
                }
            } catch (_: Exception) { }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Veri Aktarma & Yükleme", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Eski FitFlow Pro uygulamanızdaki veya dışa aktardığınız yedek dosyanızdaki tüm antrenmanları FitFlow Pro2'ye yükleyebilirsiniz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.fit.muted
                )

                GhostButton(
                    text = "Yedek Dosyası Seç (.json)",
                    onClick = { jsonFilePicker.launch("*/*") },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.History,
                    color = MaterialTheme.fit.accent
                )

                if (!showPasteField) {
                    GhostButton(
                        text = "Yedek Metnini Yapıştır (JSON)",
                        onClick = { showPasteField = true },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.EditNote
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FitTextField(
                            value = jsonText,
                            onValueChange = { jsonText = it },
                            label = "JSON Yedek Metni",
                            singleLine = false,
                            modifier = Modifier.height(130.dp)
                        )
                        AccentButton(
                            text = "Verileri Aktar",
                            onClick = {
                                if (jsonText.isNotBlank()) {
                                    onImportJson(jsonText)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = jsonText.isNotBlank()
                        )
                    }
                }

                GhostButton(
                    text = "Cihazdaki Eski Veritabanını Tara (SQLite)",
                    onClick = { onImportLegacy() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat", color = MaterialTheme.fit.muted)
            }
        }
    )
}

@Composable
internal fun PasscodeDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = code.length >= 4 && code == confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Şifre belirle", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("En az 4 haneli sayısal şifre.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
                FitTextField(code, { code = it.filter { c -> c.isDigit() }.take(8) }, "Şifre", keyboardType = KeyboardType.NumberPassword)
                FitTextField(confirm, { confirm = it.filter { c -> c.isDigit() }.take(8) }, "Şifre (tekrar)", keyboardType = KeyboardType.NumberPassword)
                if (code.isNotEmpty() && !valid) {
                    Text("Şifreler eşleşmiyor veya çok kısa.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.danger)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(code) }) {
                Text("Kaydet", color = if (valid) MaterialTheme.fit.accent else MaterialTheme.fit.muted)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç", color = MaterialTheme.fit.muted) } }
    )
}

@Composable
fun ExportDataDialog(
    vm: AppViewModel,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val allSets by vm.allSets.collectAsStateWithLifecycle()
    val routines by vm.routines.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val prs by vm.prs.collectAsStateWithLifecycle()
    val bodyMetrics by vm.bodyMetrics.collectAsStateWithLifecycle()

    var showRawJson by remember { mutableStateOf(false) }
    var jsonText by remember { mutableStateOf("") }
    var isCopied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        jsonText = vm.exportJson()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Share, null, tint = MaterialTheme.fit.accent)
                Text("Verileri Dışa Aktar", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Tüm antrenman geçmişiniz, setleriniz, egzersiz kütüphaneniz ve programlarınız JSON formatında yedeklenir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.fit.muted
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.fit.elevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Yedeklenecek İçerik Özeti:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.accent)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("• Antrenman & Setler", style = MaterialTheme.typography.bodySmall)
                            Text("${workouts.size} antrenman (${allSets.size} set)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("• Programlar", style = MaterialTheme.typography.bodySmall)
                            Text("${routines.size} program", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("• Egzersizler", style = MaterialTheme.typography.bodySmall)
                            Text("${exercises.size} hareket", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("• PR & Ölçümler", style = MaterialTheme.typography.bodySmall)
                            Text("${prs.size} PR · ${bodyMetrics.size} ölçüm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
                        }
                    }
                }

                AccentButton(
                    text = "Dosya Olarak Paylaş / Kaydet (.json)",
                    onClick = {
                        val exported = jsonText.ifBlank { vm.exportJson() }
                        val ok = Backup.shareBackup(context, exported)
                        if (ok) {
                            android.widget.Toast.makeText(context, "Yedek dosyası hazırlandı ve paylaşılıyor.", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            Backup.copyToClipboard(context, exported)
                            android.widget.Toast.makeText(context, "Yedek panoya kopyalandı.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Share
                )

                GhostButton(
                    text = if (isCopied) "✓ Panoya Kopyalandı!" else "JSON Metnini Panoya Kopyala",
                    onClick = {
                        val exported = jsonText.ifBlank { vm.exportJson() }
                        val ok = Backup.copyToClipboard(context, exported)
                        if (ok) {
                            isCopied = true
                            android.widget.Toast.makeText(context, "Yedek metni panoya kopyalandı.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.ContentCopy,
                    color = if (isCopied) MaterialTheme.fit.success else MaterialTheme.fit.accent
                )

                if (!showRawJson) {
                    TextButton(
                        onClick = { showRawJson = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("JSON Kodunu Görüntüle", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("JSON Önizleme:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.fillMaxWidth().height(110.dp)
                        ) {
                            Box(Modifier.padding(8.dp).verticalScroll(rememberScrollState())) {
                                Text(
                                    jsonText.take(1500) + if (jsonText.length > 1500) "…" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.fit.muted
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat", color = MaterialTheme.fit.muted)
            }
        }
    )
}

