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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.MuscleMap
import com.example.ui.theme.fit
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/* ==========================================================================
 * Anatomik kas haritası (v2)
 *
 * Geometri BodyData.kt içinde: düz renkli segmentasyon görsellerinden çıkarılmış
 * ön + arka görünüm, 24 detay bölgesi. Uygulamaya hiçbir görsel dosyası eklenmez;
 * her şey vektörel çizilir, her ekran boyutunda keskin kalır.
 *
 * Her kas beş katmanda çizilir:
 *   1. oluk dolgusu  — kasın koyu tonu, hafif taşırılarak komşu kaslarla arasındaki
 *                      boşluğu kapatır; aradaki çizgi doğal bir "fasya" oluğu olur
 *   2. hacim         — sol üstten ışık alan radyal degrade (orta parlak, kenar koyu)
 *   3. lif dokusu    — kasın anatomik lif yönünde ince çizgiler (yalnızca yeterince
 *                      büyük çizimlerde; küçük önizlemelerde gürültü olmasın diye kapalı)
 *   4. iç gölge      — kenar boyunca koyu bir hale, kasa kabarıklık verir
 *   5. kenar ışığı   — ince açık kontur
 * Seçili kas, üstüne parlak bir hatla işaretlenir.
 * ========================================================================== */

private val BW = BodyData.WIDTH
private val BH = BodyData.HEIGHT

enum class BodyView { FRONT, BACK }

/** Kasın göründüğü taraf: yalnızca arkada çizilen kaslar (kanat, bel, arka bacak…) için BACK. */
fun bodyViewOf(key: String): BodyView {
    val onFront = BodyData.FRONT.any { it.key == key || it.group == key }
    val onBack = BodyData.BACK.any { it.key == key || it.group == key }
    return if (onBack && !onFront) BodyView.BACK else BodyView.FRONT
}

/* ------------------------------ Ayrıştırma -------------------------------- */

private fun parsePolygons(data: String): List<List<Offset>> =
    data.split(';').mapNotNull { poly ->
        val pts = poly.trim().split(' ').mapNotNull { pair ->
            val i = pair.indexOf(',')
            if (i <= 0) return@mapNotNull null
            val x = pair.substring(0, i).toFloatOrNull() ?: return@mapNotNull null
            val y = pair.substring(i + 1).toFloatOrNull() ?: return@mapNotNull null
            Offset(x, y)
        }
        if (pts.size >= 3) pts else null
    }

/** Chaikin köşe kesme — taşma yapmadan yumuşatır. */
private fun chaikin(points: List<Offset>, iterations: Int): List<Offset> {
    var pts = points
    repeat(iterations) {
        if (pts.size < 3) return pts
        val out = ArrayList<Offset>(pts.size * 2)
        for (i in pts.indices) {
            val p = pts[i]
            val q = pts[(i + 1) % pts.size]
            out.add(Offset(p.x * 0.75f + q.x * 0.25f, p.y * 0.75f + q.y * 0.25f))
            out.add(Offset(p.x * 0.25f + q.x * 0.75f, p.y * 0.25f + q.y * 0.75f))
        }
        pts = out
    }
    return pts
}

private fun toPath(points: List<Offset>): Path = Path().apply {
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    close()
}

/* -------------------------------- Bölgeler -------------------------------- */

/** Tek bir kas parçası (bir bölge birden çok parçadan oluşabilir: sağ/sol, kas başları). */
private class Piece(points: List<Offset>, angleDeg: Float) {
    val path: Path = toPath(points)
    val bounds: Rect = path.getBounds()
    private val center = Offset(
        points.sumOf { it.x.toDouble() }.toFloat() / points.size,
        points.sumOf { it.y.toDouble() }.toFloat() / points.size
    )

    /** Lif çizgileri: vücudun sağ yarısında açı aynalanır. */
    val fibers: Path = Path().apply {
        val a = Math.toRadians((if (center.x > BW / 2f) 180f - angleDeg else angleDeg).toDouble())
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        val span = sqrt(bounds.width * bounds.width + bounds.height * bounds.height)
        val spacing = 1.0f
        val n = (span / spacing).toInt() + 1
        for (k in -n..n) {
            val bx = center.x - dy * k * spacing
            val by = center.y + dx * k * spacing
            moveTo(bx - dx * span, by - dy * span)
            lineTo(bx + dx * span, by + dy * span)
        }
    }

    fun contains(p: Offset): Boolean = bounds.contains(p)
}

private class Region(val key: String, val group: String, val pieces: List<Piece>) {
    /** En küçük parçanın alanı — iç içe geçen sınırlarda dokunmayı çözmek için. */
    fun hitArea(p: Offset): Float? =
        pieces.filter { it.contains(p) }.minOfOrNull { max(it.bounds.width * it.bounds.height, 0.01f) }
}

