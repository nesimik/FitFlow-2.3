package com.example.core

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/* ==========================================================================
 * FitFlow Pro — Progresyon Motoru
 *
 * Double progression (ör. 3x10 → 3x12 → ağırlık artır → 3x10) mantığını
 * RPE korumasıyla uygular. Sonuç bir "reçete"dir: bugün hangi ağırlıkla,
 * her sette kaç tekrar hedefleneceği ve neden.
 *
 * Tamamen saf Kotlin — Android'e bağımlı değil, birim testi yapılabilir.
 * ========================================================================== */

/** Ekipmana göre yükleme türü. Artış adımı bu türe göre seçilir. */
enum class LoadKind { BARBELL, DUMBBELL, MACHINE, BODYWEIGHT, OTHER }

fun loadKindOf(equipment: String): LoadKind = when (equipment.trim()) {
    "Barbell" -> LoadKind.BARBELL
    "Dumbbell", "Kettlebell" -> LoadKind.DUMBBELL
    "Makine", "Kablo" -> LoadKind.MACHINE
    "Vücut Ağırlığı" -> LoadKind.BODYWEIGHT
    else -> LoadKind.OTHER
}

/** Salondaki gerçek ekipmana göre ulaşılabilir artış adımları. */
data class LoadingProfile(
    val barKg: Float = 20f,
    val barbellStep: Float = 2.5f,
    val dumbbellStep: Float = 2f,
    val machineStep: Float = 5f
) {
    fun step(kind: LoadKind): Float = when (kind) {
        LoadKind.BARBELL, LoadKind.OTHER -> barbellStep
        LoadKind.DUMBBELL -> dumbbellStep
        LoadKind.MACHINE -> machineStep
        LoadKind.BODYWEIGHT -> 0f
    }.coerceAtLeast(0f)
}

/* --------------------------- Tekrar merdiveni -------------------------------
 * Hareket türüne göre haftalık "basamak" ilerlemesi. Her basamakta setler birer tekrar
 * artan hedeflerle yapılır (ör. 6-7-8). Geçen seansın tüm hedefleri tutulduysa bir üst
 * basamağa çıkılır; son basamak tamamlanınca ağırlık artar ve ilk basamağa dönülür.
 *
 *   Vücut ağırlığı (dips, barfiks): 4-5-6 → 5-6-7 → 6-7-8 → 7-8-9 → ağırlıklıya geç
 *   Ana bileşik (squat, bench, deadlift, row, OHP): 6-7-8 → … → 9-10-11
 *   Yardımcı bileşik (dambıl/makine pres, çekişler, leg press, lunge): 8-9-10 → … → 11-12-13
 *   İzole (curl, raise, fly, extension, leg curl): 11-12-13 → … → 19-20-21
 * --------------------------------------------------------------------------- */

enum class RepScheme(val label: String, val base: Int, val steps: Int, val rangeLo: Int, val rangeHi: Int) {
    BODYWEIGHT("Dips · Barfiks", 4, 4, 5, 8),
    COMPOUND("Ana bileşik", 6, 4, 6, 10),
    SEMI("Yardımcı bileşik", 8, 4, 8, 12),
    ISOLATION("İzole", 11, 9, 12, 20);

    val lastStep: Int get() = steps - 1

    /** [step] basamağının set hedefleri: ilk set taban, son set taban + 2 (ör. 6-7-8). */
    fun targets(step: Int, sets: Int): List<Int> {
        val n = sets.coerceAtLeast(1)
        return List(n) { i -> base + step + if (n <= 1) 0 else Math.round(i * 2f / (n - 1)) }
    }

