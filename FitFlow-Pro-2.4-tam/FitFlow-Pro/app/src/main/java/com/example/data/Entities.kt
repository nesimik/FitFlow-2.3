package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/* ------------------------------------------------------------------
 * FitFlow Pro — veri modeli
 *
 * Eski sürümde ağırlık/tekrar bilgileri serbest metin olarak ("8 kg / el")
 * hareket şablonunun üstünde tutuluyordu ve geçmiş "@@@" ile ayrılmış tek bir
 * string'e serileştiriliyordu. Bu yüzden gerçek analiz yapmak imkânsızdı.
 *
 * Yeni model set bazlıdır: her set ayrı bir satırdır (ağırlık: Float, tekrar:
 * Int, RPE: Float). Hacim, 1RM, PR ve kas grubu analizleri buradan hesaplanır.
 * ------------------------------------------------------------------ */

/** Hareket kütüphanesi. Uygulama 150+ hazır hareketle gelir, kullanıcı ekleyebilir. */
@Entity(tableName = "exercises", indices = [Index("name")])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroup: String,              // Göğüs, Sırt, Bacak, Omuz, Kol, Karın, Kardiyo
    val secondaryMuscles: String = "",
    val equipment: String = "Diğer",      // Barbell, Dumbbell, Makine, Kablo, Vücut Ağırlığı, Kettlebell, Bant, Diğer
    val trackingType: String = TRACK_WEIGHT_REPS,
    val instructions: String = "",
    val tips: String = "",
    val videoUrl: String = "",
    val isCustom: Boolean = false,
    val isFavorite: Boolean = false,
    val defaultRestSeconds: Int = 90
) {
    companion object {
        const val TRACK_WEIGHT_REPS = "weight_reps"   // ağırlık x tekrar
        const val TRACK_REPS = "reps"                 // sadece tekrar (vücut ağırlığı)
        const val TRACK_DURATION = "duration"         // süre (plank, kardiyo)
        const val TRACK_DISTANCE = "distance"         // mesafe + süre
    }
}

/** Bir program (ör. "3 Günlük Push / Pull / Full Body"). */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val colorHex: String = "#3B82F6",
    val isActive: Boolean = false,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/** Programın bir günü (ör. "A Günü — Push"). weekday: 0 = serbest, 1 = Pazartesi ... 7 = Pazar */
@Entity(tableName = "routine_days", indices = [Index("routineId")])
data class RoutineDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val name: String,
    val focus: String = "",
    val weekday: Int = 0,
    val orderIndex: Int = 0
)

/** Gün içindeki bir hareket satırı (şablon / hedef). */
@Entity(tableName = "routine_items", indices = [Index("dayId"), Index("exerciseId")])
data class RoutineItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val exerciseId: Long,
    val orderIndex: Int = 0,
    val targetSets: Int = 3,
    val repMin: Int = 8,
    val repMax: Int = 12,
    val targetWeight: Float = 0f,
    val restSeconds: Int = 90,
    val isWarmup: Boolean = false,
    val supersetGroup: Int = 0,           // 0 = süperset yok, aynı sayı = aynı süperset
    val note: String = "",
    val customName: String = ""
)

/** Tamamlanmış (veya devam eden) bir antrenman seansı. */
@Entity(tableName = "workouts", indices = [Index("startedAt")])
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineDayId: Long? = null,
    val routineName: String = "",
    val title: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val durationSeconds: Int = 0,
    val restSeconds: Int = 0,
    val notes: String = "",
    val feeling: Int = 0,                 // 0 = belirtilmemiş, 1..5
    val bodyWeightKg: Float = 0f,
    val isFinished: Boolean = false,
    val isDeload: Boolean = false
)

