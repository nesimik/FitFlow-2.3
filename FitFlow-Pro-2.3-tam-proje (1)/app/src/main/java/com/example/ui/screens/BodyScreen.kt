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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.core.TR
import com.example.core.formatDate
import com.example.core.formatDateShort
import com.example.core.trimNum
import com.example.data.BodyMetricEntity
import com.example.data.NoteEntity
import com.example.ui.AppViewModel
import com.example.ui.components.Badge
import com.example.ui.components.ChoiceChip
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.FitCard
import com.example.ui.components.FitTextField
import com.example.ui.components.LineChart
import com.example.ui.components.OverlineText
import com.example.ui.components.PillTabs
import com.example.ui.components.RoundIconButton
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatTile
import com.example.ui.theme.Palette
import com.example.ui.theme.fit
import com.example.ui.theme.parseHex

/* ============================== Vücut ölçümleri ============================== */

@Composable
fun BodyScreen(vm: AppViewModel, nav: NavHostController) {
    val metrics by vm.bodyMetrics.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BodyMetricEntity?>(null) }
    var toDelete by remember { mutableStateOf<BodyMetricEntity?>(null) }
    var metric by remember { mutableIntStateOf(0) }

    val sorted = remember(metrics) { metrics.sortedBy { it.dateMillis } }
    val labels = sorted.map { formatDateShort(it.dateMillis) }
    val values = sorted.map {
        when (metric) {
            0 -> it.weightKg
            1 -> it.bodyFatPct
            2 -> it.waistCm
            3 -> it.chestCm
            else -> it.armCm
        }
    }
    val filtered = values.zip(labels).filter { it.first > 0f }
    val latest = metrics.maxByOrNull { it.dateMillis }
    val first = sorted.firstOrNull { it.weightKg > 0f }
    val diff = if (latest != null && first != null && latest.weightKg > 0f) latest.weightKg - first.weightKg else 0f

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Vücut ölçümleri", "${metrics.size} kayıt", onBack = { nav.popBackStack() }) {
            RoundIconButton(Icons.Default.Add, MaterialTheme.fit.accent, 40.dp) { showAdd = true }
        }

        if (metrics.isEmpty()) {
            EmptyState(
                Icons.Default.MonitorWeight,
                "Henüz ölçüm yok",
                "Kilo ve çevre ölçümlerini düzenli kaydettiğinde değişimi grafikte görebilirsin.",
                actionLabel = "İlk ölçümü ekle",
                onAction = { showAdd = true }
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            "Güncel kilo",
                            latest?.weightKg?.takeIf { it > 0f }?.let { "${it.trimNum()} kg" } ?: "—",
                            Modifier.weight(1f),
                            caption = if (diff != 0f) (if (diff > 0) "▲ +${diff.trimNum()} kg" else "▼ ${diff.trimNum()} kg") else null,
                            captionColor = if (diff > 0) MaterialTheme.fit.warning else MaterialTheme.fit.success
                        )
                        StatTile(
                            "Yağ oranı",
                            latest?.bodyFatPct?.takeIf { it > 0f }?.let { "%${it.trimNum()}" } ?: "—",
                            Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Column {
                        PillTabs(listOf("Kilo", "Yağ %", "Bel", "Göğüs", "Kol"), metric) { metric = it }
                        Spacer(Modifier.height(12.dp))
                        FitCard {
                            if (filtered.size >= 2) {
                                LineChart(
                                    filtered.map { it.first },
                                    filtered.map { it.second },
                                    suffix = if (metric == 1) " %" else if (metric == 0) " kg" else " cm",
                                    height = 180.dp
                                )
                            } else {
                                Text(
                                    "Grafik için en az iki ölçüm gerekli.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.fit.muted
                                )
                            }
                        }
                    }
                }

                item { SectionHeader("Kayıtlar") }

                items(metrics, key = { it.id }) { m ->
                    FitCard(onClick = { editing = m }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(formatDate(m.dateMillis), style = MaterialTheme.typography.titleSmall)
                                if (m.note.isNotBlank()) {
                                    Text(m.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted, maxLines = 1)
                                }
                            }
                            RoundIconButton(Icons.Default.Delete, MaterialTheme.fit.danger, 34.dp, Color.Transparent) { toDelete = m }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (m.weightKg > 0f) Badge("${m.weightKg.trimNum()} kg", MaterialTheme.fit.accent)
                            if (m.bodyFatPct > 0f) Badge("%${m.bodyFatPct.trimNum()} yağ", Palette.warning)
                            if (m.chestCm > 0f) Badge("Göğüs ${m.chestCm.trimNum()}", MaterialTheme.fit.muted)
                            if (m.waistCm > 0f) Badge("Bel ${m.waistCm.trimNum()}", MaterialTheme.fit.muted)
                            if (m.hipCm > 0f) Badge("Kalça ${m.hipCm.trimNum()}", MaterialTheme.fit.muted)
                            if (m.armCm > 0f) Badge("Kol ${m.armCm.trimNum()}", MaterialTheme.fit.muted)
                            if (m.thighCm > 0f) Badge("Bacak ${m.thighCm.trimNum()}", MaterialTheme.fit.muted)
                        }
                    }
                }
            }
        }
    }

    if (showAdd || editing != null) {
        BodyMetricDialog(
            initial = editing,
            onSave = { m -> vm.saveBodyMetric(m); showAdd = false; editing = null },
            onDismiss = { showAdd = false; editing = null }
        )
    }

    toDelete?.let { m ->
        ConfirmDialog(
            title = "Ölçümü sil",
            text = "${formatDate(m.dateMillis)} tarihli kayıt silinecek.",
            confirmLabel = "Sil",
            destructive = true,
            onConfirm = { vm.deleteBodyMetric(m); toDelete = null },
            onDismiss = { toDelete = null }
        )
    }
}