    companion object {
        private fun fold(t: String) = t.lowercase(java.util.Locale("tr")).replace('ı', 'i').replace('İ', 'i')

        /**
         * Hareketin merdiven türü. Süreli / mesafeli hareketler, kardiyo ve (dips-barfiks
         * dışındaki) serbest vücut ağırlığı hareketleri için null → klasik çift progresyon.
         */
        fun classify(name: String, muscleGroup: String, equipment: String, trackingType: String): RepScheme? {
            if (trackingType == "duration" || trackingType == "distance") return null
            if (muscleGroup == Muscles.CARDIO) return null
            val n = fold(name)
            fun has(vararg k: String) = k.any { n.contains(it) }

            // Destekli/kolay varyasyonlar yardımcı bileşik sayılır
            if (has("avustralya", "inverted")) return if (trackingType == "reps") null else SEMI
            // Dips ve barfiks ailesi (ağırlıklı versiyonları dahil)
            if ((has("dips", "dip ") && !has("bench dip")) || has("barfiks", "pull-up", "pull up", "pullup", "chin-up", "chin up", "muscle-up", "muscle up"))
                return BODYWEIGHT
            // Diğer vücut ağırlığı hareketleri (şınav, mekik, leg raise…) merdivene girmez
            if (trackingType == "reps") return null

            // İzole
            if (has(
                    "fly", "crossover", "pec deck", "butterfly", "curl", "extension", "pushdown", "kickback",
                    "raise", "face pull", "reverse pec", "straight arm", "shrug", "abduction", "adduction",
                    "rotation", "wrist", "crunch", "woodchopper", "pallof", "skull", "tate press", "pull apart",
                    "rear delt", "pullover"
                )
            ) return ISOLATION

            // Yardımcı bileşik: barbell olsa bile daha hafif çalışılan varyasyonlar
            if (has("upright", "jm press", "landmine")) return SEMI

            // Ana bileşik: serbest bar / smith ile büyük hareketler
            val eq = equipment.trim()
            if (eq == "Barbell" || has("smith", "t-bar")) return COMPOUND

            return SEMI
        }
    }
}

/**
 * Uygulanan tekrar merdiveni. [RepScheme] türlerinden türetilir ya da kullanıcı kendisi tanımlar.
 * @param spread ilk ve son set arasındaki tekrar farkı (6-7-8 için 2)
 * @param reverse setler azalan sırada (8-7-6): aynı ağırlıkta yorgunluk birikimine uygun
 */
data class LadderSpec(
    val label: String,
    val base: Int,
    val steps: Int,
    val spread: Int = 2,
    val reverse: Boolean = false
) {
    val lastStep: Int get() = (steps - 1).coerceAtLeast(0)
    val rangeLo: Int get() = base
    val rangeHi: Int get() = base + lastStep + spread

    fun targets(step: Int, sets: Int): List<Int> {
        val n = sets.coerceAtLeast(1)
        val asc = List(n) { i -> base + step + if (n <= 1) 0 else Math.round(i * spread.toFloat() / (n - 1)) }
        return if (reverse) asc.reversed() else asc
    }

    /** "6-7-8 → 9-10-11" (set sayısına göre). */
    fun describe(sets: Int): String {
        val a = targets(0, sets).joinToString("-")
        return if (lastStep == 0) a else "$a → ${targets(lastStep, sets).joinToString("-")}"
    }
}

fun RepScheme.spec(reverse: Boolean = false): LadderSpec = LadderSpec(label, base, steps, 2, reverse)

enum class LadderMode(val label: String) { AUTO("Otomatik"), TYPE("Tür seç"), CUSTOM("Özel"), OFF("Kapalı") }

/** Program hareketi başına merdiven ayarı. */
data class LadderConfig(
    val mode: LadderMode = LadderMode.AUTO,
    val type: RepScheme? = null,
    val base: Int = 8,
    val steps: Int = 4,
    val spread: Int = 2,
    val reverse: Boolean = false
) {
    /** @param auto hareket için otomatik sınıflandırma (null = merdivene uygun değil) */
    fun resolve(auto: RepScheme?): LadderSpec? = when (mode) {
        LadderMode.AUTO -> auto?.spec(reverse)
        LadderMode.TYPE -> (type ?: auto ?: RepScheme.SEMI).spec(reverse)
        LadderMode.CUSTOM -> LadderSpec("Özel merdiven", base.coerceIn(1, 50), steps.coerceIn(1, 12), spread.coerceIn(0, 10), reverse)
        LadderMode.OFF -> null
    }

    val isDefault: Boolean get() = mode == LadderMode.AUTO && !reverse
}

