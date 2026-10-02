package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.NewReleases
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.core.formatTonnage
import com.example.core.trimNum
import com.example.ui.AppViewModel
import com.example.ui.components.AccentButton
import com.example.ui.components.BackupAndReminderCard
import com.example.ui.components.Badge
import com.example.ui.components.BlockSettingsDialog
import com.example.ui.components.ChoiceChip
import com.example.ui.components.FitCard
import com.example.ui.components.FitTextField
import com.example.ui.components.GhostButton
import com.example.ui.components.KeyValueRow
import com.example.ui.components.LabeledSwitch
import com.example.ui.components.OverlineText
import com.example.ui.components.PillTabs
import com.example.ui.components.RoundIconButton
import com.example.ui.theme.Palette
import com.example.ui.theme.fit
import com.example.ui.theme.mono
import com.example.ui.theme.parseHex

/* ==========================================================================
 * Ayarlar (2.17) — profil başlığı + kategori alt sayfaları
 * ========================================================================== */

private data class SettingsCat(val key: String, val title: String, val subtitle: String, val icon: ImageVector, val color: Color)

private val CATS = listOf(
    SettingsCat("look", "Görünüm", "Tema, vurgu rengi, yazı boyutu", Icons.Default.Palette, Color(0xFFA78BFA)),
    SettingsCat("train", "Antrenman", "Merdiven, ısınma, RPE, dinlenme, blok", Icons.Default.Whatshot, Color(0xFFF59E0B)),
    SettingsCat("gear", "Ekipman", "Bar, plakalar, dambıllar, makine", Icons.Default.FitnessCenter, Color(0xFF34D399)),
    SettingsCat("sound", "Ses ve titreşim", "Alarm, bip, titreşim", Icons.Default.VolumeUp, Color(0xFF60A5FA)),
    SettingsCat("notify", "Bildirimler ve eşitleme", "Hatırlatıcı, haftalık rapor, Health Connect", Icons.Default.Notifications, Color(0xFFF472B6)),
    SettingsCat("data", "Veri ve yedek", "Otomatik yedek, dışa/içe aktar, CSV", Icons.Default.Storage, Color(0xFF22D3EE)),
    SettingsCat("security", "Güvenlik", "Şifre, parmak izi", Icons.Default.Lock, Color(0xFFFB7185)),
    SettingsCat("about", "Hakkında", "Sürüm, yenilikler, geri bildirim", Icons.Default.Info, Color(0xFF94A3B8))
)

@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavHostController) {
    var page by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = page.isNotEmpty()) { page = "" }
    val cat = CATS.firstOrNull { it.key == page }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            cat?.title ?: "Ayarlar",
            cat?.subtitle,
            onBack = { if (page.isNotEmpty()) page = "" else nav.popBackStack() }
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (page) {
                "" -> {
                    item { ProfileHeader(vm) }
                    item {
                        FitCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                            CATS.forEachIndexed { i, c -> CategoryRow(c, divider = i < CATS.lastIndex) { page = c.key } }
                        }
                    }
                }
                "look" -> item { LookPage(vm) }
                "train" -> item { TrainingPage(vm) }
                "gear" -> item { EquipmentPage(vm) }
                "sound" -> item { SoundPage(vm) }
                "notify" -> item { BackupAndReminderCard(vm, setOf("notify", "health")) }
                "data" -> item { DataPage(vm) }
                "security" -> item { SecurityPage(vm) }
                "about" -> item { AboutPage() }
            }
        }
    }
}

/* ---------------------------------- Başlık --------------------------------- */

@Composable
private fun ProfileHeader(vm: AppViewModel) {
    val name by vm.settings.userName.collectAsStateWithLifecycle()
    val stats by vm.dashboard.collectAsStateWithLifecycle()
    val accent = MaterialTheme.fit.accent
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.22f), MaterialTheme.colorScheme.surface)))
            .border(1.dp, MaterialTheme.fit.cardBorder, RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(54.dp).clip(CircleShape).background(accent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name.trim().take(1).uppercase(java.util.Locale("tr")).ifBlank { "S" },
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = accent
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleLarge)
                    Text("FitFlow ${com.example.BuildConfig.VERSION_NAME} · veriler cihazında", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderStat("🔥 ${stats.streakWeeks}", "hafta seri", Modifier.weight(1f))
                HeaderStat("${stats.totalWorkouts}", "seans", Modifier.weight(1f))
                HeaderStat(formatTonnage(stats.totalVolume), "toplam hacim", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeaderStat(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f)).padding(10.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium.mono().copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted, maxLines = 1)
    }
}

