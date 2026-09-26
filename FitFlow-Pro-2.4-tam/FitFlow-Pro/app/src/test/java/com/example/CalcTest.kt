package com.example

import com.example.core.Calc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalcTest {

    @Test
    fun `tek tekrarda 1RM agirligin kendisidir`() {
        assertEquals(100f, Calc.e1rm(100f, 1), 0.01f)
    }

    @Test
    fun `1RM tekrar sayisiyla artar`() {
        val five = Calc.e1rm(100f, 5)
        val eight = Calc.e1rm(100f, 8)
        assertTrue(five > 100f)
        assertTrue(eight > five)
    }

    @Test
    fun `gecersiz girdilerde 1RM sifirdir`() {
        assertEquals(0f, Calc.e1rm(0f, 5), 0.001f)
        assertEquals(0f, Calc.e1rm(50f, 0), 0.001f)
    }

    @Test
    fun `plaka hesabi dogru toplami verir`() {
        // 100 kg = 20 kg bar + her tarafa 40 kg
        val plates = Calc.plates(100f, 20f)
        assertEquals(40f, plates.sum(), 0.01f)
        assertEquals(100f, Calc.achievableWeight(100f, 20f), 0.01f)
    }

    @Test
    fun `bar agirliginin altinda plaka gerekmez`() {
        assertTrue(Calc.plates(20f, 20f).isEmpty())
    }

    @Test
    fun `hedef araligin ustunde agirlik artisi onerilir`() {
        val (weight, _) = Calc.suggestNextWeight(
            lastWeight = 60f,
            lastReps = listOf(12, 12, 12),
            avgRpe = 7f,
            repMin = 8,
            repMax = 12,
            increment = 2.5f
        )
        assertEquals(62.5f, weight, 0.01f)
    }

    @Test
    fun `hedef araligin altinda ve zorlanmissa agirlik dusurulur`() {
        val (weight, _) = Calc.suggestNextWeight(
            lastWeight = 60f,
            lastReps = listOf(5, 5, 4),
            avgRpe = 9.5f,
            repMin = 8,
            repMax = 12,
            increment = 2.5f
        )
        assertEquals(57.5f, weight, 0.01f)
    }

    @Test
    fun `vki hesabi dogrudur`() {
        assertEquals(24.22f, Calc.bmi(76f, 177f), 0.05f)
        assertEquals("Normal", Calc.bmiCategory(Calc.bmi(76f, 177f)))
    }

    @Test
    fun `isinma piramidi calisma agirligini asmaz`() {
        val scheme = Calc.warmupScheme(100f, 20f)
        assertTrue(scheme.isNotEmpty())
        assertTrue(scheme.all { it.first < 100f })
    }
}
