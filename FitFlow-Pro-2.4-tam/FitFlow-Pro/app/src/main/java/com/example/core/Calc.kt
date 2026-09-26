package com.example.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

val TR: Locale = Locale("tr", "TR")

/* ================================ Biçimlendirme ================================ */

fun Float.trimNum(): String =
    if (abs(this - this.roundToInt()) < 0.001f) this.roundToInt().toString()
    else String.format(Locale.US, "%.1f", this)

fun Float.kg(): String = "${trimNum()} kg"

/** 1234.5 -> "1.2 t" / 850 -> "850 kg" */
fun formatTonnage(kg: Float): String = when {
    kg >= 1_000_000 -> String.format(Locale.US, "%.1f kt", kg / 1_000_000f)
    kg >= 1000 -> String.format(Locale.US, "%.1f t", kg / 1000f)
    else -> "${kg.roundToInt()} kg"
}

fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

fun formatDurationShort(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return when {
        h > 0 && m > 0 -> "${h}sa ${m}dk"
        h > 0 -> "${h}sa"
        m > 0 -> "${m}dk"
        else -> "${seconds}sn"
    }
}

private fun sdf(pattern: String) = SimpleDateFormat(pattern, TR)

fun formatDate(millis: Long): String = sdf("d MMMM yyyy").format(Date(millis))
fun formatDateShort(millis: Long): String = sdf("d MMM").format(Date(millis))
fun formatDateTime(millis: Long): String = sdf("d MMMM yyyy, HH:mm").format(Date(millis))
fun formatWeekday(millis: Long): String = sdf("EEEE").format(Date(millis)).trCapitalize()
fun formatMonthYear(millis: Long): String = sdf("MMMM yyyy").format(Date(millis)).trCapitalize()
fun formatTime(millis: Long): String = sdf("HH:mm").format(Date(millis))

fun String.trCapitalize(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(TR) else it.toString() }

/** "Bugün", "Dün", "3 gün önce", tarih */
fun relativeDay(millis: Long): String {
    val days = daysBetween(millis, System.currentTimeMillis())
    return when {
        days == 0 -> "Bugün"
        days == 1 -> "Dün"
        days in 2..6 -> "$days gün önce"
        days in 7..13 -> "Geçen hafta"
        days < 0 -> formatDateShort(millis)
        else -> formatDateShort(millis)
    }
}

fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun startOfWeek(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDay(millis)
    firstDayOfWeek = Calendar.MONDAY
    val diff = (get(Calendar.DAY_OF_WEEK) + 5) % 7
    add(Calendar.DAY_OF_YEAR, -diff)
}.timeInMillis

fun startOfMonth(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDay(millis)
    set(Calendar.DAY_OF_MONTH, 1)
}.timeInMillis

fun daysBetween(from: Long, to: Long): Int =
    ((startOfDay(to) - startOfDay(from)) / 86_400_000L).toInt()

fun weekdayName(weekday: Int): String = when (weekday) {
    1 -> "Pazartesi"; 2 -> "Salı"; 3 -> "Çarşamba"; 4 -> "Perşembe"
    5 -> "Cuma"; 6 -> "Cumartesi"; 7 -> "Pazar"; else -> "Serbest"
}

fun weekdayShort(weekday: Int): String = when (weekday) {
    1 -> "Pzt"; 2 -> "Sal"; 3 -> "Çar"; 4 -> "Per"
    5 -> "Cum"; 6 -> "Cmt"; 7 -> "Paz"; else -> "—"
}

/** Calendar.DAY_OF_WEEK -> 1..7 (Pazartesi = 1) */
fun todayWeekday(): Int {
    val c = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    return (c + 5) % 7 + 1
}

/* ================================ Hesaplamalar ================================ */

object Calc {

    /** Epley: 1RM = w * (1 + r/30). 1 tekrarda ağırlığın kendisi. */
    fun epley(weight: Float, reps: Int): Float =
        if (reps <= 0 || weight <= 0f) 0f
        else if (reps == 1) weight
        else weight * (1f + reps / 30f)

    /** Brzycki: 1RM = w * 36 / (37 - r) */
    fun brzycki(weight: Float, reps: Int): Float =
        if (reps <= 0 || weight <= 0f || reps >= 37) 0f
        else if (reps == 1) weight else weight * 36f / (37f - reps)

    /** Lombardi */
    fun lombardi(weight: Float, reps: Int): Float =
        if (reps <= 0 || weight <= 0f) 0f else weight * reps.toFloat().pow(0.10f)

    /** O'Conner */
    fun oconner(weight: Float, reps: Int): Float =
        if (reps <= 0 || weight <= 0f) 0f else weight * (1f + 0.025f * reps)

