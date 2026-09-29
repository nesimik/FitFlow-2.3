package com.example.core

import com.example.data.ExerciseEntity
import com.example.data.RoutineDayEntity
import com.example.data.RoutineItemEntity
import kotlin.math.roundToInt

/* ==========================================================================
 * Program danışmanı (2.26) — "Akıllı öneriler"
 *
 * Planlanan programı (günler × hareketler × set) kas katkı oranlarıyla haftalık etkin
 * sete çevirir ve inceler:
 *   1. Hacim      — eksik kaslar. Önce SÜREYİ ARTIRMAYAN çözüm aranır: fazla / tekrar eden
 *                   bir hareketin yerine eksik kası çalıştıran hareket. Yoksa set artır / ekle.
 *   2. Fazla      — üst sınırı aşan kaslar: set azalt ya da hareketi çıkar.
 *   3. Tekrar     — aynı gün aynı kası çalıştıran çok sayıda izole hareket.
 *   4. Denge      — itiş/çekiş, ön/arka bacak, ön/arka omuz.
 *   5. Sıralama   — izole hareket aynı kasın bileşiğinden önce.
 *   6. Dinlenme   — bileşikte çok kısa, izolede gereksiz uzun dinlenme (süre kazancı).
 *   7. Sıklık     — büyük kasın tek günde toplanması.
 *   8. Seans süresi — çok uzun günler: bir hareketi hafif güne taşı.
 * Her bulgu en fazla iki uygulanabilir seçenekle gelir; her seçeneğin süre etkisi hesaplanır.
 * ========================================================================== */

enum class AdviceKind(val label: String) {
    DEFICIT("Eksik hacim"), REDUCE("Fazla hacim"), REDUNDANT("Tekrarlayan hareket"), BALANCE("Denge"),
    ORDER("Sıralama"), REST("Dinlenme"), FREQUENCY("Sıklık"), DAY_LOAD("Seans süresi")
}
enum class AdviceSeverity { HIGH, MEDIUM, LOW }

sealed class AdviceAction {
    data class SetSets(val item: RoutineItemEntity, val newSets: Int) : AdviceAction()
    data class AddExercise(val dayId: Long, val exercise: ExerciseEntity, val sets: Int) : AdviceAction()
    data class Swap(val item: RoutineItemEntity, val exercise: ExerciseEntity, val sets: Int) : AdviceAction()
    data class Remove(val item: RoutineItemEntity) : AdviceAction()
    data class MoveToDay(val item: RoutineItemEntity, val dayId: Long) : AdviceAction()
    data class MoveTo(val item: RoutineItemEntity, val index: Int) : AdviceAction()
    data class SetRest(val item: RoutineItemEntity, val seconds: Int) : AdviceAction()
    data class Batch(val actions: List<AdviceAction>) : AdviceAction()
}

/** Bir öneriyi uygulamanın bir yolu. [minutes]: seans süresine etkisi (dk, + uzar / − kısalır). */
data class AdviceOption(val label: String, val note: String, val action: AdviceAction, val minutes: Int)

data class Advice(
    val id: String,
    val kind: AdviceKind,
    val severity: AdviceSeverity,
    val muscleKey: String?,
    val title: String,
    val detail: String,
    /** "Yan omuz 3,5 → 6,5 set · hedef 8–18" */
    val impact: String?,
    val options: List<AdviceOption>
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
    /** Ortalama tahmini seans süresi (dk). */
    val avgMinutes: Int,
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
    val highCount: Int get() = advice.count { it.severity == AdviceSeverity.HIGH }
}

object ProgramAdvisor {

    /** Yardımcı kaslar: yalnızca ciddi eksikse önerilir. */
    private val ACCESSORY = setOf(MuscleMap.OBLIQUES, MuscleMap.FOREARM, MuscleMap.TRAPS, MuscleMap.LOWER_BACK, MuscleMap.ADDUCTORS)
    private val BIG = setOf(MuscleMap.CHEST, MuscleMap.LATS, MuscleMap.UPPER_BACK, MuscleMap.QUADS, MuscleMap.HAMSTRINGS, MuscleMap.GLUTES)

