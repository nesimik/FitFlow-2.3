package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.geometry.Size
import com.example.ui.theme.fit
import com.example.ui.theme.mono
import com.example.ui.theme.overline

/**
 * Yan menüyü açan eylem. FitFlowApp tarafından sağlanır; ana sekmelerdeki
 * başlıklar bunu görünce otomatik olarak hamburger butonu gösterir.
 */
val LocalMenuAction = staticCompositionLocalOf<(() -> Unit)?> { null }

/* --------------------------------- Kartlar --------------------------------- */

@Composable
fun FitCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    corner: Dp = 22.dp,
    container: Color = MaterialTheme.colorScheme.surface,
    border: Color = MaterialTheme.fit.cardBorder,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val base = modifier
        .clip(RoundedCornerShape(corner))
        .background(container)
        .border(1.dp, border, RoundedCornerShape(corner))
    Column(
        modifier = (if (onClick != null) base.clickable { onClick() } else base).padding(contentPadding),
        content = content
    )
}

@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    colors: List<Color>,
    corner: Dp = 24.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val base = modifier
        .clip(RoundedCornerShape(corner))
        .background(Brush.linearGradient(colors))
    Column(
        modifier = (if (onClick != null) base.clickable { onClick() } else base).padding(contentPadding),
        content = content
    )
}

/* -------------------------------- Başlıklar -------------------------------- */

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
            }
        }
        trailing()
    }
}

@Composable
fun OverlineText(text: String, color: Color = MaterialTheme.fit.muted, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(java.util.Locale("tr")),
        style = MaterialTheme.typography.labelSmall.overline(),
        color = color,
        modifier = modifier
    )
}

/* ------------------------------- İstatistik -------------------------------- */

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.fit.accent,
    caption: String? = null,
    captionColor: Color = MaterialTheme.fit.muted,
    onClick: (() -> Unit)? = null
) {
    FitCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) {
                Box(
                    Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(tint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp)) }
            }
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.fit.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall.mono().copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!caption.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(caption, style = MaterialTheme.typography.labelMedium, color = captionColor, maxLines = 1)
        }
    }
}

/* ---------------------------------- Rozet ---------------------------------- */

@Composable
fun Badge(
    text: String,
    color: Color = MaterialTheme.fit.accent,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    icon: ImageVector? = null
) {
    Surface(
        color = if (filled) color else color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) Icon(icon, null, tint = if (filled) Color.White else color, modifier = Modifier.size(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = if (filled) Color.White else color,
                maxLines = 1
            )
        }
    }
}

/* --------------------------------- Sekmeler -------------------------------- */

@Composable
fun PillTabs(
    items: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.fit.elevated)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEachIndexed { i, label ->
            val active = i == selected
            val bg by animateColorAsState(
                if (active) MaterialTheme.fit.accent else Color.Transparent,
                tween(180), label = "pill"
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(bg)
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.fit.onAccent else MaterialTheme.fit.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.fit.accent
) {
    // Tasarım: seçili çip dolu değil, vurgu renginde ince çerçeveli ve hafif tonlu.
    Surface(
        shape = RoundedCornerShape(17.dp),
        color = if (selected) color.copy(alpha = 0.14f) else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) color.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
        modifier = modifier.clickable { onClick() }
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = if (selected) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            maxLines = 1
        )
    }
}

/* ------------------------------- Boş durumlar ------------------------------ */

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(MaterialTheme.fit.accent.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.fit.accent.copy(alpha = 0.85f), modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.fit.muted, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            AccentButton(actionLabel, onClick = onAction)
        }
    }
}

/* --------------------------------- Butonlar -------------------------------- */

@Composable
fun AccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = MaterialTheme.fit.accent
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = com.example.ui.theme.onColorFor(color)
        )
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = MaterialTheme.fit.muted
) {
    Surface(
        shape = RoundedCornerShape(15.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .height(50.dp)
            .clickable { onClick() }
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(18.dp), tint = color)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.titleMedium, color = color)
        }
    }
}

/* ------------------------------ Giriş alanları ----------------------------- */

@Composable
fun FitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    trailing: @Composable (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        placeholder = if (placeholder.isBlank()) null else {
            { Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.fit.muted) }
        },
        singleLine = singleLine,
        minLines = minLines,
        trailingIcon = trailing,
        leadingIcon = leading,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.fit.accent,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.fit.accent,
            cursorColor = MaterialTheme.fit.accent
        )
    )
}

/** Ağırlık/tekrar gibi sayısal değerler için artı-eksi kontrollü alan. */
@Composable
fun StepperField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onStep: (Float) -> Unit,
    step: Float,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.fit.accent
) {
    Column(modifier) {
        OverlineText(label)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundIconButton(Icons.Default.Remove, accent) { onStep(-step) }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = accent
                )
            )
            RoundIconButton(Icons.Default.Add, accent) { onStep(step) }
        }
    }
}

@Composable
fun RoundIconButton(
    icon: ImageVector,
    tint: Color = MaterialTheme.fit.accent,
    size: Dp = 42.dp,
    background: Color = tint.copy(alpha = 0.14f),
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 2))
            .background(background)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun LabeledSwitch(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.fit.accent,
                checkedThumbColor = MaterialTheme.fit.onAccent
            )
        )
    }
}

/* -------------------------------- İlerleme --------------------------------- */

@Composable
fun ThinProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.fit.accent,
    track: Color = MaterialTheme.fit.elevated,
    height: Dp = 8.dp
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(400), label = "prog")
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = track, size = size, cornerRadius = r)
        if (p > 0f) {
            drawRoundRect(
                color = color,
                size = Size((size.width * p).coerceAtLeast(size.height), size.height),
                cornerRadius = r
            )
        }
    }
}

/** Ortasında içerik gösterebilen halka ilerleme göstergesi. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    stroke: Dp = 10.dp,
    color: Color = MaterialTheme.fit.accent,
    track: Color = MaterialTheme.fit.elevated,
    content: @Composable () -> Unit = {}
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(500), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val inset = sw / 2f
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * p,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
        }
        content()
    }
}

/* -------------------------------- Diyaloglar ------------------------------- */

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "Evet",
    dismissLabel: String = "Vazgeç",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.fit.muted) },
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.fit.danger else MaterialTheme.fit.accent,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, color = MaterialTheme.fit.muted, style = MaterialTheme.typography.titleMedium)
            }
        }
    )
}

/* ------------------------------ Küçük yardımcı ----------------------------- */

@Composable
fun KeyValueRow(key: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.fit.muted)
        Text(value, style = MaterialTheme.typography.titleSmall, color = valueColor)
    }
}

@Composable
fun DotDivider() {
    Text("·", color = MaterialTheme.fit.muted, style = MaterialTheme.typography.bodySmall)
}

@Composable
fun InfoNote(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.fit.accent.copy(alpha = 0.07f))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Info, null, tint = MaterialTheme.fit.accent, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fit.muted)
    }
}

@Composable
fun rememberNoRipple(): MutableInteractionSource = remember { MutableInteractionSource() }

@Composable
fun CheckCircle(
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    color: Color = MaterialTheme.fit.success
) {
    // Tasarım 1d: işaretsizken koyu dolgu + soluk tik, işaretliyken yeşil dolgu + koyu tik.
    val bg by animateColorAsState(if (checked) color else MaterialTheme.fit.elevated, tween(160), label = "chk")
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(bg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Check,
            null,
            tint = if (checked) Color(0xFF062017) else Color(0xFF6B7483),
            modifier = Modifier.size(size * 0.55f)
        )
    }
}