private fun buildRegions(list: List<BodyData.Region>): List<Region> = list.map { r ->
    val angle = BodyData.fiberAngle(r.key)
    Region(r.key, r.group, parsePolygons(r.data).map { Piece(chaikin(it, 1), angle) })
}

private val frontBody: List<Path> by lazy { parsePolygons(BodyData.FRONT_BODY).map { toPath(chaikin(it, 1)) } }
private val backBody: List<Path> by lazy { parsePolygons(BodyData.BACK_BODY).map { toPath(chaikin(it, 1)) } }
private val frontRegions: List<Region> by lazy { buildRegions(BodyData.FRONT) }
private val backRegions: List<Region> by lazy { buildRegions(BodyData.BACK) }

/* --------------------------- Çizim yardımcıları ---------------------------- */

private fun Color.lighten(amount: Float): Color = lerp(this, Color.White, amount)
private fun Color.darken(amount: Float): Color = lerp(this, Color.Black, amount)

/** Bir kas parçasını hacimli, dokulu ve gölgeli çizer. Tüm ölçüler harita birimindedir. */
private fun DrawScope.drawMuscle(piece: Piece, base: Color, detailed: Boolean) {
    val groove = base.darken(0.55f)
    // 1) oluk dolgusu — komşu kaslarla aradaki boşluğu kapatır
    drawPath(piece.path, groove)
    drawPath(piece.path, groove, style = Stroke(1.1f, join = StrokeJoin.Round))
    // 2) hacim — sol üstten ışık
    val b = piece.bounds
    drawPath(
        piece.path,
        Brush.radialGradient(
            0f to base.lighten(0.33f),
            0.55f to base,
            1f to base.darken(0.45f),
            center = Offset(b.left + b.width * 0.38f, b.top + b.height * 0.30f),
            radius = max(b.width, b.height) * 0.85f
        )
    )
    // 3-4) lif dokusu + iç gölge (kasın içine kırpılmış)
    clipPath(piece.path) {
        if (detailed) {
            drawPath(piece.fibers, Color.White.copy(alpha = 0.12f), style = Stroke(0.28f, cap = StrokeCap.Round))
        }
        drawPath(piece.path, Color.Black.copy(alpha = 0.30f), style = Stroke(1.3f, join = StrokeJoin.Round))
    }
    // 5) kenar ışığı
    drawPath(piece.path, base.lighten(0.5f).copy(alpha = 0.22f), style = Stroke(0.3f, join = StrokeJoin.Round))
}

/* -------------------------------- Composable ------------------------------ */

/**
 * Tek bir vücut görünümü.
 *
 * @param colors kas anahtarı → renk. Önce detay anahtarı ("chest_upper"), yoksa grup
 *               anahtarı ("chest") aranır. Renk verilmeyen kaslar nötr tonda çizilir.
 * @param selected seçili kas (grup ya da detay anahtarı) — parlak hatla çerçevelenir
 * @param onMuscleTap dokunulan kasın GRUP anahtarını döner (MuscleMap anahtarı)
 */
