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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.core.VolumeRules
import com.example.core.formatDate
import com.example.core.formatDateShort
import com.example.core.formatTonnage
import com.example.core.kg
import com.example.core.trimNum
import com.example.data.PrEntity
import com.example.ui.AppViewModel
import com.example.ui.Routes
import com.example.ui.components.ChoiceChip
import com.example.ui.components.EmptyState
import com.example.ui.components.FitCard
import com.example.ui.components.GhostButton
import com.example.ui.components.OverlineText
import com.example.ui.theme.fit
import com.example.ui.theme.mono
import kotlin.math.roundToInt

/* ======================== Rekorlar: "güncel en iyiler" ======================== */

/** Gösterim sırası ve kısa etiketleri. */
private val RECORD_TYPES = listOf(
    PrEntity.TYPE_WEIGHT to "Ağırlık",
    PrEntity.TYPE_E1RM to "1RM",
    PrEntity.TYPE_VOLUME to "Hacim",
    PrEntity.TYPE_REPS to "Tekrar"
)

private fun recordTypeLabel(type: String): String =
    RECORD_TYPES.firstOrNull { it.first == type }?.second ?: "Rekor"

/** Bir rekorun ana değeri: ağırlıkta dambıl çifti "2×22,5 kg" olarak gösterilir. */
private fun recordValue(pr: PrEntity): String = when (pr.type) {
    PrEntity.TYPE_WEIGHT -> VolumeRules.label(pr.exerciseId, pr.exerciseName, pr.value)
    PrEntity.TYPE_E1RM -> pr.value.kg()
    PrEntity.TYPE_VOLUME -> formatTonnage(pr.value)
    PrEntity.TYPE_REPS -> "${pr.value.roundToInt()} tekrar"
    else -> pr.value.trimNum()
}

/** Ek bilgi: rekoru getiren set. */
private fun recordDetail(pr: PrEntity): String? = when (pr.type) {
    PrEntity.TYPE_WEIGHT -> if (pr.reps > 0) "× ${pr.reps}" else null
    PrEntity.TYPE_E1RM -> if (pr.weightKg > 0f && pr.reps > 0) "${pr.weightKg.trimNum()} × ${pr.reps}" else null
    PrEntity.TYPE_REPS -> if (pr.weightKg > 0f) "@ ${pr.weightKg.trimNum()} kg" else null
    else -> null
}

private data class ExerciseRecords(
    val exerciseId: Long,
    val name: String,
    /** Tür → güncel en iyi rekor (RECORD_TYPES sırasıyla). */
    val bests: List<PrEntity>,
    /** Tür → önceki rekora göre artış yüzdesi. */
    val gains: Map<String, Int>,
    /** Geride kalan (kırılmış) rekorlar, en yeniden eskiye. */
    val beaten: List<PrEntity>,
    val lastDate: Long
)

private fun buildExerciseRecords(prs: List<PrEntity>, type: String?): List<ExerciseRecords> =
    prs.asSequence()
        .filter { type == null || it.type == type }
        .groupBy { it.exerciseId }
        .map { (exId, list) ->
            val bests = ArrayList<PrEntity>()
            val gains = HashMap<String, Int>()
            val beaten = ArrayList<PrEntity>()
            RECORD_TYPES.forEach { (t, _) ->
                val ofType = list.filter { it.type == t }
                    .sortedWith(compareByDescending<PrEntity> { it.value }.thenByDescending { it.dateMillis })
                if (ofType.isNotEmpty()) {
                    val best = ofType.first()
                    bests.add(best)
                    val prev = ofType.drop(1).maxByOrNull { it.value }
                    if (prev != null && prev.value > 0f) {
                        gains[t] = ((best.value - prev.value) / prev.value * 100f).roundToInt()
                    }
                    beaten.addAll(ofType.drop(1))
                }
            }
            ExerciseRecords(
                exerciseId = exId,
                name = list.maxByOrNull { it.dateMillis }?.exerciseName ?: list.first().exerciseName,
                bests = bests,
                gains = gains,
                beaten = beaten.sortedByDescending { it.dateMillis },
                lastDate = list.maxOf { it.dateMillis }
            )
        }
        .filter { it.bests.isNotEmpty() }
        .sortedByDescending { it.lastDate }

