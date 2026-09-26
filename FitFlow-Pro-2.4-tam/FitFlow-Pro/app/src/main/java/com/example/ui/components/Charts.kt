package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.trimNum
import com.example.ui.theme.fit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/* ================================ Çizgi grafik =============================== */

/**
 * Yumuşatılmış çizgi grafik. Noktaya dokununca o noktanın değeri gösterilir.
 */
@Composable
fun LineChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.fit.accent,
    suffix: String = "",
    height: Dp = 170.dp,
    showArea: Boolean = true,
    showPoints: Boolean = true
) {
    if (values.isEmpty()) {
        ChartPlaceholder(height); return
    }
    var selected by remember(values) { mutableIntStateOf(values.lastIndex) }
    val anim by animateFloatAsState(1f, tween(600), label = "line")

    val maxV = values.maxOrNull() ?: 0f
    val minV = values.minOrNull() ?: 0f
    val span = (maxV - minV).let { if (it <= 0.0001f) max(maxV, 1f) else it }
    val lo = if (maxV == minV) 0f else minV - span * 0.15f
    val hi = maxV + span * 0.15f
    val range = (hi - lo).coerceAtLeast(0.0001f)

    val grid = MaterialTheme.fit.cardBorder

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    "${values.getOrElse(selected) { 0f }.trimNum()}$suffix",
                    style = MaterialTheme.typography.headlineMedium,
                    color = color
                )
                Text(
                    labels.getOrElse(selected) { "" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.fit.muted
                )
            }
            if (values.size >= 2) {
                val first = values.first()
                val last = values.last()
                val diff = last - first
                val pct = if (abs(first) > 0.001f) diff / first * 100f else 0f
                Badge(
                    text = (if (diff >= 0) "▲ " else "▼ ") + "%${abs(pct).roundToInt()}",
                    color = if (diff >= 0) MaterialTheme.fit.success else MaterialTheme.fit.danger
                )
            }
        }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(values) {
                    detectTapGestures { offset ->
                        if (values.size > 1) {
                            val step = size.width / (values.size - 1).toFloat()
                            selected = (offset.x / step).roundToInt().coerceIn(0, values.lastIndex)
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val bottomPad = 4f

            // yatay kılavuz çizgileri
            repeat(4) { i ->
                val y = h * i / 3f
                drawLine(
                    color = grid,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f))
                )
            }

            fun px(i: Int) = if (values.size == 1) w / 2f else w * i / (values.size - 1).toFloat()
            fun py(v: Float) = (h - bottomPad) - ((v - lo) / range) * (h - bottomPad * 2)

            val path = Path()
            val area = Path()
            values.forEachIndexed { i, v ->
                val x = px(i)
                val y = py(v)
                if (i == 0) { path.moveTo(x, y); area.moveTo(x, h) ; area.lineTo(x, y) }
                else {
                    val prevX = px(i - 1)
                    val prevY = py(values[i - 1])
                    val cx = (prevX + x) / 2f
                    path.cubicTo(cx, prevY, cx, y, x, y)
                    area.cubicTo(cx, prevY, cx, y, x, y)
                }
            }
            if (showArea) {
                area.lineTo(px(values.lastIndex), h)
                area.close()
                drawPath(
                    area,
                    Brush.verticalGradient(listOf(color.copy(alpha = 0.35f * anim), color.copy(alpha = 0f)))
                )
            }
            drawPath(path, color, style = Stroke(width = 3.5f, cap = StrokeCap.Round))

            if (showPoints) {
                values.forEachIndexed { i, v ->
                    val x = px(i); val y = py(v)
                    val isSel = i == selected
                    if (isSel) {
                        drawLine(color.copy(alpha = 0.4f), Offset(x, 0f), Offset(x, h), strokeWidth = 1.5f)
                        drawCircle(color.copy(alpha = 0.25f), radius = 12f, center = Offset(x, y))
                    }
                    drawCircle(color, radius = if (isSel) 6.5f else 4f, center = Offset(x, y))
                    drawCircle(Color.Black.copy(alpha = 0.25f), radius = if (isSel) 3f else 1.8f, center = Offset(x, y))
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(labels.firstOrNull() ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
            if (labels.size > 2) {
                Text(labels[labels.size / 2], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
            }
            Text(labels.lastOrNull() ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
        }
    }
}

/* ================================ Sütun grafik =============================== */

@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.fit.accent,
    suffix: String = "",
    height: Dp = 160.dp,
    highlightLast: Boolean = true
) {
    if (values.isEmpty()) { ChartPlaceholder(height); return }
    var selected by remember(values) { mutableIntStateOf(if (highlightLast) values.lastIndex else -1) }
    val maxV = (values.maxOrNull() ?: 0f).coerceAtLeast(0.0001f)
    val anim by animateFloatAsState(1f, tween(600), label = "bar")
    val trackColor = MaterialTheme.fit.elevated

    Column(modifier.fillMaxWidth()) {
        if (selected in values.indices) {
            Text(
                "${values[selected].trimNum()}$suffix · ${labels.getOrElse(selected) { "" }}",
                style = MaterialTheme.typography.titleMedium,
                color = color,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(values) {
                    detectTapGestures { offset ->
                        val slot = size.width / values.size
                        selected = (offset.x / slot).toInt().coerceIn(0, values.lastIndex)
                    }
                }
        ) {
            val slot = size.width / values.size
            val barW = slot * 0.56f
            values.forEachIndexed { i, v ->
                val x = slot * i + (slot - barW) / 2f
                val barH = (v / maxV) * size.height * anim
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x, 0f),
                    size = Size(barW, size.height),
                    cornerRadius = CornerRadius(barW / 3f, barW / 3f)
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            if (i == selected) color else color.copy(alpha = 0.75f),
                            (if (i == selected) color else color.copy(alpha = 0.75f)).copy(alpha = 0.45f)
                        )
                    ),
                    topLeft = Offset(x, size.height - barH),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(barW / 3f, barW / 3f)
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.fit.muted,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

/* ============================== Yatay dağılım ================================ */

data class DistributionItem(
    val label: String,
    val value: Float,
    val color: Color,
    val caption: String = "",
    val max: Float = 0f
)

@Composable
fun DistributionBars(
    items: List<DistributionItem>,
    modifier: Modifier = Modifier,
    valueSuffix: String = "",
    onItemClick: ((DistributionItem) -> Unit)? = null
) {
    val maxV = (items.maxOfOrNull { it.value } ?: 0f).coerceAtLeast(0.0001f)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { item ->
            val targetMax = if (item.max > 0f) item.max else maxV
            val frac by animateFloatAsState((item.value / targetMax).coerceIn(0f, 1f), tween(500), label = "dist")
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (onItemClick != null) Modifier.clickable { onItemClick(item) } else Modifier)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(item.color))
                        Text(item.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        "${item.value.trimNum()}$valueSuffix" + if (item.caption.isBlank()) "" else "  ·  ${item.caption}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.fit.muted
                    )
                }
                Spacer(Modifier.height(5.dp))
                Canvas(Modifier.fillMaxWidth().height(9.dp)) {
                    val r = CornerRadius(size.height / 2f, size.height / 2f)
                    drawRoundRect(color = item.color.copy(alpha = 0.12f), size = size, cornerRadius = r)
                    if (frac > 0f) {
                        drawRoundRect(
                            color = item.color,
                            size = Size((size.width * frac).coerceAtLeast(size.height), size.height),
                            cornerRadius = r
                        )
                    }
                }
            }
        }
    }
}

