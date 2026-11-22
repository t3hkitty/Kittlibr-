package com.example.ui.theme

import androidx.compose.ui.graphics.Color

data class ReaderThemeConfig(
    val name: String,
    val background: Color,
    val surface: Color,
    val text: Color,
    val secondaryText: Color,
    val accent: Color
)

object ReaderThemes {
    val Light = ReaderThemeConfig(
        name = "Default Light",
        background = Color(0xFFFAF9F6),
        surface = Color(0xFFFFFFFF),
        text = Color(0xFF1A1A1A),
        secondaryText = Color(0xFF666666),
        accent = Color(0xFF6750A4)
    )

    val Dark = ReaderThemeConfig(
        name = "Dark",
        background = Color(0xFF121212),
        surface = Color(0xFF1E1E1E),
        text = Color(0xFFE2E2E2),
        secondaryText = Color(0xFFA0A0A0),
        accent = Color(0xFFD0BCFF)
    )

    val Sepia = ReaderThemeConfig(
        name = "Sepia",
        background = Color(0xFFF4ECD8),
        surface = Color(0xFFFAF4E6),
        text = Color(0xFF4A3B32),
        secondaryText = Color(0xFF7A6B62),
        accent = Color(0xFF8B4513)
    )

    val Amoled = ReaderThemeConfig(
        name = "AMOLED Black",
        background = Color(0xFF000000),
        surface = Color(0xFF0D0D0D),
        text = Color(0xFFFFFFFF),
        secondaryText = Color(0xFF888888),
        accent = Color(0xFFBB86FC)
    )

    val PaperGreen = ReaderThemeConfig(
        name = "Paper Green",
        background = Color(0xFFE8F0E8),
        surface = Color(0xFFF2F7F2),
        text = Color(0xFF1C3A1C),
        secondaryText = Color(0xFF4A6B4A),
        accent = Color(0xFF2E7D32)
    )

    val Midnight = ReaderThemeConfig(
        name = "Midnight Blue",
        background = Color(0xFF0B132B),
        surface = Color(0xFF1C2541),
        text = Color(0xFF6FFFE9),
        secondaryText = Color(0xFF5BC0BE),
        accent = Color(0xFFFFD166)
    )

    val all = listOf(Light, Dark, Sepia, Amoled, PaperGreen, Midnight)
}
