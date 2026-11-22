package com.example.ui.screens.discovery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

data class MetadataSubscription(
    val id: String,
    val fieldName: String,
    val queryValue: String,
    var isEnabled: Boolean
)

data class OnlineSearchResult(
    val title: String,
    val author: String,
    val matchedCriteria: String,
    val updateNotice: String
)

data class NotificationHistoryItem(
    val id: String,
    val subscriptionTitle: String,
    val message: String,
    val timestamp: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    viewModel: DiscoveryViewModel,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val recommendations by viewModel.recommendations.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val customCombos by viewModel.customCombos.collectAsState()
    val genreSuggestions by viewModel.genreSuggestions.collectAsState()
    val isGenreLoading by viewModel.isGenreLoading.collectAsState()

    var selectedDiscoverySubTab by remember { mutableIntStateOf(0) }
    val discoverySubTabs = listOf("AI Recommendations", "Metadata Subscriptions", "Online Matcher", "Notification History")

    var selectedGenreCombo by remember { mutableStateOf("LitRPG + Yaoi") }
    var newComboInput by remember { mutableStateOf("") }

    // Notification Permission Launcher
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        Toast.makeText(context, if (isGranted) "Notification permission granted!" else "Notification permission denied", Toast.LENGTH_SHORT).show()
    }

    // Metadata Subscriptions State
    var subscriptions by remember {
        mutableStateOf(
            listOf(
                MetadataSubscription("1", "Author", "Sing-Shong", true),
                MetadataSubscription("2", "Series", "Shadow Slave", true),
                MetadataSubscription("3", "Genre / Tag", "ProgressionFantasy", true),
                MetadataSubscription("4", "Trope / Warning", "Does the dog die: false", true)
            )
        )
    }
    var showAddSubDialog by remember { mutableStateOf(false) }
    var newSubField by remember { mutableStateOf("Author") }
    var newSubQuery by remember { mutableStateOf("") }

    // Notification History State
    var notificationHistory by remember {
        mutableStateOf(
            listOf(
                NotificationHistoryItem("1", "Author: Sing-Shong", "New Chapter Published: Omniscient Reader's Viewpoint Side Story 50", "Today, 4:15 AM"),
                NotificationHistoryItem("2", "Series: Shadow Slave", "New Match Found: Chapter 1720 released online", "Yesterday, 11:30 PM"),
                NotificationHistoryItem("3", "Genre: ProgressionFantasy", "New Book Matched: Reincarnated as a System Wizard", "Nov 20, 2:00 PM")
            )
        )
    }

    // Online Searcher Matcher state
    var searchAuthorQuery by remember { mutableStateOf("") }
    var searchSeriesQuery by remember { mutableStateOf("") }
    var searchGenreQuery by remember { mutableStateOf("") }
    var searchResults by remember {
        mutableStateOf<List<OnlineSearchResult>>(
            listOf(
                OnlineSearchResult("Omniscient Reader's Viewpoint - Side Story 50", "Sing-Shong", "Author: Sing-Shong", "New Chapter Published 10m ago!"),
                OnlineSearchResult("Shadow Slave - Chapter 1720", "Guiltythree", "Series: Shadow Slave", "Matched subscription alert!")
            )
        )
    }
    var isSearchingOnline by remember { mutableStateOf(false) }

    // Open Library Rec Lists state
    var selectedOpenLibraryList by remember { mutableStateOf("Trending Science Fiction & Fantasy") }
    var openLibraryRecs by remember {
        mutableStateOf(
            listOf(
                Triple("The Hitchhiker's Guide to the Galaxy", "Douglas Adams", "Open Library Sci-Fi All-Time Top 10"),
                Triple("Dune", "Frank Herbert", "Open Library Classic Masterpieces"),
                Triple("Neuromancer", "William Gibson", "Open Library Cyberpunk Essentials"),
                Triple("The Way of Kings", "Brandon Sanderson", "Open Library Epic Fantasy Favorites")
            )
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Story Discovery & Subscriptions Hub", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Navigation Drawer")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
                ScrollableTabRow(
                    selectedTabIndex = selectedDiscoverySubTab,
                    edgePadding = 16.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    discoverySubTabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedDiscoverySubTab == index,
                            onClick = { selectedDiscoverySubTab = index },
                            text = { Text(title) },
                            icon = {
                                when(index) {
                                    0 -> Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                    1 -> Icon(Icons.Default.Subscriptions, contentDescription = null)
                                    2 -> Icon(Icons.Default.Public, contentDescription = null)
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
            when (selectedDiscoverySubTab) {
                0 -> {
                    // AI & Open Library Recs
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Open Library Recommendation Lists",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Curated recommendation lists directly from Open Library API and community curators.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            val openLibLists = listOf(
                                "Trending Science Fiction & Fantasy",
                                "Open Library All-Time Classics",
                                "Award Winning Web Novels & Books",
                                "Hidden Gems & Indie Masterpieces"
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                openLibLists.take(2).forEach { listName ->
                                    FilterChip(
                                        selected = selectedOpenLibraryList == listName,
                                        onClick = {
                                            selectedOpenLibraryList = listName
                                            openLibraryRecs = when(listName) {
                                                "Open Library All-Time Classics" -> listOf(
                                                    Triple("Pride and Prejudice", "Jane Austen", "Open Library Classics"),
                                                    Triple("Moby Dick", "Herman Melville", "Open Library Classics"),
                                                    Triple("Frankenstein", "Mary Shelley", "Open Library Classics")
                                                )
                                                "Award Winning Web Novels & Books" -> listOf(
                                                    Triple("The Three-Body Problem", "Liu Cixin", "Hugo Award Winner"),
                                                    Triple("American Gods", "Neil Gaiman", "Nebula Award Winner")
                                                )
                                                else -> listOf(
                                                    Triple("The Hitchhiker's Guide to the Galaxy", "Douglas Adams", "Open Library Sci-Fi Top 10"),
                                                    Triple("Dune", "Frank Herbert", "Open Library Classic Masterpieces"),
                                                    Triple("Neuromancer", "William Gibson", "Open Library Cyberpunk Essentials")
                                                )
                                            }
                                            Toast.makeText(context, "Loaded list: $listName", Toast.LENGTH_SHORT).show()
                                        },
                                        label = { Text(listName, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                openLibraryRecs.forEach { (title, author, badge) ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                Text("$author • $badge", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                            }
                                            Button(
                                                onClick = {
                                                    viewModel.addBookFromDiscovery(title, author, badge)
                                                    Toast.makeText(context, "Added '$title' to Library!", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Acquire")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // AI Taste Profile Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Gemini Taste Profile Discovery",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Analyzes your historical book records and imported library files to discover tailored web novels and books.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { viewModel.generateAiTasteRecommendations() },
                                modifier = Modifier.fillMaxWidth().testTag("generate_taste_btn")
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Text("Generate AI Recommendations")
                                }
                            }
                        }
                    }

                    if (recommendations != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("Recommended For You", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(recommendations ?: "", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                1 -> {
                    // Metadata Subscriptions Tab
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Subscriptions, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Subscribable Metadata Fields", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Button(onClick = { showAddSubDialog = true }) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Subscription")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Subscribe to any metadata field to receive system notifications when new matching chapters or books are found online.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.height(16.dp))

                            // Notification permission prompt if not granted
                            if (!hasNotificationPermission) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("System notifications permission required for alerts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                                        Button(onClick = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        }) {
                                            Text("Grant", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                subscriptions.forEach { sub ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("[${sub.fieldName}] ${sub.queryValue}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                Text(if (sub.isEnabled) "System Notifications Active" else "Paused", style = MaterialTheme.typography.labelSmall, color = if (sub.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                            }
                                            Switch(
                                                checked = sub.isEnabled,
                                                onCheckedChange = { checked ->
                                                    subscriptions = subscriptions.map { if (it.id == sub.id) it.copy(isEnabled = checked) else it }
                                                    Toast.makeText(context, "Subscription updated", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Online Searcher Matcher Tab
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TravelExplore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Online New Story Matcher", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Search online sources in real time for new stories matching your criteria across author name, series, genre, and tropes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = searchAuthorQuery,
                                onValueChange = { searchAuthorQuery = it },
                                label = { Text("Author Name (e.g. Brandon Sanderson)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = searchSeriesQuery,
                                onValueChange = { searchSeriesQuery = it },
                                label = { Text("Series (e.g. Shadow Slave)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = searchGenreQuery,
                                onValueChange = { searchGenreQuery = it },
                                label = { Text("Genre / Trope (e.g. LitRPG)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    isSearchingOnline = true
                                    Toast.makeText(context, "Searching online sources...", Toast.LENGTH_SHORT).show()
                                    // simulate results and add to notification history
                                    val matchedTitle = "New Match: $searchSeriesQuery by $searchAuthorQuery"
                                    searchResults = listOf(
                                        OnlineSearchResult(matchedTitle, searchAuthorQuery.ifBlank { "Author" }, "Criteria: $searchGenreQuery", "Found 1 matching new release online!")
                                    )
                                    val newHistoryItem = NotificationHistoryItem(
                                        id = System.currentTimeMillis().toString(),
                                        subscriptionTitle = "Online Matcher: $searchSeriesQuery",
                                        message = "System Notification Dispatched: New release found for $searchSeriesQuery",
                                        timestamp = "Just now"
                                    )
                                    notificationHistory = listOf(newHistoryItem) + notificationHistory
                                    Toast.makeText(context, "🔔 System Notification Dispatched!", Toast.LENGTH_LONG).show()
                                    isSearchingOnline = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isSearchingOnline) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Search Online & Trigger System Notification")
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Live Match Results:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                searchResults.forEach { res ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(res.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                            Text("Author: ${res.author} • ${res.matchedCriteria}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                            Text(res.updateNotice, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    // Notification History Tab
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Notification History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = { notificationHistory = emptyList() }) {
                                    Text("Clear History", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Log of all dispatched system notifications for your metadata subscriptions and online match alerts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.height(16.dp))

                            if (notificationHistory.isEmpty()) {
                                Text("No notification history available.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    notificationHistory.forEach { item ->
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(item.subscriptionTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                    Text(item.timestamp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(item.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSubDialog) {
        AlertDialog(
            onDismissRequest = { showAddSubDialog = false },
            title = { Text("Add Metadata Subscription") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newSubField,
                        onValueChange = { newSubField = it },
                        label = { Text("Metadata Field (Author, Series, Genre, Trope)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newSubQuery,
                        onValueChange = { newSubQuery = it },
                        label = { Text("Query Value") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSubQuery.isNotBlank()) {
                            val newSub = MetadataSubscription(
                                id = System.currentTimeMillis().toString(),
                                fieldName = newSubField,
                                queryValue = newSubQuery,
                                isEnabled = true
                            )
                            subscriptions = subscriptions + newSub
                            Toast.makeText(context, "Subscription added successfully!", Toast.LENGTH_SHORT).show()
                            newSubQuery = ""
                            showAddSubDialog = false
                        }
                    }
                ) {
                    Text("Subscribe")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSubDialog = false }) { Text("Cancel") }
            }
        )
    }
}
