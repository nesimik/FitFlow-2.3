package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.core.trimNum
import com.example.ui.theme.fit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/* ==========================================================================
 * Gelişim fotoğrafları
 *
 * Fotoğraflar uygulamanın özel klasörüne (files/progress_photos) küçültülmüş ve
 * döndürmesi düzeltilmiş JPEG olarak kopyalanır; bilgileri index.json'da tutulur.
 * Veritabanına dokunulmaz. Not: JSON yedeğe dahil değildir.
 * ========================================================================== */

data class ProgressPhoto(val id: Long, val dateMillis: Long, val pose: String, val file: String, val weightKg: Float)

object ProgressPhotoStore {
    val POSES = listOf("Ön", "Yan", "Arka")

    private fun dir(ctx: Context) = File(ctx.filesDir, "progress_photos").apply { mkdirs() }
    private fun index(ctx: Context) = File(dir(ctx), "index.json")
    fun fileOf(ctx: Context, p: ProgressPhoto) = File(dir(ctx), p.file)

    fun list(ctx: Context): List<ProgressPhoto> = runCatching {
        val f = index(ctx)
        if (!f.exists()) return emptyList()
        val arr = JSONArray(f.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ProgressPhoto(o.getLong("id"), o.getLong("date"), o.getString("pose"), o.getString("file"), o.optDouble("kg", 0.0).toFloat())
        }.filter { File(dir(ctx), it.file).exists() }.sortedByDescending { it.dateMillis }
    }.getOrDefault(emptyList())

    private fun save(ctx: Context, list: List<ProgressPhoto>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().apply { put("id", p.id); put("date", p.dateMillis); put("pose", p.pose); put("file", p.file); put("kg", p.weightKg.toDouble()) })
        }
        index(ctx).writeText(arr.toString())
    }

    /** Kaynağı okur, döndürmeyi düzeltir, en uzun kenarı 1600 px'e küçültüp kaydeder. */
    fun add(ctx: Context, source: Uri, pose: String, weightKg: Float): ProgressPhoto? = runCatching {
        val tmp = File(ctx.cacheDir, "photo_in.jpg")
        ctx.contentResolver.openInputStream(source)?.use { input -> FileOutputStream(tmp).use { input.copyTo(it) } } ?: return null
        val bmp = decodeScaled(tmp, 1600) ?: return null
        val id = System.currentTimeMillis()
        val name = "p_$id.jpg"
        FileOutputStream(File(dir(ctx), name)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bmp.recycle()
        tmp.delete()
        val photo = ProgressPhoto(id, id, pose, name, weightKg)
        save(ctx, list(ctx) + photo)
        photo
    }.getOrNull()

    fun delete(ctx: Context, p: ProgressPhoto) {
        fileOf(ctx, p).delete()
        save(ctx, list(ctx).filter { it.id != p.id })
    }

    /** Örneklemeli okuma + EXIF yönü. */
    fun decodeScaled(file: File, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val raw = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val deg = runCatching {
            when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        val longest = maxOf(raw.width, raw.height)
        val scale = if (longest > maxPx) maxPx.toFloat() / longest else 1f
        if (deg == 0f && scale == 1f) return raw
        val m = Matrix().apply { postScale(scale, scale); postRotate(deg) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also { if (it !== raw) raw.recycle() }
    }
}

@Composable
private fun PhotoImage(file: File, maxPx: Int, modifier: Modifier) {
    val bmp by produceState<Bitmap?>(null, file.path, maxPx) {
        value = withContext(Dispatchers.IO) { ProgressPhotoStore.decodeScaled(file, maxPx) }
    }
    Box(modifier.background(MaterialTheme.fit.elevated)) {
        bmp?.let {
            Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

private fun dayKey(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun dateLabel(ms: Long) = SimpleDateFormat("d MMM yyyy", Locale("tr")).format(Date(ms))

/** Ölçüm ekranındaki "Gelişim fotoğrafları" kartı. */
@Composable
fun ProgressPhotosCard(currentWeightKg: Float) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var photos by remember { mutableStateOf(ProgressPhotoStore.list(ctx)) }
    var showAdd by remember { mutableStateOf(false) }
    var pose by remember { mutableStateOf(ProgressPhotoStore.POSES.first()) }
    var viewing by remember { mutableStateOf<ProgressPhoto?>(null) }
    var comparing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun ingest(uri: Uri?) {
        if (uri == null) return
        busy = true
        scope.launch {
            withContext(Dispatchers.IO) { ProgressPhotoStore.add(ctx, uri, pose, currentWeightKg) }
            photos = ProgressPhotoStore.list(ctx)
            busy = false
        }
    }

    val camFile = remember { File(ctx.cacheDir, "camera_photo.jpg") }
    val camUri = remember { FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", camFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) ingest(camUri) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> ingest(uri) }

    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Gelişim fotoğrafları", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (photos.isEmpty()) "Aynı ışık ve açıyla, 2-4 haftada bir çek"
                    else "${photos.map { dayKey(it.dateMillis) }.distinct().size} tarih · ${photos.size} fotoğraf",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted
                )
            }
            if (photos.map { dayKey(it.dateMillis) }.distinct().size >= 2) {
                RoundIconButton(Icons.Default.CompareArrows, MaterialTheme.fit.accent, 38.dp, MaterialTheme.fit.accent.copy(alpha = 0.12f)) { comparing = true }
                Spacer(Modifier.width(8.dp))
            }
            RoundIconButton(Icons.Default.AddAPhoto, MaterialTheme.fit.accent, 38.dp, MaterialTheme.fit.accent.copy(alpha = 0.12f)) { showAdd = true }
        }
        if (busy) {
            Spacer(Modifier.height(8.dp))
            Text("Fotoğraf kaydediliyor…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
        }
        if (photos.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                photos.take(24).forEach { p ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PhotoImage(
                            ProgressPhotoStore.fileOf(ctx, p), 360,
                            Modifier.width(84.dp).aspectRatio(3f / 4f).clip(RoundedCornerShape(10.dp)).clickable { viewing = p }
                        )
                        Text(
                            "${p.pose} · " + SimpleDateFormat("d MMM", Locale("tr")).format(Date(p.dateMillis)),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Fotoğraf ekle", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Poz", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.fit.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProgressPhotoStore.POSES.forEach { ChoiceChip(it, pose == it, { pose = it }) }
                    }
                    AccentButton("Kamerayla çek", { showAdd = false; camera.launch(camUri) }, Modifier.fillMaxWidth(), Icons.Default.PhotoCamera)
                    GhostButton("Galeriden seç", {
                        showAdd = false
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, Modifier.fillMaxWidth(), Icons.Default.PhotoLibrary)
                    Text(
                        "Fotoğraflar yalnızca telefonda, uygulamanın içinde saklanır; galeride görünmez ve JSON yedeğe dahil değildir.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Vazgeç", color = MaterialTheme.fit.muted) } }
        )
    }

    viewing?.let { p ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text("${p.pose} · ${dateLabel(p.dateMillis)}" + if (p.weightKg > 0f) " · ${p.weightKg.trimNum()} kg" else "",
                    style = MaterialTheme.typography.titleMedium)
            },
            text = {
                PhotoImage(ProgressPhotoStore.fileOf(ctx, p), 1400, Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(14.dp)))
            },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text("Kapat", color = MaterialTheme.fit.accent) } },
            dismissButton = {
                TextButton(onClick = {
                    ProgressPhotoStore.delete(ctx, p); photos = ProgressPhotoStore.list(ctx); viewing = null
                }) { Text("Sil", color = MaterialTheme.fit.danger) }
            }
        )
    }

    if (comparing) CompareDialog(photos) { comparing = false }
}