object Ladders {
    /** Hareketin geçerli merdiveni; süreli hareketlerde her zaman null. */
    fun resolve(cfg: LadderConfig?, name: String, group: String, equipment: String, tracking: String): LadderSpec? {
        if (tracking == "duration" || tracking == "distance") return null
        return (cfg ?: LadderConfig()).resolve(RepScheme.classify(name, group, equipment, tracking))
    }
}

/** Geçmiş bir seansta bu hareketin tek bir çalışma seti. */
data class LoggedSet(val weight: Float, val reps: Int, val rpe: Float = 0f)

/** Bir hareketin geçmişteki bir seansı (yalnızca çalışma setleri). */
data class SessionLog(val dateMillis: Long, val sets: List<LoggedSet>) {
    val bestE1rm: Float get() = sets.maxOfOrNull { Calc.e1rm(it.weight, it.reps) } ?: 0f
    val volume: Float get() = sets.sumOf { (it.weight * it.reps).toDouble() }.toFloat()
    val totalReps: Int get() = sets.sumOf { it.reps }
}

enum class ProgressAction(val label: String) {
    FIRST("İlk kayıt"),
    INCREASE("Ağırlık artır"),
    REPS("Tekrar artır"),
    HOLD("Koru"),
    DECREASE("Hafiflet"),
    DELOAD("Deload")
}

data class Prescription(
    val action: ProgressAction,
    /** Bugünkü çalışma ağırlığı (0 = vücut ağırlığı / bilinmiyor). */
    val weight: Float,
    /** Her set için hedef tekrar. Boyutu = önerilen set sayısı. */
    val repTargets: List<Int>,
    val repMin: Int,
    val repMax: Int,
    /** Tek satırlık özet: "42.5 kg × 10". */
    val headline: String,
    /** Kısa gerekçe. */
    val reason: String,
    /** Art arda kaç seanstır tahmini 1RM'de yeni zirve yok. */
    val stalledSessions: Int = 0,
    /** Son iki seans da en iyi değerin belirgin altında mı? */
    val regressing: Boolean = false,
    val lastWeight: Float = 0f,
    val bestE1rm: Float = 0f
) {
    val isPlateau: Boolean get() = stalledSessions >= ProgressionEngine.PLATEAU_SESSIONS
    val sets: Int get() = repTargets.size
    fun repsFor(setIndex: Int): Int = repTargets.getOrNull(setIndex) ?: repTargets.lastOrNull() ?: repMin
}

object ProgressionEngine {

    /** Bu kadar seans üst üste yeni zirve yoksa plato sayılır. */
    const val PLATEAU_SESSIONS = 3

    /** Son setin RPE'si bu değer ve üstündeyse ağırlık artışı ertelenir. */
    const val GRIND_RPE = 9.5f

