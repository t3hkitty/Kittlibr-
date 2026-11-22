package com.example.ui.screens.stats

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import com.example.data.repository.LibraryRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(libraryRepository: LibraryRepository) {
    val context = LocalContext.current
    val allBooks by libraryRepository.allBooks.collectAsState(initial = emptyList())

    // Calculations for Shelf counts
    val totalBooks = allBooks.size
    val readingCount = allBooks.count { it.status.equals("READING", ignoreCase = true) || it.progressPercent > 0f && it.progressPercent < 1f }
    val completedCount = allBooks.count { it.status.equals("COMPLETED", ignoreCase = true) || it.progressPercent >= 1f }
    val wishlistCount = allBooks.count { !it.hasCopy || it.status.equals("WISHLIST", ignoreCase = true) }
    val dnfCount = allBooks.count { it.status.equals("DNF", ignoreCase = true) }

    // Reading pace & Projections (Daily, Weekly, Monthly, Annual)
    val dailyPacePages = 45
    val weeklyPacePages = dailyPacePages * 7
    val monthlyPacePages = dailyPacePages * 30
    val annualPacePages = dailyPacePages * 365

    val averagePageCountPerBook = 320
    val totalRemainingPages = maxOf(0, (totalBooks - completedCount) * averagePageCountPerBook)
    val etaDays = if (dailyPacePages > 0) totalRemainingPages / dailyPacePages else 999
    val etaMonths = etaDays / 30

    val completionProgress = if (totalBooks > 0) completedCount.toFloat() / totalBooks.toFloat() else 0f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reading Stats & Projections", fontWeight = FontWeight.Bold) },
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
            // Library Book Counts & Shelf Breakdown Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Library Book Counts & Shelves", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Total Library Size: $totalBooks books", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ShelfCountBadge("Reading ($readingCount)", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                        ShelfCountBadge("Completed ($completedCount)", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                        ShelfCountBadge("Wishlist ($wishlistCount)", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                        ShelfCountBadge("DNF ($dnfCount)", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            // Reading Completion Progress & ETA Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timeline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reading Progress & ETA for Everything", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Estimated time to read all books in library at current pace: ~${etaMonths} months ($etaDays days remaining).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { completionProgress },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(MaterialTheme.shapes.small),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Completed: $completedCount", style = MaterialTheme.typography.labelSmall)
                        Text("Remaining: ${totalBooks - completedCount}", style = MaterialTheme.typography.labelSmall)
                        Text("Total: $totalBooks", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Pace & Projections Card (Active vs Daily, Weekly, Monthly, Annual)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reading Pace & Projections", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    ProjectionRow(label = "Active Reading Pace", value = "$dailyPacePages pages / day")
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    ProjectionRow(label = "Weekly Projection", value = "$weeklyPacePages pages (~${weeklyPacePages / averagePageCountPerBook + 1} books)")
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    ProjectionRow(label = "Monthly Projection", value = "$monthlyPacePages pages (~${monthlyPacePages / averagePageCountPerBook} books)")
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    ProjectionRow(label = "Annual Projection", value = "$annualPacePages pages (~${annualPacePages / averagePageCountPerBook} books)")
                }
            }

            // Streak Card (Self-Comparison)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("5 Day Reading Streak!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("+15% more reading time vs. your own previous week's average.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }

            // Stat Cards Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Time Read",
                    value = "4h 25m",
                    icon = Icons.Default.Timer
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Pages Turned",
                    value = "312",
                    icon = Icons.Default.MenuBook
                )
            }

            // Google Play Games Style Achievements Hub
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SportsEsports, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Google Play Achievements & Quests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    AchievementItem(
                        title = "Pacific Voyager 🌊",
                        description = "Read the entire width of the Pacific Ocean (10,000 pages turned milestone).",
                        progress = "312 / 10,000 pages",
                        progressFloat = 0.03f,
                        isUnlocked = false
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AchievementItem(
                        title = "Angler Fish Encounter 🐟🔦",
                        description = "Read past 2:00 AM using OLED True Black theme mode.",
                        progress = "Unlocked!",
                        progressFloat = 1.0f,
                        isUnlocked = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AchievementItem(
                        title = "Polyglot Explorer 🌍",
                        description = "Discover and read books from other countries after enabling international sources.",
                        progress = "Unlocked!",
                        progressFloat = 1.0f,
                        isUnlocked = true
                    )
                }
            }

            // Share Achievement Card for Social Media
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Reading Achievement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Share your reading streak, Google Play achievements, and stats as a graphic & text card to social media.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val shareText = "🏆 My Kittlibr Gaming & Reading Achievements:\n🔥 5 Day Reading Streak! (+15% vs last week)\n🌊 Pacific Voyager Quest: 312 / 10,000 pages\n🔦 Angler Fish Encounter: Unlocked (OLED Midnight Reader)\n🌍 Polyglot Explorer: Unlocked\n\n#Kittlibr #GooglePlayGames #ReadingCommunity"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "My Kittlibr Reading Stats & Achievements")
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            try {
                                context.startActivity(Intent.createChooser(intent, "Share Reading Achievement"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Share sheet launched", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Achievement Card to Social Media")
                    }
                }
            }
        }
    }
}

@Composable
fun ShelfCountBadge(text: String, containerColor: androidx.compose.ui.graphics.Color, contentColor: androidx.compose.ui.graphics.Color) {
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            color = contentColor
        )
    }
}

@Composable
fun ProjectionRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
fun AchievementItem(title: String, description: String, progress: String, progressFloat: Float, isUnlocked: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isUnlocked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = LabelSpaceBetween(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Surface(
                    color = if (isUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = if (isUnlocked) "UNLOCKED" else "LOCKED",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progressFloat },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(progress, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

fun LabelSpaceBetween() = Arrangement.SpaceBetween
