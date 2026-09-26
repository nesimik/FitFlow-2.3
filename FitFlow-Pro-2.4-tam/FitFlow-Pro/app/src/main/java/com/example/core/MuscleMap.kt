package com.example.core

/**
 * Detaylı kas haritası.
 *
 * Uygulamanın ana kas grubu alanı (Göğüs / Sırt / Bacak / Omuz / Kol / Karın)
 * analiz için fazla kaba: "Kol" dediğinde biceps mi triceps mi çalıştı belli
 * olmuyor, "Sırt" dediğinde lat mı trapez mi bilinmiyor. Bu dosya her hareketi
 * 18 ayrı kas bölgesine birincil/ikincil olarak dağıtır.
 *
 * Hacim sayımı: birincil kas 1.0 set, ikincil kas 0.5 set alır. Ciddi hipertrofi
 * takip uygulamalarının kullandığı yaklaşım budur — bench press'i "sadece göğüs"
 * saymak triceps hacmini görünmez yapar.
 */
object MuscleMap {

    /* ------------------------------ Kas bölgeleri ------------------------------ */

    const val CHEST = "chest"
    const val FRONT_DELT = "front_delt"
    const val SIDE_DELT = "side_delt"
    const val REAR_DELT = "rear_delt"
    const val BICEPS = "biceps"
    const val TRICEPS = "triceps"
    const val FOREARM = "forearm"
    const val LATS = "lats"
    const val TRAPS = "traps"
    const val UPPER_BACK = "upper_back"
    const val LOWER_BACK = "lower_back"
    const val ABS = "abs"
    const val OBLIQUES = "obliques"
    const val GLUTES = "glutes"
    const val QUADS = "quads"
    const val HAMSTRINGS = "hamstrings"
    const val CALVES = "calves"
    const val ADDUCTORS = "adductors"

    /** Harita detay bölgeleri (analizde "chest" altında toplanır). */
    const val CHEST_UPPER = "chest_upper"
    const val CHEST_LOWER = "chest_lower"

    /** Analizlerde ve haritada kullanılan tüm bölgeler, üstten aşağı mantıklı sırada. */
    val all = listOf(
        CHEST, FRONT_DELT, SIDE_DELT, REAR_DELT,
        LATS, UPPER_BACK, TRAPS, LOWER_BACK,
        BICEPS, TRICEPS, FOREARM,
        ABS, OBLIQUES,
        GLUTES, QUADS, HAMSTRINGS, ADDUCTORS, CALVES
    )

    /** Ön görünümde çizilen bölgeler. */
    val frontVisible = setOf(
        CHEST, FRONT_DELT, SIDE_DELT, BICEPS, TRICEPS, FOREARM, LATS,
        ABS, OBLIQUES, QUADS, ADDUCTORS, CALVES, TRAPS
    )

    /** Arka görünümde çizilen bölgeler. */
    val backVisible = setOf(
        TRAPS, UPPER_BACK, LATS, LOWER_BACK, REAR_DELT, SIDE_DELT,
        TRICEPS, FOREARM, OBLIQUES, GLUTES, HAMSTRINGS, CALVES
    )

    fun label(key: String): String = when (key) {
        CHEST -> "Göğüs"
        FRONT_DELT -> "Ön omuz"
        SIDE_DELT -> "Yan omuz"
        REAR_DELT -> "Arka omuz"
        BICEPS -> "Biceps"
        TRICEPS -> "Triceps"
        FOREARM -> "Önkol"
        LATS -> "Kanat (lat)"
        TRAPS -> "Trapez"
        UPPER_BACK -> "Üst sırt"
        LOWER_BACK -> "Bel"
        ABS -> "Karın"
        OBLIQUES -> "Yan karın"
        GLUTES -> "Kalça"
        QUADS -> "Ön bacak"
        HAMSTRINGS -> "Arka bacak"
        CALVES -> "Baldır"
        ADDUCTORS -> "İç bacak"
        CHEST_UPPER -> "Üst göğüs"
        CHEST_LOWER -> "Alt göğüs"
        else -> key
    }

    /** Bölgenin ait olduğu ana kas grubu (mevcut renk paletiyle uyum için). */
    fun parentGroup(key: String): String = when (key) {
        CHEST -> Muscles.CHEST
        FRONT_DELT, SIDE_DELT, REAR_DELT -> Muscles.SHOULDERS
        BICEPS, TRICEPS, FOREARM -> Muscles.ARMS
        LATS, TRAPS, UPPER_BACK, LOWER_BACK -> Muscles.BACK
        ABS, OBLIQUES -> Muscles.CORE
        GLUTES, QUADS, HAMSTRINGS, CALVES, ADDUCTORS -> Muscles.LEGS
        else -> Muscles.CHEST
    }

    /**
     * Haftalık önerilen etkin set aralığı.
     * Hipertrofi literatüründe yaygın kabul gören aralıklar; kesin reçete değil,
     * "çok az / yeterli / çok fazla" ayrımı yapmak için referans.
     */
    fun weeklyTarget(key: String): IntRange = when (key) {
        CHEST -> 10..20
        LATS -> 10..20
        UPPER_BACK -> 8..16
        TRAPS -> 4..12
        LOWER_BACK -> 4..12
        FRONT_DELT -> 4..14
        SIDE_DELT -> 8..18
        REAR_DELT -> 6..14
        BICEPS -> 8..16
        TRICEPS -> 8..16
        FOREARM -> 2..10
        ABS -> 6..16
        OBLIQUES -> 4..12
        GLUTES -> 8..16
        QUADS -> 10..20
        HAMSTRINGS -> 8..16
        CALVES -> 6..16
        ADDUCTORS -> 2..10
        // Üst+alt katsayıların toplamı ~1.6 olduğu için grup hedefinin ~%80i: grupla tutarlı durum verir.
        CHEST_UPPER, CHEST_LOWER -> 8..16
        else -> 0..0
    }

