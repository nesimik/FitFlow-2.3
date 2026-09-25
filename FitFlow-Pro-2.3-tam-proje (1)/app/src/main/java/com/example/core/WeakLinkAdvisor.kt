package com.example.core

import com.example.data.ExerciseEntity
import com.example.data.RoutineDayEntity
import com.example.data.RoutineEntity
import com.example.data.RoutineItemEntity

/**
 * Zayıf Halkalar Akıllı İyileştirme Öneri Tipi
 */
enum class RecommendationActionType {
    ADD,   // Güne yeni hareket ekle
    SWAP   // Yoğun gündeki gereksiz/izole hareketi çıkarıp yerine ekle
}

data class WeakLinkRecommendation(
    val id: String,
    val weakMuscleKey: String,
    val weakMuscleLabel: String,
    val currentSets: Int,
    val targetSetsRange: IntRange,
    val recommendedExercise: ExerciseEntity,
    val equipment: String,
    val targetDay: RoutineDayEntity,
    val actionType: RecommendationActionType,
    val itemToSwap: RoutineItemEntity? = null,
    val exerciseToSwap: ExerciseEntity? = null,
    val reasoning: String,
    val isDenseDay: Boolean = false,
    val estimatedTimeMinutes: Int = 6
)

private data class RecommendationResult(
    val actionType: RecommendationActionType,
    val itemToSwap: RoutineItemEntity?,
    val exerciseToSwap: ExerciseEntity?,
    val reasoning: String
)

object WeakLinkAdvisor {