@Composable
private fun CategoryRow(c: SettingsCat, divider: Boolean, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(c.color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(c.icon, null, tint = c.color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.title, style = MaterialTheme.typography.titleSmall)
                Text(c.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted, maxLines = 1)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.fit.muted)
        }
        if (divider) Box(Modifier.padding(start = 50.dp).fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)))
    }
}

/* ---------------------------------- Görünüm -------------------------------- */

@Composable
private fun LookPage(vm: AppViewModel) {
    val s = vm.settings
    val themeMode by s.themeMode.collectAsStateWithLifecycle()
    val accent by s.accent.collectAsStateWithLifecycle()
    val amoled by s.amoled.collectAsStateWithLifecycle()
    val fontScale by s.fontScale.collectAsStateWithLifecycle()
    val cardGlow by s.cardGlow.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ThemePreview()
        FitCard {
            OverlineText("Tema")
            Spacer(Modifier.height(8.dp))
            PillTabs(listOf("Koyu", "Açık", "Sistem"), when (themeMode) { "dark" -> 0; "light" -> 1; else -> 2 }) {
                s.setThemeMode(when (it) { 0 -> "dark"; 1 -> "light"; else -> "system" })
            }
            Spacer(Modifier.height(6.dp))
            LabeledSwitch("AMOLED siyah", "Tam siyah zemin, kartlar koyu gri; pil dostu", amoled) { s.setAmoled(it) }
            Spacer(Modifier.height(6.dp))
            OverlineText(if (cardGlow <= 0.01f) "Kartlarda renk geçişi · kapalı" else "Kartlarda renk geçişi · %${(cardGlow * 100).toInt()}")
            Slider(
                value = cardGlow,
                onValueChange = { s.setCardGlow((it * 20).toInt() / 20f) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.fit.accent, activeTrackColor = MaterialTheme.fit.accent)
            )
            Text("Sola çekersen kartlar düz olur; sağa çektikçe köşedeki vurgu rengi belirginleşir.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
        }
        FitCard {
            OverlineText("Vurgu rengi")
            Spacer(Modifier.height(10.dp))
            Palette.accentPresets.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { (label, hex) ->
                        val c = parseHex(hex)
                        val on = accent.equals(hex, true)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(58.dp).clickable { s.setAccent(hex) }) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(c)
                                    .border(3.dp, if (on) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                            )
                            Text(label, style = MaterialTheme.typography.labelSmall, color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.fit.muted, maxLines = 1)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val custom = Palette.accentPresets.none { it.second.equals(accent, true) }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.fit.elevated)
                    .clickable { showPicker = true }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(
                        Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))
                    ),
                    contentAlignment = Alignment.Center
                ) { Box(Modifier.size(16.dp).clip(CircleShape).background(MaterialTheme.fit.accent)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Özel renk", style = MaterialTheme.typography.titleSmall)
                    Text(if (custom) "Seçili: ${accent.uppercase()}" else "Sınırsız palet: istediğin tonu seç",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.fit.muted)
            }
        }
        if (showPicker) {
            ColorPickerDialog(
                initial = parseHex(accent),
                onPick = { hex -> s.setAccent(hex); showPicker = false },
                onDismiss = { showPicker = false }
            )
        }
        FitCard {
            OverlineText("Yazı boyutu · %${(fontScale * 100).toInt()}")
            Slider(
                value = fontScale,
                onValueChange = { s.setFontScale((it * 20).toInt() / 20f) },
                valueRange = 0.85f..1.25f,
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.fit.accent, activeTrackColor = MaterialTheme.fit.accent)
            )
            Text("Örnek: Bench Press · 80 kg × 8", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Seçili tema ve vurgu rengiyle küçük bir antrenman kartı önizlemesi. */
@Composable
private fun ThemePreview() {
    val accent = MaterialTheme.fit.accent
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.fit.cardBorder, RoundedCornerShape(22.dp)).padding(12.dp)
    ) {
        OverlineText("Önizleme")
        Spacer(Modifier.height(8.dp))
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BUGÜN · İTİŞ", style = MaterialTheme.typography.labelSmall.mono(), color = accent, modifier = Modifier.weight(1f))
                Badge("RIR 1-2", accent)
            }
            Spacer(Modifier.height(6.dp))
            Text("Bench Press", style = MaterialTheme.typography.titleMedium)
            Text("80 kg · 7/8/9", style = MaterialTheme.typography.titleSmall.mono(), color = MaterialTheme.fit.muted)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(accent).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("Başla", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.fit.onAccent)
                }
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.fit.elevated).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("Tekrar artır", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.fit.success)
                }
            }
        }
    }
}

