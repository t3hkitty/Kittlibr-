package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var selectedTab by remember { mutableIntStateOf(0) }

    val pendingCount by libraryViewModel.pendingMetadataCount.collectAsState()
    val failedCount by libraryViewModel.failedLookupCount.collectAsState()

    val items = listOf("Library", "Discover", "LibrManage", "Acquire", "Stats", "Storage", "Extensions", "Settings")
    val icons = listOf(
        Icons.Default.Book,
        Icons.Default.Explore,
        Icons.Default.Edit,
        Icons.Default.AddCircle,
        Icons.Default.BarChart,
        Icons.Default.Storage,
        Icons.Default.Extension,
        Icons.Default.Settings
    )

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
                                        selectedTab = 0 // jump to Library
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

                NavigationBar(
                    modifier = Modifier.testTag("bottom_nav")
                ) {
                    items.forEachIndexed { index, item ->
                        NavigationBarItem(
                            icon = { Icon(icons[index], contentDescription = item) },
                            label = { Text(item) },
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            modifier = Modifier.testTag("nav_item_$item")
                        )
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
            when (selectedTab) {
                0 -> LibraryScreen(
                    viewModel = libraryViewModel,
                    onBookClick = onBookClick,
                    onAddClick = { selectedTab = 3 }
                )
                1 -> DiscoveryScreen(viewModel = discoveryViewModel)
                2 -> CalibreScreen(viewModel = libraryViewModel)
                3 -> AcquisitionScreen(
                    libraryViewModel = libraryViewModel,
                    onBack = { selectedTab = 0 }
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