    /**
     * Aktif programı, hareket kütüphanesini ve kas yüklenmelerini analiz ederek
     * kompakt, makinesiz (Dambıl/Barbell/Vücut ağırlığı) ve çakışmayan akıllı öneriler sunar.
     */
    fun analyzeAndRecommend(
        exercises: List<ExerciseEntity>,
        activeRoutine: RoutineEntity?,
        routineDays: List<RoutineDayEntity>,
        allItems: List<RoutineItemEntity>,
        muscleLoads: List<MuscleLoad>
    ): List<WeakLinkRecommendation> {
        if (activeRoutine == null || routineDays.isEmpty()) {
            return emptyList()
        }

        val exMap = exercises.associateBy { it.id }
        val itemsByDay = allItems.groupBy { it.dayId }

        // 1. Gerçekten hedefinin altında kalan zayıf kasları filtresiz belirle (En zayıftan başlayarak)
        val weakMuscles = muscleLoads
            .filter { it.status == LoadStatus.LOW || it.status == LoadStatus.BELOW || it.effectiveSets < it.target.first }
            .sortedBy { it.effectiveSets.toDouble() / maxOf(1, it.target.first) }

        // Eğer zayıf halka yoksa boş liste dön (Mükemmel kas dengesi)
        if (weakMuscles.isEmpty()) {
            return emptyList()
        }

        val activeExerciseIds = allItems.map { it.exerciseId }.toSet()
        val freeWeightExercises = exercises.filter { isFreeWeightOrBodyweight(it.equipment) }

        val usedDays = mutableSetOf<Long>()
        val usedSwapItemIds = mutableSetOf<Long>()
        val recommendations = mutableListOf<WeakLinkRecommendation>()

        val remainingWeakKeys = weakMuscles.map { it.key }.toMutableSet()

        // Maksimum 2-3 adet son derece etkili ve çakışmayan öneri sun
        for (load in weakMuscles) {
            if (remainingWeakKeys.isEmpty() || recommendations.size >= 3) break
            if (!remainingWeakKeys.contains(load.key)) continue

            val muscleKey = load.key
            val muscleLabel = MuscleMap.label(muscleKey)
            val parentGroup = MuscleMap.parentGroup(muscleKey)

            // 2. Çoklu kas çalıştıran bileşik (compound) dambıl/barbell hareketlerini önceliklendir
            val candidateExercises = freeWeightExercises
                .filter { e -> isMuscleMatch(e, muscleKey) || e.muscleGroup.equals(parentGroup, ignoreCase = true) }
                .sortedByDescending { e ->
                    // Diğer zayıf kasları da aynı anda ne kadar kapsıyor? (Bonus puan)
                    countTargetedWeakMuscles(e, remainingWeakKeys)
                }

            val bestExercise = candidateExercises.firstOrNull { it.id !in activeExerciseIds }
                ?: candidateExercises.firstOrNull()
                ?: continue

            // Bu hareketin aynı anda kapsadığı diğer zayıf kasları bul
            val coveredWeakKeys = remainingWeakKeys.filter { k -> isMuscleMatch(bestExercise, k) }.toSet()
            val allCoveredKeys = (coveredWeakKeys + muscleKey)
            val combinedLabels = allCoveredKeys.joinToString(", ") { MuscleMap.label(it) }

            // 3. Her gün için MAX 1 öneri: Daha önce öneri verilmemiş gün seç
            val targetDay = findBestUnusedDay(bestExercise, parentGroup, routineDays, itemsByDay, exMap, usedDays)
                ?: continue

            val dayItems = itemsByDay[targetDay.id] ?: emptyList()
            val totalDaySets = dayItems.sumOf { it.targetSets }
            val isDenseDay = dayItems.size >= 5 || totalDaySets >= 15

            // 4. Eğer gün yoğun veya gereksiz izole/makine hareketi varsa değişim (SWAP) öner
            val redundantItem = findRedundantItemOnDay(dayItems, exMap, bestExercise, usedSwapItemIds)

            val (actionType, itemToSwap, exerciseToSwap, reasoningText) = if (redundantItem != null) {
                val oldEx = exMap[redundantItem.exerciseId]
                val oldName = oldEx?.name ?: "Eski Hareket"
                val reason = "'${targetDay.name}' günündeki '${oldName}' hareketi programda fazlalık veya izole kalıyor. " +
                        "Bu hareketi çıkarıp yerine serbest ağırlıklı '${bestExercise.name}' eklendiğinde; " +
                        "makineye ihtiyaç duymadan zayıf halkan olan [${combinedLabels}] kaslarını aynı anda geliştirebilirsin."
                RecommendationResult(RecommendationActionType.SWAP, redundantItem, oldEx, reason)
            } else {
                val reason = "'${targetDay.name}' gününe '${bestExercise.name}' (${bestExercise.equipment}) eklendiğinde; " +
                        "herhangi bir makineye bağlı kalmadan dambıl/barbell ile zayıf halkan olan [${combinedLabels}] gelişimini hızlandırabilirsin."
                RecommendationResult(RecommendationActionType.ADD, null, null, reason)
            }

            // Günü ve swap yapılan hareketi kullanılmış olarak işaretle (Çakışmayı önler)
            usedDays.add(targetDay.id)
            if (itemToSwap != null) {
                usedSwapItemIds.add(itemToSwap.id)
            }

            recommendations.add(
                WeakLinkRecommendation(
                    id = "${muscleKey}_${bestExercise.id}_${targetDay.id}",
                    weakMuscleKey = muscleKey,
                    weakMuscleLabel = combinedLabels,
                    currentSets = load.effectiveSets.toInt(),
                    targetSetsRange = load.target,
                    recommendedExercise = bestExercise,
                    equipment = bestExercise.equipment,
                    targetDay = targetDay,
                    actionType = actionType,
                    itemToSwap = itemToSwap,
                    exerciseToSwap = exerciseToSwap,
                    reasoning = reasoningText,
                    isDenseDay = isDenseDay
                )
            )

            // Kapsanan tüm zayıf kasları kalan listeden düş
            remainingWeakKeys.removeAll(allCoveredKeys)
        }

        return recommendations
    }

    private fun isFreeWeightOrBodyweight(equipment: String): Boolean {
        val eq = equipment.lowercase()
        return eq.contains("dambıl") || eq.contains("dumbbell") ||
                eq.contains("halter") || eq.contains("barbell") ||
                eq.contains("vücut") || eq.contains("body") ||
                eq.contains("band") || eq.contains("direnç") ||
                eq.contains("kettlebell") || eq.contains("serbest")
    }

    private fun countTargetedWeakMuscles(e: ExerciseEntity, weakKeys: Set<String>): Int {
        var count = 0
        for (k in weakKeys) {
            if (isMuscleMatch(e, k)) count++
        }
        return count
    }

