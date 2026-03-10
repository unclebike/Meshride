package com.unclebike.meshride.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val MeshAccent = Color(0xFF2196F3)
private val MeshGreen = Color(0xFF4CAF50)
private val MeshOrange = Color(0xFFFF9800)
private val MeshBgDark = Color(0xFF1A1A1A)
private val MeshSurface = Color(0xFF2C2C2C)

private val DarkColorScheme = darkColorScheme(
    primary = MeshAccent,
    secondary = MeshGreen,
    tertiary = MeshOrange,
    background = MeshBgDark,
    surface = MeshSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
)

private val MeshTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
)

@Composable
fun MeshRideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = MeshTypography,
        content = content,
    )
}
