package com.example.core

import com.example.data.ExerciseEntity
import com.example.data.RoutineDayEntity
import com.example.data.RoutineItemEntity
import com.example.data.WorkoutEntity
import com.example.data.WorkoutSetEntity
import kotlin.math.ceil
import kotlin.math.roundToInt

/* ==========================================================================
 * Güç ve kas içgörüleri (2.28)
 *   - 1RM eğilimi (doğrusal regresyon) ve hedefe tahmini süre
 *   - Tekrar–ağırlık tablosu (tahmini + gerçek en iyi)
 *   - Plato tespiti ve somut öneri
 *   - Kas × hafta set ısı haritası
 *   - Program uyumu (plan ↔ gerçekleşen)
 *   - Kasın güç göstergesi
 * ========================================================================== */

object StrengthInsights {

    const val DAY_MS = 86_400_000L
    const val WEEK_MS = 7 * DAY_MS

    /* ------------------------------ Eğilim ------------------------------ */

    /** v(t) = intercept + slope × (t − t0) / hafta */
    data class Trend(val slopePerWeek: Float, val intercept: Float, val t0: Long) {
        fun at(t: Long): Float = intercept + slopePerWeek * ((t - t0).toFloat() / WEEK_MS)
        /** Hedefe kaç hafta (bugünkü eğilim sürerse); ulaşılmazsa null. */
        fun weeksTo(target: Float, now: Long = System.currentTimeMillis()): Float? {
            val cur = at(now)
            if (target <= cur) return 0f
            if (slopePerWeek <= 0.05f) return null
            return (target - cur) / slopePerWeek
        }
    }

    /** En küçük kareler; en az 3 nokta ve 10 günlük aralık gerekir. */
    fun linearTrend(points: List<Pair<Long, Float>>): Trend? {
        if (points.size < 3) return null
        val t0 = points.first().first
        if (points.last().first - t0 < 10 * DAY_MS) return null
        val xs = points.map { (it.first - t0).toDouble() / WEEK_MS }
        val ys = points.map { it.second.toDouble() }
        val mx = xs.average(); val my = ys.average()
        var num = 0.0; var den = 0.0
        for (i in xs.indices) { num += (xs[i] - mx) * (ys[i] - my); den += (xs[i] - mx) * (xs[i] - mx) }
        if (den <= 0.0) return null
        val slope = num / den
        return Trend(slope.toFloat(), (my - slope * mx).toFloat(), t0)
    }

    /* ------------------------- Tekrar–ağırlık tablosu ------------------------- */

    data class RepMaxRow(val reps: Int, val predicted: Float, val pct: Int, val bestActual: Float?, val bestDate: Long?)

    /** e1RM'den [reps] tekrar için tahmini ağırlık (formüller ağırlıkla doğrusal). */
    fun weightFor(e1rm: Float, reps: Int): Float =
        if (reps <= 1) e1rm else e1rm / Calc.e1rm(1f, reps).coerceAtLeast(0.01f)

    fun repMaxTable(
        sets: List<WorkoutSetEntity>,
        e1rm: Float,
        round: (Float) -> Float = { it },
        reps: List<Int> = listOf(1, 2, 3, 4, 5, 6, 8, 10, 12, 15)
    ): List<RepMaxRow> {
        val valid = sets.filter { Analytics.isEffectiveSet(it) && it.weightKg > 0f && it.reps > 0 }
        return reps.map { r ->
            val best = valid.filter { it.reps >= r }.maxByOrNull { it.weightKg }
            val w = weightFor(e1rm, r)
            RepMaxRow(r, round(w), (w / e1rm * 100f).roundToInt(), best?.weightKg, best?.performedAt)
        }
    }

    /* ------------------------------- Plato ------------------------------- */

    data class Plateau(val lift: StagnantLift, val weeks: Int, val suggestion: String)

    fun plateaus(sets: List<WorkoutSetEntity>, exercises: List<ExerciseEntity>, now: Long = System.currentTimeMillis()): List<Plateau> {
        val exMap = exercises.associateBy { it.id }
        val recent = sets.filter { now - it.performedAt < 30 * DAY_MS }.map { it.exerciseId }.toSet()
        return ProgressAnalytics.stagnation(sets).filter { it.exerciseId in recent }.map { s ->
            val mine = sets.filter { it.exerciseId == s.exerciseId && Analytics.isEffectiveSet(it) && it.reps > 0 }
            val last3 = mine.groupBy { it.workoutId }.values.sortedBy { l -> l.minOf { it.performedAt } }.takeLast(3).flatten()
            val avgReps = if (last3.isEmpty()) 0.0 else last3.map { it.reps }.average()
            val ex = exMap[s.exerciseId]
            val iso = ex != null && RepScheme.classify(ex.name, ex.muscleGroup, ex.equipment, ex.trackingType) == RepScheme.ISOLATION
            val suggestion = when {
                s.dropPct >= 5f -> "Son seanslarda ${s.dropPct.roundToInt()}% düşüş var: yorgunluk birikmiş olabilir. 1 hafta hafif (deload) çalış, sonra devam et."
                avgReps <= 6.0 -> "Hep düşük tekrarda kalmışsın (ort. ${avgReps.roundToInt()}). 3–4 hafta 8–12 tekrar bloğu yap; hacim sonraki güç artışının zeminini hazırlar."
                avgReps >= 12.0 && !iso -> "Hep yüksek tekrarda çalışıyorsun (ort. ${avgReps.roundToInt()}). Ağırlığı artırıp 5–8 tekrar bloğuna geç."
                iso -> "İzole harekette küçük adımlarla ilerle: önce tekrarı artır, üst sınıra gelince en küçük ağırlık artışını yap."
                else -> "Küçük artışlarla ilerle (mikro plaka) ya da 3–4 hafta bir varyasyona geçip geri dön."
            }
            Plateau(s, (s.daysSinceBest / 7).coerceAtLeast(3), suggestion)
        }.sortedByDescending { it.weeks }
    }