    private const val WORK_SEC = 40

    /** Bir hareketin tahmini süresi (dk): set × (iş + dinlenme). */
    fun itemMinutes(sets: Int, rest: Int): Float = sets * (WORK_SEC + rest) / 60f

    fun isCompound(ex: ExerciseEntity): Boolean =
        RepScheme.classify(ex.name, ex.muscleGroup, ex.equipment, ex.trackingType).let { it != null && it != RepScheme.ISOLATION }

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
        val weightCache = HashMap<Long, Map<String, Float>>()
        fun weightsOf(it: RoutineItemEntity): Map<String, Float> = weightCache.getOrPut(it.id) {
            val ex = exMap[it.exerciseId] ?: return@getOrPut emptyMap()
            MuscleMap.resolve(it.customName.ifBlank { ex.name }, ex.muscleGroup, ex.secondaryMuscles).weights()
        }
        fun exWeights(ex: ExerciseEntity) = MuscleMap.resolve(ex.name, ex.muscleGroup, ex.secondaryMuscles).weights()
        fun nameOf(it: RoutineItemEntity) = it.customName.ifBlank { exMap[it.exerciseId]?.name ?: "" }
        fun primaries(it: RoutineItemEntity) = weightsOf(it).filterValues { f -> f >= 0.75f }.keys
        fun compound(it: RoutineItemEntity) = exMap[it.exerciseId]?.let { e -> isCompound(e) } ?: false

        // Kas → haftalık etkin set; kas → gün → set
        val sets = HashMap<String, Float>()
        val perDay = HashMap<String, HashMap<Long, Float>>()
        work.forEach { item ->
            weightsOf(item).forEach { (m, f) ->
                sets[m] = (sets[m] ?: 0f) + f * item.targetSets
                perDay.getOrPut(m) { HashMap() }.merge(item.dayId, f * item.targetSets) { a, b -> a + b }
            }
        }
        // Önerilen değişiklikler uygulanmış gibi güncellenen tahmini hacim (aynı hareketi iki kez "fazla" saymamak için)
        val proj = HashMap(sets)
        fun project(item: RoutineItemEntity?, ex: ExerciseEntity?, n: Int) {
            item?.let { weightsOf(it).forEach { (m, f) -> proj[m] = (proj[m] ?: 0f) - f * it.targetSets } }
            ex?.let { exWeights(it).forEach { (m, f) -> proj[m] = (proj[m] ?: 0f) + f * n } }
        }
        fun region(m: String) = when (m) {
            MuscleMap.QUADS, MuscleMap.HAMSTRINGS, MuscleMap.GLUTES, MuscleMap.CALVES, MuscleMap.ADDUCTORS -> 1
            MuscleMap.ABS, MuscleMap.OBLIQUES, MuscleMap.LOWER_BACK -> 2
            else -> 0
        }
        val muscles = MuscleMap.all
        fun target(k: String) = MuscleMap.weeklyTarget(k)
        fun status(k: String) = MuscleLoad(k, sets[k] ?: 0f, 0f, -1, target(k)).status
        val advice = mutableListOf<Advice>()
        val usedItems = mutableSetOf<Long>()
        val addedExercises = mutableSetOf<Long>()
        val dayName = days.associate { it.id to it.name }
        val byDay = work.groupBy { it.dayId }
        val dayMinutes = HashMap<Long, Float>().apply {
            days.forEach { d -> put(d.id, byDay[d.id].orEmpty().sumOf { itemMinutes(it.targetSets, it.restSeconds).toDouble() }.toFloat()) }
        }
        fun lightestDay(filter: (RoutineDayEntity) -> Boolean = { true }) =
            days.filter(filter).minByOrNull { dayMinutes[it.id] ?: 0f }

