package com.example.data

import android.content.Context
import java.util.Locale
import com.example.core.Calc
import com.example.core.Muscles
import com.example.ui.Backup
import com.example.ui.ImportResult
import kotlinx.coroutines.flow.Flow

/**
 * Tek veri giriş noktası. ViewModel'ler yalnızca buraya konuşur.
 */
class FitRepository(private val context: Context, private val dao: FitDao) {

    private val TR = Locale.forLanguageTag("tr-TR")

    /* ------------------------------- Akışlar ------------------------------- */

    val exercises: Flow<List<ExerciseEntity>> = dao.observeExercises()
    val routines: Flow<List<RoutineEntity>> = dao.observeRoutines()
    val activeRoutine: Flow<RoutineEntity?> = dao.observeActiveRoutine()
    val allDays: Flow<List<RoutineDayEntity>> = dao.observeAllDays()
    val allItems: Flow<List<RoutineItemEntity>> = dao.observeAllItems()
    val workouts: Flow<List<WorkoutEntity>> = dao.observeFinishedWorkouts()
    val activeWorkout: Flow<WorkoutEntity?> = dao.observeActiveWorkout()
    val allSets: Flow<List<WorkoutSetEntity>> = dao.observeAllSets()
    val prs: Flow<List<PrEntity>> = dao.observePrs()
    val bodyMetrics: Flow<List<BodyMetricEntity>> = dao.observeBodyMetrics()
    val notes: Flow<List<NoteEntity>> = dao.observeNotes()

    fun setsOf(workoutId: Long): Flow<List<WorkoutSetEntity>> = dao.observeSets(workoutId)

    /* ------------------------------ İlk kurulum ----------------------------- */

    suspend fun seedIfNeeded(): Boolean {
        var didImport = false
        val existing = dao.getAllExercises()
        if (existing.isEmpty()) {
            dao.insertExercises(ExerciseSeed.list)
        } else {
            val existingNames = existing.map { it.name.lowercase(TR).trim() }.toSet()
            val missing = ExerciseSeed.list.filter { it.name.lowercase(TR).trim() !in existingNames }
            if (missing.isNotEmpty()) {
                dao.insertExercises(missing)
            }
        }
        if (dao.routineCount() == 0) {
            didImport = LegacyImporter(context, dao).importIfAvailable()
            if (!didImport) createDefaultRoutine()
        }
        return didImport
    }

    /** İsimden hareket bul, yoksa özel hareket olarak oluştur. */
    suspend fun ensureExercise(
        name: String,
        group: String = Muscles.guess(name),
        equipment: String = "Diğer",
        track: String = ExerciseEntity.TRACK_WEIGHT_REPS,
        video: String = ""
    ): Long {
        dao.exerciseByName(name)?.let { return it.id }
        return dao.insertExercise(
            ExerciseEntity(
                name = name, muscleGroup = group, equipment = equipment,
                trackingType = track, videoUrl = video, isCustom = true
            )
        )
    }

    private suspend fun createDefaultRoutine() {
        val routineId = dao.insertRoutine(
            RoutineEntity(
                name = "3 Günlük Güç Programı",
                description = "Push · Pull+Legs · Full Body. Beş temel bileşik hareket etrafında kurulu.",
                colorHex = "#3B82F6",
                isActive = true
            )
        )

        data class Row(val name: String, val sets: Int, val lo: Int, val hi: Int, val w: Float, val rest: Int, val warm: Boolean = false)

        suspend fun day(name: String, focus: String, weekday: Int, order: Int, rows: List<Row>) {
            val dayId = dao.insertDay(RoutineDayEntity(routineId = routineId, name = name, focus = focus, weekday = weekday, orderIndex = order))
            rows.forEachIndexed { i, r ->
                val exId = ensureExercise(r.name)
                dao.insertItem(
                    RoutineItemEntity(
                        dayId = dayId, exerciseId = exId, orderIndex = i,
                        targetSets = r.sets, repMin = r.lo, repMax = r.hi,
                        targetWeight = r.w, restSeconds = r.rest, isWarmup = r.warm
                    )
                )
            }
        }

        day("A Günü", "Push — Göğüs, Omuz, Triceps", 1, 0, listOf(
            Row("Eğimli Yürüyüş", 1, 1, 1, 0f, 0, true),
            Row("Arm Circles", 1, 15, 15, 0f, 0, true),
            Row("Scapula Push-Up", 2, 10, 10, 0f, 0, true),
            Row("Boş Bar Bench", 1, 12, 12, 0f, 0, true),
            Row("Bench Press", 3, 8, 11, 20f, 150),
            Row("Incline Dumbbell Press", 3, 9, 12, 8f, 120),
            Row("Dumbbell Shoulder Press", 3, 9, 12, 6f, 120),
            Row("Lateral Raise", 3, 12, 16, 3f, 60),
            Row("Bench Dip", 2, 10, 14, 0f, 60),
            Row("Rope Pushdown", 2, 10, 14, 0f, 60),
            Row("Plank", 3, 30, 45, 0f, 45),
            Row("Bird Dog", 2, 8, 10, 0f, 30)
        ))

        day("B Günü", "Pull + Legs — Sırt, Biceps, Bacak", 3, 1, listOf(
            Row("Eğimli Yürüyüş", 1, 1, 1, 0f, 0, true),
            Row("Cat-Cow", 1, 10, 10, 0f, 0, true),
            Row("Hip Hinge Drill", 2, 10, 10, 0f, 0, true),
            Row("Boş Bar Squat", 1, 10, 10, 0f, 0, true),
            Row("Barbell Row", 3, 8, 11, 30f, 120),
            Row("Lat Pulldown", 3, 9, 12, 30f, 90),
            Row("Barbell Squat", 3, 8, 11, 20f, 150),
            Row("Leg Extension", 3, 11, 14, 20f, 75),
            Row("Leg Curl (Yatarak)", 3, 11, 14, 20f, 75),
            Row("Dumbbell Curl", 3, 9, 12, 6f, 60),
            Row("Hammer Curl", 2, 9, 12, 6f, 60),
            Row("Face Pull", 3, 14, 18, 10f, 60)
        ))

        day("C Günü", "Full Body + Kondisyon", 5, 2, listOf(
            Row("Eğimli Yürüyüş", 1, 1, 1, 0f, 0, true),
            Row("Bodyweight Squat", 1, 15, 15, 0f, 0, true),
            Row("Band Pull Apart", 2, 15, 15, 0f, 0, true),
            Row("Romanian Deadlift", 3, 8, 11, 25f, 150),
            Row("Goblet Squat", 3, 10, 13, 10f, 105),
            Row("Şınav (Push-Up)", 3, 8, 12, 0f, 75),
            Row("One Arm Dumbbell Row", 3, 10, 13, 10f, 90),
            Row("Walking Lunge", 2, 10, 12, 6f, 90),
            Row("Dead Bug", 2, 12, 14, 0f, 30),
            Row("Side Plank", 2, 25, 40, 0f, 30),
            Row("Hanging Leg Raise", 2, 8, 12, 0f, 45),
            Row("Eğimli Yürüyüş", 1, 1, 1, 0f, 0)
        ))
    }

