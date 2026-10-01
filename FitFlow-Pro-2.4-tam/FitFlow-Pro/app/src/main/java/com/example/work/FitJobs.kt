package com.example.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.core.DeloadAdvisor
import com.example.core.MuscleMap
import com.example.core.ProgressAnalytics
import com.example.core.TrainingBlock
import com.example.core.formatTonnage
import com.example.core.startOfDay
import com.example.core.startOfWeek
import com.example.core.todayWeekday
import com.example.data.FitDatabase
import com.example.data.SettingsStore
import com.example.ui.Backup
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/* ==========================================================================
 * Arka plan işleri (WorkManager)
 *
 *  - Günlük iş: seçilen saatte çalışır → antrenman hatırlatıcısı, pazartesi haftalık
 *    rapor ve (gerekirse) otomatik yedek.
 *  - Yedek işi: her antrenman bitince ve "Şimdi yedekle" ile tek seferlik çalışır.
 * ========================================================================== */

object FitJobs {
    private const val DAILY = "fitflow_daily"
    private const val BACKUP = "fitflow_backup"

    /** Uygulama açılışında: günlük işi kurar (varsa dokunmaz). */
    fun ensureScheduled(context: Context) = scheduleDaily(context, replace = false)

    /** Hatırlatma saati değişince günlük işi yeni saate göre yeniden kurar. */
    fun reschedule(context: Context) = scheduleDaily(context, replace = true)

    private fun scheduleDaily(context: Context, replace: Boolean) {
        val minute = SettingsStore(context).reminderMinute.value
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minute / 60)
            set(Calendar.MINUTE, minute % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val delay = next.timeInMillis - now.timeInMillis
        val req = PeriodicWorkRequestBuilder<DailyWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY,
            if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
            req
        )
    }

    /** Otomatik yedek açıksa hemen (tek seferlik) yedek alır. */
    fun backupNow(context: Context) {
        if (SettingsStore(context).backupFolder.value.isBlank()) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            BACKUP, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<BackupWorker>().build()
        )
    }
}

class DailyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext)
        runCatching { Reminders.maybeRemind(applicationContext, settings) }
        runCatching { Reminders.maybeWeeklyReport(applicationContext, settings) }
        // Son 24 saatte yedek alınmadıysa ve yeni veri varsa yedekle.
        runCatching { AutoBackup.run(applicationContext, settings, onlyIfChanged = true) }
        runCatching { WidgetUpdater.refresh(applicationContext) }
        return Result.success()
    }
}

class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ok = AutoBackup.run(applicationContext, SettingsStore(applicationContext), onlyIfChanged = false)
        return if (ok) Result.success() else Result.failure()
    }
}

/* ------------------------------ Otomatik yedek ----------------------------- */

object AutoBackup {
    private const val KEEP = 10
    private const val PREFIX = "FitFlow-yedek-"

    /** @return true = yedek yazıldı ya da gerek yoktu. */
    suspend fun run(context: Context, settings: SettingsStore, onlyIfChanged: Boolean): Boolean {
        val folder = settings.backupFolder.value
        if (folder.isBlank()) return true
        val dao = FitDatabase.get(context).dao()
        val workouts = dao.observeFinishedWorkouts().first()
        val last = settings.lastBackupAt.value
        if (onlyIfChanged) {
            val newest = workouts.maxOfOrNull { it.finishedAt ?: it.startedAt } ?: 0L
            if (newest <= last) return true
        }
        return try {
            val json = Backup.export(
                dao.getAllExercises(),
                dao.observeRoutines().first(),
                dao.observeAllDays().first(),
                dao.observeAllItems().first(),
                workouts,
                dao.observeAllSets().first(),
                dao.observePrs().first(),
                dao.observeBodyMetrics().first(),
                dao.observeNotes().first()
            )
            val tree = Uri.parse(folder)
            val resolver = context.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
            val file = DocumentsContract.createDocument(resolver, parent, "application/json", "$PREFIX$stamp.json")
                ?: error("Dosya oluşturulamadı")
            resolver.openOutputStream(file, "w")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                ?: error("Dosyaya yazılamadı")
            prune(context, tree)
            settings.setLastBackupAt(System.currentTimeMillis())
            settings.setBackupError("")
            true
        } catch (t: Throwable) {
            settings.setBackupError(
                if (t is SecurityException) "Klasör izni kaldırılmış. Yedek klasörünü yeniden seç."
                else "Yedek alınamadı: ${t.message ?: t.javaClass.simpleName}"
            )
            false
        }
    }

    /** Klasörde en yeni [KEEP] yedeği bırakır, eskilerini siler. */
    private fun prune(context: Context, tree: Uri) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val found = mutableListOf<Pair<String, String>>() // (ad, belge id)
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                if (name.startsWith(PREFIX) && name.endsWith(".json")) found += name to c.getString(1)
            }
        }
        found.sortedByDescending { it.first }.drop(KEEP).forEach { (_, id) ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }
}

/* ------------------------- Hatırlatıcı ve haftalık rapor ------------------------ */

object Reminders {
    private const val CHANNEL = "fitflow_reminders"
    private const val ID_REMIND = 4101
    private const val ID_REPORT = 4102

