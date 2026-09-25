package com.example.ui

import android.app.Application
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Analytics
import com.example.core.Calc
import com.example.core.DashboardStats
import com.example.core.DeloadAdvisor
import com.example.core.LiftStandard
import com.example.core.LoadKind
import com.example.core.LoggedSet
import com.example.core.Prescription
import com.example.core.ProgressionEngine
import com.example.core.SessionLog
import com.example.core.loadKindOf
import com.example.core.MuscleLoad
import com.example.core.ProgressAnalytics
import com.example.core.RepRangeSlice
import com.example.core.StagnantLift
import com.example.core.TrainingLoad
import com.example.core.WeekAdherence
import com.example.core.startOfWeek
import com.example.core.trimNum
import com.example.data.BodyMetricEntity
import com.example.data.ExerciseEntity
import com.example.data.FitDatabase
import com.example.data.FitRepository
import com.example.data.NoteEntity
import com.example.data.PrEntity
import com.example.data.RoutineDayEntity
import com.example.data.RoutineEntity
import com.example.data.RoutineItemEntity
import com.example.data.SessionExercise
import com.example.data.SettingsStore
import com.example.data.WorkoutEntity
import com.example.data.WorkoutSetEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/* ------------------------------- UI durumları ------------------------------- */

data class RestTimerState(
    val total: Int = 0,
    val remaining: Int = 0,
    val running: Boolean = false,
    val finished: Boolean = false,
    val label: String = "",
    val exerciseId: Long? = null,
    /** Çalışırken bitiş anı (epoch ms); duraklatılmışken 0. */
    val endAt: Long = 0L
) {
    val progress: Float get() = if (total <= 0) 0f else 1f - remaining.toFloat() / total
    val active: Boolean get() = running || finished || remaining > 0
}

data class DurationTimerState(
    val total: Int = 0,
    val remaining: Int = 0,
    val running: Boolean = false,
    val label: String = "",
    val setNumber: Int = 1,
    val set: WorkoutSetEntity? = null,
    val restSeconds: Int = 90
) {
    val progress: Float get() = if (total <= 0) 0f else 1f - remaining.toFloat() / total
}

data class PrCelebration(val prs: List<PrEntity>, val workoutTitle: String)