@Composable
internal fun CurrentBestsContent(vm: AppViewModel, nav: NavHostController) {
    val prs by vm.prs.collectAsStateWithLifecycle()
    var typeFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }

    val groups = remember(prs, typeFilter) { buildExerciseRecords(prs, typeFilter) }
    val recentCount = remember(prs) {
        val since = System.currentTimeMillis() - 30L * 86_400_000L
        prs.count { it.dateMillis >= since }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "chips") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                ChoiceChip("Tümü", typeFilter == null, { typeFilter = null })
                RECORD_TYPES.forEach { (t, label) ->
                    ChoiceChip(label, typeFilter == t, { typeFilter = if (typeFilter == t) null else t }, color = MaterialTheme.fit.gold)
                }
            }
        }

        if (prs.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(
                        Icons.Default.EmojiEvents,
                        "Henüz rekor yok",
                        "Bir harekette önceki en iyi değerini geçtiğinde rekor otomatik kaydedilir. İlk seansını başlat!",
                        actionLabel = "Antrenman başlat",
                        onAction = { vm.startWorkout(null) { id -> nav.navigate("${Routes.WORKOUT}/$id") } }
                    )
                    GhostButton(
                        text = "Geçmişe git",
                        onClick = { nav.navigate(Routes.HISTORY) },
                        icon = Icons.Default.History
                    )
                }
            }
        } else if (groups.isEmpty()) {
            item(key = "empty_type") {
                EmptyState(
                    Icons.Default.EmojiEvents,
                    "Bu türde rekor yok",
                    "${recordTypeLabel(typeFilter.orEmpty())} rekoru henüz kaydedilmedi.",
                    actionLabel = "Tüm rekorlar",
                    onAction = { typeFilter = null }
                )
            }
        } else {
            item(key = "summary") {
                Text(
                    "${groups.size} hareket · ${groups.sumOf { it.bests.size }} güncel rekor" +
                        if (recentCount > 0) " · son 30 günde $recentCount yeni" else "",
                    style = MaterialTheme.typography.labelMedium.mono(),
                    color = MaterialTheme.fit.muted
                )
            }
            items(groups, key = { it.exerciseId }) { g ->
                val isOpen = expanded[g.exerciseId] == true
                ExerciseRecordCard(
                    g = g,
                    expanded = isOpen,
                    onToggle = { expanded[g.exerciseId] = !isOpen },
                    onOpenExercise = { nav.navigate("${Routes.EXERCISE}/${g.exerciseId}") }
                )
            }
        }
    }
}

@Composable
private fun ExerciseRecordCard(
    g: ExerciseRecords,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenExercise: () -> Unit
) {
    val fit = MaterialTheme.fit
    FitCard(contentPadding = PaddingValues(0.dp)) {
        // Başlık: dokununca hareket detayına gider
        Row(
            Modifier.fillMaxWidth().clickable { onOpenExercise() }.padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(fit.gold.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.EmojiEvents, null, tint = fit.gold, modifier = Modifier.size(17.dp)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    g.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Son rekor · ${formatDate(g.lastDate)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = fit.muted
                )
            }
            Icon(Icons.Default.KeyboardArrowRight, null, tint = fit.muted, modifier = Modifier.size(20.dp))
        }

        // Güncel en iyiler: tür başına bir hücre
        Row(
            Modifier.fillMaxWidth().clickable { onToggle() }.padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            g.bests.forEach { pr ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(fit.elevated)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    OverlineText(recordTypeLabel(pr.type), fit.gold)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        recordValue(pr),
                        style = (if (g.bests.size >= 4) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall)
                            .mono().copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    recordDetail(pr)?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall.mono(), color = fit.muted, maxLines = 1)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatDateShort(pr.dateMillis), style = MaterialTheme.typography.labelSmall, color = fit.muted, maxLines = 1)
                        g.gains[pr.type]?.takeIf { it > 0 }?.let { gain ->
                            Spacer(Modifier.width(4.dp))
                            Text("+%$gain", style = MaterialTheme.typography.labelSmall.mono(), color = fit.success, maxLines = 1)
                        }
                    }
                }
            }
        }

        if (g.beaten.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().clickable { onToggle() }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (expanded) "Eski rekorları gizle" else "${g.beaten.size} eski rekor",
                    style = MaterialTheme.typography.labelMedium,
                    color = fit.accent,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    null,
                    tint = fit.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (expanded) {
                Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 10.dp)) {
                    g.beaten.forEach { pr ->
                        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)))
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                recordTypeLabel(pr.type),
                                style = MaterialTheme.typography.labelMedium,
                                color = fit.muted,
                                modifier = Modifier.width(64.dp)
                            )
                            Text(
                                recordValue(pr) + (recordDetail(pr)?.let { "  $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall.mono(),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(formatDateShort(pr.dateMillis), style = MaterialTheme.typography.labelSmall, color = fit.muted)
                        }
                    }
                }
            }
        }
    }
}
