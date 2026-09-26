package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FitDao {

    /* ------------------------- Hareket kütüphanesi ------------------------- */

    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE ASC")
    fun observeExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun exerciseById(id: Long): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun exerciseByName(name: String): ExerciseEntity?

    @Query("SELECT * FROM exercises")
    suspend fun getAllExercises(): List<ExerciseEntity>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun exerciseCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(e: ExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(list: List<ExerciseEntity>)

    @Update
    suspend fun updateExercise(e: ExerciseEntity)

    @Delete
    suspend fun deleteExercise(e: ExerciseEntity)

    /* ------------------------------ Programlar ----------------------------- */

    @Query("SELECT * FROM routines ORDER BY orderIndex ASC, id ASC")
    fun observeRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE isActive = 1 LIMIT 1")
    fun observeActiveRoutine(): Flow<RoutineEntity?>

    @Query("SELECT COUNT(*) FROM routines")
    suspend fun routineCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(r: RoutineEntity): Long

    @Update
    suspend fun updateRoutine(r: RoutineEntity)

    @Delete
    suspend fun deleteRoutine(r: RoutineEntity)

    @Query("UPDATE routines SET isActive = 0")
    suspend fun clearActiveRoutines()

    @Query("UPDATE routines SET isActive = 1 WHERE id = :id")
    suspend fun setActiveRoutine(id: Long)

    @Query("SELECT * FROM routine_days ORDER BY orderIndex ASC, id ASC")
    fun observeAllDays(): Flow<List<RoutineDayEntity>>

    @Query("SELECT * FROM routine_days WHERE routineId = :routineId ORDER BY orderIndex ASC, id ASC")
    fun observeDays(routineId: Long): Flow<List<RoutineDayEntity>>

    @Query("SELECT * FROM routine_days WHERE routineId = :routineId ORDER BY orderIndex ASC, id ASC")
    suspend fun daysForRoutine(routineId: Long): List<RoutineDayEntity>

    @Query("SELECT * FROM routine_days WHERE id = :id")
    suspend fun dayById(id: Long): RoutineDayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDay(d: RoutineDayEntity): Long

    @Update
    suspend fun updateDay(d: RoutineDayEntity)

    @Delete
    suspend fun deleteDay(d: RoutineDayEntity)

    @Query("SELECT * FROM routine_items ORDER BY orderIndex ASC, id ASC")
    fun observeAllItems(): Flow<List<RoutineItemEntity>>

    @Query("SELECT * FROM routine_items WHERE dayId = :dayId ORDER BY orderIndex ASC, id ASC")
    fun observeItems(dayId: Long): Flow<List<RoutineItemEntity>>

    @Query("SELECT * FROM routine_items WHERE dayId = :dayId ORDER BY orderIndex ASC, id ASC")
    suspend fun itemsForDay(dayId: Long): List<RoutineItemEntity>

    @Query("SELECT * FROM routine_items WHERE id = :id")
    suspend fun itemById(id: Long): RoutineItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(i: RoutineItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<RoutineItemEntity>): List<Long>

    @Update
    suspend fun updateItem(i: RoutineItemEntity)

    @Update
    suspend fun updateItems(items: List<RoutineItemEntity>)

    @Delete
    suspend fun deleteItem(i: RoutineItemEntity)

    @Query("DELETE FROM routine_items WHERE dayId = :dayId")
    suspend fun deleteItemsForDay(dayId: Long)

    /* ------------------------------ Antrenman ------------------------------ */

    @Query("SELECT * FROM workouts WHERE isFinished = 1 ORDER BY startedAt DESC")
    fun observeFinishedWorkouts(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE isFinished = 0 ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveWorkout(): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workouts WHERE isFinished = 0")
    suspend fun getUnfinishedWorkouts(): List<WorkoutEntity>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun workoutById(id: Long): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE routineDayId = :dayId AND isFinished = 1 ORDER BY startedAt DESC LIMIT 1")
    suspend fun lastFinishedWorkoutForDay(dayId: Long): WorkoutEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(w: WorkoutEntity): Long

    @Update
    suspend fun updateWorkout(w: WorkoutEntity)

    @Query("UPDATE workouts SET isDeload = :isDeload WHERE id = :workoutId")
    suspend fun setWorkoutDeload(workoutId: Long, isDeload: Boolean)

    @Delete
    suspend fun deleteWorkout(w: WorkoutEntity)

    @Query(
        """SELECT s.* FROM workout_sets s
           INNER JOIN workouts w ON w.id = s.workoutId
           WHERE w.isFinished = 1
           ORDER BY s.performedAt ASC"""
    )
    fun observeAllSets(): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets WHERE workoutId = :workoutId ORDER BY exerciseOrder ASC, setNumber ASC")
    fun observeSets(workoutId: Long): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets WHERE workoutId = :workoutId ORDER BY exerciseOrder ASC, setNumber ASC")
    suspend fun setsForWorkout(workoutId: Long): List<WorkoutSetEntity>

    @Query(
        """SELECT s.* FROM workout_sets s
           INNER JOIN workouts w ON w.id = s.workoutId
           WHERE s.exerciseId = :exerciseId AND w.isFinished = 1
           ORDER BY s.performedAt ASC"""
    )
    suspend fun finishedSetsForExercise(exerciseId: Long): List<WorkoutSetEntity>

    @Query(
        """SELECT s.* FROM workout_sets s
           INNER JOIN workouts w ON w.id = s.workoutId
           WHERE s.exerciseId = :exerciseId AND w.isFinished = 1 AND s.isCompleted = 1
           ORDER BY s.performedAt DESC LIMIT 30"""
    )
    suspend fun lastSetsForExercise(exerciseId: Long): List<WorkoutSetEntity>

    @Query(
        """SELECT s.* FROM workout_sets s
           INNER JOIN workouts w ON w.id = s.workoutId
           WHERE w.routineDayId = :dayId AND s.exerciseId = :exerciseId AND w.isFinished = 1 AND s.isCompleted = 1
           ORDER BY w.startedAt DESC, s.setNumber ASC"""
    )
    suspend fun lastSetsForExerciseInDay(dayId: Long, exerciseId: Long): List<WorkoutSetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(s: WorkoutSetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(list: List<WorkoutSetEntity>)

    @Update
    suspend fun updateSet(s: WorkoutSetEntity)

    @Delete
    suspend fun deleteSet(s: WorkoutSetEntity)

    @Query("DELETE FROM workout_sets WHERE workoutId = :workoutId")
    suspend fun deleteSetsForWorkout(workoutId: Long)

    @Query("DELETE FROM workout_sets WHERE workoutId = :workoutId AND exerciseId = :exerciseId")
    suspend fun deleteExerciseFromWorkout(workoutId: Long, exerciseId: Long)

    @Query("DELETE FROM workout_sets WHERE workoutId = :workoutId AND isCompleted = 0")
    suspend fun deleteIncompleteSets(workoutId: Long)

    @Query("UPDATE workout_sets SET performedAt = :newTimestamp WHERE workoutId = :workoutId")
    suspend fun updateSetTimestampsForWorkout(workoutId: Long, newTimestamp: Long)

    @Query("UPDATE workout_sets SET exerciseName = :name WHERE exerciseId = :exerciseId")
    suspend fun updateExerciseNameInSets(exerciseId: Long, name: String)

    /* -------------------------------- Rekor -------------------------------- */

    @Query(
        """SELECT p.* FROM personal_records p
           LEFT JOIN workouts w ON w.id = p.workoutId
           WHERE p.workoutId IS NULL OR w.isFinished = 1
           ORDER BY p.dateMillis DESC"""
    )
    fun observePrs(): Flow<List<PrEntity>>

    @Query("DELETE FROM workouts")
    suspend fun deleteAllWorkouts()

    @Query("DELETE FROM workout_sets")
    suspend fun deleteAllSets()

    @Query("DELETE FROM personal_records")
    suspend fun deleteAllPrs()

    @Query("SELECT * FROM personal_records WHERE exerciseId = :exerciseId AND type = :type ORDER BY value DESC LIMIT 1")
    suspend fun bestPr(exerciseId: Long, type: String): PrEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPr(pr: PrEntity): Long

    @Delete
    suspend fun deletePr(pr: PrEntity)

    @Query("DELETE FROM personal_records WHERE workoutId = :workoutId")
    suspend fun deletePrsForWorkout(workoutId: Long)

    @Query("UPDATE personal_records SET dateMillis = :newTimestamp WHERE workoutId = :workoutId")
    suspend fun updatePrTimestampsForWorkout(workoutId: Long, newTimestamp: Long)

    /* ---------------------------- Vücut ölçümleri --------------------------- */

    @Query("SELECT * FROM body_metrics ORDER BY dateMillis DESC")
    fun observeBodyMetrics(): Flow<List<BodyMetricEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBodyMetric(m: BodyMetricEntity): Long

    @Update
    suspend fun updateBodyMetric(m: BodyMetricEntity)

    @Delete
    suspend fun deleteBodyMetric(m: BodyMetricEntity)

    /* -------------------------------- Notlar -------------------------------- */

    @Query("SELECT * FROM notes ORDER BY isPinned DESC, dateMillis DESC")
    fun observeNotes(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(n: NoteEntity): Long

    @Update
    suspend fun updateNote(n: NoteEntity)

    @Delete
    suspend fun deleteNote(n: NoteEntity)
}