@Composable
private fun BodyMetricDialog(
    initial: BodyMetricEntity?,
    onSave: (BodyMetricEntity) -> Unit,
    onDismiss: () -> Unit
) {
    fun f(v: Float) = if (v > 0f) v.trimNum() else ""
    var weight by remember { mutableStateOf(f(initial?.weightKg ?: 0f)) }
    var fat by remember { mutableStateOf(f(initial?.bodyFatPct ?: 0f)) }
    var chest by remember { mutableStateOf(f(initial?.chestCm ?: 0f)) }
    var waist by remember { mutableStateOf(f(initial?.waistCm ?: 0f)) }
    var hip by remember { mutableStateOf(f(initial?.hipCm ?: 0f)) }
    var arm by remember { mutableStateOf(f(initial?.armCm ?: 0f)) }
    var thigh by remember { mutableStateOf(f(initial?.thighCm ?: 0f)) }
    var neck by remember { mutableStateOf(f(initial?.neckCm ?: 0f)) }
    var note by remember { mutableStateOf(initial?.note ?: "") }

    fun p(s: String) = s.replace(',', '.').toFloatOrNull() ?: 0f

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(if (initial == null) "Yeni ölçüm" else "Ölçümü düzenle", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                run {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FitTextField(weight, { weight = it }, "Kilo (kg)", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                        FitTextField(fat, { fat = it }, "Yağ (%)", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                    }
                }
                run {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FitTextField(chest, { chest = it }, "Göğüs (cm)", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                        FitTextField(waist, { waist = it }, "Bel (cm)", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                    }
                }
                run {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FitTextField(hip, { hip = it }, "Kalça (cm)", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                        FitTextField(neck, { neck = it }, "Boyun (cm)", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                    }
                }
                run {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FitTextField(arm, { arm = it }, "Kol (cm)", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                        FitTextField(thigh, { thigh = it }, "Bacak (cm)", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                    }
                }
                FitTextField(note, { note = it }, "Not", singleLine = false, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    (initial ?: BodyMetricEntity()).copy(
                        weightKg = p(weight), bodyFatPct = p(fat), chestCm = p(chest),
                        waistCm = p(waist), hipCm = p(hip), armCm = p(arm),
                        thighCm = p(thigh), neckCm = p(neck), note = note.trim()
                    )
                )
            }) { Text("Kaydet", color = MaterialTheme.fit.accent, style = MaterialTheme.typography.titleMedium) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç", color = MaterialTheme.fit.muted) } }
    )
}

/* ================================== Notlar ================================== */

private val noteCategories = listOf("Genel", "Antrenman", "Beslenme", "Sakatlık", "Hedef")
private val noteColors = listOf("#22D3EE", "#22C55E", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899")

@Composable
fun NotesScreen(vm: AppViewModel, nav: NavHostController) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Hepsi") }
    var editing by remember { mutableStateOf<NoteEntity?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<NoteEntity?>(null) }

    val filtered = remember(notes, query, category) {
        notes.filter { n ->
            (category == "Hepsi" || n.category == category) &&
                (query.isBlank() ||
                    n.title.lowercase(TR).contains(query.lowercase(TR)) ||
                    n.content.lowercase(TR).contains(query.lowercase(TR)))
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Notlar", "${notes.size} kayıt", onBack = { nav.popBackStack() }) {
            RoundIconButton(Icons.Default.Add, MaterialTheme.fit.accent, 40.dp) { showAdd = true }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            SearchField(query, { query = it }, "Notlarda ara…")
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                (listOf("Hepsi") + noteCategories).forEach {
                    ChoiceChip(it, category == it, { category = it })
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            EmptyState(
                Icons.Default.EditNote,
                if (notes.isEmpty()) "Not yok" else "Sonuç bulunamadı",
                if (notes.isEmpty()) "Antrenman fikirlerini, sakatlık takibini ve hedeflerini buraya yazabilirsin."
                else "Arama kriterine uyan not yok.",
                actionLabel = if (notes.isEmpty()) "İlk notu ekle" else null,
                onAction = { showAdd = true }
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { n ->
                    val c = parseHex(n.colorHex)
                    FitCard(onClick = { editing = n }, border = c.copy(alpha = 0.35f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(c))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                n.title.ifBlank { "Başlıksız" },
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (n.isPinned) {
                                Icon(Icons.Default.PushPin, null, tint = c, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            RoundIconButton(Icons.Default.Delete, MaterialTheme.fit.danger, 32.dp, Color.Transparent) { toDelete = n }
                        }
                        if (n.content.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                n.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.fit.muted,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Badge(n.category, c)
                            Badge(formatDate(n.dateMillis), MaterialTheme.fit.muted)
                        }
                    }
                }
            }
        }
    }

    if (showAdd || editing != null) {
        NoteDialog(
            initial = editing,
            onSave = { vm.saveNote(it); showAdd = false; editing = null },
            onDismiss = { showAdd = false; editing = null }
        )
    }

    toDelete?.let { n ->
        ConfirmDialog(
            title = "Notu sil",
            text = "\"${n.title.ifBlank { "Başlıksız" }}\" silinecek.",
            confirmLabel = "Sil",
            destructive = true,
            onConfirm = { vm.deleteNote(n); toDelete = null },
            onDismiss = { toDelete = null }
        )
    }
}

@Composable
private fun NoteDialog(
    initial: NoteEntity?,
    onSave: (NoteEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var content by remember { mutableStateOf(initial?.content ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "Genel") }
    var color by remember { mutableStateOf(initial?.colorHex ?: noteColors.first()) }
    var pinned by remember { mutableStateOf(initial?.isPinned ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(if (initial == null) "Yeni not" else "Notu düzenle", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FitTextField(title, { title = it }, "Başlık")
                FitTextField(content, { content = it }, "İçerik", singleLine = false, minLines = 4)
                Column {
                    OverlineText("Kategori")
                    Spacer(Modifier.height(7.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        noteCategories.forEach { ChoiceChip(it, category == it, { category = it }) }
                    }
                }
                Column {
                    OverlineText("Renk")
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        noteColors.forEach { hex ->
                            val c = parseHex(hex)
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(c)
                                    .clickable { color = hex }
                            ) {
                                if (color == hex) {
                                    Text("✓", color = Color.White, modifier = Modifier.align(Alignment.Center))
                                }
                            }
                        }
                    }
                }
                com.example.ui.components.LabeledSwitch("Sabitle", "Listenin başında dursun", pinned) { pinned = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    (initial ?: NoteEntity(title = "", content = "")).copy(
                        title = title.trim(),
                        content = content.trim(),
                        category = category,
                        colorHex = color,
                        isPinned = pinned,
                        dateMillis = System.currentTimeMillis()
                    )
                )
            }) { Text("Kaydet", color = MaterialTheme.fit.accent, style = MaterialTheme.typography.titleMedium) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç", color = MaterialTheme.fit.muted) } }
    )
}
