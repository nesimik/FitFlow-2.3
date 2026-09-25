package com.example.core

import com.example.data.WorkoutEntity
import com.example.data.WorkoutSetEntity

/**
 * Akıllı Deload (Toparlanma) Tavsiye ve Zorlanma Analiz Motoru.
 *
 * Kasların ve merkezi sinir sisteminin zorlanma seviyesine, haftalık tonaj/set artışına
 * ve toparlanma hissiyatına göre 6-8 hafta aralığında akıllı deload zamanlamasına karar verir.
 */
object DeloadAdvisor {

    const val ADVICE_KEEP_OR_REDUCE_WEIGHT = "Ağırlık koru veya Max %10 düşür"
    const val ADVICE_HALVE_SETS = "Set sayısını yarıya indir"
    const val ADVICE_REPS_8_10 = "Tekrar 8-10'da bırak"

    data class DeloadRecommendation(
        val shouldDeloadNow: Boolean,
        val currentCycleWeek: Int,
        val recommendedWeek: Int,
        val strainScore: Int,               // 0..100
        val strainLevel: String,            // "Yüksek", "Orta", "Düşük"
        val reasonTitle: String,
        val reasonDetails: List<String>,
        val adviceList: List<String>,
        val isCurrentlyDeloadWeek: Boolean,
        /** Öneri takvim yerine gerçek performans düşüşünden mi geliyor? */
        val performanceTriggered: Boolean = false
    )

    fun analyze(
        workouts: List<WorkoutEntity>,
        allSets: List<WorkoutSetEntity>,
        activeDeloadWeekStart: Long,
        now: Long = System.currentTimeMillis()
    ): DeloadRecommendation {
        val currentWeekStart = startOfWeek(now)
        val isExplicitDeloadActive = (activeDeloadWeekStart > 0L && activeDeloadWeekStart == currentWeekStart) ||
                workouts.any { it.startedAt >= currentWeekStart && it.isDeload }

        val finishedWorkouts = workouts.filter { it.isFinished }

        // En son deload yapılan seans veya tarih
        val lastDeloadWorkout = finishedWorkouts.filter { it.isDeload }.maxByOrNull { it.startedAt }
        val cycleStartTime = when {
            lastDeloadWorkout != null -> {
                // Son deload'un ertesi haftası yeni döngü başlangıcı kabul edilir
                startOfWeek(lastDeloadWorkout.startedAt) + 7 * 86_400_000L
            }
            finishedWorkouts.isNotEmpty() -> {
                startOfWeek(finishedWorkouts.minOf { it.startedAt })
            }
            else -> currentWeekStart
        }

        val millisDiff = (now - cycleStartTime).coerceAtLeast(0L)
        val weeksElapsed = (millisDiff / (7 * 86_400_000L)).toInt() + 1
        val currentCycleWeek = weeksElapsed.coerceAtLeast(1)

        // Zorlanma Faktörleri Analizi (Hacim artışı, tonaj artışı, yorgunluk/feeling, RPE)
        val cycleWorkouts = finishedWorkouts.filter { it.startedAt >= cycleStartTime && !it.isDeload }
        val cycleSets = allSets.filter { s -> cycleWorkouts.any { it.id == s.workoutId } }

        val (strainScore, strainLevel, reasonTitle, details, recommendedWeek) =
            evaluateStrain(currentCycleWeek, cycleWorkouts, cycleSets, now)

        // Performansa dayalı sinyal: gerileyen / platoya giren ana hareketler ve RPE birikimi.
        // Takvim tek başına deload tetiklemez; yalnızca uzun süre (öneri + 2 hafta) sinyal
        // gelmezse güvenlik amaçlı devreye girer.
        val histories = cycleSets
            .filter { it.isCompleted && !it.isWarmup && it.reps > 0 }
            .groupBy { it.exerciseName }
            .mapValues { (_, sets) ->
                sets.groupBy { it.workoutId }.values.map { ws ->
                    SessionLog(ws.first().performedAt, ws.map { LoggedSet(it.weightKg, it.reps, it.rpe) })
                }
            }
        val recentRpes = cycleSets
            .filter { it.isCompleted && !it.isWarmup && it.rpe > 0f && it.performedAt >= now - 14 * 86_400_000L }
            .map { it.rpe }
        val signal = ReadinessEngine.evaluate(histories, recentRpes)
        val performanceTriggered = signal.deloadSuggested && currentCycleWeek >= 4 && !isExplicitDeloadActive
        val safetyCap = currentCycleWeek >= recommendedWeek + 2

        val shouldDeloadNow = isExplicitDeloadActive || performanceTriggered || safetyCap
        val finalTitle = when {
            performanceTriggered -> "Performans verilerin toparlanma ihtiyacını gösteriyor — bu hafta deload önerilir."
            safetyCap -> "Uzun süredir kesintisiz yüklenmedesin; güvenlik amaçlı deload önerilir."
            else -> reasonTitle
        }
        val finalDetails = if (performanceTriggered) signal.reasons + details else details

        return DeloadRecommendation(
            shouldDeloadNow = shouldDeloadNow,
            currentCycleWeek = currentCycleWeek,
            recommendedWeek = recommendedWeek,
            strainScore = strainScore,
            strainLevel = strainLevel,
            reasonTitle = finalTitle,
            reasonDetails = finalDetails,
            adviceList = listOf(
                ADVICE_KEEP_OR_REDUCE_WEIGHT,
                ADVICE_HALVE_SETS,
                ADVICE_REPS_8_10
            ),
            isCurrentlyDeloadWeek = isExplicitDeloadActive,
            performanceTriggered = performanceTriggered
        )
    }

