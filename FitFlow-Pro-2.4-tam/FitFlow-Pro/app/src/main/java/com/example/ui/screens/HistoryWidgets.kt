package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.Analytics
import com.example.core.formatDateShort
import com.example.core.formatDurationShort
import com.example.core.formatMonthYear
import com.example.core.formatTime
import com.example.core.formatTonnage
import com.example.core.formatWeekday
import com.example.core.startOfDay
import com.example.data.WorkoutEntity
import com.example.data.WorkoutSetEntity
import com.example.ui.components.Badge
import com.example.ui.components.DeloadBorderPurple
import com.example.ui.components.DeloadPurple
import com.example.ui.components.DeloadPurpleContainerDark
import com.example.ui.components.DeloadPurpleContainerLight
import com.example.ui.components.FitCard
import com.example.ui.components.OverlineText
import com.example.ui.theme.fit
import com.example.ui.theme.mono
import java.util.Calendar

/* ============================ Geçmiş: ay takvimi ============================ */

private val WEEKDAY_SHORT = listOf("Pt", "Sa", "Ça", "Pe", "Cu", "Ct", "Pz")

/** Görüntülenen ayın ilk gününün (00:00) zamanı; [monthOffset] 0 = bu ay, -1 = geçen ay. */
private fun monthStartMillis(monthOffset: Int): Long {
    val c = Calendar.getInstance().apply {
        timeInMillis = startOfDay(System.currentTimeMillis())
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, monthOffset)
    }
    return startOfDay(c.timeInMillis)
}

/**
 * Kompakt ay takvimi ısı haritası. Her gün o günün hacmiyle (ayın en yoğun gününe göre) tonlanır.
 * Antrenman olan bir güne dokunmak o günü seçer / seçimi kaldırır.
 */