    /** Uygulamanın varsayılanı: 4 formülün ortalaması (tek tekrarda ağırlığın kendisi). */
    fun e1rm(weight: Float, reps: Int): Float {
        if (weight <= 0f || reps <= 0) return 0f
        if (reps == 1) return weight
        if (reps > 20) return epley(weight, reps)
        val values = listOf(epley(weight, reps), brzycki(weight, reps), lombardi(weight, reps), oconner(weight, reps))
            .filter { it > 0f }
        return if (values.isEmpty()) 0f else values.average().toFloat()
    }

    /** Verilen 1RM'e göre hedef tekrar sayısındaki tahmini ağırlık. */
    fun weightForReps(oneRm: Float, reps: Int): Float =
        if (oneRm <= 0f || reps <= 0) 0f else oneRm / (1f + reps / 30f)

    /** 1RM yüzdesi tablosu */
    fun percentTable(oneRm: Float): List<Pair<Int, Float>> =
        listOf(100, 95, 90, 85, 80, 75, 70, 65, 60, 55, 50).map { it to oneRm * it / 100f }

    fun bmi(weightKg: Float, heightCm: Float): Float =
        if (weightKg <= 0f || heightCm <= 0f) 0f else weightKg / (heightCm / 100f).pow(2)

    fun bmiCategory(bmi: Float): String = when {
        bmi <= 0f -> "—"
        bmi < 18.5f -> "Zayıf"
        bmi < 25f -> "Normal"
        bmi < 30f -> "Fazla kilolu"
        bmi < 35f -> "Obez (1. derece)"
        else -> "Obez (2+ derece)"
    }

    /** Mifflin–St Jeor bazal metabolizma */
    fun bmr(weightKg: Float, heightCm: Float, age: Int, isMale: Boolean): Float {
        if (weightKg <= 0f || heightCm <= 0f || age <= 0) return 0f
        val base = 10f * weightKg + 6.25f * heightCm - 5f * age
        return if (isMale) base + 5f else base - 161f
    }

    fun tdee(bmr: Float, activityFactor: Float): Float = bmr * activityFactor

    /** Bar + plaka dizilimi. Tek taraftaki plakaları döner. */
    fun plates(
        totalKg: Float,
        barKg: Float = 20f,
        available: List<Float> = listOf(25f, 20f, 15f, 10f, 5f, 2.5f, 1.25f)
    ): List<Float> {
        var perSide = (totalKg - barKg) / 2f
        if (perSide <= 0f) return emptyList()
        val result = mutableListOf<Float>()
        for (p in available.sortedDescending()) {
            while (perSide >= p - 0.001f) {
                result.add(p)
                perSide -= p
                if (result.size > 40) return result
            }
        }
        return result
    }

    /** Plakalarla ulaşılabilen en yakın ağırlık */
    fun achievableWeight(
        totalKg: Float,
        barKg: Float = 20f,
        available: List<Float> = listOf(25f, 20f, 15f, 10f, 5f, 2.5f, 1.25f)
    ): Float = barKg + plates(totalKg, barKg, available).sum() * 2f

    /** Çalışma setine kadar ısınma piramidi: %40/%55/%70/%85 */
    fun warmupScheme(workingWeight: Float, barKg: Float = 20f): List<Pair<Float, Int>> {
        if (workingWeight <= barKg) return listOf(barKg to 10)
        val steps = listOf(0.40f to 8, 0.55f to 5, 0.70f to 3, 0.85f to 2)
        return steps.map { (pct, reps) ->
            val w = maxOf(barKg, roundToNearest(workingWeight * pct, 2.5f))
            w to reps
        }.distinctBy { it.first }
    }

    fun roundToNearest(value: Float, step: Float): Float =
        if (step <= 0f) value else (floor(value / step + 0.5f)) * step

    /**
     * Progresif yüklenme önerisi.
     * Son seansta tüm setler hedef tekrar aralığının üst sınırında ve RPE düşükse
     * ağırlığı artır; aralığın altındaysa ağırlığı koru/azalt.
     */
    fun suggestNextWeight(
        lastWeight: Float,
        lastReps: List<Int>,
        avgRpe: Float,
        repMin: Int,
        repMax: Int,
        increment: Float = 2.5f
    ): Pair<Float, String> {
        if (lastWeight <= 0f || lastReps.isEmpty()) return 0f to "Bu hareket için ilk kayıt — rahat bir ağırlıkla başla."
        val minDone = lastReps.minOrNull() ?: 0
        val allTop = minDone >= repMax
        val belowRange = minDone < repMin
        val hardRpe = avgRpe >= 9f
        return when {
            allTop && !hardRpe -> (lastWeight + increment) to
                "Tüm setlerde $repMax+ tekrar yaptın. Ağırlığı $increment kg artırma zamanı."
            allTop && hardRpe -> (lastWeight + increment / 2f) to
                "Hedefe ulaştın ama RPE yüksekti. Küçük bir artış (${(increment / 2f).trimNum()} kg) daha güvenli."
            belowRange && hardRpe -> (lastWeight - increment) to
                "Tekrarlar hedefin altında ve zorlanmışsın. Ağırlığı ${increment.trimNum()} kg düşürüp formu oturt."
            belowRange -> lastWeight to
                "Aynı ağırlıkta kal, $repMin tekrarı yakalamaya odaklan."
            else -> lastWeight to
                "Aynı ağırlıkla devam et, hedef $repMax tekrar. Ulaştığında ağırlık artacak."
        }
    }