    private data class Evaluation(
        val strainScore: Int,
        val strainLevel: String,
        val reasonTitle: String,
        val details: List<String>,
        val recommendedWeek: Int
    )

    private fun evaluateStrain(
        currentCycleWeek: Int,
        workouts: List<WorkoutEntity>,
        sets: List<WorkoutSetEntity>,
        now: Long
    ): Evaluation {
        val details = mutableListOf<String>()
        var score = 30 // Temel puan

        val oneWeekMillis = 7 * 86_400_000L
        val lastWeekWorkouts = workouts.filter { it.startedAt >= (now - oneWeekMillis) }
        val prevWeekWorkouts = workouts.filter { it.startedAt in (now - 2 * oneWeekMillis) until (now - oneWeekMillis) }

        // 1. Set Artışı ve Hacim Baskısı
        val recentEffectiveSets = sets.filter { s ->
            s.isCompleted && s.performedAt >= (now - 2 * oneWeekMillis) && !s.isWarmup
        }
        val weeklyAvgSets = (recentEffectiveSets.size / 2).coerceAtLeast(0)
        when {
            weeklyAvgSets >= 45 -> {
                score += 25
                details.add("Yüksek haftalık set hacmi (~$weeklyAvgSets set/hafta) kas toparlanmasını zorluyor.")
            }
            weeklyAvgSets >= 32 -> {
                score += 15
                details.add("Dengeli ancak birikimli set hacmi (~$weeklyAvgSets set/hafta).")
            }
            else -> {
                score += 5
            }
        }

        // 2. Tonaj / Ağırlık Artışı Eğilimi
        val lastWeekTonnage = lastWeekWorkouts.sumOf { w ->
            sets.filter { it.workoutId == w.id && it.isCompleted }.sumOf { (it.weightKg * it.reps).toDouble() }
        }
        val prevWeekTonnage = prevWeekWorkouts.sumOf { w ->
            sets.filter { it.workoutId == w.id && it.isCompleted }.sumOf { (it.weightKg * it.reps).toDouble() }
        }

        if (prevWeekTonnage > 0 && lastWeekTonnage > 0) {
            val growthPct = ((lastWeekTonnage - prevWeekTonnage) / prevWeekTonnage) * 100.0
            if (growthPct >= 12.0) {
                score += 25
                details.add("Son haftada %${growthPct.toInt()} tonaj/ağırlık artışı ile kaslar yoğun progressive overload altında.")
            } else if (growthPct >= 5.0) {
                score += 15
                details.add("Ağırlık ve tonaj istikrarlı şekilde artış trendinde.")
            }
        }

        // 3. Yorgunluk Hissiyatı (Feeling 1..5)
        val recentFeelings = workouts.filter { it.startedAt >= (now - 2 * oneWeekMillis) && it.feeling > 0 }
            .map { it.feeling }
        if (recentFeelings.isNotEmpty()) {
            val avgFeeling = recentFeelings.average()
            if (avgFeeling <= 2.2) {
                score += 25
                details.add("Antrenman sonu toparlanma hissiyatı düşük (Ortalama ${String.format(java.util.Locale.US, "%.1f", avgFeeling)}/5), merkezi yorgunluk birikmiş.")
            } else if (avgFeeling <= 3.0) {
                score += 15
                details.add("Orta seviye antrenman yorgunluğu gözlemlendi.")
            }
        }

        // 4. RPE Yoğunluğu
        val recentRpeSets = recentEffectiveSets.filter { it.rpe > 0f }
        if (recentRpeSets.isNotEmpty()) {
            val highRpeCount = recentRpeSets.count { it.rpe >= 9.0f }
            val highRpeRatio = highRpeCount.toFloat() / recentRpeSets.size
            if (highRpeRatio >= 0.35f) {
                score += 20
                details.add("Setlerin %${(highRpeRatio * 100).toInt()}'i RPE 9+ (tükenişe yakın) icra edildi.")
            }
        }

        val finalScore = score.coerceIn(15, 95)
        val strainLevel: String
        val recommendedWeek: Int
        val reasonTitle: String

        when {
            finalScore >= 65 -> {
                strainLevel = "Yüksek"
                recommendedWeek = 6
                reasonTitle = "Yüksek kas zorlanması ve kümülatif yorgunluk nedeniyle 6. haftada deload tavsiye edilir."
                if (details.isEmpty()) {
                    details.add("Son haftalardaki yoğun antrenman temposu toparlanma ihtiyacını 6. haftaya çekti.")
                }
            }
            finalScore in 45..64 -> {
                strainLevel = "Orta"
                recommendedWeek = 7
                reasonTitle = "Orta seviye yüklenme tespit edildi. 7. haftada deload yapılması optimum toparlanma sağlar."
                if (details.isEmpty()) {
                    details.add("Dengeli aşırı yükleme döngüsü 7. haftada süperkompanzasyon için toparlanma gerektirir.")
                }
            }
            else -> {
                strainLevel = "Düşük / Dengeli"
                recommendedWeek = 8
                reasonTitle = "Dengeli adaptasyon sağlandı. 8 haftalık hipertrofi döngüsü tamamlandığında deload önerilir."
                if (details.isEmpty()) {
                    details.add("Vücut antrenman stresini iyi tolere ediyor, 8 haftalık tam blok sonunda deload uygundur.")
                }
            }
        }

        return Evaluation(
            strainScore = finalScore,
            strainLevel = strainLevel,
            reasonTitle = reasonTitle,
            details = details,
            recommendedWeek = recommendedWeek
        )
    }
}