@Composable
internal fun HistoryMonthCalendar(
    monthOffset: Int,
    onMonthOffset: (Int) -> Unit,
    dayVolumes: Map<Long, Float>,
    dayCounts: Map<Long, Int>,
    selectedDay: Long?,
    onDayClick: (Long) -> Unit
) {
    val fit = MaterialTheme.fit
    val monthStart = remember(monthOffset) { monthStartMillis(monthOffset) }
    val today = remember { startOfDay(System.currentTimeMillis()) }
    // Ayın günleri (00:00 zamanları) ve ilk günün Pazartesi tabanlı sütunu
    val days: List<Long> = remember(monthStart) {
        val c = Calendar.getInstance().apply { timeInMillis = monthStart }
        val count = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        (1..count).map { d ->
            val cc = Calendar.getInstance().apply { timeInMillis = monthStart; set(Calendar.DAY_OF_MONTH, d) }
            startOfDay(cc.timeInMillis)
        }
    }
    val leading = remember(monthStart) {
        val dow = Calendar.getInstance().apply { timeInMillis = monthStart }.get(Calendar.DAY_OF_WEEK)
        (dow + 5) % 7 // Pazartesi = 0 … Pazar = 6
    }
    val maxVol = days.maxOfOrNull { dayVolumes[it] ?: 0f } ?: 0f
    val monthSessions = days.sumOf { dayCounts[it] ?: 0 }
    val monthVolume = days.sumOf { (dayVolumes[it] ?: 0f).toDouble() }.toFloat()
    val activeDays = days.count { (dayCounts[it] ?: 0) > 0 }

    FitCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonthOffset(monthOffset - 1) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Önceki ay", tint = fit.muted)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                OverlineText(formatMonthYear(monthStart), fit.accent)
                Text(
                    if (monthSessions == 0) "Bu ay seans yok"
                    else "$monthSessions seans · $activeDays gün · ${formatTonnage(monthVolume)}",
                    style = MaterialTheme.typography.labelSmall.mono(),
                    color = fit.muted
                )
            }
            IconButton(
                onClick = { if (monthOffset < 0) onMonthOffset(monthOffset + 1) },
                enabled = monthOffset < 0,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "Sonraki ay",
                    tint = if (monthOffset < 0) fit.muted else fit.muted.copy(alpha = 0.3f)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            WEEKDAY_SHORT.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = fit.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val totalCells = leading + days.size
        val rows = (totalCells + 6) / 7
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (col in 0 until 7) {
                        val idx = r * 7 + col - leading
                        if (idx < 0 || idx >= days.size) {
                            Spacer(Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val day = days[idx]
                            val count = dayCounts[day] ?: 0
                            val vol = dayVolumes[day] ?: 0f
                            val intensity = when {
                                count == 0 -> 0f
                                maxVol <= 0f -> 0.4f
                                else -> (0.25f + 0.75f * (vol / maxVol)).coerceIn(0.25f, 1f)
                            }
                            CalendarDayCell(
                                dayNumber = idx + 1,
                                intensity = intensity,
                                hasWorkout = count > 0,
                                isToday = day == today,
                                isFuture = day > today,
                                selected = selectedDay == day,
                                onClick = { onDayClick(day) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text("Az", style = MaterialTheme.typography.labelSmall, color = fit.muted)
            Spacer(Modifier.width(4.dp))
            listOf(0.25f, 0.5f, 0.75f, 1f).forEach { a ->
                Box(
                    Modifier.padding(horizontal = 1.dp).size(10.dp).clip(RoundedCornerShape(3.dp))
                        .background(fit.accent.copy(alpha = 0.12f + 0.68f * a))
                )
            }
            Spacer(Modifier.width(4.dp))
            Text("Çok hacim", style = MaterialTheme.typography.labelSmall, color = fit.muted)
        }
    }
}

@Composable
private fun CalendarDayCell(
    dayNumber: Int,
    intensity: Float,
    hasWorkout: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fit = MaterialTheme.fit
    val shape = RoundedCornerShape(8.dp)
    val bg = if (hasWorkout) fit.accent.copy(alpha = 0.12f + 0.68f * intensity) else fit.elevated.copy(alpha = 0.55f)
    val textColor = when {
        hasWorkout && intensity >= 0.6f -> fit.onAccent
        hasWorkout -> MaterialTheme.colorScheme.onSurface
        isFuture -> fit.muted.copy(alpha = 0.45f)
        else -> fit.muted
    }
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.onSurface
        isToday -> fit.accent
        else -> Color.Transparent
    }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(bg)
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            .then(if (hasWorkout) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$dayNumber",
            style = MaterialTheme.typography.labelSmall.mono().copy(
                fontWeight = if (hasWorkout || isToday) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = textColor
        )
    }
}

/* ============================ Geçmiş: seans kartı ============================ */

@Composable
internal fun HistoryWorkoutCard(
    w: WorkoutEntity,
    sets: List<WorkoutSetEntity>,
    volume: Float,
    hasPr: Boolean,
    isDeloadCard: Boolean,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onEditDate: () -> Unit,
    onRepeat: () -> Unit,
    onToggleDeload: () -> Unit,
    onDelete: () -> Unit
) {
    val fit = MaterialTheme.fit
    val effectiveSets = remember(sets) { sets.count { Analytics.isEffectiveSet(it) } }
    val exCount = remember(sets) { sets.map { it.exerciseOrder }.distinct().size }
    val container = if (isDeloadCard) {
        if (fit.isDark) DeloadPurpleContainerDark else DeloadPurpleContainerLight
    } else {
        MaterialTheme.colorScheme.surface
    }
    val border = when {
        isDeloadCard -> DeloadBorderPurple
        hasPr -> fit.gold.copy(alpha = 0.35f)
        else -> fit.cardBorder
    }
    val stripe: Color? = when {
        hasPr -> fit.gold
        isDeloadCard -> DeloadPurple
        else -> null
    }

    FitCard(
        onClick = onClick,
        container = container,
        border = border,
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            if (stripe != null) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(stripe))
            }
            Column(Modifier.weight(1f).padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        OverlineText(
                            "${formatWeekday(w.startedAt)} · ${formatDateShort(w.startedAt)} · ${formatTime(w.startedAt)}",
                            if (isDeloadCard) DeloadPurple else fit.muted
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            w.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isDeloadCard) DeloadPurple else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (w.feeling > 0) {
                        Text(feelingEmoji(w.feeling), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                    WorkoutOverflowMenu(
                        isDeload = w.isDeload,
                        onCopy = onCopy,
                        onEditDate = onEditDate,
                        onRepeat = onRepeat,
                        onToggleDeload = onToggleDeload,
                        onDelete = onDelete
                    )
                }
                if (hasPr || isDeloadCard) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (hasPr) Badge("Rekor", fit.gold, icon = Icons.Default.EmojiEvents)
                        if (isDeloadCard) Badge("DELOAD", DeloadPurple)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().padding(end = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardMetric(formatDurationShort(w.durationSeconds), "süre", Modifier.weight(1f))
                    CardMetric("$exCount", "hareket", Modifier.weight(1f))
                    CardMetric("$effectiveSets", "set", Modifier.weight(1f))
                    CardMetric(if (volume > 0f) formatTonnage(volume) else "—", "hacim", Modifier.weight(1.2f))
                }
                if (w.notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        w.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = fit.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CardMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall.mono().copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted, maxLines = 1)
    }
}

/** Seans kartındaki ⋮ menüsü: Kopyala / Tarihi değiştir / Bu seansı tekrarla / Deload / Sil. */
@Composable
internal fun WorkoutOverflowMenu(
    isDeload: Boolean,
    onCopy: () -> Unit,
    onEditDate: () -> Unit,
    onRepeat: () -> Unit,
    onToggleDeload: () -> Unit,
    onDelete: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val fit = MaterialTheme.fit
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "Seçenekler", tint = fit.muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Kopyala") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = fit.accent) },
                onClick = { open = false; onCopy() }
            )
            DropdownMenuItem(
                text = { Text("Tarihi değiştir") },
                leadingIcon = { Icon(Icons.Default.Event, null, tint = fit.accent) },
                onClick = { open = false; onEditDate() }
            )
            DropdownMenuItem(
                text = { Text("Bu seansı tekrarla") },
                leadingIcon = { Icon(Icons.Default.Replay, null, tint = fit.accent) },
                onClick = { open = false; onRepeat() }
            )
            DropdownMenuItem(
                text = { Text(if (isDeload) "Deload işaretini kaldır" else "Deload olarak işaretle") },
                leadingIcon = { Icon(Icons.Default.Healing, null, tint = DeloadPurple) },
                onClick = { open = false; onToggleDeload() }
            )
            DropdownMenuItem(
                text = { Text("Sil", color = fit.danger) },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = fit.danger) },
                onClick = { open = false; onDelete() }
            )
        }
    }
}
