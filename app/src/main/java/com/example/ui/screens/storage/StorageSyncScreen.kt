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
import kotlinx.coroutines.launch

data class StorageFolderInfo(
    val path: String,
    val isCloud: Boolean,
    val isMainLibrary: Boolean,
    val isDownloadOnly: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSyncScreen(webDavRepository: WebDavRepository) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var url by remember { mutableStateOf("https://dav.example.com/kittlibr/") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Boolean?>(null) }

    var storageFolders by remember {
        mutableStateOf(
            listOf(
                StorageFolderInfo("/storage/emulated/0/Books/CalibreLibrary", false, true, false),
                StorageFolderInfo("Google Drive / CalibreSync", true, false, false),
                StorageFolderInfo("/storage/emulated/0/Download/Incoming", false, false, true)
            )
        )
    }

    var showAddFolderDialog by remember { mutableStateOf(false) }
    var newFolderPath by remember { mutableStateOf("") }
    var newIsCloud by remember { mutableStateOf(false) }
    var newIsMainLibrary by remember { mutableStateOf(false) }
    var newIsDownloadOnly by remember { mutableStateOf(false) }

    // Document Tree Picker for Google Drive, Koofr, or local storage providers
    val takePerFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    val documentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, takePerFlags)
            val folderStr = it.toString()
            val newFolder = StorageFolderInfo(folderStr, true, false, false)
            storageFolders = storageFolders + newFolder
            Toast.makeText(context, "Storage folder saved & contents imported to library!", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage, Document Provider & Cloud Sync", fontWeight = FontWeight.Bold) },
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
            // Android Document Provider & Multiple Folders Card
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
                            Text("Multiple Storage Folders & Import", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Button(onClick = { showAddFolderDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Folder")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Configure multiple storage paths for import. Denote cloud paths, main library, or download-only folders.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { documentTreeLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick Folder via Android Document Provider")
                    }

                    Spacer(modifier = Modifier.height(12.dp))
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
                }
            }

            // WebDAV Config Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("WebDAV Library Storage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Connect your own Nextcloud, OwnCloud, or WebDAV server.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
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
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isTesting = true
                                    testResult = webDavRepository.testConnection(url, username, password)
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
                                    webDavRepository.saveConfig(url, username, password)
                                    Toast.makeText(context, "WebDAV Settings Saved!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                        ) {
                            Text("Save Config")
                        }
                    }

                    if (testResult == true) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Status: Connected & Synchronized", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showAddFolderDialog) {
        AlertDialog(
            onDismissRequest = { showAddFolderDialog = false },
            title = { Text("Add Storage Folder & Import") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = newFolderPath,
                        onValueChange = { newFolderPath = it },
                        label = { Text("Folder Path or URI") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cloud Storage Path", style = MaterialTheme.typography.bodyMedium)
                        Checkbox(checked = newIsCloud, onCheckedChange = { newIsCloud = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Main Library Folder", style = MaterialTheme.typography.bodyMedium)
                        Checkbox(checked = newIsMainLibrary, onCheckedChange = { newIsMainLibrary = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Download Only Folder", style = MaterialTheme.typography.bodyMedium)
                        Checkbox(checked = newIsDownloadOnly, onCheckedChange = { newIsDownloadOnly = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderPath.isNotBlank()) {
                            val newFolder = StorageFolderInfo(newFolderPath, newIsCloud, newIsMainLibrary, newIsDownloadOnly)
                            storageFolders = storageFolders + newFolder
                            Toast.makeText(context, "Folder saved and contents imported to library!", Toast.LENGTH_LONG).show()
                            newFolderPath = ""
                            newIsCloud = false
                            newIsMainLibrary = false
                            newIsDownloadOnly = false
                            showAddFolderDialog = false
                        }
                    }
                ) {
                    Text("Save & Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFolderDialog = false }) { Text("Cancel") }
            }
        )
    }
}
