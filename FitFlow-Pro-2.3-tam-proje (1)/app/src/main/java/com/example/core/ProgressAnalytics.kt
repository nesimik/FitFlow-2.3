package com.example.core

import com.example.data.ExerciseEntity
import com.example.data.RoutineDayEntity
import com.example.data.RoutineItemEntity
import com.example.data.WorkoutEntity
import com.example.data.WorkoutSetEntity
import kotlin.math.abs
import kotlin.math.roundToInt

/* ==========================================================================
 * Gelişmiş ilerleme analizleri
 *
 * Buradaki metrikler ciddi antrenman takip uygulamalarının kullandığı
 * yaklaşımlara dayanır:
 *  - Kas bazlı etkin set hacmi (birincil 1.0 / ikincil 0.5 set)
 *  - Akut : kronik yüklenme oranı (ACWR) ile yüklenme dengesi
 *  - Durağanlık tespiti (kaç haftadır 1RM ilerlemesi yok)
 *  - Vücut ağırlığına göre güç seviyesi ve lift dengesi
 *  - Tekrar aralığı dağılımı (güç / hipertrofi / dayanıklılık payı)
 * ========================================================================== */

/* ------------------------------- Kas yüklenmesi ----------------------------- */

data class MuscleLoad(
    val key: String,
    val effectiveSets: Float,     // birincil 1.0 + ikincil 0.5 toplamı
    val volume: Float,            // tonaj payı
    val daysSince: Int,           // en son ne zaman çalışıldı (-1 = hiç)
    val target: IntRange
) {
    val status: LoadStatus
        get() = when {
            effectiveSets <= 0.01f -> LoadStatus.NONE
            effectiveSets < target.first * 0.6f -> LoadStatus.LOW
            effectiveSets < target.first -> LoadStatus.BELOW
            effectiveSets <= target.last -> LoadStatus.OPTIMAL
            effectiveSets <= target.last * 1.35f -> LoadStatus.HIGH
            else -> LoadStatus.EXCESSIVE
        }

    /** Hedef aralığın neresinde olduğumuz (0..1+). Isı haritası için. */
    val fill: Float
        get() = if (target.last <= 0) 0f else (effectiveSets / target.last).coerceIn(0f, 1.4f)

    val isFresh: Boolean get() = daysSince < 0 || daysSince >= MuscleMap.recoveryDays(key)
}

data class MuscleContributor(
    val exerciseId: Long,
    val exerciseName: String,
    val muscleGroup: String,
    val isPrimary: Boolean,               // true = 1.0 set (birincil), false = 0.5 set (ikincil destek)
    val performedSetCount: Int,           // Dönemde tamamlanan set sayısı
    val effectiveContribution: Float,     // Dönemdeki haftalık etkin set katkısı
    val totalVolumeContribution: Float,   // Dönemdeki haftalık tonaj katkısı
    val inRoutineDays: List<String> = emptyList(), // Hangi program günlerinde yer alıyor
    val lastPerformedAt: Long = 0L
)

enum class LoadStatus { NONE, LOW, BELOW, OPTIMAL, HIGH, EXCESSIVE }

fun LoadStatus.label(): String = when (this) {
    LoadStatus.NONE -> "Hiç çalışılmadı"
    LoadStatus.LOW -> "Çok az"
    LoadStatus.BELOW -> "Hedefin altında"
    LoadStatus.OPTIMAL -> "İdeal aralıkta"
    LoadStatus.HIGH -> "Hedefin üstünde"
    LoadStatus.EXCESSIVE -> "Aşırı"
}

/* ------------------------------ Yüklenme dengesi ---------------------------- */