        /** Bu kası birincil çalıştıran, programda olmayan en uygun hareket. */
        fun bestFor(k: String): ExerciseEntity? {
            val inProgram = work.map { it.exerciseId }.toSet() + addedExercises
            return exercises.asSequence()
                .filter { it.id !in inProgram && it.muscleGroup != Muscles.CARDIO }
                .map { ex -> ex to (exWeights(ex)[k] ?: 0f) }
                .filter { it.second >= 0.75f }
                .sortedWith(compareByDescending<Pair<ExerciseEntity, Float>> { it.first.id in doneBefore }
                    .thenByDescending { it.first.isFavorite }
                    .thenByDescending { k in BIG && isCompound(it.first) }
                    .thenByDescending { it.second })
                .firstOrNull()?.first
        }

        /**
         * Çıkarılabilecek hareket: birincil kaslarının hepsi o hareket olmadan da hedefin alt sınırında
         * kalıyor (ya da üst sınırı aşıyor). Tekrarlayan / izole / fazla kası çalıştıran öncelikli.
         */
        fun donorFor(k: String): RoutineItemEntity? = work.asSequence()
            .filter { it.id !in usedItems && (weightsOf(it)[k] ?: 0f) < 0.5f && primaries(it).isNotEmpty() }
            // Aynı bölge: üst vücut hareketi üst vücut hareketiyle, bacak bacakla değişir
            .filter { d -> primaries(d).all { region(it) == region(k) } }
            .filter { d ->
                weightsOf(d).filterValues { it >= 0.5f }.all { (m, f) ->
                    val rest = (proj[m] ?: 0f) - f * d.targetSets
                    rest >= target(m).first
                }
            }
            .sortedWith(compareByDescending<RoutineItemEntity> { d ->
                // aynı gün aynı birincil kası çalıştıran başka hareket var mı (tekrar)
                byDay[d.dayId].orEmpty().any { o -> o.id != d.id && primaries(o).intersect(primaries(d)).isNotEmpty() }
            }.thenBy { compound(it) }
                .thenByDescending { d -> primaries(d).maxOfOrNull { m -> (sets[m] ?: 0f) / target(m).last.coerceAtLeast(1) } ?: 0f })
            .firstOrNull()