    /** Kasın tipik toparlanma süresi (gün). Küçük kaslar daha çabuk toparlanır. */
    fun recoveryDays(key: String): Int = when (key) {
        QUADS, HAMSTRINGS, GLUTES, LOWER_BACK, LATS -> 3
        CHEST, UPPER_BACK, TRAPS -> 2
        FRONT_DELT, SIDE_DELT, REAR_DELT, BICEPS, TRICEPS -> 2
        ABS, OBLIQUES, CALVES, FOREARM, ADDUCTORS -> 1
        else -> 2
    }

    /* --------------------------- Hareket → kas eşlemesi -------------------------- */

    data class Activation(
        val primary: List<String>,
        val secondary: List<String> = emptyList(),
        /** Hareket için tanımlı kas katkı oranları (0..1). Varsa birincil/ikincil varsayımının yerine geçer. */
        val custom: Map<String, Float>? = null
    ) {
        val isEmpty: Boolean get() = primary.isEmpty() && secondary.isEmpty()

        /** Set ağırlıkları: tanımlıysa katkı oranları, değilse birincil 1.0 / ikincil 0.5 */
        fun weights(): Map<String, Float> {
            custom?.let { return it }
            val m = LinkedHashMap<String, Float>()
            primary.forEach { m[it] = 1f }
            secondary.forEach { if (!m.containsKey(it)) m[it] = 0.5f }
            return m
        }
    }

    /* ----------------------------- Kas katkı oranları ---------------------------- */
    // Bir setin her kasa kaç "etkin set" olarak yazılacağı. 1.0 = hedef kas, 0.5 civarı =
    // belirgin destek, 0.2-0.3 = küçük katkı. EMG ve hipertrofi çalışmalarındaki genel
    // eğilimlere dayanan yaklaşık değerlerdir; kesin ölçüm değil, dengeli analiz için referans.
    // Yalnızca haritada fark yaratacak kadar yaygın hareketler tanımlıdır; diğerleri
    // birincil/ikincil varsayımına düşer.

