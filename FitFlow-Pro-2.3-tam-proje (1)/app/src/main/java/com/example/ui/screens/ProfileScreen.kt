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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                title = "Profil ve ayarlar",
                onBack = if (nav.previousBackStackEntry != null) { { nav.popBackStack() } } else null
            )
        }

        item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                FitCard(onClick = { showEdit = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.fit.accent.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                name.take(1).uppercase(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.fit.accent
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleLarge)
                            Text(
                                "${height.trimNum()} cm · ${weight.trimNum()} kg · $age yaş",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.fit.muted
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.fit.muted)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Badge("VKİ ${bmi.trimNum()}", MaterialTheme.fit.accent)
                        Badge("Hedef: $goal gün/hf", Palette.violet)
                        Badge(if (isMale) "Erkek" else "Kadın", MaterialTheme.fit.muted)
                    }
                }
            }
        }

        item {
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile("Toplam seans", "${stats.totalWorkouts}", Modifier.weight(1f))
                StatTile("Kaldırılan", formatTonnage(stats.totalVolume), Modifier.weight(1f))
                StatTile("Süre", formatDurationShort(stats.totalDurationSec), Modifier.weight(1f))
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionHeader("Kısayollar")
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NavRow("Vücut ölçümleri", "Kilo ve çevre ölçümü takibi", Icons.Default.MonitorWeight) { nav.navigate(Routes.BODY) }
                    NavRow("Antrenman geçmişi", "${stats.totalWorkouts} kayıt", Icons.Default.History) { nav.navigate(Routes.HISTORY) }
                    NavRow("Notlar", "Antrenman günlüğü ve fikirler", Icons.Default.EditNote) { nav.navigate(Routes.NOTES) }
                    NavRow("Hesaplayıcılar", "1RM, plaka, ısınma ve diğerleri", Icons.Default.Calculate) { nav.navigate(Routes.TOOLS) }
                    NavRow("Ayarlar", "Tema, dinlenme, ses, güvenlik", Icons.Default.Settings) { nav.navigate(Routes.SETTINGS) }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionHeader("Yedekleme & Veri Yönetimi", "Eski FitFlow Pro verilerini yükle veya yedekle")
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton(
                        "Eski FitFlow Pro Verilerini Yükle",
                        { showImportDialog = true },
                        Modifier.fillMaxWidth(),
                        Icons.Default.History,
                        MaterialTheme.fit.accent
                    )
                    GhostButton(
                        "Verileri Dışa Aktar (Yedekle)",
                        { showExportDialog = true },
                        Modifier.fillMaxWidth(),
                        Icons.Default.Share
                    )
                    GhostButton(
                        "Tüm antrenman geçmişini temizle",
                        { showClearConfirm = true },
                        Modifier.fillMaxWidth(),
                        null,
                        MaterialTheme.fit.danger
                    )
                }
            }
        }
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