/* ============================== Aktivite ısı haritası ======================== */

/**
 * GitHub tarzı katkı ızgarası. [levels] 0..4 arası yoğunluk, en eski günden bugüne.
 */
@Composable
fun ActivityHeatmap(
    levels: List<Int>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.fit.accent,
    columns: Int = 7
) {
    val empty = MaterialTheme.fit.elevated
    Canvas(
        modifier
            .fillMaxWidth()
            .height((columns * 16).dp)
    ) {
        val weeks = ((levels.size + columns - 1) / columns).coerceAtLeast(1)
        val cell = minOf(size.height / columns, size.width / weeks)
        val pad = cell * 0.14f
        levels.forEachIndexed { i, lvl ->
            val dayOfWeek = i % columns
            val week = i / columns
            val alpha = when (lvl.coerceIn(0, 4)) {
                0 -> 0f; 1 -> 0.32f; 2 -> 0.52f; 3 -> 0.76f; else -> 1f
            }
            val c = if (lvl <= 0) empty else color.copy(alpha = alpha)
            drawRoundRect(
                color = c,
                topLeft = Offset(week * cell + pad, dayOfWeek * cell + pad),
                size = Size(cell - pad * 2, cell - pad * 2),
                cornerRadius = CornerRadius(cell * 0.24f, cell * 0.24f)
            )
        }
    }
}

/* ================================== Halka ==================================== */

@Composable
fun DonutChart(
    items: List<DistributionItem>,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    stroke: Dp = 22.dp,
    center: @Composable () -> Unit = {}
) {
    val total = items.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(0.0001f)
    val anim by animateFloatAsState(1f, tween(700), label = "donut")
    val track = MaterialTheme.fit.elevated
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val inset = sw / 2f
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(
                color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(sw)
            )
            var start = -90f
            items.forEach { item ->
                val sweep = 360f * (item.value / total) * anim
                drawArc(
                    color = item.color, startAngle = start + 1f, sweepAngle = (sweep - 2f).coerceAtLeast(0f),
                    useCenter = false, topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(sw, cap = StrokeCap.Round)
                )
                start += sweep
            }
        }
        center()
    }
}

/* ================================ Yer tutucu ================================= */

@Composable
private fun ChartPlaceholder(height: Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.fit.elevated),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Grafiği oluşturmak için yeterli veri yok",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.fit.muted
        )
    }
}

/* ================================= Sparkline ================================= */

@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.fit.accent,
    height: Dp = 38.dp
) {
    if (values.size < 2) { Spacer(modifier.height(height)); return }
    val maxV = values.maxOrNull() ?: 0f
    val minV = values.minOrNull() ?: 0f
    val range = (maxV - minV).coerceAtLeast(0.0001f)
    Canvas(modifier.fillMaxWidth().height(height)) {
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1).toFloat()
            val y = size.height - ((v - minV) / range) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
    }
}

/* ============================== Haftalık şerit =============================== */

@Composable
fun WeekStrip(
    doneWeekdays: Set<Int>,
    todayWeekday: Int,
    plannedWeekdays: Set<Int> = emptySet(),
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.fit.accent
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (1..7).forEach { d ->
            val done = doneWeekdays.contains(d)
            val planned = plannedWeekdays.contains(d)
            val isToday = d == todayWeekday
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(
                    com.example.core.weekdayShort(d),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) accent else MaterialTheme.fit.muted
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                done -> accent
                                planned -> accent.copy(alpha = 0.16f)
                                else -> MaterialTheme.fit.elevated
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (done) "✓" else if (planned) "•" else "",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (done) MaterialTheme.fit.onAccent else accent
                    )
                }
            }
        }
    }
}