    /* ------------------------- Kas × hafta ısı haritası ------------------------- */

    data class MuscleGrid(val weekStarts: List<Long>, val rows: Map<String, List<Float>>)

    fun muscleWeekGrid(
        sets: List<WorkoutSetEntity>,
        exercises: List<ExerciseEntity>,
        weeks: Int = 8,
        now: Long = System.currentTimeMillis()
    ): MuscleGrid {
        val exMap = exercises.associateBy { it.id }
        val ws = startOfWeek(now)
        val starts = (weeks - 1 downTo 0).map { ws - it * WEEK_MS }
        val from = starts.first()
        val rows = HashMap<String, FloatArray>()
        val wCache = HashMap<Long, Map<String, Float>>()
        sets.filter { it.performedAt >= from && Analytics.isEffectiveSet(it) }.forEach { s ->
            val idx = ((s.performedAt - from) / WEEK_MS).toInt().coerceIn(0, weeks - 1)
            val w = wCache.getOrPut(s.exerciseId) {
                val ex = exMap[s.exerciseId]
                if (ex == null) emptyMap() else MuscleMap.resolve(ex.name, ex.muscleGroup, ex.secondaryMuscles).weights()
            }
            w.forEach { (m, f) -> rows.getOrPut(m) { FloatArray(weeks) }[idx] += f }
        }
        return MuscleGrid(starts, MuscleMap.all.associateWith { k -> rows[k]?.toList() ?: List(weeks) { 0f } })
    }

    /* ------------------------------ Program uyumu ------------------------------ */

    data class SkippedItem(val name: String, val day: String, val done: Int, val of: Int)
    data class MuscleGap(val key: String, val planned: Float, val actual: Float)
    data class Adherence(
        val weeks: Int,
        val sessionsDone: Int,
        val sessionsPlanned: Int,
        val setsDone: Int,
        val setsPlanned: Int,
        val skipped: List<SkippedItem>,
        val gaps: List<MuscleGap>
    ) {
        val sessionPct: Int get() = if (sessionsPlanned == 0) 0 else (sessionsDone * 100f / sessionsPlanned).roundToInt().coerceAtMost(100)
        val setPct: Int get() = if (setsPlanned == 0) 0 else (setsDone * 100f / setsPlanned).roundToInt().coerceAtMost(100)
        val score: Int get() = ((sessionPct + setPct) / 2f).roundToInt()
    }

    fun adherence(
        days: List<RoutineDayEntity>,
        items: List<RoutineItemEntity>,
        workouts: List<WorkoutEntity>,
        sets: List<WorkoutSetEntity>,
        exercises: List<ExerciseEntity>,
        now: Long = System.currentTimeMillis()
    ): Adherence? {
        if (days.isEmpty()) return null
        val dayIds = days.map { it.id }.toSet()
        val work = items.filter { it.dayId in dayIds && !it.isWarmup }
        if (work.isEmpty()) return null
        val progWorkouts = workouts.filter { it.isFinished && it.routineDayId in dayIds }
        val first = progWorkouts.minOfOrNull { it.startedAt } ?: return null
        val weeks = ceil((now - first).toDouble() / WEEK_MS).toInt().coerceIn(1, 4)
        val from = now - weeks * WEEK_MS
        val inWin = progWorkouts.filter { it.startedAt >= from }
        val setsByW = sets.filter { Analytics.isEffectiveSet(it) }.groupBy { it.workoutId }
        val exMap = exercises.associateBy { it.id }
        val dayName = days.associate { it.id to it.name }

        var planned = 0; var done = 0
        inWin.forEach { w ->
            val its = work.filter { it.dayId == w.routineDayId }
            val ws = setsByW[w.id].orEmpty().groupBy { it.exerciseId }
            its.forEach { i ->
                planned += i.targetSets
                done += minOf(ws[i.exerciseId]?.size ?: 0, i.targetSets)
            }
        }
        val skipped = work.mapNotNull { i ->
            val dw = inWin.filter { it.routineDayId == i.dayId }
            if (dw.size < 2) return@mapNotNull null
            val n = dw.count { w -> setsByW[w.id].orEmpty().any { it.exerciseId == i.exerciseId } }
            if (n * 2 >= dw.size) null
            else SkippedItem(i.customName.ifBlank { exMap[i.exerciseId]?.name ?: "" }, dayName[i.dayId] ?: "", n, dw.size)
        }.sortedBy { it.done.toFloat() / it.of }

        // Kas: planlanan haftalık set ↔ penceredeki haftalık ortalama
        val plannedM = HashMap<String, Float>()
        work.forEach { i ->
            val ex = exMap[i.exerciseId] ?: return@forEach
            MuscleMap.resolve(i.customName.ifBlank { ex.name }, ex.muscleGroup, ex.secondaryMuscles).weights()
                .forEach { (m, f) -> plannedM[m] = (plannedM[m] ?: 0f) + f * i.targetSets }
        }
        val actualM = HashMap<String, Float>()
        sets.filter { it.performedAt >= from && Analytics.isEffectiveSet(it) }.forEach { s ->
            val ex = exMap[s.exerciseId] ?: return@forEach
            MuscleMap.resolve(ex.name, ex.muscleGroup, ex.secondaryMuscles).weights()
                .forEach { (m, f) -> actualM[m] = (actualM[m] ?: 0f) + f / weeks }
        }
        val gaps = plannedM.filter { it.value >= 3f }.map { (k, p) -> MuscleGap(k, p, actualM[k] ?: 0f) }
            .filter { it.actual < it.planned * 0.75f }
            .sortedByDescending { it.planned - it.actual }
            .take(4)

        return Adherence(weeks, inWin.size, days.size * weeks, done, planned, skipped.take(4), gaps)
    }

