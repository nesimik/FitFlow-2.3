package com.example.work

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.core.Analytics
import com.example.core.startOfDay
import com.example.core.startOfWeek
import com.example.core.todayWeekday
import com.example.data.FitDatabase
import com.example.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Ana ekran widget'ı: sıradaki antrenman günü, haftalık seri ve bu haftanın hedefi.
 * Dokununca uygulama açılır. Uygulama açıldığında, antrenman bitince ve günlük işte güncellenir.
 */
class NextWorkoutWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        WidgetUpdater.scope.launch {
            try { WidgetUpdater.render(context, manager, ids) } finally { pending.finish() }
        }
    }
}

object WidgetUpdater {
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val DAY_NAMES = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")

    /** Tüm widget'ları yeniler (widget yoksa hiçbir şey yapmaz). */
    fun refresh(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val ids = manager.getAppWidgetIds(ComponentName(app, NextWorkoutWidget::class.java))
        if (ids.isEmpty()) return
        scope.launch { runCatching { render(app, manager, ids) } }
    }

    internal suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val dao = FitDatabase.get(context).dao()
        val settings = SettingsStore(context)
        val workouts = dao.observeFinishedWorkouts().first()
        val now = System.currentTimeMillis()
        val routineForGoal = dao.observeActiveRoutine().first()
        val allDays = routineForGoal?.let { dao.daysForRoutine(it.id) }.orEmpty()
        val goal = com.example.core.Schedule.weeklyGoal(allDays, settings.weeklyGoal.value)
        val streak = Analytics.streakWeeks(workouts, goal)
        val weekDone = workouts.count { it.startedAt >= startOfWeek(now) }

        var label = "SIRADAKİ"
        var title = "Program yok"
        var sub = "Uygulamada bir program oluştur"
        val routine = dao.observeActiveRoutine().first()
        if (routine != null) {
            // 2.39: ana ekranla aynı kural (haftalık takvim ya da A/B dönüşümlü)
            val days = allDays
            val today = todayWeekday()
            val todayD = com.example.core.Schedule.todayDay(days, workouts, today)
            val doneToday = todayD != null && workouts.any { it.startedAt >= startOfDay(now) && it.routineDayId == todayD.id }
            val target = if (todayD != null && !doneToday) todayD else com.example.core.Schedule.nextPlannedDay(days, workouts, today)
            val pick = target?.let { d ->
                val ahead = if (d.id == todayD?.id && !doneToday) 0 else com.example.core.Schedule.daysAhead(d, today).let { if (it == 0 && d.weekday in 1..7) 7 else it }
                d to ahead
            }
            if (pick != null) {
                val (day, off) = pick
                label = when {
                    off == 0 -> if (day.weekday in 1..7) "BUGÜN" else "SIRADAKİ"
                    off == 1 -> "YARIN"
                    day.weekday in 1..7 -> DAY_NAMES[day.weekday - 1].uppercase(java.util.Locale("tr"))
                    else -> "SIRADAKİ"
                }
                title = day.name
                val items = dao.itemsForDay(day.id).filter { !it.isWarmup }.sortedBy { it.orderIndex }
                val names = items.take(3).mapNotNull { i -> i.customName.ifBlank { dao.exerciseById(i.exerciseId)?.name ?: "" }.takeIf { it.isNotBlank() } }
                sub = "${items.size} hareket · ${items.sumOf { it.targetSets }} set" +
                    if (names.isNotEmpty()) "\n" + names.joinToString(" · ") else ""
            } else if (doneToday) {
                label = "BUGÜN"; title = "Antrenman tamam 💪"; sub = "Sıradaki gün programda yok"
            }
        }

        val open = PendingIntent.getActivity(
            context, 7001,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // "Başla": bugünün antrenmanını doğrudan başlatır
        val start = PendingIntent.getActivity(
            context, 7002,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_START_TODAY, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val canStart = (label == "BUGÜN" || label == "SIRADAKİ") && !title.startsWith("Antrenman tamam") && title != "Program yok"
        ids.forEach { id ->
            val v = RemoteViews(context.packageName, R.layout.widget_next).apply {
                setTextViewText(R.id.widget_label, label)
                setTextViewText(R.id.widget_title, title)
                setTextViewText(R.id.widget_sub, sub)
                setTextViewText(R.id.widget_streak, "🔥 $streak hf")
                setTextViewText(R.id.widget_week, "$weekDone/$goal bu hafta")
                setTextViewText(R.id.widget_start, if (canStart) "Başla" else "Aç")
                setOnClickPendingIntent(R.id.widget_root, open)
                setOnClickPendingIntent(R.id.widget_start, if (canStart) start else open)
            }
            manager.updateAppWidget(id, v)
        }
    }
}
