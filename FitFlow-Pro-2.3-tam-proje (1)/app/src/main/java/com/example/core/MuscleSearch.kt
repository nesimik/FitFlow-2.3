package com.example.core

import java.util.Locale

/* ==========================================================================
 * Kas / vücut bölgesi ile hareket arama
 *
 * "kalça", "arka bacak", "pazu", "üst göğüs", "rear delt" gibi aramaları kas
 * anahtarlarına çevirir ve hareketleri o kası ne kadar çalıştırdığına göre sıralar.
 * Türkçe karakterler katlanır: "kalca" = "kalça", "gogus" = "göğüs".
 * ========================================================================== */

object MuscleSearch {

    private val TR = Locale("tr", "TR")

    /** Aranabilir terim → kas anahtarları. Uzun terimler önce denenir ("arka bacak" > "bacak"). */
    private val terms: List<Pair<String, List<String>>> = listOf(
        // Göğüs
        "üst göğüs" to listOf(MuscleMap.CHEST_UPPER),
        "upper chest" to listOf(MuscleMap.CHEST_UPPER),
        "alt göğüs" to listOf(MuscleMap.CHEST_LOWER),
        "lower chest" to listOf(MuscleMap.CHEST_LOWER),
        "göğüs" to listOf(MuscleMap.CHEST), "chest" to listOf(MuscleMap.CHEST),
        "pektoral" to listOf(MuscleMap.CHEST), "pec" to listOf(MuscleMap.CHEST),
        // Omuz
        "ön omuz" to listOf(MuscleMap.FRONT_DELT), "front delt" to listOf(MuscleMap.FRONT_DELT),
        "yan omuz" to listOf(MuscleMap.SIDE_DELT), "side delt" to listOf(MuscleMap.SIDE_DELT), "lateral delt" to listOf(MuscleMap.SIDE_DELT),
        "arka omuz" to listOf(MuscleMap.REAR_DELT), "rear delt" to listOf(MuscleMap.REAR_DELT),
        "omuz" to listOf(MuscleMap.FRONT_DELT, MuscleMap.SIDE_DELT, MuscleMap.REAR_DELT),
        "shoulder" to listOf(MuscleMap.FRONT_DELT, MuscleMap.SIDE_DELT, MuscleMap.REAR_DELT),
        "delt" to listOf(MuscleMap.FRONT_DELT, MuscleMap.SIDE_DELT, MuscleMap.REAR_DELT),
        // Sırt
        "üst sırt" to listOf(MuscleMap.UPPER_BACK), "upper back" to listOf(MuscleMap.UPPER_BACK),
        "rhomboid" to listOf(MuscleMap.UPPER_BACK), "romboid" to listOf(MuscleMap.UPPER_BACK),
        "kanat" to listOf(MuscleMap.LATS), "latissimus" to listOf(MuscleMap.LATS), "lats" to listOf(MuscleMap.LATS),
        "trapez" to listOf(MuscleMap.TRAPS), "traps" to listOf(MuscleMap.TRAPS), "boyun" to listOf(MuscleMap.TRAPS),
        "bel" to listOf(MuscleMap.LOWER_BACK), "lower back" to listOf(MuscleMap.LOWER_BACK), "erector" to listOf(MuscleMap.LOWER_BACK),
        "sırt" to listOf(MuscleMap.LATS, MuscleMap.UPPER_BACK, MuscleMap.TRAPS, MuscleMap.LOWER_BACK),
        "back" to listOf(MuscleMap.LATS, MuscleMap.UPPER_BACK, MuscleMap.TRAPS, MuscleMap.LOWER_BACK),
        // Kol
        "arka kol" to listOf(MuscleMap.TRICEPS), "triceps" to listOf(MuscleMap.TRICEPS), "trisep" to listOf(MuscleMap.TRICEPS),
        "ön kol" to listOf(MuscleMap.FOREARM), "önkol" to listOf(MuscleMap.FOREARM), "forearm" to listOf(MuscleMap.FOREARM),
        "bilek" to listOf(MuscleMap.FOREARM), "kavrama" to listOf(MuscleMap.FOREARM), "grip" to listOf(MuscleMap.FOREARM),
        "pazu" to listOf(MuscleMap.BICEPS), "biceps" to listOf(MuscleMap.BICEPS), "bisep" to listOf(MuscleMap.BICEPS),
        "kol" to listOf(MuscleMap.BICEPS, MuscleMap.TRICEPS, MuscleMap.FOREARM),
        "arm" to listOf(MuscleMap.BICEPS, MuscleMap.TRICEPS, MuscleMap.FOREARM),
        // Gövde
        "yan karın" to listOf(MuscleMap.OBLIQUES), "oblik" to listOf(MuscleMap.OBLIQUES), "oblique" to listOf(MuscleMap.OBLIQUES),
        "karın" to listOf(MuscleMap.ABS), "abs" to listOf(MuscleMap.ABS), "mide" to listOf(MuscleMap.ABS), "six pack" to listOf(MuscleMap.ABS),
        "core" to listOf(MuscleMap.ABS, MuscleMap.OBLIQUES, MuscleMap.LOWER_BACK),
        "merkez" to listOf(MuscleMap.ABS, MuscleMap.OBLIQUES, MuscleMap.LOWER_BACK),
        // Bacak
        "arka bacak" to listOf(MuscleMap.HAMSTRINGS), "arka uyluk" to listOf(MuscleMap.HAMSTRINGS), "hamstring" to listOf(MuscleMap.HAMSTRINGS),
        "ön bacak" to listOf(MuscleMap.QUADS), "ön uyluk" to listOf(MuscleMap.QUADS), "quad" to listOf(MuscleMap.QUADS), "quadriceps" to listOf(MuscleMap.QUADS),
        "iç bacak" to listOf(MuscleMap.ADDUCTORS), "iç uyluk" to listOf(MuscleMap.ADDUCTORS), "adductor" to listOf(MuscleMap.ADDUCTORS), "addüktör" to listOf(MuscleMap.ADDUCTORS),
        "kalça" to listOf(MuscleMap.GLUTES), "popo" to listOf(MuscleMap.GLUTES), "glute" to listOf(MuscleMap.GLUTES), "kaba et" to listOf(MuscleMap.GLUTES),
        "baldır" to listOf(MuscleMap.CALVES), "calf" to listOf(MuscleMap.CALVES), "calves" to listOf(MuscleMap.CALVES), "kalf" to listOf(MuscleMap.CALVES),
        "uyluk" to listOf(MuscleMap.QUADS, MuscleMap.HAMSTRINGS, MuscleMap.ADDUCTORS),
        "bacak" to listOf(MuscleMap.QUADS, MuscleMap.HAMSTRINGS, MuscleMap.GLUTES, MuscleMap.CALVES, MuscleMap.ADDUCTORS),
        "leg" to listOf(MuscleMap.QUADS, MuscleMap.HAMSTRINGS, MuscleMap.GLUTES, MuscleMap.CALVES, MuscleMap.ADDUCTORS)
    ).sortedByDescending { it.first.length }

