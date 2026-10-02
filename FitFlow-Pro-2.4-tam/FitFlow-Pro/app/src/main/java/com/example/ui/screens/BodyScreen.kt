package com.example.ui.screens

import android.app.DatePickerDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.core.BodyCalc
import com.example.core.Calc
import com.example.core.kg
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
import com.example.ui.components.RoundIconButton
import com.example.ui.components.ThinProgress
import com.example.ui.theme.Palette
import com.example.ui.theme.fit
import com.example.ui.theme.mono
import com.example.ui.theme.parseHex
import kotlin.math.abs

/* ============================== Vücut ölçümleri ============================== */

@Composable
fun BodyScreen(vm: AppViewModel, nav: NavHostController) {
    val metrics by vm.bodyMetrics.collectAsStateWithLifecycle()
    val bodyWeight by vm.settings.weightKg.collectAsStateWithLifecycle()
    val heightCm by vm.settings.heightCm.collectAsStateWithLifecycle()
    val isMale by vm.settings.isMale.collectAsStateWithLifecycle()
    val targetWeight by vm.settings.targetWeightKg.collectAsStateWithLifecycle()
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
    // Güncel kilo: kilo içeren en yeni kayıt (en yeni kayıt sadece çevre ölçümü olabilir)
    val latestWeighed = sorted.lastOrNull { it.weightKg > 0f }
    val latestFatRec = sorted.lastOrNull { it.bodyFatPct > 0f }
    val first = sorted.firstOrNull { it.weightKg > 0f }
    val diff = if (latestWeighed != null && first != null && latestWeighed.id != first.id) latestWeighed.weightKg - first.weightKg else 0f

    val weightPoints = remember(metrics) { BodyCalc.weightPoints(metrics) }
    val weightTrend = remember(weightPoints) { BodyCalc.ewmaTrend(weightPoints) }
    val trendChange = remember(weightPoints, weightTrend) { BodyCalc.trendChange(weightPoints, weightTrend, 30) }

    val lastNeck = BodyCalc.latestOf(metrics) { it.neckCm }
    val lastWaist = BodyCalc.latestOf(metrics) { it.waistCm }
    val lastHip = BodyCalc.latestOf(metrics) { it.hipCm }
    val navyFat = BodyCalc.navyBodyFat(isMale, heightCm, lastNeck, lastWaist, lastHip)

    // Her kaydın bir önceki kilolu kayda göre farkı (liste satırlarında gösterilir)
    val weightDeltas = remember(sorted) {
        val out = HashMap<Long, Float>()
        var prev: Float? = null
        sorted.forEach { m ->
            if (m.weightKg > 0f) {
                prev?.let { out[m.id] = m.weightKg - it }
                prev = m.weightKg
            }
        }
        out
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Vücut ölçümleri", "${metrics.size} kayıt", onBack = { nav.popBackStack() }) {
            RoundIconButton(Icons.Default.Add, MaterialTheme.fit.accent, 40.dp) { showAdd = true }
        }

        if (metrics.isEmpty()) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                com.example.ui.components.ProgressPhotosCard(bodyWeight)
            }
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
                    BodySummaryCard(
                        latestWeighed = latestWeighed,
                        first = first,
                        diff = diff,
                        trendChange = trendChange,
                        targetWeight = targetWeight,
                        latestFatRec = latestFatRec,
                        navyFat = navyFat,
                        heightCm = heightCm,
                        fallbackWeight = bodyWeight,
                        isMale = isMale,
                        lastNeck = lastNeck,
                        lastWaist = lastWaist,
                        lastHip = lastHip
                    )
                }

                item {
                    FitCard {
                        OverlineText("Değişim grafiği")
                        Spacer(Modifier.height(10.dp))
                        TkSegmented(listOf("Kilo", "Yağ %", "Bel", "Göğüs", "Kol"), metric) { metric = it }
                        Spacer(Modifier.height(14.dp))
                        if (metric == 0 && weightPoints.size >= 2) {
                            WeightTrendChart(weightPoints, weightTrend, height = 180.dp)
                        } else if (metric != 0 && filtered.size >= 2) {
                            LineChart(
                                filtered.map { it.first },
                                filtered.map { it.second },
                                suffix = if (metric == 1) " %" else if (metric == 0) " kg" else " cm",
                                height = 180.dp
                            )
                        } else {
                            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    "Grafik için en az iki ölçüm gerekli.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.fit.muted
                                )
                            }
                        }
                    }
                }

                item {
                    Column {
                        com.example.ui.components.ProgressPhotosCard(latestWeighed?.weightKg ?: bodyWeight)
                    }
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        OverlineText("Kayıtlar", modifier = Modifier.weight(1f))
                        Text("${metrics.size}", style = MaterialTheme.typography.labelMedium.mono(), color = MaterialTheme.fit.muted)
                    }
                }

                items(metrics, key = { it.id }) { m ->
                    BodyMetricRow(
                        m = m,
                        delta = weightDeltas[m.id],
                        targetWeight = targetWeight,
                        onClick = { editing = m },
                        onDelete = { toDelete = m }
                    )
                }
            }
        }
    }

    if (showAdd || editing != null) {
        BodyMetricDialog(
            initial = editing,
            heightCm = heightCm,
            isMale = isMale,
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

/** Kilo değişiminin renk anlamı: hedef kilo varsa hedefe yaklaşmak yeşil, uzaklaşmak turuncu; yoksa nötr. */
@Composable
private fun towardGoalColor(delta: Float, current: Float, target: Float): Color {
    if (abs(delta) < 0.05f || target <= 0f || current <= 0f) return MaterialTheme.fit.muted
    val needed = target - current
    if (abs(needed) < 0.05f) return MaterialTheme.fit.muted
    return if ((delta < 0f) == (needed < 0f)) MaterialTheme.fit.success else MaterialTheme.fit.warning
}

@Composable
private fun BodySummaryCard(
    latestWeighed: BodyMetricEntity?,
    first: BodyMetricEntity?,
    diff: Float,
    trendChange: BodyCalc.TrendChange?,
    targetWeight: Float,
    latestFatRec: BodyMetricEntity?,
    navyFat: Float?,
    heightCm: Float,
    fallbackWeight: Float,
    isMale: Boolean,
    lastNeck: Float,
    lastWaist: Float,
    lastHip: Float
) {
    val current = latestWeighed?.weightKg ?: 0f
    FitCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OverlineText("Güncel kilo", modifier = Modifier.weight(1f))
            if (latestWeighed != null) {
                Text(formatDate(latestWeighed.dateMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                TkHero(if (current > 0f) current.trimNum() else "—", if (current > 0f) "kg" else null, MaterialTheme.fit.accent)
            }
            if (trendChange != null) {
                val c = towardGoalColor(trendChange.deltaKg, current, targetWeight)
                TkChip("${signedKg(trendChange.deltaKg)} / ${trendChange.spanDays} gün", c, Modifier.padding(bottom = 6.dp))
            }
        }
        if (abs(diff) >= 0.05f && first != null) {
            Text(
                "İlk kayıttan (${formatDateShort(first.dateMillis)}) bu yana ${signedKg(diff)}",
                style = MaterialTheme.typography.labelSmall,
                color = towardGoalColor(diff, current, targetWeight)
            )
        }

        if (targetWeight > 0f && current > 0f) {
            val start = first?.weightKg ?: current
            val totalNeed = start - targetWeight
            val done = start - current
            val progress = if (abs(totalNeed) < 0.05f) 1f else (done / totalNeed).coerceIn(0f, 1f)
            val left = abs(targetWeight - current)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hedef ${targetWeight.kg()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.muted, modifier = Modifier.weight(1f))
                Text(
                    if (left < 0.05f) "ulaşıldı" else "${left.trimNum()} kg kaldı",
                    style = MaterialTheme.typography.labelMedium.mono(),
                    color = if (left < 0.05f) MaterialTheme.fit.success else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(6.dp))
            ThinProgress(progress, height = 6.dp, color = MaterialTheme.fit.success)
        }

        Spacer(Modifier.height(14.dp))
        val bmiWeight = if (current > 0f) current else fallbackWeight
        val bmi = Calc.bmi(bmiWeight, heightCm)
        val bmiColor = when {
            bmi <= 0f -> null
            bmi < 18.5f -> Palette.info
            bmi < 25f -> MaterialTheme.fit.success
            bmi < 30f -> MaterialTheme.fit.warning
            else -> MaterialTheme.fit.danger
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TkStat(
                "Yağ oranı",
                latestFatRec?.let { "%${it.bodyFatPct.trimNum()}" } ?: "—",
                Modifier.weight(1f),
                caption = latestFatRec?.let { formatDateShort(it.dateMillis) } ?: "kayıt yok"
            )
            TkStat(
                "Navy tahmini",
                navyFat?.let { "%${it.trimNum()}" } ?: "—",
                Modifier.weight(1f),
                color = if (navyFat != null) MaterialTheme.fit.accent else null,
                caption = if (navyFat != null) "ölçümlerden" else "ölçüm eksik"
            )
            TkStat(
                "VKİ",
                if (bmi > 0f) bmi.trimNum() else "—",
                Modifier.weight(1f),
                color = bmiColor,
                caption = if (bmi > 0f) Calc.bmiCategory(bmi) else "boy gerekli"
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (navyFat != null) {
                "Navy: boy ${heightCm.trimNum()} · boyun ${lastNeck.trimNum()} · bel ${lastWaist.trimNum()}" +
                    (if (!isMale) " · kalça ${lastHip.trimNum()}" else "") + " cm ölçümlerinden"
            } else {
                if (isMale) "Navy yağ oranı tahmini için boyun ve bel ölçümü ekle."
                else "Navy yağ oranı tahmini için boyun, bel ve kalça ölçümü ekle."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.fit.muted
        )
    }
}

@Composable
private fun BodyMetricRow(
    m: BodyMetricEntity,
    delta: Float?,
    targetWeight: Float,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    FitCard(onClick = onClick, contentPadding = PaddingValues(start = 12.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TkDateBlock(m.dateMillis)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (m.weightKg > 0f) {
                        Text(
                            m.weightKg.kg(),
                            style = MaterialTheme.typography.titleMedium.mono().copy(fontWeight = FontWeight.SemiBold)
                        )
                        if (delta != null && abs(delta) >= 0.05f) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                signedKg(delta),
                                style = MaterialTheme.typography.labelMedium.mono(),
                                color = towardGoalColor(delta, m.weightKg, targetWeight)
                            )
                        }
                    } else {
                        Text("Çevre ölçümü", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.fit.muted)
                    }
                }
                Text(
                    formatDate(m.dateMillis) + if (m.note.isNotBlank()) " · ${m.note}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.fit.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            RoundIconButton(Icons.Default.Delete, MaterialTheme.fit.danger, 34.dp, Color.Transparent) { onDelete() }
        }
        val hasBadges = m.bodyFatPct > 0f || m.chestCm > 0f || m.waistCm > 0f || m.hipCm > 0f ||
            m.neckCm > 0f || m.armCm > 0f || m.thighCm > 0f
        if (hasBadges) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.padding(start = 56.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (m.bodyFatPct > 0f) Badge("Yağ %${m.bodyFatPct.trimNum()}", MaterialTheme.fit.warning)
                if (m.chestCm > 0f) Badge("Göğüs ${m.chestCm.trimNum()}", MaterialTheme.fit.muted)
                if (m.waistCm > 0f) Badge("Bel ${m.waistCm.trimNum()}", MaterialTheme.fit.muted)
                if (m.hipCm > 0f) Badge("Kalça ${m.hipCm.trimNum()}", MaterialTheme.fit.muted)
                if (m.neckCm > 0f) Badge("Boyun ${m.neckCm.trimNum()}", MaterialTheme.fit.muted)
                if (m.armCm > 0f) Badge("Kol ${m.armCm.trimNum()}", MaterialTheme.fit.muted)
                if (m.thighCm > 0f) Badge("Bacak ${m.thighCm.trimNum()}", MaterialTheme.fit.muted)
            }
        }
    }
}

@Composable
private fun BodyMetricDialog(
    initial: BodyMetricEntity?,
    heightCm: Float,
    isMale: Boolean,
    onSave: (BodyMetricEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var dateMillis by remember { mutableStateOf(initial?.dateMillis ?: System.currentTimeMillis()) }
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
    val navyEstimate = BodyCalc.navyBodyFat(isMale, heightCm, p(neck), p(waist), p(hip))

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(if (initial == null) "Yeni ölçüm" else "Ölçümü düzenle", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.fit.elevated)
                        .clickable { pickDate(context, dateMillis) { dateMillis = it } }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tarih", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.muted, modifier = Modifier.weight(1f))
                    Text(formatDate(dateMillis), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.fit.accent)
                }
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
                if (navyEstimate != null) {
                    if (fat.isBlank()) {
                        Text(
                            "Navy tahmini: %${navyEstimate.trimNum()} — yağ alanına yaz",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.fit.accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { fat = navyEstimate.trimNum() }
                                .padding(vertical = 4.dp)
                        )
                    } else {
                        Text(
                            "Navy tahmini: %${navyEstimate.trimNum()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.fit.muted
                        )
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
                        thighCm = p(thigh), neckCm = p(neck), note = note.trim(),
                        dateMillis = dateMillis
                    )
                )
            }) { Text("Kaydet", color = MaterialTheme.fit.accent, style = MaterialTheme.typography.titleMedium) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç", color = MaterialTheme.fit.muted) } }
    )
}

/** Sadece tarih seçer; saat önceki değerden korunur. Gelecek tarih seçilemez. */
private fun pickDate(context: android.content.Context, initialMillis: Long, onSelected: (Long) -> Unit) {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = initialMillis }
    val dpd = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            cal.set(java.util.Calendar.YEAR, year)
            cal.set(java.util.Calendar.MONTH, month)
            cal.set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)
            onSelected(minOf(cal.timeInMillis, System.currentTimeMillis()))
        },
        cal.get(java.util.Calendar.YEAR),
        cal.get(java.util.Calendar.MONTH),
        cal.get(java.util.Calendar.DAY_OF_MONTH)
    )
    dpd.datePicker.maxDate = System.currentTimeMillis()
    dpd.setTitle("Ölçüm tarihi")
    dpd.show()
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