data class TrainingLoad(
    val acuteVolume: Float,       // son 7 gün
    val chronicWeekly: Float,     // son 28 günün haftalık ortalaması
    val acuteSets: Int,
    val ratio: Float
) {
    val status: String
        get() = when {
            chronicWeekly <= 0f -> "Veri yetersiz"
            ratio < 0.75f -> "Yüklenme düşük"
            ratio <= 1.3f -> "Dengeli"
            ratio <= 1.5f -> "Hızlı artış"
            else -> "Aşırı yüklenme"
        }

    val advice: String
        get() = when {
            chronicWeekly <= 0f -> "Birkaç hafta daha veri biriktiğinde yüklenme dengesi hesaplanacak."
            ratio < 0.75f -> "Son hafta normalinin altında kaldı. Hacmi kademeli artırabilirsin."
            ratio <= 1.3f -> "Hacim artışı sürdürülebilir bandın içinde — böyle devam."
            ratio <= 1.5f -> "Hacim son haftada belirgin arttı. Formu ve toparlanmayı takip et."
            else -> "Hacim alışkın olduğunun çok üstünde. Sakatlık riskini düşürmek için bir hafta geri çekilmeyi düşün."
        }
}

/* ------------------------------- Durağanlık --------------------------------- */

data class StagnantLift(
    val exerciseId: Long,
    val name: String,
    val bestE1rm: Float,
    val latestE1rm: Float,
    val daysSinceBest: Int,
    val sessionsSinceBest: Int
) {
    val dropPct: Float get() = if (bestE1rm <= 0f) 0f else (bestE1rm - latestE1rm) / bestE1rm * 100f
}

/* --------------------------------- Güç profili ------------------------------ */

data class LiftStandard(
    val liftKey: String,
    val displayName: String,
    val exerciseId: Long,
    val e1rm: Float,
    val bodyweightRatio: Float,
    val level: String,
    val levelIndex: Int,
    val nextLevel: String?,
    val nextLevelWeight: Float
)

data class LiftBalanceItem(
    val displayName: String,
    val actualRatio: Float,       // squat'a göre gerçek oran
    val expectedRatio: Float,     // tipik oran
    val deviationPct: Float       // + güçlü, - zayıf
)

/* ------------------------------ Tekrar dağılımı ----------------------------- */

data class RepRangeSlice(val label: String, val sets: Int, val share: Float, val purpose: String)

/* --------------------------------- Tutarlılık ------------------------------- */

data class WeekAdherence(val label: String, val workouts: Int, val goal: Int) {
    val met: Boolean get() = goal > 0 && workouts >= goal
}

object ProgressAnalytics {

    private const val DAY = 86_400_000L

    /* ----------------------------- Kas yüklenmesi ---------------------------- */

    /**
     * Verilen tarihten bugüne kas bazlı etkin set ve tonaj dağılımı.
     * Birincil kas 1.0, ikincil kas 0.5 set olarak sayılır.
     */
    fun muscleLoads(
        sets: List<WorkoutSetEntity>,
        exercises: List<ExerciseEntity>,
        sinceMillis: Long,
        periodWeeks: Float = 1f
    ): List<MuscleLoad> {
        val exMap = exercises.associateBy { it.id }
        val valid = sets.filter { Analytics.isEffectiveSet(it) }

        val setAcc = HashMap<String, Float>()
        val volAcc = HashMap<String, Float>()
        val lastAcc = HashMap<String, Long>()

        valid.forEach { s ->
            val ex = exMap[s.exerciseId]
            val act = MuscleMap.resolve(s.exerciseName, ex?.muscleGroup ?: "", ex?.secondaryMuscles ?: "")
            val weights = act.weights()
            if (weights.isEmpty()) return@forEach
            val vol = s.weightKg * s.reps
            weights.forEach { (muscle, w) ->
                if (s.performedAt >= sinceMillis) {
                    setAcc[muscle] = (setAcc[muscle] ?: 0f) + w
                    volAcc[muscle] = (volAcc[muscle] ?: 0f) + vol * w
                }
                val prev = lastAcc[muscle] ?: 0L
                if (s.performedAt > prev) lastAcc[muscle] = s.performedAt
            }
        }

        val now = System.currentTimeMillis()
        val divisor = if (periodWeeks <= 0f) 1f else periodWeeks
        return MuscleMap.all.map { key ->
            MuscleLoad(
                key = key,
                effectiveSets = (setAcc[key] ?: 0f) / divisor,
                volume = (volAcc[key] ?: 0f) / divisor,
                daysSince = lastAcc[key]?.let { daysBetween(it, now) } ?: -1,
                target = MuscleMap.weeklyTarget(key)
            )
        }
    }

