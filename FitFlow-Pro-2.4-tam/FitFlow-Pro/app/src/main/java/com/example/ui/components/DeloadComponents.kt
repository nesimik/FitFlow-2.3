package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.DeloadAdvisor
import com.example.core.trimNum
import com.example.data.ExerciseEntity
import com.example.data.RoutineDayEntity
import com.example.data.RoutineItemEntity
import com.example.data.WorkoutEntity
import com.example.data.WorkoutSetEntity
import com.example.ui.theme.fit

// Deload için özel sakinleştirici toparlanma renkleri (Morumsu / Ametist tonları)
val DeloadPurple = Color(0xFF9C27B0)
val DeloadPurpleDark = Color(0xFF7B1FA2)
val DeloadPurpleContainerDark = Color(0xFF281834)
val DeloadPurpleContainerLight = Color(0xFFF3E5F5)
val DeloadBorderPurple = Color(0xFFCE93D8)

/**
 * Kullanıcıya kas zorlanmasına göre 6-8 haftada bir deload öneren,
 * deload haftası kutucuğu ve otomatik dizayn butonu sunan uyarı kartı.
 */
@Composable
fun DeloadAlertBox(
    recommendation: DeloadAdvisor.DeloadRecommendation,
    hasBackup: Boolean,
    onToggleDeloadWeek: (Boolean) -> Unit,
    onOpenDesignDialog: () -> Unit,
    onRestoreOriginalRoutine: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(recommendation.isCurrentlyDeloadWeek || recommendation.shouldDeloadNow) }

    val isDeloadActive = recommendation.isCurrentlyDeloadWeek
    val containerColor = if (isDeloadActive) {
        if (MaterialTheme.colorScheme.background.run { (red + green + blue) < 1.5f }) DeloadPurpleContainerDark
        else DeloadPurpleContainerLight
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderColor = if (isDeloadActive) DeloadBorderPurple else MaterialTheme.fit.cardBorder

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Başlık ve Durum
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(DeloadPurple.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Healing,
                            contentDescription = null,
                            tint = DeloadPurple,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (isDeloadActive) "Deload Haftası Aktif" else "Deload Haftası Uyarısı",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isDeloadActive) DeloadPurple else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (recommendation.strainLevel == "Yüksek") Color(0xFFE53935).copy(alpha = 0.15f)
                                        else DeloadPurple.copy(alpha = 0.15f)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "${recommendation.currentCycleWeek}. Hafta · ${recommendation.strainLevel} Zorlanma",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (recommendation.strainLevel == "Yüksek") Color(0xFFE53935) else DeloadPurple
                                )
                            }
                        }
                        Text(
                            "Program Kararı: ${recommendation.recommendedWeek}. Haftada Deload Önerilir",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.fit.muted
                        )
                    }
                }

                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Detayları Göster",
                        tint = MaterialTheme.fit.muted
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Deload Haftası Olarak Belirten Kutucuk (Checkbox)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleDeloadWeek(!isDeloadActive) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Checkbox(
                            checked = isDeloadActive,
                            onCheckedChange = { onToggleDeloadWeek(it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = DeloadPurple,
                                checkmarkColor = Color.White
                            )
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(
                                "Bu haftayı Deload Haftası olarak belirt",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Geçmiş kayıtlarda bu haftanın kartları özel renkte görünür",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.fit.muted
                            )
                        }
                    }
                }
            }

            // Genişletilebilir Detaylar & Öneriler
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(Modifier.padding(top = 12.dp)) {
                    // Programın Zorlanma Gerekçesi
                    if (recommendation.reasonTitle.isNotBlank()) {
                        Text(
                            recommendation.reasonTitle,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    recommendation.reasonDetails.forEach { detail ->
                        Row(
                            Modifier.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text("• ", color = DeloadPurple, fontWeight = FontWeight.Bold)
                            Text(
                                detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.fit.muted
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Kullanıcının İstediği 3 Altın Deload Önerisi
                    Text(
                        "DELOAD HAFTASI ÖNERİLERİ:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
                        color = DeloadPurple
                    )
                    Spacer(Modifier.height(6.dp))

                    recommendation.adviceList.forEach { advice ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DeloadPurple.copy(alpha = 0.08f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = DeloadPurple,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    advice,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Eylem Butonları (Otomatik Dizayn Et / Orijinal Programa Dön)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onOpenDesignDialog,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DeloadPurple,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (isDeloadActive) "Deload Programını Düzenle" else "Otomatik Deload Dizaynı",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        if (hasBackup || isDeloadActive) {
                            OutlinedButton(
                                onClick = onRestoreOriginalRoutine,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Orijinale Dön", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hareket bazında önceki ağırlık ve setleri gösterip, ağırlıkları koruyarak
 * setleri yarıya indiren ve tek tıkla programa uygulayan Deload Dizayn Diyaloğu.
 */
@Composable
fun DeloadDesignDialog(
    routineName: String,
    routineDays: List<RoutineDayEntity>,
    allItems: List<RoutineItemEntity>,
    exercises: List<ExerciseEntity>,
    allSets: List<WorkoutSetEntity>,
    workouts: List<WorkoutEntity> = emptyList(),
    onDismiss: () -> Unit,
    onApplyDeload: (reduceWeightsPercent: Float, customAdjustments: Map<Long, Pair<Int, Float>>) -> Unit
) {
    var reducePercent by remember { mutableFloatStateOf(0f) } // 0f = Ağırlığı koru, 0.10f = %10 düşür
    val exerciseMap = remember(exercises) { exercises.associateBy { it.id } }
    val workoutMap = remember(workouts) { workouts.associateBy { it.id } }

    // Her item için (itemId -> (targetSets, targetWeight))
    val adjustments = remember {
        mutableStateMapOf<Long, Pair<Int, Float>>()
    }

    // İlk hesaplama: Her item'ın SADECE KENDİ GÜNÜNE ait önceki seanstaki değerlerini alıp setleri yarıya indir
    remember(allItems, allSets, workouts) {
        allItems.forEach { item ->
            val exSets = allSets.filter { s ->
                s.exerciseId == item.exerciseId &&
                s.isCompleted &&
                workoutMap[s.workoutId]?.let { w -> w.routineDayId == item.dayId && w.isFinished } == true
            }
            val lastWorkoutSets = exSets.groupBy { it.workoutId }
                .maxByOrNull { entry -> entry.value.maxOfOrNull { it.performedAt } ?: 0L }
                ?.value ?: emptyList()

            val hasPrev = lastWorkoutSets.isNotEmpty()
            val prevSetsCount = if (hasPrev) lastWorkoutSets.size else item.targetSets
            val prevWeight = if (hasPrev) {
                lastWorkoutSets.firstOrNull { it.weightKg > 0f }?.weightKg ?: item.targetWeight
            } else {
                item.targetWeight
            }

            val halvedSets = maxOf(1, (prevSetsCount + 1) / 2)
            adjustments[item.id] = Pair(halvedSets, prevWeight)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(18.dp)) {
                // Başlık
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Deload Program Dizaynı",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = DeloadPurple
                        )
                        Text(
                            "\"$routineName\" programı için otomatik deload ayarları",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.fit.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat")
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Ağırlık Seçeneği (Ağırlığı Koru vs %10 Düşür)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Ağırlık Stratejisi",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                if (reducePercent == 0f) "Ağırlıklar aynen korunuyor (Önerilen)"
                                else "Ağırlıklar %10 hafifletildi",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.fit.muted
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { reducePercent = 0f },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (reducePercent == 0f) DeloadPurple.copy(alpha = 0.15f) else Color.Transparent
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "Koru (%0)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (reducePercent == 0f) FontWeight.Bold else FontWeight.Normal,
                                        color = if (reducePercent == 0f) DeloadPurple else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            OutlinedButton(
                                onClick = { reducePercent = 0.10f },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (reducePercent > 0f) DeloadPurple.copy(alpha = 0.15f) else Color.Transparent
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "-%10 Düşür",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (reducePercent > 0f) FontWeight.Bold else FontWeight.Normal,
                                        color = if (reducePercent > 0f) DeloadPurple else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Bilgilendirici Alt Rozet
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DeloadPurple.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = DeloadPurple, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Tüm hareketlerde setler yarıya indirildi, tekrarlar 8-10 aralığına ayarlandı.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Hareket Listesi (Gün Bazında)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    routineDays.forEach { day ->
                        val dayItems = allItems.filter { it.dayId == day.id }.sortedBy { it.orderIndex }
                        if (dayItems.isNotEmpty()) {
                            item(key = "day_${day.id}") {
                                Text(
                                    day.name,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = DeloadPurple,
                                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                                )
                            }

                            items(dayItems, key = { it.id }) { item ->
                                val ex = exerciseMap[item.exerciseId]
                                val exName = if (item.customName.isNotBlank()) item.customName else (ex?.name ?: "Hareket")

                                // Önceki seans geçmişi (Sadece bu güne ait tamamlanmış seanslar)
                                val exSets = allSets.filter { s ->
                                    s.exerciseId == item.exerciseId &&
                                    s.isCompleted &&
                                    workoutMap[s.workoutId]?.let { w -> w.routineDayId == item.dayId && w.isFinished } == true
                                }
                                val lastWorkoutSets = exSets.groupBy { it.workoutId }
                                    .maxByOrNull { entry -> entry.value.maxOfOrNull { it.performedAt } ?: 0L }
                                    ?.value ?: emptyList()

                                val hasPrev = lastWorkoutSets.isNotEmpty()
                                val prevSetsCount = if (hasPrev) lastWorkoutSets.size else item.targetSets
                                val prevWeight = if (hasPrev) {
                                    lastWorkoutSets.firstOrNull { it.weightKg > 0f }?.weightKg ?: item.targetWeight
                                } else {
                                    item.targetWeight
                                }

                                val currentAdj = adjustments[item.id] ?: Pair(maxOf(1, (prevSetsCount + 1) / 2), prevWeight)
                                val currentSets = currentAdj.first
                                val currentWeight = if (reducePercent > 0f) {
                                    kotlin.math.round((currentAdj.second * 0.90f) * 2f) / 2f
                                } else {
                                    currentAdj.second
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.fit.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                exName,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            // Önceki / Hedef durum
                                            Text(
                                                if (hasPrev) {
                                                    "Önceki: $prevSetsCount set · ${if (prevWeight > 0f) "${prevWeight.trimNum()}kg" else "Vücut Ağırlığı"}"
                                                } else {
                                                    "Hedef: ${item.targetSets} set · ${if (item.targetWeight > 0f) "${item.targetWeight.trimNum()}kg" else "Vücut Ağırlığı"}"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.fit.muted
                                            )
                                        }

                                        Spacer(Modifier.height(8.dp))

                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Deload Set Ayarı
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    "Set:",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                OutlinedButton(
                                                    onClick = {
                                                        val newS = maxOf(1, currentSets - 1)
                                                        adjustments[item.id] = Pair(newS, currentAdj.second)
                                                    },
                                                    shape = CircleShape,
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Text("-", fontWeight = FontWeight.Bold)
                                                }
                                                Text(
                                                    "$currentSets",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    modifier = Modifier.padding(horizontal = 8.dp)
                                                )
                                                OutlinedButton(
                                                    onClick = {
                                                        val newS = currentSets + 1
                                                        adjustments[item.id] = Pair(newS, currentAdj.second)
                                                    },
                                                    shape = CircleShape,
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Text("+", fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            // Deload Ağırlık Ayarı
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    "Ağırlık:",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                if (currentWeight > 0f) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            val newW = maxOf(0f, currentAdj.second - 2.5f)
                                                            adjustments[item.id] = Pair(currentSets, newW)
                                                        },
                                                        shape = CircleShape,
                                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                                        modifier = Modifier.size(30.dp)
                                                    ) {
                                                        Text("-", fontWeight = FontWeight.Bold)
                                                    }
                                                    Text(
                                                        "${currentWeight}kg",
                                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                        modifier = Modifier.padding(horizontal = 8.dp)
                                                    )
                                                    OutlinedButton(
                                                        onClick = {
                                                            val newW = currentAdj.second + 2.5f
                                                            adjustments[item.id] = Pair(currentSets, newW)
                                                        },
                                                        shape = CircleShape,
                                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                                        modifier = Modifier.size(30.dp)
                                                    ) {
                                                        Text("+", fontWeight = FontWeight.Bold)
                                                    }
                                                } else {
                                                    Text("Vücut Ağırlığı", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
                                                }
                                            }

                                            // Tekrar
                                            Text(
                                                "8-10 tk",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = DeloadPurple,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Uygula Butonu
                Button(
                    onClick = {
                        val finalAdjustments = adjustments.mapValues { entry ->
                            val currentSets = entry.value.first
                            val baseW = entry.value.second
                            val targetW = if (reducePercent > 0f) {
                                kotlin.math.round((baseW * 0.90f) * 2f) / 2f
                            } else baseW
                            Pair(currentSets, targetW)
                        }
                        onApplyDeload(reducePercent, finalAdjustments)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeloadPurple,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Programa Uygula (Ağırlıkları Koru, Setleri Yarıya İndir)",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
