package com.example.core

import com.example.data.ExerciseEntity
import com.example.data.ExerciseProgressPoint
import com.example.data.MuscleVolume
import com.example.data.WorkoutEntity
import com.example.data.WorkoutSetEntity
import java.util.Calendar

/** Ana ekran / istatistik ekranı için özet metrikler. */
data class DashboardStats(
    val totalWorkouts: Int = 0,
    val thisWeekWorkouts: Int = 0,
    val lastWeekWorkouts: Int = 0,
    val thisWeekVolume: Float = 0f,
    val lastWeekVolume: Float = 0f,
    val thisWeekSets: Int = 0,
    val totalVolume: Float = 0f,
    val totalSets: Int = 0,
    val totalDurationSec: Int = 0,
    val avgDurationSec: Int = 0,
    val streakWeeks: Int = 0,
    val streakDays: Int = 0,
    val lastWorkoutMillis: Long = 0L,
    val daysSinceLast: Int = -1
)

data class PeriodPoint(
    val startMillis: Long,
    val label: String,
    val volume: Float,
    val sets: Int,
    val reps: Int,
    val workouts: Int,
    val durationSec: Int
)

object Analytics {

    /** Bir setin hacim/istatistik hesaplamalarına dahil edilmesi için geçerli olup olmadığı. */
    fun isEffectiveSet(s: WorkoutSetEntity): Boolean =
        !s.isWarmup && (s.isCompleted || s.reps > 0 || s.weightKg > 0f || s.durationSeconds > 0) && (s.reps > 0 || s.weightKg > 0f || s.durationSeconds > 0)

    private fun getMillis(timestamp: Long): Long {
        if (timestamp <= 0L) return System.currentTimeMillis()
        return if (timestamp < 10_000_000_000L) timestamp * 1000L else timestamp
    }

    fun dashboard(workouts: List<WorkoutEntity>, sets: List<WorkoutSetEntity>): DashboardStats {
        if (workouts.isEmpty()) return DashboardStats()
        val now = System.currentTimeMillis()
        val weekStart = startOfWeek(now)
        val prevWeekStart = weekStart - 7 * 86_400_000L

        val validSets = sets.filter { isEffectiveSet(it) }
        val byWorkout = validSets.groupBy { it.workoutId }

        fun volumeOf(w: WorkoutEntity): Float =
            byWorkout[w.id]?.sumOf { (it.weightKg * it.reps).toDouble() }?.toFloat() ?: 0f

        fun setCountOf(w: WorkoutEntity): Int = byWorkout[w.id]?.size ?: 0

        val thisWeek = workouts.filter { it.startedAt >= weekStart }
        val lastWeek = workouts.filter { it.startedAt in prevWeekStart until weekStart }

        val totalVolume = validSets.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat()
        val last = workouts.maxByOrNull { it.startedAt }

        return DashboardStats(
            totalWorkouts = workouts.size,
            thisWeekWorkouts = thisWeek.size,
            lastWeekWorkouts = lastWeek.size,
            thisWeekVolume = thisWeek.sumOf { volumeOf(it).toDouble() }.toFloat(),
            lastWeekVolume = lastWeek.sumOf { volumeOf(it).toDouble() }.toFloat(),
            thisWeekSets = thisWeek.sumOf { setCountOf(it) },
            totalVolume = totalVolume,
            totalSets = validSets.size,
            totalDurationSec = workouts.sumOf { it.durationSeconds },
            avgDurationSec = if (workouts.isEmpty()) 0 else workouts.sumOf { it.durationSeconds } / workouts.size,
            streakWeeks = streakWeeks(workouts),
            streakDays = streakDays(workouts),
            lastWorkoutMillis = last?.startedAt ?: 0L,
            daysSinceLast = last?.let { daysBetween(it.startedAt, now) } ?: -1
        )
    }

    /** Kesintisiz antrenman yapılan hafta sayısı (bu hafta boşsa geçen haftadan sayar). */
    fun streakWeeks(workouts: List<WorkoutEntity>): Int {
        if (workouts.isEmpty()) return 0
        val weeks = workouts.map { startOfWeek(it.startedAt) }.toSet()
        var cursor = startOfWeek(System.currentTimeMillis())
        if (!weeks.contains(cursor)) cursor -= 7 * 86_400_000L
        var count = 0
        while (weeks.contains(cursor)) {
            count++
            cursor -= 7 * 86_400_000L
        }
        return count
    }

    /** Kesintisiz gün serisi — arka arkaya antrenman yapılan günler. */
    fun streakDays(workouts: List<WorkoutEntity>): Int {
        if (workouts.isEmpty()) return 0
        val days = workouts.map { startOfDay(it.startedAt) }.toSet()
        var cursor = startOfDay(System.currentTimeMillis())
        if (!days.contains(cursor)) cursor -= 86_400_000L
        var count = 0
        while (days.contains(cursor)) {
            count++
            cursor -= 86_400_000L
        }
        return count
    }