@Composable
fun BodyMuscleMap(
    view: BodyView,
    colors: Map<String, Color>,
    modifier: Modifier = Modifier,
    selected: String? = null,
    onMuscleTap: ((String) -> Unit)? = null
) {
    val regions = if (view == BodyView.FRONT) frontRegions else backRegions
    val bodyPaths = if (view == BodyView.FRONT) frontBody else backBody

    val isDark = MaterialTheme.fit.isDark
    val silTop = if (isDark) Color(0xFF2C3440) else Color(0xFFD5D9DF)
    val silBottom = if (isDark) Color(0xFF1E242C) else Color(0xFFBFC4CB)
    val rim = if (isDark) Color(0xFF3E4C60) else Color(0xFF9AA7B8)
    // Nötr: renksiz, sıcak gri — renkli durumlarla (özellikle mavi tonlarla) karışmasın.
    val neutral = if (isDark) Color(0xFF4A4D52) else Color(0xFFC9CBCF)
    val selectLine = if (isDark) Color.White else Color(0xFF1E1B4B)
    val selectGlow = Color(0xFFA855F7)

    val glow by animateFloatAsState(if (selected != null) 1f else 0f, tween(220), label = "sel")

    Canvas(
        modifier.pointerInput(view, onMuscleTap) {
            if (onMuscleTap == null) return@pointerInput
            detectTapGestures { tap ->
                val s = min(size.width / BW, size.height / BH)
                val dx = (size.width - BW * s) / 2f
                val dy = (size.height - BH * s) / 2f
                val local = Offset((tap.x - dx) / s, (tap.y - dy) / s)
                regions.mapNotNull { r -> r.hitArea(local)?.let { r to it } }
                    .minByOrNull { it.second }
                    ?.let { onMuscleTap(it.first.group) }
            }
        }
    ) {
        val s = min(size.width / BW, size.height / BH)
        val dx = (size.width - BW * s) / 2f
        val dy = (size.height - BH * s) / 2f
        // Lif dokusu yalnızca bir birim en az ~2.4 piksel olduğunda anlamlı.
        val detailed = s >= 2.4f

        withTransform({
            translate(dx, dy)
            scale(s, s, Offset.Zero)
        }) {
            // Gövde silueti
            bodyPaths.forEach { p ->
                val b = p.getBounds()
                drawPath(p, Brush.verticalGradient(listOf(silTop, silBottom), startY = b.top, endY = b.bottom))
                drawPath(p, rim, style = Stroke(0.5f, join = StrokeJoin.Round))
            }

            // Kaslar
            regions.forEach { r ->
                val c = colors[r.key] ?: colors[r.group]
                val isSel = selected != null && (selected == r.group || selected == r.key)
                // Rengin alfa değeri yoğunluk anlamına gelir: nötr tondan hedef renge doğru karıştırılır.
                var base = if (c == null || c.alpha <= 0.01f) neutral else lerp(neutral, c.copy(alpha = 1f), c.alpha.coerceIn(0f, 1f))
                // Bir kas seçiliyken diğerleri geri plana çekilir
                if (selected != null && !isSel) base = lerp(base, neutral, 0.55f * glow)
                r.pieces.forEach { drawMuscle(it, base, detailed) }
            }

            // Seçili kasın parlak hattı
            if (selected != null && glow > 0.01f) {
                regions.filter { selected == it.group || selected == it.key }.forEach { r ->
                    r.pieces.forEach { p ->
                        drawPath(p.path, selectGlow.copy(alpha = 0.45f * glow), style = Stroke(2.6f, join = StrokeJoin.Round))
                        drawPath(p.path, selectLine.copy(alpha = 0.95f * glow), style = Stroke(0.8f, join = StrokeJoin.Round))
                    }
                }
            }
        }
    }
}

/** Ön + arka görünümü yan yana gösterir. */
@Composable
fun BodyMuscleMapPair(
    colors: Map<String, Color>,
    modifier: Modifier = Modifier,
    height: Dp = 250.dp,
    selected: String? = null,
    onMuscleTap: ((String) -> Unit)? = null,
    showLabels: Boolean = true
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(BodyView.FRONT to "Ön", BodyView.BACK to "Arka").forEach { (view, label) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    BodyMuscleMap(
                        view = view,
                        colors = colors,
                        modifier = Modifier.fillMaxWidth().height(height),
                        selected = selected,
                        onMuscleTap = onMuscleTap
                    )
                    if (showLabels) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.fit.muted
                        )
                    }
                }
            }
        }

        if (selected != null && onMuscleTap != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .clickable { onMuscleTap(selected) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Seçimi Temizle",
                        tint = MaterialTheme.fit.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Temizle",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/** Yoğunluk ölçeği açıklaması. */
@Composable
fun MuscleHeatLegend(
    modifier: Modifier = Modifier,
    lowLabel: String = "Az",
    highLabel: String = "Çok",
    color: Color = MaterialTheme.fit.accent
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(lowLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(0.18f, 0.38f, 0.58f, 0.78f, 1f).forEach { a ->
                Box(
                    Modifier
                        .size(width = 22.dp, height = 8.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color.copy(alpha = a))
                )
            }
        }
        Text(highLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
    }
}

/** Birincil / ikincil kas göstergesi (hareket detayı için). */
@Composable
fun MusclePrimarySecondaryLegend(
    primaryColor: Color = MaterialTheme.fit.accent,
    secondaryColor: Color = MaterialTheme.fit.accent.copy(alpha = 0.42f),
    modifier: Modifier = Modifier
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(primaryColor to "Birincil", secondaryColor to "Destek").forEach { (c, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
            }
        }
    }
}

/** Tıklanabilir kas etiketi listesi — haritanın altında özet gösterir. */
@Composable
fun MuscleChipRow(
    entries: List<Pair<String, Color>>,
    modifier: Modifier = Modifier,
    selected: String? = null,
    onClick: ((String) -> Unit)? = null
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (key, color) ->
                    val active = key == selected
                    val chipBg = if (active) Color(0xFF3730A3).copy(alpha = 0.35f) else color.copy(alpha = 0.12f)
                    val dotColor = if (active) Color(0xFFA855F7) else color
                    val textColor = if (active) Color(0xFFE0E7FF) else MaterialTheme.colorScheme.onSurface
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(chipBg)
                            .then(if (onClick != null) Modifier.clickable { onClick(key) } else Modifier)
                            .padding(horizontal = 7.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(dotColor))
                        Text(
                            MuscleMap.label(key),
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor,
                            maxLines = 1,
                            textAlign = TextAlign.Start
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
