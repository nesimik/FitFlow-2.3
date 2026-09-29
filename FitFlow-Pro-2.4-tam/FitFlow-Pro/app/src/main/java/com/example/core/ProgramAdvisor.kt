package com.example.core

import com.example.data.ExerciseEntity
import com.example.data.RoutineDayEntity
import com.example.data.RoutineItemEntity
import kotlin.math.roundToInt

/* ==========================================================================
 * Program danışmanı (2.25) — "Akıllı öneriler"
 *
 * Planlanan programı (günler × hareketler × set) kas katkı oranlarıyla haftalık etkin
 * sete çevirir ve dört açıdan inceler:
 *   1. Hacim   — kas başına haftalık set, önerilen aralığa göre (eksik / fazla)
 *   2. Denge   — itiş/çekiş, ön/arka bacak, ön/arka omuz oranları
 *   3. Sıklık  — büyük kasın haftada tek günde toplanması
 *   4. Gün yükü — çok uzun / çok kısa günler
 * Her bulgu, uygulanabilir tek bir somut adımla gelir (set artır, hareket ekle, set azalt).
 * ========================================================================== */

enum class AdviceKind(val label: String) { ADD_SETS("Set artır"), ADD_EXERCISE("Hareket ekle"), REDUCE("Azalt"), BALANCE("Denge"), FREQUENCY("Sıklık"), DAY_LOAD("Gün yükü") }
enum class AdviceSeverity { HIGH, MEDIUM, LOW }

sealed class AdviceAction {
    data class SetSets(val item: RoutineItemEntity, val newSets: Int) : AdviceAction()
    data class AddExercise(val dayId: Long, val exercise: ExerciseEntity, val sets: Int) : AdviceAction()
}

data class Advice(
    val id: String,
    val kind: AdviceKind,
    val severity: AdviceSeverity,
    val muscleKey: String?,
    val title: String,
    val detail: String,
    /** "Kanat 6 → 10 set · hedef 10–20" */
    val impact: String?,
    val action: AdviceAction?
)

data class BalanceRatio(val label: String, val left: String, val right: String, val leftSets: Float, val rightSets: Float, val idealLo: Float, val idealHi: Float) {
    val ratio: Float get() = if (rightSets <= 0f) (if (leftSets > 0f) 9f else 1f) else leftSets / rightSets
    val ok: Boolean get() = ratio in idealLo..idealHi
}

data class ProgramReview(
    val score: Int,
    val inRange: Int,
    val totalMuscles: Int,
    val weeklySets: Int,
    val sets: Map<String, Float>,
    val balances: List<BalanceRatio>,
    val advice: List<Advice>
) {
    val verdict: String get() = when {
        score >= 85 -> "Çok iyi dengelenmiş bir program."
        score >= 70 -> "İyi bir program; birkaç ince ayar fark yaratır."
        score >= 50 -> "Belirgin eksikler var; öneriler önemli."
        else -> "Program dengesiz; önce yüksek öncelikli önerilere bak."
    }
}

object ProgramAdvisor {

    /** Yardımcı kaslar: yalnızca ciddi eksikse önerilir. */
    private val ACCESSORY = setOf(MuscleMap.OBLIQUES, MuscleMap.FOREARM, MuscleMap.TRAPS, MuscleMap.LOWER_BACK, MuscleMap.ADDUCTORS)

    private val BIG = setOf(MuscleMap.CHEST, MuscleMap.LATS, MuscleMap.UPPER_BACK, MuscleMap.QUADS, MuscleMap.HAMSTRINGS, MuscleMap.GLUTES)