        /* 1) Hacim eksikleri */
        muscles.map { it to (sets[it] ?: 0f) }
            .filter { (k, v) -> v < target(k).first }
            .filter { (k, v) -> k !in ACCESSORY || v < target(k).first * 0.5f }
            .sortedBy { (k, v) -> v / target(k).first.coerceAtLeast(1) }
            .forEach { (k, v) ->
                val t = target(k)
                val missing = (t.first - v).coerceAtLeast(1f)
                val need = missing.roundToInt().coerceIn(2, 6)
                val sev = when {
                    k in ACCESSORY -> AdviceSeverity.LOW
                    v < t.first * 0.5f -> AdviceSeverity.HIGH
                    v < t.first * 0.8f -> AdviceSeverity.MEDIUM
                    else -> AdviceSeverity.LOW
                }
                val options = mutableListOf<AdviceOption>()
                var gainBest = 0f

                // a) Süre artırmadan: fazla / tekrar eden hareketin yerine
                val donor = if (region(k) == 2) null else donorFor(k)
                val repl = bestFor(k)
                if (donor != null && repl != null) {
                    val f = exWeights(repl)[k] ?: 1f
                    val gain = donor.targetSets * f
                    if (gain >= 2f) {
                        val freed = primaries(donor).joinToString(", ") { MuscleMap.label(it).lowercase(TR) }
                        options += AdviceOption(
                            "${nameOf(donor)} yerine ${repl.name}",
                            "${dayName[donor.dayId]} · ${donor.targetSets} set. ${nameOf(donor)} olmadan da $freed hedefte kalıyor; seans süresi değişmez.",
                            AdviceAction.Swap(donor, repl, donor.targetSets), 0
                        )
                        gainBest = gain
                    }
                }
                // b) Mevcut harekete set ekle
                val bump = work.mapNotNull { item -> (weightsOf(item)[k] ?: 0f).takeIf { it >= 0.75f }?.let { item to it } }
                    .filter { (item, _) -> item.targetSets < 5 && item.id !in usedItems }
                    .minByOrNull { (item, _) -> item.targetSets }
                if (bump != null) {
                    val (item, f) = bump
                    val add = minOf(need, 5 - item.targetSets).coerceAtLeast(1)
                    options += AdviceOption(
                        "${nameOf(item)} +$add set",
                        "${dayName[item.dayId]} · ${item.targetSets} → ${item.targetSets + add} set. Yeni hareket öğrenmeden eksiği kapatır.",
                        AdviceAction.SetSets(item, item.targetSets + add),
                        itemMinutes(add, item.restSeconds).roundToInt()
                    )
                    if (gainBest == 0f) gainBest = add * f
                }
                // c) Yeni hareket ekle
                if (options.size < 2 && repl != null) {
                    val group = MuscleMap.parentGroup(k)
                    val day = lightestDay { d -> byDay[d.id].orEmpty().any { exMap[it.exerciseId]?.muscleGroup == group } } ?: lightestDay()
                    if (day != null) {
                        val addSets = need.coerceIn(2, 4)
                        options += AdviceOption(
                            "${day.name} gününe ${repl.name}",
                            "$addSets set ${repl.name} (${repl.equipment})." + if (repl.id in doneBefore) " Daha önce yaptığın bir hareket." else "",
                            AdviceAction.AddExercise(day.id, repl, addSets),
                            itemMinutes(addSets, repl.defaultRestSeconds).roundToInt()
                        )
                        if (gainBest == 0f) gainBest = addSets * (exWeights(repl)[k] ?: 1f)
                    }
                }
                if (options.isEmpty()) return@forEach
                val chosen = options.take(2)
                when (val a = chosen.first().action) {
                    is AdviceAction.Swap -> project(a.item, a.exercise, a.sets)
                    is AdviceAction.SetSets -> weightsOf(a.item).forEach { (m, f) -> proj[m] = (proj[m] ?: 0f) + f * (a.newSets - a.item.targetSets) }
                    is AdviceAction.AddExercise -> project(null, a.exercise, a.sets)
                    else -> {}
                }
                chosen.forEach { o ->
                    when (val a = o.action) {
                        is AdviceAction.Swap -> { usedItems += a.item.id; addedExercises += a.exercise.id }
                        is AdviceAction.SetSets -> usedItems += a.item.id
                        is AdviceAction.AddExercise -> addedExercises += a.exercise.id
                        else -> {}
                    }
                }
                advice += Advice(
                    "def_$k", AdviceKind.DEFICIT, sev, k,
                    "${MuscleMap.label(k)} eksik çalışıyor",
                    "Haftada ${v.fmt()} etkin set; önerilen ${t.first}–${t.last}. " +
                        if (chosen.first().minutes == 0) "Seansı uzatmadan kapatılabilir." else "Yaklaşık ${missing.fmt()} set eksik.",
                    "${MuscleMap.label(k)} ${v.fmt()} → ${(v + gainBest).fmt()} set · hedef ${t.first}–${t.last}",
                    chosen
                )
            }

