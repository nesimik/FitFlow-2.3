package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.DeloadAdvisor
import com.example.core.TrainingBlock
import com.example.core.startOfWeek
import com.example.ui.theme.Palette
import com.example.ui.theme.fit
import com.example.ui.theme.mono

// Geçmiş ekranı deload haftalarını bu renklerle gösterir.
val DeloadPurple = Color(0xFF9C27B0)
val DeloadPurpleDark = Color(0xFF7B1FA2)
val DeloadPurpleContainerDark = Color(0xFF281834)
val DeloadPurpleContainerLight = Color(0xFFF3E5F5)
val DeloadBorderPurple = Color(0xFFCE93D8)

/** Kartlarda kullanılan deload vurgusu (aktif seanstaki "Deload" rozetiyle aynı). */
private val DeloadAccent = Palette.violet

/**
 * Ana ekrandaki deload kartı. Üç hâli var:
 *  - Öneri: tek satır neden, "Neden?" ayrıntısı, "Bu hafta deload yap" ve "Bu hafta öneriyi yok say" kutucuğu.
 *  - Yok sayıldı: tek satır, kutucuk işaretli; kaldırınca öneri geri gelir.
 *  - Aktif: kalan gün, seansların ne yaptığı ve "Deload'u bitir".
 * [hasBackup]: 2.8 öncesi "Deload tasarla" ile değiştirilmiş program — geri yükleme satırı.
 */
@Composable
fun DeloadCard(
    recommendation: DeloadAdvisor.DeloadRecommendation,
    hasBackup: Boolean,
    onStartDeload: () -> Unit,
    onEndDeload: () -> Unit,
    onDismissChange: (Boolean) -> Unit,
    onRestoreBackup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rec = recommendation
    val active = rec.isCurrentlyDeloadWeek
    val dismissed = rec.shouldDeloadNow && rec.dismissedThisWeek && !active
    val showRec = rec.showRecommendation

    if (!active && !showRec && !dismissed) {
        if (hasBackup) BackupRestoreCard(onRestoreBackup, modifier)
        return
    }

    val tinted = active || showRec
    FitCard(
        modifier = modifier.fillMaxWidth(),
        corner = 20.dp,
        container = if (tinted) DeloadAccent.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
        border = if (tinted) DeloadAccent.copy(alpha = 0.45f) else MaterialTheme.fit.cardBorder,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 16.dp, vertical = if (dismissed) 6.dp else 14.dp
        )
    ) {
        when {
            active -> ActiveContent(rec, onEndDeload)
            showRec -> RecommendationContent(rec, onStartDeload, onDismissChange)
            else -> DismissRow(checked = true, onChange = onDismissChange, subtitle = "Pazartesi yeniden değerlendirilir")
        }
        if (hasBackup) {
            Spacer(Modifier.height(8.dp))
            BackupRestoreRow(onRestoreBackup)
        }
    }
}

@Composable
private fun DeloadHeader(title: String, trailing: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(DeloadAccent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Healing, null, tint = DeloadAccent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(trailing, style = MaterialTheme.typography.labelMedium.mono(), color = MaterialTheme.fit.muted)
    }
}

