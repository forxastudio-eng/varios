package com.forxa.pianocascada.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Paleta de diseño: casi negro, una sola fuente de luz (las notas y las teclas). */
object PC {
    val bg = Color(0xFF07070A)
    val surface1 = Color(0xFF111116)
    val surface2 = Color(0xFF1A1A21)
    val surface3 = Color(0xFF23232C)
    val line = Color(0xFF26262F)
    val text1 = Color(0xFFF3F3F6)
    val text2 = Color(0xFF9C9CA8)
    val text3 = Color(0xFF6A6A76)
    val right = Color(0xFF5AB8FF)
    val left = Color(0xFFFFB454)
    val ok = Color(0xFF3DDC97)
    val miss = Color(0xFFFF6B6B)
    val ivory1 = Color(0xFFEDEBE5)
    val ivory2 = Color(0xFFDAD7CF)
}

private val scheme = darkColorScheme(
    primary = PC.text1, onPrimary = PC.bg,
    secondary = PC.right, onSecondary = PC.bg,
    background = PC.bg, onBackground = PC.text1,
    surface = PC.surface1, onSurface = PC.text1,
    surfaceVariant = PC.surface2, onSurfaceVariant = PC.text2,
    outline = PC.line,
)

private val sans = FontFamily.SansSerif
private val typography = Typography(
    displayLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.3.sp),
)

@Composable
fun PianoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