@Composable
fun NavRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    FitCard(onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.fit.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = MaterialTheme.fit.accent, modifier = Modifier.size(19.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted, maxLines = 1)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.fit.muted)
        }
    }
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
fun SettingsScreen(vm: AppViewModel, nav: NavHostController) {
    val s = vm.settings
    val themeMode by s.themeMode.collectAsStateWithLifecycle()
    val accent by s.accent.collectAsStateWithLifecycle()
    val amoled by s.amoled.collectAsStateWithLifecycle()
    val fontScale by s.fontScale.collectAsStateWithLifecycle()
    val autoRest by s.autoRest.collectAsStateWithLifecycle()
    val defaultRest by s.defaultRest.collectAsStateWithLifecycle()
    val sound by s.sound.collectAsStateWithLifecycle()
    val vibrate by s.vibrate.collectAsStateWithLifecycle()
    val beep by s.countdownBeep.collectAsStateWithLifecycle()
    val keepOn by s.keepScreenOn.collectAsStateWithLifecycle()
    val bar by s.barWeight.collectAsStateWithLifecycle()
    val inc by s.increment.collectAsStateWithLifecycle()
    val dbStep by s.dumbbellStep.collectAsStateWithLifecycle()
    val machineStep by s.machineStep.collectAsStateWithLifecycle()
    val lock by s.lockEnabled.collectAsStateWithLifecycle()
    val pass by s.passcode.collectAsStateWithLifecycle()

    var showPasscode by remember { mutableStateOf(false) }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            s.setAlarmUri(uri?.toString() ?: "default")
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Ayarlar", onBack = { nav.popBackStack() })

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                FitCard {
                    OverlineText("Görünüm")
                    Spacer(Modifier.height(10.dp))
                    com.example.ui.components.PillTabs(
                        listOf("Koyu", "Açık", "Sistem"),
                        when (themeMode) { "dark" -> 0; "light" -> 1; else -> 2 }
                    ) { s.setThemeMode(when (it) { 0 -> "dark"; 1 -> "light"; else -> "system" }) }

                    Spacer(Modifier.height(16.dp))
                    OverlineText("Vurgu rengi")
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Palette.accentPresets.forEach { (label, hex) ->
                            val c = parseHex(hex)
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        3.dp,
                                        if (accent.equals(hex, true)) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        CircleShape
                                    )
                                    .clickable { s.setAccent(hex) }
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    LabeledSwitch("AMOLED siyah", "Tam siyah arka plan, pil dostu", amoled) { s.setAmoled(it) }

                    Spacer(Modifier.height(6.dp))
                    OverlineText("Yazı boyutu · %${(fontScale * 100).toInt()}")
                    Slider(
                        value = fontScale,
                        onValueChange = { s.setFontScale((it * 20).toInt() / 20f) },
                        valueRange = 0.85f..1.25f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.fit.accent,
                            activeTrackColor = MaterialTheme.fit.accent
                        )
                    )
                }
            }

            item {
                FitCard {
                    OverlineText("Dinlenme sayacı")
                    Spacer(Modifier.height(6.dp))
                    LabeledSwitch("Otomatik başlat", "Set tamamlandığında sayaç kendiliğinden başlar", autoRest) { s.setAutoRest(it) }
                    Spacer(Modifier.height(6.dp))
                    OverlineText("Varsayılan süre · $defaultRest sn")
                    Slider(
                        value = defaultRest.toFloat(),
                        onValueChange = { s.setDefaultRest((it / 15).toInt() * 15) },
                        valueRange = 15f..300f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.fit.accent,
                            activeTrackColor = MaterialTheme.fit.accent
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    LabeledSwitch("Ekran açık kalsın", "Antrenman sırasında ekran sönmez", keepOn) { s.setKeepScreenOn(it) }
                }
            }

            item {
                FitCard {
                    OverlineText("Ses ve titreşim")
                    Spacer(Modifier.height(6.dp))
                    LabeledSwitch("Sesli uyarı", "Dinlenme bitince alarm çalar", sound) { s.setSound(it) }
                    LabeledSwitch("Son 3 saniye bip", "Geri sayımın sonunda kısa uyarı", beep) { s.setCountdownBeep(it) }
                    LabeledSwitch("Titreşim", null, vibrate) { s.setVibrate(it) }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Alarm sesi", style = MaterialTheme.typography.titleSmall)
                            Text(vm.alarmDisplayName(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.accent)
                        }
                        com.example.ui.components.RoundIconButton(Icons.Default.VolumeUp, MaterialTheme.fit.accent, 40.dp) {
                            vm.previewAlarm()
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("Cihaz sesi seç", {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_ALARM)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sesi seç")
                            }
                            ringtoneLauncher.launch(intent)
                        }, Modifier.weight(1f))
                        GhostButton("Bip", { s.setAlarmUri("beep") }, Modifier.weight(1f))
                    }
                }
            }

            item {
                FitCard {
                    OverlineText("Ekipman")
                    Spacer(Modifier.height(10.dp))
                    Text("Bar ağırlığı", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10f, 15f, 20f, 25f).forEach { v ->
                            ChoiceChip("${v.trimNum()} kg", bar == v, { s.setBarWeight(v) })
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Barbell artış adımı (toplam)", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1f, 1.25f, 2.5f, 5f).forEach { v ->
                            ChoiceChip("${v.trimNum()} kg", inc == v, { s.setIncrement(v) })
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Dambıl artış adımı (tek dambıl)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Ayarlanabilir dambılda en küçük plaka çiftinin toplamı (ör. 2 × 1 kg = 2 kg).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.fit.muted
                    )
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1f, 2f, 2.5f, 5f).forEach { v ->
                            ChoiceChip("${v.trimNum()} kg", dbStep == v, { s.setDumbbellStep(v) })
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Makine / kablo kademesi", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2.5f, 5f, 7.5f, 10f).forEach { v ->
                            ChoiceChip("${v.trimNum()} kg", machineStep == v, { s.setMachineStep(v) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Progresyon motoru ağırlık artırırken bu adımları kullanır; böylece öneri her zaman salonda gerçekten kurabileceğin bir ağırlık olur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.fit.muted
                    )
                }
            }

            item {
                FitCard {
                    OverlineText("Güvenlik")
                    Spacer(Modifier.height(6.dp))
                    LabeledSwitch(
                        "Uygulama kilidi",
                        if (pass.isBlank()) "Önce bir şifre belirle" else "Açılışta şifre sorulur",
                        lock && pass.isNotBlank()
                    ) { enabled ->
                        if (enabled && pass.isBlank()) showPasscode = true else s.setLockEnabled(enabled)
                    }
                    Spacer(Modifier.height(8.dp))
                    GhostButton(
                        if (pass.isBlank()) "Şifre belirle" else "Şifreyi değiştir",
                        { showPasscode = true },
                        Modifier.fillMaxWidth(),
                        Icons.Default.Lock
                    )
                }
            }

            item {
                FitCard {
                    OverlineText("Hakkında")
                    Spacer(Modifier.height(8.dp))
                    KeyValueRow("Uygulama", "FitFlow Pro2")
                    KeyValueRow("Sürüm", "2.0")
                    KeyValueRow("Veri", "Cihazında saklanır")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Tüm antrenman verilerin yalnızca bu cihazda tutulur. Profil ekranından JSON olarak yedek alabilir veya eski verilerini yükleyebilirsin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.fit.muted
                    )
                }
            }
        }
    }

    if (showPasscode) {
        PasscodeDialog(
            onSave = { code ->
                s.setPasscode(code)
                s.setLockEnabled(true)
                showPasscode = false
            },
            onDismiss = { showPasscode = false }
        )
    }
}

@Composable
private fun ImportDataDialog(
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
private fun PasscodeDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
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