@Composable
private fun RecommendationContent(
    rec: DeloadAdvisor.DeloadRecommendation,
    onStartDeload: () -> Unit,
    onDismissChange: (Boolean) -> Unit
) {
    var why by remember { mutableStateOf(false) }
    DeloadHeader("Deload önerisi", "HAFTA ${rec.currentCycleWeek}")
    Spacer(Modifier.height(8.dp))
    Text(rec.reasonTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)

    if (rec.reasonDetails.isNotEmpty()) {
        Row(
            Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).clickable { why = !why }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Neden?", style = MaterialTheme.typography.labelLarge, color = DeloadAccent)
            Icon(
                if (why) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                null, tint = DeloadAccent, modifier = Modifier.size(18.dp)
            )
        }
        AnimatedVisibility(why) {
            Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rec.reasonDetails.forEach { d ->
                    Row {
                        Text("•  ", color = DeloadAccent, style = MaterialTheme.typography.bodySmall)
                        Text(d, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    Text(
        "Deload haftasında: ${DeloadAdvisor.DELOAD_SUMMARY}. Seanslar kendiliğinden ayarlanır.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.fit.muted
    )
    Spacer(Modifier.height(12.dp))
    AccentButton("Bu hafta deload yap", onStartDeload, Modifier.fillMaxWidth(), color = DeloadAccent)
    Spacer(Modifier.height(4.dp))
    DismissRow(checked = false, onChange = onDismissChange, subtitle = null)
}

@Composable
private fun DismissRow(checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String?) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(checkedColor = DeloadAccent, checkmarkColor = Color.White)
        )
        Column(Modifier.weight(1f)) {
            Text(
                if (checked) "Bu haftanın deload önerisi yok sayıldı" else "Bu hafta öneriyi yok say",
                style = MaterialTheme.typography.bodyMedium,
                color = if (checked) MaterialTheme.fit.muted else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
            }
        }
    }
}

@Composable
private fun ActiveContent(rec: DeloadAdvisor.DeloadRecommendation, onEndDeload: () -> Unit) {
    val daysLeft = remember {
        val now = System.currentTimeMillis()
        val end = startOfWeek(now) + 7 * 86_400_000L
        ((end - now + 86_399_999L) / 86_400_000L).toInt().coerceIn(1, 7)
    }
    DeloadHeader("Deload haftası", if (daysLeft == 1) "SON GÜN" else "$daysLeft GÜN KALDI")
    Spacer(Modifier.height(8.dp))
    Text(
        "${DeloadAdvisor.DELOAD_SUMMARY}. Seanslar kendiliğinden ayarlanıyor.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(4.dp))
    Text(DeloadAdvisor.DELOAD_EFFORT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
    Spacer(Modifier.height(12.dp))
    GhostButton("Deload'u bitir", onEndDeload, Modifier.fillMaxWidth(), color = DeloadAccent)
}

@Composable
private fun BackupRestoreRow(onRestore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onRestore() }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Refresh, null, tint = MaterialTheme.fit.warning, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "Programın eski \"Deload tasarla\" ile değiştirilmiş. Orijinalini geri yükle",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.fit.warning
        )
    }
}

@Composable
private fun BackupRestoreCard(onRestore: () -> Unit, modifier: Modifier) {
    FitCard(
        modifier = modifier.fillMaxWidth(),
        corner = 20.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) { BackupRestoreRow(onRestore) }
}

/* ------------------------------ Antrenman bloğu ----------------------------- */

/** Program ekranında blok durumu: "Blok · hafta 3/5 · RIR 1-2 · deload 6. hafta". */
@Composable
fun BlockStatusCard(rec: DeloadAdvisor.DeloadRecommendation, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val deload = rec.isCurrentlyDeloadWeek
    val effort = TrainingBlock.effort(rec.currentCycleWeek, rec.loadWeeks, deload)
    FitCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        corner = 20.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ViewWeek, null, tint = MaterialTheme.fit.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                OverlineText(if (rec.blockLoadWeeks > 0) "Antrenman bloğu · ${rec.blockLoadWeeks}+1" else "Antrenman bloğu · otomatik")
                Spacer(Modifier.height(2.dp))
                Text(
                    if (deload) "Deload haftası"
                    else "Hafta ${rec.currentCycleWeek} / ${rec.loadWeeks} · deload ${rec.recommendedWeek}. hafta",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Badge(effort.rir, if (deload) DeloadAccent else MaterialTheme.fit.accent)
        }
        Spacer(Modifier.height(4.dp))
        Text(effort.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
    }
}

/** Blok uzunluğu seçimi. 0 = otomatik (yorgunluğa göre 6-8. hafta). */
@Composable
fun BlockSettingsDialog(current: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var sel by remember { mutableIntStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Antrenman bloğu", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Kaç hafta yüklenip ardından 1 hafta deload yapacağını seç. Performansın düşerse öneri erkene çekilir; istemezsen o hafta yok sayabilirsin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.fit.muted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Otomatik", sel == 0, { sel = 0 })
                    listOf(3, 4).forEach { w -> ChoiceChip("$w+1", sel == w, { sel = w }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 6, 7).forEach { w -> ChoiceChip("$w+1", sel == w, { sel = w }) }
                }
                Text(
                    if (sel == 0) "Otomatik: yorgunluk göstergelerine göre deload 6-8. haftaya hedeflenir."
                    else "$sel hafta yüklenme, ${sel + 1}. hafta deload. Hedef zorluk RIR 2-3'ten 0-1'e iner.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(sel) }) {
                Text("Kaydet", color = MaterialTheme.fit.accent, style = MaterialTheme.typography.titleMedium)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç", color = MaterialTheme.fit.muted) } }
    )
}