    /**
     * Haritadaki detay bölgeler (şimdilik üst / alt göğüs) için yüklenme.
     * Göğüs katkısı, hareketin açısına göre (MuscleMap.chestSplit) iki bölgeye dağıtılır.
     */
    fun detailLoads(
        sets: List<WorkoutSetEntity>,
        exercises: List<ExerciseEntity>,
        sinceMillis: Long,
        periodWeeks: Float = 1f
    ): List<MuscleLoad> {
        val exMap = exercises.associateBy { it.id }
        var upper = 0f; var lower = 0f
        var upperVol = 0f; var lowerVol = 0f
        var lastUpper = 0L; var lastLower = 0L
        sets.filter { Analytics.isEffectiveSet(it) }.forEach { s ->
            val ex = exMap[s.exerciseId]
            val w = MuscleMap.resolve(s.exerciseName, ex?.muscleGroup ?: "", ex?.secondaryMuscles ?: "")
                .weights()[MuscleMap.CHEST] ?: return@forEach
            val (u, l) = MuscleMap.chestSplit(s.exerciseName)
            val vol = s.weightKg * s.reps
            if (s.performedAt >= sinceMillis) {
                upper += w * u; lower += w * l
                upperVol += vol * w * u; lowerVol += vol * w * l
            }
            if (u > 0f && s.performedAt > lastUpper) lastUpper = s.performedAt
            if (l > 0f && s.performedAt > lastLower) lastLower = s.performedAt
        }
        val now = System.currentTimeMillis()
        val d = if (periodWeeks <= 0f) 1f else periodWeeks
        return listOf(
            MuscleLoad(MuscleMap.CHEST_UPPER, upper / d, upperVol / d,
                if (lastUpper > 0L) daysBetween(lastUpper, now) else -1, MuscleMap.weeklyTarget(MuscleMap.CHEST_UPPER)),
            MuscleLoad(MuscleMap.CHEST_LOWER, lower / d, lowerVol / d,
                if (lastLower > 0L) daysBetween(lastLower, now) else -1, MuscleMap.weeklyTarget(MuscleMap.CHEST_LOWER))
        )
    }

    /** Kas bazlı tazelik: 0 = bugün çalışıldı, 1 = tamamen dinlenmiş. */
    fun freshness(loads: List<MuscleLoad>): Map<String, Float> =
        loads.associate { load ->
            val need = MuscleMap.recoveryDays(load.key).toFloat()
            val f = when {
                load.daysSince < 0 -> 1f
                need <= 0f -> 1f
                else -> (load.daysSince / need).coerceIn(0f, 1f)
            }
            load.key to f
        }

