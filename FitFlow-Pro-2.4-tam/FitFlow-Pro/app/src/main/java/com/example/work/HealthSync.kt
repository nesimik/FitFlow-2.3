package com.example.work

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import com.example.data.FitDatabase
import com.example.data.SettingsStore
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

/**
 * Health Connect (Samsung Health / Google Fit) eşitlemesi: antrenman seansları ve vücut ağırlığı.
 * Kayıtlar clientRecordId ile yazılır; aynı kayıt ikinci kez gönderilirse güncellenir, çoğalmaz.
 * Yalnızca Android 8.0+ ve Health Connect kuruluysa çalışır.
 */
object HealthSync {

    val PERMISSIONS: Set<String> = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class)
    )

    fun isAvailable(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            runCatching { HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE }.getOrDefault(false)

    private fun client(context: Context): HealthConnectClient? =
        if (isAvailable(context)) runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull() else null

    suspend fun hasPermissions(context: Context): Boolean {
        val c = client(context) ?: return false
        return runCatching { c.permissionController.getGrantedPermissions().containsAll(PERMISSIONS) }.getOrDefault(false)
    }

    /** Tek seansı (bitmişse) gönderir. */
    suspend fun syncWorkout(context: Context, workoutId: Long) {
        if (!SettingsStore(context).healthConnectOn.value) return
        val c = client(context) ?: return
        if (!hasPermissions(context)) return
        val w = FitDatabase.get(context).dao().workoutById(workoutId) ?: return
        sessionRecord(w)?.let { r -> runCatching { c.insertRecords(listOf(r)) } }
    }

    /** Vücut ağırlığı kaydı gönderir. */
    suspend fun syncWeight(context: Context, id: Long, dateMillis: Long, kg: Float) {
        if (!SettingsStore(context).healthConnectOn.value || kg <= 0f) return
        val c = client(context) ?: return
        if (!hasPermissions(context)) return
        runCatching { c.insertRecords(listOf(weightRecord(id, dateMillis, kg))) }
    }

    /** Son 180 günün seanslarını ve tüm ağırlık ölçümlerini gönderir. @return gönderilen kayıt sayısı */
    suspend fun syncAll(context: Context): Int {
        val c = client(context) ?: return 0
        if (!hasPermissions(context)) return 0
        val dao = FitDatabase.get(context).dao()
        val since = System.currentTimeMillis() - 180L * 86_400_000L
        val records = mutableListOf<Record>()
        dao.observeFinishedWorkouts().first().filter { it.startedAt >= since }.forEach { w -> sessionRecord(w)?.let { records += it } }
        dao.observeBodyMetrics().first().filter { it.weightKg > 0f }.forEach { m -> records += weightRecord(m.id, m.dateMillis, m.weightKg) }
        var sent = 0
        records.chunked(100).forEach { chunk -> if (runCatching { c.insertRecords(chunk) }.isSuccess) sent += chunk.size }
        return sent
    }

    private fun sessionRecord(w: com.example.data.WorkoutEntity): ExerciseSessionRecord? {
        if (!w.isFinished) return null
        val start = Instant.ofEpochMilli(w.startedAt)
        val endMs = w.finishedAt?.takeIf { it > w.startedAt }
            ?: (w.startedAt + (w.durationSeconds.takeIf { it > 0 } ?: 3600) * 1000L)
        val end = Instant.ofEpochMilli(endMs)
        val zone = ZoneId.systemDefault().rules
        return ExerciseSessionRecord(
            startTime = start,
            startZoneOffset = zone.getOffset(start),
            endTime = end,
            endZoneOffset = zone.getOffset(end),
            metadata = Metadata.manualEntry(clientRecordId = "fitflow-workout-${w.id}"),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            title = w.title,
            notes = w.notes.takeIf { it.isNotBlank() }
        )
    }

    private fun weightRecord(id: Long, dateMillis: Long, kg: Float): WeightRecord {
        val t = Instant.ofEpochMilli(dateMillis)
        return WeightRecord(
            time = t,
            zoneOffset = ZoneId.systemDefault().rules.getOffset(t),
            weight = Mass.kilograms(kg.toDouble()),
            metadata = Metadata.manualEntry(clientRecordId = "fitflow-weight-$id")
        )
    }
}
