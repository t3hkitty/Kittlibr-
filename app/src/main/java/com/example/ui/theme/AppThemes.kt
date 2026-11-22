package com.example.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class AppThemeStyle {
    DEFAULT, MOCHA, GRASS, GALAXY, SCIFI, KAWAII
}

val MochaColorScheme = darkColorScheme(
    primary = Color(0xFFD7CCC8),
    secondary = Color(0xFF8D6E63),
    tertiary = Color(0xFFBCAAA4),
    background = Color(0xFF1E1B18),
    surface = Color(0xFF2C2723),
    onPrimary = Color(0xFF3E2723),
    onSurface = Color(0xFFEFEBE9)
)

val GrassColorScheme = lightColorScheme(
    primary = Color(0xFF2E7D32),
    secondary = Color(0xFF558B2F),
    tertiary = Color(0xFF81C784),
    background = Color(0xFFF1F8F1),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B5E20)
)

val GalaxyColorScheme = darkColorScheme(
    primary = Color(0xFFE1BEE7),
    secondary = Color(0xFFBA68C8),
    tertiary = Color(0xFF7B1FA2),
    background = Color(0xFF0D071B),
    surface = Color(0xFF18102E),
    onPrimary = Color(0xFF4A148C),
    onSurface = Color(0xFFF3E5F5)
)

val SciFiColorScheme = darkColorScheme(
    primary = Color(0xFF64FFDA),
    secondary = Color(0xFF00BCD4),
    tertiary = Color(0xFF00E5FF),
    background = Color(0xFF050D1A),
    surface = Color(0xFF0A192F),
    onPrimary = Color(0xFF00332C),
    onSurface = Color(0xFFE0F7FA)
)

val KawaiiColorScheme = lightColorScheme(
    primary = Color(0xFFFF69B4),
    secondary = Color(0xFFFFB6C1),
    tertiary = Color(0xFFFFC0CB),
    background = Color(0xFFFFF0F5),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color(0xFFFFFFFF),
    onSurface = Color(0xFF4A2E35)
)
