package com.example

import com.example.core.LoadKind
import com.example.core.LoadingProfile
import com.example.core.LoggedSet
import com.example.core.ProgressAction
import com.example.core.ProgressionEngine
import com.example.core.ReadinessEngine
import com.example.core.SessionLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {

    private val profile = LoadingProfile(barKg = 20f, barbellStep = 2.5f, dumbbellStep = 2f, machineStep = 5f)

    private fun s(day: Long, vararg sets: Triple<Float, Int, Float>) =
        SessionLog(day, sets.map { LoggedSet(it.first, it.second, it.third) })

    private fun t(w: Float, r: Int, rpe: Float = 8f) = Triple(w, r, rpe)

    @Test fun firstSessionHasNoWeight() {
        val rx = ProgressionEngine.prescribe(emptyList(), 3, 10, 12, LoadKind.BARBELL, profile)
        assertEquals(ProgressAction.FIRST, rx.action)
        assertEquals(listOf(10, 10, 10), rx.repTargets)
    }

    @Test fun allSetsAtTopIncreasesWeightAndResetsReps() {
        val rx = ProgressionEngine.prescribe(listOf(s(1, t(40f, 12), t(40f, 12), t(40f, 12, 9f))), 3, 10, 12, LoadKind.BARBELL, profile)
        assertEquals(ProgressAction.INCREASE, rx.action)
        assertEquals(42.5f, rx.weight, 0.001f)
        assertEquals(listOf(10, 10, 10), rx.repTargets)
    }

    @Test fun grindingTopSetHoldsWeight() {
        val rx = ProgressionEngine.prescribe(listOf(s(1, t(40f, 12), t(40f, 12), t(40f, 12, 10f))), 3, 10, 12, LoadKind.BARBELL, profile)
        assertEquals(ProgressAction.HOLD, rx.action)
        assertEquals(40f, rx.weight, 0.001f)
    }

    @Test fun inRangeAddsOneRepPerSet() {
        val rx = ProgressionEngine.prescribe(listOf(s(1, t(40f, 11), t(40f, 10), t(40f, 10))), 3, 10, 12, LoadKind.BARBELL, profile)
        assertEquals(ProgressAction.REPS, rx.action)
        assertEquals(listOf(12, 11, 11), rx.repTargets)
    }

    @Test fun dumbbellUsesDumbbellStep() {
        val rx = ProgressionEngine.prescribe(listOf(s(1, t(6f, 12), t(6f, 12))), 2, 8, 12, LoadKind.DUMBBELL, profile)
        assertEquals(8f, rx.weight, 0.001f)
    }

    @Test fun twoMissedSessionsDecrease() {
        val h = listOf(s(1, t(40f, 9), t(40f, 8)), s(2, t(40f, 9), t(40f, 9)))
        val rx = ProgressionEngine.prescribe(h, 2, 10, 12, LoadKind.BARBELL, profile)
        assertEquals(ProgressAction.DECREASE, rx.action)
        assertEquals(37.5f, rx.weight, 0.001f)
    }

    @Test fun deloadHalvesSetsAndLightens() {
        val rx = ProgressionEngine.prescribe(listOf(s(1, t(40f, 12), t(40f, 12))), 3, 10, 12, LoadKind.BARBELL, profile, deload = true)
        assertEquals(ProgressAction.DELOAD, rx.action)
        assertEquals(35f, rx.weight, 0.001f)
        assertEquals(2, rx.sets)
    }

    @Test fun plateauAndRegressionDetection() {
        val stall = listOf(s(1, t(40f, 10)), s(2, t(40f, 12)), s(3, t(40f, 12)), s(4, t(40f, 11)), s(5, t(40f, 12)))
        assertEquals(3, ProgressionEngine.stallCount(stall))
        val reg = listOf(s(1, t(50f, 10)), s(2, t(50f, 12)), s(3, t(45f, 10)), s(4, t(45f, 9)))
        assertTrue(ProgressionEngine.isRegressing(reg))
        val signal = ReadinessEngine.evaluate(mapOf("Bench" to reg, "Squat" to reg, "Row" to stall), List(10) { 8f })
        assertTrue(signal.deloadSuggested)
    }

    @Test fun gridRounding() {
        assertEquals(8f, ProgressionEngine.nextUp(7f, 2f), 0.001f)
        assertEquals(6f, ProgressionEngine.nextDown(7f, 2f), 0.001f)
        assertEquals(7.5f, ProgressionEngine.nextUp(6f, 2.5f), 0.001f)
    }
}