    /* ------------------------------- Program ------------------------------- */

    suspend fun addRoutine(name: String, description: String, colorHex: String): Long {
        val id = dao.insertRoutine(RoutineEntity(name = name, description = description, colorHex = colorHex))
        if (dao.routineCount() == 1) selectRoutine(id)
        return id
    }

    suspend fun updateRoutine(r: RoutineEntity) = dao.updateRoutine(r)
    suspend fun deleteRoutine(r: RoutineEntity) = dao.deleteRoutine(r)

    suspend fun selectRoutine(id: Long) {
        dao.clearActiveRoutines()
        dao.setActiveRoutine(id)
    }

    suspend fun addDay(routineId: Long, name: String, focus: String, weekday: Int, order: Int): Long =
        dao.insertDay(RoutineDayEntity(routineId = routineId, name = name, focus = focus, weekday = weekday, orderIndex = order))

    suspend fun updateDay(d: RoutineDayEntity) = dao.updateDay(d)

    suspend fun deleteDay(d: RoutineDayEntity) {
        dao.deleteItemsForDay(d.id)
        dao.deleteDay(d)
    }

    suspend fun addItem(dayId: Long, exerciseId: Long): Long {
        val existing = dao.itemsForDay(dayId)
        val ex = dao.exerciseById(exerciseId)
        val id = dao.insertItem(
            RoutineItemEntity(
                dayId = dayId,
                exerciseId = exerciseId,
                orderIndex = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1,
                restSeconds = ex?.defaultRestSeconds ?: 90
            )
        )
        normalizeItemOrders(dayId)
        return id
    }

    suspend fun updateItem(i: RoutineItemEntity) {
        dao.updateItem(i)
        normalizeItemOrders(i.dayId)
    }

    suspend fun deleteItem(i: RoutineItemEntity) {
        dao.deleteItem(i)
        normalizeItemOrders(i.dayId)
    }

    suspend fun toggleItemWarmup(item: RoutineItemEntity) {
        val dayItems = dao.itemsForDay(item.dayId).sortedBy { it.orderIndex }.toMutableList()
        val idx = dayItems.indexOfFirst { it.id == item.id }
        if (idx < 0) return
        val newWarmup = !item.isWarmup
        val updatedItem = item.copy(isWarmup = newWarmup)
        dayItems.removeAt(idx)

        if (newWarmup) {
            val lastWarmupIdx = dayItems.indexOfLast { it.isWarmup }
            val insertPos = if (lastWarmupIdx >= 0) lastWarmupIdx + 1 else 0
            dayItems.add(insertPos, updatedItem)
        } else {
            dayItems.add(updatedItem)
        }

        dayItems.forEachIndexed { i, entity ->
            dao.updateItem(entity.copy(orderIndex = i))
        }
    }

    suspend fun moveItem(dayId: Long, itemId: Long, up: Boolean) {
        val items = dao.itemsForDay(dayId)
            .sortedBy { it.orderIndex }
            .toMutableList()
        val idx = items.indexOfFirst { it.id == itemId }
        if (idx < 0) return
        val target = if (up) idx - 1 else idx + 1
        if (target !in items.indices) return
        val item = items.removeAt(idx)
        items.add(target, item)
        items.forEachIndexed { index, entity ->
            dao.updateItem(entity.copy(orderIndex = index))
        }
    }

    private suspend fun normalizeItemOrders(dayId: Long) {
        val items = dao.itemsForDay(dayId)
            .sortedBy { it.orderIndex }
        items.forEachIndexed { index, entity ->
            if (entity.orderIndex != index) {
                dao.updateItem(entity.copy(orderIndex = index))
            }
        }
    }

    /** Bir günü olduğu gibi kopyalar. */
    suspend fun duplicateDay(day: RoutineDayEntity) {
        val items = dao.itemsForDay(day.id)
        val newId = dao.insertDay(day.copy(id = 0, name = day.name + " (kopya)", orderIndex = day.orderIndex + 1))
        items.forEach { dao.insertItem(it.copy(id = 0, dayId = newId)) }
    }

    /* ----------------------------- Süperset Yönetimi --------------------------- */

    suspend fun setRoutineItemSuperset(dayId: Long, itemId: Long, group: Int) {
        val items = dao.itemsForDay(dayId)
        val item = items.find { it.id == itemId } ?: return
        dao.updateItem(item.copy(supersetGroup = maxOf(0, group)))
    }

    /** İki hareketi aynı süperset grubu altında birleştirir ve ardışık konuma getirir. */
    suspend fun combineRoutineItems(dayId: Long, firstItemId: Long, secondItemId: Long) {
        val items = dao.itemsForDay(dayId).sortedBy { it.orderIndex }.toMutableList()
        val first = items.find { it.id == firstItemId } ?: return
        val second = items.find { it.id == secondItemId } ?: return
        if (first.id == second.id) return

        val targetGroup = when {
            first.supersetGroup > 0 -> first.supersetGroup
            second.supersetGroup > 0 -> second.supersetGroup
            else -> (items.maxOfOrNull { it.supersetGroup } ?: 0) + 1
        }

        // İkinci öğeyi birinci öğenin hemen sonrasına taşı
        val firstIdx = items.indexOfFirst { it.id == firstItemId }
        val secondIdx = items.indexOfFirst { it.id == secondItemId }
        val secondItem = items.removeAt(secondIdx)
        val newInsertIdx = if (secondIdx < firstIdx) firstIdx else firstIdx + 1
        items.add(newInsertIdx, secondItem)

        // Güncelle
        items.forEachIndexed { idx, it ->
            val newGroup = if (it.id == firstItemId || it.id == secondItemId) targetGroup else it.supersetGroup
            dao.updateItem(it.copy(orderIndex = idx, supersetGroup = newGroup))
        }
    }