    /**
     * Belirli bir kas bölgesine (örn. "chest", "triceps") veri ve hacim sağlayan hareketler.
     */
    fun muscleContributors(
        muscleKey: String,
        sets: List<WorkoutSetEntity>,
        exercises: List<ExerciseEntity>,
        routineItems: List<RoutineItemEntity>,
        routineDays: List<RoutineDayEntity>,
        sinceMillis: Long,
        periodWeeks: Float = 1f
    ): List<MuscleContributor> {
        val exMap = exercises.associateBy { it.id }
        val dayMap = routineDays.associateBy { it.id }

        // Aktif programdaki hareketlerin hangi günlerde kaç set planlandığı
        val routinePlanMap = HashMap<Long, MutableList<String>>()
        routineItems.forEach { item ->
            val dayName = dayMap[item.dayId]?.name ?: "Program"
            val info = "$dayName (${item.targetSets} set)"
            routinePlanMap.getOrPut(item.exerciseId) { mutableListOf() }.add(info)
        }

        val validSets = sets.filter { Analytics.isEffectiveSet(it) && it.performedAt >= sinceMillis }
        val setsByEx = validSets.groupBy { it.exerciseId }

        val result = mutableListOf<MuscleContributor>()
        val processedExIds = mutableSetOf<Long>()

        // 1. Tüm egzersizler listesini tara
        exercises.forEach { ex ->
            val act = MuscleMap.resolve(ex.name, ex.muscleGroup, ex.secondaryMuscles)
            val isPrimary = muscleKey in act.primary
            val isSecondary = muscleKey in act.secondary

            if (isPrimary || isSecondary) {
                processedExIds.add(ex.id)
                val weight = if (isPrimary) 1.0f else 0.5f
                val exSets = setsByEx[ex.id] ?: emptyList()
                val performedCount = exSets.size
                val divisor = if (periodWeeks <= 0f) 1f else periodWeeks
                val effective = (performedCount * weight) / divisor
                val vol = exSets.sumOf { (it.weightKg * it.reps * weight).toDouble() }.toFloat() / divisor
                val lastDate = exSets.maxOfOrNull { it.performedAt } ?: 0L
                val plan = routinePlanMap[ex.id] ?: emptyList()

                if (performedCount > 0 || plan.isNotEmpty()) {
                    result.add(
                        MuscleContributor(
                            exerciseId = ex.id,
                            exerciseName = ex.name,
                            muscleGroup = ex.muscleGroup,
                            isPrimary = isPrimary,
                            performedSetCount = performedCount,
                            effectiveContribution = effective,
                            totalVolumeContribution = vol,
                            inRoutineDays = plan,
                            lastPerformedAt = lastDate
                        )
                    )
                }
            }
        }

        // 2. Egzersiz listesinde id'si bulunmayan ancak sets içinde olan hareketler
        setsByEx.forEach { (exId, exSets) ->
            if (exId !in processedExIds && exSets.isNotEmpty()) {
                val firstName = exSets.first().exerciseName
                val act = MuscleMap.resolve(firstName)
                val isPrimary = muscleKey in act.primary
                val isSecondary = muscleKey in act.secondary
                if (isPrimary || isSecondary) {
                    val weight = if (isPrimary) 1.0f else 0.5f
                    val performedCount = exSets.size
                    val divisor = if (periodWeeks <= 0f) 1f else periodWeeks
                    val effective = (performedCount * weight) / divisor
                    val vol = exSets.sumOf { (it.weightKg * it.reps * weight).toDouble() }.toFloat() / divisor
                    val lastDate = exSets.maxOfOrNull { it.performedAt } ?: 0L
                    result.add(
                        MuscleContributor(
                            exerciseId = exId,
                            exerciseName = firstName,
                            muscleGroup = "",
                            isPrimary = isPrimary,
                            performedSetCount = performedCount,
                            effectiveContribution = effective,
                            totalVolumeContribution = vol,
                            inRoutineDays = emptyList(),
                            lastPerformedAt = lastDate
                        )
                    )
                }
            }
        }

        return result.sortedWith(
            compareByDescending<MuscleContributor> { it.effectiveContribution }
                .thenByDescending { it.performedSetCount }
                .thenBy { it.exerciseName }
        )
    }

    /* ---------------------------- Yüklenme dengesi --------------------------- */

