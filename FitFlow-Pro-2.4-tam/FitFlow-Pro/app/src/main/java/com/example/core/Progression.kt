package com.example.core

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/* ==========================================================================
 * FitFlow Pro — Progresyon Motoru
 *
 * Double progression (ör. 3x10 → 3x12 → ağırlık artır → 3x10) mantığını
 * RPE korumasıyla uygular. Sonuç bir "reçete"dir: bugün hangi ağırlıkla,
 * her sette kaç tekrar hedefleneceği ve neden.
 *
 * Tamamen saf Kotlin — Android'e bağımlı değil, birim testi yapılabilir.
 * ========================================================================== */

/** Ekipmana göre yükleme türü. Artış adımı bu türe göre seçilir. */
enum class LoadKind { BARBELL, DUMBBELL, MACHINE, BODYWEIGHT, OTHER }

fun loadKindOf(equipment: String): LoadKind = when (equipment.trim()) {
    "Barbell" -> LoadKind.BARBELL
    "Dumbbell", "Kettlebell" -> LoadKind.DUMBBELL
    "Makine", "Kablo" -> LoadKind.MACHINE
    "Vücut Ağırlığı" -> LoadKind.BODYWEIGHT
    else -> LoadKind.OTHER
}

/** Salondaki gerçek ekipmana göre ulaşılabilir artış adımları. */
data class LoadingProfile(
    val barKg: Float = 20f,
    val barbellStep: Float = 2.5f,
    val dumbbellStep: Float = 2f,
    val machineStep: Float = 5f
) {
    fun step(kind: LoadKind): Float = when (kind) {
        LoadKind.BARBELL, LoadKind.OTHER -> barbellStep
        LoadKind.DUMBBELL -> dumbbellStep
        LoadKind.MACHINE -> machineStep
        LoadKind.BODYWEIGHT -> 0f
    }.coerceAtLeast(0f)
}

/** Geçmiş bir seansta bu hareketin tek bir çalışma seti. */
data class LoggedSet(val weight: Float, val reps: Int, val rpe: Float = 0f)

/** Bir hareketin geçmişteki bir seansı (yalnızca çalışma setleri). */
data class SessionLog(val dateMillis: Long, val sets: List<LoggedSet>) {
    val bestE1rm: Float get() = sets.maxOfOrNull { Calc.e1rm(it.weight, it.reps) } ?: 0f
    val volume: Float get() = sets.sumOf { (it.weight * it.reps).toDouble() }.toFloat()
    val totalReps: Int get() = sets.sumOf { it.reps }
}

enum class ProgressAction(val label: String) {
    FIRST("İlk kayıt"),
    INCREASE("Ağırlık artır"),
    REPS("Tekrar artır"),
    HOLD("Koru"),
    DECREASE("Hafiflet"),
    DELOAD("Deload")
}

data class Prescription(
    val action: ProgressAction,
    /** Bugünkü çalışma ağırlığı (0 = vücut ağırlığı / bilinmiyor). */
    val weight: Float,
    /** Her set için hedef tekrar. Boyutu = önerilen set sayısı. */
    val repTargets: List<Int>,
    val repMin: Int,
    val repMax: Int,
    /** Tek satırlık özet: "42.5 kg × 10". */
    val headline: String,
    /** Kısa gerekçe. */
    val reason: String,
    /** Art arda kaç seanstır tahmini 1RM'de yeni zirve yok. */
    val stalledSessions: Int = 0,
    /** Son iki seans da en iyi değerin belirgin altında mı? */
    val regressing: Boolean = false,
    val lastWeight: Float = 0f,
    val bestE1rm: Float = 0f
) {
    val isPlateau: Boolean get() = stalledSessions >= ProgressionEngine.PLATEAU_SESSIONS
    val sets: Int get() = repTargets.size
    fun repsFor(setIndex: Int): Int = repTargets.getOrNull(setIndex) ?: repTargets.lastOrNull() ?: repMin
}

object ProgressionEngine {

    /** Bu kadar seans üst üste yeni zirve yoksa plato sayılır. */
    const val PLATEAU_SESSIONS = 3

    /** Son setin RPE'si bu değer ve üstündeyse ağırlık artışı ertelenir. */
    const val GRIND_RPE = 9.5f

