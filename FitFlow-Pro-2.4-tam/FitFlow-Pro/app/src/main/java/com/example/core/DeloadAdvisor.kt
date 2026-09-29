package com.example.core

import com.example.data.WorkoutEntity
import com.example.data.WorkoutSetEntity

/**
 * Deload (toparlanma haftası) karar motoru — 2.8.
 *
 * Karar sırası:
 *  1. Kullanıcı bu haftayı deload olarak başlattıysa → aktif.
 *  2. Performans sinyali (gerileyen / platodaki ana hareketler, yüksek RPE) → öneri (4. haftadan itibaren).
 *  3. Planlı blok ayarlıysa (ör. 5 hafta yüklenme) → blok bitince öneri.
 *     Otomatik moddaysa yorgunluk puanına göre 6-8. hafta hedeflenir; öneri hedef + 2 haftada gelir.
 *
 * Kullanıcı öneriyi o hafta için yok sayabilir; pazartesi yeniden değerlendirilir.
 * Tonaj artışı yorgunluk sayılmaz — ilerleme ceza değildir.
 */
object DeloadAdvisor {

    /** Deload haftasında seansların otomatik uyguladığı kurallar (ProgressionEngine ile birebir). */
    const val DELOAD_SUMMARY = "Yük ~%10 az · setler yarıda · merdivenin ilk basamağı"
    const val DELOAD_EFFORT = "Her sette en az 4 tekrar yedekte bırak."

    data class DeloadRecommendation(
        /** Kart gösterilmeli mi (öneri ya da aktif deload). Yok sayılsa bile öneri true kalır. */
        val shouldDeloadNow: Boolean,
        /** Döngünün kaçıncı haftası (son deload'dan sonra, 1'den başlar). */
        val currentCycleWeek: Int,
        /** Deload'un hedeflendiği hafta (yüklenme haftaları + 1). */
        val recommendedWeek: Int,
        val strainScore: Int,
        val strainLevel: String,
        /** Tek satırlık neden — kartta görünen. */
        val reasonTitle: String,
        /** "Neden?" altında açılan ayrıntılar. */
        val reasonDetails: List<String>,
        val isCurrentlyDeloadWeek: Boolean,
        val performanceTriggered: Boolean = false,
        /** Kullanıcı bu haftaki öneriyi yok saydı mı? */
        val dismissedThisWeek: Boolean = false,
        /** Planlı blok uzunluğu (yüklenme haftası); 0 = otomatik. */
        val blockLoadWeeks: Int = 0
    ) {
        /** Yüklenme haftası sayısı (planlı ya da otomatik tahmin). */
        val loadWeeks: Int get() = (recommendedWeek - 1).coerceAtLeast(1)
        /** Öneri kartı açık gösterilsin mi (yok sayılmadıysa). */
        val showRecommendation: Boolean get() = shouldDeloadNow && !isCurrentlyDeloadWeek && !dismissedThisWeek
    }

