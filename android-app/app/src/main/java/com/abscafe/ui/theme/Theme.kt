package com.abscafe.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Primary = Color(0xFFFF6D4C)
val PrimaryDark = Color(0xFFCC5533)
val Secondary = Color(0xFF2C3E50)
val Background = Color(0xFFFFF8F0)
val Surface = Color(0xFFFFFFFF)
val OnPrimary = Color(0xFFFFFFFF)
val OnSecondary = Color(0xFFFFFFFF)
val OnBackground = Color(0xFF1A1A1A)
val OnSurface = Color(0xFF1A1A1A)
val Available = Color(0xFF4CAF50)
val Occupied = Color(0xFFFF5722)
val PendingColor = Color(0xFFFFA726)
val PreparingColor = Color(0xFF42A5F5)
val ReadyColor = Color(0xFF66BB6A)
val PaidColor = Color(0xFF9E9E9E)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    secondary = Secondary,
    onSecondary = OnSecondary,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface
)

@Composable
fun ABsCafeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography(),
        content = content
    )
}
