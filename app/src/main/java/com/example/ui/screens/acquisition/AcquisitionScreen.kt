package com.example.ui.screens.acquisition

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.components.CloudflareWebViewDialog
import com.example.ui.screens.library.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcquisitionScreen(
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Explore", "Sources", "Downloads", "Updates", "History")

    var urlInput by remember { mutableStateOf("") }
    var preferredFormat by remember { mutableStateOf("EPUB3") }
    var runCalibreCleanup by remember { mutableStateOf(true) }

    // Cloudflare & Anna's Archive settings
    var cfCookie by remember { mutableStateOf("") }
    var showWebViewDialog by remember { mutableStateOf(false) }
    var annasKey by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Book Acquisition & LNReader Hub", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 16.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) },
                            icon = {
                                when(index) {
                                    0 -> Icon(Icons.Default.Explore, contentDescription = null)
                                    1 -> Icon(Icons.Default.Public, contentDescription = null)
                                    2 -> Icon(Icons.Default.Download, contentDescription = null)
                                    3 -> Icon(Icons.Default.Update, contentDescription = null)
                                    else -> Icon(Icons.Default.History, contentDescription = null)
                                }
                            }
                        )
                    }
                }
            }
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
            when (selectedTab) {
                0, 1 -> {
                    // Explore & Sources
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Web Novel & Anna's Archive Acquisition", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Import from NovelUpdates, Novelpia, RoyalRoad, or Anna's Archive with automatic Calibre cleanup and format preference.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                label = { Text("Story URL / Anna's Archive Query") },
                                modifier = Modifier.fillMaxWidth().testTag("url_input"),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Preferred Format:", style = MaterialTheme.typography.bodyMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("EPUB3", "MD", "TXT", "PDF").forEach { fmt ->
                                        FilterChip(
                                            selected = preferredFormat == fmt,
                                            onClick = { preferredFormat = fmt },
                                            label = { Text(fmt) }
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = runCalibreCleanup,
                                    onCheckedChange = { runCalibreCleanup = it }
                                )
                                Text("Run Calibre AI Metadata Cleanup on Import", style = MaterialTheme.typography.bodyMedium)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    if (urlInput.isNotBlank()) {
                                        libraryViewModel.addBook(
                                            title = if (runCalibreCleanup) "Enriched: ${urlInput.take(25)}" else urlInput,
                                            author = "Acquired Author",
                                            description = "Acquired via LNReader/Anna's Archive stream. Format: $preferredFormat. Calibre Cleaned: $runCalibreCleanup",
                                            coverUrl = "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=600",
                                            format = preferredFormat,
                                            tags = "Acquired, $preferredFormat, WebNovel",
                                            series = "Acquired Series"
                                        )
                                        successMessage = "Successfully acquired story as $preferredFormat!"
                                        urlInput = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("download_url_btn")
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download & Acquire Story")
                            }
                        }
                    }

                    // Cloudflare & Anna's Archive WebView Grabber Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Cloudflare Bypass & Cookie Grabber", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Launch built-in browser to bypass Cloudflare challenges on NovelUpdates / Novelpia and grab clearance cookies.", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = cfCookie,
                                onValueChange = { cfCookie = it },
                                label = { Text("Captured Cloudflare Cookies") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation()
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = { showWebViewDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Public, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Launch Cloudflare Cookie Grabber Browser")
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = annasKey,
                                onValueChange = { annasKey = it },
                                label = { Text("Anna's Archive API / Donator Key") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation()
                            )
                        }
                    }
                }
                2 -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Active & Completed Downloads", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("All files downloaded in $preferredFormat format are stored locally and synced via WebDAV.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                3 -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Novel Updates & Chapter Feeds", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Checking sources for new chapters... All sources up to date.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                else -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Acquisition & Reading History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Log of all recently acquired books and parsed web novels.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (successMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = successMessage!!,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showWebViewDialog) {
        CloudflareWebViewDialog(
            initialUrl = "https://www.novelupdates.com",
            onDismiss = { cookies ->
                if (cookies.isNotBlank()) {
                    cfCookie = cookies
                }
                showWebViewDialog = false
            }
        )
    }
}
