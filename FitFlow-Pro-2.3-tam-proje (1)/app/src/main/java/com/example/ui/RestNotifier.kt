package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * Dinlenme sayacını bildirim çubuğunda ve kilit ekranında gösterir.
 *
 * Geri sayım, sistem kronometresiyle (setChronometerCountDown) çizilir; yani telefon
 * kilitliyken bile saniye saniye doğru ilerler ve uygulamanın her saniye bildirim
 * güncellemesine gerek kalmaz. Süre dolduğunda yüksek öncelikli, titreşimli ikinci
 * bir bildirim gösterilir.
 */
class RestNotifier(context: Context) {

    private val ctx = context.applicationContext
    private val nm = NotificationManagerCompat.from(ctx)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = ctx.getSystemService(NotificationManager::class.java)
            mgr?.createNotificationChannel(
                NotificationChannel(CH_TIMER, "Dinlenme sayacı", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Setler arası geri sayım"
                    setShowBadge(false)
                }
            )
            mgr?.createNotificationChannel(
                NotificationChannel(CH_DONE, "Dinlenme bitti", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Dinlenme süresi dolduğunda uyarır"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 350, 150, 350)
                    setSound(null, null) // ses uygulama içinden (seçilen alarm sesi) çalınır
                }
            )
        }
    }

    private fun canPost(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        else nm.areNotificationsEnabled()

    private fun openApp(): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @SuppressLint("MissingPermission")
    fun showCountdown(endAtMillis: Long, label: String) {
        if (!canPost()) return
        val n = NotificationCompat.Builder(ctx, CH_TIMER)
            .setSmallIcon(R.drawable.ic_stat_rest)
            .setContentTitle("Dinlenme")
            .setContentText(label)
            .setWhen(endAtMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp())
            .build()
        try { nm.notify(ID, n) } catch (_: SecurityException) { }
    }

    @SuppressLint("MissingPermission")
    fun showFinished(label: String) {
        if (!canPost()) return
        val n = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(R.drawable.ic_stat_rest)
            .setContentTitle("Dinlenme bitti — sıradaki set")
            .setContentText(label)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(90_000)
            .setContentIntent(openApp())
            .build()
        try { nm.notify(ID, n) } catch (_: SecurityException) { }
    }

    fun cancel() {
        try { nm.cancel(ID) } catch (_: Throwable) { }
    }

    private companion object {
        const val CH_TIMER = "rest_timer"
        const val CH_DONE = "rest_done"
        const val ID = 4201
    }
}
