package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.ui.theme.mono
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.BarChart as BarChartIcon
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.core.Calc
import com.example.core.LiftStandard
import com.example.core.LoadStatus
import com.example.core.MuscleContributor
import com.example.core.MuscleLoad
import com.example.core.MuscleMap
import com.example.core.Muscles
import com.example.core.ProgressAnalytics
import com.example.core.RepRangeSlice
import com.example.core.formatDate
import com.example.core.formatDateShort
import com.example.core.formatDurationShort
import com.example.core.formatTonnage
import com.example.core.kg
import com.example.core.label
import com.example.core.startOfMonth
import com.example.core.startOfWeek
import com.example.core.trimNum
import com.example.data.PrEntity
import com.example.ui.AppViewModel
import com.example.ui.Routes
import com.example.ui.components.AccentButton
import com.example.ui.components.ActivityHeatmap
import com.example.ui.components.BarChart
import com.example.ui.components.Badge
import com.example.ui.components.BodyMuscleMapPair
import com.example.ui.components.ChoiceChip
import com.example.ui.components.DistributionBars
import com.example.ui.components.DistributionItem
import com.example.ui.components.EmptyState
import com.example.ui.components.FitCard
import com.example.ui.components.LineChart
import com.example.ui.components.MuscleChipRow
import com.example.ui.components.OverlineText
import com.example.ui.components.PillTabs
import com.example.ui.components.ProgressRing
import com.example.ui.components.RoundIconButton
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatTile
import com.example.ui.components.ThinProgress
import com.example.ui.components.WeakLinkRecommendationDialog
import com.example.ui.theme.Palette
import com.example.ui.theme.fit
import kotlin.math.abs
import kotlin.math.roundToInt

/* ==========================================================================
 * İlerleme ekranı — dört sekme
 *   Genel   : haftalık karşılaştırma, yüklenme dengesi, trend, tutarlılık
 *   Kaslar  : anatomik kas haritası, kas bazlı hacim ve toparlanma
 *   Güç     : ana hareketlerde seviye, lift dengesi, 1RM gelişimi
 *   Rekorlar: kişisel rekor geçmişi
 * ========================================================================== */

@Composable
fun StatsScreen(vm: AppViewModel, nav: NavHostController) {
    val stats by vm.dashboard.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val tab by vm.statsTab.collectAsStateWithLifecycle()
    val onBackAction: () -> Unit = {
        val popped = nav.popBackStack()
        if (!popped) {
            nav.navigate(Routes.HOME) {
                popUpTo(Routes.HOME) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    if (workouts.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("İlerleme", onBack = onBackAction)
            EmptyState(
                Icons.Default.ShowChart,
                "Henüz veri yok",
                "İlk antrenmanını tamamladığında hacim, kas dengesi ve güç analizlerin burada oluşur."
            )
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "İlerleme",
            subtitle = "${stats.totalWorkouts} seans · ${formatTonnage(stats.totalVolume)}",
            onBack = onBackAction
        ) {
            RoundIconButton(Icons.Default.History, MaterialTheme.fit.muted, 40.dp, MaterialTheme.fit.elevated) {
                nav.navigate(Routes.HISTORY)
            }
        }
        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            // 3 sekme: Özet · Kaslar · Güç. Rekorlar Güç sekmesinin alt sayfası (tab = 3).
            PillTabs(listOf("Özet", "Kaslar", "Güç"), if (tab == 3) 2 else tab) { vm.setStatsTab(it) }
        }
        Spacer(Modifier.height(8.dp))
        when (tab) {
            0 -> OverviewTab(vm, nav)
            1 -> MusclesTab(vm, nav)
            2 -> StrengthTab(vm, nav)
            else -> Column {
                Text(
                    "← Güç",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.fit.accent,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp).clickable { vm.setStatsTab(2) }
                )
                RecordsTab(vm, nav)
            }
        }
    }
}

/* ================================ 1. GENEL ================================= */