        /* 2) Fazla hacim */
        muscles.filter { k -> (sets[k] ?: 0f) > target(k).last * 1.3f }.forEach { k ->
            val v = sets[k] ?: 0f
            val t = target(k)
            val cands = work.filter { it.id !in usedItems && (weightsOf(it)[k] ?: 0f) >= 0.75f }
            if (cands.isEmpty()) return@forEach
            val options = mutableListOf<AdviceOption>()
            // Bu hareket tamamen çıkarılabilir mi (kas yine de hedefte kalıyor)?
            val removable = cands.filter { !compound(it) }.maxByOrNull { it.targetSets }
                ?.takeIf { r -> v - (weightsOf(r)[k] ?: 1f) * r.targetSets >= t.first }
            if (removable != null) {
                options += AdviceOption(
                    "${nameOf(removable)} çıkar",
                    "${dayName[removable.dayId]} · ${removable.targetSets} set. ${MuscleMap.label(k)} yine de hedefte kalır.",
                    AdviceAction.Remove(removable), -itemMinutes(removable.targetSets, removable.restSeconds).roundToInt()
                )
            }
            val top = cands.filter { it.targetSets > 2 }.maxByOrNull { it.targetSets }
            if (top != null) {
                val cut = minOf(top.targetSets - 2, ((v - t.last) / (weightsOf(top)[k] ?: 1f)).roundToInt().coerceAtLeast(1))
                options += AdviceOption(
                    "${nameOf(top)} −$cut set",
                    "${dayName[top.dayId]} · ${top.targetSets} → ${top.targetSets - cut} set.",
                    AdviceAction.SetSets(top, top.targetSets - cut), -itemMinutes(cut, top.restSeconds).roundToInt()
                )
            }
            if (options.isEmpty()) return@forEach
            options.forEach { o -> (o.action as? AdviceAction.Remove)?.let { usedItems += it.item.id }; (o.action as? AdviceAction.SetSets)?.let { usedItems += it.item.id } }
            advice += Advice(
                "red_$k", AdviceKind.REDUCE, AdviceSeverity.MEDIUM, k,
                "${MuscleMap.label(k)} fazla yükleniyor",
                "Haftada ${v.fmt()} etkin set; üst sınır ${t.last}. Fazlası toparlanmayı bozar, ek kazanç getirmez — azaltmak seansı da kısaltır.",
                "${MuscleMap.label(k)} ${v.fmt()} set · hedef ${t.first}–${t.last}",
                options.take(2)
            )
        }

        /* 3) Tekrarlayan hareketler: aynı gün aynı hareket ya da aynı kasa 3+ izole hareket */
        days.forEach { d ->
            val list = byDay[d.id].orEmpty()
            list.groupBy { it.exerciseId }.filter { it.value.size > 1 }.forEach { (_, dups) ->
                val extra = dups.drop(1).firstOrNull { it.id !in usedItems } ?: return@forEach
                usedItems += extra.id
                advice += Advice(
                    "dup_${extra.id}", AdviceKind.REDUNDANT, AdviceSeverity.MEDIUM, null,
                    "${nameOf(extra)} aynı günde iki kez",
                    "${d.name} gününde bu hareket iki ayrı satırda. Setleri tek satırda birleştir.",
                    null,
                    listOf(
                        AdviceOption(
                            "Birleştir (${dups[0].targetSets + extra.targetSets} set)",
                            "İkinci satır silinir, setler ilkine eklenir.",
                            AdviceAction.Batch(listOf(AdviceAction.SetSets(dups[0], (dups[0].targetSets + extra.targetSets).coerceAtMost(8)), AdviceAction.Remove(extra))),
                            0
                        )
                    )
                )
            }
            val iso = list.filter { !compound(it) && it.id !in usedItems }
            iso.flatMap { i -> primaries(i).map { it to i } }.groupBy({ it.first }, { it.second })
                .filter { (m, l) -> l.size >= 3 && m !in setOf(MuscleMap.ABS, MuscleMap.OBLIQUES) }
                .forEach { (m, l) ->
                    val drop = l.minByOrNull { it.targetSets } ?: return@forEach
                    if (drop.id in usedItems) return@forEach
                    usedItems += drop.id
                    val keep = l.filter { it.id != drop.id }
                    advice += Advice(
                        "iso_${d.id}_$m", AdviceKind.REDUNDANT, AdviceSeverity.LOW, m,
                        "${d.name}: ${MuscleMap.label(m)} için ${l.size} izole hareket",
                        "Aynı kası aynı gün 3+ izole hareketle çalıştırmak ek kazanç getirmez, seansı uzatır. " +
                            "${nameOf(drop)} setlerini ${nameOf(keep.first())} hareketine aktar.",
                        null,
                        listOf(
                            AdviceOption(
                                "${nameOf(drop)} çıkar, ${nameOf(keep.first())} +1 set",
                                "${MuscleMap.label(m)} hacmi neredeyse aynı kalır.",
                                AdviceAction.Batch(listOf(AdviceAction.Remove(drop), AdviceAction.SetSets(keep.first(), (keep.first().targetSets + 1).coerceAtMost(6)))),
                                -itemMinutes(drop.targetSets - 1, drop.restSeconds).roundToInt()
                            )
                        )
                    )
                }
        }

