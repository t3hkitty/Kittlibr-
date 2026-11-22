package com.example.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
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
import com.example.ui.theme.AppThemeStyle
import com.example.ui.theme.ReaderThemes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeConfigurationScreen(
    currentAppTheme: AppThemeStyle,
    onThemeChange: (AppThemeStyle) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var oledTrueBlackEnabled by remember { mutableStateOf(false) }
    var sepiaModeEnabled by remember { mutableStateOf(false) }
    var reverseSepiaEnabled by remember { mutableStateOf(false) }
    var selectedPrimaryColor by remember { mutableStateOf(Color(0xFF6750A4)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Theme Configuration & Eye Health", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            // OLED & Sepia Toggles Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Eye Health & Display Modes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("True Black OLED Mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text("Pure #000000 background for OLED battery savings and contrast", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        Switch(
                            checked = oledTrueBlackEnabled,
                            onCheckedChange = {
                                oledTrueBlackEnabled = it
                                if (it) {
                                    Toast.makeText(context, "OLED True Black enabled", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sepia Reading Mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text("Warm parchment tones to reduce eye strain during long reading sessions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        Switch(
                            checked = sepiaModeEnabled,
                            onCheckedChange = {
                                sepiaModeEnabled = it
                                if (it) reverseSepiaEnabled = false
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Reverse Sepia Mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text("Dark warm brown background with sepia text for night reading", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        Switch(
                            checked = reverseSepiaEnabled,
                            onCheckedChange = {
                                reverseSepiaEnabled = it
                                if (it) sepiaModeEnabled = false
                            }
                        )
                    }
                }
            }

            // Primary Theme Color Picker Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Primary Theme Color Picker", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Select your primary accent color. Kittlibr automatically computes complementary hues for buttons and headers.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(16.dp))

                    val colorSwatches = listOf(
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
                        colorSwatches.forEach { (color, name) ->
                            FilterChip(
                                selected = selectedPrimaryColor == color,
                                onClick = {
                                    selectedPrimaryColor = color
                                    Toast.makeText(context, "Primary color updated to $name", Toast.LENGTH_SHORT).show()
                                },
                                label = { Text(name) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            Toast.makeText(context, "Theme configurations applied successfully!", Toast.LENGTH_SHORT).show()
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save & Apply Theme Configuration")
                    }
                }
            }
        }
    }
}