    fun trainingLoad(workouts: List<WorkoutEntity>, sets: List<WorkoutSetEntity>): TrainingLoad {
        val now = System.currentTimeMillis()
        val valid = sets.filter { Analytics.isEffectiveSet(it) }
        val byWorkout = valid.groupBy { it.workoutId }

        fun volumeBetween(fromDaysAgo: Int, toDaysAgo: Int): Float {
            val from = now - fromDaysAgo * DAY
            val to = now - toDaysAgo * DAY
            return workouts.filter { it.startedAt in from..to }
                .sumOf { w ->
                    (byWorkout[w.id] ?: emptyList()).sumOf { (it.weightKg * it.reps).toDouble() }
                }.toFloat()
        }

        val acute = volumeBetween(7, 0)
        val chronicTotal = volumeBetween(28, 0)
        val chronicWeekly = chronicTotal / 4f
        val acuteSets = workouts.filter { it.startedAt >= now - 7 * DAY }
            .sumOf { (byWorkout[it.id] ?: emptyList()).size }

        return TrainingLoad(
            acuteVolume = acute,
            chronicWeekly = chronicWeekly,
            acuteSets = acuteSets,
            ratio = if (chronicWeekly <= 0f) 0f else acute / chronicWeekly
        )
    }

    /* ------------------------------- Durağanlık ------------------------------ */

    /**
     * Belirli süredir tahmini 1RM rekorunu kıramayan hareketler.
     * En az [minSessions] seans kaydı olan hareketlere bakılır.
     */
    fun stagnation(
        sets: List<WorkoutSetEntity>,
        minSessions: Int = 3,
        minDays: Int = 21
    ): List<StagnantLift> {
        val now = System.currentTimeMillis()
        return sets
            .filter { Analytics.isEffectiveSet(it) && it.weightKg > 0f && it.reps > 0 }
            .groupBy { it.exerciseId }
            .mapNotNull { (exId, list) ->
                val bySession = list.groupBy { it.workoutId }
                if (bySession.size < minSessions) return@mapNotNull null
                val sessionBest = bySession.map { (_, l) ->
                    l.minOf { it.performedAt } to l.maxOf { Calc.e1rm(it.weightKg, it.reps) }
                }.sortedBy { it.first }

                val best = sessionBest.maxByOrNull { it.second } ?: return@mapNotNull null
                val latest = sessionBest.last()
                val daysSinceBest = daysBetween(best.first, now)
                if (daysSinceBest < minDays) return@mapNotNull null
                val sessionsSince = sessionBest.count { it.first > best.first }
                if (sessionsSince < 2) return@mapNotNull null

                StagnantLift(
                    exerciseId = exId,
                    name = list.first().exerciseName,
                    bestE1rm = best.second,
                    latestE1rm = latest.second,
                    daysSinceBest = daysSinceBest,
                    sessionsSinceBest = sessionsSince
                )
            }
            .sortedByDescending { it.daysSinceBest }
    }

    /** Son 3 seansta 1RM artışı olan hareketler — "iyi gidiyor" listesi. */
    fun improving(sets: List<WorkoutSetEntity>, minSessions: Int = 3): List<StagnantLift> =
        sets
            .filter { Analytics.isEffectiveSet(it) && it.weightKg > 0f && it.reps > 0 }
            .groupBy { it.exerciseId }
            .mapNotNull { (exId, list) ->
                val bySession = list.groupBy { it.workoutId }
                if (bySession.size < minSessions) return@mapNotNull null
                val sessionBest = bySession.map { (_, l) ->
                    l.minOf { it.performedAt } to l.maxOf { Calc.e1rm(it.weightKg, it.reps) }
                }.sortedBy { it.first }
                val latest = sessionBest.last()
                val previousBest = sessionBest.dropLast(1).maxOfOrNull { it.second } ?: return@mapNotNull null
                if (latest.second <= previousBest + 0.01f) return@mapNotNull null
                StagnantLift(
                    exerciseId = exId,
                    name = list.first().exerciseName,
                    bestE1rm = latest.second,
                    latestE1rm = latest.second,
                    daysSinceBest = daysBetween(latest.first, System.currentTimeMillis()),
                    sessionsSinceBest = 0
                )
            }
            .sortedByDescending { it.bestE1rm }

    /* -------------------------------- Güç profili --------------------------- */

