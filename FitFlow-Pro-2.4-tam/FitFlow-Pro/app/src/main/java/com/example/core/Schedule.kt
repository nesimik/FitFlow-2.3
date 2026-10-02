package com.example.core

import com.example.data.RoutineDayEntity
import com.example.data.WorkoutEntity

/**
 * 2.39: "Bugün hangi gün?" kuralı tek yerde — ana ekran, widget ve hatırlatıcı aynı sonucu verir.
 * Haftanın günü seçilmiş günler takvime göre; hiçbirinde gün yoksa (A/B dönüşümlü) en uzun süredir yapılmayan gün.
 */
object Schedule {
    private const val DAY = 86_400_000L

    fun isRotation(days: List<RoutineDayEntity>) = days.isNotEmpty() && days.none { it.weekday in 1..7 }

    /** Bugünün program günü (dönüşümlüde: bugün yapılan ya da sıradaki). */
    fun todayDay(days: List<RoutineDayEntity>, workouts: List<WorkoutEntity>, today: Int = todayWeekday(), now: Long = System.currentTimeMillis()): RoutineDayEntity? {
        if (days.isEmpty()) return null
        if (!isRotation(days)) return days.firstOrNull { it.weekday == today }
        val dayStart = startOfDay(now)
        val doneId = workouts.filter { it.isFinished && it.startedAt >= dayStart }.maxByOrNull { it.startedAt }?.routineDayId
        return days.firstOrNull { it.id == doneId } ?: nextPlannedDay(days, workouts, today)
    }

    /** Sıradaki planlı gün: haftalıkta bugünden sonraki ilk gün; dönüşümlüde en uzun süredir yapılmayan. */
    fun nextPlannedDay(days: List<RoutineDayEntity>, workouts: List<WorkoutEntity>, today: Int = todayWeekday()): RoutineDayEntity? {
        if (days.isEmpty()) return null
        val withDay = days.filter { it.weekday in 1..7 }
        if (withDay.isNotEmpty()) return withDay.minByOrNull { ((it.weekday - today + 7) % 7).let { d -> if (d == 0) 7 else d } }
        val lastByDay = workouts.filter { it.routineDayId != null }.groupBy { it.routineDayId!! }.mapValues { e -> e.value.maxOf { it.startedAt } }
        return days.minByOrNull { lastByDay[it.id] ?: 0L }
    }

    /** Gün kaç gün sonra (0 = bugün); dönüşümlüde 0. */
    fun daysAhead(day: RoutineDayEntity, today: Int = todayWeekday()): Int =
        if (day.weekday in 1..7) (day.weekday - today + 7) % 7 else 0

    /** Haftalık hedef: programdaki takvim günleri; yoksa gün sayısı; program yoksa ayardaki hedef. */
    fun weeklyGoal(days: List<RoutineDayEntity>, settingGoal: Int): Int {
        val withDay = days.count { it.weekday in 1..7 }
        return when {
            withDay > 0 -> withDay
            days.isNotEmpty() -> days.size
            else -> settingGoal.coerceAtLeast(1)
        }
    }
}