        /* 4) Denge oranları */
        fun s(vararg k: String) = k.sumOf { (sets[it] ?: 0f).toDouble() }.toFloat()
        val balances = listOf(
            BalanceRatio("İtiş / Çekiş", "İtiş", "Çekiş", s(MuscleMap.CHEST, MuscleMap.FRONT_DELT), s(MuscleMap.LATS, MuscleMap.UPPER_BACK, MuscleMap.REAR_DELT), 0.7f, 1.3f),
            BalanceRatio("Ön / Arka bacak", "Ön bacak", "Arka bacak", s(MuscleMap.QUADS), s(MuscleMap.HAMSTRINGS), 0.8f, 1.8f),
            BalanceRatio("Ön / Arka omuz", "Ön omuz", "Arka omuz", s(MuscleMap.FRONT_DELT), s(MuscleMap.REAR_DELT), 0.5f, 2.0f)
        )
        val sideMuscles = mapOf(
            "İtiş" to listOf(MuscleMap.CHEST, MuscleMap.FRONT_DELT),
            "Çekiş" to listOf(MuscleMap.LATS, MuscleMap.UPPER_BACK, MuscleMap.REAR_DELT),
            "Ön bacak" to listOf(MuscleMap.QUADS), "Arka bacak" to listOf(MuscleMap.HAMSTRINGS),
            "Ön omuz" to listOf(MuscleMap.FRONT_DELT), "Arka omuz" to listOf(MuscleMap.REAR_DELT)
        )
        balances.filter { !it.ok && (it.leftSets + it.rightSets) > 0f }.forEach { b ->
            val heavyIsLeft = b.ratio > b.idealHi
            val heavy = if (heavyIsLeft) b.left else b.right
            val light = if (heavyIsLeft) b.right else b.left
            // Hafif tarafın eksiği zaten bir öneride ele alınıyorsa tekrar etme
            if (advice.any { it.muscleKey in sideMuscles[light].orEmpty() }) return@forEach
            val lightKey = sideMuscles[light].orEmpty().minByOrNull { (sets[it] ?: 0f) / target(it).first.coerceAtLeast(1) } ?: return@forEach
            val heavyKeys = sideMuscles[heavy].orEmpty().toSet()
            val options = mutableListOf<AdviceOption>()
            val repl = bestFor(lightKey)
            // Ağır taraftan bir izole hareketi hafif tarafa çevir: süre değişmez
            val donor = work.filter { it.id !in usedItems && !compound(it) && primaries(it).any { m -> m in heavyKeys } }.maxByOrNull { it.targetSets }
            if (repl != null && donor != null) {
                options += AdviceOption(
                    "${nameOf(donor)} yerine ${repl.name}",
                    "${dayName[donor.dayId]} · ${donor.targetSets} set. Seans süresi değişmez.",
                    AdviceAction.Swap(donor, repl, donor.targetSets), 0
                )
            }
            if (repl != null) {
                val day = lightestDay()
                if (day != null) options += AdviceOption(
                    "${day.name} gününe ${repl.name}", "3 set ${repl.name} (${repl.equipment}).",
                    AdviceAction.AddExercise(day.id, repl, 3), itemMinutes(3, repl.defaultRestSeconds).roundToInt()
                )
            }
            options.forEach { o -> (o.action as? AdviceAction.Swap)?.let { usedItems += it.item.id; addedExercises += it.exercise.id }; (o.action as? AdviceAction.AddExercise)?.let { addedExercises += it.exercise.id } }
            advice += Advice(
                "bal_${b.label}", AdviceKind.BALANCE,
                if (b.ratio > b.idealHi * 1.5f || b.ratio < b.idealLo / 1.5f) AdviceSeverity.HIGH else AdviceSeverity.MEDIUM, lightKey,
                "${b.label} dengesiz",
                "$heavy tarafı ${light.lowercase(TR)} tarafından belirgin fazla (oran ${b.ratio.fmt()}, ideal ${b.idealLo.fmt()}–${b.idealHi.fmt()}). " +
                    "Uzun vadede duruş ve eklem sağlığı için ${light.lowercase(TR)} hacmini artır.",
                "${b.left} ${b.leftSets.fmt()} · ${b.right} ${b.rightSets.fmt()} set",
                options.take(2)
            )
        }

