package com.example.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.AiRepository
import com.example.ui.theme.AppThemeStyle
import com.example.ui.theme.ReaderThemes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentAppTheme: AppThemeStyle,
    onThemeChange: (AppThemeStyle) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val aiRepository = AiRepository()

    var selectedFont by remember { mutableStateOf("Inter / Sans-serif") }
    var autoNightMode by remember { mutableStateOf(true) }
    var useExternalReader by remember { mutableStateOf(false) }

    // Theme Builder state
    var selectedPrimaryColor by remember { mutableStateOf(Color(0xFF6750A4)) }
    var isCustomDark by remember { mutableStateOf(true) }
    var customThemeName by remember { mutableStateOf("My Custom Theme") }
    var customThemeCreated by remember { mutableStateOf(false) }

    var isCleaning by remember { mutableStateOf(false) }
    var cleanedResult by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Custom Theme Builder", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Reader Custom Theme Builder Card (Primary Color Chooser + Complementary + Auto Night/Day Inversion)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reading Theme Builder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Pick a primary color and auto-create a custom reading theme with complementary accents and automatic night/day font-vs-background inversion.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Choose Primary Accent Color:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    val colorOptions = listOf(
                        Pair(Color(0xFF6750A4), "Purple"),
                        Pair(Color(0xFF2E7D32), "Emerald"),
                        Pair(Color(0xFFD32F2F), "Crimson"),
                        Pair(Color(0xFFE65100), "Amber"),
                        Pair(Color(0xFF0288D1), "Cyan"),
                        Pair(Color(0xFFC2185B), "Rose")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        colorOptions.forEach { (color, name) ->
                            FilterChip(
                                selected = selectedPrimaryColor == color,
                                onClick = { selectedPrimaryColor = color },
                                label = { Text(name) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isCustomDark,
                            onCheckedChange = { isCustomDark = it }
                        )
                        Text("Auto Night Mode (Invert font vs background for dark contrast)", style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val newTheme = ReaderThemes.createCustomTheme(selectedPrimaryColor, isCustomDark)
                            ReaderThemes.all.add(newTheme)
                            customThemeCreated = true
                            Toast.makeText(context, "Custom theme '${newTheme.name}' created and added to Reader!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate & Save Custom Reading Theme")
                    }

                    if (customThemeCreated) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Status: Custom theme added successfully to reader theme options!", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // App UI Theme Picker Card (Mocha, Grass, Galaxy, Sci-Fi, Kawaii)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("App Interface Themes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Choose built-in app visual themes: Mocha, Grass, Galaxy, Sci-Fi, and Kawaii.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(16.dp))

                    val themes = listOf(
                        Pair(AppThemeStyle.DEFAULT, "Dynamic Material 3 / Default"),
                        Pair(AppThemeStyle.MOCHA, "Mocha (Warm Coffee & Beige)"),
                        Pair(AppThemeStyle.GRASS, "Grass (Lush Forest Green)"),
                        Pair(AppThemeStyle.GALAXY, "Galaxy (Cosmic Purple & Starry)"),
                        Pair(AppThemeStyle.SCIFI, "Sci-Fi (Cyberpunk Neon Cyan)"),
                        Pair(AppThemeStyle.KAWAII, "Kawaii (Pastel Pink & Soft)")
                    )

                    themes.forEach { (style, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onThemeChange(style) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentAppTheme == style,
                                onClick = { onThemeChange(style) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Typography & Google Fonts Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Google Fonts Typography", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    listOf("Inter / Sans-serif", "Lora / Serif", "JetBrains Mono / Monospace", "Merriweather").forEach { fontName ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedFont = fontName }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedFont == fontName,
                                onClick = { selectedFont = fontName }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(fontName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // External Reader Integration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("External Reader Integration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Open books in external PDF/EPUB readers (Librera Reader, Moon+ Reader). Reading stats are safely truncated to launch time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = useExternalReader,
                            onCheckedChange = { useExternalReader = it }
                        )
                        Text("Prefer External Reader App for EPUB/PDF", style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse("content://com.example.provider/sample"), "application/epub+zip")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Launching external reader intent (Librera/Moon+)", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Test External Reader Launch & Stats Truncation")
                    }
                }
            }

            // Calibre AI Metadata Cleaner Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Calibre AI Metadata Cleaner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Clean up poorly formatted book titles, authors, and missing tags using Gemini AI.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isCleaning = true
                                cleanedResult = aiRepository.cleanMetadata(
                                    title = "the omniscient reader viewpoint vol 1 [uncensored]",
                                    author = "sing-shong & team",
                                    description = "raw web novel translation ch 1-50"
                                )
                                isCleaning = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isCleaning) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Run AI Metadata Cleanup on Sample Book")
                        }
                    }

                    if (cleanedResult != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = cleanedResult!!,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