/* --------------------------------- Antrenman ------------------------------- */

@Composable
private fun TrainingPage(vm: AppViewModel) {
    val s = vm.settings
    val ladderOn by s.ladderEnabled.collectAsStateWithLifecycle()
    val warmup by s.warmupOn.collectAsStateWithLifecycle()
    val rpe by s.rpeOn.collectAsStateWithLifecycle()
    val pr by s.prCelebrate.collectAsStateWithLifecycle()
    val autoRest by s.autoRest.collectAsStateWithLifecycle()
    val defaultRest by s.defaultRest.collectAsStateWithLifecycle()
    val keepOn by s.keepScreenOn.collectAsStateWithLifecycle()
    val block by s.blockLoadWeeks.collectAsStateWithLifecycle()
    var showBlock by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FitCard {
            OverlineText("Öneriler")
            LabeledSwitch("Tekrar merdiveni", "Kapalıysa tüm hareketlerde programdaki tekrar aralığı kullanılır. Hareket bazında ayar Program ekranında.", ladderOn) { s.setLadderEnabled(it) }
            LabeledSwitch("Isınma önerisi", "Ana hareketlerde ısınma setleri ve plaka dizilimi", warmup) { s.setWarmupOn(it) }
            LabeledSwitch("RPE sütunu", "Setlerde zorluk (RPE) girişi; kapatırsan yorgunluk analizleri daha az veriyle çalışır", rpe) { s.setRpeOn(it) }
            LabeledSwitch("Rekor kutlaması", "Yeni kişisel rekorda kutlama ekranı", pr) { s.setPrCelebrate(it) }
        }
        FitCard(onClick = { showBlock = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    OverlineText("Blok ve deload")
                    Spacer(Modifier.height(4.dp))
                    Text(if (block > 0) "$block hafta yüklenme + 1 hafta deload" else "Otomatik (yorgunluğa göre 6-8. hafta)", style = MaterialTheme.typography.titleSmall)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.fit.muted)
            }
        }
        FitCard {
            OverlineText("Dinlenme sayacı")
            LabeledSwitch("Otomatik başlat", "Set tamamlanınca sayaç kendiliğinden başlar", autoRest) { s.setAutoRest(it) }
            OverlineText("Varsayılan süre · $defaultRest sn")
            Slider(
                value = defaultRest.toFloat(),
                onValueChange = { s.setDefaultRest((it / 15).toInt() * 15) },
                valueRange = 15f..300f,
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.fit.accent, activeTrackColor = MaterialTheme.fit.accent)
            )
            LabeledSwitch("Ekran açık kalsın", "Antrenman sırasında ekran sönmez", keepOn) { s.setKeepScreenOn(it) }
        }
    }
    if (showBlock) BlockSettingsDialog(block, onSave = { vm.setBlockLoadWeeks(it); showBlock = false }, onDismiss = { showBlock = false })
}

/* ---------------------------------- Ekipman -------------------------------- */

private val PLATE_OPTIONS = listOf(25f, 20f, 15f, 10f, 5f, 2.5f, 2f, 1.25f, 1f, 0.5f)
private val DUMBBELL_OPTIONS = listOf(
    1f, 2f, 2.5f, 3f, 4f, 5f, 6f, 7f, 7.5f, 8f, 9f, 10f, 12f, 12.5f, 14f, 15f, 16f, 17.5f, 18f, 20f,
    22f, 22.5f, 24f, 25f, 26f, 27.5f, 28f, 30f, 32f, 32.5f, 34f, 35f, 36f, 37.5f, 40f, 42.5f, 45f, 47.5f, 50f
)