    fun analyze(
        workouts: List<WorkoutEntity>,
        allSets: List<WorkoutSetEntity>,
        activeDeloadWeekStart: Long,
        dismissedWeekStart: Long = 0L,
        blockLoadWeeks: Int = 0,
        now: Long = System.currentTimeMillis()
    ): DeloadRecommendation {
        val week = 7 * 86_400_000L
        val currentWeekStart = startOfWeek(now)
        val isActive = activeDeloadWeekStart > 0L && activeDeloadWeekStart == currentWeekStart

        val finished = workouts.filter { it.isFinished }

        // Döngü başlangıcı: son deload haftasının ertesi haftası (bu haftaki deload hariç).
        val lastDeloadWeek = listOfNotNull(
            finished.filter { it.isDeload }.maxOfOrNull { startOfWeek(it.startedAt) }?.takeIf { it < currentWeekStart },
            activeDeloadWeekStart.takeIf { it in 1 until currentWeekStart }
        ).maxOrNull()
        val cycleStart = when {
            lastDeloadWeek != null -> lastDeloadWeek + week
            finished.isNotEmpty() -> startOfWeek(finished.minOf { it.startedAt })
            else -> currentWeekStart
        }
        val cycleWeek = (((now - cycleStart).coerceAtLeast(0L) / week).toInt() + 1).coerceAtLeast(1)

        val cycleWorkouts = finished.filter { it.startedAt >= cycleStart && !it.isDeload }
        val cycleIds = cycleWorkouts.map { it.id }.toHashSet()
        val cycleSets = allSets.filter { it.workoutId in cycleIds }

        val strain = evaluateStrain(cycleWorkouts, cycleSets, cycleStart, now)
        val planned = blockLoadWeeks > 0
        val recommendedWeek = if (planned) blockLoadWeeks + 1 else strain.autoWeek

        // Performans sinyali: gerileyen / platodaki hareketler, RPE birikimi.
        val histories = cycleSets
            .filter { it.isCompleted && !it.isWarmup && it.reps > 0 }
            .groupBy { it.exerciseName }
            .mapValues { (_, sets) ->
                sets.groupBy { it.workoutId }.values.map { ws ->
                    SessionLog(ws.first().performedAt, ws.map { LoggedSet(it.weightKg, it.reps, it.rpe) })
                }
            }
        val recentRpes = cycleSets
            .filter { it.isCompleted && !it.isWarmup && it.rpe > 0f && it.performedAt >= now - 2 * week }
            .map { it.rpe }
        val signal = ReadinessEngine.evaluate(histories, recentRpes)

        val performanceTriggered = !isActive && signal.deloadSuggested && cycleWeek >= 4
        val scheduleDue = !isActive && if (planned) cycleWeek >= recommendedWeek else cycleWeek >= recommendedWeek + 2
        val shouldDeload = isActive || performanceTriggered || scheduleDue

        val title = when {
            isActive -> "Toparlanma haftası: hafif çalış, bir sonraki blokta daha güçlü dön."
            performanceTriggered -> signal.reasons.firstOrNull()
                ?: "Performansın düşüyor; toparlanma zamanı."
            scheduleDue && planned -> "$blockLoadWeeks haftalık yüklenme bloğu tamamlandı."
            scheduleDue -> "${cycleWeek - 1} haftadır kesintisiz yükleniyorsun."
            else -> ""
        }
        val details = buildList {
            if (performanceTriggered) addAll(signal.reasons.drop(1))
            addAll(strain.details)
            if (planned) add("Planın: $blockLoadWeeks hafta yüklenme + 1 hafta deload. Şu an döngünün $cycleWeek. haftası.")
            else add("Otomatik mod: yorgunluk seviyene göre deload ${recommendedWeek}. haftaya hedeflendi. Program ekranından sabit blok belirleyebilirsin.")
        }

        return DeloadRecommendation(
            shouldDeloadNow = shouldDeload,
            currentCycleWeek = cycleWeek,
            recommendedWeek = recommendedWeek,
            strainScore = strain.score,
            strainLevel = strain.level,
            reasonTitle = title,
            reasonDetails = details,
            isCurrentlyDeloadWeek = isActive,
            performanceTriggered = performanceTriggered,
            dismissedThisWeek = !isActive && dismissedWeekStart == currentWeekStart,
            blockLoadWeeks = blockLoadWeeks
        )
    }

    private data class Strain(val score: Int, val level: String, val autoWeek: Int, val details: List<String>)