    /**
     * Bugünün reçetesini üretir.
     * @param history eski → yeni ya da karışık sıralı; içeride tarihe göre sıralanır.
     */
    fun prescribe(
        history: List<SessionLog>,
        targetSets: Int,
        repMin: Int,
        repMax: Int,
        kind: LoadKind,
        profile: LoadingProfile,
        deload: Boolean = false,
        /** Verilirse tekrar merdiveni kullanılır; program aralığı yerine türün aralığı geçerlidir. */
        scheme: RepScheme? = null,
        /** Hareket adı (ağırlıklı versiyon önerisinde kullanılır). */
        exerciseName: String = "",
        /** Kullanıcının hareket ayarından gelen merdiven; verilirse [scheme] yerine kullanılır. */
        ladder: LadderSpec? = null
    ): Prescription {
        val spec = ladder ?: scheme?.spec()
        if (spec != null) return prescribeLadder(history, targetSets, spec, kind, profile, deload, exerciseName)
        val lo = repMin.coerceAtLeast(1)
        val hi = repMax.coerceAtLeast(lo)
        val setCount = targetSets.coerceAtLeast(1)
        val sessions = history.filter { s -> s.sets.any { it.reps > 0 } }.sortedBy { it.dateMillis }
        val step = profile.step(kind)

        if (sessions.isEmpty()) {
            return Prescription(
                action = ProgressAction.FIRST,
                weight = 0f,
                repTargets = List(setCount) { lo },
                repMin = lo, repMax = hi,
                headline = "$setCount × $lo-$hi",
                reason = "İlk kayıt. $hi tekrarı RPE 7-8 ile rahat yapabileceğin bir ağırlık seç."
            )
        }

        val last = sessions.last()
        val working = last.sets.filter { it.reps > 0 }
        val w = workingWeight(working)
        val atW = working.filter { abs(it.weight - w) < 0.01f }
        val repsAtW = atW.map { it.reps }
        val topRpe = atW.maxOfOrNull { it.rpe } ?: 0f
        val stalled = stallCount(sessions)
        val regressing = isRegressing(sessions)
        val best = sessions.maxOf { it.bestE1rm }
        val bodyweight = kind == LoadKind.BODYWEIGHT || w <= 0f

        fun build(action: ProgressAction, weight: Float, reps: List<Int>, reason: String) = Prescription(
            action = action,
            weight = weight,
            repTargets = reps,
            repMin = lo, repMax = hi,
            headline = headline(weight, reps),
            reason = reason + plateauNote(stalled, action),
            stalledSessions = stalled,
            regressing = regressing,
            lastWeight = w,
            bestE1rm = best
        )

        // Deload: ~%90 yük, setler yarıya, tekrar alt sınırda.
        if (deload) {
            val dSets = maxOf(1, (setCount + 1) / 2)
            val dWeight = if (bodyweight || step <= 0f) w else {
                val r = Calc.roundToNearest(w * 0.9f, step)
                if (r >= w) nextDown(w, step) else r
            }
            return build(
                ProgressAction.DELOAD, dWeight, List(dSets) { lo },
                "Deload haftası: yük hafif, set yarıda. Amaç toparlanmak, zorlanmak değil."
            )
        }

        val required = maxOf(1, minOf(setCount, working.size))
        val allTop = repsAtW.size >= required && repsAtW.all { it >= hi }
        val minReps = repsAtW.minOrNull() ?: 0
        val grinding = topRpe >= GRIND_RPE

        // Vücut ağırlığı: yalnızca tekrarla ilerle.
        if (bodyweight || step <= 0f) {
            return if (allTop) build(
                ProgressAction.HOLD, w, List(setCount) { hi },
                "Tüm setlerde $hi tekrar. Zorlaştırma zamanı: ağırlık ekle, tempoyu yavaşlat ya da zor varyasyona geç."
            ) else build(
                ProgressAction.REPS, w, repTargetsPlusOne(repsAtW.ifEmpty { working.map { it.reps } }, setCount, lo, hi),
                "Her sette bir tekrar fazlasını hedefle. $hi tekrara ulaşınca zorlaştır."
            )
        }

        return when {
            allTop && !grinding -> build(
                ProgressAction.INCREASE, nextUp(w, step), List(setCount) { lo },
                "Tüm setlerde $hi tekrar tamam. +${(nextUp(w, step) - w).trimNum()} kg, tekrar $lo'a döner."
            )

            allTop && grinding -> build(
                ProgressAction.HOLD, w, List(setCount) { hi },
                "$hi tekrar tamam ama son set RPE ${topRpe.trimNum()}. Artıştan önce aynı işi RPE 9 altında tekrarla."
            )

            minReps < lo -> {
                val prev = sessions.getOrNull(sessions.size - 2)
                val missedBefore = prev != null && run {
                    val pw = prev.sets.filter { it.reps > 0 }
                    val pW = workingWeight(pw)
                    abs(pW - w) < 0.01f && (pw.filter { abs(it.weight - w) < 0.01f }.minOfOrNull { it.reps } ?: hi) < lo
                }
                if (missedBefore || topRpe >= 10f) build(
                    ProgressAction.DECREASE, nextDown(w, step), List(setCount) { lo },
                    if (missedBefore) "İki seanstır $lo tekrarın altındasın. Bir kademe hafifle, formu oturt, yeniden tırman."
                    else "Tükeniş setine gitmişsin. Bir kademe hafifle, RPE 8 civarında kal."
                ) else build(
                    ProgressAction.HOLD, w, List(setCount) { lo },
                    "Aynı ağırlık. Önce her sette $lo tekrarı yakala."
                )
            }

            else -> build(
                ProgressAction.REPS, w, repTargetsPlusOne(repsAtW, setCount, lo, hi),
                "Aynı ağırlıkta her sete +1 tekrar. Hepsi $hi olunca ağırlık artacak."
            )
        }
    }

