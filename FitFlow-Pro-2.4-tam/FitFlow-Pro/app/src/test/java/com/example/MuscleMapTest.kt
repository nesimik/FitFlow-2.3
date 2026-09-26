package com.example

import com.example.core.LoadStatus
import com.example.core.MuscleMap
import com.example.core.ProgressAnalytics
import com.example.data.ExerciseSeed
import com.example.data.WorkoutEntity
import com.example.data.WorkoutSetEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleMapTest {

    /**
     * Kütüphanedeki her hareket bir kas bölgesine düşmeli.
     * Bu test, ExerciseSeed'e yeni hareket eklendiğinde MuscleMap tablosunun
     * güncellenmesini unutmayı yakalar.
     */
    @Test
    fun `kutuphanedeki tum hareketler bir kasa eslenir`() {
        val unmapped = ExerciseSeed.list.filter { ex ->
            MuscleMap.resolve(ex.name, ex.muscleGroup, ex.secondaryMuscles).isEmpty
        }
        assertTrue(
            "Kas eşlemesi olmayan hareketler: ${unmapped.map { it.name }}",
            unmapped.isEmpty()
        )
    }

    @Test
    fun `bench press gogus birincil triceps ikincil`() {
        val a = MuscleMap.resolve("Bench Press")
        assertTrue(a.primary.contains(MuscleMap.CHEST))
        assertTrue(a.secondary.contains(MuscleMap.TRICEPS))
        assertFalse(a.primary.contains(MuscleMap.TRICEPS))
    }

    @Test
    fun `birincil kas bir set ikincil yarim set sayilir`() {
        val w = MuscleMap.resolve("Bench Press").weights()
        assertEquals(1f, w[MuscleMap.CHEST]!!, 0.001f)
        assertEquals(0.5f, w[MuscleMap.TRICEPS]!!, 0.001f)
    }

    @Test
    fun `bilinmeyen hareket isimden tahmin edilir`() {
        val a = MuscleMap.resolve("Smith Machine Squat", "Bacak")
        assertTrue(a.primary.contains(MuscleMap.QUADS))
    }

    @Test
    fun `her bolgenin turkce etiketi vardir`() {
        MuscleMap.all.forEach { key ->
            assertFalse("Etiket eksik: $key", MuscleMap.label(key) == key)
        }
    }

    @Test
    fun `on ve arka gorunumler tum bolgeleri kapsar`() {
        val covered = MuscleMap.frontVisible + MuscleMap.backVisible
        val missing = MuscleMap.all.filterNot { covered.contains(it) }
        assertTrue("Haritada çizilmeyen bölgeler: $missing", missing.isEmpty())
    }
}

class ProgressAnalyticsTest {

    private val day = 86_400_000L

    private fun set(
        exId: Long,
        name: String,
        weight: Float,
        reps: Int,
        workoutId: Long,
        daysAgo: Int
    ) = WorkoutSetEntity(
        id = 0,
        workoutId = workoutId,
        exerciseId = exId,
        exerciseName = name,
        weightKg = weight,
        reps = reps,
        isCompleted = true,
        performedAt = System.currentTimeMillis() - daysAgo * day
    )

    @Test
    fun `kas yuklenmesi ikincil kaslari yarim sayar`() {
        // 4 set bench press: göğüs 4.0, triceps 2.0
        val sets = (1..4).map { set(1L, "Bench Press", 60f, 8, 100L, 1) }
        val loads = ProgressAnalytics.muscleLoads(sets, emptyList(), 0L, 1f)
        val chest = loads.first { it.key == MuscleMap.CHEST }
        val tri = loads.first { it.key == MuscleMap.TRICEPS }
        assertEquals(4f, chest.effectiveSets, 0.01f)
        assertEquals(2f, tri.effectiveSets, 0.01f)
    }

    @Test
    fun `hic calisilmayan kas NONE durumundadir`() {
        val loads = ProgressAnalytics.muscleLoads(emptyList(), emptyList(), 0L, 1f)
        assertTrue(loads.all { it.status == LoadStatus.NONE })
    }

