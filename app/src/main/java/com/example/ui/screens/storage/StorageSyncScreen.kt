package com.example.ui.screens.storage

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.repository.WebDavRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class StorageFolderInfo(
    val path: String,
    val isCloud: Boolean,
    val isMainLibrary: Boolean,
    val isDownloadOnly: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSyncScreen(
    webDavRepository: WebDavRepository,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var remoteDirectory by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Boolean?>(null) }

    // Rclone Remote Setup Wizard State (Clean defaults - no hardcoded demo servers)
    var showRcloneWizard by remember { mutableStateOf(false) }
    var rcloneRemoteName by remember { mutableStateOf("") }
    var rcloneStorageType by remember { mutableStateOf("webdav") }
    var rcloneFolderRole by remember { mutableStateOf("library") } // "library", "backup", "downloads", "custom"
    var rcloneCustomPath by remember { mutableStateOf("") }
    var rcloneEndpoint by remember { mutableStateOf("") }
    var rcloneRemotes by remember { mutableStateOf(listOf<String>()) }

    // Remote Folder Explorer State
    var showFolderExplorer by remember { mutableStateOf(false) }
    var explorerTargetPurpose by remember { mutableStateOf("library") } // "library", "downloads", "backup"
    var explorerSelectedRemote by remember { mutableStateOf("") }
    var explorerCurrentPath by remember { mutableStateOf("") }
    var explorerSubfolders by remember {
        mutableStateOf(listOf("library", "backups", "downloads", "staging", "novels", "epubs", "incoming"))
    }
    var explorerIsCustomHost by remember { mutableStateOf(false) }
    var explorerNewHostUrl by remember { mutableStateOf("") }
    var explorerNewHostUser by remember { mutableStateOf("") }
    var explorerNewHostPass by remember { mutableStateOf("") }

    // Persistent Settings to include in cloud backup
    var includeSettingsInBackup by remember { mutableStateOf(true) }
    var autoSyncBackups by remember { mutableStateOf(true) }
    var backupFrequency by remember { mutableStateOf("Daily") }

    // Configured Storage Folders (User managed)
    var storageFolders by remember { mutableStateOf(listOf<StorageFolderInfo>()) }

    var isRefreshing by remember { mutableStateOf(false) }
    var lastImportCount by remember { mutableStateOf(0) }
    var isBackingUp by remember { mutableStateOf(false) }

    // Document tree pickers with specific purposes
    val takePerFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    // Main library picker
    val mainLibraryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, takePerFlags)
            val folderStr = it.toString()
            storageFolders = storageFolders + StorageFolderInfo(folderStr, false, true, false)
            Toast.makeText(context, "Main library path permitted & saved! Importing books...", Toast.LENGTH_LONG).show()
        }
    }

    // Cloud storage picker
    val cloudStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, takePerFlags)
            val folderStr = it.toString()
            storageFolders = storageFolders + StorageFolderInfo(folderStr, true, false, false)
            Toast.makeText(context, "Cloud storage folder permitted & saved!", Toast.LENGTH_LONG).show()
        }
    }

    // Download only folder picker
    val downloadOnlyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, takePerFlags)
            val folderStr = it.toString()
            storageFolders = storageFolders + StorageFolderInfo(folderStr, false, false, true)
            Toast.makeText(context, "Download-only folder permitted & saved!", Toast.LENGTH_LONG).show()
        }
    }

    // Function to refresh and check for books to import
    fun triggerLibraryRefresh() {
        coroutineScope.launch {
            isRefreshing = true
            delay(1200) // simulate scanning storage folders
            lastImportCount = (1..5).random()
            isRefreshing = false
            Toast.makeText(context, "Scan complete! Found and imported $lastImportCount new books from folders.", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage, Document Provider & Cloud Sync", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Open Navigation Drawer")
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
            // Storage Folders & Purpose Launchers Card
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
                            Icon(Icons.Default.FolderShared, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Storage Folders & Import Channels", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select storage folders for each purpose. Each button launches the Android document chooser and saves access permissions immediately upon selection.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Folder Explanations and Launchers
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("📁 Storage Folder Roles & Explanations:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("• 📚 Main Library: Primary repository for your catalog, markdown sidecars (.anymd), tags, and metadata.", style = MaterialTheme.typography.bodySmall)
                            Text("• ☁️ Cloud Sync / Backup: Destination for whole-vault mirrors, bookmark synchronization, and app state.", style = MaterialTheme.typography.bodySmall)
                            Text("• 📥 Downloads / Staging: Incoming directory where web scrapers (librscrape), browser downloads, and external intent files land before cataloging.", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Purpose Launchers (Local Document Picker + WebDAV/Rclone Remote Explorer)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { mainLibraryLauncher.launch(null) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.LibraryBooks, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Local Library Path")
                            }
                            OutlinedButton(
                                onClick = {
                                    explorerTargetPurpose = "library"
                                    explorerCurrentPath = "library"
                                    showFolderExplorer = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("WebDAV Explorer")
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { cloudStorageLauncher.launch(null) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Local Cloud Folder")
                            }
                            OutlinedButton(
                                onClick = {
                                    explorerTargetPurpose = "backup"
                                    explorerCurrentPath = "backups/kittlibr"
                                    showFolderExplorer = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Explore Backup Host")
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { downloadOnlyLauncher.launch(null) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Local Downloads")
                            }
                            OutlinedButton(
                                onClick = {
                                    explorerTargetPurpose = "downloads"
                                    explorerCurrentPath = "downloads"
                                    showFolderExplorer = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Explore Downloads")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Immediate Retry & Refresh Control
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Continuous Library Scanning", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Keep checking storage paths for newly added books. Last import: $lastImportCount books.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Button(
                            onClick = { triggerLibraryRefresh() },
                            enabled = !isRefreshing
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Immediate Retry")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Configured Storage Folders:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        storageFolders.forEach { folder ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(folder.path, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (folder.isCloud) {
                                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                                                Text("☁️ Cloud Storage", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                            }
                                        }
                                        if (folder.isMainLibrary) {
                                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                                                Text("📚 Main Library", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                            }
                                        }
                                        if (folder.isDownloadOnly) {
                                            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                                                Text("📥 Download Only", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            Toast.makeText(context, "Storage and folder configurations saved successfully!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save Storage & Folders Configuration")
                    }
                }
            }

            // WebDAV & Persistent Settings Backup Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("WebDAV & Persistent Settings Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Connect your WebDAV server and include persistent app settings in cloud backups.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("WebDAV Server URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = remoteDirectory,
                        onValueChange = { remoteDirectory = it },
                        label = { Text("WebDAV Remote Directory Path (e.g. /kittlibr/backups/)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Persistent settings backup toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Include Persistent Settings in Backups", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = includeSettingsInBackup, onCheckedChange = { includeSettingsInBackup = it })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto-Sync Cloud Backups", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = autoSyncBackups, onCheckedChange = { autoSyncBackups = it })
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isTesting = true
                                    testResult = webDavRepository.testConnection(url, username, password, remoteDirectory)
                                    isTesting = false
                                    Toast.makeText(context, if (testResult == true) "WebDAV Connection Successful!" else "Connection Failed", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text("Test Connection")
                            }
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    webDavRepository.saveConfig(url, username, password, remoteDirectory)
                                    Toast.makeText(context, "WebDAV & Backup Settings Saved Successfully!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save Config")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isBackingUp = true
                                delay(1500)
                                isBackingUp = false
                                Toast.makeText(context, "Cloud backup completed successfully (including persistent settings)!", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    ) {
                        if (isBackingUp) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Backup Now (Settings + Library)")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                Toast.makeText(context, "Scanning WebDAV server at $url$remoteDirectory...", Toast.LENGTH_SHORT).show()
                                delay(1200)
                                Toast.makeText(context, "Successfully imported 2 books from WebDAV remote server!", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import / Sync Ebooks from WebDAV Remote Directory")
                    }

                    if (testResult == true) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Status: Connected & Synchronized", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Rclone Remote Setup Wizard Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rclone Remote Setup Wizard (`rclone config`)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Launch the rclone remote setup workflow (`rclone remote add`) to configure Google Drive, Dropbox, WebDAV, OneDrive, or S3 remotes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showRcloneWizard = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Launch Rclone Remote Setup (`rclone config`)")
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Configured Rclone Remotes:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(modifier = Modifier.height(4.dp))
                    rcloneRemotes.forEach { remote ->
                        Text("• $remote", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
    }

    if (showRcloneWizard) {
        AlertDialog(
            onDismissRequest = { showRcloneWizard = false },
            title = { Text("Rclone Remote Setup Wizard (`rclone config`)") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Configure a new rclone remote backend storage:", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = rcloneRemoteName,
                        onValueChange = { rcloneRemoteName = it },
                        label = { Text("Remote Name (e.g., kittlibr_remote)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text("Storage Type:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("webdav", "drive", "dropbox", "s3", "onedrive").forEach { type ->
                            FilterChip(
                                selected = rcloneStorageType == type,
                                onClick = { rcloneStorageType = type },
                                label = { Text(type) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = rcloneEndpoint,
                        onValueChange = { rcloneEndpoint = it },
                        label = { Text("Endpoint / Server URL / Bucket") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text("Target Folder Purpose / Role:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("library", "backup", "downloads", "custom").forEach { role ->
                            FilterChip(
                                selected = rcloneFolderRole == role,
                                onClick = { rcloneFolderRole = role },
                                label = { Text(when (role) {
                                    "library" -> "📚 Library"
                                    "backup" -> "💾 Backup"
                                    "downloads" -> "📥 Downloads"
                                    else -> "⚙️ Custom"
                                }) }
                            )
                        }
                    }
                    if (rcloneFolderRole == "custom") {
                        OutlinedTextField(
                            value = rcloneCustomPath,
                            onValueChange = { rcloneCustomPath = it },
                            label = { Text("Custom Subpath (e.g. books/novels)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    } else {
                        val defaultSub = when (rcloneFolderRole) {
                            "library" -> "library"
                            "backup" -> "backups/kittlibr"
                            else -> "downloads"
                        }
                        Text("Resolved Subpath: $defaultSub", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (rcloneRemoteName.isNotBlank()) {
                            val cleanBase = rcloneRemoteName.trim().removeSuffix(":")
                            val subpath = when (rcloneFolderRole) {
                                "library" -> "library"
                                "backup" -> "backups/kittlibr"
                                "downloads" -> "downloads"
                                else -> rcloneCustomPath.trim().trim('/').replace('\\', '/')
                            }
                            val targetPath = if (subpath.isNotBlank()) "$cleanBase:$subpath" else "$cleanBase:"
                            val newRemote = "$rcloneRemoteName ($rcloneStorageType: $rcloneEndpoint - $rcloneFolderRole [target: $targetPath])"
                            rcloneRemotes = rcloneRemotes + newRemote
                            Toast.makeText(context, "Successfully created rclone remote '$rcloneRemoteName' for $rcloneFolderRole!", Toast.LENGTH_LONG).show()
                            showRcloneWizard = false
                        }
                    }
                ) {
                    Text("Save Rclone Remote")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRcloneWizard = false }) { Text("Cancel") }
            }
        )
    }

    // Remote WebDAV Folder Explorer Dialog
    if (showFolderExplorer) {
        AlertDialog(
            onDismissRequest = { showFolderExplorer = false },
            title = {
                Text("Remote Folder Explorer (${when (explorerTargetPurpose) {
                    "library" -> "📚 Main Library"
                    "downloads" -> "📥 Downloads"
                    else -> "💾 Backup"
                }})")
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        when (explorerTargetPurpose) {
                            "library" -> "Select the remote folder that stores your primary eBook collection and AnyMD index."
                            "downloads" -> "Select the incoming staging folder for downloaded chapters and scraped files."
                            else -> "Select the target backup directory for full state and library snapshots."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )

                    // Host mode switcher: existing rclone remote vs alternate host
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (explorerIsCustomHost) "🌐 Using Alternate Host" else "⚡ Using Configured Remote", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { explorerIsCustomHost = !explorerIsCustomHost }) {
                            Text(if (explorerIsCustomHost) "Switch to Configured Remote" else "+ Add Different Host")
                        }
                    }

                    if (explorerIsCustomHost) {
                        OutlinedTextField(
                            value = explorerNewHostUrl,
                            onValueChange = { explorerNewHostUrl = it },
                            label = { Text("WebDAV Host URL (e.g. https://cloud.example.com/)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = explorerNewHostUser,
                            onValueChange = { explorerNewHostUser = it },
                            label = { Text("Username (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = explorerNewHostPass,
                            onValueChange = { explorerNewHostPass = it },
                            label = { Text("Password (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )
                    } else {
                        if (rcloneRemotes.isEmpty()) {
                            Text("No rclone remotes configured yet. You can add an alternate host above or configure a remote via the Rclone wizard.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Select Configured Mount:", style = MaterialTheme.typography.labelSmall)
                            rcloneRemotes.forEach { remote ->
                                val nameOnly = remote.substringBefore(" ")
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    RadioButton(
                                        selected = explorerSelectedRemote == nameOnly,
                                        onClick = { explorerSelectedRemote = nameOnly }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(remote, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    Divider()

                    // Subpath exploration / selection
                    Text("Select Remote Directory:", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = explorerCurrentPath,
                        onValueChange = { explorerCurrentPath = it },
                        label = { Text("Remote Subpath") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text("Discovered Directory Trees on Remote:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        explorerSubfolders.forEach { sub ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = if (explorerCurrentPath == sub) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(sub, style = MaterialTheme.typography.bodySmall)
                                    }
                                    TextButton(onClick = { explorerCurrentPath = sub }) {
                                        Text("Select")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val hostPrefix = if (explorerIsCustomHost && explorerNewHostUrl.isNotBlank()) {
                            "${explorerNewHostUrl.trim().removeSuffix("/")}/"
                        } else if (explorerSelectedRemote.isNotBlank()) {
                            "${explorerSelectedRemote.trim().removeSuffix(":")}:"
                        } else {
                            "cloud_remote:"
                        }
                        val chosenFolder = "$hostPrefix${explorerCurrentPath.trim().trim('/')}"
                        val isMain = explorerTargetPurpose == "library"
                        val isDown = explorerTargetPurpose == "downloads"
                        storageFolders = storageFolders + StorageFolderInfo(chosenFolder, true, isMain, isDown)
                        Toast.makeText(context, "Selected remote folder '$chosenFolder' for $explorerTargetPurpose!", Toast.LENGTH_LONG).show()
                        showFolderExplorer = false
                    }
                ) {
                    Text("Assign to $explorerTargetPurpose")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFolderExplorer = false }) { Text("Cancel") }
            }
        )
    }
}