    private val contributions: Map<String, Map<String, Float>> by lazy {
        val m = HashMap<String, Map<String, Float>>()
        fun c(vararg names: String, w: Map<String, Float>) = names.forEach { m[norm(it)] = w }

        // Göğüs
        c("Bench Press", "Smith Machine Bench Press", "Chest Press Makinesi",
            w = mapOf(CHEST to 1f, FRONT_DELT to 0.5f, TRICEPS to 0.5f))
        c("Dumbbell Bench Press", "Dumbbell Floor Press", "Floor Press",
            w = mapOf(CHEST to 1f, FRONT_DELT to 0.45f, TRICEPS to 0.4f))
        c("Incline Bench Press", "Incline Dumbbell Press", "Smith Machine Incline Press", "Incline Chest Press Makinesi",
            w = mapOf(CHEST to 1f, FRONT_DELT to 0.7f, TRICEPS to 0.4f))
        c("Decline Bench Press", "Decline Dumbbell Press",
            w = mapOf(CHEST to 1f, TRICEPS to 0.5f, FRONT_DELT to 0.25f))
        c("Şınav (Push-Up)", "Push-Up", "Şınav", "Deficit Push-Up", "Incline Push-Up",
            w = mapOf(CHEST to 1f, FRONT_DELT to 0.5f, TRICEPS to 0.5f, ABS to 0.25f))
        c("Decline Push-Up", w = mapOf(CHEST to 1f, FRONT_DELT to 0.7f, TRICEPS to 0.5f, ABS to 0.25f))
        c("Dumbbell Fly", "Incline Dumbbell Fly", "Cable Crossover", "Pec Deck / Butterfly",
            w = mapOf(CHEST to 1f, FRONT_DELT to 0.3f))
        c("Dips (Göğüs)", "Ağırlıklı Dips", w = mapOf(CHEST to 1f, TRICEPS to 0.8f, FRONT_DELT to 0.5f))

        // Omuz
        c("Overhead Press", "Seated Barbell OHP", "Dumbbell Shoulder Press", "Arnold Press",
            "Machine Shoulder Press", "Smith Machine Shoulder Press",
            w = mapOf(FRONT_DELT to 1f, SIDE_DELT to 0.5f, TRICEPS to 0.5f, TRAPS to 0.2f))
        c("Lateral Raise", "Cable Lateral Raise", "Egyptian Cable Lateral Raise", "Machine Lateral Raise",
            "Dumbbell Lateral Raise", w = mapOf(SIDE_DELT to 1f, TRAPS to 0.2f))
        c("Rear Delt Fly (Dumbbell)", "Rear Delt Fly", "Dumbbell Reverse Fly", "Reverse Fly",
            "Reverse Pec Deck", "Cable Rear Delt Cross", w = mapOf(REAR_DELT to 1f, UPPER_BACK to 0.5f, TRAPS to 0.2f))
        c("Face Pull", w = mapOf(REAR_DELT to 1f, UPPER_BACK to 0.7f, TRAPS to 0.3f))

        // Sırt
        c("One Arm Dumbbell Row", "One-Arm Dumbbell Row", "Single Arm Dumbbell Row", "Bent Over Dumbbell Row",
            w = mapOf(LATS to 1f, UPPER_BACK to 0.7f, BICEPS to 0.5f, REAR_DELT to 0.4f, FOREARM to 0.2f))
        c("Barbell Row", "Pendlay Row", "T-Bar Row",
            w = mapOf(UPPER_BACK to 1f, LATS to 0.9f, BICEPS to 0.5f, REAR_DELT to 0.5f, LOWER_BACK to 0.3f, FOREARM to 0.2f))
        c("Seated Cable Row", "Chest Supported Row", "Seal Row",
            w = mapOf(UPPER_BACK to 1f, LATS to 0.8f, BICEPS to 0.5f, REAR_DELT to 0.4f))
        c("Lat Pulldown", "Close Grip Pulldown",
            w = mapOf(LATS to 1f, BICEPS to 0.5f, UPPER_BACK to 0.4f, REAR_DELT to 0.2f))
        c("Barfiks (Pull-Up)", "Ağırlıklı Barfiks", "Pull-Up",
            w = mapOf(LATS to 1f, BICEPS to 0.6f, UPPER_BACK to 0.5f, FOREARM to 0.3f, ABS to 0.1f))
        c("Chin-Up", "Reverse Grip Lat Pulldown", w = mapOf(LATS to 1f, BICEPS to 0.8f, UPPER_BACK to 0.4f, FOREARM to 0.3f))
        c("Shrug (Barbell)", "Shrug (Dumbbell)", "Shrug", w = mapOf(TRAPS to 1f, FOREARM to 0.3f))

        // Kalça menteşesi
        c("Deadlift", w = mapOf(GLUTES to 0.9f, HAMSTRINGS to 0.8f, LOWER_BACK to 0.8f, QUADS to 0.5f,
            ADDUCTORS to 0.5f, TRAPS to 0.4f, FOREARM to 0.4f, LATS to 0.3f))
        c("Romanian Deadlift", "Dumbbell Romanian Deadlift", "RDL",
            w = mapOf(HAMSTRINGS to 1f, GLUTES to 0.8f, LOWER_BACK to 0.5f, ADDUCTORS to 0.4f, FOREARM to 0.25f, TRAPS to 0.15f))
        c("Hip Thrust", "Machine Hip Thrust", "Glute Bridge", w = mapOf(GLUTES to 1f, HAMSTRINGS to 0.3f, ADDUCTORS to 0.2f))

        // Bacak
        c("Barbell Squat", "Back Squat", w = mapOf(QUADS to 1f, GLUTES to 0.8f, ADDUCTORS to 0.5f,
            LOWER_BACK to 0.3f, HAMSTRINGS to 0.15f, ABS to 0.15f))
        c("Goblet Squat", w = mapOf(QUADS to 1f, GLUTES to 0.6f, ADDUCTORS to 0.4f, ABS to 0.2f, UPPER_BACK to 0.15f))
        c("Front Squat", w = mapOf(QUADS to 1f, GLUTES to 0.6f, ADDUCTORS to 0.4f, ABS to 0.25f, UPPER_BACK to 0.25f))
        c("Leg Press", "Hack Squat", "Pendulum Squat", w = mapOf(QUADS to 1f, GLUTES to 0.6f, ADDUCTORS to 0.4f))
        c("Walking Lunge", "Reverse Lunge", "Bulgarian Split Squat", "Step Up",
            w = mapOf(QUADS to 1f, GLUTES to 0.8f, ADDUCTORS to 0.4f, HAMSTRINGS to 0.2f, CALVES to 0.1f))
        c("Leg Extension", w = mapOf(QUADS to 1f))
        c("Leg Curl (Yatarak)", "Seated Leg Curl", "Standing Leg Curl", "Leg Curl", w = mapOf(HAMSTRINGS to 1f, CALVES to 0.1f))
        c("Calf Raise (Ayakta)", "Standing Calf Raise", "Seated Calf Raise", "Donkey Calf Raise", "Calf Raise",
            w = mapOf(CALVES to 1f))

        // Kol
        c("Dumbbell Curl", "Barbell Curl", "EZ-Bar Curl", "Cable Curl", "Incline Dumbbell Curl",
            w = mapOf(BICEPS to 1f, FOREARM to 0.3f))
        c("Hammer Curl", "Cable Hammer Curl", "Cross Body Hammer Curl", w = mapOf(BICEPS to 0.8f, FOREARM to 0.7f))
        c("Triceps Pushdown", "Triceps Pushdown (Düz Bar)", "Rope Pushdown", "Rope Pushdown (Halat)",
            "Overhead Triceps Extension", "Overhead Cable Triceps Extension", "Skull Crusher", w = mapOf(TRICEPS to 1f))
        c("Close Grip Bench Press", w = mapOf(TRICEPS to 1f, CHEST to 0.6f, FRONT_DELT to 0.4f))

        // 2.3 ile kütüphaneye eklenenler
        c("Dumbbell Leg Curl", "Sliding Leg Curl", w = mapOf(HAMSTRINGS to 1f, CALVES to 0.1f, GLUTES to 0.1f))
        c("Stiff Leg Deadlift", w = mapOf(HAMSTRINGS to 1f, GLUTES to 0.7f, LOWER_BACK to 0.6f, FOREARM to 0.25f))
        c("Single Leg Romanian Deadlift", w = mapOf(HAMSTRINGS to 1f, GLUTES to 0.9f, LOWER_BACK to 0.3f, ADDUCTORS to 0.3f, FOREARM to 0.2f))
        c("Cable Pull-Through", w = mapOf(GLUTES to 1f, HAMSTRINGS to 0.7f, LOWER_BACK to 0.2f))
        c("Dumbbell Hip Thrust", "Single Leg Glute Bridge", w = mapOf(GLUTES to 1f, HAMSTRINGS to 0.35f, ADDUCTORS to 0.2f))
        c("Cable Hip Abduction", "Abduction (Kalça Açma)", w = mapOf(GLUTES to 1f))
        c("Heel Elevated Goblet Squat", w = mapOf(QUADS to 1f, GLUTES to 0.5f, ADDUCTORS to 0.35f, ABS to 0.2f))
        c("Dumbbell Split Squat", "Smith Machine Squat", w = mapOf(QUADS to 1f, GLUTES to 0.7f, ADDUCTORS to 0.4f, HAMSTRINGS to 0.15f))
        c("Single Leg Calf Raise", w = mapOf(CALVES to 1f))
        c("Chest Supported Dumbbell Row", w = mapOf(UPPER_BACK to 1f, LATS to 0.7f, REAR_DELT to 0.6f, BICEPS to 0.5f))
        c("Single Arm Lat Pulldown", "Single Arm Cable Row", w = mapOf(LATS to 1f, BICEPS to 0.5f, UPPER_BACK to 0.4f))
        c("Dumbbell External Rotation", "Cable External Rotation", w = mapOf(REAR_DELT to 1f, UPPER_BACK to 0.3f))
        c("Incline Y Raise", w = mapOf(REAR_DELT to 1f, TRAPS to 0.6f, SIDE_DELT to 0.5f, UPPER_BACK to 0.5f))
        c("Landmine Press", w = mapOf(FRONT_DELT to 1f, CHEST to 0.5f, TRICEPS to 0.5f, SIDE_DELT to 0.3f, ABS to 0.2f))
        c("Seated Dumbbell Lateral Raise", w = mapOf(SIDE_DELT to 1f, TRAPS to 0.15f))
        c("Bayesian Cable Curl", w = mapOf(BICEPS to 1f, FOREARM to 0.2f))
        c("Zottman Curl", w = mapOf(BICEPS to 0.9f, FOREARM to 0.8f))
        c("Suitcase Carry", w = mapOf(OBLIQUES to 1f, FOREARM to 0.7f, TRAPS to 0.5f, ABS to 0.4f))
        c("Reverse Crunch", w = mapOf(ABS to 1f, OBLIQUES to 0.3f))
        c("Copenhagen Plank", w = mapOf(ADDUCTORS to 1f, OBLIQUES to 0.8f, ABS to 0.3f))

        // Karın
        c("Plank", w = mapOf(ABS to 1f, OBLIQUES to 0.5f, LOWER_BACK to 0.2f))
        c("Crunch", "Crunch (Mekik)", "Cable Crunch", w = mapOf(ABS to 1f, OBLIQUES to 0.3f))
        c("Hanging Leg Raise", "Leg Raise", "Leg Raise (Yerde)", w = mapOf(ABS to 1f, OBLIQUES to 0.4f, FOREARM to 0.1f))
        c("Russian Twist", "Side Plank", "Pallof Press", "Cable Woodchopper", w = mapOf(OBLIQUES to 1f, ABS to 0.5f))
        m
    }