    @Test
    fun `hedef araligindaki hacim OPTIMAL durumdadir`() {
        val target = MuscleMap.weeklyTarget(MuscleMap.CHEST)
        val n = target.first + 1
        val sets = (1..n).map { set(1L, "Dumbbell Fly", 12f, 12, 100L, 1) }
        val loads = ProgressAnalytics.muscleLoads(sets, emptyList(), 0L, 1f)
        val chest = loads.first { it.key == MuscleMap.CHEST }
        assertEquals(LoadStatus.OPTIMAL, chest.status)
    }

    @Test
    fun `yuklenme orani hesaplanir`() {
        val workouts = listOf(
            WorkoutEntity(id = 1, title = "A", startedAt = System.currentTimeMillis() - 2 * day, isFinished = true),
            WorkoutEntity(id = 2, title = "B", startedAt = System.currentTimeMillis() - 20 * day, isFinished = true)
        )
        val sets = listOf(
            set(1L, "Bench Press", 100f, 10, 1L, 2),   // 1000 kg
            set(1L, "Bench Press", 100f, 10, 2L, 20)   // 1000 kg
        )
        val load = ProgressAnalytics.trainingLoad(workouts, sets)
        assertEquals(1000f, load.acuteVolume, 1f)
        assertEquals(500f, load.chronicWeekly, 1f)   // 2000 / 4 hafta
        assertEquals(2f, load.ratio, 0.05f)
    }

    @Test
    fun `duragan hareket tespit edilir`() {
        val sets = listOf(
            set(1L, "Bench Press", 100f, 5, 1L, 60),   // en iyi
            set(1L, "Bench Press", 90f, 5, 2L, 40),
            set(1L, "Bench Press", 92f, 5, 3L, 20),
            set(1L, "Bench Press", 90f, 5, 4L, 5)
        )
        val stagnant = ProgressAnalytics.stagnation(sets)
        assertEquals(1, stagnant.size)
        assertEquals("Bench Press", stagnant.first().name)
        assertTrue(stagnant.first().daysSinceBest >= 55)
    }

    @Test
    fun `ilerleyen hareket tespit edilir`() {
        val sets = listOf(
            set(1L, "Barbell Squat", 80f, 5, 1L, 20),
            set(1L, "Barbell Squat", 85f, 5, 2L, 10),
            set(1L, "Barbell Squat", 95f, 5, 3L, 2)
        )
        val improving = ProgressAnalytics.improving(sets)
        assertEquals(1, improving.size)
    }

    @Test
    fun `tekrar araligi dagilimi yuzde verir`() {
        val sets = listOf(
            set(1L, "Bench Press", 100f, 3, 1L, 1),
            set(1L, "Bench Press", 80f, 10, 1L, 1),
            set(1L, "Bench Press", 80f, 10, 1L, 1),
            set(1L, "Bench Press", 60f, 15, 1L, 1)
        )
        val dist = ProgressAnalytics.repRangeDistribution(sets)
        val hyper = dist.first { it.label == "9-12" }
        assertEquals(2, hyper.sets)
        assertEquals(0.5f, hyper.share, 0.01f)
    }

    @Test
    fun `guc seviyesi vucut agirligina gore artar`() {
        val weak = listOf(set(1L, "Bench Press", 40f, 5, 1L, 1))
        val strong = listOf(set(1L, "Bench Press", 140f, 3, 1L, 1))
        val weakLevel = ProgressAnalytics.strengthProfile(weak, 80f, true).first().levelIndex
        val strongLevel = ProgressAnalytics.strengthProfile(strong, 80f, true).first().levelIndex
        assertTrue(strongLevel > weakLevel)
    }

    @Test
    fun `dumbbell varyasyonlari ana lift olarak sayilmaz`() {
        val sets = listOf(set(1L, "Dumbbell Bench Press", 40f, 8, 1L, 1))
        val profile = ProgressAnalytics.strengthProfile(sets, 80f, true)
        assertTrue(profile.none { it.liftKey == "bench" })
    }

    @Test
    fun `lift dengesi squat olmadan bos doner`() {
        val sets = listOf(set(1L, "Bench Press", 100f, 5, 1L, 1))
        val profile = ProgressAnalytics.strengthProfile(sets, 80f, true)
        assertTrue(ProgressAnalytics.liftBalance(profile).isEmpty())
    }
}
