package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.BodyMetricEntity
import com.example.data.ExerciseEntity
import com.example.data.FitDao
import com.example.data.NoteEntity
import com.example.data.PrEntity
import com.example.data.RoutineDayEntity
import com.example.data.RoutineEntity
import com.example.data.RoutineItemEntity
import com.example.data.WorkoutEntity
import com.example.data.WorkoutSetEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ImportResult(
    val success: Boolean,
    val message: String,
    val workoutsImported: Int = 0,
    val exercisesImported: Int = 0,
    val routinesImported: Int = 0
)

/**
 * Tüm veriyi tek bir JSON metnine çevirir ve geri yükler.
 * FitFlow Pro ve FitFlow Pro2 yedeklerinin tümünü destekler.
 */
object Backup {

    const val VERSION = 1

    fun export(
        exercises: List<ExerciseEntity>,
        routines: List<RoutineEntity>,
        days: List<RoutineDayEntity>,
        items: List<RoutineItemEntity>,
        workouts: List<WorkoutEntity>,
        sets: List<WorkoutSetEntity>,
        prs: List<PrEntity>,
        body: List<BodyMetricEntity>,
        notes: List<NoteEntity>
    ): String {
        val root = JSONObject()
        root.put("format", "fitflow-pro-backup")
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        root.put("exercises", JSONArray().also { arr ->
            exercises.forEach { e ->
                arr.put(JSONObject().apply {
                    put("id", e.id); put("name", e.name); put("muscleGroup", e.muscleGroup)
                    put("secondaryMuscles", e.secondaryMuscles); put("equipment", e.equipment)
                    put("trackingType", e.trackingType); put("instructions", e.instructions)
                    put("tips", e.tips); put("videoUrl", e.videoUrl)
                    put("isCustom", e.isCustom); put("isFavorite", e.isFavorite)
                    put("defaultRestSeconds", e.defaultRestSeconds)
                })
            }
        })

        root.put("routines", JSONArray().also { arr ->
            routines.forEach { r ->
                arr.put(JSONObject().apply {
                    put("id", r.id); put("name", r.name); put("description", r.description)
                    put("colorHex", r.colorHex); put("isActive", r.isActive)
                    put("orderIndex", r.orderIndex); put("createdAt", r.createdAt)
                })
            }
        })

        root.put("days", JSONArray().also { arr ->
            days.forEach { d ->
                arr.put(JSONObject().apply {
                    put("id", d.id); put("routineId", d.routineId); put("name", d.name)
                    put("focus", d.focus); put("weekday", d.weekday); put("orderIndex", d.orderIndex)
                })
            }
        })

        root.put("items", JSONArray().also { arr ->
            items.forEach { i ->
                arr.put(JSONObject().apply {
                    put("id", i.id); put("dayId", i.dayId); put("exerciseId", i.exerciseId)
                    put("orderIndex", i.orderIndex); put("targetSets", i.targetSets)
                    put("repMin", i.repMin); put("repMax", i.repMax)
                    put("targetWeight", i.targetWeight.toDouble()); put("restSeconds", i.restSeconds)
                    put("isWarmup", i.isWarmup); put("supersetGroup", i.supersetGroup); put("note", i.note)
                })
            }
        })

        root.put("workouts", JSONArray().also { arr ->
            workouts.forEach { w ->
                arr.put(JSONObject().apply {
                    put("id", w.id); put("routineDayId", w.routineDayId ?: JSONObject.NULL)
                    put("routineName", w.routineName); put("title", w.title)
                    put("startedAt", w.startedAt); put("finishedAt", w.finishedAt ?: JSONObject.NULL)
                    put("durationSeconds", w.durationSeconds); put("notes", w.notes)
                    put("feeling", w.feeling); put("bodyWeightKg", w.bodyWeightKg.toDouble())
                    put("isFinished", w.isFinished); put("isDeload", w.isDeload)
                })
            }
        })

        root.put("sets", JSONArray().also { arr ->
            sets.forEach { s ->
                arr.put(JSONObject().apply {
                    put("id", s.id); put("workoutId", s.workoutId); put("exerciseId", s.exerciseId)
                    put("exerciseName", s.exerciseName); put("exerciseOrder", s.exerciseOrder)
                    put("setNumber", s.setNumber); put("weightKg", s.weightKg.toDouble())
                    put("reps", s.reps); put("rpe", s.rpe.toDouble())
                    put("durationSeconds", s.durationSeconds); put("isWarmup", s.isWarmup)
                    put("isCompleted", s.isCompleted); put("setType", s.setType)
                    put("note", s.note); put("performedAt", s.performedAt)
                })
            }
        })

        root.put("prs", JSONArray().also { arr ->
            prs.forEach { p ->
                arr.put(JSONObject().apply {
                    put("id", p.id); put("exerciseId", p.exerciseId); put("exerciseName", p.exerciseName)
                    put("type", p.type); put("value", p.value.toDouble())
                    put("weightKg", p.weightKg.toDouble()); put("reps", p.reps)
                    put("dateMillis", p.dateMillis); put("workoutId", p.workoutId); put("isManual", p.isManual)
                })
            }
        })

        root.put("bodyMetrics", JSONArray().also { arr ->
            body.forEach { b ->
                arr.put(JSONObject().apply {
                    put("id", b.id); put("dateMillis", b.dateMillis)
                    put("weightKg", b.weightKg.toDouble()); put("bodyFatPct", b.bodyFatPct.toDouble())
                    put("chestCm", b.chestCm.toDouble()); put("waistCm", b.waistCm.toDouble())
                    put("hipCm", b.hipCm.toDouble()); put("armCm", b.armCm.toDouble())
                    put("thighCm", b.thighCm.toDouble()); put("neckCm", b.neckCm.toDouble())
                    put("note", b.note)
                })
            }
        })

        root.put("notes", JSONArray().also { arr ->
            notes.forEach { n ->
                arr.put(JSONObject().apply {
                    put("id", n.id); put("title", n.title); put("content", n.content)
                    put("category", n.category); put("colorHex", n.colorHex)
                    put("isPinned", n.isPinned); put("dateMillis", n.dateMillis)
                })
            }
        })

        return root.toString(2)
    }

