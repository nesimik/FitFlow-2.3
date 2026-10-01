package com.example.core

import com.example.data.ExerciseEntity

/**
 * 2.33: Hacim (tonaj) kuralı.
 * Ağırlık her zaman TEK dambılın ağırlığı olarak kaydedilir (dambılın üzerinde yazan).
 * Hacimde ise gerçekte kaldırılan sayılır: iki dambıl aynı anda → ×2; tek kol / tek dambıl → ×1.
 */
object VolumeRules {
    /** Hareket id → çarpan (yalnızca 2 olanlar tutulur). Kütüphane yüklenince güncellenir. */
    @Volatile private var doubleIds: Set<Long> = emptySet()
    @Volatile private var known: Set<Long> = emptySet()

    private val SINGLE = listOf(
        "one arm", "one-arm", "single arm", "single-arm", "tek kol", "tek el", "concentration", "kroc",
        "pullover", "goblet", "swing", "unilateral", "1 arm", "1-arm"
    )

    fun isDumbbellPair(name: String, equipment: String): Boolean {
        val n = name.lowercase(TR)
        val db = equipment.trim() == "Dumbbell" || n.contains("dumbbell") || n.contains("dambıl")
        return db && SINGLE.none { n.contains(it) }
    }

    fun update(exercises: List<ExerciseEntity>) {
        known = exercises.map { it.id }.toSet()
        doubleIds = exercises.filter { isDumbbellPair(it.name, it.equipment) }.map { it.id }.toSet()
    }

    fun factor(exerciseId: Long, exerciseName: String): Float = when {
        exerciseId in doubleIds -> 2f
        exerciseId in known -> 1f
        // Kütüphane henüz yüklenmediyse (arka plan işleri) ada göre karar ver
        else -> if (isDumbbellPair(exerciseName, "")) 2f else 1f
    }

    /** Ekranda gösterim: "2×22,5 kg" ya da "80 kg". */
    fun label(exerciseId: Long, exerciseName: String, weight: Float): String =
        if (factor(exerciseId, exerciseName) == 2f && weight > 0f) "2×${weight.trimNum()} kg" else "${weight.trimNum()} kg"
}
