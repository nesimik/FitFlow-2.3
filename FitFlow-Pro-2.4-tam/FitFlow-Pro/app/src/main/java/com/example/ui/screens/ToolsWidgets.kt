package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.weekdayShort
import com.example.ui.components.OverlineText
import com.example.ui.theme.fit
import com.example.ui.theme.mono
import java.util.Calendar

/* ==========================================================================
 * Araçlar ve Vücut ekranlarının ortak küçük bileşenleri (yeni tasarım dili).
 * ========================================================================== */

/** Tam genişlikte segmentli seçici: yükseltilmiş zemin + seçili "hap". */
@Composable
internal fun TkSegmented(
    items: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.fit.elevated)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        items.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (on) MaterialTheme.fit.accent else MaterialTheme.fit.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Kart başlığı: üst etiket + tek satırlık açıklama. */
@Composable
internal fun TkCardTitle(title: String, subtitle: String? = null, color: Color = MaterialTheme.fit.muted) {
    OverlineText(title, color)
    if (!subtitle.isNullOrBlank()) {
        Spacer(Modifier.height(3.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
    }
}

/** Büyük mono sayı + birim + alt açıklama. */
@Composable
internal fun TkHero(
    value: String,
    unit: String? = null,
    color: Color = MaterialTheme.fit.accent,
    caption: String? = null
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            value,
            style = MaterialTheme.typography.displaySmall.mono().copy(fontWeight = FontWeight.SemiBold),
            color = color,
            maxLines = 1
        )
        if (!unit.isNullOrBlank()) {
            Spacer(Modifier.width(6.dp))
            Text(
                unit,
                style = MaterialTheme.typography.titleMedium.mono(),
                color = MaterialTheme.fit.muted,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
    if (!caption.isNullOrBlank()) {
        Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
    }
}

/** Küçük istatistik kutusu: soluk etiket + mono değer. */
@Composable
internal fun TkStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    caption: String? = null,
    captionColor: Color? = null
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.fit.elevated.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleSmall.mono().copy(fontWeight = FontWeight.SemiBold),
            color = color ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!caption.isNullOrBlank()) {
            Text(caption, style = MaterialTheme.typography.labelSmall, color = captionColor ?: MaterialTheme.fit.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Küçük renkli değişim etiketi ("−1,2 kg / 30 gün"). */
@Composable
internal fun TkChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.mono().copy(fontWeight = FontWeight.SemiBold),
        color = color,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/** Bir aralık bölgesi: [upTo] değerine kadar bu renk. */
internal data class TkZone(val upTo: Float, val color: Color, val label: String)

/**
 * Renkli aralık çubuğu (VKİ gibi): bölgeler eşit olmayan genişlikte, değer bir işaretçiyle.
 * [min]..[max] dışındaki değerler kenara sabitlenir.
 */
@Composable
internal fun TkRangeBar(value: Float, min: Float, max: Float, zones: List<TkZone>, modifier: Modifier = Modifier) {
    val marker = MaterialTheme.colorScheme.onSurface
    val activeIdx = zones.indexOfFirst { value < it.upTo }.let { if (it < 0) zones.lastIndex else it }
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(16.dp)) {
            val h = 6.dp.toPx()
            val top = (size.height - h) / 2f
            val gap = 2.dp.toPx()
            val span = (max - min).coerceAtLeast(0.0001f)
            var start = min
            zones.forEachIndexed { i, z ->
                val end = z.upTo.coerceAtMost(max)
                val x0 = (start - min) / span * size.width
                val x1 = (end - min) / span * size.width
                val w = (x1 - x0 - if (i < zones.lastIndex) gap else 0f).coerceAtLeast(0f)
                drawRoundRect(
                    z.color.copy(alpha = if (i == activeIdx) 0.95f else 0.35f),
                    Offset(x0, top), Size(w, h), CornerRadius(h / 2f)
                )
                start = end
            }
            if (value > 0f) {
                val mx = ((value.coerceIn(min, max) - min) / span * size.width).coerceIn(4.dp.toPx(), size.width - 4.dp.toPx())
                drawCircle(marker, 6.dp.toPx(), Offset(mx, size.height / 2f))
                drawCircle(zones[activeIdx].color, 3.5.dp.toPx(), Offset(mx, size.height / 2f))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            val span = (max - min).coerceAtLeast(0.0001f)
            var start = min
            zones.forEachIndexed { i, z ->
                val end = z.upTo.coerceAtMost(max)
                val w = ((end - start) / span).coerceAtLeast(0.01f)
                Text(
                    z.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == activeIdx) z.color else MaterialTheme.fit.muted,
                    modifier = Modifier.weight(w),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
                start = end
            }
        }
    }
}

/** Liste satırlarındaki tarih kutusu: kısa gün adı + ayın günü. */
@Composable
internal fun TkDateBlock(millis: Long, modifier: Modifier = Modifier) {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val weekday = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
    Column(
        modifier
            .width(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.fit.elevated)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            weekdayShort(weekday).uppercase(java.util.Locale("tr")),
            style = MaterialTheme.typography.labelSmall.mono(),
            color = MaterialTheme.fit.muted
        )
        Text(
            cal.get(Calendar.DAY_OF_MONTH).toString(),
            style = MaterialTheme.typography.titleSmall.mono().copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

/** İnce yatay ayraç. */
@Composable
internal fun TkDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)))
}