    /** Katkı tablosundan Activation üretir: 0.75 ve üstü birincil, diğerleri destek. */
    private fun fromContribution(w: Map<String, Float>) = Activation(
        primary = w.filterValues { it >= 0.75f }.keys.toList(),
        secondary = w.filterValues { it < 0.75f }.keys.toList(),
        custom = w
    )

    /**
     * Göğüs setinin üst / alt göğüse dağılımı (göğüs katkısının çarpanı).
     * Eğimli presler üst göğsü, düz ve negatif presler alt/orta göğsü ağırlıklı çalıştırır.
     */
    fun chestSplit(name: String): Pair<Float, Float> {
        // Türkçe küçük harf dönüşümü "Incline" → "ıncline" yapar; Latin karşılaştırma için ı → i.
        val n = norm(name).replace('ı', 'i')
        return when {
            n.contains("decline push") -> 1f to 0.6f            // ayaklar yüksekte = üst göğüs
            n.contains("incline push") -> 0.3f to 1f            // eller yüksekte = alt göğüs
            n.contains("incline") || n.contains("low to high") || n.contains("landmine") -> 1f to 0.6f
            n.contains("decline") || n.contains("dip") || n.contains("high to low") -> 0.3f to 1f
            else -> 0.55f to 1f
        }
    }

    private fun norm(s: String): String =
        s.lowercase(TR).replace("’", "'").replace(Regex("\\s+"), " ").trim()

