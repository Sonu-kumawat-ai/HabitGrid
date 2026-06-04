@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableStateMapOf
import com.example.data.HabitTask
import com.example.data.HabitSet
import com.example.ui.HabitViewModel
import com.example.util.Scheduler
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun HomeScreen(
    viewModel: HabitViewModel,
    modifier: Modifier = Modifier
) {
    val habitSetsWithTasks by viewModel.unfilteredHabitSets.collectAsState()
    val todayLogs by viewModel.todayLogs.collectAsState()
    val allLogs by viewModel.allLogs.collectAsState()
    val today = remember { LocalDate.now() }
    
    // Dynamic greeting calculation
    val currentHour = remember { LocalTime.now().hour }
    val greeting = when (currentHour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        else -> "Good Evening"
    }
    
    // Read user's real name from SharedPreferences like ProfileScreen does
    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("habit_flow_prefs", android.content.Context.MODE_PRIVATE) }
    val profileName = remember(sharedPrefs) { sharedPrefs.getString("name", "Username") ?: "Username" }
    val firstName = remember(profileName) { profileName.substringBefore(" ").trim() }
    
    // 3 June 2026 format style matching image
    val formattedDate = remember { today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())) }

    // Today's scheduled tasks from active habit sets
    val todaysTasks = remember(habitSetsWithTasks) {
        habitSetsWithTasks
            .filter { it.habitSet.status == "Active" }
            .flatMap { setWithTasks ->
                setWithTasks.tasks.filter { Scheduler.isTaskScheduled(it, today) }
            }
    }

    // Map of today's task log status
    val logMap = remember(todayLogs) {
        todayLogs.associate { it.taskId to it.status }
    }

    // Counts for the Progress Card
    val totalScheduled = todaysTasks.size
    val completedCount = todaysTasks.count { logMap[it.id] == "Complete" }
    val partialCount = todaysTasks.count { logMap[it.id] == "Partial" }
    val missedCount = todaysTasks.count { logMap[it.id] == "Missed" }
    val remainingCount = totalScheduled - (completedCount)

    // Calculate overall completion percentage: Complete counts as 100%, Partial counts as 50%
    val progressPercentage = if (totalScheduled > 0) {
        ((completedCount.toFloat() + partialCount.toFloat() * 0.5f) / totalScheduled.toFloat() * 100f).toInt()
    } else {
        0
    }

    // Aggregate streaks across all active Sets using HabitAnalyticsService
    val activeSets = remember(habitSetsWithTasks) { habitSetsWithTasks.filter { it.habitSet.status == "Active" } }
    
    val currentOverallStreak = remember(activeSets, allLogs, today) {
        if (activeSets.isEmpty()) 0 else activeSets.maxOfOrNull { setWithTasks ->
            com.example.util.HabitAnalyticsService.calculateCurrentStreak(setWithTasks, allLogs, today)
        } ?: 0
    }
    
    val bestOverallStreak = remember(activeSets, allLogs, today) {
        if (activeSets.isEmpty()) 0 else activeSets.maxOfOrNull { setWithTasks ->
            com.example.util.HabitAnalyticsService.calculateBestStreak(setWithTasks, allLogs, today)
        } ?: 0
    }

    // Grouping tasks by HabitSet (Status default logic)
    val groupedTasks = remember(habitSetsWithTasks, logMap) {
        habitSetsWithTasks
            .filter { it.habitSet.status == "Active" }
            .map { setWithTasks ->
                val scheduledForSet = setWithTasks.tasks.filter { Scheduler.isTaskScheduled(it, today) }
                val sortedTasks = scheduledForSet.sortedWith { t1, t2 ->
                    val rank1 = when (logMap[t1.id] ?: "None") {
                        "Partial" -> 1
                        "Missed" -> 2
                        "Complete" -> 3
                        else -> 0
                    }
                    val rank2 = when (logMap[t2.id] ?: "None") {
                        "Partial" -> 1
                        "Missed" -> 2
                        "Complete" -> 3
                        else -> 0
                    }
                    rank1.compareTo(rank2)
                }
                setWithTasks.habitSet to sortedTasks
            }
            .filter { it.second.isNotEmpty() }
            .sortedWith { p1, p2 ->
                val (h1, tasks1) = p1
                val (h2, tasks2) = p2
                val isComplete1 = tasks1.all { 
                    val status = logMap[it.id] ?: "None"
                    status == "Complete" || status == "Partial" || status == "Missed"
                }
                val isComplete2 = tasks2.all { 
                    val status = logMap[it.id] ?: "None"
                    status == "Complete" || status == "Partial" || status == "Missed"
                }
                if (isComplete1 != isComplete2) {
                    if (isComplete1) 1 else -1
                } else {
                    val prio1 = h1.priority ?: -1
                    val prio2 = h2.priority ?: -1
                    if (prio1 != prio2) {
                        prio2.compareTo(prio1)
                    } else {
                        h1.name.compareTo(h2.name, ignoreCase = true)
                    }
                }
            }
    }

    // Keep track of which Habit Sets are expanded/collapsed on Home screen
    val expandedStates = remember { mutableStateMapOf<Int, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "HabitGrid",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.selectedTab.value = "Settings" },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 80.dp, start = 16.dp, end = 16.dp, top = 0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SECTION 1: Greeting Header Block
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "$greeting! 👋",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 28.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }

            // SECTION 2: Dynamic Two-Column Progress Widgets Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Widget Card: Today's Progress
                    Card(
                        modifier = Modifier
                            .weight(2f)
                            .height(148.dp)
                            .testTag("today_overall_progress_card"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Today's Progress",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                              ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    CircularProgressIndicator(
                                        progress = progressPercentage.toFloat() / 100f,
                                        modifier = Modifier.fillMaxSize(),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 7.dp,
                                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    )
                                    Text(
                                        text = "$progressPercentage%",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "$completedCount / $totalScheduled",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontFamily = FontFamily.Serif,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Completed",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "$remainingCount",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontFamily = FontFamily.Serif,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Remaining",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Right Widget Card: Current Streak
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(148.dp)
                            .testTag("current_streak_card"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "Current Streak",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "🔥",
                                    fontSize = 28.sp
                                )
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "$currentOverallStreak",
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 32.sp
                                    )
                                    Text(
                                        text = "Days",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 3: Today's Scheduled Tasks Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Today's Scheduled Tasks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Empty state if no tasks scheduled today
            if (groupedTasks.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                            .testTag("home_empty_state"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "No habits scheduled today",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = "No habits scheduled for today.\nEnjoy your day.",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                // Loop through Habit Set cards
                groupedTasks.forEach { (habitSet, tasks) ->
                    item {
                        val isExpanded = expandedStates[habitSet.id] != false // default to true
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("group_card_${habitSet.id}"),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Collapsible Card Header Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expandedStates[habitSet.id] = !isExpanded }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = getCategoryIconForSet(habitSet.name),
                                        contentDescription = "Category icon",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = habitSet.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    
                                    // Number of tasks badge
                                    val sizeLabel = if (tasks.size == 1) "1 task" else "${tasks.size} tasks"
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = sizeLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Expand collapse arrow",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                
                                AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        modifier = Modifier.padding(bottom = 16.dp, start = 12.dp, end = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        tasks.forEach { task ->
                                            TaskRow(
                                                task = task,
                                                currentStatus = logMap[task.id] ?: "None",
                                                onStatusChange = { newStatus ->
                                                    viewModel.updateTaskStatus(task.id, newStatus)
                                                }
                                            )
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

    // End of HomeScreen
}

@Composable
fun TaskRow(
    task: HabitTask,
    currentStatus: String,
    onStatusChange: (String) -> Unit
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    // Soft alert colors for statuses
    val completeColor = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
    val completeBg = if (isDark) Color(0xFF1B3D23) else Color(0xFFE8F5E9)
    val completeBorder = if (isDark) Color(0xFF2E7D32) else Color(0xFFC8E6C9)

    val partialColor = if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
    val partialBg = if (isDark) Color(0xFF3E2723) else Color(0xFFFFF3E0)
    val partialBorder = if (isDark) Color(0xFFFFB74D) else Color(0xFFFFE0B2)

    val missedColor = if (isDark) Color(0xFFE57373) else Color(0xFFC62828)
    val missedBg = if (isDark) Color(0xFF401C1C) else Color(0xFFFFEBEE)
    val missedBorder = if (isDark) Color(0xFFE57373) else Color(0xFFFFCDD2)

    val containerBg = when (currentStatus) {
        "Complete" -> completeBg
        "Partial" -> partialBg
        "Missed" -> missedBg
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
    }

    val borderStrokeColor = when (currentStatus) {
        "Complete" -> completeBorder
        "Partial" -> partialBorder
        "Missed" -> missedBorder
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }

    val titleColor = when (currentStatus) {
        "Complete" -> completeColor
        "Partial" -> partialColor
        "Missed" -> missedColor
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_row_card_${task.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerBg
        ),
        border = BorderStroke(1.dp, borderStrokeColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Drag Indicator handle on left
            Icon(
                imageVector = Icons.Default.DragIndicator,
                contentDescription = "Reorder Indicator",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                modifier = Modifier.size(20.dp)
            )

            // Title and Description Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = if (currentStatus == "Complete") TextDecoration.LineThrough else TextDecoration.None
                    ),
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                if (task.description.isNotBlank()) {
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Action Status Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Button 1: Complete
                StatusSelectionBox(
                    icon = Icons.Default.Check,
                    label = "Complete",
                    selected = currentStatus == "Complete",
                    activeColor = completeColor,
                    activeBg = completeBg,
                    activeBorder = completeBorder,
                    onClick = {
                        onStatusChange(if (currentStatus == "Complete") "None" else "Complete")
                    }
                )

                // Button 2: Partial
                StatusSelectionBox(
                    icon = Icons.Default.Category,
                    useCustomDiamond = true,
                    label = "Partial",
                    selected = currentStatus == "Partial",
                    activeColor = partialColor,
                    activeBg = partialBg,
                    activeBorder = partialBorder,
                    onClick = {
                        onStatusChange(if (currentStatus == "Partial") "None" else "Partial")
                    }
                )

                // Button 3: Missed
                StatusSelectionBox(
                    icon = Icons.Default.Close,
                    label = "Missed",
                    selected = currentStatus == "Missed",
                    activeColor = missedColor,
                    activeBg = missedBg,
                    activeBorder = missedBorder,
                    onClick = {
                        onStatusChange(if (currentStatus == "Missed") "None" else "Missed")
                    }
                )
            }
        }
    }
}

@Composable
fun StatusSelectionBox(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    activeColor: Color,
    activeBg: Color,
    activeBorder: Color,
    useCustomDiamond: Boolean = false,
    onClick: () -> Unit
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val baseBorderColor = if (isDark) Color(0xFF3E362F) else Color(0xFFEBE0D5)
    val baseBgColor = if (isDark) Color(0xFF2A201A) else Color(0xFFFFFDFC)
    val baseIconColor = if (isDark) Color(0xFF8A7E72) else Color(0xFFB1A091)
    
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(58.dp)
            .height(54.dp)
            .testTag("status_${label.lowercase()}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) activeBg else baseBgColor
        ),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) activeBorder else baseBorderColor
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (useCustomDiamond) {
                // Draw outline gold diamond icon matching mockup exactly
                Canvas(modifier = Modifier.size(16.dp)) {
                    val w = size.width
                    val h = size.height
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w / 2f, 0f)
                        lineTo(w, h / 2f)
                        lineTo(w / 2f, h)
                        lineTo(0f, h / 2f)
                        close()
                    }
                    drawPath(
                        path = path,
                        color = if (selected) activeColor else baseIconColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                }
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) activeColor else baseIconColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 8.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) activeColor else baseIconColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

fun getCategoryIconForSet(name: String): ImageVector {
    val lowercase = name.lowercase()
    return when {
        lowercase.contains("routine") || lowercase.contains("daily") || lowercase.contains("morning") || lowercase.contains("night") -> Icons.Default.WbSunny
        lowercase.contains("study") || lowercase.contains("learn") || lowercase.contains("math") || lowercase.contains("read") || lowercase.contains("book") -> Icons.Default.MenuBook
        lowercase.contains("exercise") || lowercase.contains("gym") || lowercase.contains("walk") || lowercase.contains("fitness") || lowercase.contains("sport") || lowercase.contains("run") -> Icons.Default.FitnessCenter
        lowercase.contains("health") || lowercase.contains("water") || lowercase.contains("meditate") || lowercase.contains("yoga") || lowercase.contains("spa") -> Icons.Default.Spa
        else -> Icons.Default.Bookmark
    }
}