    /** Belirtilen gruptaki tüm hareketlerin süperset bağını çözer. */
    suspend fun dissolveRoutineSuperset(dayId: Long, group: Int) {
        if (group <= 0) return
        val items = dao.itemsForDay(dayId)
        items.filter { it.supersetGroup == group }.forEach {
            dao.updateItem(it.copy(supersetGroup = 0))
        }
    }

    /** Seçilen hareketleri tek bir süperset grubu olarak programa ekler. */
    suspend fun addSupersetToDay(dayId: Long, exerciseIds: List<Long>): List<Long> {
        if (exerciseIds.isEmpty()) return emptyList()
        val existing = dao.itemsForDay(dayId).sortedBy { it.orderIndex }
        val targetGroup = (existing.maxOfOrNull { it.supersetGroup } ?: 0) + 1
        var nextOrder = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val insertedIds = mutableListOf<Long>()

        exerciseIds.forEach { exId ->
            val ex = dao.exerciseById(exId) ?: return@forEach
            val id = dao.insertItem(
                RoutineItemEntity(
                    dayId = dayId,
                    exerciseId = ex.id,
                    orderIndex = nextOrder++,
                    targetSets = 3,
                    repMin = 8,
                    repMax = 12,
                    targetWeight = 0f,
                    restSeconds = 30,
                    supersetGroup = targetGroup
                )
            )
            insertedIds.add(id)
        }
        return insertedIds
    }

    /* ------------------------------ Hareketler ------------------------------ */

    suspend fun saveExercise(e: ExerciseEntity): Long {
        return if (e.id == 0L) {
            dao.insertExercise(e)
        } else {
            dao.updateExercise(e)
            if (e.name.isNotBlank()) {
                dao.updateExerciseNameInSets(e.id, e.name)
            }
            e.id
        }
    }

    suspend fun deleteExercise(e: ExerciseEntity) = dao.deleteExercise(e)

    suspend fun toggleFavorite(e: ExerciseEntity) = dao.updateExercise(e.copy(isFavorite = !e.isFavorite))

    /* ------------------------------- Antrenman ------------------------------ */

    /** Programdaki bir günden seans başlatır; dayId null ise boş seans açar. */
    suspend fun startWorkout(
        dayId: Long?,
        title: String,
        routineName: String = "",
        isDeload: Boolean = false,
        profile: com.example.core.LoadingProfile = com.example.core.LoadingProfile()
    ): Long {
        val now = System.currentTimeMillis()

        // Arka planda açık kalan eski seansları otomatik kapat veya temizle (yalnızca 1 aktif seans kalsın)
        val unfinished = dao.getUnfinishedWorkouts()
        for (uw in unfinished) {
            val existingSets = dao.setsForWorkout(uw.id)
            val completedCount = existingSets.count { it.isCompleted || it.reps > 0 || it.weightKg > 0f || it.durationSeconds > 0 }
            if (completedCount == 0) {
                dao.deleteSetsForWorkout(uw.id)
                dao.deletePrsForWorkout(uw.id)
                dao.deleteWorkout(uw)
            } else {
                val duration = maxOf(1, ((now - uw.startedAt) / 1000).toInt())
                finishWorkout(uw.id, duration, uw.notes, uw.feeling, uw.bodyWeightKg)
            }
        }

        val workoutId = dao.insertWorkout(
            WorkoutEntity(
                routineDayId = dayId, routineName = routineName,
                title = title, startedAt = now, isFinished = false,
                isDeload = isDeload
            )
        )
        if (dayId != null) {
            val items = dao.itemsForDay(dayId).sortedBy { it.orderIndex }
            val lastWorkoutForDay = dao.lastFinishedWorkoutForDay(dayId)
            val lastWorkoutSets = if (lastWorkoutForDay != null) dao.setsForWorkout(lastWorkoutForDay.id) else emptyList()

            val newSets = mutableListOf<WorkoutSetEntity>()
            items.forEachIndexed { order, item ->
                val ex = dao.exerciseById(item.exerciseId) ?: return@forEachIndexed
                // 1) Öncelikle bu günün en son tamamlanmış seansındaki bu hareketin setlerini al
                val daySetsForEx = lastWorkoutSets.filter { it.exerciseId == item.exerciseId }.sortedBy { it.setNumber }
                // 2) Eğer o en son seansta bu hareket yapılmamışsa, bu günün (dayId) daha önceki tamamlanmış seanslarına bak
                val olderDaySets = if (daySetsForEx.isEmpty()) {
                    val allDaySets = dao.lastSetsForExerciseInDay(dayId, item.exerciseId)
                    val wid = allDaySets.firstOrNull()?.workoutId
                    if (wid != null) allDaySets.filter { it.workoutId == wid }.sortedBy { it.setNumber } else emptyList()
                } else emptyList()

                // ASLA BAŞKA BİR GÜNDEN ÇEKME: Sadece ve sadece bu güne ait kayıtlar kullanılır!
                val cardLastSets = if (daySetsForEx.isNotEmpty()) daySetsForEx else olderDaySets
                val baseSets = if (cardLastSets.isNotEmpty()) maxOf(item.targetSets.coerceAtLeast(1), cardLastSets.size) else item.targetSets.coerceAtLeast(1)
                val numSets = if (isDeload) maxOf(1, (baseSets + 1) / 2) else baseSets
                val isDuration = (ex.trackingType == ExerciseEntity.TRACK_DURATION)

                // Progresyon reçetesi: bu günün geçmişine göre bugünkü ağırlık ve hedef tekrarlar.
                // Seans, geçen haftanın değerleriyle değil, reçeteyle açılır — tek dokunuşla kayıt.
                val rx = if (isDuration || item.isWarmup) null else {
                    val daySets = dao.lastSetsForExerciseInDay(dayId, item.exerciseId)
                    val sessions = daySets
                        .filter { !it.isWarmup && it.reps > 0 }
                        .groupBy { it.workoutId }
                        .values
                        .map { ws ->
                            com.example.core.SessionLog(
                                ws.first().performedAt,
                                ws.sortedBy { it.setNumber }.map { com.example.core.LoggedSet(it.weightKg, it.reps, it.rpe) }
                            )
                        }
                        .sortedByDescending { it.dateMillis }
                        .take(8)
                    com.example.core.ProgressionEngine.prescribe(
                        history = sessions,
                        targetSets = baseSets,
                        repMin = item.repMin,
                        repMax = item.repMax,
                        kind = com.example.core.loadKindOf(ex.equipment),
                        profile = profile,
                        deload = isDeload
                    ).takeIf { it.action != com.example.core.ProgressAction.FIRST }
                }
                var workingIndex = 0

                repeat(numSets) { i ->
                    val prefill = cardLastSets.getOrNull(i) ?: cardLastSets.lastOrNull()
                    val isWarmupSet = prefill?.isWarmup ?: item.isWarmup
                    val rxIndex = if (isWarmupSet) -1 else workingIndex++
                    val baseWeight = prefill?.weightKg?.takeIf { it > 0f } ?: item.targetWeight
                    val weight = if (rx != null && rxIndex >= 0 && rx.weight > 0f) rx.weight else baseWeight
                    val defaultReps = if (isDeload) 8 else (if (item.repMin > 0) item.repMin else 10)
                    val baseReps = prefill?.reps?.takeIf { it > 0 } ?: defaultReps
                    val reps = if (isDuration) 0 else if (rx != null && rxIndex >= 0) rx.repsFor(rxIndex) else baseReps
                    val exName = if (item.customName.isNotBlank()) item.customName else (prefill?.exerciseName ?: ex.name)
                    val isWarmup = isWarmupSet
                    val durationSecs = if (isDuration) {
                        prefill?.durationSeconds?.takeIf { it > 0 } ?: prefill?.reps?.takeIf { it > 0 } ?: if (item.repMin > 0) item.repMin else 30
                    } else 0

                    newSets.add(
                        WorkoutSetEntity(
                            workoutId = workoutId,
                            exerciseId = ex.id,
                            exerciseName = exName,
                            exerciseOrder = order,
                            setNumber = i + 1,
                            weightKg = weight,
                            reps = reps,
                            durationSeconds = durationSecs,
                            isWarmup = isWarmup,
                            isCompleted = false,
                            supersetGroup = item.supersetGroup
                        )
                    )
                }
            }
            dao.insertSets(newSets)
        }
        return workoutId
    }

