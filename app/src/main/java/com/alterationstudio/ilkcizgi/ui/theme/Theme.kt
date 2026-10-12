package com.alterationstudio.ilkcizgi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * Uygulamanın renkleri Ui nesnesinde (AppUi.kt). Burası sadece hazır bileşenlerin
 * (ileti kutuları, yazı kutuları, düğmeler) aynı renkleri kullanmasını sağlar.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F7FA8),
    onPrimary = Color.White,
    secondary = Color(0xFF6FD3A0),
    background = Color(0xFFF3F4F0),
    onBackground = Color(0xFF1F232A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F232A),
    surfaceVariant = Color(0xFFE2E4DE),
    onSurfaceVariant = Color(0xFF6B717B),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    error = Color(0xFFD64545)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7CC6E8),
    onPrimary = Color(0xFF10293A),
    secondary = Color(0xFF6FD3A0),
    background = Color(0xFF2B2E34),
    onBackground = Color(0xFFECEEF1),
    surface = Color(0xFF373B42),
    onSurface = Color(0xFFECEEF1),
    surfaceVariant = Color(0xFF4A4F58),
    onSurfaceVariant = Color(0xFF9AA1AB),
    surfaceContainerHigh = Color(0xFF3E424A),
    error = Color(0xFFFF6B6B)
)

@Composable
fun IlkCizgiTheme(
    darkTheme: Boolean = false,
    // Eski çağrılarla uyum için duruyor; uygulama kendi renklerini kullanır
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
