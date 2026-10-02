package com.example.core

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Bar + plaka için en uygun dizilim çözücüsü.
 * Her plaka türünden sınırsız çift olduğu varsayılır. Tek taraf için dinamik programlama:
 * önce kalan farkı (hedefe uzaklığı), sonra plaka sayısını en aza indirir.
 * Hesap 0,01 kg tamsayı birimlerinde yapılır (kayan nokta hatası olmasın diye).
 */
object PlateSolver {

    data class Load(val perSide: List<Float>, val totalKg: Float)

    data class Result(
        val targetKg: Float,
        val barKg: Float,
        /** Hedef tam kurulabiliyorsa dizilim. */
        val exact: Load?,
        /** Hedefin altındaki/eşit en yakın kurulabilir yük (en az sadece bar). */
        val below: Load?,
        /** Hedefin üstündeki en yakın kurulabilir yük. */
        val above: Load?
    ) {
        val belowBar: Boolean get() = targetKg < barKg - 0.001f

        /** Gösterilecek en iyi dizilim: tam varsa o, yoksa hedefe en yakın (eşitlikte alttaki). */
        val best: Load?
            get() = exact ?: run {
                val b = below; val a = above
                when {
                    b == null -> a
                    a == null -> b
                    abs(a.totalKg - targetKg) < abs(targetKg - b.totalKg) - 0.001f -> a
                    else -> b
                }
            }
    }

    private const val MAX_PER_SIDE_UNITS = 40_000 // 400 kg / taraf

    private fun units(kg: Float): Int = (kg * 100f).roundToInt()

    fun solve(totalKg: Float, barKg: Float, plates: List<Float>): Result {
        val bar = barKg.coerceAtLeast(0f)
        val denoms = plates.filter { it > 0f }.map { units(it) }.filter { it > 0 }.distinct().sortedDescending()
        if (totalKg < bar - 0.001f) {
            return Result(totalKg, bar, null, null, Load(emptyList(), bar))
        }
        val target = units((totalKg - bar) / 2f).coerceIn(0, MAX_PER_SIDE_UNITS)
        if (denoms.isEmpty()) {
            val barOnly = Load(emptyList(), bar)
            return Result(totalKg, bar, if (target == 0) barOnly else null, barOnly, null)
        }
        val limit = target + denoms.first()
        // count[u] = u birimi tam yapmak için en az plaka; -1 = yapılamaz
        val count = IntArray(limit + 1) { -1 }
        val last = IntArray(limit + 1) { -1 }
        count[0] = 0
        for (u in 1..limit) {
            var best = -1
            var bestPlate = -1
            for (d in denoms) {
                if (d <= u) {
                    val c = count[u - d]
                    if (c >= 0 && (best < 0 || c + 1 < best)) {
                        best = c + 1; bestPlate = d
                    }
                }
            }
            count[u] = best
            last[u] = bestPlate
        }

        fun build(u: Int): Load {
            val list = mutableListOf<Float>()
            var x = u
            while (x > 0) {
                val p = last[x]
                list.add(p / 100f)
                x -= p
            }
            list.sortDescending()
            return Load(list, bar + 2f * (u / 100f))
        }

        val exact = if (count[target] >= 0) build(target) else null
        var lo = target
        while (lo > 0 && count[lo] < 0) lo--
        var hi = target
        while (hi <= limit && count[hi] < 0) hi++
        val below = build(lo)
        val above = if (hi <= limit) build(hi) else null
        return Result(totalKg, bar, exact, below, above)
    }

    /** Hedefe en yakın kurulabilir toplam ağırlık (eşitlikte alttaki; en az bar). */
    fun nearestTotal(totalKg: Float, barKg: Float, plates: List<Float>): Float =
        solve(totalKg, barKg, plates).best?.totalKg ?: maxOf(totalKg, barKg)
}