    suspend fun toggleExerciseWarmup(workoutId: Long, exerciseOrder: Int, isWarmup: Boolean) {
        val workout = dao.workoutById(workoutId) ?: return
        val allSets = dao.setsForWorkout(workoutId)
        val grouped = allSets.groupBy { it.exerciseOrder }.toSortedMap()
        if (!grouped.containsKey(exerciseOrder)) return

        val cardOrders = grouped.keys.toList()
        val mutableOrders = cardOrders.toMutableList()
        val currentIdx = mutableOrders.indexOf(exerciseOrder)
        if (currentIdx < 0) return

        mutableOrders.removeAt(currentIdx)

        if (isWarmup) {
            val lastWarmupIdx = mutableOrders.indexOfLast { order ->
                grouped[order]?.any { it.isWarmup } == true
            }
            val insertPos = if (lastWarmupIdx >= 0) lastWarmupIdx + 1 else 0
            mutableOrders.add(insertPos, exerciseOrder)
        } else {
            mutableOrders.add(exerciseOrder)
        }

        mutableOrders.forEachIndexed { newOrderIndex, oldOrder ->
            val setsForCard = grouped[oldOrder] ?: emptyList()
            val updatedSets = setsForCard.map { s ->
                if (oldOrder == exerciseOrder) {
                    s.copy(exerciseOrder = newOrderIndex, isWarmup = isWarmup)
                } else {
                    s.copy(exerciseOrder = newOrderIndex)
                }
            }
            dao.insertSets(updatedSets)
        }
    }

    suspend fun renameSetsInWorkout(workoutId: Long, exerciseOrder: Int, newName: String) {
        val sets = dao.setsForWorkout(workoutId).filter { it.exerciseOrder == exerciseOrder }
        if (sets.isNotEmpty()) {
            val updated = sets.map { it.copy(exerciseName = newName) }
            dao.insertSets(updated)
        }
    }

    suspend fun addExerciseToWorkout(workoutId: Long, exerciseId: Long, sets: Int = 3) {
        val ex = dao.exerciseById(exerciseId) ?: return
        val workout = dao.workoutById(workoutId)
        val workoutDate = workout?.startedAt ?: System.currentTimeMillis()
        val isFinished = workout?.isFinished ?: false
        val current = dao.setsForWorkout(workoutId)
        val order = (current.maxOfOrNull { it.exerciseOrder } ?: -1) + 1
        val dayId = workout?.routineDayId

        val lastSets = if (dayId != null) {
            val allDaySets = dao.lastSetsForExerciseInDay(dayId, exerciseId)
            val wid = allDaySets.firstOrNull()?.workoutId
            if (wid != null) allDaySets.filter { it.workoutId == wid }.sortedBy { it.setNumber } else emptyList()
        } else {
            val last = dao.lastSetsForExercise(exerciseId)
            val lastWorkoutId = last.firstOrNull()?.workoutId
            if (lastWorkoutId != null) last.filter { it.workoutId == lastWorkoutId }.sortedBy { it.setNumber } else emptyList()
        }

        val dayItem = if (dayId != null) dao.itemsForDay(dayId).firstOrNull { it.exerciseId == exerciseId } else null
        val isDuration = ex.trackingType == ExerciseEntity.TRACK_DURATION
        val newSets = (1..sets).map { i ->
            val prefill = lastSets.getOrNull(i - 1) ?: lastSets.lastOrNull()
            WorkoutSetEntity(
                workoutId = workoutId, exerciseId = ex.id, exerciseName = ex.name,
                exerciseOrder = order, setNumber = i,
                weightKg = prefill?.weightKg?.takeIf { it > 0f } ?: dayItem?.targetWeight ?: 0f,
                reps = if (isDuration) 0 else (prefill?.reps?.takeIf { it > 0 } ?: dayItem?.repMin ?: 10),
                durationSeconds = if (isDuration) (prefill?.durationSeconds?.takeIf { it > 0 } ?: prefill?.reps?.takeIf { it > 0 } ?: dayItem?.repMin ?: 30) else 0,
                isCompleted = isFinished,
                performedAt = workoutDate
            )
        }
        dao.insertSets(newSets)
        if (isFinished) {
            recalculatePrsForWorkout(workoutId)
        }
    }

    suspend fun setSessionExerciseSuperset(workoutId: Long, exerciseOrder: Int, group: Int) {
        val allSets = dao.setsForWorkout(workoutId)
        val targetSets = allSets.filter { it.exerciseOrder == exerciseOrder }
        if (targetSets.isNotEmpty()) {
            val updated = targetSets.map { it.copy(supersetGroup = maxOf(0, group)) }
            dao.insertSets(updated)
        }
    }

