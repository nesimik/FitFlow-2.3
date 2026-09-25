package com.example.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/* ------------------------------- Renk paleti ------------------------------- */

object Palette {
    // Koyu tema — derin gece mavisi/kömür
    val bgDark = Color(0xFF0A0D13)
    val bgAmoled = Color(0xFF000000)
    val surfaceDark = Color(0xFF141A23)
    val surfaceDark2 = Color(0xFF1C2430)
    val outlineDark = Color(0xFF2B3543)
    val onDark = Color(0xFFF2F5F9)
    val onDarkMuted = Color(0xFF93A0B4)

    // Açık tema
    val bgLight = Color(0xFFF4F6F9)
    val surfaceLight = Color(0xFFFFFFFF)
    val surfaceLight2 = Color(0xFFEBEFF5)
    val outlineLight = Color(0xFFD8DFE9)
    val onLight = Color(0xFF0D141F)
    val onLightMuted = Color(0xFF5B6878)

    // Anlam renkleri
    val success = Color(0xFF22C55E)
    val warning = Color(0xFFF59E0B)
    val danger = Color(0xFFEF4444)
    val info = Color(0xFF3B82F6)
    val gold = Color(0xFFFBBF24)
    val violet = Color(0xFF8B5CF6)

    /** Kas grubu renkleri — grafiklerde ve rozet/etiketlerde kullanılır. */
    fun muscle(group: String): Color = when (group) {
        "Göğüs" -> Color(0xFFF43F5E)
        "Sırt" -> Color(0xFF8B5CF6)
        "Bacak" -> Color(0xFF22C55E)
        "Omuz" -> Color(0xFFF59E0B)
        "Kol" -> Color(0xFF3B82F6)
        "Karın" -> Color(0xFF14B8A6)
        "Kardiyo" -> Color(0xFFEC4899)
        else -> Color(0xFF94A3B8)
    }

    val accentPresets = listOf(
        "Buz Mavisi" to "#22D3EE",
        "Elektrik" to "#3B82F6",
        "Lime" to "#A3E635",
        "Zümrüt" to "#10B981",
        "Turuncu" to "#F97316",
        "Kızıl" to "#EF4444",
        "Menekşe" to "#8B5CF6",
        "Şeftali" to "#FB7185",
        "Altın" to "#FBBF24",
        "Mercan" to "#F472B6"
    )
}

fun parseHex(hex: String, fallback: Color = Color(0xFF22D3EE)): Color = try {
    val clean = if (hex.startsWith("#")) hex else "#$hex"
    Color(android.graphics.Color.parseColor(clean))
} catch (t: Throwable) {
    fallback
}