@Composable
private fun OverviewTab(vm: AppViewModel, nav: NavHostController) {
    val stats by vm.dashboard.collectAsStateWithLifecycle()
    val weekly by vm.weeklySeries.collectAsStateWithLifecycle()
    val adherence by vm.adherence.collectAsStateWithLifecycle()
    val stagnant by vm.stagnantLifts.collectAsStateWithLifecycle()
    val improving by vm.improvingLifts.collectAsStateWithLifecycle()
    val rpe by vm.weeklyRpe.collectAsStateWithLifecycle()
    val monthly by vm.monthlySeries.collectAsStateWithLifecycle()
    val load by vm.trainingLoad.collectAsStateWithLifecycle()
    val allSets by vm.allSets.collectAsStateWithLifecycle()
    var metric by rememberSaveable { mutableIntStateOf(0) }
    var period by rememberSaveable { mutableIntStateOf(0) }   // 0 = haftalık, 1 = aylık
    // Tekrar dağılımı: son 8 hafta (yoksa tüm zamanlar)
    val repSlices = remember(allSets) {
        val from = System.currentTimeMillis() - 56L * 86_400_000L
        ProgressAnalytics.repRangeDistribution(allSets.filter { it.performedAt >= from })
            .ifEmpty { ProgressAnalytics.repRangeDistribution(allSets) }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        /* Bu hafta — geçen haftaya göre */
        item {
            FitCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Bu hafta", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("geçen haftaya göre", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.muted)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiCell("Antrenman", "${stats.thisWeekWorkouts}", deltaInt(stats.thisWeekWorkouts, stats.lastWeekWorkouts), Modifier.weight(1f))
                    KpiCell("Hacim", formatTonnage(stats.thisWeekVolume), ProgressAnalytics.deltaText(stats.thisWeekVolume, stats.lastWeekVolume)
                        .takeIf { stats.lastWeekVolume > 0f }, Modifier.weight(1f), positive = stats.thisWeekVolume >= stats.lastWeekVolume)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiCell("Set", "${stats.thisWeekSets}", null, Modifier.weight(1f))
                    val lastRpe = rpe.lastOrNull()?.second
                    KpiCell("Ort. RPE", lastRpe?.trimNum() ?: "—", "hedef 7–8", Modifier.weight(1f), positive = null)
                }
            }
        }

        /* Trend: haftalık / aylık × hacim, set, seans, tekrar, süre */
        val series = if (period == 0) weekly.takeLast(8) else monthly.takeLast(6)
        if (series.isNotEmpty()) {
            item {
                val asLine by vm.settings.trendAsLine.collectAsStateWithLifecycle()
                TrendCard(
                    series = series,
                    period = period,
                    metric = metric,
                    asLine = asLine,
                    onPeriod = { period = it },
                    onMetric = { metric = it },
                    onChartStyle = { vm.settings.setTrendAsLine(it) }
                )
            }
        }

        /* Yüklenme dengesi (akut : kronik) */
        item { TrainingLoadCard(load) }

        /* Tekrar dağılımı */
        if (repSlices.isNotEmpty()) {
            item { RepRangeCard(repSlices) }
        }

        /* Zorlanma (RPE) */
        item { RpeCard(rpe) }

        /* Tutarlılık */
        if (adherence.isNotEmpty()) {
            item {
                FitCard {
                    val pct = ProgressAnalytics.adherencePct(adherence)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Tutarlılık", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(
                            "${adherence.sumOf { it.workouts }} antrenman · hedefe ulaşılan hafta %$pct",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.fit.muted
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        adherence.forEach { w ->
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                val slots = maxOf(w.goal, w.workouts, 1)
                                (0 until slots).forEach { i ->
                                    Box(
                                        Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(3.dp))
                                            .background(if (i < w.workouts) MaterialTheme.fit.success else MaterialTheme.fit.elevated)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        /* Hareketler: ilerleyenler ve takılanlar */
        if (improving.isNotEmpty() || stagnant.isNotEmpty()) {
            item {
                FitCard {
                    Text("Hareketler", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    improving.take(4).forEach { lift ->
                        LiftTrendRow("İLERLİYOR", MaterialTheme.fit.success, lift.name, "1RM ${lift.bestE1rm.kg()}") {
                            nav.navigate("${Routes.EXERCISE}/${lift.exerciseId}")
                        }
                    }
                    stagnant.take(4).forEach { lift ->
                        LiftTrendRow("PLATO", MaterialTheme.fit.warning, lift.name, "${lift.sessionsSinceBest} seanstır rekor yok") {
                            nav.navigate("${Routes.EXERCISE}/${lift.exerciseId}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCell(label: String, value: String, delta: String?, modifier: Modifier = Modifier, positive: Boolean? = true) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.fit.elevated).padding(12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.muted)
        Text(value, style = MaterialTheme.typography.titleLarge.mono().copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
        if (delta != null) {
            Text(
                delta,
                style = MaterialTheme.typography.labelMedium,
                color = when (positive) {
                    null -> MaterialTheme.fit.muted
                    true -> MaterialTheme.fit.success
                    false -> MaterialTheme.fit.warning
                }
            )
        }
    }
}

private fun deltaInt(now: Int, before: Int): String? =
    if (before <= 0 && now <= 0) null else (if (now >= before) "▲ " else "▼ ") + kotlin.math.abs(now - before)

@Composable
private fun LiftTrendRow(tag: String, color: Color, name: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Badge(tag, color)
        Spacer(Modifier.width(10.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.muted, maxLines = 1)
    }
}

/* ----------------------------- Özet: yeni kartlar ---------------------------- */

private val TREND_METRICS = listOf("Hacim", "Set", "Seans", "Tekrar", "Süre")

private fun trendValue(p: com.example.core.PeriodPoint, metric: Int): Float = when (metric) {
    0 -> p.volume
    1 -> p.sets.toFloat()
    2 -> p.workouts.toFloat()
    3 -> p.reps.toFloat()
    else -> p.durationSec / 60f
}

private fun trendFormat(v: Float, metric: Int): String = when (metric) {
    0 -> formatTonnage(v)
    1 -> "${v.roundToInt()} set"
    2 -> "${v.roundToInt()} seans"
    3 -> "${v.roundToInt()} tekrar"
    else -> "${v.roundToInt()} dk"
}

/** Haftalık / aylık trend: seçilen metrik, önceki döneme göre değişim, ortalama ve zirve. */
@Composable
private fun TrendCard(
    series: List<com.example.core.PeriodPoint>,
    period: Int,
    metric: Int,
    asLine: Boolean,
    onPeriod: (Int) -> Unit,
    onMetric: (Int) -> Unit,
    onChartStyle: (Boolean) -> Unit
) {
    val values = series.map { trendValue(it, metric) }
    val current = values.lastOrNull() ?: 0f
    val previous = values.getOrNull(values.lastIndex - 1)
    val nonZero = values.filter { it > 0f }
    val avg = if (nonZero.isEmpty()) 0f else nonZero.average().toFloat()
    val peak = values.maxOrNull() ?: 0f
    val deltaPct = if (previous != null && previous > 0f) Math.round((current - previous) / previous * 100f) else null
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Trend", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            ChartStyleToggle(asLine, onChartStyle)
            Spacer(Modifier.width(8.dp))
            SegmentedToggle(listOf("Haftalık", "Aylık"), period, onPeriod)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TREND_METRICS.forEachIndexed { i, label ->
                com.example.ui.components.ChoiceChip(label, metric == i, { onMetric(i) })
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (period == 0) "Bu hafta" else "Bu ay",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.fit.muted
                )
                Text(
                    trendFormat(current, metric),
                    style = MaterialTheme.typography.headlineMedium.mono().copy(fontWeight = FontWeight.SemiBold)
                )
            }
            if (deltaPct != null) {
                Text(
                    (if (deltaPct >= 0) "+" else "−") + "%" + kotlin.math.abs(deltaPct) +
                        if (period == 0) " geçen hafta" else " geçen ay",
                    style = MaterialTheme.typography.labelLarge.mono(),
                    color = if (deltaPct >= 0) MaterialTheme.fit.success else MaterialTheme.fit.warning,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (asLine) {
            LineChart(
                values = values,
                labels = series.map { it.label },
                format = { trendFormat(it, metric) },
                height = 150.dp
            )
        } else {
            BarChart(
                values = values,
                labels = series.map { it.label },
                format = { trendFormat(it, metric) },
                showAverage = true,
                height = 150.dp
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrendStat("Ortalama", trendFormat(avg, metric), Modifier.weight(1f))
            TrendStat("Zirve", trendFormat(peak, metric), Modifier.weight(1f))
            TrendStat("Toplam", trendFormat(values.sum(), metric), Modifier.weight(1f))
        }
    }
}

/** Sütun / çizgi grafik seçimi: iki küçük ikon düğmesi. */
@Composable
private fun ChartStyleToggle(asLine: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(2.dp)
    ) {
        listOf(false to androidx.compose.material.icons.Icons.Default.BarChartIcon,
               true to androidx.compose.material.icons.Icons.Default.ShowChart).forEach { (line, icon) ->
            val on = asLine == line
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) MaterialTheme.fit.elevated else Color.Transparent)
                    .clickable { onChange(line) }
                    .padding(horizontal = 7.dp, vertical = 5.dp)
            ) {
                androidx.compose.material3.Icon(
                    icon,
                    contentDescription = if (line) "Çizgi grafik" else "Sütun grafik",
                    tint = if (on) MaterialTheme.fit.accent else MaterialTheme.fit.muted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun TrendStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.fit.elevated).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
        Text(value, style = MaterialTheme.typography.labelLarge.mono().copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

/** Küçük iki/üç seçenekli anahtar (tasarımdaki Ön/Arka anahtarıyla aynı görünüm). */
@Composable
private fun SegmentedToggle(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(3.dp)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.fit.muted,
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) MaterialTheme.fit.elevated else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
    }
}

/** Akut (son 7 gün) : kronik (4 haftalık ortalama) yük oranı, bölgeli gösterge ile. */
@Composable
private fun TrainingLoadCard(load: com.example.core.TrainingLoad) {
    val low = com.example.ui.components.MuscleColors.below
    val ok = MaterialTheme.fit.success
    val warn = MaterialTheme.fit.warning
    val bad = MaterialTheme.fit.danger
    val statusColor = when {
        load.chronicWeekly <= 0f -> MaterialTheme.fit.muted
        load.ratio < 0.75f -> low
        load.ratio <= 1.3f -> ok
        load.ratio <= 1.5f -> warn
        else -> bad
    }
    val track = MaterialTheme.fit.elevated
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Yüklenme dengesi", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Text(
                load.status,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = statusColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusColor.copy(alpha = 0.13f))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (load.chronicWeekly > 0f) load.ratio.trimNum().replace('.', ',') else "—",
                style = MaterialTheme.typography.displaySmall.mono().copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "son 7 gün ÷ 4 haftalık ortalama",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.fit.muted,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        // Bölgeli gösterge: 0 – 2.0
        val maxR = 2f
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val barH = 10.dp.toPx()
            val top = (size.height - barH) / 2f
            fun x(r: Float) = size.width * (r / maxR).coerceIn(0f, 1f)
            val r = androidx.compose.ui.geometry.CornerRadius(barH / 2f, barH / 2f)
            drawRoundRect(track, topLeft = androidx.compose.ui.geometry.Offset(0f, top), size = androidx.compose.ui.geometry.Size(size.width, barH), cornerRadius = r)
            listOf(0f to 0.75f to low, 0.75f to 1.3f to ok, 1.3f to 1.5f to warn, 1.5f to maxR to bad).forEach { (range, c) ->
                val (a, b) = range
                drawRect(
                    c.copy(alpha = 0.35f),
                    topLeft = androidx.compose.ui.geometry.Offset(x(a), top),
                    size = androidx.compose.ui.geometry.Size(x(b) - x(a), barH)
                )
            }
            if (load.chronicWeekly > 0f) {
                val mx = x(load.ratio).coerceIn(2.dp.toPx(), size.width - 2.dp.toPx())
                drawRoundRect(
                    statusColor,
                    topLeft = androidx.compose.ui.geometry.Offset(mx - 2.dp.toPx(), 0f),
                    size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            listOf("Düşük", "Dengeli", "Hızlı", "Aşırı").forEachIndexed { i, t ->
                Text(
                    t,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.fit.muted,
                    textAlign = if (i == 0) TextAlign.Start else if (i == 3) TextAlign.End else TextAlign.Center,
                    modifier = Modifier.weight(if (i == 1) 1.4f else 1f)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrendStat("Son 7 gün", formatTonnage(load.acuteVolume), Modifier.weight(1f))
            TrendStat("Haftalık ort.", formatTonnage(load.chronicWeekly), Modifier.weight(1f))
            TrendStat("Set (7 gün)", "${load.acuteSets}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Text(load.advice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f))
    }
}

/** Tekrar aralığı dağılımı: yatay çubuklar, her aralığın amacıyla. */
@Composable
private fun RepRangeCard(slices: List<RepRangeSlice>) {
    val maxShare = (slices.maxOfOrNull { it.share } ?: 1f).coerceAtLeast(0.01f)
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tekrar dağılımı", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Text("son 8 hafta · ${slices.sumOf { it.sets }} set", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.muted)
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            slices.forEach { sl ->
                val c = repRangeColor(sl.label)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(sl.label, style = MaterialTheme.typography.labelLarge.mono().copy(fontWeight = FontWeight.SemiBold), color = c, modifier = Modifier.width(52.dp))
                        Text(sl.purpose, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(
                            "%${(sl.share * 100).roundToInt()}",
                            style = MaterialTheme.typography.labelLarge.mono().copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(" · ${sl.sets}", style = MaterialTheme.typography.labelMedium.mono(), color = MaterialTheme.fit.muted)
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.fit.elevated)) {
                        Box(
                            Modifier
                                .fillMaxWidth((sl.share / maxShare).coerceIn(0f, 1f))
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(c)
                        )
                    }
                }
            }
        }
    }
}

/** Haftalık ortalama RPE: 5–10 ölçeği, 7–8 hedef bandı. */
@Composable
private fun RpeCard(rpe: List<Pair<String, Float>>) {
    val line = MaterialTheme.fit.accent
    val band = MaterialTheme.fit.success
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val bg = MaterialTheme.colorScheme.surface
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Zorlanma (RPE)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Text("haftalık ortalama", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.muted)
        }
        if (rpe.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Setlere RPE girdikçe zorlanma eğilimin burada çizilecek. Seans sırasında RPE hücresine dokunman yeterli.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.fit.muted
            )
            return@FitCard
        }
        val last = rpe.last().second
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(last.trimNum().replace('.', ','), style = MaterialTheme.typography.headlineMedium.mono().copy(fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    last < 7f -> "hedefin altında — biraz daha zorlanabilirsin"
                    last <= 8.5f -> "hedef bandında"
                    else -> "yüksek — toparlanmayı izle"
                },
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    last < 7f -> com.example.ui.components.MuscleColors.below
                    last <= 8.5f -> MaterialTheme.fit.success
                    else -> MaterialTheme.fit.warning
                },
                modifier = Modifier.padding(bottom = 5.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        val lo = 5f
        val hi = 10f
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            fun y(v: Float) = size.height * (1f - ((v - lo) / (hi - lo)).coerceIn(0f, 1f))
            // hedef bandı 7–8
            drawRect(
                band.copy(alpha = 0.12f),
                topLeft = androidx.compose.ui.geometry.Offset(0f, y(8f)),
                size = androidx.compose.ui.geometry.Size(size.width, y(7f) - y(8f))
            )
            listOf(6f, 7f, 8f, 9f).forEach { g ->
                drawLine(grid, androidx.compose.ui.geometry.Offset(0f, y(g)), androidx.compose.ui.geometry.Offset(size.width, y(g)), strokeWidth = 1f)
            }
            val n = rpe.size
            val step = if (n <= 1) 0f else size.width / (n - 1)
            val pts = rpe.mapIndexed { i, (_, v) ->
                androidx.compose.ui.geometry.Offset(if (n <= 1) size.width / 2f else step * i, y(v))
            }
            if (pts.size >= 2) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                }
                drawPath(path, line, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
            pts.forEach { p ->
                drawCircle(bg, radius = 4.5.dp.toPx(), center = p)
                drawCircle(line, radius = 4.5.dp.toPx(), center = p, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(rpe.first().first, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
            Text("yeşil bant: hedef 7–8", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
            Text(rpe.last().first, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
        }
    }
}

/* ================================ 2. KASLAR ================================ */

@Composable
private fun MusclesTab(vm: AppViewModel, nav: NavHostController) {
    val weekLoads by vm.weeklyMuscleLoads.collectAsStateWithLifecycle()
    val monthLoads by vm.monthlyMuscleLoads.collectAsStateWithLifecycle()
    val weekDetail by vm.weeklyDetailLoads.collectAsStateWithLifecycle()
    val monthDetail by vm.monthlyDetailLoads.collectAsStateWithLifecycle()
    val allSets by vm.allSets.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val allItems by vm.allItems.collectAsStateWithLifecycle()
    val routineDays by vm.routineDays.collectAsStateWithLifecycle()
    val activeRoutine by vm.activeRoutine.collectAsStateWithLifecycle()
    val allDays by vm.allDays.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()

    var scope by rememberSaveable { mutableIntStateOf(0) }   // 0 = bu hafta, 1 = 4 hafta ortalaması
    var selected by remember { mutableStateOf<String?>(null) }
    var showWeakLinkAdvisor by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()

    val underActivatedInfo = remember(activeRoutine, allDays, workouts, allSets, exercises) {
        if (activeRoutine == null) return@remember Pair(false, emptyList<String>())
        val activeDays = allDays.filter { it.routineId == activeRoutine!!.id }
        if (activeDays.isEmpty()) return@remember Pair(false, emptyList<String>())

        val now = System.currentTimeMillis()
        val weekStart = startOfWeek(now)

        val thisWeekWorkouts = workouts.filter { it.isFinished && it.startedAt >= weekStart }
        val completedDayIds = thisWeekWorkouts.mapNotNull { it.routineDayId }.toSet()
        val allActiveDayIds = activeDays.map { it.id }.toSet()

        val allProgramDaysCompleted = allActiveDayIds.isNotEmpty() && completedDayIds.containsAll(allActiveDayIds)
        if (!allProgramDaysCompleted) return@remember Pair(false, emptyList<String>())

        val mainGroups = listOf("Göğüs", "Sırt", "Bacak", "Omuz", "Kol", "Karın")
        val exMap = exercises.associateBy { it.id }
        val thisWeekWorkoutIds = thisWeekWorkouts.map { it.id }.toSet()
        val thisWeekSets = allSets.filter { it.workoutId in thisWeekWorkoutIds && it.isCompleted }
        val setsByWorkout = thisWeekSets.groupBy { it.workoutId }

        val activations = mutableMapOf<String, Int>()
        mainGroups.forEach { activations[it] = 0 }

        thisWeekWorkouts.forEach { w ->
            val wSets = setsByWorkout[w.id] ?: emptyList()
            val sessionGroups = mutableSetOf<String>()
            wSets.forEach { set ->
                val ex = exMap[set.exerciseId]
                if (ex != null) {
                    sessionGroups.add(ex.muscleGroup)
                    val resolved = MuscleMap.resolve(ex.name, ex.muscleGroup, ex.secondaryMuscles)
                    (resolved.primary + resolved.secondary).forEach { k ->
                        val parent = MuscleMap.parentGroup(k)
                        val trName = when (parent) {
                            Muscles.CHEST -> "Göğüs"
                            Muscles.BACK -> "Sırt"
                            Muscles.LEGS -> "Bacak"
                            Muscles.SHOULDERS -> "Omuz"
                            Muscles.ARMS -> "Kol"
                            Muscles.CORE -> "Karın"
                            else -> parent
                        }
                        sessionGroups.add(trName)
                    }
                }
            }
            sessionGroups.forEach { g ->
                if (activations.containsKey(g)) {
                    activations[g] = (activations[g] ?: 0) + 1
                }
            }
        }

        val under2 = mainGroups.filter { (activations[it] ?: 0) < 2 }
        Pair(under2.isNotEmpty(), under2)
    }

    val sinceMillis = remember(scope) {
        if (scope == 0) startOfWeek(System.currentTimeMillis()) else System.currentTimeMillis() - 28L * 86_400_000L
    }
    val periodWeeks = if (scope == 0) 1f else 4f

    if (showWeakLinkAdvisor) {
        WeakLinkRecommendationDialog(
            vm = vm,
            onDismiss = { showWeakLinkAdvisor = false }
        )
    }

    val loads = if (scope == 0) weekLoads else monthLoads
    val loadMap = loads.associateBy { it.key }

    val accent = MaterialTheme.fit.accent
    val success = MaterialTheme.fit.success
    val warning = MaterialTheme.fit.warning
    val danger = MaterialTheme.fit.danger

    val detail = if (scope == 0) weekDetail else monthDetail
    val detailMap = detail.associateBy { it.key }

    // Grup renkleri + detay bölgeler (üst / alt göğüs ayrı renklenir).
    val colors = remember(loads, detail) {
        (loads + detail).mapNotNull { l -> com.example.ui.components.MuscleColors.forStatus(l.status)?.let { l.key to it } }.toMap()
    }
    val recovery = remember(allSets, exercises) { vm.recoveryNow() }

    val weakLinks = loads.filter { it.status == LoadStatus.LOW || it.status == LoadStatus.BELOW }
        .sortedBy { it.effectiveSets }
    val overloaded = loads.filter { it.status == LoadStatus.EXCESSIVE }

    // Listeden bir kas seçilince haritaya ve detay kartına kaydır.
    fun selectFromList(key: String) {
        selected = key
        scrollScope.launch { listState.animateScrollToItem(if (underActivatedInfo.first) 2 else 1) }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PillTabs(listOf("Bu hafta", "4 hafta ortalaması"), scope) { scope = it; selected = null }
        }

        if (underActivatedInfo.first) {
            item {
                FitCard(
                    container = warning.copy(alpha = 0.12f),
                    border = warning.copy(alpha = 0.5f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = warning,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Haftalık Program Tamamlandı — Kas Uyarısı",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Bu haftaki tüm program günlerin tamamlandı! Ancak şu kas grupları bu hafta boyunca en az 2 kez aktive edilmedi: ${underActivatedInfo.second.joinToString(", ")}. Optimal hipertrofi ve dengeli gelişim için her ana kas grubunun haftada en az 2 kez çalıştırılması önerilir.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.fit.muted
                            )
                        }
                    }
                }
            }
        }

        /* ------------------------------ Kas haritası ----------------------------- */
        item {
            FitCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (scope == 0) "Bu hafta · etkin set" else "4 hafta · haftalık ort.",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(14.dp))
                BodyMuscleMapPair(
                    colors = colors,
                    height = 300.dp,
                    selected = selected,
                    onMuscleTap = { selected = if (selected == it) null else it }
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    com.example.ui.components.MuscleColors.loadLegend.forEach { (c, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(c))
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.muted)
                        }
                    }
                }
            }
        }

        /* --------------------------- Seçilen kas detayı -------------------------- */
        selected?.let { key ->
            val load = loadMap[key]
            if (load != null) {
                item {
                    val sc = statusColor(load.status)
                    FitCard(border = sc.copy(alpha = 0.33f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                MuscleMap.label(key),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                load.status.label(),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = sc,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(sc.copy(alpha = 0.13f))
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            RoundIconButton(Icons.Default.Close, MaterialTheme.fit.muted, 32.dp, MaterialTheme.fit.elevated) {
                                selected = null
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                load.effectiveSets.trimNum().replace('.', ','),
                                style = MaterialTheme.typography.displaySmall.mono().copy(fontWeight = FontWeight.SemiBold)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "etkin set · hedef ${load.target.first}–${load.target.last}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.fit.muted,
                                modifier = Modifier.padding(bottom = 5.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        TargetRangeBar(load.effectiveSets, load.target.first, load.target.last, sc)
                        Spacer(Modifier.height(14.dp))
                        Text(muscleAdvice(load), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Son çalışma: " + if (load.daysSince < 0) "—" else daysLabel(load.daysSince),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF6B7483)
                        )
                        // Göğüs: üst / alt ayrımı
                        if (key == MuscleMap.CHEST) {
                            val up = detailMap[MuscleMap.CHEST_UPPER]
                            val low = detailMap[MuscleMap.CHEST_LOWER]
                            if (up != null && low != null) {
                                Spacer(Modifier.height(10.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    MiniStat("Üst göğüs", "${up.effectiveSets.trimNum()} · ${up.status.label()}", Modifier.weight(1f))
                                    MiniStat("Alt göğüs", "${low.effectiveSets.trimNum()} · ${low.status.label()}", Modifier.weight(1f))
                                }
                            }
                        }
                        // Toparlanma durumu
                        recovery[key]?.let { r ->
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Toparlanma: %${(r.readiness * 100).toInt()} · ${r.state.label()}" +
                                    if (r.readyAt > 0L) " · hazır: ${com.example.core.formatWeekday(r.readyAt)} ${com.example.core.formatTime(r.readyAt)}" else "",
                                style = MaterialTheme.typography.labelLarge,
                                color = com.example.ui.components.MuscleColors.forRecovery(r.state)
                            )
                        }

                        val contributors = remember(key, allSets, exercises, allItems, routineDays, sinceMillis, periodWeeks) {
                            ProgressAnalytics.muscleContributors(
                                muscleKey = key,
                                sets = allSets,
                                exercises = exercises,
                                routineItems = allItems,
                                routineDays = routineDays,
                                sinceMillis = sinceMillis,
                                periodWeeks = periodWeeks
                            )
                        }
                        val progSets by vm.progressSets.collectAsStateWithLifecycle()
                        val strength = remember(key, progSets, exercises) { com.example.core.StrengthInsights.muscleStrength(key, progSets, exercises) }
                        strength?.let { st ->
                            Spacer(Modifier.height(12.dp))
                            MuscleStrengthRow(st) { nav.navigate("${Routes.EXERCISE}/${st.exerciseId}") }
                        }
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(12.dp))
                        MuscleContributorsSection(key = key, contributors = contributors)
                        if (load.status == LoadStatus.LOW || load.status == LoadStatus.BELOW) {
                            Spacer(Modifier.height(12.dp))
                            AccentButton(
                                text = "Akıllı öneriler",
                                onClick = { showWeakLinkAdvisor = true },
                                icon = Icons.Default.AutoAwesome,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        if (overloaded.isNotEmpty()) {
            item {
                FitCard(
                    container = MaterialTheme.fit.danger.copy(alpha = 0.07f),
                    border = MaterialTheme.fit.danger.copy(alpha = 0.3f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.fit.danger, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Aşırı yüklenen kaslar", style = MaterialTheme.typography.titleSmall)
                            Text(
                                overloaded.joinToString(", ") { MuscleMap.label(it.key) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.fit.muted
                            )
                        }
                    }
                }
            }
        }

        /* --------------------------- Program uyumu (2.28) --------------------------- */
        item(key = "adherence") {
            val adh = remember(routineDays, allItems, workouts, allSets, exercises) {
                com.example.core.StrengthInsights.adherence(routineDays, allItems, workouts, allSets, exercises)
            }
            if (adh != null) AdherenceCard(adh)
        }

        /* ---------------------------- Tüm kas dökümü ---------------------------- */
        item {
            val ranked = loads.filter { it.effectiveSets > 0f || it.target.last > 0 }
                .sortedBy { if (it.target.first <= 0) 99f else it.effectiveSets / it.target.first }
            val maxSets = (ranked.maxOfOrNull { maxOf(it.effectiveSets, it.target.last.toFloat()) } ?: 1f).coerceAtLeast(1f)
            FitCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                OverlineText("En eksikten", modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                ranked.forEach { load ->
                    val c = statusColor(load.status)
                    val isSel = selected == load.key
                    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isSel) {
                                    selected = null
                                } else {
                                    selectFromList(load.key)
                                }
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(c))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            MuscleMap.label(load.key),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSel) MaterialTheme.fit.accent else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(96.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.fit.elevated)) {
                            Box(
                                Modifier
                                    .fillMaxWidth((load.effectiveSets / maxSets).coerceIn(0f, 1f))
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(c)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            load.effectiveSets.trimNum().replace('.', ','),
                            style = MaterialTheme.typography.labelLarge.mono(),
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(38.dp)
                        )
                    }
                }
            }
        }

        /* ------------------------ Kas × hafta ısı haritası (2.28) ------------------------ */
        item(key = "heatmap") {
            val grid = remember(allSets, exercises) { com.example.core.StrengthInsights.muscleWeekGrid(allSets, exercises) }
            if (grid.rows.values.any { r -> r.any { it > 0f } }) MuscleHeatmapCard(grid)
        }
    }
}

/** Etkin set göstergesi: yeşil bölge önerilen aralık, dikey çizgi mevcut değer. */
@Composable
private fun TargetRangeBar(value: Float, lo: Int, hi: Int, color: Color) {
    val max = (maxOf(hi.toFloat() * 1.5f, value * 1.1f)).coerceAtLeast(1f)
    val zone = MaterialTheme.fit.success
    val track = MaterialTheme.fit.elevated
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(18.dp)) {
        val barH = 10.dp.toPx()
        val top = (size.height - barH) / 2f
        val r = androidx.compose.ui.geometry.CornerRadius(barH / 2f, barH / 2f)
        drawRoundRect(track, topLeft = androidx.compose.ui.geometry.Offset(0f, top), size = androidx.compose.ui.geometry.Size(size.width, barH), cornerRadius = r)
        val x1 = size.width * (lo / max)
        val x2 = size.width * (hi / max)
        drawRect(zone.copy(alpha = 0.18f), topLeft = androidx.compose.ui.geometry.Offset(x1, top), size = androidx.compose.ui.geometry.Size(x2 - x1, barH))
        drawLine(zone.copy(alpha = 0.5f), androidx.compose.ui.geometry.Offset(x1, top), androidx.compose.ui.geometry.Offset(x1, top + barH), strokeWidth = 1.dp.toPx())
        drawLine(zone.copy(alpha = 0.5f), androidx.compose.ui.geometry.Offset(x2, top), androidx.compose.ui.geometry.Offset(x2, top + barH), strokeWidth = 1.dp.toPx())
        val x = (size.width * (value / max)).coerceIn(2.dp.toPx(), size.width - 2.dp.toPx())
        drawRoundRect(
            color,
            topLeft = androidx.compose.ui.geometry.Offset(x - 2.dp.toPx(), 0f),
            size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
    }
}

/* ================================= 3. GÜÇ ================================== */

@Composable
private fun StrengthTab(vm: AppViewModel, nav: NavHostController) {
    val profile by vm.strengthProfile.collectAsStateWithLifecycle()
    val bestLifts by vm.bestLifts.collectAsStateWithLifecycle()
    // 2.29: programdan çıkmış ve 14 gündür yapılmayan hareketler gizli
    val allSets by vm.progressSets.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val bodyWeight by vm.settings.weightKg.collectAsStateWithLifecycle()
    val isMale by vm.settings.isMale.collectAsStateWithLifecycle()
    val goals by vm.settings.strengthGoals.collectAsStateWithLifecycle()
    val activeDays by vm.routineDays.collectAsStateWithLifecycle()
    val allItems by vm.allItems.collectAsStateWithLifecycle()

    val balance = remember(profile) { ProgressAnalytics.liftBalance(profile) }
    val total = remember(profile) { ProgressAnalytics.totalScore(profile) }
    val change12 = remember(allSets, bodyWeight, isMale) { com.example.core.StrengthInsights.totalChange(allSets, bodyWeight, isMale) }
    val plateaus = remember(allSets, exercises) { com.example.core.StrengthInsights.plateaus(allSets, exercises) }

    // Aktif programdaki hareketler
    val activeExerciseIds = remember(activeDays, allItems) {
        val activeDayIds = activeDays.map { it.id }.toSet()
        allItems.filter { it.dayId in activeDayIds }.map { it.exerciseId }.toSet()
    }

    // 1RM gelişimi: Aktif programda olan VEYA son 3 hafta içinde yapılmış, en az 2 seanslı hareketler
    val trackable = remember(allSets, activeExerciseIds) {
        val now = System.currentTimeMillis()
        val threeWeeksCutoff = now - 21L * 24 * 3600 * 1000L
        allSets
            .filter { com.example.core.Analytics.isEffectiveSet(it) && it.weightKg > 0f && it.reps > 0 }
            .groupBy { it.exerciseId }
            .filter { entry ->
                val sets = entry.value
                val distinctWorkouts = sets.map { s -> s.workoutId }.distinct().size
                if (distinctWorkouts < 2) return@filter false
                val latestSetTime = sets.maxOfOrNull { it.performedAt } ?: 0L
                entry.key in activeExerciseIds || latestSetTime >= threeWeeksCutoff || activeExerciseIds.isEmpty()
            }
            .entries
            .sortedByDescending { it.value.maxOfOrNull { s -> s.performedAt } ?: 0L }
            .take(15)
            .map { it.key to it.value.first().exerciseName }
    }
    var selectedExercise by remember(trackable) { mutableStateOf(trackable.firstOrNull()?.first) }
    var goalFor by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(trackable) {
        if (selectedExercise == null || trackable.none { it.first == selectedExercise }) {
            selectedExercise = trackable.firstOrNull()?.first
        }
    }

    goalFor?.let { id ->
        val name = trackable.firstOrNull { it.first == id }?.second ?: ""
        val best = remember(allSets, id) { vm.progressFor(id).maxOfOrNull { it.e1rm } ?: 0f }
        GoalDialog(name, best, goals[id], onSave = { vm.settings.setStrengthGoal(id, it); goalFor = null }, onDismiss = { goalFor = null })
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (profile.isEmpty()) {
            item {
                FitCard {
                    Text("Güç seviyesi hesaplanamadı", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Squat, Bench Press, Deadlift, Overhead Press veya Barbell Row kaydı biriktiğinde " +
                            "vücut ağırlığına göre seviyen burada görünür. Profil ekranından kilonu güncel tuttuğundan emin ol.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.fit.muted
                    )
                }
            }
        } else {
            /* 1) Güç özeti */
            item { StrengthSummaryCard(profile, total, bodyWeight, change12) }
            item { StrengthLevelsCard(profile, bodyWeight) { id -> nav.navigate("${Routes.EXERCISE}/$id") } }
        }

        /* 2) 1RM gelişimi + hedef */
        if (trackable.isNotEmpty()) {
            item {
                Column {
                    SectionHeader("Tahmini 1RM gelişimi")
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        trackable.forEach { (id, name) ->
                            ChoiceChip(name + if (goals.containsKey(id)) " ⚑" else "", selectedExercise == id, { selectedExercise = id })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    val exId = selectedExercise
                    if (exId != null) {
                        val points = remember(allSets, exId) { vm.progressFor(exId) }
                        E1rmCard(points, goals[exId]) { goalFor = exId }
                    }
                }
            }
            /* 3) Tekrar–ağırlık tablosu */
            selectedExercise?.let { exId ->
                item(key = "repmax_$exId") {
                    val ex = exercises.firstOrNull { it.id == exId }
                    val exSets = remember(allSets, exId) { allSets.filter { it.exerciseId == exId } }
                    val e1rm = remember(exSets) {
                        exSets.filter { com.example.core.Analytics.isEffectiveSet(it) && it.weightKg > 0f && it.reps in 1..12 }
                            .maxOfOrNull { com.example.core.Calc.e1rm(it.weightKg, it.reps) } ?: 0f
                    }
                    if (e1rm > 0f) {
                        val lp = remember { vm.settings.loadingProfile() }
                        val kind = com.example.core.loadKindOf(ex?.equipment ?: "")
                        val rows = remember(exSets, e1rm) {
                            com.example.core.StrengthInsights.repMaxTable(exSets, e1rm, { w -> if (kind == com.example.core.LoadKind.BODYWEIGHT) w else lp.round(w, kind) })
                        }
                        RepMaxCard(ex?.name ?: exSets.first().exerciseName, e1rm, rows)
                    }
                }
            }
        }

        /* 4) Plato */
        if (plateaus.isNotEmpty()) {
            item { PlateauCard(plateaus) { id -> nav.navigate("${Routes.EXERCISE}/$id") } }
        }

        /* 5) Lift dengesi */
        if (profile.isNotEmpty()) {
            if (balance.isNotEmpty()) {
                item { LiftBalanceCard(balance) }
            }
        }

        /* 6) Sıralama listesi */
        if (bestLifts.isNotEmpty()) {
            item {
                Column {
                    SectionHeader("En güçlü hareketlerin", "Tahmini 1RM sıralaması")
                    Spacer(Modifier.height(12.dp))
                    FitCard {
                        bestLifts.entries
                            .sortedByDescending { it.value.second }
                            .take(10)
                            .forEachIndexed { i, entry ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { nav.navigate("${Routes.EXERCISE}/${entry.key}") }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${i + 1}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.fit.muted, modifier = Modifier.width(24.dp))
                                    Text(entry.value.first, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (bodyWeight > 0f) {
                                        Text("${(entry.value.second / bodyWeight).trimNum()}×", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.muted)
                                        Spacer(Modifier.width(10.dp))
                                    }
                                    Text(entry.value.second.kg(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.fit.gold)
                                }
                            }
                    }
                }
            }
        }
        item {
            Text(
                "Tüm rekorlar →",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.fit.accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { vm.setStatsTab(3) }.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun LiftStandardCard(lift: LiftStandard, nav: NavHostController) {
    val levelColor = when (lift.levelIndex) {
        0 -> MaterialTheme.fit.muted
        1 -> MaterialTheme.fit.accent.copy(alpha = 0.7f)
        2 -> MaterialTheme.fit.accent
        3 -> MaterialTheme.fit.success
        4 -> Palette.violet
        else -> MaterialTheme.fit.gold
    }
    FitCard(onClick = { nav.navigate("${Routes.EXERCISE}/${lift.exerciseId}") }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(lift.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${lift.e1rm.kg()} · vücut ağırlığının ${lift.bodyweightRatio.trimNum()} katı",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.fit.muted
                )
            }
            Badge(lift.level, levelColor, filled = lift.levelIndex >= 3)
        }
        if (lift.nextLevel != null && lift.nextLevelWeight > lift.e1rm) {
            Spacer(Modifier.height(12.dp))
            val span = lift.nextLevelWeight
            ThinProgress((lift.e1rm / span).coerceIn(0f, 1f), color = levelColor, height = 6.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                "${lift.nextLevel} seviyesine ${(lift.nextLevelWeight - lift.e1rm).kg()} kaldı",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.fit.muted
            )
        }
    }
}

/** Squat'a göre lift oranları: çubuk gerçek oran, beyaz çizgi beklenen oran. */
@Composable
private fun LiftBalanceCard(balance: List<com.example.core.LiftBalanceItem>) {
    FitCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Lift dengesi", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("squat = 1.00", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.muted)
        }
        Text(
            "Çubuk: gerçek oran · çizgi: beklenen oran",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.fit.muted
        )
        Spacer(Modifier.height(10.dp))
        val scale = maxOf(1.3f, balance.maxOf { maxOf(it.actualRatio, it.expectedRatio) } * 1.1f)
        balance.forEach { item ->
            val ok = abs(item.deviationPct) < 12f
            val c = if (ok) MaterialTheme.fit.success else if (item.deviationPct > 0f) MaterialTheme.fit.accent else MaterialTheme.fit.warning
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${item.actualRatio.trimNum()} / ${item.expectedRatio.trimNum()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.fit.muted
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        (if (item.deviationPct >= 0f) "+" else "−") + "%" + abs(item.deviationPct).roundToInt(),
                        style = MaterialTheme.typography.labelLarge,
                        color = c
                    )
                }
                Spacer(Modifier.height(5.dp))
                androidx.compose.foundation.layout.BoxWithConstraints(
                    Modifier.fillMaxWidth().height(12.dp)
                ) {
                    val w = maxWidth
                    Box(Modifier.fillMaxWidth().height(8.dp).align(Alignment.CenterStart).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.fit.elevated))
                    Box(
                        Modifier.width(w * (item.actualRatio / scale).coerceIn(0f, 1f)).height(8.dp)
                            .align(Alignment.CenterStart).clip(RoundedCornerShape(4.dp)).background(c)
                    )
                    Box(
                        Modifier.padding(start = w * (item.expectedRatio / scale).coerceIn(0f, 1f)).width(2.dp).fillMaxHeight()
                            .background(MaterialTheme.colorScheme.onSurface)
                    )
                }
            }
        }
        val weakest = balance.minByOrNull { it.deviationPct }
        Spacer(Modifier.height(6.dp))
        InfoNote(
            (if (weakest != null && weakest.deviationPct < -12f) "En çok geride kalan: ${weakest.displayName}. " else "Liftlerin birbirine göre dengeli görünüyor. ") +
                "Dambıl hareketleri tahmini barbell karşılığına çevrilerek karşılaştırılır."
        )
    }
}

/* =============================== 4. REKORLAR =============================== */

@Composable
private fun RecordsTab(vm: AppViewModel, nav: NavHostController) {
    val prs by vm.prs.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableIntStateOf(0) }

    val types = listOf(null, PrEntity.TYPE_WEIGHT, PrEntity.TYPE_E1RM, PrEntity.TYPE_VOLUME)
    val filtered = if (filter == 0) prs else prs.filter { it.type == types[filter] }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PillTabs(listOf("Hepsi", "Ağırlık", "1RM", "Hacim"), filter) { filter = it }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    Icons.Default.EmojiEvents,
                    "Rekor yok",
                    "Bir harekette önceki en iyi değerini geçtiğinde rekor otomatik kaydedilir."
                )
            }
        } else {
            item {
                Text(
                    "${filtered.size} rekor",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.fit.muted
                )
            }
            items(filtered, key = { it.id }) { pr ->
                FitCard(
                    onClick = { nav.navigate("${Routes.EXERCISE}/${pr.exerciseId}") },
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(MaterialTheme.fit.gold.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.EmojiEvents, null, tint = MaterialTheme.fit.gold, modifier = Modifier.size(18.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pr.exerciseName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${prTypeLabel(pr.type)} · ${formatDate(pr.dateMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.fit.muted
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                when (pr.type) {
                                    PrEntity.TYPE_VOLUME -> formatTonnage(pr.value)
                                    else -> pr.value.kg()
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.fit.gold
                            )
                            if (pr.reps > 0 && pr.type != PrEntity.TYPE_VOLUME) {
                                Text(
                                    "${pr.weightKg.trimNum()} × ${pr.reps}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.fit.muted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ============================== Yardımcı parçalar ========================== */

@Composable
private fun CompareCell(label: String, current: String, previous: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.fit.elevated)
            .padding(vertical = 11.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
        Spacer(Modifier.height(4.dp))
        Text(current, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(
            "önce $previous",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.fit.muted,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.fit.elevated)
            .padding(vertical = 9.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

@Composable
private fun InfoNote(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.fit.accent.copy(alpha = 0.07f))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Info, null, tint = MaterialTheme.fit.accent, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
    }
}

@Composable
private fun InsightRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted, maxLines = 1)
        }
    }
}

@Composable
private fun statusColor(status: LoadStatus): Color =
    com.example.ui.components.MuscleColors.forStatus(status) ?: MaterialTheme.fit.muted

private fun repRangeColor(label: String): Color = when (label) {
    "1-5" -> Palette.danger
    "6-8" -> Palette.warning
    "9-12" -> Palette.success
    "13-20" -> Palette.info
    else -> Palette.violet
}

private fun daysLabel(days: Int): String = when (days) {
    0 -> "bugün"
    1 -> "dün"
    else -> "$days gün önce"
}

private fun muscleAdvice(load: MuscleLoad): String = when (load.status) {
    LoadStatus.NONE -> "Bu kas için kayıt yok. Programına bu bölgeyi hedefleyen bir hareket eklemeyi düşünebilirsin."
    LoadStatus.LOW -> "Haftalık ${load.effectiveSets.trimNum()} set, önerilen ${load.target.first}-${load.target.last} aralığının belirgin altında. 3–4 set eklemek anlamlı fark yaratır."
    LoadStatus.BELOW -> "Hedefe yakınsın. 1–2 set daha eklemek yeterli olur."
    LoadStatus.OPTIMAL -> "Bu kas ideal aralıkta çalışıyor. Hacmi korumak yeterli; ilerleme için ağırlığı artırmaya odaklan."
    LoadStatus.HIGH -> "Önerilen aralığın üstünde. Toparlanma sıkıntısı yaşamıyorsan sorun değil, ama diğer kasların ihmal edilmediğinden emin ol."
    LoadStatus.EXCESSIVE -> "Bu kas için hacim çok yüksek. Set sayısını azaltıp kaliteye odaklanmak daha verimli olabilir."
}

private fun prTypeLabel(type: String) = when (type) {
    PrEntity.TYPE_WEIGHT -> "En ağır set"
    PrEntity.TYPE_E1RM -> "Tahmini 1RM"
    PrEntity.TYPE_VOLUME -> "Seans hacmi"
    PrEntity.TYPE_REPS -> "Tekrar rekoru"
    else -> "Rekor"
}

@Composable
private fun MuscleContributorsSection(key: String, contributors: List<MuscleContributor>) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.fit.accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Beslendiği & Veri Aldığı Hareketler (${contributors.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${MuscleMap.label(key)} kasına 1.0 birincil hedef veya 0.5 ikincil destek katkısı veren egzersizler:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.fit.muted
        )
        Spacer(Modifier.height(10.dp))

        if (contributors.isEmpty()) {
            Text(
                "Seçilen dönemde veya aktif programda bu kasa veri sağlayan hareket bulunamadı.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.fit.muted,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                contributors.forEach { c ->
                    MuscleContributorItem(c)
                }
            }
        }
    }
}

@Composable
private fun MuscleContributorItem(c: MuscleContributor) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.fit.cardBorder.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        c.exerciseName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            val targetUrl = "https://www.youtube.com/results?search_query=${android.net.Uri.encode("${c.exerciseName} egzersizi yapılışı")}"
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(targetUrl))
                            try { context.startActivity(intent) } catch (_: Exception) {}
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = "YouTube'da izle",
                            tint = Color(0xFFFF0000),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (c.isPrimary) {
                    Badge("1.0 Set (Birincil)", MaterialTheme.fit.success)
                } else {
                    Badge("0.5 Set (İkincil Destek)", MaterialTheme.fit.accent)
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    if (c.performedSetCount > 0) {
                        Text(
                            "${c.performedSetCount} set yapıldı  →  +${c.effectiveContribution.trimNum()} etkin set/hafta",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (c.totalVolumeContribution > 0f) {
                            Text(
                                "Hacim katkısı: ${formatTonnage(c.totalVolumeContribution)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.fit.muted
                            )
                        }
                    } else {
                        Text(
                            "Bu dönemde yapılmadı",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.fit.muted
                        )
                    }
                }

                if (c.inRoutineDays.isNotEmpty()) {
                    Badge(
                        "Program: ${c.inRoutineDays.joinToString(", ")}",
                        MaterialTheme.fit.gold
                    )
                }
            }
        }
    }
}