    /** Tekrar merdiveni reçetesi. */
    private fun prescribeLadder(
        history: List<SessionLog>,
        targetSets: Int,
        scheme: LadderSpec,
        kind: LoadKind,
        profile: LoadingProfile,
        deload: Boolean,
        exerciseName: String
    ): Prescription {
        val setCount = targetSets.coerceAtLeast(1)
        val lo = scheme.rangeLo
        val hi = scheme.rangeHi
        val sessions = history.filter { s -> s.sets.any { it.reps > 0 } }.sortedBy { it.dateMillis }
        // Dips / barfiks: ağırlıklı versiyonda (kg alanı olan) plaka adımıyla artar.
        val step = if (kind == LoadKind.BODYWEIGHT) 0f
        else profile.step(kind).takeIf { it > 0f } ?: profile.barbellStep
        val first = scheme.targets(0, setCount)

        if (sessions.isEmpty()) {
            return Prescription(
                action = ProgressAction.FIRST,
                weight = 0f,
                repTargets = first,
                repMin = lo, repMax = hi,
                headline = first.joinToString("/"),
                reason = "${scheme.label} · merdivenin ilk basamağı ${first.joinToString("-")}. " +
                    if (kind == LoadKind.BODYWEIGHT) "Tüm setleri temiz formla tamamlamaya odaklan."
                    else "Son sette 1-2 tekrar yedek bırakacağın (RPE 8) bir ağırlık seç."
            )
        }

        val last = sessions.last()
        val working = last.sets.filter { it.reps > 0 }
        val w = workingWeight(working)
        val repsAtW = working.filter { abs(it.weight - w) < 0.01f }.map { it.reps }
        val topRpe = working.filter { abs(it.weight - w) < 0.01f }.maxOfOrNull { it.rpe } ?: 0f
        val stalled = stallCount(sessions)
        val regressing = isRegressing(sessions)
        val best = sessions.maxOf { it.bestE1rm }

        fun build(action: ProgressAction, weight: Float, reps: List<Int>, reason: String) = Prescription(
            action = action,
            weight = weight,
            repTargets = reps,
            repMin = lo, repMax = hi,
            headline = if (weight <= 0f) reps.joinToString("/") else "${weight.trimNum()} kg · ${reps.joinToString("/")}",
            reason = reason + plateauNote(stalled, action),
            stalledSessions = stalled,
            regressing = regressing,
            lastWeight = w,
            bestE1rm = best
        )

        if (deload) {
            val dSets = maxOf(1, (setCount + 1) / 2)
            val dWeight = if (w <= 0f || step <= 0f) w else {
                val r = Calc.roundToNearest(w * 0.9f, step)
                if (r >= w) nextDown(w, step) else r
            }
            return build(
                ProgressAction.DELOAD, dWeight, scheme.targets(0, dSets),
                "Deload haftası: yük hafif, set yarıda, ilk basamak. Amaç toparlanmak."
            )
        }

        // Geçen seans hangi basamağın hedeflerini tam tuttu?
        val required = maxOf(1, minOf(setCount, repsAtW.size))
        fun met(stepIdx: Int): Boolean {
            if (repsAtW.size < required) return false
            val t = scheme.targets(stepIdx, setCount)
            return (0 until required).all { i -> repsAtW[i] >= t[i] }
        }
        val kMet = (scheme.lastStep downTo 0).firstOrNull { met(it) } ?: -1
        val minReps = repsAtW.minOrNull() ?: 0

        // Son basamak tamam → ağırlık artır, ilk basamağa dön
        if (kMet == scheme.lastStep) {
            val top = scheme.targets(scheme.lastStep, setCount).joinToString("-")
            return when {
                topRpe >= GRIND_RPE -> build(
                    ProgressAction.HOLD, w, scheme.targets(scheme.lastStep, setCount),
                    "$top tamam ama son set RPE ${topRpe.trimNum()}. Artıştan önce aynı basamağı RPE 9 altında tekrarla."
                )
                step <= 0f -> build(
                    ProgressAction.INCREASE, w, scheme.targets(scheme.lastStep, setCount),
                    "$top tamamlandı — ağırlıklı sisteme geçme zamanı. Programda '${weightedVariant(exerciseName)}' ile değiştir; kemerle +2,5 kg ekleyip ${first.joinToString("-")}'dan başla."
                )
                else -> {
                    val nw = nextUp(w, step)
                    build(
                        ProgressAction.INCREASE, nw, first,
                        "$top merdiveni tamam. +${(nw - w).trimNum()} kg, ${first.joinToString("-")} ile yeniden başla."
                    )
                }
            }
        }

        // Taban basamağın belirgin altında: iki seans üst üste ise hafiflet
        if (kMet < 0 && minReps < scheme.base - 1 && step > 0f && w > 0f) {
            val prev = sessions.getOrNull(sessions.size - 2)
            val missedBefore = prev != null && run {
                val pw = prev.sets.filter { it.reps > 0 }
                abs(workingWeight(pw) - w) < 0.01f &&
                    (pw.filter { abs(it.weight - w) < 0.01f }.minOfOrNull { it.reps } ?: hi) < scheme.base - 1
            }
            if (missedBefore || topRpe >= 10f) {
                return build(
                    ProgressAction.DECREASE, nextDown(w, step), first,
                    if (missedBefore) "İki seanstır ilk basamağın altındasın. Bir kademe hafifle, ${first.joinToString("-")} ile yeniden tırman."
                    else "Tükeniş setine gitmişsin. Bir kademe hafifle, ${first.joinToString("-")} ile devam."
                )
            }
        }

        val next = kMet + 1
        val targets = scheme.targets(next, setCount)
        val remaining = scheme.lastStep - next
        val tail = if (remaining == 0) " Bu son basamak — tamamlarsan " + (if (step > 0f) "ağırlık artacak." else "ağırlıklı sisteme geçeceksin.")
        else if (step > 0f) " Ağırlık artışına $remaining basamak kaldı."
        else " Ağırlıklı sisteme geçişe $remaining basamak kaldı."
        return if (kMet < 0) build(
            ProgressAction.HOLD, w, targets,
            "Aynı basamak: ${targets.joinToString("-")}. Geçen seans tüm hedefler tutmadı; bu hafta hepsini tamamla.$tail"
        ) else build(
            ProgressAction.REPS, w, targets,
            "Geçen hafta ${scheme.targets(kMet, setCount).joinToString("-")} tamam. Bu hafta her sete +1: ${targets.joinToString("-")}.$tail"
        )
    }