    /**
     * Bugünün reçetesini üretir.
     * @param history eski → yeni ya da karışık sıralı; içeride tarihe göre sıralanır.
     */
    fun prescribe(
        history: List<SessionLog>,
        targetSets: Int,
        repMin: Int,
        repMax: Int,
        kind: LoadKind,
        profile: LoadingProfile,
        deload: Boolean = false
    ): Prescription {
        val lo = repMin.coerceAtLeast(1)
        val hi = repMax.coerceAtLeast(lo)
        val setCount = targetSets.coerceAtLeast(1)
        val sessions = history.filter { s -> s.sets.any { it.reps > 0 } }.sortedBy { it.dateMillis }
        val step = profile.step(kind)

        if (sessions.isEmpty()) {
            return Prescription(
                action = ProgressAction.FIRST,
                weight = 0f,
                repTargets = List(setCount) { lo },
                repMin = lo, repMax = hi,
                headline = "$setCount × $lo-$hi",
                reason = "İlk kayıt. $hi tekrarı RPE 7-8 ile rahat yapabileceğin bir ağırlık seç."
            )
        }

        val last = sessions.last()
        val working = last.sets.filter { it.reps > 0 }
        val w = workingWeight(working)
        val atW = working.filter { abs(it.weight - w) < 0.01f }
        val repsAtW = atW.map { it.reps }
        val topRpe = atW.maxOfOrNull { it.rpe } ?: 0f
        val stalled = stallCount(sessions)
        val regressing = isRegressing(sessions)
        val best = sessions.maxOf { it.bestE1rm }
        val bodyweight = kind == LoadKind.BODYWEIGHT || w <= 0f

        fun build(action: ProgressAction, weight: Float, reps: List<Int>, reason: String) = Prescription(
            action = action,
            weight = weight,
            repTargets = reps,
            repMin = lo, repMax = hi,
            headline = headline(weight, reps),
            reason = reason + plateauNote(stalled, action),
            stalledSessions = stalled,
            regressing = regressing,
            lastWeight = w,
            bestE1rm = best
        )

        // Deload: ~%90 yük, setler yarıya, tekrar alt sınırda.
        if (deload) {
            val dSets = maxOf(1, (setCount + 1) / 2)
            val dWeight = if (bodyweight || step <= 0f) w else {
                val r = Calc.roundToNearest(w * 0.9f, step)
                if (r >= w) nextDown(w, step) else r
            }
            return build(
                ProgressAction.DELOAD, dWeight, List(dSets) { lo },
                "Deload haftası: yük hafif, set yarıda. Amaç toparlanmak, zorlanmak değil."
            )
        }

        val required = maxOf(1, minOf(setCount, working.size))
        val allTop = repsAtW.size >= required && repsAtW.all { it >= hi }
        val minReps = repsAtW.minOrNull() ?: 0
        val grinding = topRpe >= GRIND_RPE

        // Vücut ağırlığı: yalnızca tekrarla ilerle.
        if (bodyweight || step <= 0f) {
            return if (allTop) build(
                ProgressAction.HOLD, w, List(setCount) { hi },
                "Tüm setlerde $hi tekrar. Zorlaştırma zamanı: ağırlık ekle, tempoyu yavaşlat ya da zor varyasyona geç."
            ) else build(
                ProgressAction.REPS, w, repTargetsPlusOne(repsAtW.ifEmpty { working.map { it.reps } }, setCount, lo, hi),
                "Her sette bir tekrar fazlasını hedefle. $hi tekrara ulaşınca zorlaştır."
            )
        }

        return when {
            allTop && !grinding -> build(
                ProgressAction.INCREASE, nextUp(w, step), List(setCount) { lo },
                "Tüm setlerde $hi tekrar tamam. +${(nextUp(w, step) - w).trimNum()} kg, tekrar $lo'a döner."
            )

            allTop && grinding -> build(
                ProgressAction.HOLD, w, List(setCount) { hi },
                "$hi tekrar tamam ama son set RPE ${topRpe.trimNum()}. Artıştan önce aynı işi RPE 9 altında tekrarla."
            )

            minReps < lo -> {
                val prev = sessions.getOrNull(sessions.size - 2)
                val missedBefore = prev != null && run {
                    val pw = prev.sets.filter { it.reps > 0 }
                    val pW = workingWeight(pw)
                    abs(pW - w) < 0.01f && (pw.filter { abs(it.weight - w) < 0.01f }.minOfOrNull { it.reps } ?: hi) < lo
                }
                if (missedBefore || topRpe >= 10f) build(
                    ProgressAction.DECREASE, nextDown(w, step), List(setCount) { lo },
                    if (missedBefore) "İki seanstır $lo tekrarın altındasın. Bir kademe hafifle, formu oturt, yeniden tırman."
                    else "Tükeniş setine gitmişsin. Bir kademe hafifle, RPE 8 civarında kal."
                ) else build(
                    ProgressAction.HOLD, w, List(setCount) { lo },
                    "Aynı ağırlık. Önce her sette $lo tekrarı yakala."
                )
            }

            else -> build(
                ProgressAction.REPS, w, repTargetsPlusOne(repsAtW, setCount, lo, hi),
                "Aynı ağırlıkta her sete +1 tekrar. Hepsi $hi olunca ağırlık artacak."
            )
        }
    }