    /* ---------------------------- Kasın güç göstergesi ---------------------------- */

    data class MuscleStrength(val exerciseId: Long, val name: String, val e1rm: Float, val changePct: Float?, val points: List<Float>)

    /** Kası (birincil) en çok çalıştıran hareketin son 8 haftadaki 1RM gidişatı. */
    fun muscleStrength(
        key: String,
        sets: List<WorkoutSetEntity>,
        exercises: List<ExerciseEntity>,
        now: Long = System.currentTimeMillis()
    ): MuscleStrength? {
        val from = now - 8 * WEEK_MS
        val exMap = exercises.associateBy { it.id }
        val cand = sets.filter { it.performedAt >= from && Analytics.isEffectiveSet(it) && it.weightKg > 0f && it.reps in 1..20 }
            .groupBy { it.exerciseId }
            .filter { (id, _) ->
                val ex = exMap[id] ?: return@filter false
                (MuscleMap.resolve(ex.name, ex.muscleGroup, ex.secondaryMuscles).weights()[key] ?: 0f) >= 0.75f
            }
            .maxByOrNull { it.value.size } ?: return null
        val sessions = cand.value.groupBy { it.workoutId }.values
            .map { l -> l.minOf { it.performedAt } to l.maxOf { Calc.e1rm(it.weightKg, it.reps) } }
            .sortedBy { it.first }
        if (sessions.isEmpty()) return null
        val pts = sessions.map { it.second }
        val change = if (pts.size >= 2) {
            val a = pts.take(2).max(); val b = pts.takeLast(2).max()
            if (a > 0f) (b - a) / a * 100f else null
        } else null
        return MuscleStrength(cand.key, exMap[cand.key]?.name ?: cand.value.first().exerciseName, pts.max(), change, pts)
    }

    /* ---------------------------- Toplam güç değişimi ---------------------------- */

    /** S+B+D toplamının [weeksAgo] hafta önceki değere göre değişimi (kg). */
    fun totalChange(sets: List<WorkoutSetEntity>, bw: Float, male: Boolean, weeksAgo: Int = 12, now: Long = System.currentTimeMillis()): Float? {
        val before = sets.filter { it.performedAt < now - weeksAgo * WEEK_MS }
        val old = ProgressAnalytics.totalScore(ProgressAnalytics.strengthProfile(before, bw, male))
        val cur = ProgressAnalytics.totalScore(ProgressAnalytics.strengthProfile(sets, bw, male))
        return if (old <= 0f || cur <= 0f) null else cur - old
    }
}

/* ---------------------------- İlerlemede görünen hareketler ---------------------------- */

object ExerciseVisibility {
    /** Programda olmayan ve bu kadar gündür yapılmayan hareketler ilerleme ekranlarında gizlenir. */
    const val GRACE_DAYS = 14

    /**
     * Aktif programdaki hareketler + son [GRACE_DAYS] günde yapılanlar.
     * Aktif program boşsa null (filtre yok). Hareket programa geri eklenince eski kayıtları da geri gelir.
     */
    fun visible(
        activeDays: List<RoutineDayEntity>,
        items: List<RoutineItemEntity>,
        sets: List<WorkoutSetEntity>,
        now: Long = System.currentTimeMillis()
    ): Set<Long>? {
        val dayIds = activeDays.map { it.id }.toSet()
        val inProgram = items.filter { it.dayId in dayIds }.map { it.exerciseId }.toSet()
        if (inProgram.isEmpty()) return null
        val cutoff = now - GRACE_DAYS * StrengthInsights.DAY_MS
        return inProgram + sets.filter { it.performedAt >= cutoff }.map { it.exerciseId }
    }
}