    /** Geçmiş tarihler dahil haftalık hacim/set serisi (en eskiden yeniye). */
    fun weeklySeries(
        workouts: List<WorkoutEntity>,
        sets: List<WorkoutSetEntity>,
        defaultWeeks: Int = 8
    ): List<PeriodPoint> {
        if (workouts.isEmpty()) return emptyList()
        val validSets = sets.filter { isEffectiveSet(it) }
        val byWorkout = validSets.groupBy { it.workoutId }
        val currentWeek = startOfWeek(System.currentTimeMillis())

        val minDate = workouts.minOfOrNull { it.startedAt } ?: System.currentTimeMillis()
        val minWeek = startOfWeek(minDate)
        val weeksBetween = ((currentWeek - minWeek) / (7 * 86_400_000L)).toInt()
        val totalWeeks = maxOf(defaultWeeks, weeksBetween + 1)

        return (totalWeeks - 1 downTo 0).map { i ->
            val start = currentWeek - i * 7 * 86_400_000L
            val end = start + 7 * 86_400_000L
            val ws = workouts.filter { it.startedAt in start until end }
            val s = ws.flatMap { byWorkout[it.id] ?: emptyList() }
            PeriodPoint(
                startMillis = start,
                label = formatDateShort(start),
                volume = s.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat(),
                sets = s.size,
                reps = s.sumOf { it.reps },
                workouts = ws.size,
                durationSec = ws.sumOf { it.durationSeconds }
            )
        }
    }

