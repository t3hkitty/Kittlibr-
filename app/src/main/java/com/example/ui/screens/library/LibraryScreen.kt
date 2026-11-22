package com.example.ui.screens.library

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.BookEntity
import com.example.ui.components.GeneratedCover

enum class LibrarySortOption(val label: String) {
    TITLE("Title (A-Z)"),
    AUTHOR("Author (A-Z)"),
    RECENTLY_ADDED("Recently Added"),
    RECENTLY_OPENED("Recently Opened"),
    RATING("Rating (High-Low)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onBookClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    onSelectFolderKey: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val books by viewModel.books.collectAsState()
    val customShelves by viewModel.customShelves.collectAsState()
    val smartShelves by viewModel.smartShelves.collectAsState()
    val savedDynamicRules by viewModel.savedDynamicRules.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val activeDynamicLibrary by viewModel.activeDynamicLibrary.collectAsState()
    val longPressAction by viewModel.longPressAction.collectAsState()
    val selectedBookForMoreInfo by viewModel.selectedBookForMoreInfo.collectAsState()
    val selectedBookForEdit by viewModel.selectedBookForEdit.collectAsState()

    val selectedBookIds by viewModel.selectedBookIds.collectAsState()
    val isMultiSelectActive by viewModel.isMultiSelectActive.collectAsState()
    val pendingMetadataCount by viewModel.pendingMetadataCount.collectAsState()
    val failedLookupCount by viewModel.failedLookupCount.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showDynamicLibraryDialog by remember { mutableStateOf(false) }
    var currentSortOption by remember { mutableStateOf(LibrarySortOption.RECENTLY_ADDED) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Custom dynamic rule builder inputs
    var newRuleName by remember { mutableStateOf("") }
    var newRuleAuthor by remember { mutableStateOf("") }
    var useOrForAuthor by remember { mutableStateOf(false) }
    var newRuleTags by remember { mutableStateOf("") }
    var useOrForTags by remember { mutableStateOf(false) }
    var newRuleShelf by remember { mutableStateOf("") }
    var useOrForShelf by remember { mutableStateOf(false) }
    var newRuleFileType by remember { mutableStateOf("") }
    var sensitiveContentPassState by remember { mutableStateOf<Boolean?>(null) } // true, false, or null

    // Cover zoom state
    var isCoverZoomed by remember { mutableStateOf(false) }

    // Batch action dialog states
    var showBatchTagDialog by remember { mutableStateOf(false) }
    var batchTagInput by remember { mutableStateOf("") }
    var showBatchStatusDialog by remember { mutableStateOf(false) }
    var showBatchDateDialog by remember { mutableStateOf(false) }
    var batchDateInput by remember { mutableStateOf("2026-11-21") }

    val filteredBooks = books.filter { book ->
        val matchesSearch = searchQuery.isBlank() ||
                book.title.contains(searchQuery, ignoreCase = true) ||
                book.author.contains(searchQuery, ignoreCase = true) ||
                book.tags.contains(searchQuery, ignoreCase = true) ||
                book.series.contains(searchQuery, ignoreCase = true)

        val rule = activeDynamicLibrary
        val matchesRule = if (rule == null || rule.name == "All Books") {
            true
        } else {
            var match = true
            if (rule.acquiredOnly != null) {
                match = match && (book.hasCopy == rule.acquiredOnly)
            }
            if (rule.minSeriesCount > 0) {
                val seriesCount = books.count { it.series.isNotBlank() && it.series.equals(book.series, ignoreCase = true) }
                match = match && (seriesCount >= rule.minSeriesCount && book.seriesIndex <= 1.1f)
            }
            if (rule.missingMetadataOnly) {
                match = match && (book.description.isBlank() || book.coverUrl.isBlank() || book.author.isBlank())
            }
            if (rule.fileType.isNotBlank()) {
                match = match && book.format.equals(rule.fileType, ignoreCase = true)
            }
            if (rule.authorFilter.isNotBlank()) {
                val authors = rule.authorFilter.split(",").map { it.trim() }.filter { it.isNotBlank() }
                if (rule.useOrForAuthor) {
                    match = match && authors.any { book.author.contains(it, ignoreCase = true) }
                } else {
                    match = match && authors.all { book.author.contains(it, ignoreCase = true) }
                }
            }
            if (rule.shelfFilter.isNotBlank()) {
                val shelves = rule.shelfFilter.split(",").map { it.trim() }.filter { it.isNotBlank() }
                if (rule.useOrForShelf) {
                    match = match && shelves.any { book.tags.contains(it, ignoreCase = true) }
                } else {
                    match = match && shelves.all { book.tags.contains(it, ignoreCase = true) }
                }
            }
            if (rule.tagQuery.isNotBlank()) {
                val tagsList = rule.tagQuery.split(",").map { it.trim() }.filter { it.isNotBlank() }
                if (rule.useOrLogicForTags) {
                    match = match && tagsList.any { tag -> book.tags.contains(tag, ignoreCase = true) }
                } else {
                    match = match && tagsList.all { tag -> book.tags.contains(tag, ignoreCase = true) }
                }
            }
            if (rule.sensitiveContentPass != null) {
                val hasWarning = book.description.contains("Content Warnings", ignoreCase = true) || book.description.contains("Does the dog die", ignoreCase = true)
                match = match && (hasWarning == rule.sensitiveContentPass)
            }
            match
        }
        matchesSearch && matchesRule
    }.sortedWith(
        when (currentSortOption) {
            LibrarySortOption.TITLE -> compareBy { it.title.lowercase() }
            LibrarySortOption.AUTHOR -> compareBy { it.author.lowercase() }
            LibrarySortOption.RECENTLY_ADDED -> compareByDescending { it.addedDate }
            LibrarySortOption.RECENTLY_OPENED -> compareByDescending { it.lastReadDate }
            LibrarySortOption.RATING -> compareByDescending { it.rating }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (isMultiSelectActive) "${selectedBookIds.size} Selected" else "Kittlibr Library", fontWeight = FontWeight.Bold)
                        Text(
                            text = activeDynamicLibrary?.name ?: "All Books",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Open Navigation Drawer")
                    }
                },
                actions = {
                    if (isMultiSelectActive) {
                        IconButton(onClick = { viewModel.clearBookSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                    } else {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "Sort Library")
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                LibrarySortOption.values().forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            currentSortOption = option
                                            showSortMenu = false
                                        },
                                        leadingIcon = {
                                            if (currentSortOption == option) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { showDynamicLibraryDialog = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Dynamic Library Picker")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        bottomBar = {
            if (isMultiSelectActive) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.selectAllBooks(books.map { it.id }) }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text("All", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(onClick = { viewModel.inverseBookSelection(books.map { it.id }) }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text("Inverse", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(onClick = { viewModel.clearBookSelection() }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text("None", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(onClick = { showBatchTagDialog = true }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Label, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text("Tag", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(onClick = { showBatchStatusDialog = true }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text("Status", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(onClick = { showBatchDateDialog = true }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text("Date", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(onClick = {
                            viewModel.batchDelete(selectedBookIds)
                            Toast.makeText(context, "Deleted selected books", Toast.LENGTH_SHORT).show()
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                Text("Delete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isMultiSelectActive) {
                FloatingActionButton(
                    onClick = onAddClick,
                    modifier = Modifier.testTag("add_book_fab"),
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Book / Acquire")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (pendingMetadataCount > 0 || failedLookupCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🔄 Auto-seeking metadata: $pendingMetadataCount books remaining.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (failedLookupCount > 0) {
                                Text(
                                    text = "⚠️ $failedLookupCount failed lookups detected.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        if (failedLookupCount > 0) {
                            TextButton(onClick = {
                                viewModel.setDynamicLibrary(viewModel.presetDynamicLibraries.find { it.missingMetadataOnly })
                            }) {
                                Text("View Failed", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search title, author, tags, or series...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("library_search"),
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            if (filteredBooks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            "Your library is empty. Import your first ebook or scan a library folder!",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = onAddClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("+ Import .epub or .md Book")
                        }
                        OutlinedButton(
                            onClick = onSelectFolderKey,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("+ Select Library Folder")
                        }
                    }
                }
            } else {
                when (viewMode) {
                    LibraryViewMode.CAROUSEL -> {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Text("Featured Carousel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth().height(380.dp)
                            ) {
                                items(filteredBooks, key = { it.id }) { book ->
                                    Box(modifier = Modifier.width(240.dp).fillMaxHeight()) {
                                        BookCard(
                                            book = book,
                                            isSelected = selectedBookIds.contains(book.id),
                                            isMultiSelectActive = isMultiSelectActive,
                                            onTap = {
                                                if (isMultiSelectActive) viewModel.toggleBookSelection(book.id)
                                                else viewModel.setSelectedBookForMoreInfo(book)
                                            },
                                            onLongPress = {
                                                if (longPressAction == "auto_open") {
                                                    if (book.hasCopy) onBookClick(book.id) else onAddClick()
                                                } else {
                                                    viewModel.setSelectedBookForEdit(book)
                                                }
                                            },
                                            onSelectToggle = { viewModel.toggleBookSelection(book.id) },
                                            onFavoriteClick = { viewModel.toggleFavorite(book) },
                                            onMoreInfoClick = { viewModel.setSelectedBookForMoreInfo(book) },
                                            onEditClick = { viewModel.setSelectedBookForEdit(book) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    LibraryViewMode.LIST -> {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize().testTag("book_list")
                        ) {
                            items(filteredBooks, key = { it.id }) { book ->
                                BookListItem(
                                    book = book,
                                    isSelected = selectedBookIds.contains(book.id),
                                    isMultiSelectActive = isMultiSelectActive,
                                    onTap = {
                                        if (isMultiSelectActive) viewModel.toggleBookSelection(book.id)
                                        else viewModel.setSelectedBookForMoreInfo(book)
                                    },
                                    onLongPress = {
                                        if (longPressAction == "auto_open") {
                                            if (book.hasCopy) onBookClick(book.id) else onAddClick()
                                        } else {
                                            viewModel.setSelectedBookForEdit(book)
                                        }
                                    },
                                    onSelectToggle = { viewModel.toggleBookSelection(book.id) },
                                    onFavoriteClick = { viewModel.toggleFavorite(book) },
                                    onMoreInfoClick = { viewModel.setSelectedBookForMoreInfo(book) },
                                    onEditClick = { viewModel.setSelectedBookForEdit(book) }
                                )
                            }
                        }
                    }
                    LibraryViewMode.SMALL_GRID -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize().testTag("book_small_grid")
                        ) {
                            items(filteredBooks, key = { it.id }) { book ->
                                BookSmallCard(
                                    book = book,
                                    isSelected = selectedBookIds.contains(book.id),
                                    onTap = {
                                        if (isMultiSelectActive) viewModel.toggleBookSelection(book.id)
                                        else viewModel.setSelectedBookForMoreInfo(book)
                                    },
                                    onLongPress = { viewModel.toggleBookSelection(book.id) }
                                )
                            }
                        }
                    }
                    LibraryViewMode.MEDIUM_GRID -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize().testTag("book_grid")
                        ) {
                            items(filteredBooks, key = { it.id }) { book ->
                                BookCard(
                                    book = book,
                                    isSelected = selectedBookIds.contains(book.id),
                                    isMultiSelectActive = isMultiSelectActive,
                                    onTap = {
                                        if (isMultiSelectActive) viewModel.toggleBookSelection(book.id)
                                        else viewModel.setSelectedBookForMoreInfo(book)
                                    },
                                    onLongPress = {
                                        if (longPressAction == "auto_open") {
                                            if (book.hasCopy) onBookClick(book.id) else onAddClick()
                                        } else {
                                            viewModel.setSelectedBookForEdit(book)
                                        }
                                    },
                                    onSelectToggle = { viewModel.toggleBookSelection(book.id) },
                                    onFavoriteClick = { viewModel.toggleFavorite(book) },
                                    onMoreInfoClick = { viewModel.setSelectedBookForMoreInfo(book) },
                                    onEditClick = { viewModel.setSelectedBookForEdit(book) }
                                )
                            }
                        }
                    }
                    LibraryViewMode.DETAILED_CARD -> {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize().testTag("book_detailed_list")
                        ) {
                            items(filteredBooks, key = { it.id }) { book ->
                                DetailedBookCard(
                                    book = book,
                                    isSelected = selectedBookIds.contains(book.id),
                                    isMultiSelectActive = isMultiSelectActive,
                                    onTap = {
                                        if (isMultiSelectActive) viewModel.toggleBookSelection(book.id)
                                        else viewModel.setSelectedBookForMoreInfo(book)
                                    },
                                    onLongPress = {
                                        if (longPressAction == "auto_open") {
                                            if (book.hasCopy) onBookClick(book.id) else onAddClick()
                                        } else {
                                            viewModel.setSelectedBookForEdit(book)
                                        }
                                    },
                                    onSelectToggle = { viewModel.toggleBookSelection(book.id) },
                                    onFavoriteClick = { viewModel.toggleFavorite(book) },
                                    onMoreInfoClick = { viewModel.setSelectedBookForMoreInfo(book) },
                                    onEditClick = { viewModel.setSelectedBookForEdit(book) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modular, Responsive More Info View
    if (selectedBookForMoreInfo != null) {
        val book = selectedBookForMoreInfo!!
        val seriesBooks = books.filter { it.series.isNotBlank() && it.series.equals(book.series, ignoreCase = true) && it.id != book.id }
        val authorBooks = books.filter { it.author.equals(book.author, ignoreCase = true) && it.id != book.id }
        val tagsList = book.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }

        AlertDialog(
            onDismissRequest = { viewModel.setSelectedBookForMoreInfo(null) },
            title = { Text("Metadata & Detailed Info", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            text = {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(520.dp)) {
                    val isWide = maxWidth > 600.dp
                    if (isWide) {
                        Row(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Column(modifier = Modifier.width(140.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(
                                    modifier = Modifier.width(110.dp).height(160.dp).clip(MaterialTheme.shapes.medium).clickable { isCoverZoomed = true }
                                ) {
                                    if (book.coverUrl.isNotBlank() && book.coverUrl.startsWith("http")) {
                                        AsyncImage(model = book.coverUrl, contentDescription = book.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    } else {
                                        GeneratedCover(title = book.title, author = book.author, series = book.series, seriesIndex = book.seriesIndex, modifier = Modifier.fillMaxSize())
                                    }
                                }
                                Button(onClick = {
                                    viewModel.setSelectedBookForMoreInfo(null)
                                    if (book.hasCopy) onBookClick(book.id) else onAddClick()
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (book.hasCopy) "Read" else "Acquire", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                MetadataSectionContent(book, tagsList, seriesBooks, authorBooks, viewModel)
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.width(110.dp).height(160.dp).clip(MaterialTheme.shapes.medium).clickable { isCoverZoomed = true }
                            ) {
                                if (book.coverUrl.isNotBlank() && book.coverUrl.startsWith("http")) {
                                    AsyncImage(model = book.coverUrl, contentDescription = book.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    GeneratedCover(title = book.title, author = book.author, series = book.series, seriesIndex = book.seriesIndex, modifier = Modifier.fillMaxSize())
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                MetadataSectionContent(book, tagsList, seriesBooks, authorBooks, viewModel)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.setSelectedBookForMoreInfo(null) }) { Text("Close") } }
        )
    }

    // Cover Zoom Dialog
    if (isCoverZoomed && selectedBookForMoreInfo != null) {
        val book = selectedBookForMoreInfo!!
        AlertDialog(
            onDismissRequest = { isCoverZoomed = false },
            title = { Text(book.title, style = MaterialTheme.typography.titleMedium) },
            text = {
                Box(modifier = Modifier.fillMaxWidth().height(400.dp).clip(MaterialTheme.shapes.medium)) {
                    if (book.coverUrl.isNotBlank() && book.coverUrl.startsWith("http")) {
                        AsyncImage(model = book.coverUrl, contentDescription = book.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    } else {
                        GeneratedCover(title = book.title, author = book.author, series = book.series, seriesIndex = book.seriesIndex, modifier = Modifier.fillMaxSize())
                    }
                }
            },
            confirmButton = { Button(onClick = { isCoverZoomed = false }) { Text("Close Zoom") } }
        )
    }

    // Batch Tag Dialog
    if (showBatchTagDialog) {
        AlertDialog(
            onDismissRequest = { showBatchTagDialog = false },
            title = { Text("Mass Tagging (${selectedBookIds.size} books)") },
            text = {
                Column {
                    OutlinedTextField(value = batchTagInput, onValueChange = { batchTagInput = it }, label = { Text("New Tags (comma separated)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.batchUpdateTags(selectedBookIds, batchTagInput, true)
                    showBatchTagDialog = false
                    batchTagInput = ""
                    Toast.makeText(context, "Tags applied successfully!", Toast.LENGTH_SHORT).show()
                }) { Text("Apply Tags") }
            },
            dismissButton = { TextButton(onClick = { showBatchTagDialog = false }) { Text("Cancel") } }
        )
    }

    // Batch Status Dialog
    if (showBatchStatusDialog) {
        AlertDialog(
            onDismissRequest = { showBatchStatusDialog = false },
            title = { Text("Mass Status / Reading List (${selectedBookIds.size} books)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("READING", "COMPLETED", "DNF", "WISHLIST").forEach { status ->
                        OutlinedButton(onClick = {
                            viewModel.batchUpdateStatus(selectedBookIds, status)
                            showBatchStatusDialog = false
                            Toast.makeText(context, "Status set to $status", Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.fillMaxWidth()) { Text(status) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBatchStatusDialog = false }) { Text("Cancel") } }
        )
    }

    // Batch Date Dialog
    if (showBatchDateDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDateDialog = false },
            title = { Text("Set Read Date En Masse") },
            text = {
                Column {
                    OutlinedTextField(value = batchDateInput, onValueChange = { batchDateInput = it }, label = { Text("Read Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.batchUpdateReadDate(selectedBookIds, batchDateInput)
                    showBatchDateDialog = false
                    Toast.makeText(context, "Read date recorded!", Toast.LENGTH_SHORT).show()
                }) { Text("Set Date") }
            },
            dismissButton = { TextButton(onClick = { showBatchDateDialog = false }) { Text("Cancel") } }
        )
    }

    // LibrManage Edit Modal
    if (selectedBookForEdit != null) {
        val book = selectedBookForEdit!!
        var editTitle by remember { mutableStateOf(book.title) }
        var editAuthor by remember { mutableStateOf(book.author) }
        var editSeries by remember { mutableStateOf(book.series) }
        var editTags by remember { mutableStateOf(book.tags) }

        AlertDialog(
            onDismissRequest = { viewModel.setSelectedBookForEdit(null) },
            title = { Text("LibrManage: Edit Book Metadata") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editAuthor, onValueChange = { editAuthor = it }, label = { Text("Author") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editSeries, onValueChange = { editSeries = it }, label = { Text("Series") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editTags, onValueChange = { editTags = it }, label = { Text("Tags (comma separated)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateBook(book.copy(title = editTitle, author = editAuthor, series = editSeries, tags = editTags))
                    viewModel.setSelectedBookForEdit(null)
                    Toast.makeText(context, "Metadata updated successfully!", Toast.LENGTH_SHORT).show()
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { viewModel.setSelectedBookForEdit(null) }) { Text("Cancel") } }
        )
    }

    // Dynamic Library Picker & Custom Dynamic Rule Builder Dialog
    if (showDynamicLibraryDialog) {
        AlertDialog(
            onDismissRequest = { showDynamicLibraryDialog = false },
            title = { Text("Dynamic Library Picker & Rule Builder") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().height(450.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Presets & Saved Dynamic Lists:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    viewModel.presetDynamicLibraries.forEach { rule ->
                        OutlinedButton(onClick = {
                            viewModel.setDynamicLibrary(rule)
                            showDynamicLibraryDialog = false
                        }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Label, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(rule.name)
                        }
                    }

                    savedDynamicRules.forEach { rule ->
                        OutlinedButton(onClick = {
                            viewModel.setDynamicLibrary(rule)
                            showDynamicLibraryDialog = false
                        }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saved: ${rule.name}")
                        }
                    }

                    Text("Smart Shelves (Auto-Updating):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    smartShelves.forEach { rule ->
                        OutlinedButton(onClick = {
                            viewModel.setDynamicLibrary(rule)
                            showDynamicLibraryDialog = false
                        }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(rule.name)
                        }
                    }

                    Divider()

                    Text("Build Custom Dynamic Rule (AND / OR logic):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = newRuleName, onValueChange = { newRuleName = it }, label = { Text("Rule / List Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = newRuleAuthor, onValueChange = { newRuleAuthor = it }, label = { Text("Author(s)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = useOrForAuthor, onCheckedChange = { useOrForAuthor = it })
                        Text("Use OR logic for authors (otherwise AND)", style = MaterialTheme.typography.bodySmall)
                    }

                    OutlinedTextField(value = newRuleTags, onValueChange = { newRuleTags = it }, label = { Text("Tags / Genres") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = useOrForTags, onCheckedChange = { useOrForTags = it })
                        Text("Use OR logic for tags (otherwise AND)", style = MaterialTheme.typography.bodySmall)
                    }

                    OutlinedTextField(value = newRuleShelf, onValueChange = { newRuleShelf = it }, label = { Text("Custom Shelf") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = useOrForShelf, onCheckedChange = { useOrForShelf = it })
                        Text("Use OR logic for shelves", style = MaterialTheme.typography.bodySmall)
                    }

                    Text("Sensitive Content / Tropes ('Does the dog die?'):", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = sensitiveContentPassState == true, onClick = { sensitiveContentPassState = if (sensitiveContentPassState == true) null else true }, label = { Text("True (Has Warning)") })
                        FilterChip(selected = sensitiveContentPassState == false, onClick = { sensitiveContentPassState = if (sensitiveContentPassState == false) null else false }, label = { Text("False (No Warning)") })
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        if (newRuleName.isNotBlank()) {
                            val rule = DynamicLibraryRule(
                                name = newRuleName,
                                authorFilter = newRuleAuthor,
                                useOrForAuthor = useOrForAuthor,
                                tagQuery = newRuleTags,
                                useOrLogicForTags = useOrForTags,
                                shelfFilter = newRuleShelf,
                                useOrForShelf = useOrForShelf,
                                sensitiveContentPass = sensitiveContentPassState
                            )
                            viewModel.saveDynamicRule(rule)
                            viewModel.setDynamicLibrary(rule)
                            showDynamicLibraryDialog = false
                            Toast.makeText(context, "Dynamic list saved and applied!", Toast.LENGTH_SHORT).show()
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Save & Apply Dynamic List")
                    }

                    OutlinedButton(onClick = {
                        viewModel.setDynamicLibrary(null)
                        showDynamicLibraryDialog = false
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text("Clear Dynamic Filter (Show All)")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDynamicLibraryDialog = false }) { Text("Close") } }
        )
    }
}

@Composable
fun MetadataSectionContent(
    book: BookEntity,
    tagsList: List<String>,
    seriesBooks: List<BookEntity>,
    authorBooks: List<BookEntity>,
    viewModel: LibraryViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Author: ${book.author}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            if (book.series.isNotBlank()) {
                Text("Series: ${book.series} (#${book.seriesIndex.toInt()})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }

        Divider()

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Classification & Shelves", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                    Text("Fiction", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                    Text(book.format, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                    Text(book.status, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            if (tagsList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tagsList.forEach { tag ->
                        Surface(
                            color = if (tag.equals("demo", ignoreCase = true)) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(if (tag.equals("demo", ignoreCase = true)) "🏷️ (New! Demo)" else "#$tag", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                        }
                    }
                }
            }
        }

        Divider()

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Plot Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                text = book.description.ifBlank { "No summary available for this book." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Divider()

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Store Ratings & AI Community Insights", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("⭐ Store Average: 4.8 / 5.0 (Across Stores)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Text("💬 AI Popular Opinion: Highly praised for fast pacing and immersive worldbuilding.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            Text("⚠️ Content Warnings / Tropes (Does the dog die?): Verified safe (No animal harm).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Divider()

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("File & Storage Path", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Path: ${book.filePath}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }

        Divider()

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Related Books", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (seriesBooks.isNotEmpty()) {
                Text("Rest of Series:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                seriesBooks.forEach { sb ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.setSelectedBookForMoreInfo(sb) },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("${sb.title} (#${sb.seriesIndex.toInt()})", modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (authorBooks.isNotEmpty()) {
                Text("Same Author:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                authorBooks.take(3).forEach { ab ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.setSelectedBookForMoreInfo(ab) },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(ab.title, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCard(
    book: BookEntity,
    isSelected: Boolean,
    isMultiSelectActive: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onSelectToggle: () -> Unit,
    onFavoriteClick: () -> Unit,
    onMoreInfoClick: () -> Unit,
    onEditClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(290.dp)
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
            .testTag("book_item_${book.id}"),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                if (book.coverUrl.isNotBlank() && book.coverUrl.startsWith("http")) {
                    AsyncImage(
                        model = book.coverUrl,
                        contentDescription = book.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.large),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    GeneratedCover(
                        title = book.title,
                        author = book.author,
                        series = book.series,
                        seriesIndex = book.seriesIndex,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.large)
                    )
                }

                if (isMultiSelectActive || isSelected) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelectToggle() },
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                    )
                }

                Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Actions", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("More Info") },
                            onClick = { showMenu = false; onMoreInfoClick() },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit Metadata") },
                            onClick = { showMenu = false; onEditClick() },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (book.isFavorite) "Unfavorite" else "Favorite") },
                            onClick = { showMenu = false; onFavoriteClick() },
                            leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isSelected) "Deselect" else "Select") },
                            onClick = { showMenu = false; onSelectToggle() },
                            leadingIcon = { Icon(Icons.Default.CheckBox, contentDescription = null) }
                        )
                    }
                }

                Surface(
                    color = if (book.hasCopy) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = if (book.hasCopy) book.format else "🔍 Wishlist",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = if (book.hasCopy) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (book.hasCopy) {
                    LinearProgressIndicator(
                        progress = { book.progressPercent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(MaterialTheme.shapes.small),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                } else {
                    Text("Needs Acquisition", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookListItem(
    book: BookEntity,
    isSelected: Boolean,
    isMultiSelectActive: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onSelectToggle: () -> Unit,
    onFavoriteClick: () -> Unit,
    onMoreInfoClick: () -> Unit,
    onEditClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelectToggle() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.size(50.dp, 75.dp)) {
                GeneratedCover(
                    title = book.title,
                    author = book.author,
                    series = book.series,
                    seriesIndex = book.seriesIndex,
                    modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.small)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                if (book.series.isNotBlank()) {
                    Text("${book.series} #${book.seriesIndex.toInt()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Actions")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("More Info") }, onClick = { showMenu = false; onMoreInfoClick() })
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { showMenu = false; onEditClick() })
                    DropdownMenuItem(text = { Text("Favorite") }, onClick = { showMenu = false; onFavoriteClick() })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookSmallCard(book: BookEntity, isSelected: Boolean, onTap: () -> Unit, onLongPress: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                GeneratedCover(
                    title = book.title,
                    author = book.author,
                    series = book.series,
                    seriesIndex = book.seriesIndex,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text(
                text = book.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(6.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailedBookCard(
    book: BookEntity,
    isSelected: Boolean,
    isMultiSelectActive: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onSelectToggle: () -> Unit,
    onFavoriteClick: () -> Unit,
    onMoreInfoClick: () -> Unit,
    onEditClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectToggle() }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.size(80.dp, 120.dp)) {
                    GeneratedCover(
                        title = book.title,
                        author = book.author,
                        series = book.series,
                        seriesIndex = book.seriesIndex,
                        modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(book.format, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Actions")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text("More Info") }, onClick = { showMenu = false; onMoreInfoClick() })
                                DropdownMenuItem(text = { Text("Edit") }, onClick = { showMenu = false; onEditClick() })
                                DropdownMenuItem(text = { Text("Favorite") }, onClick = { showMenu = false; onFavoriteClick() })
                            }
                        }
                    }
                    Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(book.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                    if (book.series.isNotBlank()) {
                        Text("Series: ${book.series} #${book.seriesIndex.toInt()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = book.description.ifBlank { "Summary: No description or Goodreads comments loaded yet. Tap for more info and options." },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
