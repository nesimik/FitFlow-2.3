package com.example.ui.components

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppViewModel
import com.example.ui.theme.fit
import com.example.work.FitJobs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ayarlar: otomatik yedek klasörü, antrenman hatırlatıcısı ve haftalık rapor. */
@Composable
fun BackupAndReminderCard(vm: AppViewModel, sections: Set<String> = setOf("backup", "notify", "health")) {
    val s = vm.settings
    val context = LocalContext.current
    val folder by s.backupFolder.collectAsStateWithLifecycle()
    val lastAt by s.lastBackupAt.collectAsStateWithLifecycle()
    val error by s.backupError.collectAsStateWithLifecycle()
    val remindOn by s.reminderOn.collectAsStateWithLifecycle()
    val remindMin by s.reminderMinute.collectAsStateWithLifecycle()
    val reportOn by s.weeklyReportOn.collectAsStateWithLifecycle()
    val healthOn by s.healthConnectOn.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var healthMsg by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val healthAvailable = androidx.compose.runtime.remember { com.example.work.HealthSync.isAvailable(context) }
    val healthPerms = rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(com.example.work.HealthSync.PERMISSIONS)) {
            s.setHealthConnectOn(true)
            healthMsg = "Eşitleniyor…"
            scope.launch { val n = com.example.work.HealthSync.syncAll(context); healthMsg = "$n kayıt gönderildi" }
        } else {
            s.setHealthConnectOn(false)
            healthMsg = "İzin verilmedi"
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            s.setBackupFolder(uri.toString())
            s.setBackupError("")
            FitJobs.backupNow(context)
        }
    }

    FitCard {
        if ("backup" in sections) {
        OverlineText("Otomatik yedek")
        Spacer(Modifier.height(6.dp))
        if (folder.isBlank()) {
            Text(
                "Kapalı. Bir klasör seçersen her antrenmandan sonra ve günde bir kez otomatik yedek alınır; son 10 yedek saklanır. Klasörü Google Drive gibi senkronize bir yere seçersen telefon bozulsa bile verin kalır.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.fit.muted
            )
            Spacer(Modifier.height(10.dp))
            AccentButton("Yedek klasörü seç", { folderPicker.launch(null) }, Modifier.fillMaxWidth(), Icons.Default.FolderOpen)
        } else {
            val name = remember(folder) { folderLabel(folder) }
            KeyValueRow("Klasör", name)
            KeyValueRow(
                "Son yedek",
                if (lastAt <= 0L) "henüz yok"
                else SimpleDateFormat("d MMM HH:mm", Locale("tr")).format(Date(lastAt))
            )
            if (error.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.danger)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentButton("Şimdi yedekle", { FitJobs.backupNow(context) }, Modifier.weight(1f), Icons.Default.Backup)
                GhostButton("Klasör", { folderPicker.launch(null) }, Modifier.weight(1f), Icons.Default.FolderOpen)
            }
            Spacer(Modifier.height(4.dp))
            GhostButton("Otomatik yedeği kapat", { s.setBackupFolder(""); s.setBackupError("") }, Modifier.fillMaxWidth())
        }

        }
        if ("notify" in sections) {
        if (sections.size > 1 && "backup" in sections) Spacer(Modifier.height(16.dp))
        OverlineText("Bildirimler")
        Spacer(Modifier.height(6.dp))
        LabeledSwitch(
            "Antrenman hatırlatıcısı",
            "Programdaki günlerde, antrenman yapılmadıysa",
            remindOn
        ) { s.setReminderOn(it) }
        if (remindOn) {
            GhostButton(
                "Saat: %02d:%02d".format(remindMin / 60, remindMin % 60),
                {
                    TimePickerDialog(context, { _, h, m ->
                        s.setReminderMinute(h * 60 + m)
                        FitJobs.reschedule(context)
                    }, remindMin / 60, remindMin % 60, true).show()
                },
                Modifier.fillMaxWidth(),
                Icons.Default.Schedule
            )
            Spacer(Modifier.height(6.dp))
        }
        LabeledSwitch(
            "Haftalık rapor",
            "Pazartesi: geçen haftanın özeti, eksik kaslar ve bu haftanın hedefi",
            reportOn
        ) { s.setWeeklyReportOn(it) }

        }
        if ("health" in sections) {
        if (sections.size > 1) Spacer(Modifier.height(16.dp))
        OverlineText("Health Connect")
        Spacer(Modifier.height(6.dp))
        if (!healthAvailable) {
            Text(
                "Bu telefonda Health Connect bulunamadı. Play Store'dan \"Health Connect\" uygulamasını kurarsan antrenmanların ve kilon Samsung Health / Google Fit'e aktarılabilir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.fit.muted
            )
        } else {
            LabeledSwitch(
                "Samsung Health / Google Fit eşitlemesi",
                "Biten antrenmanlar ve kilo ölçümleri Health Connect'e yazılır",
                healthOn
            ) { on ->
                if (on) healthPerms.launch(com.example.work.HealthSync.PERMISSIONS)
                else { s.setHealthConnectOn(false); healthMsg = "" }
            }
            if (healthOn) {
                GhostButton("Geçmişi şimdi eşitle", {
                    healthMsg = "Eşitleniyor…"
                    scope.launch { val n = com.example.work.HealthSync.syncAll(context); healthMsg = "$n kayıt gönderildi" }
                }, Modifier.fillMaxWidth())
            }
            if (healthMsg.isNotBlank()) {
                Text(healthMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
            }
        }
        }
    }
}

/** "primary:Documents/FitFlow" → "Documents/FitFlow". */
private fun folderLabel(uri: String): String = runCatching {
    val id = DocumentsContract.getTreeDocumentId(Uri.parse(uri))
    id.substringAfter(':').ifBlank { id }.ifBlank { "Seçili klasör" }
}.getOrDefault("Seçili klasör")