    /** Seansın çalışma ağırlığı: en çok set yapılan ağırlık (eşitlikte ağır olan). */
    fun workingWeight(sets: List<LoggedSet>): Float {
        if (sets.isEmpty()) return 0f
        return sets.groupBy { Math.round(it.weight * 100f) }
            .maxWithOrNull(compareBy<Map.Entry<Int, List<LoggedSet>>> { it.value.size }.thenBy { it.key })
            ?.value?.first()?.weight ?: 0f
    }

    /** Son seanslardan geriye doğru: kaç seanstır tahmini 1RM'de yeni zirve yok? */
    fun stallCount(sessions: List<SessionLog>): Int {
        val sorted = sessions.sortedBy { it.dateMillis }
        if (sorted.size < 2) return 0
        val e = sorted.map { it.bestE1rm }
        var count = 0
        for (i in e.indices.reversed()) {
            if (i == 0) break
            val priorBest = e.subList(0, i).maxOrNull() ?: 0f
            if (e[i] > priorBest + 0.05f) break
            count++
        }
        return count
    }

    /** Son iki seans da önceki en iyinin %3'ten fazla altındaysa gerileme var. */
    fun isRegressing(sessions: List<SessionLog>): Boolean {
        val sorted = sessions.sortedBy { it.dateMillis }
        if (sorted.size < 3) return false
        val before = sorted.dropLast(2).maxOf { it.bestE1rm }
        if (before <= 0f) return false
        return sorted.takeLast(2).all { it.bestE1rm < before * 0.97f }
    }

    /** Izgaraya göre bir üst ulaşılabilir ağırlık (ör. 6 kg, adım 2.5 → 7.5). */
    fun nextUp(w: Float, step: Float): Float {
        if (step <= 0f) return w
        return (floor(w / step + 1e-3f) + 1f) * step
    }

    /** Izgaraya göre bir alt ulaşılabilir ağırlık (en az 0). */
    fun nextDown(w: Float, step: Float): Float {
        if (step <= 0f) return w
        return ((ceil(w / step - 1e-3f) - 1f) * step).coerceAtLeast(0f)
    }

    private fun repTargetsPlusOne(prev: List<Int>, sets: Int, lo: Int, hi: Int): List<Int> =
        List(sets) { i ->
            val p = prev.getOrNull(i) ?: prev.lastOrNull() ?: lo
            (p + 1).coerceIn(lo, hi)
        }

    fun headline(weight: Float, reps: List<Int>): String {
        val repText = if (reps.isEmpty()) "" else if (reps.distinct().size == 1) "${reps.size} × ${reps.first()}"
        else reps.joinToString("/")
        return if (weight > 0f) "${weight.trimNum()} kg · $repText" else repText
    }

    private fun plateauNote(stalled: Int, action: ProgressAction): String =
        if (stalled >= PLATEAU_SESSIONS && action != ProgressAction.INCREASE && action != ProgressAction.DELOAD)
            " ($stalled seanstır yeni zirve yok — uyku, beslenme ve teknik gözden geçirilmeli.)"
        else ""
}

/* ==========================================================================
 * Performansa dayalı toparlanma (deload) sinyali
 * Takvime değil, gerçek performansa bakar.
 * ========================================================================== */

object ReadinessEngine {

    data class Signal(
        val deloadSuggested: Boolean,
        val regressingLifts: List<String>,
        val stalledLifts: List<String>,
        val avgRecentRpe: Float,
        val highRpe: Boolean
    ) {
        val reasons: List<String>
            get() = buildList {
                if (regressingLifts.isNotEmpty())
                    add("Gerileyen hareketler: ${regressingLifts.joinToString(", ")} — son iki seans en iyinin belirgin altında.")
                if (stalledLifts.isNotEmpty())
                    add("Platodaki hareketler: ${stalledLifts.joinToString(", ")}.")
                if (highRpe)
                    add("Son iki haftada çalışma setlerinin RPE ortalaması ${avgRecentRpe.trimNum()} — yorgunluk birikiyor.")
            }
    }

    /**
     * @param histories hareket adı → seans geçmişi (yalnızca çalışma setleri)
     * @param recentRpes son ~14 gündeki çalışma setlerinin RPE değerleri (0 olmayanlar)
     */
    fun evaluate(histories: Map<String, List<SessionLog>>, recentRpes: List<Float>): Signal {
        val eligible = histories.filterValues { it.size >= 3 }
        val regressing = eligible.filterValues { ProgressionEngine.isRegressing(it) }.keys.sorted()
        val stalled = eligible.filterValues {
            ProgressionEngine.stallCount(it) >= ProgressionEngine.PLATEAU_SESSIONS
        }.keys.filter { it !in regressing }.sorted()
        val rpes = recentRpes.filter { it > 0f }
        val avg = if (rpes.size >= 6) rpes.average().toFloat() else 0f
        val highRpe = avg >= 9f
        val suggested = regressing.size >= 2 ||
            (regressing.isNotEmpty() && highRpe) ||
            (stalled.size + regressing.size) >= 3
        return Signal(suggested, regressing, stalled, avg, highRpe)
    }
}