fun Color.toHex(): String {
    val argb = android.graphics.Color.argb(
        (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
    )
    return String.format("#%06X", 0xFFFFFF and argb)
}

/** Arka plana göre okunaklı yazı rengi seçer. */
fun onColorFor(bg: Color): Color {
    val l = 0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue
    return if (l > 0.6f) Color(0xFF0D141F) else Color.White
}

/* --------------------------- Ek renk taşıyıcıları --------------------------- */

data class FitColors(
    val accent: Color,
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val gold: Color,
    val muted: Color,
    val cardBorder: Color,
    val elevated: Color,
    val isDark: Boolean
)

val LocalFitColors = staticCompositionLocalOf {
    FitColors(
        accent = Color(0xFF22D3EE), onAccent = Color.Black, success = Palette.success,
        warning = Palette.warning, danger = Palette.danger, gold = Palette.gold,
        muted = Palette.onDarkMuted, cardBorder = Palette.outlineDark,
        elevated = Palette.surfaceDark2, isDark = true
    )
}

/* -------------------------------- Tipografi -------------------------------- */

private fun buildTypography(scale: Float): Typography {
    val f = FontFamily.Default
    fun sp(v: Float) = (v * scale).sp
    return Typography(
        displayLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.Black, fontSize = sp(48f), lineHeight = sp(52f), letterSpacing = (-1.5).sp),
        displayMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.Black, fontSize = sp(38f), lineHeight = sp(42f), letterSpacing = (-1).sp),
        displaySmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Black, fontSize = sp(30f), lineHeight = sp(34f), letterSpacing = (-0.6).sp),
        headlineLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.ExtraBold, fontSize = sp(26f), lineHeight = sp(31f), letterSpacing = (-0.5).sp),
        headlineMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.ExtraBold, fontSize = sp(22f), lineHeight = sp(27f), letterSpacing = (-0.3).sp),
        headlineSmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = sp(19f), lineHeight = sp(24f)),
        titleLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = sp(17f), lineHeight = sp(22f)),
        titleMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = sp(15f), lineHeight = sp(20f)),
        titleSmall = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = sp(13f), lineHeight = sp(17f)),
        bodyLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = sp(15f), lineHeight = sp(22f)),
        bodyMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = sp(13.5f), lineHeight = sp(19f)),
        bodySmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = sp(12f), lineHeight = sp(16f)),
        labelLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = sp(13f), lineHeight = sp(17f)),
        labelMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = sp(11.5f), lineHeight = sp(15f), letterSpacing = 0.2.sp),
        labelSmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = sp(10f), lineHeight = sp(13f), letterSpacing = 0.6.sp)
    )
}

/* ---------------------------------- Tema ----------------------------------- */

@Composable
fun FitFlowTheme(
    themeMode: String = "dark",
    accentHex: String = "#22D3EE",
    amoled: Boolean = false,
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    val accent = parseHex(accentHex)
    val onAccent = onColorFor(accent)

    val scheme = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent.copy(alpha = 0.18f),
            onPrimaryContainer = accent,
            secondary = Palette.info,
            onSecondary = Color.White,
            tertiary = Palette.violet,
            background = if (amoled) Palette.bgAmoled else Palette.bgDark,
            onBackground = Palette.onDark,
            surface = if (amoled) Color(0xFF0B0B0D) else Palette.surfaceDark,
            onSurface = Palette.onDark,
            surfaceVariant = if (amoled) Color(0xFF141417) else Palette.surfaceDark2,
            onSurfaceVariant = Palette.onDarkMuted,
            outline = Palette.outlineDark,
            outlineVariant = Palette.outlineDark.copy(alpha = 0.5f),
            error = Palette.danger
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent.copy(alpha = 0.16f),
            onPrimaryContainer = accent,
            secondary = Palette.info,
            onSecondary = Color.White,
            tertiary = Palette.violet,
            background = Palette.bgLight,
            onBackground = Palette.onLight,
            surface = Palette.surfaceLight,
            onSurface = Palette.onLight,
            surfaceVariant = Palette.surfaceLight2,
            onSurfaceVariant = Palette.onLightMuted,
            outline = Palette.outlineLight,
            outlineVariant = Palette.outlineLight.copy(alpha = 0.6f),
            error = Palette.danger
        )
    }

    val fit = FitColors(
        accent = accent,
        onAccent = onAccent,
        success = Palette.success,
        warning = Palette.warning,
        danger = Palette.danger,
        gold = Palette.gold,
        muted = if (dark) Palette.onDarkMuted else Palette.onLightMuted,
        cardBorder = if (dark) Palette.outlineDark else Palette.outlineLight,
        elevated = if (dark) (if (amoled) Color(0xFF141417) else Palette.surfaceDark2) else Palette.surfaceLight2,
        isDark = dark
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context.findActivity())?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalFitColors provides fit) {
        MaterialTheme(
            colorScheme = scheme,
            typography = buildTypography(fontScale),
            content = content
        )
    }
}

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Kısayol: MaterialTheme.fit */
val MaterialTheme.fit: FitColors
    @Composable get() = LocalFitColors.current