@Composable
private fun EquipmentPage(vm: AppViewModel) {
    val s = vm.settings
    val bar by s.barWeight.collectAsStateWithLifecycle()
    val inc by s.increment.collectAsStateWithLifecycle()
    val dbStep by s.dumbbellStep.collectAsStateWithLifecycle()
    val machineStep by s.machineStep.collectAsStateWithLifecycle()
    val plates by s.plates.collectAsStateWithLifecycle()
    val dumbbells by s.dumbbells.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FitCard {
            OverlineText("Barbell")
            Spacer(Modifier.height(8.dp))
            Text("Bar ağırlığı", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10f, 15f, 20f, 25f).forEach { v -> ChoiceChip("${v.trimNum()} kg", bar == v, { s.setBarWeight(v) }) }
            }
            Spacer(Modifier.height(12.dp))
            Text("Artış adımı (toplam)", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1f, 1.25f, 2.5f, 5f).forEach { v -> ChoiceChip("${v.trimNum()} kg", inc == v, { s.setIncrement(v) }) }
            }
            Spacer(Modifier.height(12.dp))
            Text("Salondaki plakalar", style = MaterialTheme.typography.titleSmall)
            Text("Plaka dizilimi yalnızca bunlarla hesaplanır.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
            ToggleGrid(PLATE_OPTIONS, plates, perRow = 5) { v ->
                s.setPlates(if (v in plates) plates - v else plates + v)
            }
        }
        FitCard {
            OverlineText("Dambıl")
            LabeledSwitch(
                "Sabit dambıllar",
                if (dumbbells.isEmpty()) "Kapalı: ayarlanabilir dambıl, adım ${dbStep.trimNum()} kg" else "${dumbbells.size} dambıl · öneriler bunlara yuvarlanır",
                dumbbells.isNotEmpty()
            ) { on ->
                s.setDumbbells(if (on) (1..20).map { it * 2.5f } else emptyList())
            }
            if (dumbbells.isEmpty()) {
                Text("Ayarlanabilir dambıl adımı (tek dambıl)", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1f, 2f, 2.5f, 5f).forEach { v -> ChoiceChip("${v.trimNum()} kg", dbStep == v, { s.setDumbbellStep(v) }) }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("2,5 kg aralık", { s.setDumbbells((1..20).map { it * 2.5f }) }, Modifier.weight(1f))
                    GhostButton("2 kg aralık", { s.setDumbbells((1..20).map { it * 2f }) }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Text("Salonda olanları işaretle:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
                ToggleGrid(DUMBBELL_OPTIONS, dumbbells, perRow = 5) { v ->
                    s.setDumbbells(if (v in dumbbells) dumbbells - v else dumbbells + v)
                }
            }
        }
        FitCard {
            OverlineText("Makine / kablo")
            Spacer(Modifier.height(6.dp))
            Text("Ağırlık kademesi", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2.5f, 5f, 7.5f, 10f).forEach { v -> ChoiceChip("${v.trimNum()} kg", machineStep == v, { s.setMachineStep(v) }) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Progresyon motoru artış ve düşüşleri bu ekipmana göre yapar; öneri her zaman salonda gerçekten kurabileceğin bir ağırlık olur.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted
            )
        }
    }
}

