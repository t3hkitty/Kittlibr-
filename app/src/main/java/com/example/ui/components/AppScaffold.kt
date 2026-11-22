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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    var activeScreenIndex by remember { mutableIntStateOf(0) } // 0: Library, 1: Discover, 2: LibrManage, 3: Acquire, 4: Stats, 5: Storage, 6: Extensions, 7: Settings

    val pendingCount by libraryViewModel.pendingMetadataCount.collectAsState()
    val failedCount by libraryViewModel.failedLookupCount.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Kittlibr Navigation",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Divider()
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Book, contentDescription = null) },
                    label = { Text("Library & Catalog") },
                    selected = activeScreenIndex == 0,
                    onClick = {
                        activeScreenIndex = 0
                        coroutineScope.launch { drawerState.close() }
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Explore, contentDescription = null) },
                    label = { Text("Discovery & Rec Lists") },
                    selected = activeScreenIndex == 1,
                    onClick = {
                        activeScreenIndex = 1
                        coroutineScope.launch { drawerState.close() }
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    label = { Text("LibrManage & Smart Shelves") },
                    selected = activeScreenIndex == 2,
                    onClick = {
                        activeScreenIndex = 2
                        coroutineScope.launch { drawerState.close() }
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.AddCircle, contentDescription = null) },
                    label = { Text("Acquisition & LNReader") },
                    selected = activeScreenIndex == 3,
                    onClick = {
                        activeScreenIndex = 3
                        coroutineScope.launch { drawerState.close() }
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                    label = { Text("Stats & Achievements") },
                    selected = activeScreenIndex == 4,
                    onClick = {
                        activeScreenIndex = 4
                        coroutineScope.launch { drawerState.close() }
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Storage, contentDescription = null) },
                    label = { Text("Storage & Cloud Sync") },
                    selected = activeScreenIndex == 5,
                    onClick = {
                        activeScreenIndex = 5
                        coroutineScope.launch { drawerState.close() }
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Extension, contentDescription = null) },
                    label = { Text("Extensions & Repos") },
                    selected = activeScreenIndex == 6,
                    onClick = {
                        activeScreenIndex = 6
                        coroutineScope.launch { drawerState.close() }
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings & Themes") },
                    selected = activeScreenIndex == 7,
                    onClick = {
                        activeScreenIndex = 7
                        coroutineScope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            bottomBar = {
                // Global Background Process Notification Bar (if pending)
                if (pendingCount > 0 || failedCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Metadata Sync: $pendingCount remaining",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            if (failedCount > 0) {
                                TextButton(
                                    onClick = {
                                        activeScreenIndex = 0
                                        libraryViewModel.setDynamicLibrary(
                                            libraryViewModel.smartShelves.value.find { it.name.contains("Missing Metadata", ignoreCase = true) }
                                                ?: libraryViewModel.presetDynamicLibraries.find { it.missingMetadataOnly }
                                        )
                                    }
                                ) {
                                    Text("View Failed ($failedCount)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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
                when (activeScreenIndex) {
                    0 -> LibraryScreen(
                        viewModel = libraryViewModel,
                        onBookClick = onBookClick,
                        onAddClick = { activeScreenIndex = 3 },
                        onOpenDrawer = { coroutineScope.launch { drawerState.open() } }
                    )
                    1 -> DiscoveryScreen(viewModel = discoveryViewModel)
                    2 -> CalibreScreen(viewModel = libraryViewModel)
                    3 -> AcquisitionScreen(
                        libraryViewModel = libraryViewModel,
                        onBack = { activeScreenIndex = 0 }
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
}