    /** Türkçe karakterleri katlar ve küçük harfe çevirir. */
    fun fold(s: String): String = s.lowercase(TR)
        .replace('ç', 'c').replace('ğ', 'g').replace('ı', 'i').replace('ö', 'o').replace('ş', 's').replace('ü', 'u')
        .replace(Regex("\\s+"), " ").trim()

    private val folded: List<Pair<String, List<String>>> by lazy { terms.map { fold(it.first) to it.second } }

    /**
     * Sorgu bir kas / bölge adıysa kas anahtarlarını döner, değilse null.
     * Tam eşleşme veya sorgunun terimle başlaması (en az 3 harf) aranır: "kalc" → kalça.
     */
    fun muscles(query: String): List<String>? {
        val q = fold(query)
        if (q.length < 3) return null
        folded.firstOrNull { it.first == q }?.let { return it.second }
        folded.firstOrNull { it.first.startsWith(q) }?.let { return it.second }
        return null
    }

    /** Sorgunun karşılık geldiği okunabilir bölge adı. */
    fun label(keys: List<String>): String = when {
        keys.size == 1 -> MuscleMap.label(keys.first())
        keys.containsAll(listOf(MuscleMap.FRONT_DELT, MuscleMap.SIDE_DELT)) -> "Omuz"
        keys.containsAll(listOf(MuscleMap.LATS, MuscleMap.UPPER_BACK)) -> "Sırt"
        keys.containsAll(listOf(MuscleMap.BICEPS, MuscleMap.TRICEPS)) -> "Kol"
        keys.containsAll(listOf(MuscleMap.QUADS, MuscleMap.HAMSTRINGS, MuscleMap.GLUTES)) -> "Bacak"
        keys.containsAll(listOf(MuscleMap.QUADS, MuscleMap.HAMSTRINGS)) -> "Uyluk"
        keys.contains(MuscleMap.ABS) -> "Karın ve gövde"
        else -> keys.joinToString(", ") { MuscleMap.label(it) }
    }

    /**
     * Hareketin bu kaslara katkısı (0..1): aranan kaslardan en yüksek katkı.
     * Üst / alt göğüs aramasında göğüs katkısı hareketin açısına göre ölçeklenir.
     */
    fun score(name: String, muscleGroup: String, secondaryMuscles: String, keys: List<String>): Float {
        val w = MuscleMap.resolve(name, muscleGroup, secondaryMuscles).weights()
        return keys.maxOf { k ->
            when (k) {
                MuscleMap.CHEST_UPPER -> (w[MuscleMap.CHEST] ?: 0f) * MuscleMap.chestSplit(name).first
                MuscleMap.CHEST_LOWER -> (w[MuscleMap.CHEST] ?: 0f) * MuscleMap.chestSplit(name).second
                else -> w[k] ?: 0f
            }
        }
    }

    /** Bir harekette kasın "anlamlı" sayılması için gereken en düşük katkı. */
    const val MIN_SCORE = 0.3f
}
