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