/** İki tarihi aynı pozda yan yana karşılaştırır. */
@Composable
private fun CompareDialog(photos: List<ProgressPhoto>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val days = remember(photos) { photos.map { dayKey(it.dateMillis) }.distinct().sorted() }
    var pose by remember { mutableStateOf(ProgressPhotoStore.POSES.first()) }
    var a by remember { mutableIntStateOf(0) }
    var b by remember { mutableIntStateOf(days.lastIndex) }
    LaunchedEffect(days) { a = 0; b = days.lastIndex }

    fun photoFor(dayIdx: Int): ProgressPhoto? {
        val d = days.getOrNull(dayIdx) ?: return null
        val sameDay = photos.filter { dayKey(it.dateMillis) == d }
        return sameDay.firstOrNull { it.pose == pose } ?: sameDay.firstOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Karşılaştır", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProgressPhotoStore.POSES.forEach { ChoiceChip(it, pose == it, { pose = it }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(a to { i: Int -> a = i }, b to { i: Int -> b = i }).forEach { (idx, set) ->
                        val p = photoFor(idx)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (p != null) {
                                PhotoImage(ProgressPhotoStore.fileOf(ctx, p), 900, Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(12.dp)))
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("‹", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.fit.accent,
                                    modifier = Modifier.clickable(enabled = idx > 0) { set(idx - 1) }.padding(horizontal = 8.dp))
                                Text(SimpleDateFormat("d MMM yy", Locale("tr")).format(Date(days[idx])),
                                    style = MaterialTheme.typography.labelMedium)
                                Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.fit.accent,
                                    modifier = Modifier.clickable(enabled = idx < days.lastIndex) { set(idx + 1) }.padding(horizontal = 8.dp))
                            }
                            p?.weightKg?.takeIf { it > 0f }?.let {
                                Text("${it.trimNum()} kg", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fit.muted)
                            }
                        }
                    }
                }
                val wa = photoFor(a)?.weightKg ?: 0f
                val wb = photoFor(b)?.weightKg ?: 0f
                val dayDiff = ((days.getOrElse(b) { 0L } - days.getOrElse(a) { 0L }) / 86_400_000L).toInt()
                Text(
                    "$dayDiff gün arayla" + if (wa > 0f && wb > 0f) " · " + (if (wb >= wa) "+" else "−") + "${kotlin.math.abs(wb - wa).trimNum()} kg" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat", color = MaterialTheme.fit.accent) } }
    )
}