    private data class LiftDef(
        val key: String,
        val display: String,
        val matches: List<String>,
        val thresholds: List<Float>,
        val upperBody: Boolean,
        /** Adında bunlardan biri geçen hareketler bu lifte sayılmaz. */
        val excludes: List<String> = emptyList(),
        /**
         * Dambıl varyasyonu: tek dambılın tahmini 1RM'i × 2 × bu çarpan ≈ barbell karşılığı.
         * 0 = dambıl varyasyonu kabul edilmez. (İki dambıl, dengeleme ihtiyacı yüzünden
         * genelde barbell'den biraz daha az kaldırılır; çarpan bu farkı yaklaşık düzeltir.)
         */
        val dumbbellFactor: Float = 0f
    )

    private val liftDefs = listOf(
        LiftDef("squat", "Squat", listOf("barbell squat", "squat"), listOf(0.75f, 1.1f, 1.45f, 1.9f, 2.4f), false,
            excludes = listOf("goblet", "split", "pistol", "sissy", "bodyweight", "wall sit", "cossack")),
        LiftDef("bench", "Bench Press", listOf("bench press"), listOf(0.5f, 0.75f, 1.0f, 1.35f, 1.75f), true,
            excludes = listOf("close grip", "incline", "decline"), dumbbellFactor = 0.85f),
        LiftDef("deadlift", "Deadlift", listOf("deadlift"), listOf(1.0f, 1.35f, 1.75f, 2.2f, 2.75f), false,
            excludes = listOf("romanian", "rdl", "stiff", "single leg")),
        // RDL klasik deadlift'ten doğası gereği hafiftir: kendi eşik ve beklenen oranıyla değerlendirilir.
        LiftDef("rdl", "Romanian Deadlift", listOf("romanian", "rdl"), listOf(0.8f, 1.1f, 1.4f, 1.8f, 2.2f), false,
            dumbbellFactor = 0.9f),
        LiftDef("ohp", "Overhead Press", listOf("overhead press", "push press", "shoulder press"), listOf(0.35f, 0.5f, 0.7f, 0.9f, 1.15f), true,
            excludes = listOf("machine", "makine", "smith"), dumbbellFactor = 0.85f),
        // Tek kol dambıl row'da gövde desteği daha fazla yük taşıtır; çarpan buna göre düşük tutuldu.
        LiftDef("row", "Row", listOf("barbell row", "pendlay row", "dumbbell row"), listOf(0.5f, 0.75f, 1.0f, 1.3f, 1.6f), true,
            dumbbellFactor = 0.75f)
    )

    private fun isDumbbell(n: String) = n.contains("dumbbell") || n.contains("dambıl") || n.contains("db ")

    private val levelNames = listOf("Başlangıç", "Acemi", "Orta", "İleri", "Çok İleri", "Elit")

    /**
     * Ana hareketler için vücut ağırlığına göre güç seviyesi.
     * Eşikler kaba referanslardır; kesin bir standart değil.
     */
    fun strengthProfile(
        sets: List<WorkoutSetEntity>,
        bodyWeightKg: Float,
        isMale: Boolean
    ): List<LiftStandard> {
        if (bodyWeightKg <= 0f) return emptyList()
        val valid = sets.filter { Analytics.isEffectiveSet(it) && it.weightKg > 0f && it.reps > 0 }
        if (valid.isEmpty()) return emptyList()

        return liftDefs.mapNotNull { def ->
            // Türkçe küçük harf "I" → "ı" yapar; Latin karşılaştırma için ı → i.
            fun norm(x: String) = x.lowercase(TR).replace('ı', 'i')
            val candidates = valid.filter { s ->
                val n = norm(s.exerciseName)
                def.matches.any { n.contains(it) } &&
                    def.excludes.none { n.contains(it) } &&
                    !n.contains("goblet") && !n.contains("boş bar") &&
                    (!isDumbbell(n) || def.dumbbellFactor > 0f)
            }
            if (candidates.isEmpty()) return@mapNotNull null
            // Dambıl setleri tahmini barbell karşılığına çevrilir.
            fun equiv(s: WorkoutSetEntity): Float {
                val raw = Calc.e1rm(s.weightKg, s.reps)
                return if (isDumbbell(norm(s.exerciseName))) raw * 2f * def.dumbbellFactor else raw
            }
            val best = candidates.maxByOrNull { equiv(it) } ?: return@mapNotNull null
            val e1rm = equiv(best)
            if (e1rm <= 0f) return@mapNotNull null
            val fromDumbbell = isDumbbell(norm(best.exerciseName))

            val genderFactor = if (isMale) 1f else if (def.upperBody) 0.68f else 0.78f
            val thresholds = def.thresholds.map { it * genderFactor }
            val ratio = e1rm / bodyWeightKg
            var idx = 0
            thresholds.forEach { if (ratio >= it) idx++ }
            val next = if (idx < thresholds.size) levelNames[idx + 1] else null
            val nextWeight = if (idx < thresholds.size) thresholds[idx] * bodyWeightKg else 0f

            LiftStandard(
                liftKey = def.key,
                displayName = if (fromDumbbell) best.exerciseName else def.display,
                exerciseId = best.exerciseId,
                e1rm = e1rm,
                bodyweightRatio = ratio,
                level = levelNames[idx],
                levelIndex = idx,
                nextLevel = next,
                nextLevelWeight = nextWeight
            )
        }
    }

