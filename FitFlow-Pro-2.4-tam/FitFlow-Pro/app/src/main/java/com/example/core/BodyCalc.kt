package com.example.core

import com.example.data.BodyMetricEntity
import kotlin.math.log10
import kotlin.math.pow

/**
 * Vücut ölçümü hesapları: kilo trendi (zaman ağırlıklı EWMA) ve US Navy yağ oranı tahmini.
 * BodyScreen ve ProfileScreen aynı trend mantığını kullanır.
 */
object BodyCalc {

    private const val DAY_MS = 86_400_000L

    /** Kilo içeren tek bir ölçüm noktası. */
    data class WeightPoint(val dateMillis: Long, val weightKg: Float)

    /** Kilo içeren kayıtlar, eskiden yeniye. */
    fun weightPoints(metrics: List<BodyMetricEntity>): List<WeightPoint> =
        metrics.asSequence()
            .filter { it.weightKg > 0f }
            .sortedBy { it.dateMillis }
            .map { WeightPoint(it.dateMillis, it.weightKg) }
            .toList()

    /**
     * Üstel ağırlıklı hareketli ortalama (≈ 7 günlük trend).
     * [alphaPerDay] günlük düzgünleştirme katsayısıdır; ölçümler arası gün sayısına göre
     * etkin katsayı 1 − (1 − α)^gün olarak büyür. Böylece seyrek tartılarda trend
     * gereksiz geride kalmaz, sık tartılarda günlük dalgalanma bastırılır.
     */
    fun ewmaTrend(points: List<WeightPoint>, alphaPerDay: Float = 0.25f): List<Float> {
        if (points.isEmpty()) return emptyList()
        val out = ArrayList<Float>(points.size)
        var t = points[0].weightKg
        out.add(t)
        for (i in 1 until points.size) {
            val days = ((points[i].dateMillis - points[i - 1].dateMillis).toDouble() / DAY_MS).coerceAtLeast(0.0)
            // Aynı gün içindeki ölçümler de en az bir günlük katsayıyla işlenir
            val effDays = if (days < 1.0) 1.0 else days
            val a = (1.0 - (1.0 - alphaPerDay).toDouble().pow(effDays)).toFloat().coerceIn(0f, 1f)
            t += a * (points[i].weightKg - t)
            out.add(t)
        }
        return out
    }

    /** Trend değişimi: son trend değeri − [days] gün önceki (veya o pencerenin başındaki) trend değeri. */
    data class TrendChange(val deltaKg: Float, val spanDays: Int)

    fun trendChange(points: List<WeightPoint>, trend: List<Float>, days: Int = 30): TrendChange? {
        if (points.size < 2 || trend.size != points.size) return null
        val lastDate = points.last().dateMillis
        val cutoff = lastDate - days * DAY_MS
        // Pencere başlangıcındaki trend: cutoff'tan önceki/eşit son nokta; yoksa ilk nokta.
        val idx = points.indexOfLast { it.dateMillis <= cutoff }.let { if (it < 0) 0 else it }
        if (idx >= points.lastIndex) return null
        val span = ((lastDate - points[idx].dateMillis) / DAY_MS).toInt().coerceAtLeast(1)
        return TrendChange(trend.last() - trend[idx], minOf(span, days))
    }

    /**
     * Son [windowDays] gündeki trend değerleri (eskiden yeniye) — sparkline için.
     * Trend tüm geçmişten hesaplanır, sadece pencere kesilir.
     */
    fun trendWindow(points: List<WeightPoint>, trend: List<Float>, windowDays: Int, now: Long = System.currentTimeMillis()): List<Float> {
        if (trend.size != points.size) return emptyList()
        val from = now - windowDays * DAY_MS
        return points.indices.filter { points[it].dateMillis >= from }.map { trend[it] }
    }

    /**
     * US Navy yağ oranı (cm). Erkek: boyun + bel; kadın: boyun + bel + kalça.
     * Geçersiz/eksik girdide null döner.
     */
    fun navyBodyFat(isMale: Boolean, heightCm: Float, neckCm: Float, waistCm: Float, hipCm: Float = 0f): Float? {
        if (heightCm <= 0f || neckCm <= 0f || waistCm <= 0f) return null
        val bf = if (isMale) {
            val d = waistCm - neckCm
            if (d <= 0f) return null
            495.0 / (1.0324 - 0.19077 * log10(d.toDouble()) + 0.15456 * log10(heightCm.toDouble())) - 450.0
        } else {
            if (hipCm <= 0f) return null
            val d = waistCm + hipCm - neckCm
            if (d <= 0f) return null
            495.0 / (1.29579 - 0.35004 * log10(d.toDouble()) + 0.22100 * log10(heightCm.toDouble())) - 450.0
        }
        if (bf.isNaN() || bf < 2.0 || bf > 70.0) return null
        return (Math.round(bf * 10.0) / 10.0).toFloat()
    }

    /** Kayıtlarda bu alanın dolu olduğu en yeni değer. */
    fun latestOf(metrics: List<BodyMetricEntity>, field: (BodyMetricEntity) -> Float): Float =
        metrics.filter { field(it) > 0f }.maxByOrNull { it.dateMillis }?.let(field) ?: 0f
}
