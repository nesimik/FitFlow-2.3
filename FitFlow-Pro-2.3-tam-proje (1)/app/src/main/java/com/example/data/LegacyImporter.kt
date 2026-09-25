package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.core.Muscles

/**
 * Eski FitFlow (workout_database) verisini yeni şemaya taşır.
 *
 * Eski sürümde ağırlık/tekrar serbest metindi ("8 kg / el", "30 sn", "11").
 * Burada metinden ilk sayı çekilerek sayısal alanlara dönüştürülür; tam
 * doğruluk garanti edilemez ama geçmiş kaybolmaz. İşlem tamamen hatasız
 * çalışmazsa sessizce atlanır ve varsayılan program kurulur.
 */
class LegacyImporter(private val context: Context, private val dao: FitDao) {

    private val legacyDbName = "workout_database"

    suspend fun importIfAvailable(): Boolean {
        val file = context.getDatabasePath(legacyDbName)
        if (!file.exists()) return false
        var db: SQLiteDatabase? = null
        return try {
            db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val routineId = dao.insertRoutine(
                RoutineEntity(
                    name = "FitFlow (aktarılan program)",
                    description = "Önceki sürümden otomatik aktarıldı.",
                    isActive = true
                )
            )
            importDays(db, routineId)
            importSessions(db)
            importPrs(db)
            true
        } catch (t: Throwable) {
            false
        } finally {
            try { db?.close() } catch (_: Throwable) {}
        }
    }

    private suspend fun importDays(db: SQLiteDatabase, routineId: Long) {
        val dayIdMap = HashMap<Int, Long>()
        db.rawQuery("SELECT id, name, description FROM workout_days ORDER BY id ASC", null).use { c ->
            var order = 0
            while (c.moveToNext()) {
                val oldId = c.getInt(0)
                val name = c.getString(1) ?: "Gün"
                val desc = c.getString(2) ?: ""
                val newId = dao.insertDay(
                    RoutineDayEntity(
                        routineId = routineId, name = name, focus = desc,
                        weekday = weekdayFromName(name), orderIndex = order++
                    )
                )
                dayIdMap[oldId] = newId
            }
        }

        db.rawQuery(
            "SELECT dayId, name, sets, reps, weight, restSeconds, isWarmUp, youtubeUrl, orderIndex FROM exercises ORDER BY dayId ASC, isWarmUp DESC, orderIndex ASC",
            null
        ).use { c ->
            val counters = HashMap<Long, Int>()
            while (c.moveToNext()) {
                val dayId = dayIdMap[c.getInt(0)] ?: continue
                val name = c.getString(1) ?: continue
                val sets = c.getInt(2).coerceAtLeast(1)
                val repsStr = c.getString(3) ?: ""
                val weightStr = c.getString(4) ?: ""
                val rest = c.getInt(5)
                val warm = c.getInt(6) == 1
                val url = c.getString(7) ?: ""
                val reps = firstInt(repsStr) ?: 10
                val weight = firstFloat(weightStr) ?: 0f
                val track = if (repsStr.contains("sn") || repsStr.contains("dk")) {
                    ExerciseEntity.TRACK_DURATION
                } else if (weight <= 0f) ExerciseEntity.TRACK_REPS else ExerciseEntity.TRACK_WEIGHT_REPS

                val exId = findOrCreateExercise(name, url, track)
                val order = counters.getOrDefault(dayId, 0)
                counters[dayId] = order + 1
                dao.insertItem(
                    RoutineItemEntity(
                        dayId = dayId, exerciseId = exId, orderIndex = order,
                        targetSets = sets, repMin = reps, repMax = reps + 2,
                        targetWeight = weight, restSeconds = if (rest > 0) rest else 90,
                        isWarmup = warm
                    )
                )
            }
        }
    }

    private suspend fun importSessions(db: SQLiteDatabase) {
        db.rawQuery(
            "SELECT id, dayName, dateMillis, durationSeconds, notes, exercisesJson FROM workout_sessions ORDER BY dateMillis ASC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val dayName = c.getString(1) ?: "Antrenman"
                val date = c.getLong(2)
                val duration = c.getInt(3)
                val notes = c.getString(4) ?: ""
                val json = c.getString(5) ?: ""

                val workoutId = dao.insertWorkout(
                    WorkoutEntity(
                        title = dayName, startedAt = date,
                        finishedAt = date + duration * 1000L,
                        durationSeconds = duration, notes = notes,
                        routineName = "Aktarılan", isFinished = true
                    )
                )
                val newSets = mutableListOf<WorkoutSetEntity>()
                json.split("###").filter { it.isNotBlank() }.forEachIndexed { order, raw ->
                    val p = raw.split("@@@")
                    if (p.size < 4) return@forEachIndexed
                    val name = p[0]
                    val setCount = p.getOrNull(1)?.toIntOrNull() ?: 1
                    val reps = firstInt(p.getOrNull(2) ?: "") ?: 0
                    val weight = firstFloat(p.getOrNull(3) ?: "") ?: 0f
                    val rpe = (p.getOrNull(5) ?: "").let { firstFloat(it) } ?: 0f
                    val exId = findOrCreateExercise(name, "", if (weight > 0f) ExerciseEntity.TRACK_WEIGHT_REPS else ExerciseEntity.TRACK_REPS)
                    for (i in 1..setCount.coerceIn(1, 20)) {
                        newSets.add(
                            WorkoutSetEntity(
                                workoutId = workoutId, exerciseId = exId, exerciseName = name,
                                exerciseOrder = order, setNumber = i,
                                weightKg = weight, reps = reps, rpe = rpe,
                                isCompleted = true, performedAt = date
                            )
                        )
                    }
                }
                if (newSets.isNotEmpty()) dao.insertSets(newSets)
            }
        }
    }

    private suspend fun importPrs(db: SQLiteDatabase) {
        db.rawQuery("SELECT exerciseName, maxWeight, dateMillis, notes FROM personal_records", null).use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val weight = firstFloat(c.getString(1) ?: "") ?: continue
                val date = c.getLong(2)
                val exId = findOrCreateExercise(name, "", ExerciseEntity.TRACK_WEIGHT_REPS)
                dao.insertPr(
                    PrEntity(
                        exerciseId = exId, exerciseName = name, type = PrEntity.TYPE_WEIGHT,
                        value = weight, weightKg = weight, dateMillis = date, isManual = true
                    )
                )
            }
        }
    }

    private suspend fun findOrCreateExercise(name: String, url: String, track: String): Long {
        val clean = name.trim()
        dao.exerciseByName(clean)?.let { return it.id }
        return dao.insertExercise(
            ExerciseEntity(
                name = clean,
                muscleGroup = Muscles.guess(clean),
                trackingType = track,
                videoUrl = url,
                isCustom = true
            )
        )
    }

    private fun firstFloat(s: String): Float? =
        Regex("([0-9]+([.,][0-9]+)?)").find(s)?.value?.replace(',', '.')?.toFloatOrNull()

    private fun firstInt(s: String): Int? =
        Regex("([0-9]+)").find(s)?.value?.toIntOrNull()

    private fun weekdayFromName(name: String): Int {
        val n = name.lowercase(com.example.core.TR)
        return when {
            n.contains("pazartesi") -> 1
            n.contains("salı") -> 2
            n.contains("çarşamba") -> 3
            n.contains("perşembe") -> 4
            n.contains("cuma") && !n.contains("cumartesi") -> 5
            n.contains("cumartesi") -> 6
            n.contains("pazar") -> 7
            else -> 0
        }
    }
}