    /** Squat'a göre tipik lift oranları — zayıf halkayı bulmak için. */
    fun liftBalance(profile: List<LiftStandard>): List<LiftBalanceItem> {
        val squat = profile.firstOrNull { it.liftKey == "squat" }?.e1rm ?: return emptyList()
        if (squat <= 0f) return emptyList()
        val expected = mapOf(
            "bench" to 0.75f,
            "deadlift" to 1.2f,
            "rdl" to 0.95f,
            "ohp" to 0.45f,
            "row" to 0.6f
        )
        return profile.filter { it.liftKey != "squat" }.mapNotNull { lift ->
            val exp = expected[lift.liftKey] ?: return@mapNotNull null
            val actual = lift.e1rm / squat
            LiftBalanceItem(
                displayName = lift.displayName,
                actualRatio = actual,
                expectedRatio = exp,
                deviationPct = (actual - exp) / exp * 100f
            )
        }
    }

    /** SBD toplamı (squat + bench + deadlift tahmini 1RM). */
    fun totalScore(profile: List<LiftStandard>): Float =
        profile.filter { it.liftKey in listOf("squat", "bench", "deadlift") }.sumOf { it.e1rm.toDouble() }.toFloat()

    /* ------------------------------ Tekrar dağılımı -------------------------- */

    fun repRangeDistribution(sets: List<WorkoutSetEntity>): List<RepRangeSlice> {
        val valid = sets.filter { Analytics.isEffectiveSet(it) && it.reps > 0 }
        val total = valid.size
        if (total == 0) return emptyList()
        val buckets = listOf(
            Triple("1-5", 1..5, "Maksimal güç"),
            Triple("6-8", 6..8, "Güç + kas"),
            Triple("9-12", 9..12, "Hipertrofi"),
            Triple("13-20", 13..20, "Kas dayanıklılığı"),
            Triple("20+", 21..999, "Dayanıklılık")
        )
        return buckets.map { (label, range, purpose) ->
            val c = valid.count { it.reps in range }
            RepRangeSlice(label, c, c.toFloat() / total, purpose)
        }.filter { it.sets > 0 }
    }

    /** Haftalık ortalama RPE serisi (girilmiş RPE'ler üzerinden). */
    fun weeklyRpe(sets: List<WorkoutSetEntity>, weeks: Int = 8): List<Pair<String, Float>> {
        val valid = sets.filter { Analytics.isEffectiveSet(it) && it.rpe > 0f }
        if (valid.isEmpty()) return emptyList()
        val currentWeek = startOfWeek(System.currentTimeMillis())
        return (weeks - 1 downTo 0).mapNotNull { i ->
            val start = currentWeek - i * 7 * DAY
            val end = start + 7 * DAY
            val inWeek = valid.filter { it.performedAt in start until end }
            if (inWeek.isEmpty()) null
            else formatDateShort(start) to inWeek.map { it.rpe }.average().toFloat()
        }
    }