@Composable
private fun ToggleGrid(options: List<Float>, selected: List<Float>, perRow: Int, onToggle: (Float) -> Unit) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { v ->
                    val on = selected.any { kotlin.math.abs(it - v) < 0.01f }
                    Box(
                        Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (on) MaterialTheme.fit.accent.copy(alpha = 0.16f) else MaterialTheme.fit.elevated)
                            .border(1.dp, if (on) MaterialTheme.fit.accent else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { onToggle(v) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(v.trimNum().replace('.', ','), style = MaterialTheme.typography.labelLarge.mono(),
                            color = if (on) MaterialTheme.fit.accent else MaterialTheme.fit.muted)
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/* ------------------------------ Ses ve titreşim ----------------------------- */

@Composable
private fun SoundPage(vm: AppViewModel) {
    val s = vm.settings
    val sound by s.sound.collectAsStateWithLifecycle()
    val vibrate by s.vibrate.collectAsStateWithLifecycle()
    val beep by s.countdownBeep.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            s.setAlarmUri(uri?.toString() ?: "default")
        }
    }
    FitCard {
        LabeledSwitch("Sesli uyarı", "Dinlenme bitince alarm çalar", sound) { s.setSound(it) }
        LabeledSwitch("Son 3 saniye bip", "Geri sayımın sonunda kısa uyarı", beep) { s.setCountdownBeep(it) }
        LabeledSwitch("Titreşim", null, vibrate) { s.setVibrate(it) }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Alarm sesi", style = MaterialTheme.typography.titleSmall)
                Text(vm.alarmDisplayName(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.accent)
            }
            RoundIconButton(Icons.Default.VolumeUp, MaterialTheme.fit.accent, 40.dp) { vm.previewAlarm() }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Cihaz sesi seç", {
                picker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_ALARM)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sesi seç")
                })
            }, Modifier.weight(1f))
            GhostButton("Bip", { s.setAlarmUri("beep") }, Modifier.weight(1f))
        }
    }
}

/* ------------------------------- Veri ve yedek ------------------------------ */

@Composable
private fun DataPage(vm: AppViewModel) {
    val context = LocalContext.current
    var showExport by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackupAndReminderCard(vm, setOf("backup"))
        FitCard {
            OverlineText("Dışa ve içe aktar")
            Spacer(Modifier.height(10.dp))
            ActionRow(Icons.Default.Upload, "Yedeği dışa aktar (JSON)", "Tüm veriler; başka telefona taşımak için") { showExport = true }
            ActionRow(Icons.Default.Download, "Yedekten yükle", "JSON dosyası ya da eski FitFlow") { showImport = true }
            ActionRow(Icons.Default.TableChart, "Excel için CSV", "Tüm setler: tarih, hareket, kg, tekrar, RPE, hacim") {
                if (!vm.shareCsv(context)) message = "CSV oluşturulamadı."
            }
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
        }
        FitCard(border = MaterialTheme.fit.danger.copy(alpha = 0.35f)) {
            OverlineText("Tehlikeli bölge", color = MaterialTheme.fit.danger)
            Spacer(Modifier.height(6.dp))
            Text("Antrenman geçmişini, setleri ve rekorları kalıcı olarak siler. Program, hareketler ve ölçümler kalır.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
            Spacer(Modifier.height(10.dp))
            GhostButton("Antrenman geçmişini sil", { showReset = true }, Modifier.fillMaxWidth(), Icons.Default.DeleteForever, MaterialTheme.fit.danger)
        }
    }

    if (showExport) ExportDataDialog(vm = vm, onDismiss = { showExport = false })
    if (showImport) {
        ImportDataDialog(
            onImportJson = { json -> showImport = false; vm.importBackupJson(json) { r -> message = r.message } },
            onImportLegacy = {
                showImport = false
                vm.importLegacyDatabase { ok -> message = if (ok) "Eski FitFlow verileri aktarıldı." else "Eski veritabanı bulunamadı." }
            },
            onDismiss = { showImport = false }
        )
    }
    if (showReset) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReset = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Geçmişi sil", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Bu işlem geri alınamaz. Önce yedek almanı öneririm. Onaylamak için SİL yaz.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.fit.muted)
                    FitTextField(typed, { typed = it }, "SİL")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = typed.trim().uppercase(java.util.Locale("tr")) == "SİL",
                    onClick = { vm.clearAllWorkoutHistory(); showReset = false; message = "Antrenman geçmişi silindi." }
                ) { Text("Kalıcı olarak sil", color = MaterialTheme.fit.danger) }
            },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text("Vazgeç", color = MaterialTheme.fit.muted) } }
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.fit.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.fit.muted)
    }
}

/* --------------------------------- Güvenlik -------------------------------- */