    suspend fun combineSessionExercises(workoutId: Long, firstOrder: Int, secondOrder: Int) {
        val allSets = dao.setsForWorkout(workoutId)
        val firstSets = allSets.filter { it.exerciseOrder == firstOrder }
        val secondSets = allSets.filter { it.exerciseOrder == secondOrder }
        if (firstSets.isEmpty() || secondSets.isEmpty() || firstOrder == secondOrder) return

        val targetGroup = when {
            firstSets.first().supersetGroup > 0 -> firstSets.first().supersetGroup
            secondSets.first().supersetGroup > 0 -> secondSets.first().supersetGroup
            else -> (allSets.maxOfOrNull { it.supersetGroup } ?: 0) + 1
        }

        val updated = (firstSets + secondSets).map { it.copy(supersetGroup = targetGroup) }
        dao.insertSets(updated)
    }

    suspend fun dissolveSessionSuperset(workoutId: Long, group: Int) {
        if (group <= 0) return
        val allSets = dao.setsForWorkout(workoutId)
        val targetSets = allSets.filter { it.supersetGroup == group }
        if (targetSets.isNotEmpty()) {
            val updated = targetSets.map { it.copy(supersetGroup = 0) }
            dao.insertSets(updated)
        }
    }

    suspend fun addSupersetToWorkout(workoutId: Long, exerciseIds: List<Long>, sets: Int = 3) {
        if (exerciseIds.isEmpty()) return
        val workout = dao.workoutById(workoutId)
        val workoutDate = workout?.startedAt ?: System.currentTimeMillis()
        val isFinished = workout?.isFinished ?: false
        val currentSets = dao.setsForWorkout(workoutId)
        val targetGroup = (currentSets.maxOfOrNull { it.supersetGroup } ?: 0) + 1
        var nextOrder = (currentSets.maxOfOrNull { it.exerciseOrder } ?: -1) + 1

        val dayId = workout?.routineDayId
        val dayItems = if (dayId != null) dao.itemsForDay(dayId).associateBy { it.exerciseId } else emptyMap()

        val newSets = mutableListOf<WorkoutSetEntity>()
        exerciseIds.forEach { exId ->
            val ex = dao.exerciseById(exId) ?: return@forEach
            val lastSets = if (dayId != null) {
                val allDaySets = dao.lastSetsForExerciseInDay(dayId, exId)
                val wid = allDaySets.firstOrNull()?.workoutId
                if (wid != null) allDaySets.filter { it.workoutId == wid }.sortedBy { it.setNumber } else emptyList()
            } else {
                val last = dao.lastSetsForExercise(exId)
                val lastWorkoutId = last.firstOrNull()?.workoutId
                if (lastWorkoutId != null) last.filter { it.workoutId == lastWorkoutId }.sortedBy { it.setNumber } else emptyList()
            }
            val dayItem = dayItems[exId]
            val isDuration = ex.trackingType == ExerciseEntity.TRACK_DURATION
            val order = nextOrder++

            (1..sets).forEach { i ->
                val prefill = lastSets.getOrNull(i - 1) ?: lastSets.lastOrNull()
                newSets.add(
                    WorkoutSetEntity(
                        workoutId = workoutId,
                        exerciseId = ex.id,
                        exerciseName = ex.name,
                        exerciseOrder = order,
                        setNumber = i,
                        weightKg = prefill?.weightKg?.takeIf { it > 0f } ?: dayItem?.targetWeight ?: 0f,
                        reps = if (isDuration) 0 else (prefill?.reps?.takeIf { it > 0 } ?: dayItem?.repMin ?: 10),
                        durationSeconds = if (isDuration) (prefill?.durationSeconds?.takeIf { it > 0 } ?: prefill?.reps?.takeIf { it > 0 } ?: dayItem?.repMin ?: 30) else 0,
                        isCompleted = isFinished,
                        supersetGroup = targetGroup,
                        performedAt = workoutDate
                    )
                )
            }
        }
        if (newSets.isNotEmpty()) {
            dao.insertSets(newSets)
            if (isFinished) {
                recalculatePrsForWorkout(workoutId)
            }
        }
    }

    suspend fun addSetRow(workoutId: Long, exerciseOrder: Int, exerciseId: Long) {
        val ex = dao.exerciseById(exerciseId)
        val workout = dao.workoutById(workoutId)
        val workoutDate = workout?.startedAt ?: System.currentTimeMillis()
        val isFinished = workout?.isFinished ?: false
        val sets = dao.setsForWorkout(workoutId).filter { it.exerciseOrder == exerciseOrder }.sortedBy { it.setNumber }
        val last = sets.lastOrNull()
        val nextNum = (sets.maxOfOrNull { it.setNumber } ?: 0) + 1
        val exName = last?.exerciseName ?: ex?.name ?: "Hareket"
        val isWarmup = last?.isWarmup ?: false
        val weight = last?.weightKg ?: 0f
        val isDuration = ex?.trackingType == ExerciseEntity.TRACK_DURATION
        val reps = if (isDuration) 0 else (last?.reps ?: 10)
        val durSecs = if (isDuration) (last?.durationSeconds?.takeIf { it > 0 } ?: last?.reps?.takeIf { it > 0 } ?: 30) else 0

        dao.insertSet(
            WorkoutSetEntity(
                workoutId = workoutId,
                exerciseId = exerciseId,
                exerciseName = exName,
                exerciseOrder = exerciseOrder,
                setNumber = nextNum,
                weightKg = weight,
                reps = reps,
                durationSeconds = durSecs,
                isWarmup = isWarmup,
                isCompleted = isFinished,
                performedAt = workoutDate
            )
        )
        if (isFinished) {
            recalculatePrsForWorkout(workoutId)
        }
    }

    suspend fun updateSetExplicit(s: WorkoutSetEntity, isCompleted: Boolean) {
        val workout = dao.workoutById(s.workoutId)
        val workoutDate = workout?.startedAt ?: s.performedAt
        val updated = s.copy(
            isCompleted = isCompleted,
            performedAt = workoutDate
        )
        dao.updateSet(updated)
        if (workout?.isFinished == true) {
            recalculatePrsForWorkout(s.workoutId)
        }
    }

    suspend fun updateSet(s: WorkoutSetEntity) {
        val workout = dao.workoutById(s.workoutId)
        val workoutDate = workout?.startedAt ?: s.performedAt
        val isFinished = workout?.isFinished ?: false
        val updated = s.copy(
            isCompleted = if (isFinished) true else s.isCompleted,
            performedAt = workoutDate
        )
        dao.updateSet(updated)
        if (isFinished) {
            recalculatePrsForWorkout(s.workoutId)
        }
    }

