package com.example.ui.screens.extensions

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

data class ExtensionManifest(
    val id: String,
    val name: String,
    val category: String,
    val version: String,
    val description: String,
    val repoUrl: String,
    var isInstalled: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionManagerScreen() {
    val context = LocalContext.current

    var extensionRepos by remember {
        mutableStateOf(
            listOf("https://raw.githubusercontent.com/kittlibr/kittlibr-extensions/main/repo.json")
        )
    }

    var availableExtensions by remember {
        mutableStateOf(
            listOf(
                ExtensionManifest(
                    id = "librget_lnreader_sources",
                    name = "Librget: All LNReader Sources (NovelUpdates, RoyalRoad, ScribbleHub, WebNovel, FreeWebNovel, LightNovelPub)",
                    category = "Librget",
                    version = "2.5.0",
                    description = "Comprehensive acquisition extension containing all major LNReader sources for web novels and light novels.",
                    repoUrl = "https://github.com/kittlibr/kittlibr-extensions",
                    isInstalled = true
                ),
                ExtensionManifest(
                    id = "librdiscover_goodreads",
                    name = "Librdiscover: Goodreads & Bestsellers Feed",
                    category = "Librdiscover",
                    version = "1.5.0",
                    description = "Discovery extension for Goodreads reading lists, reviews, NYT bestsellers, and custom combination genre feeds.",
                    repoUrl = "https://github.com/kittlibr/kittlibr-extensions",
                    isInstalled = true
                ),
                ExtensionManifest(
                    id = "webview_cookier",
                    name = "WebView Cookier (Cloudflare & Auth)",
                    category = "Utility",
                    version = "1.0.1",
                    description = "Shared cookie grabber utility requested by acquisition extensions to store session/Cloudflare cookies.",
                    repoUrl = "https://github.com/kittlibr/kittlibr-extensions",
                    isInstalled = true
                ),
                ExtensionManifest(
                    id = "librmanage",
                    name = "LibrManage & Series Manager",
                    category = "LibrManage",
                    version = "1.4.0",
                    description = "Library manager extension including built-in Series Manager for sorting multi-book series (5+ books), indices, and batch metadata cleanup.",
                    repoUrl = "https://github.com/kittlibr/kittlibr-extensions",
                    isInstalled = true
                ),
                ExtensionManifest(
                    id = "librsync",
                    name = "LibrSync: WebDAV, Document Providers & Rclone Cloud",
                    category = "LibrSync",
                    version = "1.2.0",
                    description = "Synchronizes library files and reading progress via WebDAV, Android Document Providers (Google Drive, Koofr), and cloud storage adapters.",
                    repoUrl = "https://github.com/kittlibr/kittlibr-extensions",
                    isInstalled = true
                ),
                ExtensionManifest(
                    id = "libraudio",
                    name = "LibrAudio: TTS & Audiobook Sync",
                    category = "LibrAudio",
                    version = "1.0.4",
                    description = "High-fidelity text-to-speech engine and synchronized audiobook playback extension.",
                    repoUrl = "https://github.com/kittlibr/kittlibr-extensions",
                    isInstalled = true
                )
            )
        )
    }

    var showAddRepoDialog by remember { mutableStateOf(false) }
    var newRepoUrlInput by remember { mutableStateOf("") }
    var configuringExtension by remember { mutableStateOf<ExtensionManifest?>(null) }

    var groupSeriesFivePlus by remember { mutableStateOf(true) }
    var autoNumberSeriesIndices by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extensions & Community Repos", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Community Disclaimer Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Community Extensions Disclaimer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Extensions and scrapers are entirely community-driven open-source plugins. They have nothing to do with the Kittlibr app developers and are neither condoned nor endorsed by them.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Kittlibr Extension Repositories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Button(onClick = { showAddRepoDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Repo")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Connected Repos: kittlibr-extensions\n" + extensionRepos.joinToString("\n") { "• $it" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            Text("Installed & Available Extensions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableExtensions, key = { it.id }) { ext ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(ext.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Text(ext.category, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("v${ext.version} • ${ext.repoUrl}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(ext.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { configuringExtension = ext },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Configure")
                                }
                                Button(
                                    onClick = {
                                        ext.isInstalled = !ext.isInstalled
                                        availableExtensions = availableExtensions.toList()
                                        Toast.makeText(context, if (ext.isInstalled) "Successfully installed ${ext.name}" else "Uninstalled ${ext.name}", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = if (ext.isInstalled) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) else ButtonDefaults.buttonColors()
                                ) {
                                    Text(if (ext.isInstalled) "Installed" else "Install")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddRepoDialog) {
        AlertDialog(
            onDismissRequest = { showAddRepoDialog = false },
            title = { Text("Add Custom Extension Repository") },
            text = {
                Column {
                    Text("Enter the raw JSON URL for a custom extension repository.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newRepoUrlInput,
                        onValueChange = { newRepoUrlInput = it },
                        label = { Text("Repository JSON URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newRepoUrlInput.isNotBlank()) {
                            extensionRepos = extensionRepos + newRepoUrlInput
                            Toast.makeText(context, "Added repository successfully!", Toast.LENGTH_SHORT).show()
                            newRepoUrlInput = ""
                            showAddRepoDialog = false
                        }
                    }
                ) {
                    Text("Add Repository")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRepoDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (configuringExtension != null) {
        AlertDialog(
            onDismissRequest = { configuringExtension = null },
            title = { Text("Configure: ${configuringExtension!!.name}") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Extension ID: ${configuringExtension!!.id}", style = MaterialTheme.typography.bodySmall)
                    Text("Version: ${configuringExtension!!.version}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (configuringExtension!!.id == "librmanage") {
                        Text("Series Manager Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Group Series with 5+ Books", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = groupSeriesFivePlus, onCheckedChange = { groupSeriesFivePlus = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Auto-number Series Indices (#1, #2)", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = autoNumberSeriesIndices, onCheckedChange = { autoNumberSeriesIndices = it })
                        }
                    } else if (configuringExtension!!.id == "librsync") {
                        Text("Storage & Cloud Sync Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Configured via WebDAV credentials or Android Document Provider.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("Extension parameters and scraper hooks are active.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        Toast.makeText(context, "Extension settings saved!", Toast.LENGTH_SHORT).show()
                        configuringExtension = null
                    }
                ) {
                    Text("Save Settings")
                }
            }
        )
    }
}
