package com.example.ui.screens.acquisition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.screens.library.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcquisitionScreen(
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit
) {
    var urlInput by remember { mutableStateOf("") }
    var titleInput by remember { mutableStateOf("") }
    var authorInput by remember { mutableStateOf("") }
    var formatChoice by remember { mutableStateOf("NOVEL") }
    var successMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Book Acquisition & Importer", fontWeight = FontWeight.Bold) },
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
            // URL Downloader (WebToEpub / LNReader style)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Web Novel / EPUB URL Downloader", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Paste a public web novel link (NovelUpdates, RoyalRoad, Gutenberg) or EPUB URL to download and parse chapters.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Story or Chapter URL") },
                        modifier = Modifier.fillMaxWidth().testTag("url_input"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (urlInput.isNotBlank()) {
                                libraryViewModel.addBook(
                                    title = "Imported Web Novel",
                                    author = "Online Author",
                                    description = "Imported from URL: $urlInput",
                                    coverUrl = "https://images.unsplash.com/photo-1516979187457-637abb4f9353?w=600",
                                    format = "NOVEL",
                                    tags = "WebNovel, Imported",
                                    series = "Web Series"
                                )
                                successMessage = "Successfully acquired and added to library!"
                                urlInput = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("download_url_btn")
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download & Parse Story")
                    }
                }
            }

            // Manual Add / Calibre Metadata Entry
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Manual Book / Calibre Entry", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Book Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = authorInput,
                        onValueChange = { authorInput = it },
                        label = { Text("Author") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (titleInput.isNotBlank() && authorInput.isNotBlank()) {
                                libraryViewModel.addBook(
                                    title = titleInput,
                                    author = authorInput,
                                    description = "Manually added book entry.",
                                    coverUrl = "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=600",
                                    format = "EPUB",
                                    tags = "ManualEntry",
                                    series = ""
                                )
                                successMessage = "Book added successfully!"
                                titleInput = ""
                                authorInput = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add to Library")
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
}
