@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.HabitLog
import com.example.data.HabitSetWithTasks
import com.example.ui.HabitViewModel
import com.example.util.HabitAnalyticsService
import com.example.util.Scheduler
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ProfileScreen(
    viewModel: HabitViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allLogs by viewModel.allLogs.collectAsState()
    val habitSetsWithTasks by viewModel.filteredHabitSets.collectAsState()

    // 1. SharedPreferences Profile state
    val sharedPrefs = remember { context.getSharedPreferences("habitgrid_profile", Context.MODE_PRIVATE) }
    
    var profileName by remember { mutableStateOf(sharedPrefs.getString("name", "Sonu Kumawat") ?: "Sonu Kumawat") }
    var profileBio by remember { mutableStateOf(sharedPrefs.getString("bio", "Building better habits, every day.") ?: "Building better habits, every day.") }
    var avatarColorIndex by remember { mutableStateOf(sharedPrefs.getInt("avatar_color", 0)) }
    var avatarEmoji by remember { mutableStateOf(sharedPrefs.getString("avatar_emoji", "🚀") ?: "🚀") }
    
    var showEditDialog by remember { mutableStateOf(false) }

    // Color definitions for Avatar preset backgrounds
    val avatarColors = listOf(
        Color(0xFF673AB7), // Purple
        Color(0xFF009688), // Teal
        Color(0xFFFF9800), // Orange/Amber
        Color(0xFFE91E63), // Pink/Crimson
        Color(0xFF2196F3), // Blue
        Color(0xFF4CAF50)  // Green
    )
    val currentAvatarColor = avatarColors.getOrElse(avatarColorIndex) { avatarColors[0] }

    val avatarEmojis = listOf("🚀", "🪐", "🎯", "🍀", "🌱", "🧘", "👑", "🔥", "⚡", "✨")

    // 2. Compute Global Achievements
    val totalCompletedTasks = remember(allLogs) {
        allLogs.count { HabitAnalyticsService.isCompletedStatus(it.status) }
    }

    // Identify first perfect day (daily productivity = 100%)
    val firstPerfectDayDate = remember(habitSetsWithTasks, allLogs) {
        findFirstPerfectDay(habitSetsWithTasks, allLogs)
    }

    // Determine global max streak of perfect days
    val globalMaxStreak = remember(habitSetsWithTasks, allLogs) {
        calculateGlobalBestStreak(habitSetsWithTasks, allLogs)
    }

    // List of achievement configs
    val achievements = remember(totalCompletedTasks, firstPerfectDayDate, globalMaxStreak) {
        listOf(
            AchievementConfig(
                id = "first_perfect_day",
                name = "First Perfect Day",
                description = "Earn a flawless tick on all scheduled checklists for a single day.",
                isUnlocked = firstPerfectDayDate != null,
                reqProgress = 1,
                currProgress = if (firstPerfectDayDate != null) 1 else 0,
                progressUnit = "Perfect Day Completed",
                unlockDate = firstPerfectDayDate?.let { formatDate(it) },
                icon = Icons.Default.WorkspacePremium,
                badgeColor = Color(0xFFFFBF00) // Amber
            ),
            AchievementConfig(
                id = "streak_7",
                name = "First 7-Day Streak",
                description = "Keep your habits aligned on a consistent 7-day perfect run.",
                isUnlocked = globalMaxStreak >= 7,
                reqProgress = 7,
                currProgress = globalMaxStreak,
                progressUnit = "Consecutive Days",
                unlockDate = if (globalMaxStreak >= 7) "Unlocked" else null,
                icon = Icons.Default.LocalFireDepartment,
                badgeColor = Color(0xFFFF5722) // Coral
            ),
            AchievementConfig(
                id = "streak_30",
                name = "First 30-Day Streak",
                description = "Demonstrate absolute mastery with a magnificent 30-day perfect streak.",
                isUnlocked = globalMaxStreak >= 30,
                reqProgress = 30,
                currProgress = globalMaxStreak,
                progressUnit = "Consecutive Days",
                unlockDate = if (globalMaxStreak >= 30) "Unlocked" else null,
                icon = Icons.Default.Stars,
                badgeColor = Color(0xFF9C27B0) // Deep Purple
            ),
            AchievementConfig(
                id = "tasks_100",
                name = "100 Tasks Met",
                description = "Unlock premium momentum across 100 fully completed task checklists.",
                isUnlocked = totalCompletedTasks >= 100,
                reqProgress = 100,
                currProgress = totalCompletedTasks,
                progressUnit = "Tasks Completed",
                unlockDate = if (totalCompletedTasks >= 100) "Unlocked" else null,
                icon = Icons.Default.CheckCircle,
                badgeColor = Color(0xFF4CAF50) // Emerald
            ),
            AchievementConfig(
                id = "tasks_500",
                name = "500 Tasks Met",
                description = "Reach elite status with 500 tasks fully executed under HabitGrid.",
                isUnlocked = totalCompletedTasks >= 500,
                reqProgress = 500,
                currProgress = totalCompletedTasks,
                progressUnit = "Tasks Completed",
                unlockDate = if (totalCompletedTasks >= 500) "Unlocked" else null,
                icon = Icons.Default.DoneAll,
                badgeColor = Color(0xFF00BCD4) // Teal cyan
            ),
            AchievementConfig(
                id = "tasks_1000",
                name = "Habit Grandmaster (1000 Tasks)",
                description = "Establish legendary alignment through 1000 checked tasks.",
                isUnlocked = totalCompletedTasks >= 1000,
                reqProgress = 1000,
                currProgress = totalCompletedTasks,
                progressUnit = "Tasks Completed",
                unlockDate = if (totalCompletedTasks >= 1000) "Unlocked" else null,
                icon = Icons.Default.EmojiEvents,
                badgeColor = Color(0xFF2196F3) // Royal Blue
            )
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // TOP HEADER
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "My Profile",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Customize identity and browse master achievements.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                IconButton(
                    onClick = { showEditDialog = true },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), CircleShape)
                        .testTag("edit_profile_entry_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Profile",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // PROFILE HERO CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Profile Avatar with preset custom background & elegant drawn Canvas boy avatar (Image 3)
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .testTag("profile_avatar_box"),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        AvatarIllustration(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                        
                        // Camera overlay badge (Image 3)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE08244)) // Warm Orange-Terracotta to match
                                .clickable { showEditDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Change profile picture",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Text(
                        text = profileName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("profile_user_name")
                    )

                    if (profileBio.isNotBlank()) {
                        Text(
                            text = profileBio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .testTag("profile_user_bio")
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = "Achievements earned",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            val unlockedCount = achievements.count { it.isUnlocked }
                            Text(
                                text = "Unlocked $unlockedCount of ${achievements.size} Badges",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }

        // ACHIEVEMENTS SECTION HEADER
        item {
            Text(
                text = "My Achievements",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // ACHIEVEMENT INDEX CARDS
        items(achievements, key = { it.id }) { ach ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("achievement_card_${ach.id}"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (ach.isUnlocked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (ach.isUnlocked) ach.badgeColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Badge circle status
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                color = if (ach.isUnlocked) ach.badgeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = ach.icon,
                            contentDescription = ach.name,
                            tint = if (ach.isUnlocked) ach.badgeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Achievement details
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ach.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Black,
                                color = if (ach.isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                            )

                            // Locked / Unlocked label badge
                            if (ach.isUnlocked) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Unlocked",
                                        tint = ach.badgeColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Unlocked",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = ach.badgeColor
                                    )
                                }
                            } else {
                                Text(
                                    text = "Locked",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Text(
                            text = ach.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        // If locked, show progress metrics
                        if (!ach.isUnlocked) {
                            val ratio = (ach.currProgress.toFloat() / ach.reqProgress.toFloat()).coerceIn(0f, 1f)
                            Column(
                                modifier = Modifier.padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${ach.currProgress} / ${ach.reqProgress} ${ach.progressUnit}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        text = "${(ratio * 100).toInt()}% Done",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = ratio,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = MaterialTheme.colorScheme.outline,
                                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                )
                            }
                        } else if (ach.unlockDate != null) {
                            Text(
                                text = "Earned on ${ach.unlockDate}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ach.badgeColor.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }

        // NAVIGATION SHORTCUTS
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "App Shortcuts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NavigationCard(
                        modifier = Modifier.weight(1f),
                        title = "Habit Sets",
                        icon = Icons.Default.Layers,
                        baseColor = Color(0xFF673AB7),
                        testTag = "nav_sets_shortcut",
                        onClick = { viewModel.selectedTab.value = "Habit Sets" }
                    )
                    NavigationCard(
                        modifier = Modifier.weight(1f),
                        title = "Progress logs",
                        icon = Icons.Default.BarChart,
                        baseColor = Color(0xFF3F51B5),
                        testTag = "nav_analytics_shortcut",
                        onClick = { viewModel.selectedTab.value = "Analytics" }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NavigationCard(
                        modifier = Modifier.weight(1f),
                        title = "Calendar",
                        icon = Icons.Default.CalendarToday,
                        baseColor = Color(0xFF009688),
                        testTag = "nav_calendar_shortcut",
                        onClick = { viewModel.selectedTab.value = "Calendar" }
                    )
                    NavigationCard(
                        modifier = Modifier.weight(1f),
                        title = "Settings",
                        icon = Icons.Default.Settings,
                        baseColor = Color(0xFF607D8B),
                        testTag = "nav_settings_shortcut",
                        onClick = { viewModel.selectedTab.value = "Settings" }
                    )
                }
            }
        }
    }

    // DIALOG : EDIT USER PROFILE
    if (showEditDialog) {
        var nameInput by remember { mutableStateOf(profileName) }
        var bioInput by remember { mutableStateOf(profileBio) }
        var selectedColorIdx by remember { mutableStateOf(avatarColorIndex) }
        var selectedEmojiVal by remember { mutableStateOf(avatarEmoji) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = {
                Text(
                    text = "Configure Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // PREVIEW OF THE AVATAR
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .align(Alignment.CenterHorizontally)
                            .clip(CircleShape)
                            .background(avatarColors.getOrElse(selectedColorIdx) { avatarColors[0] }),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectedEmojiVal,
                            fontSize = 32.sp
                        )
                    }

                    // Field 1: User Name
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Display Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_name_input"),
                        singleLine = true,
                        placeholder = { Text("Enter your name") }
                    )

                    // Field 2: Bio
                    OutlinedTextField(
                        value = bioInput,
                        onValueChange = { bioInput = it },
                        label = { Text("Short Bio") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_bio_input"),
                        minLines = 2,
                        maxLines = 3,
                        placeholder = { Text("A short personal motivation") }
                    )

                    // Avatar Background Color presets selection
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Choose Avatar Accent Color",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(avatarColors.size) { idx ->
                                val colorVal = avatarColors[idx]
                                val isSelected = idx == selectedColorIdx
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(colorVal)
                                        .clickable { selectedColorIdx = idx }
                                        .testTag("avatar_color_picker_$idx"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Avatar Emoji presets selection
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Choose Icon Avatar Symbol",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(avatarEmojis) { emoji ->
                                val isSelected = emoji == selectedEmojiVal
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                        )
                                        .clickable { selectedEmojiVal = emoji }
                                        .testTag("avatar_emoji_picker_$emoji"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = emoji,
                                        fontSize = 18.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameInput.isBlank()) {
                            Toast.makeText(context, "Display name cannot be empty", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        profileName = nameInput
                        profileBio = bioInput
                        avatarColorIndex = selectedColorIdx
                        avatarEmoji = selectedEmojiVal

                        sharedPrefs.edit()
                            .putString("name", nameInput)
                            .putString("bio", bioInput)
                            .putInt("avatar_color", selectedColorIdx)
                            .putString("avatar_emoji", selectedEmojiVal)
                            .apply()

                        Toast.makeText(context, "Profile successfully saved", Toast.LENGTH_SHORT).show()
                        showEditDialog = false
                    },
                    modifier = Modifier.testTag("profile_save_btn")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditDialog = false },
                    modifier = Modifier.testTag("profile_cancel_btn")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun NavigationCard(
    title: String,
    icon: ImageVector,
    baseColor: Color,
    modifier: Modifier = Modifier,
    testTag: String = "",
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(baseColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = baseColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Model Configuration for Achievements
data class AchievementConfig(
    val id: String,
    val name: String,
    val description: String,
    val isUnlocked: Boolean,
    val reqProgress: Int,
    val currProgress: Int,
    val progressUnit: String,
    val unlockDate: String?,
    val icon: ImageVector,
    val badgeColor: Color
)

// Helper function to format dates nicely
fun formatDate(date: LocalDate): String {
    return date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
}

// SCANNING ALGORITHM TO IDENTIFY THE FIRST FLAWLESS 100% PRODUCTIVITY DAY FOR PROFILE
fun findFirstPerfectDay(
    habitSetsWithTasks: List<HabitSetWithTasks>,
    allLogs: List<HabitLog>
): LocalDate? {
    if (habitSetsWithTasks.isEmpty() || allLogs.isEmpty()) return null

    val startStr = habitSetsWithTasks.map { it.habitSet.startDate }.minOrNull() ?: return null
    val earliestLocalDate = try {
        LocalDate.parse(startStr)
    } catch (e: Exception) {
        LocalDate.now().minusDays(15)
    }

    val today = LocalDate.now()
    var current = earliestLocalDate
    val logsByDateAndTask = allLogs.associateBy { it.date to it.taskId }

    var safety = 0
    while (!current.isAfter(today) && safety < 5000) {
        safety++
        val activeSets = habitSetsWithTasks.filter { it.habitSet.status == "Active" }
        var scheduledCount = 0
        var completedCount = 0

        for (set in activeSets) {
            val scheduledForSet = set.tasks.filter { Scheduler.isTaskScheduled(it, current) }
            scheduledCount += scheduledForSet.size
            for (task in scheduledForSet) {
                val log = logsByDateAndTask[current.toString() to task.id]
                if (HabitAnalyticsService.isCompletedStatus(log?.status)) {
                    completedCount++
                }
            }
        }

        if (scheduledCount > 0 && completedCount == scheduledCount) {
            return current
        }
        current = current.plusDays(1)
    }
    return null
}

// SCANNING ALGORITHM TO COMPUTE MAX STREAK OF PERFECT DAYS RETROACTIVELY FOR PROFILE Achievements
fun calculateGlobalBestStreak(
    habitSetsWithTasks: List<HabitSetWithTasks>,
    allLogs: List<HabitLog>
): Int {
    if (habitSetsWithTasks.isEmpty() || allLogs.isEmpty()) return 0

    val startStr = habitSetsWithTasks.map { it.habitSet.startDate }.minOrNull() ?: return 0
    val earliestLocalDate = try {
        LocalDate.parse(startStr)
    } catch (e: Exception) {
        LocalDate.now().minusDays(15)
    }

    val today = LocalDate.now()
    var current = earliestLocalDate
    var tempStreak = 0
    var bestStreak = 0
    val logsByDateAndTask = allLogs.associateBy { it.date to it.taskId }

    var safety = 0
    while (!current.isAfter(today) && safety < 5000) {
        safety++
        val activeSets = habitSetsWithTasks.filter { it.habitSet.status == "Active" }
        var scheduledCount = 0
        var completedCount = 0

        for (set in activeSets) {
            val scheduledForSet = set.tasks.filter { Scheduler.isTaskScheduled(it, current) }
            scheduledCount += scheduledForSet.size
            for (task in scheduledForSet) {
                val log = logsByDateAndTask[current.toString() to task.id]
                if (HabitAnalyticsService.isCompletedStatus(log?.status)) {
                    completedCount++
                }
            }
        }

        if (scheduledCount > 0) {
            if (completedCount == scheduledCount) {
                tempStreak++
                if (tempStreak > bestStreak) {
                    bestStreak = tempStreak
                }
            } else {
                if (current == today) {
                    // Today is in progress. Don't break streak unless active failure exists
                    var hasFailures = false
                    for (set in activeSets) {
                        val scheduledForSet = set.tasks.filter { Scheduler.isTaskScheduled(it, current) }
                        for (task in scheduledForSet) {
                            val log = logsByDateAndTask[current.toString() to task.id]
                            if (HabitAnalyticsService.isPartialStatus(log?.status) || HabitAnalyticsService.isMissedStatus(log?.status)) {
                                hasFailures = true
                            }
                        }
                    }
                    if (hasFailures) {
                        tempStreak = 0
                    }
                } else {
                    tempStreak = 0
                }
            }
        }
        current = current.plusDays(1)
    }

    return bestStreak
}

@Composable
fun AvatarIllustration(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        
        // Background Circle (warm yellow/cream)
        drawCircle(
            color = Color(0xFFFFE0B2),
            radius = w / 2f
        )
        
        // Orange Hoodie Shoulder area (arc or custom path at the bottom)
        val hoodiePath = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.15f, h * 0.95f)
            quadraticBezierTo(w * 0.2f, h * 0.7f, w * 0.35f, h * 0.72f)
            lineTo(w * 0.65f, h * 0.72f)
            quadraticBezierTo(w * 0.8f, h * 0.7f, w * 0.85f, h * 0.95f)
            close()
        }
        drawPath(hoodiePath, Color(0xFFE08244)) // Warm orange hoodie outer
        
        // Inner hood (darker orange)
        val innerHoodPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.3f, h * 0.73f)
            quadraticBezierTo(w * 0.5f, h * 0.82f, w * 0.7f, h * 0.73f)
            quadraticBezierTo(w * 0.78f, h * 0.62f, w * 0.75f, h * 0.55f)
            quadraticBezierTo(w * 0.5f, h * 0.48f, w * 0.25f, h * 0.55f)
            close()
        }
        drawPath(innerHoodPath, Color(0xFFC76228)) // Darker hoodie inner
        
        // Head & Neck
        drawRect(
            color = Color(0xFFFFD1A9), // Skin color
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.42f, h * 0.55f),
            size = androidx.compose.ui.geometry.Size(w * 0.16f, h * 0.2f)
        )
         
        // Face Circle
        drawCircle(
            color = Color(0xFFFFE2C4), // Slightly lighter skin
            radius = w * 0.22f,
            center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.48f)
        )
         
        // Eyes (drawn as small curved capsule pills or dark circles)
        drawCircle(
            color = Color(0xFF231F20),
            radius = w * 0.02f,
            center = androidx.compose.ui.geometry.Offset(w * 0.42f, h * 0.46f)
        )
        drawCircle(
            color = Color(0xFF231F20),
            radius = w * 0.02f,
            center = androidx.compose.ui.geometry.Offset(w * 0.58f, h * 0.46f)
        )
         
        // Smile / Mouth
        val smilePath = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.46f, h * 0.53f)
            quadraticBezierTo(w * 0.5f, h * 0.57f, w * 0.54f, h * 0.53f)
        }
        drawPath(
            smilePath, 
            Color(0xFFC76228), 
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 3.dp.toPx(), 
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )
         
        // Hair (beautifully styled brown hair arc path on top of the head)
        val hairPath1 = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.24f, h * 0.45f)
            quadraticBezierTo(w * 0.20f, h * 0.32f, w * 0.32f, h * 0.24f)
            quadraticBezierTo(w * 0.5f, h * 0.18f, w * 0.68f, h * 0.24f)
            quadraticBezierTo(w * 0.80f, h * 0.32f, w * 0.76f, h * 0.45f)
            quadraticBezierTo(w * 0.70f, h * 0.34f, w * 0.58f, h * 0.32f)
            quadraticBezierTo(w * 0.40f, h * 0.34f, w * 0.24f, h * 0.45f)
            close()
        }
        drawPath(hairPath1, Color(0xFF5A3825)) // Dark brown hair
         
        // Hair tuft bangs falling on forehead
        val hairBangs = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.26f, h * 0.35f)
            quadraticBezierTo(w * 0.38f, h * 0.32f, w * 0.42f, h * 0.40f)
            quadraticBezierTo(w * 0.48f, h * 0.30f, w * 0.56f, h * 0.38f)
            quadraticBezierTo(w * 0.64f, h * 0.32f, w * 0.74f, h * 0.35f)
            quadraticBezierTo(w * 0.5f, h * 0.26f, w * 0.26f, h * 0.35f)
            close()
        }
        drawPath(hairBangs, Color(0xFF5A3825))
    }
}
