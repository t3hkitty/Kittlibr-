package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.data.repository.LibraryRepository
import com.example.data.repository.WebDavRepository
import com.example.ui.screens.acquisition.AcquisitionScreen
import com.example.ui.screens.discovery.DiscoveryScreen
import com.example.ui.screens.discovery.DiscoveryViewModel
import com.example.ui.screens.library.LibraryScreen
import com.example.ui.screens.library.LibraryViewModel
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.stats.StatsScreen
import com.example.ui.screens.storage.StorageSyncScreen

@Composable
fun AppScaffold(
    libraryViewModel: LibraryViewModel,
    discoveryViewModel: DiscoveryViewModel,
    libraryRepository: LibraryRepository,
    webDavRepository: WebDavRepository,
    onBookClick: (Long) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val items = listOf("Library", "Discover", "Acquire", "Stats", "Storage", "Settings")
    val icons = listOf(
        Icons.Default.Book,
        Icons.Default.Explore,
        Icons.Default.AddCircle,
        Icons.Default.BarChart,
        Icons.Default.Storage,
        Icons.Default.Settings
    )

    Scaffold(
        bottomBar = {
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
                    onAddClick = { selectedTab = 2 }
                )
                1 -> DiscoveryScreen(viewModel = discoveryViewModel)
                2 -> AcquisitionScreen(
                    libraryViewModel = libraryViewModel,
                    onBack = { selectedTab = 0 }
                )
                3 -> StatsScreen(libraryRepository = libraryRepository)
                4 -> StorageSyncScreen(webDavRepository = webDavRepository)
                5 -> SettingsScreen()
            }
        }
    }
}
