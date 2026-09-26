package com.example.core

import com.example.data.ExerciseEntity
import com.example.data.WorkoutSetEntity
import kotlin.math.max

/* ==========================================================================
 * Kas toparlanma tahmini
 *
 * Her çalışma seti, çalıştırdığı kaslara katkı oranı kadar "yorgunluk" bırakır.
 * Bu yorgunluk, kasın toparlanma süresi boyunca doğrusal olarak azalır. Büyük kaslar
 * (bacak, kanat, bel) daha yavaş, küçük kaslar (baldır, karın, önkol) daha hızlı
 * toparlanır. Zorlu setler (RPE 9+) biraz daha fazla, hafif setler daha az yorar.
 *
 * Bu bir TAHMİNDİR: uyku, beslenme, stres ve yaş toparlanmayı etkiler. Amaç kesin bir
 * ölçüm değil, "bugün hangi kaslar hazır" sorusuna makul bir cevap vermektir.
 * ========================================================================== */

data class MuscleRecovery(
    val key: String,
    /** 0 = tamamen yorgun, 1 = tamamen toparlanmış */
    val readiness: Float,
    /** Son çalışılma anı (epoch ms) */
    val lastTrainedAt: Long,
    /** Hazır sayılacağı an (epoch ms); zaten hazırsa 0 */
    val readyAt: Long,
    /** Son günlerde bu kasa düşen etkin set toplamı */
    val recentSets: Float
) {
    val state: RecoveryState
        get() = when {
            readiness >= RecoveryEngine.READY -> RecoveryState.FRESH
            readiness >= 0.5f -> RecoveryState.RECOVERING
            else -> RecoveryState.FATIGUED
        }
}

enum class RecoveryState { FRESH, RECOVERING, FATIGUED }

fun RecoveryState.label(): String = when (this) {
    RecoveryState.FRESH -> "Dinç"
    RecoveryState.RECOVERING -> "Toparlanıyor"
    RecoveryState.FATIGUED -> "Yorgun"
}

object RecoveryEngine {

    /** Bu hazırlık oranının üstü "dinç" sayılır. */
    const val READY = 0.85f

    /** Taze bir seansta bu kadar etkin set, kası tamamen yorgun kabul ettirir. */
    const val FULL_FATIGUE_SETS = 6f

    /** Kaç günlük geçmişe bakılır. */
    const val LOOKBACK_DAYS = 6

    private const val HOUR = 3_600_000L

    /** Tek bir set uyarısı: hangi kaslara ne oranda, ne zaman, ne zorlukta. */
    data class Stimulus(val weights: Map<String, Float>, val atMillis: Long, val rpe: Float)

    /** Kasın yorgunluğunun sıfırlandığı süre (saat). Orta yaş için biraz uzatılmış. */
    fun recoveryHours(key: String): Float = MuscleMap.recoveryDays(key) * 24f * 1.25f + 12f

    private fun rpeFactor(rpe: Float): Float = when {
        rpe <= 0f -> 1f
        rpe >= 9f -> 1.15f
        rpe <= 7f -> 0.85f
        else -> 1f
    }

    private fun fatigueAt(key: String, stimuli: List<Pair<Float, Long>>, atMillis: Long): Float {
        val t = recoveryHours(key)
        return stimuli.sumOf { (w, at) ->
            val h = (atMillis - at).toFloat() / HOUR
            if (h < 0f) 0.0 else (w * max(0f, 1f - h / t)).toDouble()
        }.toFloat()
    }

    fun compute(stimuli: List<Stimulus>, now: Long = System.currentTimeMillis()): Map<String, MuscleRecovery> {
        val since = now - LOOKBACK_DAYS * 24 * HOUR
        val perMuscle = HashMap<String, MutableList<Pair<Float, Long>>>()
        stimuli.filter { it.atMillis in since..now }.forEach { s ->
            val f = rpeFactor(s.rpe)
            s.weights.forEach { (k, w) -> if (w > 0f) perMuscle.getOrPut(k) { mutableListOf() }.add(w * f to s.atMillis) }
        }
        return perMuscle.mapValues { (key, list) ->
            val fatigue = fatigueAt(key, list, now)
            val readiness = (1f - fatigue / FULL_FATIGUE_SETS).coerceIn(0f, 1f)
            var readyAt = 0L
            if (readiness < READY) {
                // Saat saat ileri giderek hazır olacağı anı bul (en fazla toparlanma süresi kadar).
                val limit = recoveryHours(key).toInt() + 1
                for (h in 1..limit) {
                    val at = now + h * HOUR
                    if (1f - fatigueAt(key, list, at) / FULL_FATIGUE_SETS >= READY) { readyAt = at; break }
                }
            }
            MuscleRecovery(
                key = key,
                readiness = readiness,
                lastTrainedAt = list.maxOf { it.second },
                readyAt = readyAt,
                recentSets = list.sumOf { it.first.toDouble() }.toFloat()
            )
        }
    }

    /** Uygulama verisinden: tamamlanmış çalışma setleri → kas uyarıları. */
    fun fromSets(
        sets: List<WorkoutSetEntity>,
        exercises: List<ExerciseEntity>,
        now: Long = System.currentTimeMillis()
    ): Map<String, MuscleRecovery> {
        val exMap = exercises.associateBy { it.id }
        val since = now - LOOKBACK_DAYS * 24 * HOUR
        val stimuli = sets.asSequence()
            .filter { it.performedAt in since..now && Analytics.isEffectiveSet(it) }
            .map { s ->
                val ex = exMap[s.exerciseId]
                val w = MuscleMap.resolve(s.exerciseName, ex?.muscleGroup ?: "", ex?.secondaryMuscles ?: "").weights()
                Stimulus(w, s.performedAt, s.rpe)
            }
            .toList()
        return compute(stimuli, now)
    }
}