/** Seans bitişinde gösterilen özet: önceki seansa göre hacim ve bir sonraki seansın reçeteleri. */
data class SessionReview(
    val volume: Float,
    val previousVolume: Float,
    val next: List<Pair<String, Prescription>>
) {
    /** Önceki seansa göre hacim değişimi (%), karşılaştırma yoksa null. */
    val volumeDeltaPct: Int? get() =
        if (previousVolume <= 0f || volume <= 0f) null
        else Math.round((volume - previousVolume) / previousVolume * 100f)
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val db = FitDatabase.get(app)
    val repo = FitRepository(app, db.dao())
    val settings = SettingsStore(app)

    private fun <T> stateOf(f: kotlinx.coroutines.flow.Flow<T>, initial: T): StateFlow<T> =
        f.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    /* --------------------------------- Veri --------------------------------- */

    val exercises = stateOf(repo.exercises, emptyList())
    val routines = stateOf(repo.routines, emptyList())
    val activeRoutine = stateOf(repo.activeRoutine, null)
    val allDays = stateOf(repo.allDays, emptyList())
    val allItems = stateOf(repo.allItems, emptyList())
    val workouts = stateOf(repo.workouts, emptyList())
    val allSets = stateOf(repo.allSets, emptyList())
    val prs = stateOf(repo.prs, emptyList())
    val bodyMetrics = stateOf(repo.bodyMetrics, emptyList())
    val notes = stateOf(repo.notes, emptyList())
    val activeWorkout = stateOf(repo.activeWorkout, null)

    /** Aktif programın günleri. */
    val routineDays: StateFlow<List<RoutineDayEntity>> =
        combine(allDays, activeRoutine) { days, routine ->
            days.filter { routine != null && it.routineId == routine.id }.sortedBy { it.orderIndex }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _legacyImported = MutableStateFlow(false)
    val legacyImported: StateFlow<Boolean> = _legacyImported.asStateFlow()

    /* -------------------------------- Analiz -------------------------------- */

    val dashboard: StateFlow<DashboardStats> =
        combine(workouts, allSets) { w, s -> Analytics.dashboard(w, s) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardStats())

    val weeklySeries = combine(workouts, allSets) { w, s -> Analytics.weeklySeries(w, s, 8) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val monthlySeries = combine(workouts, allSets) { w, s -> Analytics.monthlySeries(w, s, 6) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val heatmap = combine(workouts, allSets) { w, s -> Analytics.heatmapLevels(w, s, 84) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weeklyMuscleSetsInfo = combine(allSets, exercises) { s, e -> Analytics.weeklySetsPerMuscleInfo(s, e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Analytics.MuscleSetsResult("Bu hafta", emptyMap()))

    val weeklyMuscleSets = weeklyMuscleSetsInfo.map { it.setsPerMuscle }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val bestLifts = allSets.map { Analytics.bestE1rmByExercise(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /* ---------------------- Gelişmiş ilerleme analizleri ---------------------- */

    /** Bu haftanın kas bazlı etkin set yüklenmesi (birincil 1.0 / ikincil 0.5). */
    val weeklyMuscleLoads: StateFlow<List<MuscleLoad>> =
        combine(allSets, exercises) { s, e ->
            ProgressAnalytics.muscleLoads(s, e, startOfWeek(System.currentTimeMillis()), 1f)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Son 4 haftanın haftalık ortalaması — daha stabil bir denge tablosu. */
    val monthlyMuscleLoads: StateFlow<List<MuscleLoad>> =
        combine(allSets, exercises) { s, e ->
            ProgressAnalytics.muscleLoads(
                s, e, System.currentTimeMillis() - 28L * 86_400_000L, 4f
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Harita detay bölgeleri (üst / alt göğüs) — bu hafta. */
    val weeklyDetailLoads: StateFlow<List<MuscleLoad>> =
        combine(allSets, exercises) { s, e ->
            ProgressAnalytics.detailLoads(s, e, startOfWeek(System.currentTimeMillis()), 1f)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Harita detay bölgeleri — son 4 haftanın haftalık ortalaması. */
    val monthlyDetailLoads: StateFlow<List<MuscleLoad>> =
        combine(allSets, exercises) { s, e ->
            ProgressAnalytics.detailLoads(s, e, System.currentTimeMillis() - 28L * 86_400_000L, 4f)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Şu anki kas toparlanma tahmini (her çağrıda güncel saatle hesaplanır). */
    fun recoveryNow(): Map<String, com.example.core.MuscleRecovery> =
        com.example.core.RecoveryEngine.fromSets(allSets.value, exercises.value)

    /** Akut : kronik yüklenme oranı. */
    val trainingLoad: StateFlow<TrainingLoad> =
        combine(workouts, allSets) { w, s -> ProgressAnalytics.trainingLoad(w, s) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainingLoad(0f, 0f, 0, 0f))

    /** Uzun süredir rekor kırılmayan hareketler. */
    val stagnantLifts: StateFlow<List<StagnantLift>> =
        allSets.map { ProgressAnalytics.stagnation(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Son seansta ilerleme kaydedilen hareketler. */
    val improvingLifts: StateFlow<List<StagnantLift>> =
        allSets.map { ProgressAnalytics.improving(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Ana hareketlerde vücut ağırlığına göre güç seviyesi. */
    val strengthProfile: StateFlow<List<LiftStandard>> =
        combine(allSets, settings.weightKg, settings.isMale) { s, bw, male ->
            ProgressAnalytics.strengthProfile(s, bw, male)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Haftalık hedefe uyum geçmişi. */
    val adherence: StateFlow<List<WeekAdherence>> =
        combine(workouts, settings.weeklyGoal) { w, goal ->
            ProgressAnalytics.adherence(w, goal, 8)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tekrar aralığı dağılımı — güç / hipertrofi / dayanıklılık payı. */
    val repRanges: StateFlow<List<RepRangeSlice>> =
        allSets.map { ProgressAnalytics.repRangeDistribution(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Haftalık ortalama RPE eğilimi. */
    val weeklyRpe: StateFlow<List<Pair<String, Float>>> =
        allSets.map { ProgressAnalytics.weeklyRpe(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 6-8 haftalık zorlanma ve deload tavsiye durumu. */
    val deloadRecommendation: StateFlow<DeloadAdvisor.DeloadRecommendation> =
        combine(workouts, allSets, settings.activeDeloadWeekStart) { wList, sList, deloadStart ->
            DeloadAdvisor.analyze(wList, sList, deloadStart)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DeloadAdvisor.DeloadRecommendation(
                shouldDeloadNow = false,
                currentCycleWeek = 1,
                recommendedWeek = 7,
                strainScore = 30,
                strainLevel = "Düşük",
                reasonTitle = "",
                reasonDetails = emptyList(),
                adviceList = listOf(
                    DeloadAdvisor.ADVICE_KEEP_OR_REDUCE_WEIGHT,
                    DeloadAdvisor.ADVICE_HALVE_SETS,
                    DeloadAdvisor.ADVICE_REPS_8_10
                ),
                isCurrentlyDeloadWeek = false
            )
        )

    fun muscleLoadsSince(sinceMillis: Long, periodWeeks: Float): List<MuscleLoad> =
        ProgressAnalytics.muscleLoads(allSets.value, exercises.value, sinceMillis, periodWeeks)

    fun setsForExercise(exerciseId: Long): List<WorkoutSetEntity> =
        allSets.value.filter { it.exerciseId == exerciseId }

    fun muscleDistribution(sinceMillis: Long) =
        Analytics.muscleDistribution(allSets.value, exercises.value, sinceMillis)

    fun progressFor(exerciseId: Long) =
        Analytics.exerciseProgress(allSets.value.filter { it.exerciseId == exerciseId })

    fun setsOfWorkout(workoutId: Long) =
        allSets.value.filter { it.workoutId == workoutId }.sortedWith(compareBy({ it.exerciseOrder }, { it.setNumber }))

    /* ------------------------------ Aktif seans ----------------------------- */

    private val activeSets: StateFlow<List<WorkoutSetEntity>> = activeWorkout
        .flatMapLatest { w -> if (w == null) flowOf(emptyList()) else repo.setsOf(w.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Aktif seansın hareketleri, önceki seans verisiyle birlikte. */
    val sessionExercises: StateFlow<List<SessionExercise>> =
        combine(
            combine(activeSets, exercises, allSets) { current, lib, history -> Triple(current, lib, history) },
            combine(activeWorkout, allItems, workouts) { workout, items, wList -> Triple(workout, items, wList) }
        ) { (current, lib, history), (workout, items, wList) ->
            if (workout == null) emptyList() else {
                val libMap = lib.associateBy { it.id }
                val dayItems = items.filter { it.dayId == workout.routineDayId }.sortedBy { it.orderIndex }
                val workoutMap = wList.associateBy { it.id }
                val currentRoutineDayId = workout.routineDayId

                current.groupBy { it.exerciseOrder }
                    .map { (order, sets) ->
                        val exId = sets.first().exerciseId
                        val ex = libMap[exId]
                        // SADECE ve SADECE bu güne (currentRoutineDayId) ait tamamlanmış seanslardaki setleri al.
                        // Bir hareket başka günlerde de olsa, o günlerdeki set ve ağırlıklar ASLA önceki olarak gelmez!
                        val pastSets = history
                            .filter { s ->
                                s.exerciseId == exId &&
                                s.workoutId != workout.id &&
                                s.isCompleted &&
                                (if (currentRoutineDayId != null) {
                                    workoutMap[s.workoutId]?.let { it.routineDayId == currentRoutineDayId && it.isFinished } == true
                                } else {
                                    workoutMap[s.workoutId]?.isFinished == true
                                })
                            }
                        val prevSets = pastSets
                            .groupBy { it.workoutId }
                            .maxByOrNull { entry -> entry.value.maxOf { it.performedAt } }
                            ?.value
                            ?.let { workoutSets ->
                                val setsByOrder = workoutSets.groupBy { it.exerciseOrder }
                                setsByOrder[order] ?: setsByOrder.values.toList().getOrNull(order) ?: workoutSets
                            }
                            ?.sortedBy { it.setNumber }
                            ?: emptyList()
                        val item = dayItems.find { it.orderIndex == order } ?: dayItems.getOrNull(order)
                        val sGroup = sets.firstOrNull()?.supersetGroup?.takeIf { it > 0 } ?: item?.supersetGroup ?: 0
                        val tracking = ex?.trackingType ?: ExerciseEntity.TRACK_WEIGHT_REPS
                        val exIsWarmup = sets.firstOrNull()?.isWarmup ?: (item?.isWarmup == true)
                        val rxHistory = pastSets
                            .filter { !it.isWarmup && it.reps > 0 }
                            .groupBy { it.workoutId }
                            .map { (wid, ws) ->
                                SessionLog(
                                    workoutMap[wid]?.startedAt ?: ws.first().performedAt,
                                    ws.sortedBy { it.setNumber }.map { LoggedSet(it.weightKg, it.reps, it.rpe) }
                                )
                            }
                            .sortedByDescending { it.dateMillis }
                            .take(8)
                        val prescription = if (tracking == ExerciseEntity.TRACK_DURATION || exIsWarmup) null
                        else ProgressionEngine.prescribe(
                            history = rxHistory,
                            targetSets = run {
                                val rows = sets.count { !it.isWarmup }
                                if (workout.isDeload) (item?.targetSets ?: rows).coerceAtLeast(1)
                                else maxOf(item?.targetSets ?: 0, rows).coerceAtLeast(1)
                            },
                            repMin = item?.repMin ?: 8,
                            repMax = item?.repMax ?: 12,
                            kind = if (tracking == ExerciseEntity.TRACK_REPS) LoadKind.BODYWEIGHT
                                   else loadKindOf(ex?.equipment ?: ""),
                            profile = settings.loadingProfile(),
                            deload = workout.isDeload
                        )
                        SessionExercise(
                            exerciseId = exId,
                            name = sets.first().exerciseName,
                            muscleGroup = ex?.muscleGroup ?: com.example.core.Muscles.guess(sets.first().exerciseName),
                            trackingType = ex?.trackingType ?: ExerciseEntity.TRACK_WEIGHT_REPS,
                            order = order,
                            restSeconds = item?.restSeconds ?: ex?.defaultRestSeconds ?: settings.defaultRest.value,
                            targetRepMin = item?.repMin ?: 8,
                            targetRepMax = item?.repMax ?: 12,
                            supersetGroup = sGroup,
                            note = item?.note ?: "",
                            sets = sets.sortedBy { it.setNumber },
                            previous = prevSets,
                            videoUrl = ex?.videoUrl ?: "",
                            isWarmup = exIsWarmup,
                            equipment = ex?.equipment ?: "",
                            prescription = prescription,
                            history = rxHistory
                        )
                    }
                    .sortedBy { it.order }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _elapsed = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsed.asStateFlow()

    private val _restTimer = MutableStateFlow(RestTimerState())
    val restTimer: StateFlow<RestTimerState> = _restTimer.asStateFlow()

    private val _durationTimer = MutableStateFlow<DurationTimerState?>(null)
    val durationTimer: StateFlow<DurationTimerState?> = _durationTimer.asStateFlow()

    private var durationJob: Job? = null

    private val _celebration = MutableStateFlow<PrCelebration?>(null)
    val celebration: StateFlow<PrCelebration?> = _celebration.asStateFlow()
    fun dismissCelebration() { _celebration.value = null }

    private val _statsTab = MutableStateFlow(0)
    val statsTab: StateFlow<Int> = _statsTab.asStateFlow()
    fun setStatsTab(tab: Int) { _statsTab.value = tab.coerceIn(0, 3) }

    private val _scrollToExerciseEvent = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val scrollToExerciseEvent = _scrollToExerciseEvent.asSharedFlow()

    fun scrollToExercise(exerciseId: Long) {
        viewModelScope.launch {
            _scrollToExerciseEvent.emit(exerciseId)
        }
    }

    private var restJob: Job? = null
    private var ringtone: Ringtone? = null
    private var alarmJob: Job? = null

    init {
        viewModelScope.launch {
            val imported = repo.seedIfNeeded()
            _legacyImported.value = imported
            _isReady.value = true
        }
        // Seans kronometresi
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val w = activeWorkout.value
                _elapsed.value = if (w == null) 0
                else ((System.currentTimeMillis() - w.startedAt) / 1000L).toInt().coerceAtLeast(0)
            }
        }
    }

    /* ------------------------------ Seans akışı ----------------------------- */

    fun startWorkout(day: RoutineDayEntity?, isDeload: Boolean? = null, onStarted: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val routine = activeRoutine.value
            val shouldDeload = isDeload ?: deloadRecommendation.value.isCurrentlyDeloadWeek
            val workoutTitle = if (shouldDeload && !day?.name.orEmpty().contains("Deload", ignoreCase = true)) {
                "${day?.name ?: "Antrenman"} (Deload)"
            } else {
                day?.name ?: "Serbest Antrenman"
            }
            val id = repo.startWorkout(
                dayId = day?.id,
                title = workoutTitle,
                routineName = routine?.name ?: "",
                isDeload = shouldDeload,
                profile = settings.loadingProfile()
            )
            _elapsed.value = 0
            onStarted(id)
        }
    }

    fun setWorkoutDeload(workoutId: Long, isDeload: Boolean) {
        viewModelScope.launch {
            repo.setWorkoutDeload(workoutId, isDeload)
        }
    }

    fun toggleCurrentWeekDeload() {
        val currentWeek = startOfWeek(System.currentTimeMillis())
        val active = settings.activeDeloadWeekStart.value
        if (active == currentWeek) {
            settings.clearActiveDeloadWeek()
        } else {
            settings.setActiveDeloadWeek(currentWeek)
        }
    }

    fun setDeloadWeekActive(active: Boolean) {
        val currentWeek = startOfWeek(System.currentTimeMillis())
        if (active) {
            settings.setActiveDeloadWeek(currentWeek)
        } else {
            settings.clearActiveDeloadWeek()
        }
    }

    fun applyDeloadToActiveRoutine(
        reduceWeightsPercent: Float = 0f,
        customAdjustments: Map<Long, Pair<Int, Float>>? = null,
        onSuccess: () -> Unit = {}
    ) {
        val routine = activeRoutine.value ?: return
        viewModelScope.launch {
            val backup = repo.applyDeloadToRoutine(routine.id, reduceWeightsPercent, customAdjustments)
            if (backup.isNotBlank()) {
                settings.setPreDeloadBackup(backup)
            }
            val currentWeek = startOfWeek(System.currentTimeMillis())
            settings.setActiveDeloadWeek(currentWeek)
            onSuccess()
        }
    }

    fun restorePreDeloadRoutine(onSuccess: () -> Unit = {}) {
        val backup = settings.getPreDeloadBackup()
        viewModelScope.launch {
            if (backup.isNotBlank()) {
                repo.restorePreDeloadRoutine(backup)
                settings.setPreDeloadBackup("")
            }
            settings.clearActiveDeloadWeek()
            onSuccess()
        }
    }

    fun cancelActiveWorkout() {
        val w = activeWorkout.value ?: return
        viewModelScope.launch { repo.deleteWorkout(w) }
    }

    fun toggleItemWarmup(item: RoutineItemEntity) {
        viewModelScope.launch { repo.toggleItemWarmup(item) }
    }

    fun addExerciseToSession(exerciseId: Long) {
        val w = activeWorkout.value ?: return
        viewModelScope.launch { repo.addExerciseToWorkout(w.id, exerciseId) }
    }

    fun addExerciseToWorkout(workoutId: Long, exerciseId: Long) {
        viewModelScope.launch { repo.addExerciseToWorkout(workoutId, exerciseId) }
    }

    fun removeExerciseFromSession(exerciseOrder: Int) {
        val w = activeWorkout.value ?: return
        viewModelScope.launch { repo.removeExerciseFromWorkout(w.id, exerciseOrder) }
    }

    fun toggleExerciseWarmup(exerciseOrder: Int, isWarmup: Boolean) {
        val w = activeWorkout.value ?: return
        viewModelScope.launch {
            repo.toggleExerciseWarmup(w.id, exerciseOrder, isWarmup)
        }
    }

    fun renameSessionExercise(exerciseOrder: Int, newName: String) {
        val w = activeWorkout.value ?: return
        viewModelScope.launch {
            repo.renameSetsInWorkout(w.id, exerciseOrder, newName)
        }
    }

    fun addSetRow(exerciseOrder: Int, exerciseId: Long) {
        val w = activeWorkout.value ?: return
        viewModelScope.launch { repo.addSetRow(w.id, exerciseOrder, exerciseId) }
    }

    fun removeExerciseFromWorkoutByOrder(workoutId: Long, exerciseOrder: Int) {
        viewModelScope.launch { repo.removeExerciseFromWorkout(workoutId, exerciseOrder) }
    }

    fun removeExerciseFromWorkout(workoutId: Long, exerciseOrder: Int) {
        viewModelScope.launch { repo.removeExerciseFromWorkout(workoutId, exerciseOrder) }
    }

    fun addSetRowToWorkout(workoutId: Long, exerciseOrder: Int, exerciseId: Long) {
        viewModelScope.launch { repo.addSetRow(workoutId, exerciseOrder, exerciseId) }
    }

    fun updateSet(set: WorkoutSetEntity) {
        viewModelScope.launch { repo.updateSet(set) }
    }

    fun deleteSet(set: WorkoutSetEntity) {
        viewModelScope.launch { repo.deleteSet(set) }
    }

    /** Seti tamamlandı/iptal olarak işaretler; tamamlandığında dinlenme sayacını başlatır. */
    fun toggleSetDone(set: WorkoutSetEntity, restSeconds: Int) {
        viewModelScope.launch {
            val now = !set.isCompleted
            repo.updateSetExplicit(set, now)
            if (now && settings.autoRest.value && restSeconds > 0) {
                startRest(restSeconds, set.exerciseName, set.exerciseId)
            } else if (!now) {
                stopRest()
            }
        }
    }

    /** Süreli hareketlerde (Plank vb.): Önce hareket süresi sayacı çalışır, bittiğinde set tamamlanır ve otomatik dinlenme başlar. */
    fun toggleTimedSetDone(set: WorkoutSetEntity, durationSec: Int, restSeconds: Int) {
        if (set.isCompleted) {
            viewModelScope.launch {
                repo.updateSetExplicit(set, false)
                if (_durationTimer.value?.set?.id == set.id) {
                    cancelDurationTimer()
                }
            }
            return
        }

        val targetDuration = if (durationSec > 0) durationSec else if (set.durationSeconds > 0) set.durationSeconds else 30
        durationJob?.cancel()
        stopRest()
        stopAlarm()

        _durationTimer.value = DurationTimerState(
            total = targetDuration,
            remaining = targetDuration,
            running = true,
            label = set.exerciseName,
            setNumber = set.setNumber,
            set = set,
            restSeconds = restSeconds
        )

        durationJob = viewModelScope.launch {
            while ((_durationTimer.value?.remaining ?: 0) > 0 && (_durationTimer.value?.running == true)) {
                delay(1000)
                val curr = _durationTimer.value ?: return@launch
                if (!curr.running) return@launch
                val next = curr.remaining - 1
                _durationTimer.value = curr.copy(remaining = next)
                if (next in 1..3 && settings.countdownBeep.value && settings.sound.value) shortBeep()
            }

            val finalState = _durationTimer.value
            if (finalState != null && finalState.running) {
                _durationTimer.value = null
                repo.updateSetExplicit(set, true)
                if (settings.vibrate.value) vibrate(600)
                if (settings.sound.value) completionBeep()
                if (settings.autoRest.value && restSeconds > 0) {
                    startRest(restSeconds, set.exerciseName, set.exerciseId)
                } else {
                    stopAlarm()
                }
            }
        }
    }

    fun finishDurationTimerEarly() {
        val curr = _durationTimer.value ?: return
        val set = curr.set ?: return
        durationJob?.cancel()
        _durationTimer.value = null
        viewModelScope.launch {
            repo.updateSetExplicit(set, true)
            if (settings.vibrate.value) vibrate(600)
            if (settings.sound.value) completionBeep()
            if (settings.autoRest.value && curr.restSeconds > 0) {
                startRest(curr.restSeconds, set.exerciseName, set.exerciseId)
            } else {
                stopAlarm()
            }
        }
    }

    fun cancelDurationTimer() {
        durationJob?.cancel()
        _durationTimer.value = null
    }

    fun finishWorkout(notes: String, feeling: Int, onDone: () -> Unit) {
        val w = activeWorkout.value ?: return
        val duration = _elapsed.value
        viewModelScope.launch {
            val newPrs = repo.finishWorkout(w.id, duration, notes, feeling, settings.weightKg.value)
            stopRest()
            _elapsed.value = 0
            if (newPrs.isNotEmpty()) _celebration.value = PrCelebration(newPrs, w.title)
            onDone()
        }
    }

    fun discardWorkout(onDone: () -> Unit) {
        val w = activeWorkout.value ?: return
        viewModelScope.launch {
            repo.discardWorkout(w.id)
            stopRest()
            _elapsed.value = 0
            onDone()
        }
    }

    /* --------------------------- Dinlenme sayacı ---------------------------- */
    // Sayaç bitiş zamanına (endAt) göre çalışır: her döngüde kalan süre gerçek saatten
    // hesaplanır, bu yüzden uygulama arka plana düşse de sapma birikmez. Aynı bitiş
    // zamanı sistem bildirimine verilir; kilit ekranında da doğru geri sayım görünür.

    private val restNotifier = RestNotifier(app)

    fun startRest(seconds: Int, label: String, exerciseId: Long? = null) {
        if (seconds <= 0) { stopRest(); return }
        restJob?.cancel()
        stopAlarm()
        val endAt = System.currentTimeMillis() + seconds * 1000L
        _restTimer.value = RestTimerState(
            total = seconds, remaining = seconds, running = true,
            label = label, exerciseId = exerciseId, endAt = endAt
        )
        restNotifier.showCountdown(endAt, label)
        runRestLoop()
    }

    private fun runRestLoop() {
        restJob?.cancel()
        restJob = viewModelScope.launch {
            var lastBeep = -1
            while (true) {
                val s = _restTimer.value
                if (!s.running) return@launch
                val left = (((s.endAt - System.currentTimeMillis()) + 999L) / 1000L).toInt().coerceAtLeast(0)
                if (left != s.remaining) _restTimer.value = s.copy(remaining = left)
                if (left in 1..3 && left != lastBeep && settings.countdownBeep.value && settings.sound.value) {
                    lastBeep = left
                    shortBeep()
                }
                if (left <= 0) break
                delay(200)
            }
            val done = _restTimer.value
            _restTimer.value = done.copy(running = false, finished = true, remaining = 0, endAt = 0L)
            restNotifier.showFinished(done.label)
            if (settings.sound.value) playAlarm()
            if (settings.vibrate.value) vibrate(600)
        }
    }

    fun adjustRest(delta: Int) {
        val s = _restTimer.value
        if (!s.active) return
        when {
            s.finished -> if (delta > 0) startRest(delta, s.label, s.exerciseId)
            s.running -> {
                val newEnd = s.endAt + delta * 1000L
                val left = (((newEnd - System.currentTimeMillis()) + 999L) / 1000L).toInt()
                if (left <= 0) { stopRest(); return }
                _restTimer.value = s.copy(endAt = newEnd, remaining = left, total = maxOf(s.total, left))
                restNotifier.showCountdown(newEnd, s.label)
            }
            else -> { // duraklatılmış
                val left = s.remaining + delta
                if (left <= 0) { stopRest(); return }
                _restTimer.value = s.copy(remaining = left, total = maxOf(s.total, left))
            }
        }
    }

    fun pauseResumeRest() {
        val s = _restTimer.value
        if (s.finished) { stopRest(); return }
        if (s.running) {
            restJob?.cancel()
            val left = (((s.endAt - System.currentTimeMillis()) + 999L) / 1000L).toInt().coerceAtLeast(0)
            _restTimer.value = s.copy(running = false, remaining = left, endAt = 0L)
            restNotifier.cancel()
        } else if (s.remaining > 0) {
            val endAt = System.currentTimeMillis() + s.remaining * 1000L
            _restTimer.value = s.copy(running = true, endAt = endAt)
            restNotifier.showCountdown(endAt, s.label)
            runRestLoop()
        }
    }

    fun stopRest() {
        restJob?.cancel()
        stopAlarm()
        restNotifier.cancel()
        _restTimer.value = RestTimerState()
    }

    /* ----------------------------- Ses & titreşim ---------------------------- */

    fun previewAlarm() {
        if (ringtone?.isPlaying == true) stopAlarm() else playAlarm(autoStopMs = 4000)
    }

    private fun playAlarm(autoStopMs: Long = 20_000) {
        stopAlarm()
        try {
            val uriStr = settings.alarmUri.value
            val uri: Uri? = when {
                uriStr == "beep" -> null
                uriStr.isBlank() || uriStr == "default" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                else -> Uri.parse(uriStr)
            }
            if (uri != null) {
                ringtone = RingtoneManager.getRingtone(getApplication(), uri)?.also { it.play() }
                alarmJob = viewModelScope.launch {
                    val end = System.currentTimeMillis() + autoStopMs
                    while (System.currentTimeMillis() < end) {
                        delay(700)
                        val r = ringtone ?: return@launch
                        if (!r.isPlaying) r.play()
                    }
                    stopAlarm()
                }
            } else {
                alarmJob = viewModelScope.launch {
                    repeat(3) {
                        shortBeep(400)
                        delay(600)
                    }
                }
            }
        } catch (t: Throwable) {
            try { shortBeep(400) } catch (_: Throwable) {}
        }
    }

    fun stopAlarm() {
        alarmJob?.cancel()
        try { ringtone?.let { if (it.isPlaying) it.stop() } } catch (_: Throwable) {}
        ringtone = null
    }

    private fun shortBeep(durationMs: Int = 120) {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, durationMs)
            viewModelScope.launch { delay(durationMs + 200L); try { tg.release() } catch (_: Throwable) {} }
        } catch (_: Throwable) {}
    }

    private fun completionBeep() {
        viewModelScope.launch {
            try {
                shortBeep(180)
                delay(120)
                shortBeep(320)
            } catch (_: Throwable) {}
        }
    }

    fun vibrate(ms: Long) {
        try {
            val app = getApplication<Application>()
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = app.getSystemService(VibratorManager::class.java)
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                app.getSystemService(Vibrator::class.java)
            } ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(ms)
            }
        } catch (_: Throwable) {}
    }

    fun alarmDisplayName(): String {
        val v = settings.alarmUri.value
        return when {
            v == "beep" -> "Bip sesi"
            v.isBlank() || v == "default" -> "Sistem bildirim sesi"
            else -> try {
                RingtoneManager.getRingtone(getApplication(), Uri.parse(v))?.getTitle(getApplication())
                    ?: "Seçilen ses"
            } catch (t: Throwable) { "Seçilen ses" }
        }
    }

    /* -------------------------- Program düzenleme --------------------------- */

    fun addRoutine(name: String, desc: String, color: String) =
        viewModelScope.launch { repo.addRoutine(name, desc, color) }

    fun updateRoutine(r: RoutineEntity) = viewModelScope.launch { repo.updateRoutine(r) }
    fun deleteRoutine(r: RoutineEntity) = viewModelScope.launch { repo.deleteRoutine(r) }
    fun selectRoutine(id: Long) = viewModelScope.launch { repo.selectRoutine(id) }

    fun addDay(name: String, focus: String, weekday: Int) {
        val routine = activeRoutine.value ?: return
        viewModelScope.launch {
            repo.addDay(routine.id, name, focus, weekday, routineDays.value.size)
        }
    }

    fun updateDay(d: RoutineDayEntity) = viewModelScope.launch { repo.updateDay(d) }
    fun deleteDay(d: RoutineDayEntity) = viewModelScope.launch { repo.deleteDay(d) }
    fun duplicateDay(d: RoutineDayEntity) = viewModelScope.launch { repo.duplicateDay(d) }

    fun addItem(dayId: Long, exerciseId: Long) = viewModelScope.launch { repo.addItem(dayId, exerciseId) }
    fun updateItem(i: RoutineItemEntity) = viewModelScope.launch { repo.updateItem(i) }
    fun deleteItem(i: RoutineItemEntity) = viewModelScope.launch { repo.deleteItem(i) }
    fun moveItem(dayId: Long, itemId: Long, up: Boolean) = viewModelScope.launch { repo.moveItem(dayId, itemId, up) }

    fun setRoutineItemSuperset(dayId: Long, itemId: Long, group: Int) =
        viewModelScope.launch { repo.setRoutineItemSuperset(dayId, itemId, group) }

    fun combineRoutineItems(dayId: Long, firstItemId: Long, secondItemId: Long) =
        viewModelScope.launch { repo.combineRoutineItems(dayId, firstItemId, secondItemId) }

    fun dissolveRoutineSuperset(dayId: Long, group: Int) =
        viewModelScope.launch { repo.dissolveRoutineSuperset(dayId, group) }

    fun addSupersetToDay(dayId: Long, exerciseIds: List<Long>) =
        viewModelScope.launch { repo.addSupersetToDay(dayId, exerciseIds) }

    fun setSessionExerciseSuperset(exerciseOrder: Int, group: Int) {
        val w = activeWorkout.value ?: return
        viewModelScope.launch { repo.setSessionExerciseSuperset(w.id, exerciseOrder, group) }
    }

    fun combineSessionExercises(firstOrder: Int, secondOrder: Int) {
        val w = activeWorkout.value ?: return
        viewModelScope.launch { repo.combineSessionExercises(w.id, firstOrder, secondOrder) }
    }

    fun dissolveSessionSuperset(group: Int) {
        val w = activeWorkout.value ?: return
        viewModelScope.launch { repo.dissolveSessionSuperset(w.id, group) }
    }

    fun addSupersetToWorkout(exerciseIds: List<Long>, sets: Int = 3) {
        val w = activeWorkout.value ?: return
        viewModelScope.launch { repo.addSupersetToWorkout(w.id, exerciseIds, sets) }
    }

    fun itemsOf(dayId: Long): List<RoutineItemEntity> =
        allItems.value.filter { it.dayId == dayId }.sortedBy { it.orderIndex }

    fun exerciseById(id: Long): ExerciseEntity? = exercises.value.firstOrNull { it.id == id }

    /* ------------------------------ Kütüphane ------------------------------- */

    fun saveExercise(e: ExerciseEntity) = viewModelScope.launch { repo.saveExercise(e) }
    fun deleteExercise(e: ExerciseEntity) = viewModelScope.launch { repo.deleteExercise(e) }
    fun toggleFavorite(e: ExerciseEntity) = viewModelScope.launch { repo.toggleFavorite(e) }

    /* --------------------------- Geçmiş & rekorlar --------------------------- */

    fun deleteWorkout(w: WorkoutEntity) = viewModelScope.launch { repo.deleteWorkout(w) }
    fun clearAllWorkoutHistory() = viewModelScope.launch { repo.clearAllWorkoutHistory() }
    fun updateWorkout(w: WorkoutEntity) = viewModelScope.launch { repo.updateWorkout(w) }
    fun updateWorkoutDate(workoutId: Long, newStartedAt: Long) = viewModelScope.launch { repo.updateWorkoutDate(workoutId, newStartedAt) }
    fun replaceRoutineDayWithWorkout(dayId: Long, workoutId: Long, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.replaceRoutineDayWithWorkout(dayId, workoutId)
            onDone()
        }
    }
    fun duplicateWorkout(workoutId: Long, onDuplicated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repo.duplicateWorkout(workoutId)
            onDuplicated(id)
        }
    }
    fun createPastWorkout(day: RoutineDayEntity?, dateMillis: Long, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val routine = activeRoutine.value
            val id = repo.createPastWorkout(
                dayId = day?.id,
                title = day?.name ?: "Eski Antrenman",
                routineName = routine?.name ?: "",
                dateMillis = dateMillis
            )
            onCreated(id)
        }
    }
    fun deletePr(pr: PrEntity) = viewModelScope.launch { repo.deletePr(pr) }
    fun addManualPr(exerciseId: Long, name: String, weight: Float, reps: Int, note: String) =
        viewModelScope.launch { repo.addManualPr(exerciseId, name, weight, reps, note) }

    /* -------------------------- Vücut ölçümü / not --------------------------- */

    fun saveBodyMetric(m: BodyMetricEntity) = viewModelScope.launch {
        repo.saveBodyMetric(m)
        if (m.weightKg > 0f) settings.setBodyWeight(m.weightKg)
    }

    fun deleteBodyMetric(m: BodyMetricEntity) = viewModelScope.launch { repo.deleteBodyMetric(m) }
    fun saveNote(n: NoteEntity) = viewModelScope.launch { repo.saveNote(n) }
    fun deleteNote(n: NoteEntity) = viewModelScope.launch { repo.deleteNote(n) }

    /* ------------------------------ Yedekleme ------------------------------- */

    /** Tüm verinin JSON metni. Paylaş menüsüyle dışa aktarılır. */
    fun exportJson(): String = Backup.export(
        exercises.value, routines.value, allDays.value, allItems.value,
        workouts.value, allSets.value, prs.value, bodyMetrics.value, notes.value
    )

    /** Yedek JSON metnini veya dosyasını içe aktarır. */
    fun importBackupJson(jsonStr: String, onResult: (ImportResult) -> Unit) {
        viewModelScope.launch {
            val result = repo.importBackupJson(jsonStr)
            onResult(result)
        }
    }

    /** Cihazdaki eski FitFlow (workout_database) SQLite veritabanını tarayıp içe aktarır. */
    fun importLegacyDatabase(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = repo.importLegacyDatabase()
            onResult(result)
        }
    }

    /* ------------------------------ Öneri motoru ----------------------------- */

    /**
     * Bir program gününün, seans başlamadan önceki reçetesi (ana ekrandaki "Bugünün hedefleri").
     * Seans başlatıldığında FitRepository.startWorkout aynı mantıkla setleri doldurur.
     */
    fun planFor(dayId: Long): List<Pair<String, Prescription>> {
        val items = allItems.value.filter { it.dayId == dayId }.sortedBy { it.orderIndex }
        val dayWorkouts = workouts.value.filter { it.isFinished && it.routineDayId == dayId }.associateBy { it.id }
        val lib = exercises.value.associateBy { it.id }
        val profile = settings.loadingProfile()
        val deload = deloadRecommendation.value.isCurrentlyDeloadWeek
        val sets = allSets.value
        return items.mapNotNull { item ->
            val ex = lib[item.exerciseId] ?: return@mapNotNull null
            if (item.isWarmup || ex.trackingType == ExerciseEntity.TRACK_DURATION) return@mapNotNull null
            val sessions = sets
                .filter { it.exerciseId == item.exerciseId && it.isCompleted && !it.isWarmup && it.reps > 0 && it.workoutId in dayWorkouts }
                .groupBy { it.workoutId }
                .map { (wid, ws) ->
                    SessionLog(
                        dayWorkouts[wid]?.startedAt ?: ws.first().performedAt,
                        ws.sortedBy { it.setNumber }.map { LoggedSet(it.weightKg, it.reps, it.rpe) }
                    )
                }
                .sortedByDescending { it.dateMillis }
                .take(8)
            val name = item.customName.ifBlank { ex.name }
            name to ProgressionEngine.prescribe(
                history = sessions,
                targetSets = maxOf(item.targetSets, sessions.firstOrNull()?.sets?.size ?: 0),
                repMin = item.repMin,
                repMax = item.repMax,
                kind = if (ex.trackingType == ExerciseEntity.TRACK_REPS) LoadKind.BODYWEIGHT else loadKindOf(ex.equipment),
                profile = profile,
                deload = deload
            )
        }
    }

    /**
     * Devam eden seansın özetini ve bu seansın sonuçlarına göre BİR SONRAKİ seansın
     * reçetelerini üretir. Bitirme ekranında "gelecek hafta ne yapacağım" sorusunu yanıtlar.
     */
    fun sessionReview(): SessionReview {
        val list = sessionExercises.value
        val profile = settings.loadingProfile()
        var volume = 0f
        var prevVolume = 0f
        val next = mutableListOf<Pair<String, Prescription>>()
        list.forEach { se ->
            val working = se.sets.filter { it.isCompleted && !it.isWarmup && it.reps > 0 }
            volume += working.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat()
            prevVolume += se.previous.filter { !it.isWarmup }.sumOf { (it.weightKg * it.reps).toDouble() }.toFloat()
            if (se.prescription == null || working.isEmpty()) return@forEach
            val today = SessionLog(System.currentTimeMillis(), working.map { LoggedSet(it.weightKg, it.reps, it.rpe) })
            val rx = ProgressionEngine.prescribe(
                history = listOf(today) + se.history,
                targetSets = working.size,
                repMin = se.targetRepMin,
                repMax = se.targetRepMax,
                kind = if (se.trackingType == ExerciseEntity.TRACK_REPS) LoadKind.BODYWEIGHT else loadKindOf(se.equipment),
                profile = profile
            )
            next += se.name to rx
        }
        return SessionReview(volume, prevVolume, next)
    }

    /** Bir hareket için bir sonraki seans önerisi. */
    fun suggestionFor(se: SessionExercise): String {
        val prev = se.previous.filter { !it.isWarmup }
        if (prev.isEmpty()) return "İlk kayıt — kontrollü bir ağırlıkla başla, formu önceliklendir."
        val lastWeight = prev.maxOf { it.weightKg }
        val reps = prev.map { it.reps }
        val rpe = prev.filter { it.rpe > 0f }.map { it.rpe }.average().let { if (it.isNaN()) 0f else it.toFloat() }
        val (weight, text) = Calc.suggestNextWeight(
            lastWeight, reps, rpe, se.targetRepMin, se.targetRepMax, settings.increment.value
        )
        return if (weight > 0f) "${weight.trimNum()} kg · $text" else text
    }

    override fun onCleared() {
        super.onCleared()
        restJob?.cancel()
        stopAlarm()
        restNotifier.cancel()
    }
}