    private fun isMuscleMatch(e: ExerciseEntity, muscleKey: String): Boolean {
        val name = e.name.lowercase()
        val sec = e.secondaryMuscles.lowercase()
        return when (muscleKey) {
            MuscleMap.REAR_DELT -> name.contains("arka omuz") || name.contains("face pull") || name.contains("rear") || sec.contains("arka omuz")
            MuscleMap.SIDE_DELT -> name.contains("yan omuz") || name.contains("lateral") || name.contains("press")
            MuscleMap.FRONT_DELT -> name.contains("ön omuz") || name.contains("overhead") || name.contains("press")
            MuscleMap.HAMSTRINGS -> name.contains("arka bacak") || name.contains("romanian") || name.contains("rdl") || name.contains("deadlift") || sec.contains("hamstring")
            MuscleMap.GLUTES -> name.contains("kalça") || name.contains("thrust") || name.contains("lunge") || name.contains("squat")
            MuscleMap.QUADS -> name.contains("ön bacak") || name.contains("squat") || name.contains("lunge")
            MuscleMap.LATS -> name.contains("kanat") || name.contains("pull-up") || name.contains("barfiks") || name.contains("row")
            MuscleMap.TRICEPS -> name.contains("triceps") || name.contains("dips") || name.contains("skullcrusher")
            MuscleMap.BICEPS -> name.contains("biceps") || name.contains("curl")
            MuscleMap.CHEST -> name.contains("göğüs") || name.contains("bench") || name.contains("push-up") || name.contains("şınav")
            MuscleMap.ABS -> name.contains("karın") || name.contains("leg raise") || name.contains("plank")
            else -> false
        }
    }

    private fun findBestUnusedDay(
        exercise: ExerciseEntity,
        parentGroup: String,
        routineDays: List<RoutineDayEntity>,
        itemsByDay: Map<Long, List<RoutineItemEntity>>,
        exMap: Map<Long, ExerciseEntity>,
        usedDays: Set<Long>
    ): RoutineDayEntity? {
        val availableDays = routineDays.filter { it.id !in usedDays }
        if (availableDays.isEmpty()) return null

        // 1. Önce uyumlu ana kas grubunun olduğu boş günü bul
        val matchingDay = availableDays.firstOrNull { day ->
            val items = itemsByDay[day.id] ?: emptyList()
            val dayFocus = (day.focus + " " + day.name).lowercase()
            val matchesFocus = dayFocus.contains(parentGroup.lowercase()) ||
                    (parentGroup.lowercase().contains("göğüs") && dayFocus.contains("üst")) ||
                    (parentGroup.lowercase().contains("sırt") && dayFocus.contains("çekiş")) ||
                    (parentGroup.lowercase().contains("bacak") && dayFocus.contains("alt"))
            matchesFocus || items.any { item ->
                exMap[item.exerciseId]?.muscleGroup.equals(parentGroup, ignoreCase = true)
            }
        }

        if (matchingDay != null) return matchingDay

        // 2. Eğer özel kas günü bulunamadıysa kullanılmamış günler arasında en az hareketi olanı seç
        return availableDays.minByOrNull { day ->
            (itemsByDay[day.id] ?: emptyList()).size
        }
    }

    private fun findRedundantItemOnDay(
        dayItems: List<RoutineItemEntity>,
        exMap: Map<Long, ExerciseEntity>,
        recommended: ExerciseEntity,
        usedSwapItemIds: Set<Long>
    ): RoutineItemEntity? {
        val candidates = dayItems.filter { it.id !in usedSwapItemIds }
        if (candidates.size < 3) return null

        // Makine veya izole olup aynı kas grubunu çalıştıran hareket var mı?
        val machineOrIsolation = candidates.firstOrNull { item ->
            val ex = exMap[item.exerciseId] ?: return@firstOrNull false
            val isMachine = ex.equipment.lowercase().contains("makine") || ex.equipment.lowercase().contains("cable") || ex.equipment.lowercase().contains("kablo")
            val isSameGroup = ex.muscleGroup.equals(recommended.muscleGroup, ignoreCase = true)
            isMachine || isSameGroup
        }

        if (machineOrIsolation != null) return machineOrIsolation

        // Eğer gün 5+ hareket içeriyorsa son hareketi değişim adayı olarak öner
        if (candidates.size >= 5) {
            return candidates.lastOrNull()
        }

        return null
    }
}