@Composable
private fun SecurityPage(vm: AppViewModel) {
    val s = vm.settings
    val context = LocalContext.current
    val lock by s.lockEnabled.collectAsStateWithLifecycle()
    val pass by s.passcode.collectAsStateWithLifecycle()
    val bio by s.biometricOn.collectAsStateWithLifecycle()
    var showPass by remember { mutableStateOf(false) }
    val canBio = remember {
        androidx.biometric.BiometricManager.from(context)
            .canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    }
    FitCard {
        LabeledSwitch(
            "Uygulama kilidi",
            if (pass.isBlank()) "Önce bir şifre belirle" else "Açılışta şifre sorulur",
            lock && pass.isNotBlank()
        ) { on -> if (on && pass.isBlank()) showPass = true else s.setLockEnabled(on) }
        LabeledSwitch(
            "Parmak izi ile aç",
            when {
                !canBio -> "Bu telefonda kayıtlı parmak izi / yüz tanıma yok"
                pass.isBlank() -> "Önce şifre belirle (yedek giriş yolu)"
                else -> "Kilit ekranında otomatik sorulur; şifre yedek olarak kalır"
            },
            bio && canBio && pass.isNotBlank()
        ) { on -> if (canBio && pass.isNotBlank()) s.setBiometricOn(on) }
        Spacer(Modifier.height(8.dp))
        GhostButton(if (pass.isBlank()) "Şifre belirle" else "Şifreyi değiştir", { showPass = true }, Modifier.fillMaxWidth(), Icons.Default.Lock)
        Spacer(Modifier.height(8.dp))
        Text("Şifre telefonda özetlenmiş (şifrelenmiş) olarak saklanır.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
    }
    if (showPass) {
        PasscodeDialog(onSave = { code -> s.setPasscode(code); s.setLockEnabled(true); showPass = false }, onDismiss = { showPass = false })
    }
}

/* --------------------------------- Hakkında -------------------------------- */

private val CHANGELOG = listOf(
    "2.38" to "Kaslar sekmesinde kas detayındaki hareket dökümü yenilendi; seans süresi büyük; dinlenme çubuğu başka ekrandayken seansa döndürür, seanstayken büyük sayacı açar.",
    "2.37" to "Gün kartları yukarı/aşağı taşınabilir; odak hareketlerden otomatik dolar; adında gün adı geçen günler o güne bağlanır, Bugün kartı haftanın gününe göre gelir.",
    "2.36" to "Kütüphaneye Dumbbell Calf Raise (Ayakta) eklendi (iki dambıl hacmi doğru sayılır); tek bacak calf raise tek dambıl sayılır.",
    "2.35" to "Program kartındaki set/ağırlık/tekrar seansa birebir aynı gelir (deload dahil): kart, seans açılışı ve seans içi öneri tek hesaptan beslenir.",
    "2.34" to "Gün düzenleyici: hareketleri sırala, sil (geri al), dokunup tüm ayarlarına ulaş. Seansta değişen hareket sırası programa da yazılır (geri alınabilir); süperset birlikte taşınır.",
    "2.33" to "Dambıl hacmi iki dambılla sayılır; seansta anında PR rozeti, geri al, kalan setlere uygula, süperset turları, hareket bazlı dinlenme, drop/tükeniş seti, hareket sırası, dinlenme duraklatma ve büyük sayaç; geçmişten seansı tekrarla.",
    "2.32" to "Hesap denetimi: işaretlenmeyen setler artık kaydedilmez, 1,25 kg adımı düzeltildi, sahte plato/gerileme ve yanlış aşırı yüklenme uyarıları giderildi, deload kıyaslardan çıkarıldı, makine squat/bench güç seviyesine sayılmaz.",
    "2.31" to "Güç özeti düzeltmesi: deadlift yoksa RDL toplama katılır; her kaldırışın en iyi seti ve 1RM dökümü gösterilir.",
    "2.30" to "Deload haftası: kartlar ve seans deload set/ağırlıklarını gösterir, öneriler son normal haftaya göre hesaplanır. Uygulama içinden güncelleme (indir ve kur).",
    "2.29" to "Programdan çıkan ve 14 gündür yapılmayan hareketler ilerleme ekranlarında gizlenir; programa geri eklenince geçmişiyle birlikte döner. Yeni güç seviyesi merdiveni.",
    "2.28" to "Güç sekmesi: güç özeti, dönem seçmeli 1RM grafiği (rekor noktaları, eğilim), 1RM hedefi ve tahmini süre, tekrar–ağırlık tablosu, plato uyarıları. Kaslar: kas × hafta ısı haritası, program uyumu, kas detayında güç göstergesi.",
    "2.27" to "Geçmiş seanslar kilitli açılır; düzenleme ve hareket/set silme onay ister. Ana ekranda 4 haftalık özet kartı.",
    "2.26" to "Akıllı öneriler: seansı uzatmayan değişim önerileri, iki seçenekli kartlar ve süre etkisi, tekrar eden hareket / sıralama / dinlenme analizi. Öneriler kas haritasında ve program kapsam kartında. Açık seans şeridi daha belirgin.",
    "2.25" to "Yeni Akıllı öneriler: program puanı, kas dengesi, tek dokunuşla uygulanan öncelikli öneriler. Tam ekran kas haritasında detay kaydırılabilir.",
    "2.17" to "Yeni ayarlar: kategoriler, tema önizleme, salon plakaları ve dambılları, antrenman tercihleri, CSV dışa aktarma, parmak izi kilidi.",
    "2.16" to "Açılır gün kartları, hareket paneli, hareket başına tekrar merdiveni (ters / özel), seans devam et / bitir.",
    "2.15" to "Ana ekran widget'ı, Health Connect eşitlemesi.",
    "2.14" to "Gelişim fotoğrafları ve karşılaştırma, görsel seans paylaşım kartı.",
    "2.13" to "Kademeli toparlanma haritası, kasa dokununca bilgi.",
    "2.12" to "Alternatif hareket önerisi, kilit ekranı sayacında +30 sn / Atla.",
    "2.11" to "Otomatik yedek, antrenman hatırlatıcısı, haftalık rapor.",
    "2.10" to "Toparlanmış kaslar renksiz, trend grafiğinde çizgi seçeneği.",
    "2.9" to "Koyu ve AMOLED temada belirgin kart katmanları.",
    "2.8" to "Sade deload kartı ve yok say, planlı blok, hedef RIR, genişletilmiş ısınma."
)

@Composable
private fun AboutPage() {
    val context = LocalContext.current
    var showAll by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FitCard {
            KeyValueRow("Uygulama", "FitFlow")
            KeyValueRow("Sürüm", com.example.BuildConfig.VERSION_NAME)
            KeyValueRow("Veri", "Yalnızca bu cihazda")
            KeyValueRow("İnternet", "Yalnızca güncelleme denetimi")
        }
        UpdateSection()
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NewReleases, null, tint = MaterialTheme.fit.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Yenilikler", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            (if (showAll) CHANGELOG else CHANGELOG.take(3)).forEach { (v, t) ->
                Row(Modifier.padding(vertical = 5.dp)) {
                    Text(v, style = MaterialTheme.typography.labelLarge.mono(), color = MaterialTheme.fit.accent, modifier = Modifier.width(44.dp))
                    Text(t, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!showAll) {
                Text("Tümünü göster", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.fit.accent,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showAll = true }.padding(vertical = 6.dp))
            }
        }
        GhostButton("Geri bildirim gönder", {
            val body = "\n\n---\nFitFlow ${com.example.BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}"
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                putExtra(Intent.EXTRA_SUBJECT, "FitFlow geri bildirim")
                putExtra(Intent.EXTRA_TEXT, body)
            }
            runCatching { context.startActivity(Intent.createChooser(intent, "Geri bildirim gönder")) }
        }, Modifier.fillMaxWidth(), Icons.Default.Mail)
    }
}


