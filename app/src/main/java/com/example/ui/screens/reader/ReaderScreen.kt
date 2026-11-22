package com.example.ui.screens.reader

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ReaderThemes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBackClick: () -> Unit
) {
    val book by viewModel.book.collectAsState()
    val theme by viewModel.currentTheme.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val aiSummary by viewModel.aiSummary.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showAiSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }

    val sampleChapterText = """
        Chapter 1: The Beginning of the End
        
        The sky over the city of Seoul bore an eerie violet hue, reminiscent of bruised twilight. Kim Dokja stared out the cracked window of his cramped office cubicle, his fingers resting lazily on his smartphone screen. For over a decade, he had been the sole reader of a web novel titled 'Three Ways to Survive the Apocalypse'. 
        
        No one else had stuck with it. The author had stopped responding to comments long ago, and the view count dwindled to a lonely single digit. Yet, Dokja knew every twist, every hidden trait, and every impending catastrophe described in its three thousand chapters.
        
        Then, the subway clock struck 7:00 PM.
        
        A sudden blinding flash illuminated the subterranean tracks outside. Sirens wailed in the distance, followed by a deep, resonant rumble that shook the concrete foundations of the building. The subway screen flickered violently, replacing the transit schedule with a glowing crimson message:
        
        [The free service of the main scenario has ended.]
        [The main scenario #1 has begun.]
        
        Dokja felt his breath catch in his throat. The impossible had just become reality. The world he knew was gone, replaced by the lethal rules of his favorite web novel.
        
        "If I know the plot," Dokja whispered to himself, adjusting his collar with a steadying breath, "I can survive."
    """.trimIndent()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = book?.title ?: "Loading...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = theme.text
                        )
                        Text(
                            text = book?.author ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.secondaryText
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = theme.text)
                    }
                },
                actions = {
                    IconButton(onClick = { showAiSheet = true }, modifier = Modifier.testTag("ai_summary_btn")) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Summary", tint = theme.accent)
                    }
                    IconButton(onClick = { showBookmarksSheet = true }) {
                        Icon(Icons.Default.Bookmark, contentDescription = "Bookmarks", tint = theme.text)
                    }
                    IconButton(onClick = { showSettingsSheet = true }, modifier = Modifier.testTag("reader_settings_btn")) {
                        Icon(Icons.Default.Settings, contentDescription = "Reader Settings", tint = theme.text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.surface)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(theme.background)
        ) {
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(24.dp)
                    .testTag("reader_content_scroll")
            ) {
                Text(
                    text = sampleChapterText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = fontSize.sp,
                        fontFamily = FontFamily.Serif,
                        lineHeight = (fontSize * 1.5f).sp
                    ),
                    color = theme.text
                )
                Spacer(modifier = Modifier.height(64.dp))
            }

            // Bottom Progress bar overlay
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                color = theme.surface.copy(alpha = 0.9f),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Progress: ${((book?.progressPercent ?: 0f) * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.secondaryText
                    )
                    Button(
                        onClick = { viewModel.addBookmark("Chapter 1", "Marked at ${System.currentTimeMillis()}") }
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bookmark")
                    }
                }
            }
        }
    }

    // Reader Settings Bottom Sheet
    if (showSettingsSheet) {
        ModalBottomSheet(onDismissRequest = { showSettingsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text("Reader Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                Text("Font Size: ${fontSize.toInt()}sp", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = fontSize,
                    onValueChange = { viewModel.setFontSize(it) },
                    valueRange = 12f..32f,
                    steps = 10
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("Reading Theme", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReaderThemes.all.take(3).forEach { th ->
                        ElevatedButton(
                            onClick = { viewModel.setTheme(th) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(th.name, maxLines = 1)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReaderThemes.all.drop(3).forEach { th ->
                        ElevatedButton(
                            onClick = { viewModel.setTheme(th) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(th.name, maxLines = 1)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // AI Summary Bottom Sheet
    if (showAiSheet) {
        ModalBottomSheet(onDismissRequest = { showAiSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gemini AI Reading Companion", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (isAiLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Text(
                        text = aiSummary ?: "Tap below to generate an AI summary and plot analysis for this chapter.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.generateAiChapterSummary(sampleChapterText) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Generate Chapter Summary & Insights")
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