    private val table: Map<String, Activation> by lazy {
        val m = LinkedHashMap<String, Activation>()
        fun e(name: String, primary: List<String>, secondary: List<String> = emptyList()) {
            m[norm(name)] = Activation(primary, secondary)
        }

        /* --------------------------------- Göğüs --------------------------------- */
        e("Bench Press", listOf(CHEST), listOf(FRONT_DELT, TRICEPS))
        e("Incline Bench Press", listOf(CHEST, FRONT_DELT), listOf(TRICEPS))
        e("Decline Bench Press", listOf(CHEST), listOf(TRICEPS, FRONT_DELT))
        e("Dumbbell Bench Press", listOf(CHEST), listOf(FRONT_DELT, TRICEPS))
        e("Incline Dumbbell Press", listOf(CHEST, FRONT_DELT), listOf(TRICEPS))
        e("Decline Dumbbell Press", listOf(CHEST), listOf(TRICEPS, FRONT_DELT))
        e("Floor Press", listOf(CHEST), listOf(TRICEPS, FRONT_DELT))
        e("Dumbbell Floor Press", listOf(CHEST), listOf(TRICEPS, FRONT_DELT))
        e("Dumbbell Fly", listOf(CHEST), listOf(FRONT_DELT))
        e("Incline Dumbbell Fly", listOf(CHEST), listOf(FRONT_DELT))
        e("Cable Crossover", listOf(CHEST), listOf(FRONT_DELT))
        e("Low to High Cable Fly", listOf(CHEST, FRONT_DELT), listOf())
        e("Pec Deck / Butterfly", listOf(CHEST), listOf(FRONT_DELT))
        e("Chest Press Makinesi", listOf(CHEST), listOf(TRICEPS, FRONT_DELT))
        e("Incline Chest Press Makinesi", listOf(CHEST, FRONT_DELT), listOf(TRICEPS))
        e("Dips (Göğüs)", listOf(CHEST, TRICEPS), listOf(FRONT_DELT))
        e("Ağırlıklı Dips", listOf(CHEST, TRICEPS), listOf(FRONT_DELT))
        e("Bench Dip", listOf(TRICEPS), listOf(CHEST, FRONT_DELT))
        e("Şınav (Push-Up)", listOf(CHEST), listOf(TRICEPS, FRONT_DELT, ABS))
        e("Diamond Push-Up", listOf(TRICEPS, CHEST), listOf(FRONT_DELT))
        e("Decline Push-Up", listOf(CHEST, FRONT_DELT), listOf(TRICEPS))
        e("Incline Push-Up", listOf(CHEST), listOf(TRICEPS, FRONT_DELT))
        e("Deficit Push-Up", listOf(CHEST), listOf(FRONT_DELT, TRICEPS))
        e("Pullover", listOf(LATS, CHEST), listOf(TRICEPS))
        e("Smith Machine Bench Press", listOf(CHEST), listOf(TRICEPS, FRONT_DELT))
        e("Smith Machine Incline Press", listOf(CHEST, FRONT_DELT), listOf(TRICEPS))
        e("Landmine Chest Press", listOf(CHEST, FRONT_DELT), listOf(TRICEPS))

        /* ---------------------------------- Sırt --------------------------------- */
        e("Deadlift", listOf(LOWER_BACK, GLUTES, HAMSTRINGS), listOf(TRAPS, QUADS, FOREARM, LATS))
        e("Romanian Deadlift", listOf(HAMSTRINGS, GLUTES), listOf(LOWER_BACK, FOREARM))
        e("Dumbbell Romanian Deadlift", listOf(HAMSTRINGS, GLUTES), listOf(LOWER_BACK, FOREARM))
        e("Sumo Deadlift", listOf(GLUTES, QUADS), listOf(LOWER_BACK, HAMSTRINGS, TRAPS, ADDUCTORS))
        e("Trap Bar Deadlift", listOf(QUADS, GLUTES, LOWER_BACK), listOf(TRAPS, FOREARM, LATS))
        e("Barbell Row", listOf(LATS, UPPER_BACK), listOf(BICEPS, REAR_DELT, LOWER_BACK))
        e("Pendlay Row", listOf(LATS, UPPER_BACK), listOf(BICEPS, TRAPS))
        e("T-Bar Row", listOf(LATS, UPPER_BACK), listOf(BICEPS, REAR_DELT))
        e("Chest Supported Row", listOf(UPPER_BACK, LATS), listOf(REAR_DELT, BICEPS))
        e("One Arm Dumbbell Row", listOf(LATS, UPPER_BACK), listOf(BICEPS, REAR_DELT))
        e("Bent Over Dumbbell Row", listOf(LATS, UPPER_BACK), listOf(BICEPS, REAR_DELT))
        e("Meadows Row", listOf(LATS, UPPER_BACK), listOf(REAR_DELT, BICEPS))
        e("Seal Row", listOf(UPPER_BACK, LATS), listOf(REAR_DELT, BICEPS))
        e("Kroc Row", listOf(LATS, UPPER_BACK, TRAPS), listOf(BICEPS, FOREARM))
        e("Barfiks (Pull-Up)", listOf(LATS), listOf(BICEPS, UPPER_BACK, FOREARM))
        e("Ağırlıklı Barfiks", listOf(LATS), listOf(BICEPS, UPPER_BACK, FOREARM))
        e("Chin-Up", listOf(LATS, BICEPS), listOf(UPPER_BACK, FOREARM))
        e("Neutral Grip Pull-Up", listOf(LATS, BICEPS), listOf(UPPER_BACK, FOREARM))
        e("Lat Pulldown", listOf(LATS), listOf(BICEPS, UPPER_BACK))
        e("Close Grip Pulldown", listOf(LATS), listOf(BICEPS))
        e("Reverse Grip Lat Pulldown", listOf(LATS, BICEPS), listOf(UPPER_BACK))
        e("Seated Cable Row", listOf(UPPER_BACK, LATS), listOf(BICEPS, REAR_DELT))
        e("Wide Grip Cable Row", listOf(UPPER_BACK, REAR_DELT), listOf(LATS, BICEPS))
        e("Single Arm Cable Row", listOf(LATS), listOf(BICEPS, UPPER_BACK))
        e("Straight Arm Pulldown", listOf(LATS), listOf(ABS))
        e("Face Pull", listOf(REAR_DELT, UPPER_BACK), listOf(TRAPS))
        e("Shrug (Barbell)", listOf(TRAPS), listOf(FOREARM))
        e("Shrug (Dumbbell)", listOf(TRAPS), listOf(FOREARM))
        e("Shrug", listOf(TRAPS), listOf(FOREARM))
        e("Hyperextension", listOf(LOWER_BACK, GLUTES), listOf(HAMSTRINGS))
        e("Good Morning", listOf(HAMSTRINGS, LOWER_BACK), listOf(GLUTES))
        e("Rack Pull", listOf(TRAPS, LOWER_BACK), listOf(GLUTES, FOREARM, LATS))
        e("Avustralya Barfiksi (Inverted Row)", listOf(UPPER_BACK, LATS), listOf(BICEPS))

        /* --------------------------------- Bacak --------------------------------- */
        e("Barbell Squat", listOf(QUADS, GLUTES), listOf(LOWER_BACK, HAMSTRINGS, ABS, ADDUCTORS))
        e("Front Squat", listOf(QUADS), listOf(GLUTES, ABS, UPPER_BACK))
        e("Goblet Squat", listOf(QUADS, GLUTES), listOf(ABS, ADDUCTORS))
        e("Hack Squat", listOf(QUADS), listOf(GLUTES))
        e("Leg Press", listOf(QUADS, GLUTES), listOf(HAMSTRINGS))
        e("Pendulum Squat", listOf(QUADS), listOf(GLUTES))
        e("Bulgarian Split Squat", listOf(QUADS, GLUTES), listOf(HAMSTRINGS, ADDUCTORS))
        e("Walking Lunge", listOf(QUADS, GLUTES), listOf(HAMSTRINGS, ADDUCTORS))
        e("Reverse Lunge", listOf(GLUTES, QUADS), listOf(HAMSTRINGS))
        e("Step Up", listOf(QUADS, GLUTES), listOf(HAMSTRINGS, CALVES))
        e("Leg Extension", listOf(QUADS))
        e("Leg Curl (Yatarak)", listOf(HAMSTRINGS), listOf(CALVES))
        e("Seated Leg Curl", listOf(HAMSTRINGS), listOf(CALVES))
        e("Standing Leg Curl", listOf(HAMSTRINGS), listOf(CALVES))
        e("Hip Thrust", listOf(GLUTES), listOf(HAMSTRINGS, ABS))
        e("Machine Hip Thrust", listOf(GLUTES), listOf(HAMSTRINGS))
        e("Glute Bridge", listOf(GLUTES), listOf(HAMSTRINGS))
        e("Cable Glute Kickback", listOf(GLUTES), listOf())
        e("Abduction (Kalça Açma)", listOf(GLUTES), listOf())
        e("Adduction (İç Bacak)", listOf(ADDUCTORS), listOf(QUADS))
        e("Calf Raise (Ayakta)", listOf(CALVES), listOf())
        e("Seated Calf Raise", listOf(CALVES), listOf())
        e("Donkey Calf Raise", listOf(CALVES), listOf())
        e("Tibialis Raise", listOf(CALVES), listOf())
        e("Sissy Squat", listOf(QUADS), listOf())
        e("Bodyweight Squat", listOf(QUADS, GLUTES))
        e("Wall Sit", listOf(QUADS), listOf(GLUTES))
        e("Nordic Curl", listOf(HAMSTRINGS), listOf(GLUTES, CALVES))
        e("Pistol Squat", listOf(QUADS, GLUTES), listOf(HAMSTRINGS))

        /* ---------------------------------- Omuz --------------------------------- */
        e("Overhead Press", listOf(FRONT_DELT, SIDE_DELT), listOf(TRICEPS, TRAPS, ABS))
        e("Push Press", listOf(FRONT_DELT), listOf(TRICEPS, QUADS, TRAPS))
        e("Seated Barbell OHP", listOf(FRONT_DELT, SIDE_DELT), listOf(TRICEPS))
        e("Dumbbell Shoulder Press", listOf(FRONT_DELT, SIDE_DELT), listOf(TRICEPS))
        e("Arnold Press", listOf(FRONT_DELT, SIDE_DELT), listOf(TRICEPS))
        e("Machine Shoulder Press", listOf(FRONT_DELT, SIDE_DELT), listOf(TRICEPS))
        e("Smith Machine Shoulder Press", listOf(FRONT_DELT, SIDE_DELT), listOf(TRICEPS))
        e("Lateral Raise", listOf(SIDE_DELT), listOf(TRAPS))
        e("Cable Lateral Raise", listOf(SIDE_DELT), listOf(TRAPS))
        e("Egyptian Cable Lateral Raise", listOf(SIDE_DELT), listOf(TRAPS))
        e("Machine Lateral Raise", listOf(SIDE_DELT), listOf())
        e("Front Raise (Dumbbell)", listOf(FRONT_DELT), listOf(CHEST))
        e("Front Raise (Plate/Plaka)", listOf(FRONT_DELT), listOf(CHEST))
        e("Cable Front Raise", listOf(FRONT_DELT), listOf(CHEST))
        e("Front Raise", listOf(FRONT_DELT), listOf(CHEST))
        e("Rear Delt Fly (Dumbbell)", listOf(REAR_DELT), listOf(UPPER_BACK))
        e("Rear Delt Fly", listOf(REAR_DELT), listOf(UPPER_BACK))
        e("Reverse Pec Deck", listOf(REAR_DELT), listOf(UPPER_BACK))
        e("Cable Rear Delt Cross", listOf(REAR_DELT), listOf(UPPER_BACK))
        e("Upright Row (Barbell)", listOf(SIDE_DELT, TRAPS), listOf(BICEPS))
        e("Upright Row (Dumbbell)", listOf(SIDE_DELT, TRAPS), listOf(BICEPS))
        e("Cable Upright Row", listOf(SIDE_DELT, TRAPS), listOf(BICEPS))
        e("Upright Row", listOf(SIDE_DELT, TRAPS), listOf(BICEPS))
        e("Pike Push-Up", listOf(FRONT_DELT), listOf(TRICEPS, SIDE_DELT))
        e("Handstand Push-Up (Amut Şınav)", listOf(FRONT_DELT, SIDE_DELT), listOf(TRICEPS, TRAPS))

        /* ----------------------------------- Kol --------------------------------- */
        e("Barbell Curl", listOf(BICEPS), listOf(FOREARM))
        e("EZ-Bar Curl", listOf(BICEPS), listOf(FOREARM))
        e("Dumbbell Curl", listOf(BICEPS), listOf(FOREARM))
        e("Incline Dumbbell Curl", listOf(BICEPS), listOf())
        e("Spider Curl", listOf(BICEPS), listOf())
        e("Preacher Curl (Scott Curl)", listOf(BICEPS), listOf())
        e("Preacher Curl", listOf(BICEPS), listOf())
        e("Dumbbell Preacher Curl", listOf(BICEPS), listOf())
        e("Concentration Curl", listOf(BICEPS), listOf())
        e("Hammer Curl", listOf(BICEPS, FOREARM), listOf())
        e("Cable Hammer Curl", listOf(BICEPS, FOREARM), listOf())
        e("Cross Body Hammer Curl", listOf(BICEPS, FOREARM), listOf())
        e("Cable Curl", listOf(BICEPS), listOf(FOREARM))
        e("High Cable Curl (Herakles)", listOf(BICEPS), listOf())
        e("Reverse Curl", listOf(FOREARM, BICEPS), listOf())
        e("21s Biceps Curl", listOf(BICEPS), listOf(FOREARM))

        e("Skull Crusher (Alna Triceps)", listOf(TRICEPS), listOf())
        e("Skull Crusher", listOf(TRICEPS), listOf())
        e("Dumbbell Skull Crusher", listOf(TRICEPS), listOf())
        e("Close Grip Bench Press", listOf(TRICEPS), listOf(CHEST, FRONT_DELT))
        e("Triceps Pushdown (Düz Bar)", listOf(TRICEPS), listOf())
        e("Triceps Pushdown", listOf(TRICEPS), listOf())
        e("Rope Pushdown (Halat)", listOf(TRICEPS), listOf())
        e("Rope Pushdown", listOf(TRICEPS), listOf())
        e("V-Bar Pushdown", listOf(TRICEPS), listOf())
        e("Overhead Cable Triceps Extension", listOf(TRICEPS), listOf())
        e("Overhead Triceps Extension", listOf(TRICEPS), listOf())
        e("Overhead Dumbbell Extension", listOf(TRICEPS), listOf())
        e("Single Arm Overhead DB Extension", listOf(TRICEPS), listOf())
        e("Triceps Kickback (Dumbbell)", listOf(TRICEPS), listOf())
        e("Cable Triceps Kickback", listOf(TRICEPS), listOf())
        e("Triceps Kickback", listOf(TRICEPS), listOf())
        e("Dips (Triceps Odaklı)", listOf(TRICEPS), listOf(CHEST, FRONT_DELT))
        e("JM Press", listOf(TRICEPS), listOf(CHEST))
        e("Tate Press", listOf(TRICEPS), listOf())

        e("Wrist Curl (Bilek Bükme)", listOf(FOREARM), listOf())
        e("Wrist Curl", listOf(FOREARM), listOf())
        e("Reverse Wrist Curl", listOf(FOREARM), listOf())
        e("Farmer's Walk", listOf(FOREARM, TRAPS), listOf(ABS, QUADS))
        e("Plate Pinch Hold", listOf(FOREARM), listOf())

        /* ---------------------------------- Karın -------------------------------- */
        e("Plank", listOf(ABS), listOf(OBLIQUES, LOWER_BACK))
        e("Side Plank", listOf(OBLIQUES), listOf(ABS))
        e("RKC Plank", listOf(ABS, OBLIQUES), listOf(LOWER_BACK))
        e("Crunch (Mekik)", listOf(ABS), listOf())
        e("Crunch", listOf(ABS), listOf())
        e("Decline Bench Crunch", listOf(ABS), listOf())
        e("Weighted Crunch", listOf(ABS), listOf())
        e("Cable Crunch", listOf(ABS), listOf(OBLIQUES))
        e("Bicycle Crunch", listOf(ABS, OBLIQUES), listOf())
        e("Russian Twist", listOf(OBLIQUES), listOf(ABS))
        e("Leg Raise (Yerde)", listOf(ABS), listOf(OBLIQUES))
        e("Leg Raise", listOf(ABS), listOf(OBLIQUES))
        e("Hanging Leg Raise", listOf(ABS), listOf(OBLIQUES, FOREARM))
        e("Hanging Knee Raise", listOf(ABS), listOf(OBLIQUES))
        e("Ab Wheel Rollout", listOf(ABS), listOf(LATS, LOWER_BACK))
        e("Dead Bug", listOf(ABS), listOf(LOWER_BACK))
        e("Bird Dog", listOf(LOWER_BACK), listOf(ABS, GLUTES))
        e("Hollow Body Hold", listOf(ABS), listOf())
        e("Pallof Press", listOf(OBLIQUES), listOf(ABS))
        e("Cable Woodchopper", listOf(OBLIQUES), listOf(ABS))
        e("Mountain Climber", listOf(ABS), listOf(QUADS))
        e("V-Up", listOf(ABS), listOf())
        e("Toe to Bar", listOf(ABS), listOf(FOREARM))
        e("Mide Vakumu (Stomach Vacuum)", listOf(ABS), listOf())

        /* --------------------------- Kardiyo & mobilite -------------------------- */
        e("Eğimli Yürüyüş", emptyList(), listOf(CALVES, GLUTES))
        e("Koşu", emptyList(), listOf(QUADS, CALVES, HAMSTRINGS))
        e("Sprint / HIIT", emptyList(), listOf(QUADS, CALVES, HAMSTRINGS, GLUTES))
        e("Bisiklet", emptyList(), listOf(QUADS, CALVES))
        e("Spin Bike", emptyList(), listOf(QUADS, CALVES, GLUTES))
        e("AirBike / Assault Bike", emptyList(), listOf(QUADS, CALVES, CHEST, LATS))
        e("Kürek Makinesi", emptyList(), listOf(LATS, UPPER_BACK, QUADS))
        e("İp Atlama", emptyList(), listOf(CALVES))
        e("Burpee", emptyList(), listOf(QUADS, CHEST, ABS))
        e("Kettlebell Swing", listOf(GLUTES, HAMSTRINGS), listOf(LOWER_BACK, FRONT_DELT))
        e("Box Jump", listOf(QUADS, GLUTES), listOf(CALVES))
        e("Battle Ropes (Halat)", emptyList(), listOf(SIDE_DELT, FRONT_DELT, ABS))
        e("Sled Push (Kızak İtme)", listOf(QUADS, GLUTES), listOf(CALVES))
        e("Slam Ball", emptyList(), listOf(ABS, FRONT_DELT, LATS))
        e("Jumping Jack", emptyList(), listOf(CALVES, SIDE_DELT))
        e("Merdiven / StairMaster", emptyList(), listOf(GLUTES, QUADS, CALVES))

        e("Arm Circles", emptyList(), listOf(SIDE_DELT))
        e("Shoulder Dislocates", emptyList(), listOf(FRONT_DELT, CHEST))
        e("Band Pull Apart", emptyList(), listOf(REAR_DELT, UPPER_BACK))
        e("Scapula Push-Up", emptyList(), listOf(UPPER_BACK))
        e("Cat-Cow", emptyList(), listOf(LOWER_BACK))
        e("World's Greatest Stretch", emptyList(), listOf(GLUTES, HAMSTRINGS, UPPER_BACK))
        e("90/90 Kalça Açma", emptyList(), listOf(GLUTES))
        e("Hip Hinge Drill", emptyList(), listOf(HAMSTRINGS))
        e("Cossack Squat", emptyList(), listOf(ADDUCTORS, QUADS, GLUTES))
        e("Couch Stretch", emptyList(), listOf(QUADS))
        e("Thoracic Rotation", emptyList(), listOf(UPPER_BACK))
        e("Pigeon Pose (Güvercin Duruşu)", emptyList(), listOf(GLUTES))
        e("Ölü Asılma (Dead Hang)", emptyList(), listOf(LATS, FOREARM))
        e("Boş Bar Squat", emptyList(), listOf(QUADS))
        e("Boş Bar Bench", emptyList(), listOf(CHEST))
        e("Boş Bar OHP", emptyList(), listOf(FRONT_DELT))

        m
    }

