package com.example.ui.components

import androidx.compose.ui.graphics.Color
import com.example.core.LoadStatus
import com.example.core.RecoveryState

/**
 * Kas haritasının tek renk sözlüğü. Tüm ekranlar (Bugün, İlerleme, açıklamalar) buradan
 * okur; böylece aynı durum her yerde aynı renkte görünür.
 *
 * Skala tek yönde "dolar": gri (hiç) → soluk açık mavi (çok az) → dolgun mavi (az) →
 * yeşil (ideal) → amber (yüksek) → kırmızı (aşırı). Renk ne kadar dolgunsa kas o kadar
 * çalışılmış demektir; "çok az" bilerek griye yakın ve soluk tutulur.
 * Hiç çalışılmayan kas renksiz kalır (haritanın nötr grisi).
 */
object MuscleColors {
    val low = Color(0xFF7F9CC0)        // Çok az — soluk açık mavi
    val below = Color(0xFF3E72E6)      // Az — dolgun mavi (vurgu rengi buz mavisinden ayrı)
    val optimal = Color(0xFF2FB380)    // İdeal — yeşil
    val high = Color(0xFFE0A33A)       // Yüksek — amber
    val excessive = Color(0xFFE0584F)  // Aşırı — kırmızı

    val fresh = Color(0xFF2FB380)      // Dinç
    val recovering = Color(0xFFD9B23A) // Toparlanıyor
    val fatigued = Color(0xFFE0584F)   // Yorgun

    /** Harita dolgusu; NONE için null (nötr gri çizilir). */
    fun forStatus(status: LoadStatus): Color? = when (status) {
        LoadStatus.NONE -> null
        LoadStatus.LOW -> low
        LoadStatus.BELOW -> below
        LoadStatus.OPTIMAL -> optimal
        LoadStatus.HIGH -> high
        LoadStatus.EXCESSIVE -> excessive
    }

    fun forRecovery(state: RecoveryState): Color = when (state) {
        RecoveryState.FRESH -> fresh
        RecoveryState.RECOVERING -> recovering
        RecoveryState.FATIGUED -> fatigued
    }

    val loadLegend: List<Pair<Color, String>> = listOf(
        low to "Çok az", below to "Az", optimal to "İdeal", high to "Yüksek", excessive to "Aşırı"
    )

    val recoveryLegend: List<Pair<Color, String>> = listOf(
        fresh to "Dinç", recovering to "Toparlanıyor", fatigued to "Yorgun"
    )
}