    fun review(
        days: List<RoutineDayEntity>,
        items: List<RoutineItemEntity>,
        exercises: List<ExerciseEntity>,
        /** Hareket id → kullanıcının geçmişte yaptığı (öneride öncelik). */
        doneBefore: Set<Long> = emptySet()
    ): ProgramReview {
        val exMap = exercises.associateBy { it.id }
        val dayIds = days.map { it.id }.toSet()
        val work = items.filter { it.dayId in dayIds && !it.isWarmup }
        fun weightsOf(it: RoutineItemEntity): Map<String, Float> {
            val ex = exMap[it.exerciseId] ?: return emptyMap()
            return MuscleMap.resolve(it.customName.ifBlank { ex.name }, ex.muscleGroup, ex.secondaryMuscles).weights()
        }

        // Kas → haftalık etkin set; kas → gün → set
        val sets = HashMap<String, Float>()
        val perDay = HashMap<String, HashMap<Long, Float>>()
        work.forEach { item ->
            weightsOf(item).forEach { (m, f) ->
                sets[m] = (sets[m] ?: 0f) + f * item.targetSets
                perDay.getOrPut(m) { HashMap() }.merge(item.dayId, f * item.targetSets) { a, b -> a + b }
            }
        }
        val muscles = MuscleMap.all
        fun status(k: String) = MuscleLoad(k, sets[k] ?: 0f, 0f, -1, MuscleMap.weeklyTarget(k)).status
        val advice = mutableListOf<Advice>()
        val usedItems = mutableSetOf<Long>()
        val addedExercises = mutableSetOf<Long>()
        val dayName = days.associate { it.id to it.name }
        val daysByItems = work.groupBy { it.dayId }

        /* 1) Hacim eksikleri */
        muscles.map { it to (sets[it] ?: 0f) }
            .filter { (k, v) -> v < MuscleMap.weeklyTarget(k).first }
            .filter { (k, v) -> k !in ACCESSORY || v < MuscleMap.weeklyTarget(k).first * 0.5f }
            .sortedBy { (k, v) -> v / MuscleMap.weeklyTarget(k).first.coerceAtLeast(1) }
            .forEach { (k, v) ->
                val t = MuscleMap.weeklyTarget(k)
                val missing = (t.first - v).coerceAtLeast(1f)
                val need = missing.roundToInt().coerceIn(2, 6)
                val sev = when {
                    k in ACCESSORY -> AdviceSeverity.LOW
                    v < t.first * 0.5f -> AdviceSeverity.HIGH
                    v < t.first * 0.8f -> AdviceSeverity.MEDIUM
                    else -> AdviceSeverity.LOW
                }
                // a) Bu kası birincil çalıştıran mevcut harekete set ekle (en basit, en etkili)
                val primaryItems = work.mapNotNull { item -> (weightsOf(item)[k] ?: 0f).takeIf { it >= 0.75f }?.let { item to it } }
                    .filter { (item, _) -> item.targetSets < 5 && item.id !in usedItems }
                    .sortedBy { (item, _) -> item.targetSets }
                val bump = primaryItems.firstOrNull()
                if (bump != null && need <= 2 + (5 - bump.first.targetSets)) {
                    val (item, f) = bump
                    val add = minOf(need, 5 - item.targetSets).coerceAtLeast(1)
                    usedItems += item.id
                    val ex = exMap[item.exerciseId]
                    val after = v + add * f
                    advice += Advice(
                        "sets_${k}_${item.id}", AdviceKind.ADD_SETS, sev, k,
                        "${MuscleMap.label(k)} için +$add set",
                        "${dayName[item.dayId]} günündeki ${item.customName.ifBlank { ex?.name ?: "" }} ${item.targetSets} → ${item.targetSets + add} set. " +
                            "Yeni hareket eklemeden eksiği kapatmanın en kolay yolu.",
                        "${MuscleMap.label(k)} ${v.fmt()} → ${after.fmt()} set · hedef ${t.first}–${t.last}",
                        AdviceAction.SetSets(item, item.targetSets + add)
                    )
                    return@forEach
                }
                // b) Yeni hareket ekle: bu kası en çok çalıştıran, programda olmayan hareket
                val inProgram = work.map { it.exerciseId }.toSet() + addedExercises
                val best = exercises.asSequence()
                    .filter { it.id !in inProgram && it.muscleGroup != Muscles.CARDIO }
                    .map { ex -> ex to (MuscleMap.resolve(ex.name, ex.muscleGroup, ex.secondaryMuscles).weights()[k] ?: 0f) }
                    .filter { it.second >= 0.75f }
                    .sortedWith(compareByDescending<Pair<ExerciseEntity, Float>> { it.first.id in doneBefore }
                        .thenByDescending { it.first.isFavorite }
                        // Büyük kaslarda bileşik hareket öncelikli
                        .thenByDescending { k in BIG && RepScheme.classify(it.first.name, it.first.muscleGroup, it.first.equipment, it.first.trackingType).let { s -> s != null && s != RepScheme.ISOLATION } }
                        .thenByDescending { it.second })
                    .firstOrNull()?.first
                    ?: return@forEach
                // Gün: bu kasın ana grubunu çalıştıran, en hafif gün; yoksa en hafif gün
                val group = MuscleMap.parentGroup(k)
                val day = days.filter { d -> daysByItems[d.id].orEmpty().any { exMap[it.exerciseId]?.muscleGroup == group } }
                    .minByOrNull { d -> daysByItems[d.id].orEmpty().sumOf { it.targetSets } }
                    ?: days.minByOrNull { d -> daysByItems[d.id].orEmpty().sumOf { it.targetSets } }
                    ?: return@forEach
                val addSets = need.coerceIn(2, 4)
                addedExercises += best.id
                advice += Advice(
                    "add_${k}_${best.id}_${day.id}", AdviceKind.ADD_EXERCISE, sev, k,
                    "${MuscleMap.label(k)} için ${best.name}",
                    "${day.name} gününe $addSets set ${best.name} (${best.equipment}). " +
                        (if (best.id in doneBefore) "Daha önce yaptığın bir hareket. " else "") +
                        "Programda bu kası birincil çalıştıran yeterli hareket yok.",
                    "${MuscleMap.label(k)} ${v.fmt()} → ${(v + addSets).fmt()} set · hedef ${t.first}–${t.last}",
                    AdviceAction.AddExercise(day.id, best, addSets)
                )
            }

        /* 2) Fazla hacim */
        muscles.filter { k -> (sets[k] ?: 0f) > MuscleMap.weeklyTarget(k).last * 1.35f }.forEach { k ->
            val v = sets[k] ?: 0f
            val t = MuscleMap.weeklyTarget(k)
            val top = work.mapNotNull { item -> (weightsOf(item)[k] ?: 0f).takeIf { it >= 0.75f }?.let { item to it } }
                .filter { it.first.targetSets > 2 }
                .maxByOrNull { it.first.targetSets }
            val ex = top?.let { exMap[it.first.exerciseId] }
            advice += Advice(
                "reduce_$k", AdviceKind.REDUCE, AdviceSeverity.MEDIUM, k,
                "${MuscleMap.label(k)} fazla yükleniyor",
                "Haftada ${v.fmt()} etkin set; önerilen üst sınır ${t.last}. Fazlası toparlanmayı bozar, ek kazanç getirmez." +
                    (if (top != null) " ${dayName[top.first.dayId]} · ${ex?.name}: ${top.first.targetSets} → ${top.first.targetSets - 1} set." else ""),
                "${MuscleMap.label(k)} ${v.fmt()} → ${(v - (top?.second ?: 0f)).fmt()} set",
                top?.let { AdviceAction.SetSets(it.first, it.first.targetSets - 1) }
            )
        }

        /* 3) Denge oranları */
        fun s(vararg k: String) = k.sumOf { (sets[it] ?: 0f).toDouble() }.toFloat()
        val balances = listOf(
            BalanceRatio("İtiş / Çekiş", "İtiş", "Çekiş", s(MuscleMap.CHEST, MuscleMap.FRONT_DELT), s(MuscleMap.LATS, MuscleMap.UPPER_BACK, MuscleMap.REAR_DELT), 0.7f, 1.3f),
            BalanceRatio("Ön / Arka bacak", "Ön bacak", "Arka bacak", s(MuscleMap.QUADS), s(MuscleMap.HAMSTRINGS), 0.8f, 1.8f),
            BalanceRatio("Ön / Arka omuz", "Ön omuz", "Arka omuz", s(MuscleMap.FRONT_DELT), s(MuscleMap.REAR_DELT), 0.5f, 2.0f)
        )
        val lightSideMuscles = mapOf(
            "İtiş" to listOf(MuscleMap.CHEST, MuscleMap.FRONT_DELT),
            "Çekiş" to listOf(MuscleMap.LATS, MuscleMap.UPPER_BACK, MuscleMap.REAR_DELT),
            "Ön bacak" to listOf(MuscleMap.QUADS), "Arka bacak" to listOf(MuscleMap.HAMSTRINGS),
            "Ön omuz" to listOf(MuscleMap.FRONT_DELT), "Arka omuz" to listOf(MuscleMap.REAR_DELT)
        )
        balances.filter { !it.ok && (it.leftSets + it.rightSets) > 0f }.forEach { b ->
            val lightName = if (b.ratio > b.idealHi) b.right else b.left
            // Hafif tarafın eksiği zaten bir öneride ele alınıyorsa tekrar etme
            if (advice.any { it.muscleKey in lightSideMuscles[lightName].orEmpty() }) return@forEach
            val heavy = if (b.ratio > b.idealHi) b.left else b.right
            val light = if (b.ratio > b.idealHi) b.right else b.left
            advice += Advice(
                "bal_${b.label}", AdviceKind.BALANCE,
                if (b.ratio > b.idealHi * 1.5f || b.ratio < b.idealLo / 1.5f) AdviceSeverity.HIGH else AdviceSeverity.MEDIUM, null,
                "${b.label} dengesiz",
                "$heavy tarafı ${light.lowercase(java.util.Locale("tr"))} tarafından belirgin fazla (oran ${b.ratio.fmt()}, ideal ${b.idealLo.fmt()}–${b.idealHi.fmt()}). " +
                    "Uzun vadede duruş ve omuz sağlığı için ${light.lowercase(java.util.Locale("tr"))} hacmini artır.",
                "${b.left} ${b.leftSets.fmt()} · ${b.right} ${b.rightSets.fmt()} set",
                null
            )
        }

        /* 4) Sıklık: büyük kas tek günde toplanmışsa */
        BIG.forEach { k ->
            val d = perDay[k].orEmpty().filterValues { it >= 1.5f }
            val v = sets[k] ?: 0f
            if (d.size == 1 && v >= 8f) {
                advice += Advice(
                    "freq_$k", AdviceKind.FREQUENCY, AdviceSeverity.LOW, k,
                    "${MuscleMap.label(k)} haftada tek günde",
                    "${v.fmt()} setin tamamı ${dayName[d.keys.first()]} gününde. Aynı hacmi iki güne bölmek (haftada 2×) kas gelişimi için genelde daha verimli.",
                    null, null
                )
            }
        }

        /* 5) Gün yükü */
        days.forEach { d ->
            val list = daysByItems[d.id].orEmpty()
            val n = list.sumOf { it.targetSets }
            if (n >= 26 || list.size >= 9) {
                advice += Advice(
                    "day_${d.id}", AdviceKind.DAY_LOAD, AdviceSeverity.LOW, null,
                    "${d.name} çok uzun",
                    "${list.size} hareket · $n set. 25 setin üzerinde son hareketlerin kalitesi düşer; bazılarını daha hafif bir güne taşı.",
                    null, null
                )
            }
        }

        // Skor: kas başına hedefe yakınlık (%75) + denge (%25)
        val muscleScore = muscles.map { k ->
            val v = sets[k] ?: 0f; val t = MuscleMap.weeklyTarget(k)
            when {
                v < t.first -> (v / t.first).coerceIn(0f, 1f)
                v <= t.last -> 1f
                else -> (1f - (v - t.last) / t.last).coerceIn(0.4f, 1f)
            }
        }.average().toFloat()
        val balanceScore = balances.count { it.ok }.toFloat() / balances.size
        val score = ((muscleScore * 0.75f + balanceScore * 0.25f) * 100).roundToInt().coerceIn(0, 100)

        val ordered = advice.sortedWith(compareBy<Advice> { it.severity.ordinal }.thenBy { it.kind.ordinal })
        return ProgramReview(
            score = score,
            inRange = muscles.count { status(it) == LoadStatus.OPTIMAL },
            totalMuscles = muscles.size,
            weeklySets = work.sumOf { it.targetSets },
            sets = sets,
            balances = balances,
            advice = ordered
        )
    }

    private fun Float.fmt(): String = trimNum().replace('.', ',')
}
