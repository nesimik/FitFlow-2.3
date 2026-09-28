package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.example.core.formatDuration
import com.example.core.formatTonnage
import com.example.core.trimNum
import com.example.data.WorkoutEntity
import com.example.data.WorkoutSetEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Seans paylaşım kartı: 1080×1350 (Instagram dikey) görsel. Android Canvas ile çizilir,
 * önbelleğe PNG olarak yazılır ve paylaşım menüsüyle gönderilir.
 */
object ShareCard {

    data class Line(val name: String, val detail: String, val isPr: Boolean)

    fun share(
        context: Context,
        w: WorkoutEntity,
        sets: List<WorkoutSetEntity>,
        volume: Float,
        prExerciseNames: Set<String>,
        accentArgb: Int
    ) {
        val file = render(context, w, sets, volume, prExerciseNames, accentArgb) ?: return
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Seansı paylaş").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun render(
        context: Context,
        w: WorkoutEntity,
        sets: List<WorkoutSetEntity>,
        volume: Float,
        prExerciseNames: Set<String>,
        accentArgb: Int
    ): File? = runCatching {
        val W = 1080; val H = 1350
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val accent = accentArgb or 0xFF000000.toInt()

        // Arka plan: koyu gradyan + köşede vurgu ışıması
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, H.toFloat(), 0xFF0E131A.toInt(), 0xFF07090D.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(
                W * 0.9f, 0f, 700f, (accent and 0x00FFFFFF) or 0x44000000, 0x00000000, Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), glow)

        fun text(size: Float, color: Int, bold: Boolean = false, mono: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color
            typeface = Typeface.create(if (mono) Typeface.MONOSPACE else Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        val white = 0xFFF1F3F6.toInt()
        val muted = 0xFF8D97A7.toInt()
        val pad = 80f

        // Üst etiket + başlık
        val date = SimpleDateFormat("d MMMM yyyy · EEEE", Locale("tr")).format(Date(w.startedAt))
        c.drawText(date.uppercase(Locale("tr")), pad, 150f, text(30f, accent, bold = true, mono = true))
        val titlePaint = text(72f, white, bold = true)
        c.drawText(ellipsize(w.title, titlePaint, W - 2 * pad), pad, 240f, titlePaint)

        // Üç büyük istatistik
        val working = sets.filter { it.isCompleted && !it.isWarmup }
        val stats = listOf(
            "SÜRE" to formatDuration(w.durationSeconds),
            "HACİM" to formatTonnage(volume),
            "SET" to "${working.size}"
        )
        val boxW = (W - 2 * pad - 2 * 24f) / 3f
        stats.forEachIndexed { i, (label, value) ->
            val x = pad + i * (boxW + 24f)
            val r = RectF(x, 300f, x + boxW, 470f)
            c.drawRoundRect(r, 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF161C25.toInt() })
            c.drawText(label, x + 30f, 355f, text(26f, muted, bold = true, mono = true))
            val vp = text(54f, white, bold = true, mono = true)
            c.drawText(ellipsize(value, vp, boxW - 50f), x + 30f, 435f, vp)
        }

        // Hareketler: en iyi set (en yüksek tahmini 1RM)
        val lines = sets.filter { it.isCompleted && !it.isWarmup }
            .groupBy { it.exerciseOrder }.values
            .map { ex ->
                val best = ex.maxByOrNull { it.weightKg * (1f + it.reps / 30f) } ?: ex.first()
                val detail = when {
                    best.durationSeconds > 0 -> "${ex.size} × ${best.durationSeconds} sn"
                    best.weightKg > 0f -> "${ex.size} set · ${best.weightKg.trimNum()} kg × ${best.reps}"
                    else -> "${ex.size} set · ${best.reps} tekrar"
                }
                Line(ex.first().exerciseName, detail, ex.first().exerciseName in prExerciseNames)
            }
        var y = 560f
        c.drawText("HAREKETLER", pad, y, text(26f, muted, bold = true, mono = true))
        y += 30f
        val shown = lines.take(7)
        val nameP = text(40f, white, bold = true)
        val detP = text(32f, muted, mono = true)
        shown.forEach { l ->
            y += 82f
            c.drawCircle(pad + 8f, y - 13f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (l.isPr) 0xFFF2C14E.toInt() else accent })
            val nameMax = W - 2 * pad - 40f - detP.measureText(l.detail) - 30f
            c.drawText(ellipsize(l.name, nameP, nameMax), pad + 36f, y, nameP)
            c.drawText(l.detail, W - pad - detP.measureText(l.detail), y, detP)
        }
        if (lines.size > shown.size) {
            y += 62f
            c.drawText("+${lines.size - shown.size} hareket daha", pad + 36f, y, text(30f, muted))
        }

        // Rekorlar
        if (prExerciseNames.isNotEmpty()) {
            val gold = 0xFFF2C14E.toInt()
            val r = RectF(pad, H - 250f, W - pad, H - 160f)
            c.drawRoundRect(r, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (gold and 0x00FFFFFF) or 0x22000000 })
            val prText = "🏆  ${prExerciseNames.size} yeni rekor"
            c.drawText(prText, pad + 32f, H - 192f, text(38f, gold, bold = true))
        }

        // Alt imza
        c.drawText("FitFlow", pad, H - 70f, text(36f, accent, bold = true))
        val f = text(28f, muted)
        val sig = "ile kaydedildi"
        c.drawText(sig, pad + text(36f, accent, bold = true).measureText("FitFlow") + 14f, H - 70f, f)

        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "fitflow-seans.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        file
    }.getOrNull()

    private fun ellipsize(s: String, p: Paint, max: Float): String {
        if (p.measureText(s) <= max) return s
        var t = s
        while (t.isNotEmpty() && p.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }
}