        /* 5) Sıralama: izole hareket, aynı kasın bileşiğinden önce */
        days.forEach { d ->
            val list = byDay[d.id].orEmpty().sortedBy { it.orderIndex }
            for ((i, a) in list.withIndex()) {
                if (compound(a) || a.supersetGroup != 0) continue
                val pa = primaries(a)
                val later = list.drop(i + 1).firstOrNull { b -> compound(b) && b.supersetGroup == 0 && primaries(b).intersect(pa).isNotEmpty() } ?: continue
                advice += Advice(
                    "ord_${later.id}", AdviceKind.ORDER, AdviceSeverity.LOW, pa.firstOrNull(),
                    "${d.name}: ${nameOf(later)} öne alınmalı",
                    "${nameOf(a)} (izole) aynı kası çalıştıran ${nameOf(later)} hareketinden önce. Bileşik hareketi taze kasla yapmak daha fazla ağırlık ve daha iyi ilerleme demek.",
                    null,
                    listOf(AdviceOption("${nameOf(later)} öne al", "${nameOf(a)} hareketinin önüne taşınır.", AdviceAction.MoveTo(later, i), 0))
                )
                break
            }
        }

        /* 6) Dinlenme: bileşikte çok kısa, izolede uzun */
        val shortRest = work.filter { compound(it) && it.restSeconds < 90 }
        val longRest = work.filter { !compound(it) && it.restSeconds > 120 }
        if (longRest.isNotEmpty()) {
            val saved = longRest.sumOf { ((it.restSeconds - 75) * it.targetSets).toDouble() } / 60.0 / days.size.coerceAtLeast(1)
            advice += Advice(
                "rest_long", AdviceKind.REST, AdviceSeverity.LOW, null,
                "İzole hareketlerde dinlenme uzun",
                "${longRest.size} izole harekette dinlenme 2 dakikanın üzerinde. İzole hareketlerde 60–90 sn yeterli; seans kısalır, hacim aynı kalır.",
                longRest.take(3).joinToString(", ") { nameOf(it) } + if (longRest.size > 3) " +${longRest.size - 3}" else "",
                listOf(AdviceOption("Hepsini 75 sn yap", "Seans başına ortalama ~${saved.roundToInt()} dk kazanç.",
                    AdviceAction.Batch(longRest.map { AdviceAction.SetRest(it, 75) }), -saved.roundToInt()))
            )
        }
        if (shortRest.isNotEmpty()) {
            val added = shortRest.sumOf { ((120 - it.restSeconds) * it.targetSets).toDouble() } / 60.0 / days.size.coerceAtLeast(1)
            advice += Advice(
                "rest_short", AdviceKind.REST, AdviceSeverity.LOW, null,
                "Bileşik hareketlerde dinlenme kısa",
                "${shortRest.size} bileşik harekette dinlenme 90 sn'nin altında. Ağır bileşiklerde 2–3 dk dinlenme sonraki setlerde tekrarı korur.",
                shortRest.take(3).joinToString(", ") { nameOf(it) } + if (shortRest.size > 3) " +${shortRest.size - 3}" else "",
                listOf(AdviceOption("Hepsini 120 sn yap", "Setler arası performans korunur.",
                    AdviceAction.Batch(shortRest.map { AdviceAction.SetRest(it, 120) }), added.roundToInt()))
            )
        }

