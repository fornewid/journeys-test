package io.github.fornewid.journeys.sample

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SampleColors = lightColorScheme(
    primary = Color(0xFF415F91),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF565F71),
    background = Color(0xFFF9F9FF),
    surface = Color(0xFFF9F9FF),
    surfaceContainer = Color(0xFFEDEEF4),
    onSurface = Color(0xFF1A1B20),
    onSurfaceVariant = Color(0xFF44474E),
)

@Composable
fun SampleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SampleColors,
        content = content,
    )
}
