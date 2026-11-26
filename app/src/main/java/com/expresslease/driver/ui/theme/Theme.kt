package com.expresslease.driver.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ExpressLeaseDarkColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),       // Sky 400
    onPrimary = Color(0xFF082F49),
    primaryContainer = Color(0xFF0369A1),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = Color(0xFF34D399),     // Emerald 400
    onSecondary = Color(0xFF022C22),
    background = Color(0xFF0F172A),    // Slate 900
    onBackground = Color.White,
    surface = Color(0xFF1E293B),       // Slate 800
    onSurface = Color.White,
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8),
    error = Color(0xFFF87171),         // Red 400
    onError = Color(0xFF7F1D1D),
    outline = Color(0xFF334155),
)

@Composable
fun ExpressLeaseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ExpressLeaseDarkColorScheme,
        typography = Typography(),
        content = content,
    )
}
