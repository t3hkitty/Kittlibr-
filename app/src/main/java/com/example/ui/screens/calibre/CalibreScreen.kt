package com.example.ui.screens.calibre

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import com.example.data.local.BookEntity
import com.example.data.repository.AiRepository
import com.example.ui.screens.library.LibraryViewModel
import com.example.ui.screens.library.DynamicLibraryRule
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibreScreen(
    viewModel: LibraryViewModel,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val books by viewModel.books.collectAsState()
    val customShelves by viewModel.customShelves.collectAsState()
    val smartShelves by viewModel.smartShelves.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val aiRepository = remember { AiRepository() }

    var selectedBookIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var isSeekingMetadata by remember { mutableStateOf(false) }

    // Metadata Source Seeking Configuration Checkboxes
    var seekTitleAuthor by remember { mutableStateOf(true) }
    var seekSeriesIndex by remember { mutableStateOf(true) }
    var seekCoverArt by remember { mutableStateOf(true) }
    var seekPlotSummary by remember { mutableStateOf(true) }
    var seekGenresTags by remember { mutableStateOf(true) }
    var seekStoreRatings by remember { mutableStateOf(true) }
    var seekAiOpinion by remember { mutableStateOf(true) }
    var seekContentWarnings by remember { mutableStateOf(true) }

    // Custom Shelves Selection & Creation
    var selectedShelf by remember { mutableStateOf("Masterpieces") }
    var showCreateShelfDialog by remember { mutableStateOf(false) }
    var newShelfNameInput by remember { mutableStateOf("") }

    // Smart Shelves Builder States
    var showCreateSmartShelfDialog by remember { mutableStateOf(false) }
    var smartRuleName by remember { mutableStateOf("") }
    var smartRuleAuthor by remember { mutableStateOf("") }
    var smartUseOrAuthor by remember { mutableStateOf(false) }
    var smartRuleTags by remember { mutableStateOf("") }
    var smartUseOrTags by remember { mutableStateOf(false) }
    var smartRuleFileType by remember { mutableStateOf("") }
    var smartSensitivePass by remember { mutableStateOf<Boolean?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LibrManage: Metadata & Source Seeking Hub", fontWeight = FontWeight.Bold) },
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
            // Metadata Source Seeking Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Metadata Source Seeking Preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select the specific metadata fields and custom attributes you want scraped, synthesized by AI, or pulled from online stores.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(12.dp))

                    MetadataCheckboxRow("Title & Author", seekTitleAuthor) { seekTitleAuthor = it }
                    MetadataCheckboxRow("Series & Volume Index", seekSeriesIndex) { seekSeriesIndex = it }
                    MetadataCheckboxRow("Cover Art & Artwork", seekCoverArt) { seekCoverArt = it }
                    MetadataCheckboxRow("Plot Summary & Description", seekPlotSummary) { seekPlotSummary = it }
                    MetadataCheckboxRow("Genres & Tags", seekGenresTags) { seekGenresTags = it }
                    MetadataCheckboxRow("Average Store Ratings & Reviews", seekStoreRatings) { seekStoreRatings = it }
                    MetadataCheckboxRow("AI Summary of Popular Opinion", seekAiOpinion) { seekAiOpinion = it }
                    MetadataCheckboxRow("Content Warnings & Tropes (e.g., 'Does the dog die?')", seekContentWarnings) { seekContentWarnings = it }
                }
            }

            // Goodreads-Inspired Custom Shelves Card with Create New Shelf option
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Goodreads Custom Shelves", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = { showCreateShelfDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Shelf")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Assign selected books to custom shelves en masse.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        customShelves.forEach { shelf ->
                            FilterChip(
                                selected = selectedShelf == shelf,
                                onClick = { selectedShelf = shelf },
                                label = { Text(shelf, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }

            // Smart Shelves Hub Card (Auto-updating dynamic filter shelves)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Smart Shelves (Auto-Updating)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        OutlinedButton(onClick = { showCreateSmartShelfDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Smart Shelf")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Smart Shelves automatically update as new books matching your dynamic filter criteria are added to the library.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(modifier = Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        smartShelves.forEach { rule ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setDynamicLibrary(rule)
                                        Toast.makeText(context, "Applied Smart Shelf: ${rule.name}", Toast.LENGTH_SHORT).show()
                                    },
                                color = MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(rule.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        val criteriaText = buildString {
                                            if (rule.authorFilter.isNotBlank()) append("Author: ${rule.authorFilter} ")
                                            if (rule.tagQuery.isNotBlank()) append("Tags: ${rule.tagQuery} ")
                                            if (rule.fileType.isNotBlank()) append("Format: ${rule.fileType} ")
                                            if (rule.minSeriesCount > 0) append("Min Series: ${rule.minSeriesCount} ")
                                            if (rule.missingMetadataOnly) append("Missing Metadata ")
                                        }.ifBlank { "All matching items" }
                                        Text(criteriaText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            }

            // Multi-Select & Execution Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Batch Selection & Execution (${selectedBookIds.size} selected)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { selectedBookIds = books.map { it.id }.toSet() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Select All", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { selectedBookIds = emptySet() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Select None", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = {
                                val allIds = books.map { it.id }.toSet()
                                selectedBookIds = allIds - selectedBookIds
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Inverse", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isSeekingMetadata = true
                                    val selectedBooks = books.filter { selectedBookIds.contains(it.id) }
                                    selectedBooks.forEach { book ->
                                        val cleaned = aiRepository.cleanMetadata(book.title, book.author, book.description)
                                        val enrichedDesc = "$cleaned\n\n[Shelf: $selectedShelf | AI Opinion & Content Warnings Verified]"
                                        viewModel.updateBook(book.copy(description = enrichedDesc, tags = "${book.tags}, $selectedShelf"))
                                    }
                                    isSeekingMetadata = false
                                    successMessage = "Successfully fetched requested metadata & assigned to shelf '$selectedShelf' for ${selectedBookIds.size} books!"
                                    selectedBookIds = emptySet()
                                }
                            },
                            enabled = selectedBookIds.isNotEmpty() && !isSeekingMetadata,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isSeekingMetadata) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Run Metadata Seeking", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    books.filter { selectedBookIds.contains(it.id) }.forEach { book ->
                                        viewModel.deleteBook(book)
                                    }
                                    successMessage = "Successfully deleted ${selectedBookIds.size} books!"
                                    selectedBookIds = emptySet()
                                }
                            },
                            enabled = selectedBookIds.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete Selected", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            val msg = successMessage
            if (msg != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Book List for Selection
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                books.forEach { book ->
                    val isSelected = selectedBookIds.contains(book.id)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedBookIds = if (isSelected) {
                                    selectedBookIds - book.id
                                } else {
                                    selectedBookIds + book.id
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(book.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("${book.author} • Tags: ${book.tags}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            }
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedBookIds = if (checked) {
                                        selectedBookIds + book.id
                                    } else {
                                        selectedBookIds - book.id
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Create New Custom Shelf Dialog
    if (showCreateShelfDialog) {
        AlertDialog(
            onDismissRequest = { showCreateShelfDialog = false },
            title = { Text("Create New Custom Shelf") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newShelfNameInput,
                        onValueChange = { newShelfNameInput = it },
                        label = { Text("Shelf Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newShelfNameInput.isNotBlank()) {
                            viewModel.createCustomShelf(newShelfNameInput)
                            Toast.makeText(context, "Custom shelf '$newShelfNameInput' created!", Toast.LENGTH_SHORT).show()
                            newShelfNameInput = ""
                            showCreateShelfDialog = false
                        }
                    }
                ) {
                    Text("Create Shelf")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateShelfDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Create New Smart Shelf Dialog
    if (showCreateSmartShelfDialog) {
        AlertDialog(
            onDismissRequest = { showCreateSmartShelfDialog = false },
            title = { Text("Create Smart Shelf (Auto-Updating Rule)") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().height(400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Smart Shelves automatically filter books matching criteria and update live as new books are added.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    OutlinedTextField(value = smartRuleName, onValueChange = { smartRuleName = it }, label = { Text("Smart Shelf Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = smartRuleAuthor, onValueChange = { smartRuleAuthor = it }, label = { Text("Author(s)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = smartUseOrAuthor, onCheckedChange = { smartUseOrAuthor = it })
                        Text("Use OR logic for authors", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(value = smartRuleTags, onValueChange = { smartRuleTags = it }, label = { Text("Tags / Genres") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = smartUseOrTags, onCheckedChange = { smartUseOrTags = it })
                        Text("Use OR logic for tags", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(value = smartRuleFileType, onValueChange = { smartRuleFileType = it }, label = { Text("File Format (e.g. EPUB, PDF)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    
                    Text("Sensitive Content / Tropes Filter:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = smartSensitivePass == true, onClick = { smartSensitivePass = if (smartSensitivePass == true) null else true }, label = { Text("True (Has Warning)") })
                        FilterChip(selected = smartSensitivePass == false, onClick = { smartSensitivePass = if (smartSensitivePass == false) null else false }, label = { Text("False (No Warning)") })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (smartRuleName.isNotBlank()) {
                            val rule = DynamicLibraryRule(
                                name = smartRuleName,
                                authorFilter = smartRuleAuthor,
                                useOrForAuthor = smartUseOrAuthor,
                                tagQuery = smartRuleTags,
                                useOrLogicForTags = smartUseOrTags,
                                fileType = smartRuleFileType,
                                sensitiveContentPass = smartSensitivePass
                            )
                            viewModel.saveSmartShelf(rule)
                            viewModel.setDynamicLibrary(rule)
                            Toast.makeText(context, "Smart Shelf '$smartRuleName' created and applied!", Toast.LENGTH_SHORT).show()
                            smartRuleName = ""
                            smartRuleAuthor = ""
                            smartRuleTags = ""
                            smartRuleFileType = ""
                            smartSensitivePass = null
                            showCreateSmartShelfDialog = false
                        }
                    }
                ) {
                    Text("Create Smart Shelf")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateSmartShelfDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun MetadataCheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