        /* 7) Sıklık: büyük kas tek günde toplanmışsa → bir hareketini başka güne taşı */
        BIG.forEach { k ->
            val d = perDay[k].orEmpty().filterValues { it >= 1.5f }
            val v = sets[k] ?: 0f
            if (d.size == 1 && v >= 8f && days.size >= 2) {
                val onlyDay = d.keys.first()
                val movable = byDay[onlyDay].orEmpty().filter { (weightsOf(it)[k] ?: 0f) >= 0.75f && it.id !in usedItems }
                    .sortedBy { it.orderIndex }.drop(1).firstOrNull()
                val to = lightestDay { it.id != onlyDay }
                val options = if (movable != null && to != null) listOf(
                    AdviceOption("${nameOf(movable)} → ${to.name}", "${movable.targetSets} set başka güne taşınır; toplam hacim ve süre aynı kalır.",
                        AdviceAction.MoveToDay(movable, to.id), 0)
                ) else emptyList()
                movable?.let { usedItems += it.id }
                advice += Advice(
                    "freq_$k", AdviceKind.FREQUENCY, AdviceSeverity.LOW, k,
                    "${MuscleMap.label(k)} haftada tek günde",
                    "${v.fmt()} setin tamamı ${dayName[onlyDay]} gününde. Aynı hacmi iki güne bölmek (haftada 2×) kas gelişimi için genelde daha verimli.",
                    null, options
                )
            }
        }

        /* 8) Seans süresi */
        days.forEach { d ->
            val list = byDay[d.id].orEmpty()
            val mins = dayMinutes[d.id] ?: 0f
            val n = list.sumOf { it.targetSets }
            if (mins > 80f || n >= 26) {
                val movable = list.filter { !compound(it) && it.id !in usedItems }.maxByOrNull { it.orderIndex }
                val to = lightestDay { it.id != d.id }?.takeIf { (dayMinutes[it.id] ?: 0f) + 10 < mins }
                val options = if (movable != null && to != null) listOf(
                    AdviceOption("${nameOf(movable)} → ${to.name}", "Son sıradaki izole hareket daha hafif güne taşınır.",
                        AdviceAction.MoveToDay(movable, to.id), -itemMinutes(movable.targetSets, movable.restSeconds).roundToInt())
                ) else emptyList()
                advice += Advice(
                    "day_${d.id}", AdviceKind.DAY_LOAD, if (mins > 95f) AdviceSeverity.MEDIUM else AdviceSeverity.LOW, null,
                    "${d.name} ~${mins.roundToInt()} dk",
                    "${list.size} hareket · $n set. 75–80 dakikanın üzerinde son hareketlerin kalitesi belirgin düşer.",
                    null, options
                )
            }
        }

        // Skor: kas başına hedefe yakınlık (%75) + denge (%25)
        val muscleScore = muscles.map { k ->
            val v = sets[k] ?: 0f; val t = target(k)
            when {
                v < t.first -> (v / t.first).coerceIn(0f, 1f)
                v <= t.last -> 1f
                else -> (1f - (v - t.last) / t.last).coerceIn(0.4f, 1f)
            }
        }.average().toFloat()
        val balanceScore = balances.count { it.ok }.toFloat() / balances.size
        val score = ((muscleScore * 0.75f + balanceScore * 0.25f) * 100).roundToInt().coerceIn(0, 100)
        val trained = days.filter { byDay[it.id].orEmpty().isNotEmpty() }

        val ordered = advice.sortedWith(compareBy<Advice> { it.severity.ordinal }.thenBy { it.kind.ordinal })
        return ProgramReview(
            score = score,
            inRange = muscles.count { status(it) == LoadStatus.OPTIMAL },
            totalMuscles = muscles.size,
            weeklySets = work.sumOf { it.targetSets },
            avgMinutes = if (trained.isEmpty()) 0 else (trained.sumOf { (dayMinutes[it.id] ?: 0f).toDouble() } / trained.size).roundToInt(),
            sets = sets,
            balances = balances,
            advice = ordered
        )
    }

    private val TR = java.util.Locale("tr")
    private fun Float.fmt(): String = trimNum().replace('.', ',')
}