    /** Geçmiş tarihler dahil aylık seri. */
    fun monthlySeries(
        workouts: List<WorkoutEntity>,
        sets: List<WorkoutSetEntity>,
        defaultMonths: Int = 6
    ): List<PeriodPoint> {
        if (workouts.isEmpty()) return emptyList()
        val validSets = sets.filter { isEffectiveSet(it) }
        val byWorkout = validSets.groupBy { it.workoutId }

        val minDate = workouts.minOfOrNull { it.startedAt } ?: System.currentTimeMillis()
        val currentMonthStart = startOfMonth(System.currentTimeMillis())
        val minMonthStart = startOfMonth(minDate)

        val nowCal = Calendar.getInstance().apply { timeInMillis = currentMonthStart }
        val minCal = Calendar.getInstance().apply { timeInMillis = minMonthStart }
        val monthDiff = (nowCal.get(Calendar.YEAR) - minCal.get(Calendar.YEAR)) * 12 +
                (nowCal.get(Calendar.MONTH) - minCal.get(Calendar.MONTH))
        val totalMonths = maxOf(defaultMonths, monthDiff + 1)

        val cal = Calendar.getInstance()
        return (totalMonths - 1 downTo 0).map { i ->
            cal.timeInMillis = System.currentTimeMillis()
            cal.add(Calendar.MONTH, -i)
            val start = startOfMonth(cal.timeInMillis)
            val endCal = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.MONTH, 1) }
            val end = endCal.timeInMillis
            val ws = workouts.filter { it.startedAt in start until end }
            val s = ws.flatMap { byWorkout[it.id] ?: emptyList() }
            PeriodPoint(
                startMillis = start,
                label = SimpleMonth.short(start),
                volume = s.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat(),
                sets = s.size,
                reps = s.sumOf { it.reps },
                workouts = ws.size,
                durationSec = ws.sumOf { it.durationSeconds }
            )
        }
    }

    private object SimpleMonth {
        fun short(millis: Long): String =
            java.text.SimpleDateFormat("MMM", TR).format(java.util.Date(millis)).trCapitalize()
    }

    /** Belirli tarihten sonraki kas grubu hacim/set dağılımı. */
    fun muscleDistribution(
        sets: List<WorkoutSetEntity>,
        exercises: List<ExerciseEntity>,
        sinceMillis: Long
    ): List<MuscleVolume> {
        val exMap = exercises.associateBy { it.id }
        return sets.asSequence()
            .filter { isEffectiveSet(it) && it.performedAt >= sinceMillis }
            .groupBy { exMap[it.exerciseId]?.muscleGroup ?: Muscles.guess(it.exerciseName) }
            .map { (group, list) ->
                MuscleVolume(
                    muscleGroup = group,
                    sets = list.size,
                    volume = list.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat()
                )
            }
            .sortedByDescending { it.sets }
    }

    /** Bir hareketin seans seans gelişimi. */
    fun exerciseProgress(sets: List<WorkoutSetEntity>): List<ExerciseProgressPoint> =
        sets.filter { isEffectiveSet(it) && it.reps > 0 }
            .groupBy { it.workoutId }
            .map { (_, list) ->
                ExerciseProgressPoint(
                    dateMillis = list.minOf { it.performedAt },
                    topWeight = list.maxOf { it.weightKg },
                    e1rm = list.maxOf { Calc.e1rm(it.weightKg, it.reps) },
                    volume = list.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat(),
                    totalReps = list.sumOf { it.reps },
                    sets = list.size
                )
            }
            .sortedBy { it.dateMillis }

    /** Son [days] gün için 0..4 yoğunluk seviyeleri (en eskiden bugüne). */
    fun heatmapLevels(
        workouts: List<WorkoutEntity>,
        sets: List<WorkoutSetEntity>,
        defaultDays: Int = 84
    ): List<Int> {
        if (workouts.isEmpty()) return emptyList()
        val validSets = sets.filter { isEffectiveSet(it) }
        val byWorkout = validSets.groupBy { it.workoutId }
        val volumeByDay = HashMap<Long, Float>()
        workouts.forEach { w ->
            val day = startOfDay(w.startedAt)
            val v = byWorkout[w.id]?.sumOf { (it.weightKg * it.reps).toDouble() }?.toFloat() ?: 0f
            volumeByDay[day] = (volumeByDay[day] ?: 0f) + maxOf(v, 1f)
        }
        val maxV = volumeByDay.values.maxOrNull() ?: 1f
        val today = startOfDay(System.currentTimeMillis())

        val minDate = workouts.minOfOrNull { it.startedAt } ?: System.currentTimeMillis()
        val minDay = startOfDay(minDate)
        val daysDiff = ((today - minDay) / 86_400_000L).toInt()
        val totalDays = maxOf(defaultDays, daysDiff + 7)

        return (totalDays - 1 downTo 0).map { i ->
            val day = today - i * 86_400_000L
            val v = volumeByDay[day] ?: 0f
            when {
                v <= 0f -> 0
                v < maxV * 0.25f -> 1
                v < maxV * 0.5f -> 2
                v < maxV * 0.75f -> 3
                else -> 4
            }
        }
    }

    /** Tahmini 1RM sıralaması (her hareket için en iyi değer). */
    fun bestE1rmByExercise(sets: List<WorkoutSetEntity>): Map<Long, Pair<String, Float>> =
        sets.filter { isEffectiveSet(it) && it.weightKg > 0f && it.reps > 0 }
            .groupBy { it.exerciseId }
            .mapNotNull { (exId, list) ->
                val best = list.maxByOrNull { Calc.e1rm(it.weightKg, it.reps) } ?: return@mapNotNull null
                exId to (best.exerciseName to Calc.e1rm(best.weightKg, best.reps))
            }.toMap()

    data class MuscleSetsResult(
        val periodLabel: String,
        val setsPerMuscle: Map<String, Int>
    )

    /** Haftalık / dönemsel kas grubu set sayısı — hacim hedefiyle karşılaştırmak için. */
    fun weeklySetsPerMuscleInfo(
        sets: List<WorkoutSetEntity>,
        exercises: List<ExerciseEntity>
    ): MuscleSetsResult {
        val now = System.currentTimeMillis()
        val weekStart = startOfWeek(now)
        val exMap = exercises.associateBy { it.id }
        val validSets = sets.filter { isEffectiveSet(it) }

        if (validSets.isEmpty()) {
            return MuscleSetsResult("Bu hafta", emptyMap())
        }

        // 1. Bu haftaki kayıtlar
        val thisWeekSets = validSets.filter { getMillis(it.performedAt) >= weekStart }
        if (thisWeekSets.isNotEmpty()) {
            val counts = thisWeekSets
                .groupBy { exMap[it.exerciseId]?.muscleGroup ?: Muscles.guess(it.exerciseName) }
                .mapValues { it.value.size }
            return MuscleSetsResult("Bu hafta", counts)
        }

        // 2. Bu hafta henüz antrenman yoksa: En son aktif olunan haftadaki set sayıları (haftalık ölçek)
        val latestTime = validSets.maxOf { getMillis(it.performedAt) }
        val latestWeekStart = startOfWeek(latestTime)
        val latestWeekEnd = latestWeekStart + 7 * 86_400_000L
        val lastActiveWeekSets = validSets.filter {
            val t = getMillis(it.performedAt)
            t >= latestWeekStart && t < latestWeekEnd
        }

        val counts = lastActiveWeekSets
            .groupBy { exMap[it.exerciseId]?.muscleGroup ?: Muscles.guess(it.exerciseName) }
            .mapValues { it.value.size }

        return MuscleSetsResult("Son aktif hafta", counts)
    }

    fun weeklySetsPerMuscle(
        sets: List<WorkoutSetEntity>,
        exercises: List<ExerciseEntity>
    ): Map<String, Int> = weeklySetsPerMuscleInfo(sets, exercises).setsPerMuscle

    /** Bir seansın toplam hacmi. */
    fun workoutVolume(workoutId: Long, sets: List<WorkoutSetEntity>): Float =
        sets.filter { it.workoutId == workoutId && isEffectiveSet(it) }
            .sumOf { (it.weightKg * it.reps).toDouble() }.toFloat()
}
