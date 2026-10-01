package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 2.30: GitHub "Releases" üzerinden güncelleme.
 * Her başarılı derleme FitFlow.apk dosyasını sürüm olarak yayınlar; uygulama en son sürümü
 * denetler, yeniyse tarayıcıda APK'yı indirir (dokununca kurulum ekranı açılır).
 */
object AppUpdate {
    const val REPO = "nesimik/FitFlow-2.3"
    /** Her zaman en son sürümün APK'sı — telefona yer imi olarak eklenebilir. */
    const val LATEST_APK = "https://github.com/$REPO/releases/latest/download/FitFlow.apk"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases/latest"

    data class Info(val version: String, val notes: String, val apkUrl: String)

    /** En son yayımlanan sürüm; ağ yoksa / hata olursa null. */
    suspend fun latest(): Info? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                if (c.responseCode != 200) return@runCatching null
                val o = org.json.JSONObject(c.inputStream.bufferedReader().readText())
                val tag = o.optString("tag_name").removePrefix("v")
                val assets = o.optJSONArray("assets")
                var url = LATEST_APK
                if (assets != null) for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) { url = a.optString("browser_download_url", url); break }
                }
                if (tag.isBlank()) null else Info(tag, o.optString("body").trim(), url)
            } finally { c.disconnect() }
        }.getOrNull()
    }

    /** "2.30" > "2.29" — noktalı sayısal karşılaştırma. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val l = local.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    fun download(context: Context, url: String = LATEST_APK) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