    private fun channel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Hatırlatıcılar ve raporlar", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Antrenman günü hatırlatıcısı ve pazartesi haftalık raporu"
                }
            )
        }
    }

    private fun canPost(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        else NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun openApp(context: Context, code: Int): PendingIntent = PendingIntent.getActivity(
        context, code,
        Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    @android.annotation.SuppressLint("MissingPermission")
    private fun post(context: Context, id: Int, title: String, text: String, big: String = text) {
        if (!canPost(context)) return
        channel(context)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rest)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(big))
            .setContentIntent(openApp(context, id))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    /** Bugün programda gün varsa ve henüz antrenman yapılmadıysa hatırlatır. */
    suspend fun maybeRemind(context: Context, settings: SettingsStore) {
        if (!settings.reminderOn.value) return
        val today = startOfDay(System.currentTimeMillis())
        if (settings.lastReminderDay == today) return
        val dao = FitDatabase.get(context).dao()
        val routine = dao.observeActiveRoutine().first() ?: return
        val day = dao.daysForRoutine(routine.id).firstOrNull { it.weekday == todayWeekday() } ?: return
        val doneToday = dao.observeFinishedWorkouts().first().any { it.startedAt >= today }
        if (doneToday) return
        val items = dao.itemsForDay(day.id).filter { !it.isWarmup }
        val names = items.sortedBy { it.orderIndex }.take(3)
            .mapNotNull { dao.exerciseById(it.exerciseId)?.let { e -> it.customName.ifBlank { e.name } } }
        post(
            context, ID_REMIND,
            "Bugün antrenman günü: ${day.name}",
            "${items.size} hareket · ${items.sumOf { it.targetSets }} set" +
                if (names.isNotEmpty()) " · ${names.joinToString(", ")}" else ""
        )
        settings.lastReminderDay = today
    }

    /** Pazartesi: geçen haftanın özeti ve bu haftanın blok hedefi. */
    suspend fun maybeWeeklyReport(context: Context, settings: SettingsStore) {
        if (!settings.weeklyReportOn.value) return
        val now = System.currentTimeMillis()
        if (todayWeekday() != 1) return
        val thisWeek = startOfWeek(now)
        if (settings.lastReportWeek == thisWeek) return
        val week = 7 * 86_400_000L
        val lastWeek = thisWeek - week
        val prevWeek = lastWeek - week

        val dao = FitDatabase.get(context).dao()
        val workouts = dao.observeFinishedWorkouts().first()
        val sets = dao.observeAllSets().first()
        val exercises = dao.getAllExercises()
        val prs = dao.observePrs().first()

        fun volumeIn(from: Long, to: Long): Float {
            val ids = workouts.filter { it.startedAt in from until to }.map { it.id }.toHashSet()
            return sets.filter { it.workoutId in ids && it.isCompleted && !it.isWarmup }.sumOf { it.load.toDouble() }.toFloat()
        }
        val sessions = workouts.count { it.startedAt in lastWeek until thisWeek }
        if (sessions == 0 && workouts.none { it.startedAt >= lastWeek - 3 * week }) {
            settings.lastReportWeek = thisWeek; return // uzun süredir kullanılmıyorsa rapor gönderme
        }
        val vol = volumeIn(lastWeek, thisWeek)
        val prev = volumeIn(prevWeek, lastWeek)
        val change = if (prev > 0f) Math.round((vol - prev) / prev * 100f) else null
        val prCount = prs.count { it.dateMillis in lastWeek until thisWeek && !it.isManual }

        val lastWeekSets = sets.filter { s -> s.performedAt in lastWeek until thisWeek }
        val weak = ProgressAnalytics.muscleLoads(lastWeekSets, exercises, lastWeek, 1f)
            .filter { it.key in MuscleMap.all && it.target.first > 0 && it.effectiveSets < it.target.first }
            .sortedBy { it.effectiveSets / it.target.first }
            .take(3)
            .map { MuscleMap.label(it.key) }

        val rec = DeloadAdvisor.analyze(
            workouts, sets, settings.activeDeloadWeekStart.value, settings.deloadDismissedWeek.value, settings.blockLoadWeeks.value, now
        )
        val effort = TrainingBlock.effort(rec.currentCycleWeek, rec.loadWeeks, rec.isCurrentlyDeloadWeek)

        val headline = "$sessions seans · ${formatTonnage(vol)}" +
            (change?.let { " · " + (if (it >= 0) "+" else "−") + "%" + kotlin.math.abs(it) } ?: "")
        val body = buildString {
            append("Geçen hafta: $headline")
            if (prCount > 0) append("\n$prCount yeni rekor")
            if (weak.isNotEmpty()) append("\nEksik kalan: ${weak.joinToString(", ")}")
            append("\nBu hafta: ")
            append(
                when {
                    rec.showRecommendation -> "deload önerisi var"
                    rec.isCurrentlyDeloadWeek -> "deload haftası"
                    else -> "blok haftası ${rec.currentCycleWeek}/${rec.loadWeeks}"
                }
            )
            append(" · hedef ${effort.rir}")
        }
        post(context, ID_REPORT, "Haftalık rapor", headline, body)
        settings.lastReportWeek = thisWeek
    }
}