    /** Toplam hacim (tonaj) */
    fun volume(sets: List<com.example.data.WorkoutSetEntity>): Float =
        sets.filter { it.isCompleted && !it.isWarmup }.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat()

    /** Wilks benzeri basitleştirilmiş güç skoru: kaldırılan / vücut ağırlığı */
    fun strengthRatio(lift: Float, bodyWeight: Float): Float =
        if (bodyWeight <= 0f) 0f else lift / bodyWeight

    /**
     * Kas grubuna göre güç seviyesi. Oran = 1RM / vücut ağırlığı.
     * Kaba bir referans; kesin bir standart değil.
     */
    fun strengthLevel(muscleGroup: String, ratio: Float): Pair<String, Int> {
        val thresholds = when (muscleGroup) {
            "Bacak" -> listOf(0.75f, 1.25f, 1.75f, 2.25f)
            "Sırt" -> listOf(0.6f, 1.0f, 1.5f, 2.0f)
            "Göğüs" -> listOf(0.5f, 0.85f, 1.25f, 1.6f)
            "Omuz" -> listOf(0.35f, 0.55f, 0.8f, 1.0f)
            else -> listOf(0.3f, 0.5f, 0.75f, 1.0f)
        }
        val names = listOf("Başlangıç", "Orta", "İleri", "Çok İleri", "Elit")
        var idx = 0
        thresholds.forEach { if (ratio >= it) idx++ }
        return names[idx] to idx
    }
}

/* ------------------------------- Kas grupları -------------------------------- */

object Muscles {
    const val CHEST = "Göğüs"
    const val BACK = "Sırt"
    const val LEGS = "Bacak"
    const val SHOULDERS = "Omuz"
    const val ARMS = "Kol"
    const val CORE = "Karın"
    const val CARDIO = "Kardiyo"

    val all = listOf(CHEST, BACK, LEGS, SHOULDERS, ARMS, CORE, CARDIO)

    /** Haftalık önerilen set aralığı (hipertrofi literatüründe yaygın aralık). */
    fun weeklyTarget(group: String): IntRange = when (group) {
        LEGS, BACK -> 12..20
        CHEST -> 10..18
        SHOULDERS -> 8..16
        ARMS -> 6..14
        CORE -> 4..12
        else -> 0..0
    }

    fun emoji(group: String): String = when (group) {
        CHEST -> "🎯"; BACK -> "🪢"; LEGS -> "🦵"; SHOULDERS -> "🏔"
        ARMS -> "💪"; CORE -> "🧱"; CARDIO -> "🏃"; else -> "⚙️"
    }

    /** İsimden kas grubu tahmini (özel hareketler ve eski veri için). */
    fun guess(name: String): String {
        val n = name.lowercase(TR)
        return when {
            listOf("bench", "göğüs", "chest", "fly", "flye", "dip", "push-up", "şınav", "pec").any { n.contains(it) } -> CHEST
            listOf("row", "kürek", "lat", "pull", "barfiks", "deadlift", "sırt", "chin", "shrug", "pullover").any { n.contains(it) } -> BACK
            listOf("squat", "bacak", "leg", "lunge", "calf", "baldır", "hamstring", "quad", "glute", "kalça", "hip thrust", "step up").any { n.contains(it) } -> LEGS
            listOf("shoulder", "omuz", "lateral", "overhead", "ohp", "press", "delt", "upright", "face pull").any { n.contains(it) } -> SHOULDERS
            listOf("curl", "biceps", "triceps", "pazu", "kol", "pushdown", "skull", "hammer", "kickback").any { n.contains(it) } -> ARMS
            listOf("plank", "crunch", "karın", "abs", "core", "dead bug", "bird dog", "leg raise", "russian", "mekik").any { n.contains(it) } -> CORE
            listOf("kardiyo", "cardio", "koş", "yürüy", "bisiklet", "run", "walk", "bike", "row machine", "kürek çek", "jump").any { n.contains(it) } -> CARDIO
            else -> CHEST
        }
    }
}

object Equipment {
    val all = listOf("Barbell", "Dumbbell", "Makine", "Kablo", "Vücut Ağırlığı", "Kettlebell", "Bant", "Diğer")
}