    /** Ağırlıklı versiyon önerisi: dips → "Ağırlıklı Dips", diğerleri → "Ağırlıklı Barfiks". */
    private fun weightedVariant(name: String): String {
        val n = name.lowercase(java.util.Locale("tr"))
        return when {
            n.contains("dip") -> "Ağırlıklı Dips"
            n.contains("barfiks") || n.contains("pull") || n.contains("chin") -> "Ağırlıklı Barfiks"
            else -> "daha zor / ağırlıklı bir varyasyon"
        }
    }

    /** Seansın çalışma ağırlığı: en çok set yapılan ağırlık (eşitlikte ağır olan). */
    fun workingWeight(sets: List<LoggedSet>): Float {
        if (sets.isEmpty()) return 0f
        return sets.groupBy { Math.round(it.weight * 100f) }
            .maxWithOrNull(compareBy<Map.Entry<Int, List<LoggedSet>>> { it.value.size }.thenBy { it.key })
            ?.value?.first()?.weight ?: 0f
    }

    /** Son seanslardan geriye doğru: kaç seanstır tahmini 1RM'de yeni zirve yok? */
    fun stallCount(sessions: List<SessionLog>): Int {
        val sorted = sessions.sortedBy { it.dateMillis }
        if (sorted.size < 2) return 0
        val e = sorted.map { it.bestE1rm }
        var count = 0
        for (i in e.indices.reversed()) {
            if (i == 0) break
            val priorBest = e.subList(0, i).maxOrNull() ?: 0f
            if (e[i] > priorBest + 0.05f) break
            count++
        }
        return count
    }