    suspend fun importBackupJson(jsonStr: String, dao: FitDao): ImportResult {
        return try {
            val rootStr = jsonStr.trim()
            if (rootStr.isBlank()) {
                return ImportResult(false, "Yedek verisi boş.")
            }

            val exerciseIdMap = mutableMapOf<Long, Long>()
            val routineIdMap = mutableMapOf<Long, Long>()
            val dayIdMap = mutableMapOf<Long, Long>()
            val workoutIdMap = mutableMapOf<Long, Long>()

            var exercisesCount = 0
            var routinesCount = 0
            var workoutsCount = 0

            suspend fun getOrFindExerciseId(oldId: Long, name: String): Long {
                if (oldId != 0L && exerciseIdMap.containsKey(oldId)) {
                    return exerciseIdMap[oldId]!!
                }
                val cleanName = name.trim()
                if (cleanName.isNotBlank()) {
                    dao.exerciseByName(cleanName)?.let {
                        if (oldId != 0L) exerciseIdMap[oldId] = it.id
                        return it.id
                    }
                    val newId = dao.insertExercise(
                        ExerciseEntity(
                            name = cleanName,
                            muscleGroup = com.example.core.Muscles.guess(cleanName),
                            isCustom = true
                        )
                    )
                    if (oldId != 0L) exerciseIdMap[oldId] = newId
                    exercisesCount++
                    return newId
                }
                return 0L
            }

            if (rootStr.startsWith("{")) {
                val root = JSONObject(rootStr)

                // 1. Exercises
                val exercisesArr = root.optJSONArray("exercises")
                if (exercisesArr != null) {
                    for (i in 0 until exercisesArr.length()) {
                        val obj = exercisesArr.optJSONObject(i) ?: continue
                        val oldId = obj.optLong("id")
                        val name = obj.optString("name").trim()
                        if (name.isBlank()) continue

                        val existing = dao.exerciseByName(name)
                        if (existing != null) {
                            if (oldId != 0L) exerciseIdMap[oldId] = existing.id
                        } else {
                            val newId = dao.insertExercise(
                                ExerciseEntity(
                                    name = name,
                                    muscleGroup = obj.optString("muscleGroup", com.example.core.Muscles.guess(name)),
                                    secondaryMuscles = obj.optString("secondaryMuscles", ""),
                                    equipment = obj.optString("equipment", "Diğer"),
                                    trackingType = obj.optString("trackingType", ExerciseEntity.TRACK_WEIGHT_REPS),
                                    instructions = obj.optString("instructions", ""),
                                    tips = obj.optString("tips", ""),
                                    videoUrl = obj.optString("videoUrl", ""),
                                    isCustom = obj.optBoolean("isCustom", true),
                                    isFavorite = obj.optBoolean("isFavorite", false),
                                    defaultRestSeconds = obj.optInt("defaultRestSeconds", 90)
                                )
                            )
                            if (oldId != 0L) exerciseIdMap[oldId] = newId
                            exercisesCount++
                        }
                    }
                }

                // 2. Routines
                val routinesArr = root.optJSONArray("routines")
                if (routinesArr != null) {
                    for (i in 0 until routinesArr.length()) {
                        val obj = routinesArr.optJSONObject(i) ?: continue
                        val oldId = obj.optLong("id")
                        val name = obj.optString("name").trim()
                        if (name.isBlank()) continue

                        val newId = dao.insertRoutine(
                            RoutineEntity(
                                name = name,
                                description = obj.optString("description", ""),
                                colorHex = obj.optString("colorHex", "#3B82F6"),
                                isActive = obj.optBoolean("isActive", false),
                                orderIndex = obj.optInt("orderIndex", i),
                                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                        if (oldId != 0L) routineIdMap[oldId] = newId
                        routinesCount++
                    }
                }

                // 3. Days
                val daysArr = root.optJSONArray("days")
                if (daysArr != null) {
                    for (i in 0 until daysArr.length()) {
                        val obj = daysArr.optJSONObject(i) ?: continue
                        val oldId = obj.optLong("id")
                        val oldRoutineId = obj.optLong("routineId")
                        val newRoutineId = routineIdMap[oldRoutineId]
                        if (newRoutineId != null) {
                            val newId = dao.insertDay(
                                RoutineDayEntity(
                                    routineId = newRoutineId,
                                    name = obj.optString("name", "Gün"),
                                    focus = obj.optString("focus", ""),
                                    weekday = obj.optInt("weekday", 0),
                                    orderIndex = obj.optInt("orderIndex", i)
                                )
                            )
                            if (oldId != 0L) dayIdMap[oldId] = newId
                        }
                    }
                }

                // 4. Items
                val itemsArr = root.optJSONArray("items")
                if (itemsArr != null) {
                    for (i in 0 until itemsArr.length()) {
                        val obj = itemsArr.optJSONObject(i) ?: continue
                        val oldDayId = obj.optLong("dayId")
                        val newDayId = dayIdMap[oldDayId] ?: continue
                        val oldExId = obj.optLong("exerciseId")
                        val newExId = getOrFindExerciseId(oldExId, "")
                        if (newExId == 0L) continue

                        dao.insertItem(
                            RoutineItemEntity(
                                dayId = newDayId,
                                exerciseId = newExId,
                                orderIndex = obj.optInt("orderIndex", i),
                                targetSets = obj.optInt("targetSets", 3),
                                repMin = obj.optInt("repMin", 8),
                                repMax = obj.optInt("repMax", 12),
                                targetWeight = obj.optDouble("targetWeight", 0.0).toFloat(),
                                restSeconds = obj.optInt("restSeconds", 90),
                                isWarmup = obj.optBoolean("isWarmup", false),
                                supersetGroup = obj.optInt("supersetGroup", 0),
                                note = obj.optString("note", "")
                            )
                        )
                    }
                }

                // 5. Workouts
                val workoutsArr = root.optJSONArray("workouts")
                if (workoutsArr != null) {
                    for (i in 0 until workoutsArr.length()) {
                        val obj = workoutsArr.optJSONObject(i) ?: continue
                        val oldId = obj.optLong("id")
                        val oldDayId = if (obj.isNull("routineDayId")) null else obj.optLong("routineDayId")
                        val newDayId = if (oldDayId != null) dayIdMap[oldDayId] else null

                        val newWorkoutId = dao.insertWorkout(
                            WorkoutEntity(
                                routineDayId = newDayId,
                                routineName = obj.optString("routineName", ""),
                                title = obj.optString("title", "Antrenman"),
                                startedAt = obj.optLong("startedAt", System.currentTimeMillis()),
                                finishedAt = if (obj.isNull("finishedAt")) null else obj.optLong("finishedAt"),
                                durationSeconds = obj.optInt("durationSeconds", 0),
                                notes = obj.optString("notes", ""),
                                feeling = obj.optInt("feeling", 3),
                                bodyWeightKg = obj.optDouble("bodyWeightKg", 0.0).toFloat(),
                                isFinished = obj.optBoolean("isFinished", true),
                                isDeload = obj.optBoolean("isDeload", false)
                            )
                        )
                        if (oldId != 0L) workoutIdMap[oldId] = newWorkoutId
                        workoutsCount++
                    }
                }

                // 6. Sets
                val setsArr = root.optJSONArray("sets")
                if (setsArr != null) {
                    val setList = mutableListOf<WorkoutSetEntity>()
                    for (i in 0 until setsArr.length()) {
                        val obj = setsArr.optJSONObject(i) ?: continue
                        val oldWorkoutId = obj.optLong("workoutId")
                        val newWorkoutId = workoutIdMap[oldWorkoutId] ?: continue
                        val oldExId = obj.optLong("exerciseId")
                        val exName = obj.optString("exerciseName", "Hareket")
                        val newExId = getOrFindExerciseId(oldExId, exName)

                        setList.add(
                            WorkoutSetEntity(
                                workoutId = newWorkoutId,
                                exerciseId = newExId,
                                exerciseName = exName,
                                exerciseOrder = obj.optInt("exerciseOrder", 0),
                                setNumber = obj.optInt("setNumber", 1),
                                weightKg = obj.optDouble("weightKg", 0.0).toFloat(),
                                reps = obj.optInt("reps", 0),
                                rpe = obj.optDouble("rpe", 0.0).toFloat(),
                                durationSeconds = obj.optInt("durationSeconds", 0),
                                isWarmup = obj.optBoolean("isWarmup", false),
                                isCompleted = obj.optBoolean("isCompleted", true),
                                setType = obj.optString("setType", WorkoutSetEntity.TYPE_NORMAL),
                                note = obj.optString("note", ""),
                                performedAt = obj.optLong("performedAt", System.currentTimeMillis())
                            )
                        )
                    }
                    if (setList.isNotEmpty()) {
                        dao.insertSets(setList)
                    }
                }

                // 7. PRs
                val prsArr = root.optJSONArray("prs")
                if (prsArr != null) {
                    for (i in 0 until prsArr.length()) {
                        val obj = prsArr.optJSONObject(i) ?: continue
                        val oldExId = obj.optLong("exerciseId")
                        val exName = obj.optString("exerciseName", "")
                        val newExId = getOrFindExerciseId(oldExId, exName)
                        val oldWkId = obj.optLong("workoutId")
                        val newWkId = if (oldWkId > 0) workoutIdMap[oldWkId] ?: 0L else 0L

                        dao.insertPr(
                            PrEntity(
                                exerciseId = newExId,
                                exerciseName = exName,
                                type = obj.optString("type", PrEntity.TYPE_WEIGHT),
                                value = obj.optDouble("value", 0.0).toFloat(),
                                weightKg = obj.optDouble("weightKg", 0.0).toFloat(),
                                reps = obj.optInt("reps", 0),
                                dateMillis = obj.optLong("dateMillis", System.currentTimeMillis()),
                                workoutId = newWkId,
                                isManual = obj.optBoolean("isManual", false)
                            )
                        )
                    }
                }

                // 8. Body Metrics
                val bodyArr = root.optJSONArray("bodyMetrics")
                if (bodyArr != null) {
                    for (i in 0 until bodyArr.length()) {
                        val obj = bodyArr.optJSONObject(i) ?: continue
                        dao.insertBodyMetric(
                            BodyMetricEntity(
                                dateMillis = obj.optLong("dateMillis", System.currentTimeMillis()),
                                weightKg = obj.optDouble("weightKg", 0.0).toFloat(),
                                bodyFatPct = obj.optDouble("bodyFatPct", 0.0).toFloat(),
                                chestCm = obj.optDouble("chestCm", 0.0).toFloat(),
                                waistCm = obj.optDouble("waistCm", 0.0).toFloat(),
                                hipCm = obj.optDouble("hipCm", 0.0).toFloat(),
                                armCm = obj.optDouble("armCm", 0.0).toFloat(),
                                thighCm = obj.optDouble("thighCm", 0.0).toFloat(),
                                neckCm = obj.optDouble("neckCm", 0.0).toFloat(),
                                note = obj.optString("note", "")
                            )
                        )
                    }
                }

                // 9. Notes
                val notesArr = root.optJSONArray("notes")
                if (notesArr != null) {
                    for (i in 0 until notesArr.length()) {
                        val obj = notesArr.optJSONObject(i) ?: continue
                        dao.insertNote(
                            NoteEntity(
                                title = obj.optString("title", ""),
                                content = obj.optString("content", ""),
                                category = obj.optString("category", "Genel"),
                                colorHex = obj.optString("colorHex", "#3B82F6"),
                                isPinned = obj.optBoolean("isPinned", false),
                                dateMillis = obj.optLong("dateMillis", System.currentTimeMillis())
                            )
                        )
                    }
                }
            } else if (rootStr.startsWith("[")) {
                // Raw array of workout objects
                val arr = JSONArray(rootStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val title = obj.optString("title", obj.optString("name", "Antrenman"))
                    val startedAt = obj.optLong("startedAt", obj.optLong("dateMillis", System.currentTimeMillis()))
                    val duration = obj.optInt("durationSeconds", 1800)
                    dao.insertWorkout(
                        WorkoutEntity(
                            title = title,
                            startedAt = startedAt,
                            finishedAt = startedAt + (duration * 1000L),
                            durationSeconds = duration,
                            notes = obj.optString("notes", ""),
                            isFinished = true
                        )
                    )
                    workoutsCount++
                }
            } else {
                return ImportResult(false, "Geçersiz veri formatı. JSON formatında olmalıdır.")
            }

            ImportResult(
                success = true,
                message = "$workoutsCount antrenman, $exercisesCount yeni hareket ve $routinesCount program başarıyla aktarıldı.",
                workoutsImported = workoutsCount,
                exercisesImported = exercisesCount,
                routinesImported = routinesCount
            )
        } catch (e: Exception) {
            ImportResult(
                success = false,
                message = "Hata: ${e.localizedMessage ?: "Veri ayrıştırılamadı"}"
            )
        }
    }

    /**
     * JSON yedeğini cache dizinine .json dosyası olarak yazar.
     */
    fun writeBackupToFile(context: Context, jsonStr: String): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "fitflow_pro_yedek_$timeStamp.json"
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, fileName)
            FileOutputStream(file).use { it.write(jsonStr.toByteArray(Charsets.UTF_8)) }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Yedeği hem dosya hem de metin olarak Android Paylaşım Menüsü (ShareSheet) ile açar.
     */
    fun shareBackup(context: Context, jsonStr: String): Boolean {
        return try {
            val file = writeBackupToFile(context, jsonStr)
            val intent = Intent(Intent.ACTION_SEND)
            
            if (file != null) {
                val authority = "${context.packageName}.fileprovider"
                val uri: Uri = FileProvider.getUriForFile(context, authority, file)
                intent.type = "application/json"
                intent.putExtra(Intent.EXTRA_STREAM, uri)
                intent.putExtra(Intent.EXTRA_SUBJECT, "FitFlow Pro Antrenman Yedeği")
                intent.putExtra(Intent.EXTRA_TEXT, "FitFlow Pro antrenman ve program yedeğim ekte yer almaktadır.")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_SUBJECT, "FitFlow Pro Antrenman Yedeği")
                intent.putExtra(Intent.EXTRA_TEXT, jsonStr)
            }

            val chooser = Intent.createChooser(intent, "FitFlow Pro Yedeğini Paylaş / Kaydet").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            // Doğrudan metin olarak paylaşmayı dene
            try {
                val fallback = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, jsonStr)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(fallback, "Yedeği Paylaş"))
                true
            } catch (err: Exception) {
                false
            }
        }
    }

    /**
     * JSON metnini panoya kopyalar.
     */
    fun copyToClipboard(context: Context, jsonStr: String): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("FitFlow Yedek", jsonStr)
            clipboard?.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            false
        }
    }
}
