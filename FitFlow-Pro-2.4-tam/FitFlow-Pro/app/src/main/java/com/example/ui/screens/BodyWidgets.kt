package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.BodyCalc
import com.example.core.formatDateShort
import com.example.core.trimNum
import com.example.ui.theme.fit
import kotlin.math.abs

/** "−1,2 kg" / "+0,8 kg" / "0 kg" — tek işaret, ondalık virgül. */
internal fun signedKg(v: Float): String {
    val sign = when {
        v > 0.049f -> "+"
        v < -0.049f -> "−"
        else -> ""
    }
    return sign + abs(v).trimNum().replace('.', ',') + " kg"
}

/**
 * Kilo grafiği: ham ölçümler nokta olarak, zaman ağırlıklı EWMA trendi düz çizgi olarak.
 * X ekseni tarihle orantılıdır (düzensiz ölçüm aralıkları doğru görünür).
 */
@Composable
internal fun WeightTrendChart(
    points: List<BodyCalc.WeightPoint>,
    trend: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp
) {
    if (points.size < 2 || trend.size != points.size) return
    var selected by remember(points) { mutableIntStateOf(points.lastIndex) }
    val accent = MaterialTheme.fit.accent
    val trendColor = MaterialTheme.fit.gold
    val grid = MaterialTheme.fit.cardBorder

    val all = points.map { it.weightKg } + trend
    val maxV = all.maxOrNull() ?: 0f
    val minV = all.minOrNull() ?: 0f
    val span = (maxV - minV).coerceAtLeast(1f)
    val lo = minV - span * 0.15f
    val hi = maxV + span * 0.15f
    val range = (hi - lo).coerceAtLeast(0.0001f)
    val t0 = points.first().dateMillis
    val tSpan = (points.last().dateMillis - t0).coerceAtLeast(1L).toFloat()
    val change = BodyCalc.trendChange(points, trend, 30)

    Column(modifier.fillMaxWidth()) {
        val sel = selected.coerceIn(0, points.lastIndex)
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    points[sel].weightKg.trimNum().replace('.', ',') + " kg",
                    style = MaterialTheme.typography.headlineMedium,
                    color = accent
                )
                Text(
                    formatDateShort(points[sel].dateMillis) + " · trend " + trend[sel].trimNum().replace('.', ',') + " kg",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.fit.muted
                )
            }
            if (change != null) {
                Text(
                    "Trend: ${signedKg(change.deltaKg)} / ${change.spanDays} gün",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.fit.muted
                )
            }
        }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat()
                        var best = 0
                        var bestD = Float.MAX_VALUE
                        points.forEachIndexed { i, p ->
                            val x = w * (p.dateMillis - t0) / tSpan
                            val d = abs(x - offset.x)
                            if (d < bestD) { bestD = d; best = i }
                        }
                        selected = best
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val pad = 6f
            repeat(4) { i ->
                val y = h * i / 3f
                drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f)))
            }
            fun px(i: Int) = w * (points[i].dateMillis - t0) / tSpan
            fun py(v: Float) = (h - pad) - ((v - lo) / range) * (h - pad * 2)

            // ham ölçümler: ince, soluk bağlantı + noktalar
            val raw = Path()
            points.forEachIndexed { i, p ->
                if (i == 0) raw.moveTo(px(i), py(p.weightKg)) else raw.lineTo(px(i), py(p.weightKg))
            }
            drawPath(raw, accent.copy(alpha = 0.25f), style = Stroke(width = 1.5f))

            // trend çizgisi
            val tp = Path()
            trend.forEachIndexed { i, v ->
                if (i == 0) tp.moveTo(px(i), py(v)) else tp.lineTo(px(i), py(v))
            }
            drawPath(tp, trendColor, style = Stroke(width = 4f, cap = StrokeCap.Round))

            points.forEachIndexed { i, p ->
                val c = Offset(px(i), py(p.weightKg))
                if (i == sel) {
                    drawLine(accent.copy(alpha = 0.4f), Offset(c.x, 0f), Offset(c.x, h), strokeWidth = 1.5f)
                    drawCircle(accent.copy(alpha = 0.25f), radius = 12f, center = c)
                }
                drawCircle(accent, radius = if (i == sel) 6.5f else 4f, center = c)
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDateShort(points.first().dateMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(accent))
                Spacer(Modifier.width(4.dp))
                Text("ölçüm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.width(14.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(trendColor))
                Spacer(Modifier.width(4.dp))
                Text("7 günlük trend", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
            }
            Text(formatDateShort(points.last().dateMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
        }
    }
}
