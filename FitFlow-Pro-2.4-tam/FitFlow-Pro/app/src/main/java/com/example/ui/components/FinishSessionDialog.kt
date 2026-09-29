package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.theme.fit

/**
 * Açık seansı bitirme seçimi: kaydet ve bitir / kaydetmeden çık / vazgeç.
 * "Kaydetmeden çık" ikinci bir onay ister.
 */
@Composable
fun FinishSessionDialog(
    title: String,
    summary: String?,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(if (confirmDiscard) "Kaydetmeden çıkılsın mı?" else "Seansı bitir", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (confirmDiscard) {
                    Text(
                        "\"$title\" seansındaki tüm setler silinecek, geçmişe kaydedilmeyecek. Bu geri alınamaz.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.fit.muted
                    )
                    AccentButton("Evet, sil ve çık", onDiscard, Modifier.fillMaxWidth(), Icons.Default.DeleteOutline, MaterialTheme.fit.danger)
                } else {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
                    AccentButton("Kaydet ve bitir", onSave, Modifier.fillMaxWidth(), Icons.Default.CheckCircle, MaterialTheme.fit.success)
                    GhostButton("Kaydetmeden çık", { confirmDiscard = true }, Modifier.fillMaxWidth(), Icons.Default.DeleteOutline, MaterialTheme.fit.danger)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { if (confirmDiscard) confirmDiscard = false else onDismiss() }) {
                Text(if (confirmDiscard) "Geri" else "Vazgeç", color = MaterialTheme.fit.muted)
            }
        }
    )
}
