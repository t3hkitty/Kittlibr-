package com.example.ui.screens.extensions

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    var isInstalled: Boolean,
    var isEnabled: Boolean = true,
    val isNew: Boolean = false,
    val hasUpdate: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionManagerScreen() {
    val context = LocalContext.current

    var extensionRepos by remember {
        mutableStateOf(
            listOf(
                "https://raw.githubusercontent.com/kttlibr/kttlibr-extensions/main/repo.json",
                "https://raw.githubusercontent.com/kittlibr/kittlibr-extensions/main/repo.json"
            )
        )
    }

    var availableExtensions by remember {
        mutableStateOf(
            listOf(
                ExtensionManifest(
                    id = "librget_lnreader_sources",
                    name = "Librget: All LNReader Sources (NovelUpdates, RoyalRoad, ScribbleHub)",
                    category = "Librget",
                    version = "2.6.0",
                    description = "Comprehensive acquisition extension containing all major LNReader sources for web novels and light novels.",
                    repoUrl = "https://github.com/kttlibr/kttlibr-extensions",
                    isInstalled = true,
                    isEnabled = true,
                    hasUpdate = true
                ),
                ExtensionManifest(
                    id = "librdiscover_goodreads",
                    name = "Librdiscover: Goodreads & OpenLibrary Rec Lists",
                    category = "Librdiscover",
                    version = "1.6.0",
                    description = "Discovery extension for Goodreads reading lists, OpenLibrary rec lists, and NYT bestsellers.",
                    repoUrl = "https://github.com/kttlibr/kttlibr-extensions",
                    isInstalled = true,
                    isEnabled = true
                ),
                ExtensionManifest(
                    id = "subscription_matcher_ext",
                    name = "Subscribable Metadata & Online Matcher (New Feature Extension)",
                    category = "Automation",
                    version = "1.0.0",
                    description = "New feature extension enabling metadata field subscriptions and real-time online new story alerts.",
                    repoUrl = "https://github.com/kttlibr/kttlibr-extensions",
                    isInstalled = true,
                    isEnabled = true,
                    isNew = true
                ),
                ExtensionManifest(
                    id = "smart_shelves_ext",
                    name = "Smart Shelves & Dynamic Rules Engine (New Feature Extension)",
                    category = "Library",
                    version = "1.0.0",
                    description = "New feature extension for auto-updating dynamic library shelves with AND/OR logic and sensitive content filters.",
                    repoUrl = "https://github.com/kttlibr/kittlibr-extensions",
                    isInstalled = true,
                    isEnabled = true,
                    isNew = true
                ),
                ExtensionManifest(
                    id = "webview_cookier",
                    name = "WebView Cookier (Cloudflare & Auth)",
                    category = "Utility",
                    version = "1.0.1",
                    description = "Shared cookie grabber utility requested by acquisition extensions to store session cookies.",
                    repoUrl = "https://github.com/kttlibr/kittlibr-extensions",
                    isInstalled = true,
                    isEnabled = true
                ),
                ExtensionManifest(
                    id = "librmanage",
                    name = "LibrManage & Series Manager",
                    category = "LibrManage",
                    version = "1.4.0",
                    description = "Library manager extension including built-in Series Manager for sorting multi-book series (5+ books).",
                    repoUrl = "https://github.com/kttlibr/kittlibr-extensions",
                    isInstalled = true,
                    isEnabled = true
                ),
                ExtensionManifest(
                    id = "librsync",
                    name = "LibrSync: WebDAV, Document Providers & Cloud Backup",
                    category = "LibrSync",
                    version = "1.3.0",
                    description = "Synchronizes library files, reading progress, and persistent app settings via WebDAV and cloud backups.",
                    repoUrl = "https://github.com/kttlibr/kittlibr-extensions",
                    isInstalled = true,
                    isEnabled = true,
                    hasUpdate = true
                )
            )
        )
    }

    var showAddRepoDialog by remember { mutableStateOf(false) }
    var newRepoUrlInput by remember { mutableStateOf("") }
    var configuringExtension by remember { mutableStateOf<ExtensionManifest?>(null) }

    // Local extension zip/json import from file picker
    val importExtensionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = it.lastPathSegment ?: "CustomExtension.json"
            val newExt = ExtensionManifest(
                id = "custom_ext_${System.currentTimeMillis()}",
                name = fileName.substringBeforeLast("."),
                category = "Custom",
                version = "1.0.0",
                description = "Imported custom extension package from local device storage.",
                repoUrl = "Local Import",
                isInstalled = true,
                isEnabled = true,
                isNew = true
            )
            availableExtensions = availableExtensions + newExt
            Toast.makeText(context, "Successfully imported and installed extension: ${newExt.name}", Toast.LENGTH_LONG).show()
        }
    }

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
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Kittlibr & Kttlibr Repositories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { importExtensionLauncher.launch(arrayOf("application/json", "application/zip", "*/*")) }) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Import File")
                            }
                            Button(onClick = { showAddRepoDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Repo")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Connected Repositories:\n" + extensionRepos.joinToString("\n") { "• $it" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            Text("Installed & Available Extensions (New! & Updates)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

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
                                        if (ext.isNew) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                                                Text("NEW", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                            }
                                        }
                                        if (ext.hasUpdate) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.extraSmall) {
                                                Text("UPDATE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                                            }
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
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = ext.isEnabled,
                                        onCheckedChange = { enabled ->
                                            ext.isEnabled = enabled
                                            availableExtensions = availableExtensions.toList()
                                            Toast.makeText(context, "${ext.name} is now ${if (enabled) "Enabled" else "Disabled"}", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (ext.isEnabled) "Enabled" else "Disabled", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { configuringExtension = ext }
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
                    Text("Extension parameters, scrapers, and feature hooks are active.", style = MaterialTheme.typography.bodyMedium)
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
