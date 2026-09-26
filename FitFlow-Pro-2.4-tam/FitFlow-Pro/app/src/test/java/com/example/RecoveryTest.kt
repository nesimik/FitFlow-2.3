package com.example

import com.example.core.MuscleMap
import com.example.core.ProgressAnalytics
import com.example.core.RecoveryEngine
import com.example.core.RecoveryState
import com.example.data.WorkoutSetEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryTest {

    private val hour = 3_600_000L
    private val now = System.currentTimeMillis()

    private fun sets(name: String, hoursAgo: Long, n: Int, rpe: Float = 8f) = (1..n).map {
        WorkoutSetEntity(workoutId = 1, exerciseId = 0, exerciseName = name, weightKg = 40f, reps = 10,
            rpe = rpe, isCompleted = true, performedAt = now - hoursAgo * hour)
    }

    @Test fun justTrainedMuscleIsFatigued() {
        val r = RecoveryEngine.fromSets(sets("Barbell Squat", 1, 6), emptyList(), now)
        assertEquals(RecoveryState.FATIGUED, r.getValue(MuscleMap.QUADS).state)
        assertTrue(r.getValue(MuscleMap.QUADS).readyAt > now)
    }

    @Test fun muscleRecoversOverTime() {
        val early = RecoveryEngine.fromSets(sets("Bench Press", 12, 6), emptyList(), now).getValue(MuscleMap.CHEST)
        val late = RecoveryEngine.fromSets(sets("Bench Press", 60, 6), emptyList(), now).getValue(MuscleMap.CHEST)
        assertTrue(late.readiness > early.readiness)
        assertEquals(RecoveryState.FRESH, late.state)
    }

    @Test fun untouchedMusclesAreAbsent() {
        val r = RecoveryEngine.fromSets(sets("Leg Extension", 2, 3), emptyList(), now)
        assertTrue(MuscleMap.CHEST !in r)
    }

    @Test fun inclineFavoursUpperChest() {
        val d = ProgressAnalytics.detailLoads(sets("Incline Dumbbell Press", 2, 4), emptyList(), 0L, 1f)
        val up = d.first { it.key == MuscleMap.CHEST_UPPER }.effectiveSets
        val low = d.first { it.key == MuscleMap.CHEST_LOWER }.effectiveSets
        assertTrue(up > low)
    }

    @Test fun contributionTableOverridesDefaults() {
        val w = MuscleMap.resolve("One Arm Dumbbell Row").weights()
        assertEquals(1f, w.getValue(MuscleMap.LATS), 0.001f)
        assertEquals(0.7f, w.getValue(MuscleMap.UPPER_BACK), 0.001f)
    }
}