    /**
     * Yorgunluk puanı: seans sonu hissiyatı, RPE yoğunluğu ve döngü içindeki hacim birikimi.
     * Tonaj artışı (ilerleme) puana eklenmez.
     */
    private fun evaluateStrain(
        workouts: List<WorkoutEntity>,
        sets: List<WorkoutSetEntity>,
        cycleStart: Long,
        now: Long
    ): Strain {
        val week = 7 * 86_400_000L
        val details = mutableListOf<String>()
        var score = 30
        val working = sets.filter { it.isCompleted && !it.isWarmup }

        // 1) Hissiyat (1..5) — son 2 hafta
        val feelings = workouts.filter { it.startedAt >= now - 2 * week && it.feeling > 0 }.map { it.feeling }
        if (feelings.size >= 2) {
            val avg = feelings.average()
            val txt = String.format(java.util.Locale("tr"), "%.1f", avg)
            if (avg <= 2.2) { score += 25; details.add("Seans sonu hissiyatın düşük (ortalama $txt/5).") }
            else if (avg <= 3.0) { score += 15; details.add("Seans sonu hissiyatın orta (ortalama $txt/5).") }
        }

        // 2) RPE 9+ oranı — son 2 hafta
        val rpeSets = working.filter { it.rpe > 0f && it.performedAt >= now - 2 * week }
        if (rpeSets.size >= 6) {
            val ratio = rpeSets.count { it.rpe >= 9f }.toFloat() / rpeSets.size
            if (ratio >= 0.35f) { score += 20; details.add("Setlerinin %${(ratio * 100).toInt()}'i RPE 9 ve üstü (tükenişe yakın).") }
        }

        // 3) Hacim birikimi — son haftanın set sayısı, döngünün ilk iki haftasına göre
        val firstWeeks = working.count { it.performedAt in cycleStart until cycleStart + 2 * week } / 2f
        val lastWeek = working.count { it.performedAt >= now - week }.toFloat()
        if (firstWeeks >= 8f && now - cycleStart >= 3 * week) {
            val growth = (lastWeek - firstWeeks) / firstWeeks
            if (growth >= 0.25f) {
                score += 10
                details.add("Haftalık set sayın döngü başına göre %${(growth * 100).toInt()} arttı (${firstWeeks.toInt()} → ${lastWeek.toInt()}).")
            }
        }

        val final = score.coerceIn(15, 95)
        return when {
            final >= 65 -> Strain(final, "Yüksek", 6, details)
            final >= 45 -> Strain(final, "Orta", 7, details)
            else -> Strain(final, "Düşük", 8, details)
        }
    }
}

/**
 * Blok içindeki hedef zorluk (RIR = yedekte kalan tekrar).
 * Blok başında daha fazla yedek, sonuna doğru tükenişe yaklaşılır; deload'da bol yedek.
 */
object TrainingBlock {

    data class Effort(val rir: String, val hint: String)

    fun effort(cycleWeek: Int, loadWeeks: Int, deload: Boolean): Effort {
        if (deload) return Effort("RIR 4+", DeloadAdvisor.DELOAD_EFFORT)
        val n = loadWeeks.coerceAtLeast(1)
        val f = if (n <= 1) 1f else (cycleWeek - 1).toFloat() / (n - 1)
        return when {
            f < 0.3f -> Effort("RIR 2-3", "Blok başı: setleri 2-3 tekrar yedekte bitir.")
            f < 0.8f -> Effort("RIR 1-2", "Bloğun ortası: 1-2 tekrar yedekte bırak.")
            else -> Effort("RIR 0-1", "Blok sonu: tükenişe 0-1 tekrar kala bitir.")
        }
    }

    /**
     * Isınma rampası (ağırlık × tekrar). Barbell: klasik %40-85 rampası (bar ağırlığından başlar).
     * Dambıl / makine: %50×8 ve %75×3, ekipmanın artış adımına yuvarlanır.
     */
    fun warmupRamp(work: Float, kind: LoadKind, profile: LoadingProfile): List<Pair<Float, Int>> {
        if (work <= 0f) return emptyList()
        return when (kind) {
            LoadKind.BARBELL ->
                if (work < profile.barKg + 10f) emptyList()
                else Calc.warmupScheme(work, profile.barKg).filter { it.first < work }
            LoadKind.DUMBBELL, LoadKind.MACHINE -> {
                val step = profile.step(kind).takeIf { it > 0f } ?: 1f
                if (work < step * 4) emptyList()
                else listOf(0.5f to 8, 0.75f to 3)
                    .map { (p, r) -> profile.round(work * p, kind) to r }
                    .filter { it.first > 0f && it.first < work }
                    .distinctBy { it.first }
            }
            LoadKind.BODYWEIGHT, LoadKind.OTHER -> emptyList()
        }
    }
}