@Composable
private fun UpdateSection() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var latest by remember { mutableStateOf<com.example.ui.AppUpdate.Info?>(null) }
    var busy by remember { mutableStateOf(false) }
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SystemUpdate, null, tint = MaterialTheme.fit.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Güncelleme", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            status ?: "Yeni sürümler GitHub'da yayımlanır. Uygulama açılışta kendisi denetler; buradan elle de bakabilirsin.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted
        )
        Spacer(Modifier.height(10.dp))
        val l = latest
        if (l != null && com.example.ui.AppUpdate.isNewer(l.version, com.example.BuildConfig.VERSION_NAME)) {
            AccentButton("${l.version} sürümünü indir ve kur", { com.example.ui.AppUpdate.download(context, l.apkUrl) }, Modifier.fillMaxWidth(), Icons.Default.Download)
        } else {
            GhostButton(if (busy) "Denetleniyor…" else "Güncellemeleri denetle", {
                if (!busy) {
                    busy = true
                    scope.launch {
                        val info = com.example.ui.AppUpdate.latest()
                        busy = false
                        latest = info
                        status = when {
                            info == null -> "Denetlenemedi. İnternet bağlantını kontrol et."
                            com.example.ui.AppUpdate.isNewer(info.version, com.example.BuildConfig.VERSION_NAME) -> "Yeni sürüm var: ${info.version}"
                            else -> "En güncel sürümü kullanıyorsun (${com.example.BuildConfig.VERSION_NAME})."
                        }
                    }
                }
            }, Modifier.fillMaxWidth(), Icons.Default.Refresh)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Her zaman son sürüm: ${com.example.ui.AppUpdate.LATEST_APK}",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.accent,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { com.example.ui.AppUpdate.download(context) }.padding(vertical = 4.dp)
        )
    }
}

