package com.example.core

import com.example.data.ExerciseEntity
import kotlin.math.sqrt

/**
 * Alternatif hareket önerisi: makine doluysa ya da hareket yapılamıyorsa, aynı kasları
 * benzer oranda çalıştıran hareketleri sıralar.
 *
 * Puan = kas katkı benzerliği (kosinüs, 0..1) + aynı hareket türü (bileşik/izole) bonusu
 * + daha önce yapılmış olma bonusu + favori bonusu. Aynı ekipman hafif cezalandırılır
 * (genelde sebep o ekipmanın dolu olmasıdır).
 */
object Alternatives {

    data class Suggestion(
        val exercise: ExerciseEntity,
        val score: Float,
        /** "Göğüs, Triceps · Dambıl · daha önce yaptın" */
        val reason: String,
        /** Tahmini başlangıç ağırlığı (geçmiş yoksa ve dönüştürülebiliyorsa), 0 = yok. */
        val weightHint: Float
    )

    fun find(
        target: ExerciseEntity,
        library: List<ExerciseEntity>,
        /** Hareket id → son çalışma ağırlığı (geçmişi olanlar). */
        lastWeights: Map<Long, Float>,
        targetWeight: Float,
        profile: LoadingProfile,
        limit: Int = 6
    ): List<Suggestion> {
        val tw = weights(target)
        if (tw.isEmpty()) return emptyList()
        val tScheme = RepScheme.classify(target.name, target.muscleGroup, target.equipment, target.trackingType)
        val tIsolation = tScheme == RepScheme.ISOLATION

        return library.asSequence()
            .filter { it.id != target.id && it.muscleGroup != Muscles.CARDIO }
            .filter { it.trackingType == target.trackingType ||
                (it.trackingType != ExerciseEntity.TRACK_DURATION && target.trackingType != ExerciseEntity.TRACK_DURATION) }
            // Birincil kası olmayan (ısınma / mobilite) hareketler önerilmez.
            .filter { MuscleMap.resolve(it.name, it.muscleGroup, it.secondaryMuscles).primary.isNotEmpty() }
            .mapNotNull { ex ->
                val sim = cosine(tw, weights(ex))
                if (sim < 0.55f) return@mapNotNull null
                val scheme = RepScheme.classify(ex.name, ex.muscleGroup, ex.equipment, ex.trackingType)
                val sameKind = (scheme == RepScheme.ISOLATION) == tIsolation
                val done = lastWeights.containsKey(ex.id)
                var score = sim
                if (sameKind) score += 0.15f
                if (done) score += 0.12f
                if (ex.isFavorite) score += 0.05f
                if (ex.equipment == target.equipment) score -= 0.08f

                // Ağırlık yalnızca hareket neredeyse aynıysa çevrilir (ör. barbell bench ↔ dambıl bench).
                val hint = if (done || sim < 0.9f || !sameKind) 0f
                    else convertWeight(targetWeight, target.equipment, ex.equipment, profile)
                val reason = buildList {
                    add(MuscleMap.summary(ex.name, ex.muscleGroup).substringBefore("  ·").trim())
                    add(ex.equipment)
                    if (done) add("daha önce yaptın")
                    else if (hint > 0f) add("~${hint.trimNum()} kg tahmini")
                }.filter { it.isNotBlank() && it != "—" }.joinToString(" · ")
                Suggestion(ex, score, reason, hint)
            }
            .sortedByDescending { it.score }
            .toList()
            .let { diversify(it, limit) }
    }

    /** Önce her ekipmandan en iyi aday (çeşitlilik), sonra kalan en yüksek puanlılar. */
    private fun diversify(sorted: List<Suggestion>, limit: Int): List<Suggestion> {
        val top = sorted.firstOrNull()?.score ?: return emptyList()
        val good = sorted.filter { it.score >= top - 0.35f && it.exercise.equipment != "Bant" }
        val firsts = good.filter { it.score >= top - 0.12f }
            .groupBy { it.exercise.equipment }.values.map { it.first() }
            .sortedByDescending { it.score }
        val picked = LinkedHashSet<Suggestion>()
        firsts.take(limit).forEach { picked += it }
        good.forEach { if (picked.size < limit) picked += it }
        return picked.sortedByDescending { it.score }
    }

    private fun weights(e: ExerciseEntity): Map<String, Float> =
        MuscleMap.resolve(e.name, e.muscleGroup, e.secondaryMuscles).weights()

    private fun cosine(a: Map<String, Float>, b: Map<String, Float>): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        var dot = 0f
        a.forEach { (k, v) -> dot += v * (b[k] ?: 0f) }
        val na = sqrt(a.values.sumOf { (it * it).toDouble() }).toFloat()
        val nb = sqrt(b.values.sumOf { (it * it).toDouble() }).toFloat()
        return if (na == 0f || nb == 0f) 0f else dot / (na * nb)
    }

    /**
     * Kaba ağırlık dönüşümü. Yalnızca güvenilir çiftler: barbell ↔ dambıl (tek dambıl).
     * Makine / kablo ağırlıkları markadan markaya çok değiştiği için tahmin edilmez.
     */
    fun convertWeight(w: Float, from: String, to: String, profile: LoadingProfile): Float {
        if (w <= 0f) return 0f
        val f = loadKindOf(from)
        val t = loadKindOf(to)
        val raw = when {
            f == LoadKind.BARBELL && t == LoadKind.DUMBBELL -> w * 0.4f
            f == LoadKind.DUMBBELL && t == LoadKind.BARBELL -> w * 2.2f
            else -> return 0f
        }
        val r = profile.round(raw, t)
        return if (t == LoadKind.BARBELL) maxOf(r, profile.barKg) else r
    }
}