    /** Son iki seans da önceki en iyinin %3'ten fazla altındaysa gerileme var. */
    fun isRegressing(sessions: List<SessionLog>): Boolean {
        val sorted = sessions.sortedBy { it.dateMillis }
        if (sorted.size < 3) return false
        val before = sorted.dropLast(2).maxOf { it.bestE1rm }
        if (before <= 0f) return false
        return sorted.takeLast(2).all { it.bestE1rm < before * 0.97f }
    }

    /** Izgaraya göre bir üst ulaşılabilir ağırlık (ör. 6 kg, adım 2.5 → 7.5). */
    fun nextUp(w: Float, step: Float): Float {
        if (step <= 0f) return w
        return (floor(w / step + 1e-3f) + 1f) * step
    }

    /** Izgaraya göre bir alt ulaşılabilir ağırlık (en az 0). */
    fun nextDown(w: Float, step: Float): Float {
        if (step <= 0f) return w
        return ((ceil(w / step - 1e-3f) - 1f) * step).coerceAtLeast(0f)
    }

    private fun repTargetsPlusOne(prev: List<Int>, sets: Int, lo: Int, hi: Int): List<Int> =
        List(sets) { i ->
            val p = prev.getOrNull(i) ?: prev.lastOrNull() ?: lo
            (p + 1).coerceIn(lo, hi)
        }

    fun headline(weight: Float, reps: List<Int>): String {
        val repText = if (reps.isEmpty()) "" else if (reps.distinct().size == 1) "${reps.size} × ${reps.first()}"
        else reps.joinToString("/")
        return if (weight > 0f) "${weight.trimNum()} kg · $repText" else repText
    }

    private fun plateauNote(stalled: Int, action: ProgressAction): String =
        if (stalled >= PLATEAU_SESSIONS && action != ProgressAction.INCREASE && action != ProgressAction.DELOAD)
            " ($stalled seanstır yeni zirve yok — uyku, beslenme ve teknik gözden geçirilmeli.)"
        else ""
}

/* ==========================================================================
 * Performansa dayalı toparlanma (deload) sinyali
 * Takvime değil, gerçek performansa bakar.
 * ========================================================================== */

object ReadinessEngine {

    data class Signal(
        val deloadSuggested: Boolean,
        val regressingLifts: List<String>,
        val stalledLifts: List<String>,
        val avgRecentRpe: Float,
        val highRpe: Boolean
    ) {
        val reasons: List<String>
            get() = buildList {
                if (regressingLifts.isNotEmpty())
                    add("Gerileyen hareketler: ${regressingLifts.joinToString(", ")} — son iki seans en iyinin belirgin altında.")
                if (stalledLifts.isNotEmpty())
                    add("Platodaki hareketler: ${stalledLifts.joinToString(", ")}.")
                if (highRpe)
                    add("Son iki haftada çalışma setlerinin RPE ortalaması ${avgRecentRpe.trimNum()} — yorgunluk birikiyor.")
            }
    }

    /**
     * @param histories hareket adı → seans geçmişi (yalnızca çalışma setleri)
     * @param recentRpes son ~14 gündeki çalışma setlerinin RPE değerleri (0 olmayanlar)
     */
    fun evaluate(histories: Map<String, List<SessionLog>>, recentRpes: List<Float>): Signal {
        val eligible = histories.filterValues { it.size >= 3 }
        val regressing = eligible.filterValues { ProgressionEngine.isRegressing(it) }.keys.sorted()
        val stalled = eligible.filterValues {
            ProgressionEngine.stallCount(it) >= ProgressionEngine.PLATEAU_SESSIONS
        }.keys.filter { it !in regressing }.sorted()
        val rpes = recentRpes.filter { it > 0f }
        val avg = if (rpes.size >= 6) rpes.average().toFloat() else 0f
        val highRpe = avg >= 9f
        val suggested = regressing.size >= 2 ||
            (regressing.isNotEmpty() && highRpe) ||
            (stalled.size + regressing.size) >= 3
        return Signal(suggested, regressing, stalled, avg, highRpe)
    }
}
