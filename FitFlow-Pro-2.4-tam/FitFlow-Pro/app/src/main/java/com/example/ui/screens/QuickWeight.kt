package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.trimNum
import com.example.data.BodyMetricEntity
import com.example.ui.AppViewModel
import com.example.ui.components.AccentButton
import com.example.ui.theme.fit
import com.example.ui.theme.mono
import kotlin.math.roundToInt

/**
 * 2.39: Hızlı kilo girişi — son kilo ile dolu, ±0,1 / ±1 düğmeleri, tek dokunuşla kayıt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickWeightSheet(vm: AppViewModel, onAllMetrics: () -> Unit, onDismiss: () -> Unit) {
    val metrics by vm.bodyMetrics.collectAsStateWithLifecycle()
    val fallback by vm.settings.weightKg.collectAsStateWithLifecycle()
    val last = metrics.filter { it.weightKg > 0f }.maxByOrNull { it.dateMillis }
    var w by remember { mutableFloatStateOf(last?.weightKg ?: fallback) }
    fun adj(d: Float) { w = ((w + d) * 10f).roundToInt() / 10f }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Bugünkü kilon", style = MaterialTheme.typography.titleMedium)
            Text(
                last?.let { "Son: ${it.weightKg.trimNum()} kg · ${com.example.core.formatDateShort(it.dateMillis)}" } ?: "İlk kilo kaydın",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.fit.muted
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "${w.trimNum()} kg",
                style = MaterialTheme.typography.displayMedium.mono().copy(fontWeight = FontWeight.SemiBold, fontSize = 52.sp),
                color = MaterialTheme.fit.accent
            )
            last?.let {
                val d = w - it.weightKg
                if (kotlin.math.abs(d) >= 0.05f) Text(
                    "${if (d > 0) "▲" else "▼"} ${kotlin.math.abs(d).trimNum()} kg",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.fit.muted
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(-1f to "−1", -0.1f to "−0,1", 0.1f to "+0,1", 1f to "+1").forEach { (d, l) ->
                    Box(
                        Modifier.size(width = 70.dp, height = 52.dp).clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.fit.elevated).clickable { adj(d) },
                        contentAlignment = Alignment.Center
                    ) { Text(l, style = MaterialTheme.typography.titleMedium.mono()) }
                }
            }
            Spacer(Modifier.height(20.dp))
            AccentButton("Kaydet", {
                if (w > 0f) vm.saveBodyMetric(BodyMetricEntity(weightKg = w))
                onDismiss()
            }, Modifier.fillMaxWidth(), Icons.Default.Check)
            Spacer(Modifier.height(10.dp))
            Text(
                "Tüm ölçümler (bel, yağ oranı…) →", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.fit.accent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onDismiss(); onAllMetrics() }.padding(8.dp)
            )
        }
    }
}
