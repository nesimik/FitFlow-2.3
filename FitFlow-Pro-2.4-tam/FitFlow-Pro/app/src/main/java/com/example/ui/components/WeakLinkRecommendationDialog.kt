package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.RecommendationActionType
import com.example.core.WeakLinkAdvisor
import com.example.core.WeakLinkRecommendation
import com.example.ui.AppViewModel
import com.example.ui.theme.fit

@Composable
fun WeakLinkRecommendationDialog(
    vm: AppViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val activeRoutine by vm.activeRoutine.collectAsStateWithLifecycle()
    val routineDays by vm.routineDays.collectAsStateWithLifecycle()
    val allItems by vm.allItems.collectAsStateWithLifecycle()
    val weeklyMuscleLoads by vm.weeklyMuscleLoads.collectAsStateWithLifecycle()

    // Canlı senkronize öneri listesi
    val recommendations by remember(exercises, activeRoutine, routineDays, allItems, weeklyMuscleLoads) {
        derivedStateOf {
            WeakLinkAdvisor.analyzeAndRecommend(
                exercises = exercises,
                activeRoutine = activeRoutine,
                routineDays = routineDays,
                allItems = allItems,
                muscleLoads = weeklyMuscleLoads
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.fit.accent,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Akıllı Zayıf Halka Önerileri",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = activeRoutine?.let { "Aktif Program: ${it.name}" } ?: "Aktif Program Seçilmedi",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.fit.accent
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
            ) {
                InfoNote(
                    text = "Bu öneriler makineye bağlı kalmadan dambıl, bar veya vücut ağırlığı ile zayıf halkalarını geliştirmek için programını otomatik analiz eder."
                )

                Spacer(Modifier.height(12.dp))

                if (activeRoutine == null) {
                    EmptyState(
                        icon = Icons.Default.Warning,
                        title = "Aktif Program Bulunamadı",
                        text = "Zayıf halka önerilerini programa uygulayabilmek için lütfen önce Programlar ekranından bir aktif program seçin."
                    )
                } else if (recommendations.isEmpty()) {
                    FitCard(
                        container = MaterialTheme.fit.success.copy(alpha = 0.08f),
                        border = MaterialTheme.fit.success.copy(alpha = 0.3f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.fit.success,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Mükemmel Kas Dengesi!",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.fit.success
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Aktif programınızdaki tüm kas grupları hedeflenen haftalık set aralığında. Zayıf kalan bir kas tespit edilmedi.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.fit.muted
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(recommendations, key = { it.id }) { rec ->
                            RecommendationCard(
                                rec = rec,
                                onApply = {
                                    if (rec.actionType == RecommendationActionType.SWAP && rec.itemToSwap != null) {
                                        vm.deleteItem(rec.itemToSwap)
                                        vm.addItem(rec.targetDay.id, rec.recommendedExercise.id)
                                        Toast.makeText(
                                            context,
                                            "‘${rec.exerciseToSwap?.name ?: "Eski hareket"}’ çıkarıldı, yerine ‘${rec.recommendedExercise.name}’ eklendi!",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        vm.addItem(rec.targetDay.id, rec.recommendedExercise.id)
                                        Toast.makeText(
                                            context,
                                            "‘${rec.recommendedExercise.name}’ ${rec.targetDay.name} gününe eklendi!",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat", color = MaterialTheme.fit.muted, style = MaterialTheme.typography.titleMedium)
            }
        }
    )
}

@Composable
private fun RecommendationCard(
    rec: WeakLinkRecommendation,
    onApply: () -> Unit
) {
    FitCard(
        container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = MaterialTheme.fit.accent.copy(alpha = 0.35f)
    ) {
        // Üst Başlık & Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Badge(
                text = "Zayıf Halka: ${rec.weakMuscleLabel}",
                color = MaterialTheme.fit.warning
            )
            Badge(
                text = if (rec.actionType == RecommendationActionType.SWAP) "🔄 Hareket Değişimi" else "➕ Yeni Hareket Ekle",
                color = if (rec.actionType == RecommendationActionType.SWAP) MaterialTheme.fit.gold else MaterialTheme.fit.success
            )
        }

        Spacer(Modifier.height(10.dp))

        // Önerilen Hareket
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.fit.accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = rec.recommendedExercise.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        Spacer(Modifier.height(6.dp))

        // Ekipman & Hedef Gün
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChoiceChip(
                selected = true,
                label = "Ekipman: ${rec.equipment}",
                onClick = {},
                color = MaterialTheme.fit.accent
            )
            ChoiceChip(
                selected = true,
                label = "Hedef Gün: ${rec.targetDay.name}",
                onClick = {},
                color = MaterialTheme.fit.elevated
            )
        }

        Spacer(Modifier.height(10.dp))

        // Değişim Callout Kutusu (Varsa)
        if (rec.actionType == RecommendationActionType.SWAP && rec.exerciseToSwap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.fit.danger.copy(alpha = 0.1f))
                    .padding(10.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = MaterialTheme.fit.danger,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Yoğun Gün & Çıkarılabilecek Hareket:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.fit.danger
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "❌ Çıkarılacak: ${rec.exerciseToSwap.name} (${rec.exerciseToSwap.equipment})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "✔ Eklenecek: ${rec.recommendedExercise.name} (${rec.equipment})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.fit.success
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // Açıklama / Gerekçe Metni
        Text(
            text = rec.reasoning,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.fit.muted
        )

        Spacer(Modifier.height(12.dp))

        // Uygula Butonu
        AccentButton(
            text = if (rec.actionType == RecommendationActionType.SWAP) "Programa Değiştirerek Uygula" else "'${rec.targetDay.name}' Gününü Güncelle",
            onClick = onApply,
            icon = if (rec.actionType == RecommendationActionType.SWAP) Icons.Default.SwapHoriz else Icons.Default.Add,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