/* ------------------------------ Sınırsız renk seçici ----------------------------- */

/** HSV renk seçici: doygunluk/parlaklık alanı + ton şeridi + HEX kodu. */
@Composable
private fun ColorPickerDialog(initial: Color, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val hsv0 = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(android.graphics.Color.rgb(
            (initial.red * 255).toInt(), (initial.green * 255).toInt(), (initial.blue * 255).toInt()), it) }
    }
    var hue by remember { mutableStateOf(hsv0[0]) }
    var sat by remember { mutableStateOf(hsv0[1]) }
    var value by remember { mutableStateOf(hsv0[2]) }
    val color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
    val hex = "#%06X".format(0xFFFFFF and android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
    var hexText by remember { mutableStateOf(hex) }
    androidx.compose.runtime.LaunchedEffect(hex) { hexText = hex }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Özel vurgu rengi", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Doygunluk (yatay) × parlaklık (dikey)
                val pure = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                Box(
                    Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(Color.White, pure)))
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                        .pointerInputSv { x, y -> sat = x; value = 1f - y }
                ) {
                    androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                        val c = androidx.compose.ui.geometry.Offset(sat * size.width, (1f - value) * size.height)
                        drawCircle(Color.White, radius = 11.dp.toPx(), center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                        drawCircle(Color.Black.copy(alpha = 0.4f), radius = 13.dp.toPx(), center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                    }
                }
                // Ton şeridi
                Box(
                    Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient((0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) }))
                        .pointerInputSv { x, _ -> hue = (x * 360f).coerceIn(0f, 359.9f) }
                ) {
                    androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                        val x = hue / 360f * size.width
                        drawCircle(Color.White, radius = size.height / 2 - 2.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, size.height / 2),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(color))
                    Box(Modifier.weight(1f)) {
                        FitTextField(hexText, { t ->
                            hexText = t
                            val clean = t.trim().removePrefix("#")
                            if (clean.length == 6 && clean.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                                val c = android.graphics.Color.parseColor("#$clean")
                                val h = FloatArray(3); android.graphics.Color.colorToHSV(c, h)
                                hue = h[0]; sat = h[1]; value = h[2]
                            }
                        }, "HEX kodu")
                    }
                }
                if (value < 0.35f) {
                    Text("Çok koyu renkler koyu temada az görünür.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.warning)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(hex) }) { Text("Uygula", color = color) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç", color = MaterialTheme.fit.muted) } }
    )
}

/** Dokunma ve sürüklemeyi 0..1 aralığında (x, y) olarak bildirir. */
private fun Modifier.pointerInputSv(onChange: (Float, Float) -> Unit): Modifier = this.pointerInput(Unit) {
    fun emit(p: androidx.compose.ui.geometry.Offset) =
        onChange((p.x / size.width).coerceIn(0f, 1f), (p.y / size.height).coerceIn(0f, 1f))
    awaitEachGesture {
        val down = awaitFirstDown()
        emit(down.position)
        drag(down.id) { change -> change.consume(); emit(change.position) }
    }
}
