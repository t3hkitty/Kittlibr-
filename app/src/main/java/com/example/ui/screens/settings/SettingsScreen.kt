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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.AiRepository
import com.example.ui.screens.library.LibraryViewModel
import com.example.ui.theme.AppThemeStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    libraryViewModel: LibraryViewModel,
    currentAppTheme: AppThemeStyle,
    onThemeChange: (AppThemeStyle) -> Unit,
    onOpenThemeConfig: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val aiRepository = AiRepository()
    val longPressAction by libraryViewModel.longPressAction.collectAsState()

    var selectedFont by remember { mutableStateOf("Inter / Sans-serif") }
    var useExternalReader by remember { mutableStateOf(false) }

    var isCleaning by remember { mutableStateOf(false) }
    var cleanedResult by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Preferences", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Open Navigation Drawer")
                    }
                },
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
            // Theme Configuration Screen Launcher Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                onClick = onOpenThemeConfig
            ) {
                Row(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Advanced Theme Configuration", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("OLED True Black, Sepia/Reverse Sepia, & Color Picker", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            // Library Tap & Hold Press Configuration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Library Tap & Hold Press Settings", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tap opens the 'More Info' modal (choose how to read, external reader, TTS, or Markdown export). Configure what long press (hold press) does:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(16.dp))

                    val options = listOf(
                        Pair("auto_open", "Auto Open Book (Instant Reader Launch)"),
                        Pair("librmanage_edit", "LibrManage Edit (Metadata Editor)")
                    )

                    options.forEach { (actionKey, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { libraryViewModel.setLongPressAction(actionKey) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = longPressAction == actionKey,
                                onClick = { libraryViewModel.setLongPressAction(actionKey) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // App UI Theme Picker Card (Mocha, Grass, Galaxy, Sci-Fi, Kawaii)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("App Interface Themes", style = MaterialTheme.typography.titleMedium)
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
                    Text("Google Fonts Typography", style = MaterialTheme.typography.titleMedium)
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
                    Text("External Reader Integration", style = MaterialTheme.typography.titleMedium)
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
                    Text("Calibre AI Metadata Cleaner", style = MaterialTheme.typography.titleMedium)
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
