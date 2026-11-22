package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.LibraryRepository
import com.example.data.repository.WebDavRepository
import com.example.ui.screens.acquisition.AcquisitionScreen
import com.example.ui.screens.calibre.CalibreScreen
import com.example.ui.screens.discovery.DiscoveryScreen
import com.example.ui.screens.discovery.DiscoveryViewModel
import com.example.ui.screens.extensions.ExtensionManagerScreen
import com.example.ui.screens.library.LibraryScreen
import com.example.ui.screens.library.LibraryViewModel
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.stats.StatsScreen
import com.example.ui.screens.storage.StorageSyncScreen
import com.example.ui.theme.AppThemeStyle

data class NavCategory(
    val name: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val panels: List<Pair<String, Int>> // title -> panelIndex
)

@Composable
fun AppScaffold(
    libraryViewModel: LibraryViewModel,
    discoveryViewModel: DiscoveryViewModel,
    libraryRepository: LibraryRepository,
    webDavRepository: WebDavRepository,
    currentAppTheme: AppThemeStyle,
    onThemeChange: (AppThemeStyle) -> Unit,
    onOpenThemeConfig: () -> Unit,
    onBookClick: (Long) -> Unit
) {
    val context = LocalContext.current
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var selectedPanelIndex by remember { mutableIntStateOf(0) }

    // Pinning state for home screen replacement per category
    var pinnedPanels by remember { mutableStateOf(mapOf(0 to 0, 1 to 1, 2 to 4)) } // category -> pinned panelIndex

    val pendingCount by libraryViewModel.pendingMetadataCount.collectAsState()
    val failedCount by libraryViewModel.failedLookupCount.collectAsState()

    val categories = listOf(
        NavCategory(
            name = "Library & Hub",
            icon = Icons.Default.Book,
            panels = listOf(
                Pair("Library", 0),
                Pair("LibrManage", 2),
                Pair("Storage", 5)
            )
        ),
        NavCategory(
            name = "Discovery & Acquisition",
            icon = Icons.Default.Explore,
            panels = listOf(
                Pair("Discover", 1),
                Pair("Acquire", 3),
                Pair("Extensions", 6)
            )
        ),
        NavCategory(
            name = "Insights & Settings",
            icon = Icons.Default.BarChart,
            panels = listOf(
                Pair("Stats", 4),
                Pair("Settings", 7)
            )
        )
    )

    val currentCategory = categories[selectedCategoryIndex]
    val activePanelIndex = pinnedPanels[selectedCategoryIndex] ?: currentCategory.panels.first().second

    Scaffold(
        bottomBar = {
            Column {
                // Global Background Process Notification Bar
                if (pendingCount > 0 || failedCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Column {
                                    Text(
                                        text = "Metadata Import: $pendingCount books pending synchronization",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    if (failedCount > 0) {
                                        Text(
                                            text = "$failedCount lookups failed. Manual review needed.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            if (failedCount > 0 || pendingCount > 0) {
                                TextButton(
                                    onClick = {
                                        selectedCategoryIndex = 0
                                        pinnedPanels = pinnedPanels + (0 to 0)
                                        libraryViewModel.setDynamicLibrary(
                                            libraryViewModel.smartShelves.value.find { it.name.contains("Missing Metadata", ignoreCase = true) }
                                                ?: libraryViewModel.presetDynamicLibraries.find { it.missingMetadataOnly }
                                        )
                                    }
                                ) {
                                    Text("Failed Lookups", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Two-Row Categorized Navigation Bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        // Row 1: Categories
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            categories.forEachIndexed { catIndex, cat ->
                                val isSelected = selectedCategoryIndex == catIndex
                                TextButton(
                                    onClick = {
                                        selectedCategoryIndex = catIndex
                                        // default to pinned panel for this category
                                        selectedPanelIndex = pinnedPanels[catIndex] ?: cat.panels.first().second
                                    }
                                ) {
                                    Icon(cat.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        cat.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Row 2: Dynamic Sub-panels for the selected Category + Pin Home Screen Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                currentCategory.panels.forEach { (panelTitle, panelIndex) ->
                                    val isPanelSelected = activePanelIndex == panelIndex
                                    val isPinned = (pinnedPanels[selectedCategoryIndex] == panelIndex)
                                    
                                    FilterChip(
                                        selected = isPanelSelected,
                                        onClick = { selectedPanelIndex = panelIndex },
                                        label = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (isPinned) {
                                                    Icon(Icons.Default.PushPin, contentDescription = "Pinned", modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                }
                                                Text(panelTitle, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    )
                                }
                            }

                            // Pin / Set as Home Screen Button
                            IconButton(
                                onClick = {
                                    pinnedPanels = pinnedPanels + (selectedCategoryIndex to activePanelIndex)
                                    Toast.makeText(context, "Pinned current panel as default home screen for ${currentCategory.name}!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.BookmarkAdded,
                                    contentDescription = "Pin as category home",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (activePanelIndex) {
                0 -> LibraryScreen(
                    viewModel = libraryViewModel,
                    onBookClick = onBookClick,
                    onAddClick = {
                        selectedCategoryIndex = 1
                        selectedPanelIndex = 3
                    }
                )
                1 -> DiscoveryScreen(viewModel = discoveryViewModel)
                2 -> CalibreScreen(viewModel = libraryViewModel)
                3 -> AcquisitionScreen(
                    libraryViewModel = libraryViewModel,
                    onBack = {
                        selectedCategoryIndex = 0
                        selectedPanelIndex = 0
                    }
                )
                4 -> StatsScreen(libraryRepository = libraryRepository)
                5 -> StorageSyncScreen(webDavRepository = webDavRepository)
                6 -> ExtensionManagerScreen()
                7 -> SettingsScreen(
                    libraryViewModel = libraryViewModel,
                    currentAppTheme = currentAppTheme,
                    onThemeChange = onThemeChange,
                    onOpenThemeConfig = onOpenThemeConfig
                )
            }
        }
    }
}