    suspend fun deleteSet(s: WorkoutSetEntity) {
        dao.deleteSet(s)
        val workout = dao.workoutById(s.workoutId)
        val remaining = dao.setsForWorkout(s.workoutId).filter { it.exerciseOrder == s.exerciseOrder }.sortedBy { it.setNumber }
        remaining.forEachIndexed { i, item ->
            if (item.setNumber != i + 1) dao.updateSet(item.copy(setNumber = i + 1))
        }
        if (workout?.isFinished == true) {
            recalculatePrsForWorkout(s.workoutId)
        }
    }

    suspend fun removeExerciseFromWorkout(workoutId: Long, exerciseOrder: Int) {
        val sets = dao.setsForWorkout(workoutId).filter { it.exerciseOrder == exerciseOrder }
        sets.forEach { dao.deleteSet(it) }
        val workout = dao.workoutById(workoutId)
        if (workout?.isFinished == true) {
            recalculatePrsForWorkout(workoutId)
        }
    }

    suspend fun removeExerciseFromWorkoutByExerciseId(workoutId: Long, exerciseId: Long) {
        dao.deleteExerciseFromWorkout(workoutId, exerciseId)
        val workout = dao.workoutById(workoutId)
        if (workout?.isFinished == true) {
            recalculatePrsForWorkout(workoutId)
        }
    }

    suspend fun previousSetsFor(exerciseId: Long): List<WorkoutSetEntity> {
        val last = dao.lastSetsForExercise(exerciseId)
        val lastWorkoutId = last.firstOrNull()?.workoutId ?: return emptyList()
        return last.filter { it.workoutId == lastWorkoutId }.sortedBy { it.setNumber }
    }

    suspend fun discardWorkout(workoutId: Long) {
        dao.deleteSetsForWorkout(workoutId)
        dao.deletePrsForWorkout(workoutId)
        dao.workoutById(workoutId)?.let { dao.deleteWorkout(it) }
    }

    /**
     * Bir seans için rekorları hesaplar ve kaydeder.
     */
    suspend fun recalculatePrsForWorkout(workoutId: Long): List<PrEntity> {
        val w = dao.workoutById(workoutId) ?: return emptyList()
        if (!w.isFinished) return emptyList()
        val workoutDate = w.startedAt
        dao.deletePrsForWorkout(workoutId)

        val sets = dao.setsForWorkout(workoutId)
        val newPrs = mutableListOf<PrEntity>()
        sets.filter { !it.isWarmup && it.isCompleted && it.weightKg > 0f && it.reps > 0 }
            .groupBy { it.exerciseId }
            .forEach { (exerciseId, exSets) ->
                val history = dao.finishedSetsForExercise(exerciseId)
                    .filter { it.workoutId != workoutId && !it.isWarmup && it.isCompleted && it.performedAt < workoutDate }

                val bestWeight = exSets.maxOf { it.weightKg }
                val prevWeight = history.maxOfOrNull { it.weightKg } ?: 0f

                val bestE1rm = exSets.maxOf { Calc.e1rm(it.weightKg, it.reps) }
                val prevE1rm = history.maxOfOrNull { Calc.e1rm(it.weightKg, it.reps) } ?: 0f

                val sessionVolume = exSets.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat()
                val prevVolume = history.groupBy { it.workoutId }
                    .map { (_, l) -> l.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat() }
                    .maxOrNull() ?: 0f

                val name = exSets.first().exerciseName

                if (bestWeight > prevWeight + 0.01f) {
                    val s = exSets.first { it.weightKg == bestWeight }
                    newPrs.add(
                        PrEntity(
                            exerciseId = exerciseId, exerciseName = name, type = PrEntity.TYPE_WEIGHT,
                            value = bestWeight, weightKg = bestWeight, reps = s.reps, dateMillis = workoutDate, workoutId = workoutId
                        )
                    )
                }
                if (bestE1rm > prevE1rm + 0.01f) {
                    val s = exSets.maxByOrNull { Calc.e1rm(it.weightKg, it.reps) }!!
                    newPrs.add(
                        PrEntity(
                            exerciseId = exerciseId, exerciseName = name, type = PrEntity.TYPE_E1RM,
                            value = bestE1rm, weightKg = s.weightKg, reps = s.reps, dateMillis = workoutDate, workoutId = workoutId
                        )
                    )
                }
                if (sessionVolume > prevVolume + 0.01f && history.isNotEmpty()) {
                    newPrs.add(
                        PrEntity(
                            exerciseId = exerciseId, exerciseName = name, type = PrEntity.TYPE_VOLUME,
                            value = sessionVolume, dateMillis = workoutDate, workoutId = workoutId
                        )
                    )
                }
            }
        newPrs.forEach { dao.insertPr(it) }
        return newPrs
    }

    /**
     * Seansı bitirir, tamamlanmamış setleri siler ve kırılan rekorları hesaplar.
     * @return kırılan rekorların listesi
     */
    suspend fun finishWorkout(
        workoutId: Long,
        durationSeconds: Int,
        notes: String,
        feeling: Int,
        bodyWeightKg: Float
    ): List<PrEntity> {
        val workout = dao.workoutById(workoutId) ?: return emptyList()
        val rawSets = dao.setsForWorkout(workoutId)
        val validRawSets = rawSets.filter { it.isCompleted || it.reps > 0 || it.weightKg > 0f || it.durationSeconds > 0 }
        if (validRawSets.isEmpty()) {
            dao.deleteSetsForWorkout(workoutId)
            dao.deletePrsForWorkout(workoutId)
            dao.deleteWorkout(workout)
            return emptyList()
        }
        // Temizleme: Veri girilmemiş ve tamamlanmamış setleri DB'den sil
        val unperformedSets = rawSets.filter { !validRawSets.contains(it) }
        unperformedSets.forEach { dao.deleteSet(it) }

        val now = System.currentTimeMillis()
        val sets = validRawSets.map {
            it.copy(
                isCompleted = true,
                performedAt = if (it.performedAt > 0L) it.performedAt else now
            )
        }
        dao.insertSets(sets)

        dao.updateWorkout(
            workout.copy(
                finishedAt = now,
                durationSeconds = durationSeconds,
                notes = notes,
                feeling = feeling,
                bodyWeightKg = bodyWeightKg,
                isFinished = true
            )
        )

        if (workout.routineDayId != null) {
            syncRoutineDayWithFinishedWorkout(workout.routineDayId, workoutId)
        }

        return recalculatePrsForWorkout(workoutId)
    }

