package com.renotify.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * iOS-inspirierte Systempalette (Human-Interface-Farbwerte nachempfunden,
 * keine Apple-Assets/-Fonts).
 */
data class IosPalette(
    val background: Color,
    val card: Color,
    val label: Color,
    val secondaryLabel: Color,
    val separator: Color,
    val searchFill: Color,
    val blue: Color,
    val red: Color,
    val green: Color,
)

val IosLight = IosPalette(
    background = Color(0xFFF2F2F7),
    card = Color(0xFFFFFFFF),
    label = Color(0xFF000000),
    secondaryLabel = Color(0xFF8A8A8E),
    separator = Color(0xFFC6C6C8),
    searchFill = Color(0xFFE9E9EB),
    blue = Color(0xFF007AFF),
    red = Color(0xFFFF3B30),
    green = Color(0xFF34C759),
)

val IosDark = IosPalette(
    background = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0xFF8D8D93),
    separator = Color(0xFF38383A),
    searchFill = Color(0xFF2C2C2E),
    blue = Color(0xFF0A84FF),
    red = Color(0xFFFF453A),
    green = Color(0xFF30D158),
)

val LocalIosPalette = staticCompositionLocalOf { IosLight }

object IosType {
    val largeTitle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
    val title3 = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    val headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal)
    val subhead = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal)
    val subheadBold = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    val footnote = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal)
    val caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)
}

@Composable
fun ReNotifyTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val palette = if (dark) IosDark else IosLight
    val colorScheme = if (dark) {
        darkColorScheme(
            background = palette.background,
            surface = palette.background,
            primary = palette.blue,
            error = palette.red,
        )
    } else {
        lightColorScheme(
            background = palette.background,
            surface = palette.background,
            primary = palette.blue,
            error = palette.red,
        )
    }
    CompositionLocalProvider(LocalIosPalette provides palette) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