/** Tek bir set kaydı. Analizlerin tamamı bu tablodan üretilir. */
@Entity(tableName = "workout_sets", indices = [Index("workoutId"), Index("exerciseId")])
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val exerciseOrder: Int = 0,
    val setNumber: Int = 1,
    val weightKg: Float = 0f,
    val reps: Int = 0,
    val rpe: Float = 0f,                  // 0 = girilmemiş
    val durationSeconds: Int = 0,
    val distanceMeters: Float = 0f,
    val isWarmup: Boolean = false,
    val isCompleted: Boolean = false,
    val setType: String = TYPE_NORMAL,
    val supersetGroup: Int = 0,           // 0 = süperset yok, aynı sayı = aynı süperset
    val note: String = "",
    val performedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_NORMAL = "normal"
        const val TYPE_DROP = "drop"
        const val TYPE_FAILURE = "failure"
        const val TYPE_AMRAP = "amrap"
    }

    /** Bu setin hacmi (tonaj). Isınma setleri hacme dahil edilmez. */
    val volume: Float get() = if (isWarmup) 0f else weightKg * reps
}

/** Kişisel rekor. Antrenman bitişinde otomatik hesaplanır. */
@Entity(tableName = "personal_records", indices = [Index("exerciseId")])
data class PrEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val exerciseName: String,
    val type: String,                     // weight | reps | volume | e1rm
    val value: Float,
    val weightKg: Float = 0f,
    val reps: Int = 0,
    val dateMillis: Long = System.currentTimeMillis(),
    val workoutId: Long = 0,
    val isManual: Boolean = false
) {
    companion object {
        const val TYPE_WEIGHT = "weight"
        const val TYPE_REPS = "reps"
        const val TYPE_VOLUME = "volume"
        const val TYPE_E1RM = "e1rm"
    }
}

/** Vücut ölçümleri (kilo, yağ oranı, çevre ölçüleri). */
@Entity(tableName = "body_metrics", indices = [Index("dateMillis")])
data class BodyMetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateMillis: Long = System.currentTimeMillis(),
    val weightKg: Float = 0f,
    val bodyFatPct: Float = 0f,
    val chestCm: Float = 0f,
    val waistCm: Float = 0f,
    val hipCm: Float = 0f,
    val armCm: Float = 0f,
    val thighCm: Float = 0f,
    val neckCm: Float = 0f,
    val note: String = ""
)

/** Serbest notlar / antrenman günlüğü. */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val category: String = "Genel",
    val colorHex: String = "#3B82F6",
    val isPinned: Boolean = false,
    val dateMillis: Long = System.currentTimeMillis()
)

/* ---------------- Yardımcı (Room olmayan) modeller ---------------- */

/** Aktif seans ekranında bir hareketi ve setlerini bir arada tutar. */
data class SessionExercise(
    val exerciseId: Long,
    val name: String,
    val muscleGroup: String,
    val trackingType: String,
    val order: Int,
    val restSeconds: Int,
    val targetRepMin: Int,
    val targetRepMax: Int,
    val supersetGroup: Int,
    val note: String,
    val sets: List<WorkoutSetEntity>,
    val previous: List<WorkoutSetEntity> = emptyList(),
    val videoUrl: String = "",
    val isWarmup: Boolean = false,
    val equipment: String = "",
    /** Progresyon motorunun bugün için önerisi (süreli / ısınma hareketlerinde null). */
    val prescription: com.example.core.Prescription? = null,
    /** Bu günün geçmiş seansları (yeni → eski), yalnızca çalışma setleri. */
    val history: List<com.example.core.SessionLog> = emptyList()
) {
    val completedSets: Int get() = sets.count { it.isCompleted }
    val totalSets: Int get() = sets.size
    val volume: Float get() = sets.filter { it.isCompleted }.sumOf { it.volume.toDouble() }.toFloat()
    val isDone: Boolean get() = sets.isNotEmpty() && sets.all { it.isCompleted }
}

data class RoutineDayWithItems(
    val day: RoutineDayEntity,
    val items: List<RoutineItemEntity>,
    val exerciseNames: List<String>
)

data class MuscleVolume(
    val muscleGroup: String,
    val sets: Int,
    val volume: Float
)

data class ExerciseProgressPoint(
    val dateMillis: Long,
    val topWeight: Float,
    val e1rm: Float,
    val volume: Float,
    val totalReps: Int,
    val sets: Int
)