    suspend fun deleteWorkout(w: WorkoutEntity) {
        dao.deleteSetsForWorkout(w.id)
        dao.deletePrsForWorkout(w.id)
        dao.deleteWorkout(w)
    }

    suspend fun clearAllWorkoutHistory() {
        dao.deleteAllSets()
        dao.deleteAllPrs()
        dao.deleteAllWorkouts()
    }

    suspend fun updateWorkout(w: WorkoutEntity) {
        dao.updateWorkout(w)
        recalculatePrsForWorkout(w.id)
    }

    suspend fun updateWorkoutDate(workoutId: Long, newStartedAt: Long) {
        val w = dao.workoutById(workoutId) ?: return
        val newFinishedAt = if (w.finishedAt != null && w.finishedAt > w.startedAt) {
            val diff = w.finishedAt - w.startedAt
            newStartedAt + diff
        } else if (w.durationSeconds > 0) {
            newStartedAt + (w.durationSeconds * 1000L)
        } else null

        val updatedWorkout = w.copy(startedAt = newStartedAt, finishedAt = newFinishedAt)
        dao.updateWorkout(updatedWorkout)
        dao.updateSetTimestampsForWorkout(workoutId, newStartedAt)
        dao.updatePrTimestampsForWorkout(workoutId, newStartedAt)
        recalculatePrsForWorkout(workoutId)
    }

    suspend fun createPastWorkout(
        dayId: Long?,
        title: String,
        routineName: String,
        dateMillis: Long,
        durationSeconds: Int = 1800
    ): Long {
        val finishedAt = dateMillis + (durationSeconds * 1000L)
        val workoutId = dao.insertWorkout(
            WorkoutEntity(
                routineDayId = dayId,
                routineName = routineName,
                title = title,
                startedAt = dateMillis,
                finishedAt = finishedAt,
                durationSeconds = durationSeconds,
                isFinished = true
            )
        )
        if (dayId != null) {
            val items = dao.itemsForDay(dayId).sortedBy { it.orderIndex }
            val newSets = mutableListOf<WorkoutSetEntity>()
            items.forEachIndexed { order, item ->
                val ex = dao.exerciseById(item.exerciseId) ?: return@forEachIndexed
                val allDaySets = dao.lastSetsForExerciseInDay(dayId, item.exerciseId)
                val lastWorkoutId = allDaySets.firstOrNull()?.workoutId
                val lastSets = if (lastWorkoutId != null) allDaySets.filter { it.workoutId == lastWorkoutId }.sortedBy { it.setNumber } else emptyList()
                val numSets = if (lastSets.isNotEmpty()) maxOf(lastSets.size, item.targetSets.coerceAtLeast(1)) else item.targetSets.coerceAtLeast(1)

                repeat(numSets) { i ->
                    val prefill = lastSets.getOrNull(i) ?: lastSets.lastOrNull()
                    val weight = prefill?.weightKg?.takeIf { it > 0f } ?: item.targetWeight
                    val reps = prefill?.reps?.takeIf { it > 0 } ?: if (item.repMin > 0) item.repMin else 10
                    val isWarmup = prefill?.isWarmup ?: item.isWarmup
                    newSets.add(
                        WorkoutSetEntity(
                            workoutId = workoutId,
                            exerciseId = ex.id,
                            exerciseName = ex.name,
                            exerciseOrder = order,
                            setNumber = i + 1,
                            weightKg = weight,
                            reps = reps,
                            durationSeconds = if (ex.trackingType == ExerciseEntity.TRACK_DURATION) (prefill?.durationSeconds ?: item.repMin) else 0,
                            isWarmup = isWarmup,
                            isCompleted = true,
                            performedAt = dateMillis
                        )
                    )
                }
            }
            dao.insertSets(newSets)
            recalculatePrsForWorkout(workoutId)
        }
        return workoutId
    }

    suspend fun syncRoutineDayWithFinishedWorkout(dayId: Long, workoutId: Long) {
        val day = dao.dayById(dayId) ?: return
        val workout = dao.workoutById(workoutId) ?: return
        val sets = dao.setsForWorkout(workoutId).filter { it.isCompleted || it.reps > 0 || it.weightKg > 0f || it.durationSeconds > 0 }
        if (sets.isEmpty()) return

        val existingItems = dao.itemsForDay(dayId).associateBy { it.exerciseId }
        dao.deleteItemsForDay(dayId)

        val groupedSets = sets.groupBy { it.exerciseOrder }.toSortedMap()
        var orderIdx = 0
        groupedSets.forEach { (_, setList) ->
            if (setList.isEmpty()) return@forEach
            val firstSet = setList.first()
            val exId = if (firstSet.exerciseId > 0) firstSet.exerciseId else ensureExercise(firstSet.exerciseName)
            val existing = existingItems[exId]

            val targetSets = setList.size
            val isWarmup = setList.all { it.isWarmup } || firstSet.isWarmup
            val completedSets = setList.filter { it.isCompleted || it.weightKg > 0f }
            val weights = (if (completedSets.isNotEmpty()) completedSets else setList).map { it.weightKg }.filter { it > 0f }
            val targetWeight = weights.lastOrNull() ?: weights.maxOrNull() ?: existing?.targetWeight ?: 0f
            val reps = (if (completedSets.isNotEmpty()) completedSets else setList).map { it.reps }.filter { it > 0 }
            val minReps = reps.minOrNull() ?: existing?.repMin ?: 8
            val maxReps = reps.maxOrNull() ?: existing?.repMax ?: 12
            val durSecs = setList.map { it.durationSeconds }.maxOrNull() ?: existing?.repMin ?: 0
            val sGroup = firstSet.supersetGroup
            val customName = if (firstSet.exerciseName.isNotBlank() && firstSet.exerciseName != (dao.exerciseById(exId)?.name ?: "")) {
                firstSet.exerciseName
            } else {
                existing?.customName ?: ""
            }

            dao.insertItem(
                RoutineItemEntity(
                    dayId = dayId,
                    exerciseId = exId,
                    orderIndex = orderIdx++,
                    targetSets = targetSets,
                    repMin = if (durSecs > 0) durSecs else minReps,
                    repMax = if (durSecs > 0) durSecs else maxReps,
                    targetWeight = targetWeight,
                    restSeconds = existing?.restSeconds ?: 90,
                    isWarmup = isWarmup,
                    supersetGroup = sGroup,
                    customName = customName,
                    note = existing?.note ?: ""
                )
            )
        }
    }