    /* -------------------------------- Tutarlılık ---------------------------- */

    fun adherence(workouts: List<WorkoutEntity>, weeklyGoal: Int, weeks: Int = 8): List<WeekAdherence> {
        val currentWeek = startOfWeek(System.currentTimeMillis())
        return (weeks - 1 downTo 0).map { i ->
            val start = currentWeek - i * 7 * DAY
            val end = start + 7 * DAY
            WeekAdherence(
                label = formatDateShort(start),
                workouts = workouts.count { it.startedAt in start until end },
                goal = weeklyGoal
            )
        }
    }

    /** Hedefe ulaşılan hafta oranı (%). */
    fun adherencePct(list: List<WeekAdherence>): Int {
        if (list.isEmpty()) return 0
        return (list.count { it.met } * 100f / list.size).roundToInt()
    }

    /* ---------------------------- Hareket içgörüleri ------------------------- */

    data class BestSetInRange(val range: String, val weight: Float, val reps: Int, val e1rm: Float, val dateMillis: Long)

    /** Bir hareket için tekrar aralığı başına en iyi set. */
    fun bestSetsByRange(sets: List<WorkoutSetEntity>): List<BestSetInRange> {
        val valid = sets.filter { Analytics.isEffectiveSet(it) && it.reps > 0 && it.weightKg > 0f }
        if (valid.isEmpty()) return emptyList()
        val buckets = listOf("1-5" to 1..5, "6-8" to 6..8, "9-12" to 9..12, "13+" to 13..999)
        return buckets.mapNotNull { (label, range) ->
            val best = valid.filter { it.reps in range }.maxByOrNull { it.weightKg } ?: return@mapNotNull null
            BestSetInRange(label, best.weightKg, best.reps, Calc.e1rm(best.weightKg, best.reps), best.performedAt)
        }
    }

    /**
     * Bir hareketin son [window] seansındaki 1RM eğilimi (yüzde).
     * Pozitif = ilerleme.
     */
    fun trendPct(sets: List<WorkoutSetEntity>, window: Int = 4): Float {
        val bySession = sets
            .filter { Analytics.isEffectiveSet(it) && it.reps > 0 && it.weightKg > 0f }
            .groupBy { it.workoutId }
            .map { (_, l) -> l.minOf { it.performedAt } to l.maxOf { Calc.e1rm(it.weightKg, it.reps) } }
            .sortedBy { it.first }
        if (bySession.size < 2) return 0f
        val recent = bySession.takeLast(window)
        val first = recent.first().second
        val last = recent.last().second
        if (first <= 0f) return 0f
        return (last - first) / first * 100f
    }

    /** Toplam kaldırılan ağırlığı somut karşılaştırmaya çevirir. */
    fun tonnageComparison(totalKg: Float): String = when {
        totalKg <= 0f -> "—"
        totalKg < 5_000 -> "${(totalKg / 100f).roundToInt()} adet 100 kg'lık dolap"
        totalKg < 60_000 -> "${(totalKg / 1_400f * 10).roundToInt() / 10f} adet otomobil"
        totalKg < 500_000 -> "${(totalKg / 6_000f * 10).roundToInt() / 10f} adet Afrika fili"
        else -> "${(totalKg / 180_000f * 10).roundToInt() / 10f} adet mavi balina"
    }

    /** İki değer arasındaki yüzde değişimi, işaretli metin olarak. */
    fun deltaText(current: Float, previous: Float): String = when {
        previous <= 0f -> "—"
        abs(current - previous) < 0.01f -> "değişim yok"
        current > previous -> "+%${((current - previous) / previous * 100).roundToInt()}"
        else -> "-%${((previous - current) / previous * 100).roundToInt()}"
    }
}