    /**
     * Hareket adından (ve gerekirse ana kas grubundan) kas aktivasyonunu bulur.
     * Tabloda yoksa ada göre anahtar kelime tahmini, o da tutmazsa ana kas
     * grubunun varsayılan dağılımı kullanılır.
     */
    fun resolve(name: String, muscleGroup: String = "", secondaryMuscles: String = ""): Activation {
        contributions[norm(name)]?.let { return fromContribution(it) }
        table[norm(name)]?.let { return it }

        val n = norm(name)
        fun has(vararg keys: String) = keys.any { n.contains(it) }

        // İsimden tahmin
        val guessed: Activation? = when {
            has("bench", "göğüs", "chest", "fly", "flye", "pec", "şınav", "push-up", "push up") ->
                Activation(listOf(CHEST), listOf(TRICEPS, FRONT_DELT))
            has("dip") -> Activation(listOf(CHEST, TRICEPS), listOf(FRONT_DELT))
            has("deadlift", "hinge") -> Activation(listOf(HAMSTRINGS, GLUTES, LOWER_BACK), listOf(TRAPS, FOREARM))
            has("pulldown", "pull-up", "pull up", "barfiks", "chin-up", "chin up", "chinup", "lat ") || n.split(" ").any { it == "chin" || it == "chins" } ->
                Activation(listOf(LATS), listOf(BICEPS, UPPER_BACK))
            has("row", "kürek") -> Activation(listOf(LATS, UPPER_BACK), listOf(BICEPS, REAR_DELT))
            has("shrug", "trapez") -> Activation(listOf(TRAPS))
            has("hyperextension", "bel", "back extension") -> Activation(listOf(LOWER_BACK), listOf(GLUTES))
            has("squat") -> Activation(listOf(QUADS, GLUTES), listOf(HAMSTRINGS, LOWER_BACK))
            has("lunge", "split squat", "step up") -> Activation(listOf(QUADS, GLUTES), listOf(HAMSTRINGS))
            has("leg press") -> Activation(listOf(QUADS, GLUTES))
            has("leg extension") -> Activation(listOf(QUADS))
            has("leg curl", "hamstring", "nordic") -> Activation(listOf(HAMSTRINGS))
            has("hip thrust", "glute", "kalça", "abduction") -> Activation(listOf(GLUTES), listOf(HAMSTRINGS))
            has("calf", "baldır") -> Activation(listOf(CALVES))
            has("lateral raise", "yan omuz", "side delt") -> Activation(listOf(SIDE_DELT), listOf(TRAPS))
            has("rear delt", "reverse pec", "face pull", "arka omuz") -> Activation(listOf(REAR_DELT), listOf(UPPER_BACK))
            has("front raise", "ön omuz") -> Activation(listOf(FRONT_DELT))
            has("overhead press", "ohp", "shoulder press", "arnold", "push press", "omuz") ->
                Activation(listOf(FRONT_DELT, SIDE_DELT), listOf(TRICEPS))
            has("upright row") -> Activation(listOf(SIDE_DELT, TRAPS))
            has("curl") && !has("leg") -> Activation(listOf(BICEPS), listOf(FOREARM))
            has("triceps", "pushdown", "skull", "kickback", "extension") -> Activation(listOf(TRICEPS))
            has("wrist", "önkol", "grip", "farmer") -> Activation(listOf(FOREARM))
            has("plank", "hollow", "dead bug", "crunch", "mekik", "leg raise", "ab wheel", "karın") ->
                Activation(listOf(ABS), listOf(OBLIQUES))
            has("oblique", "russian twist", "side plank", "pallof", "yan karın") -> Activation(listOf(OBLIQUES), listOf(ABS))
            has("koş", "yürüy", "bisiklet", "run", "walk", "bike", "cardio", "kardiyo", "jump", "ip atlama") ->
                Activation(emptyList(), listOf(QUADS, CALVES))
            else -> null
        }
        if (guessed != null) return guessed

        // Ana kas grubuna göre varsayılan
        return when (muscleGroup) {
            Muscles.CHEST -> Activation(listOf(CHEST), listOf(TRICEPS, FRONT_DELT))
            Muscles.BACK -> Activation(listOf(LATS, UPPER_BACK), listOf(BICEPS))
            Muscles.LEGS -> Activation(listOf(QUADS, GLUTES), listOf(HAMSTRINGS))
            Muscles.SHOULDERS -> Activation(listOf(FRONT_DELT, SIDE_DELT), listOf(TRICEPS))
            Muscles.ARMS -> Activation(listOf(BICEPS, TRICEPS))
            Muscles.CORE -> Activation(listOf(ABS), listOf(OBLIQUES))
            else -> Activation(emptyList(), emptyList())
        }
    }

    /** Hareket için kısa okunabilir özet: "Göğüs, Ön omuz · Triceps" */
    fun summary(name: String, muscleGroup: String = ""): String {
        val a = resolve(name, muscleGroup)
        val p = a.primary.joinToString(", ") { label(it) }
        val s = a.secondary.joinToString(", ") { label(it) }
        return when {
            p.isBlank() && s.isBlank() -> "—"
            s.isBlank() -> p
            p.isBlank() -> "Destek: $s"
            else -> "$p  ·  $s"
        }
    }
}