    suspend fun replaceRoutineDayWithWorkout(dayId: Long, workoutId: Long) {
        val day = dao.dayById(dayId) ?: return
        val workout = dao.workoutById(workoutId) ?: return
        if (workout.title.isNotBlank()) {
            dao.updateDay(day.copy(focus = workout.title))
        }
        syncRoutineDayWithFinishedWorkout(dayId, workoutId)
    }

    suspend fun duplicateWorkout(workoutId: Long): Long {
        val original = dao.workoutById(workoutId) ?: return 0L
        val originalSets = dao.setsForWorkout(workoutId)
        val now = System.currentTimeMillis()
        val newWorkoutId = dao.insertWorkout(
            WorkoutEntity(
                routineDayId = original.routineDayId,
                routineName = original.routineName,
                title = original.title,
                startedAt = now,
                finishedAt = now + (original.durationSeconds * 1000L),
                durationSeconds = original.durationSeconds,
                isFinished = true,
                notes = original.notes,
                feeling = original.feeling,
                bodyWeightKg = original.bodyWeightKg
            )
        )
        val newSets = originalSets.map { s ->
            WorkoutSetEntity(
                workoutId = newWorkoutId,
                exerciseId = s.exerciseId,
                exerciseName = s.exerciseName,
                exerciseOrder = s.exerciseOrder,
                setNumber = s.setNumber,
                weightKg = s.weightKg,
                reps = s.reps,
                rpe = s.rpe,
                durationSeconds = s.durationSeconds,
                isWarmup = s.isWarmup,
                isCompleted = true,
                performedAt = now
            )
        }
        dao.insertSets(newSets)
        recalculatePrsForWorkout(newWorkoutId)
        return newWorkoutId
    }

    suspend fun addManualPr(exerciseId: Long, name: String, weight: Float, reps: Int, note: String) {
        dao.insertPr(
            PrEntity(
                exerciseId = exerciseId, exerciseName = name, type = PrEntity.TYPE_WEIGHT,
                value = weight, weightKg = weight, reps = reps, isManual = true
            )
        )
    }

    suspend fun deletePr(pr: PrEntity) = dao.deletePr(pr)

    /* ------------------------- Vücut ölçümü & notlar ------------------------ */

    suspend fun saveBodyMetric(m: BodyMetricEntity) =
        if (m.id == 0L) { dao.insertBodyMetric(m); Unit } else dao.updateBodyMetric(m)

    suspend fun deleteBodyMetric(m: BodyMetricEntity) = dao.deleteBodyMetric(m)

    suspend fun saveNote(n: NoteEntity) =
        if (n.id == 0L) { dao.insertNote(n); Unit } else dao.updateNote(n)

    suspend fun deleteNote(n: NoteEntity) = dao.deleteNote(n)

    /* -------------------------------- Deload --------------------------------- */

    suspend fun setWorkoutDeload(workoutId: Long, isDeload: Boolean) {
        dao.setWorkoutDeload(workoutId, isDeload)
    }

    /**
     * Aktif rutindeki günlerin hareketlerini deload protokolüne göre ayarlar:
     * - Ağırlıklar: Korunur (reduceWeightsPercent = 0f) veya Max %10 düşürülür.
     * - Set sayıları: Yarıya indirilir (örn: 4 -> 2, 3 -> 2, 5 -> 3).
     * - Tekrarlar: 8-10 aralığına çekilir.
     * Orijinal programı JSON olarak döndürür, böylece deload bitince aynen geri yüklenebilir.
     */
    suspend fun applyDeloadToRoutine(
        routineId: Long,
        reduceWeightsPercent: Float = 0f,
        customAdjustments: Map<Long, Pair<Int, Float>>? = null
    ): String {
        val days = dao.daysForRoutine(routineId)
        val allItems = mutableListOf<RoutineItemEntity>()
        for (d in days) {
            allItems.addAll(dao.itemsForDay(d.id))
        }
        if (allItems.isEmpty()) return ""

        // 1. Orijinal programı yedekle
        val backupArr = org.json.JSONArray()
        allItems.forEach { itm ->
            val obj = org.json.JSONObject().apply {
                put("id", itm.id)
                put("dayId", itm.dayId)
                put("exerciseId", itm.exerciseId)
                put("orderIndex", itm.orderIndex)
                put("targetSets", itm.targetSets)
                put("repMin", itm.repMin)
                put("repMax", itm.repMax)
                put("targetWeight", itm.targetWeight.toDouble())
                put("restSeconds", itm.restSeconds)
                put("isWarmup", itm.isWarmup)
                put("supersetGroup", itm.supersetGroup)
                put("note", itm.note)
                put("customName", itm.customName)
            }
            backupArr.put(obj)
        }
        val backupJson = backupArr.toString()

        // 2. Deload değerlerini uygula
        allItems.forEach { itm ->
            val custom = customAdjustments?.get(itm.id)
            val newSets = custom?.first ?: maxOf(1, (itm.targetSets + 1) / 2)
            val baseWeight = custom?.second ?: itm.targetWeight
            val newWeight = if (reduceWeightsPercent > 0f) {
                (baseWeight * (1f - reduceWeightsPercent).coerceIn(0.85f, 1f))
            } else {
                baseWeight
            }
            val roundedWeight = kotlin.math.round(newWeight * 2f) / 2f // 0.5 kg hassasiyet

            dao.updateItem(
                itm.copy(
                    targetSets = newSets,
                    targetWeight = roundedWeight,
                    repMin = 8,
                    repMax = 10
                )
            )
        }

        return backupJson
    }

    /** Deload öncesi yedeklenen orijinal program değerlerini geri yükler. */
    suspend fun restorePreDeloadRoutine(backupJson: String): Boolean {
        if (backupJson.isBlank()) return false
        return try {
            val arr = org.json.JSONArray(backupJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val itemId = obj.getLong("id")
                val existing = dao.itemById(itemId)
                if (existing != null) {
                    dao.updateItem(
                        existing.copy(
                            targetSets = obj.getInt("targetSets"),
                            targetWeight = obj.getDouble("targetWeight").toFloat(),
                            repMin = obj.getInt("repMin"),
                            repMax = obj.getInt("repMax")
                        )
                    )
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /* ----------------------------- İçe Aktarma ------------------------------ */

    suspend fun importBackupJson(jsonStr: String): ImportResult {
        return Backup.importBackupJson(jsonStr, dao)
    }

    suspend fun importLegacyDatabase(): Boolean {
        return LegacyImporter(context, dao).importIfAvailable()
    }
}
