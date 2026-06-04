@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.HabitViewModel
import com.example.util.HabitAnalyticsService
import com.example.util.Scheduler
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(top = 16.dp, bottom = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
fun TaskSetDashboardScreen(
    viewModel: HabitViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeSetWithTasks by viewModel.activeHabitSetWithTasks.collectAsState()
    val allLogs by viewModel.allLogs.collectAsState()
    val today = remember { LocalDate.now() }

    // State for History Note editing
    var showNoteEditDialogForDate by remember { mutableStateOf<LocalDate?>(null) }
    var currentEditNoteText by remember { mutableStateOf("") }

    if (activeSetWithTasks == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "No habit set selected",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "No Habit Set selected.",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Button(onClick = { viewModel.selectedHabitSetId.value = null }) {
                    Text("Go back")
                }
            }
        }
        return
    }

    val setWithTasks = activeSetWithTasks!!
    val habitSet = setWithTasks.habitSet
    val tasks = setWithTasks.tasks

    // Isolated calculations from start date to today
    val setStartDate = remember(habitSet.startDate) {
        try { LocalDate.parse(habitSet.startDate) } catch (e: Exception) { today }
    }
    val setEndDate = remember(habitSet.endDate) {
        habitSet.endDate?.let { try { LocalDate.parse(it) } catch (e: Exception) { null } }
    }
    val trackingRangeEnd = if (setEndDate != null && setEndDate.isBefore(today)) setEndDate else today

    // Prefs helper to persist/access notes under "daily_note_{setId}_{date}"
    val sharedPrefs = remember { context.getSharedPreferences("habitgrid_notes", Context.MODE_PRIVATE) }

    // Calculations:

    // 1. Streak values
    val currentStreak = remember(setWithTasks, allLogs) {
        HabitAnalyticsService.calculateCurrentStreak(setWithTasks, allLogs, today)
    }
    val bestStreak = remember(setWithTasks, allLogs) {
        HabitAnalyticsService.calculateBestStreak(setWithTasks, allLogs, today)
    }

    // 2. Productivity Score (overall completion percentage of habit bundle tasks)
    val productivityScore = remember(setWithTasks, allLogs) {
        HabitAnalyticsService.calculateHabitSetProductivity(setWithTasks, allLogs, setStartDate, today)
    }

    // 3. Today's details
    val scheduledToday = remember(setWithTasks) {
        tasks.filter { Scheduler.isTaskScheduled(it, today) }
    }
    val todayLogsMap = remember(allLogs) {
        allLogs.filter { it.date == today.toString() }.associateBy { it.taskId }
    }
    val totalTasksToday = scheduledToday.size
    val tasksCompletedToday = remember(scheduledToday, todayLogsMap) {
        scheduledToday.count { HabitAnalyticsService.isCompletedStatus(todayLogsMap[it.id]?.status) }
    }
    val tasksRemainingToday = totalTasksToday - tasksCompletedToday
    val todayProgressPercent = if (totalTasksToday == 0) 0 else ((tasksCompletedToday.toFloat() / totalTasksToday) * 100).toInt()

    // 4. Task Performance & Longest Missed Streaks (isolated functions)
    val taskPerformanceList = remember(tasks, allLogs) {
        tasks.map { task ->
            val taskStart = try { LocalDate.parse(task.startDate) } catch(e: Exception) { today }
            val taskProd = HabitAnalyticsService.calculateTaskProductivity(task, allLogs, taskStart, today)
            val taskStreak = HabitAnalyticsService.calculateTaskCurrentStreak(task, allLogs, today)
            val taskBestStreak = HabitAnalyticsService.calculateTaskBestStreak(task, allLogs, today)
            
            // Calculate longest missed streak
            val longestMissed = calculateTaskLongestMissedStreak(task, allLogs, today)
            
            TaskPerfMetrics(
                task = task,
                completionPercent = taskProd,
                currentStreak = taskStreak,
                bestStreak = taskBestStreak,
                longestMissedStreak = longestMissed
            )
        }
    }

    // 5. Insights: Most Consistent & Most Missed
    val mostConsistentTask = remember(taskPerformanceList) {
        if (taskPerformanceList.isEmpty()) null
        else taskPerformanceList.maxByOrNull { it.completionPercent }
    }
    val mostMissedTask = remember(taskPerformanceList) {
        if (taskPerformanceList.isEmpty()) null
        else taskPerformanceList.minByOrNull { it.completionPercent }
    }

    // 6. Schedule Progress:
    val dateRangeText = "${habitSet.startDate} to ${habitSet.endDate ?: "Ongoing"}"
    val totalCalendarDays = remember(setStartDate, trackingRangeEnd) {
        ChronoUnit.DAYS.between(setStartDate, trackingRangeEnd).toInt() + 1
    }
    val totalDaysRemaining = remember(setEndDate) {
        if (setEndDate != null && setEndDate.isAfter(today)) {
            ChronoUnit.DAYS.between(today, setEndDate).toInt()
        } else {
            0
        }
    }

    // Count Perfect Active Days between setStart and today
    val daysCompletedMetrics = remember(setWithTasks, allLogs, setStartDate) {
        var completedCounter = 0
        var activeCounter = 0
        var loopDate = setStartDate
        val logMapForRange = allLogs.associateBy { it.taskId to it.date }
        while (!loopDate.isAfter(today)) {
            val scheduledForDay = setWithTasks.tasks.filter { Scheduler.isTaskScheduled(it, loopDate) }
            if (scheduledForDay.isNotEmpty()) {
                activeCounter++
                val perfect = scheduledForDay.all { task ->
                    val status = logMapForRange[task.id to loopDate.toString()]?.status
                    HabitAnalyticsService.isCompletedStatus(status)
                }
                if (perfect) {
                    completedCounter++
                }
            }
            loopDate = loopDate.plusDays(1)
        }
        Pair(completedCounter, activeCounter)
    }
    val totalDaysCompleted = daysCompletedMetrics.first
    val activeDaysCount = daysCompletedMetrics.second
    val totalDaysCompletedPercent = if (activeDaysCount == 0) 0 else ((totalDaysCompleted.toFloat() / activeDaysCount) * 100).toInt()

    // 7. Last 10 Tracked days list back scanned
    val last10Days = remember(today, setStartDate) {
        (0..9).map { today.minusDays(it.toLong()) }
            .filter { !it.isBefore(setStartDate) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("task_set_dashboard_root"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // HEADER TOOLBAR WITH BACK ACCENT
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = { viewModel.selectedHabitSetId.value = null },
                    modifier = Modifier
                        .testTag("dashboard_back_to_global")
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back to Overall Dashboard",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = habitSet.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "ISOLATED",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "Task-Set Dashboard • No global aggregation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // SECTION 1: OVERVIEW METRICS
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Overview")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Current Streak Card
                    OverviewCard(
                        modifier = Modifier.weight(1f),
                        emojiIcon = { Text("🔥", fontSize = 26.sp) },
                        label = "Current Streak",
                        value = "${currentStreak}d",
                        testTag = "set_current_streak_label"
                    )

                    // Best Streak Card
                    OverviewCard(
                        modifier = Modifier.weight(1f),
                        emojiIcon = { Text("🏆", fontSize = 26.sp) },
                        label = "Best Streak",
                        value = "${bestStreak}d",
                        testTag = "set_best_streak_label"
                    )

                    // Productivity Score Card
                    OverviewCard(
                        modifier = Modifier.weight(1f),
                        emojiIcon = { Text("📈", fontSize = 26.sp) },
                        label = "Productivity",
                        value = "$productivityScore%",
                        testTag = "set_productivity_score_label"
                    )
                }
            }
        }

        // SECTION 2: TODAY'S PROGRESS
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(title = "Today's Progress")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("set_today_progress_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Today's Focus Progress",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
                                )
                                Text(
                                    text = "${scheduledToday.size} subtasks scheduled for today",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(54.dp)
                            ) {
                                CircularProgressIndicator(
                                    progress = todayProgressPercent.toFloat() / 100f,
                                    strokeWidth = 5.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    modifier = Modifier.fillMaxSize()
                                )
                                Text(
                                    text = "$todayProgressPercent%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = todayProgressPercent.toFloat() / 100f,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatMiniItem(modifier = Modifier.weight(1f), value = "$totalTasksToday", desc = "Total Today", bg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                            StatMiniItem(modifier = Modifier.weight(1f), value = "$tasksCompletedToday", desc = "Completed", bg = Color(0xFFE8F5E9).copy(alpha = if (androidx.compose.foundation.isSystemInDarkTheme()) 0.1f else 0.8f))
                            StatMiniItem(modifier = Modifier.weight(1f), value = "$tasksRemainingToday", desc = "Remaining", bg = Color(0xFFFFF3E0).copy(alpha = if (androidx.compose.foundation.isSystemInDarkTheme()) 0.1f else 0.8f))
                        }
                    }
                }
            }
        }

        // SECTION 4: TASK INSIGHTS
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Task Insights")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Most Consistent Task
                    InsightCard(
                        modifier = Modifier.weight(1f),
                        title = "Most Consistent Task",
                        taskName = mostConsistentTask?.task?.name ?: "No tasks yet",
                        scoreText = mostConsistentTask?.let { "${it.completionPercent}% productivity" } ?: "N/A",
                        emoji = "🏆",
                        testTag = "most_consistent_task_label"
                    )

                    // Most Missed Task
                    InsightCard(
                        modifier = Modifier.weight(1f),
                        title = "Most Missed Task",
                        taskName = mostMissedTask?.task?.name ?: "No tasks yet",
                        scoreText = mostMissedTask?.let { "${it.completionPercent}% productivity" } ?: "N/A",
                        emoji = "💔",
                        testTag = "most_missed_task_label"
                    )
                }
            }
        }

        // SECTION 5: SCHEDULE PROGRESS
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(title = "Schedule Progress")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("set_schedule_progress_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Perfect Days Completed",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "$totalDaysCompletedPercent% Complete",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = totalDaysCompletedPercent / 100f,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )

                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ScheduleColumnStat(label = "Total Days", value = "$totalCalendarDays")
                            ScheduleColumnStat(label = "Completed", value = "$totalDaysCompleted")
                            ScheduleColumnStat(label = "Remaining", value = "$totalDaysRemaining")
                            ScheduleColumnStat(label = "Completion %", value = "$totalDaysCompletedPercent%")
                        }
                    }
                }
            }
        }

        // SECTION 3: OVERALL PROGRESS (TASK PERFORMANCE LIST AS CARDS)
        item {
            SectionHeader(title = "Overall Progress")
        }

        if (taskPerformanceList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No subtasks defined in this Habit Set.\nStats will populate when tasks are created.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            items(taskPerformanceList, key = { it.task.id }) { metrics ->
                TaskAnalyticsCard(metrics = metrics)
            }
        }

        // RECENT TRACKING HISTORY TABULAR LAYOUT
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionHeader(title = "Recent Tracking History")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        Column {
                            // 1. Sticky Table Header
                            Row(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                                    .padding(vertical = 12.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Date",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.width(90.dp)
                                )

                                tasks.forEachIndexed { idx, task ->
                                    Text(
                                        text = if (task.name.length > 10) task.name.take(8) + ".." else task.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.width(100.dp)
                                    )
                                }

                                Text(
                                    text = "Streak",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.width(70.dp)
                                )

                                Text(
                                    text = "Notes",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.width(70.dp)
                                )
                            }

                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                            // 2. Table Row Entries
                            last10Days.forEach { date ->
                                val dateStr = date.toString()
                                val dateText = date.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM"))

                                val dateLogsMap = allLogs.filter { it.date == dateStr }.associateBy { it.taskId }
                                val streakOnDate = HabitAnalyticsService.calculateCurrentStreak(setWithTasks, allLogs, date)

                                val noteKey = "daily_note_${habitSet.id}_${date}"
                                var savedNoteValue = sharedPrefs.getString(noteKey, "") ?: ""

                                Row(
                                    modifier = Modifier
                                        .padding(vertical = 10.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = dateText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.width(90.dp)
                                    )

                                    tasks.forEach { task ->
                                        val isScheduled = Scheduler.isTaskScheduled(task, date)
                                        val log = dateLogsMap[task.id]
                                        val status = log?.status

                                        val cellValue = when {
                                            !isScheduled -> "—"
                                            HabitAnalyticsService.isCompletedStatus(status) -> "✅"
                                            HabitAnalyticsService.isPartialStatus(status) -> "🔶"
                                            else -> "❌"
                                        }

                                        Text(
                                            text = cellValue,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.width(100.dp)
                                        )
                                    }

                                    Text(
                                        text = "🔥 ${streakOnDate}d",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE65100),
                                        modifier = Modifier.width(70.dp)
                                    )

                                    Row(
                                        modifier = Modifier.width(70.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val isToday = date == LocalDate.now()
                                        if (isToday || savedNoteValue.isNotBlank()) {
                                            Icon(
                                                imageVector = if (savedNoteValue.isNotBlank()) Icons.Default.Note else Icons.Default.NoteAdd,
                                                contentDescription = if (isToday) "Edit Note" else "View Note",
                                                tint = if (savedNoteValue.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clickable {
                                                        currentEditNoteText = if (savedNoteValue.contains("] ")) savedNoteValue.substringAfter("] ") else savedNoteValue
                                                        showNoteEditDialogForDate = date
                                                    }
                                            )
                                        } else {
                                            Text("-", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                }
                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f))
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DAILY NOTE INLINE EDIT DIALOG FOR HISTORY CARD ---
    if (showNoteEditDialogForDate != null) {
        val targetDate = showNoteEditDialogForDate!!
        val isToday = targetDate == LocalDate.now()
        val formattedTargetDate = targetDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"))
        val noteKey = "daily_note_${habitSet.id}_${targetDate}"
        
        Dialog(
            onDismissRequest = {
                if (isToday) {
                    val fullLogValue = currentEditNoteText
                    sharedPrefs.edit().putString(noteKey, fullLogValue).apply()
                }
                showNoteEditDialogForDate = null
            },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { showNoteEditDialogForDate = null },
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            enabled = false
                        ) {},
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Drag Handle
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    CircleShape
                                )
                                .align(Alignment.CenterHorizontally)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = if (isToday) "Journal Entry" else "Past Entry",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = formattedTargetDate,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            
                            if (isToday) {
                                IconButton(
                                    onClick = {
                                        val fullLogValue = currentEditNoteText
                                        sharedPrefs.edit().putString(noteKey, fullLogValue).apply()
                                        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                        showNoteEditDialogForDate = null
                                    },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                ) {
                                    Icon(
                                        Icons.Default.Check, 
                                        contentDescription = "Save",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            } else {
                                IconButton(onClick = { showNoteEditDialogForDate = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            }
                        }

                        if (isToday) {
                            androidx.compose.material3.OutlinedTextField(
                                value = currentEditNoteText,
                                onValueChange = { 
                                    currentEditNoteText = it 
                                    val fullLogValue = it
                                    sharedPrefs.edit().putString(noteKey, fullLogValue).apply()
                                },
                                placeholder = {
                                    Text("Write your thoughts...")
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 150.dp, max = 250.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                textStyle = MaterialTheme.typography.bodyLarge
                            )
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = currentEditNoteText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// CARD: EXPERT OVERVIEW METRIC CARD
// ==========================================
@Composable
fun OverviewCard(
    emojiIcon: @Composable () -> Unit,
    label: String,
    value: String,
    suffix: String = "",
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Card(
        modifier = modifier
            .height(138.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                emojiIcon()
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 24.sp
                    )
                    if (suffix.isNotBlank()) {
                        Text(
                            text = suffix,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// HELPER: TODAY PROGRESS SPLIT ROW METRIC
// ==========================================
@Composable
fun StatMiniItem(
    value: String,
    desc: String,
    bg: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black)
            Text(text = desc, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

// ==========================================
// CARD: DETAILED PERFORMANCE CARD for a Task
// ==========================================
@Composable
fun TaskAnalyticsCard(
    metrics: TaskPerfMetrics,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("task_analytics_card_${metrics.task.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.07f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Task Name Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = metrics.task.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${metrics.completionPercent}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Task Progress Bar
            LinearProgressIndicator(
                progress = metrics.completionPercent.toFloat() / 100f,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            // Dynamic Stats Grid Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Current Streak
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "CURRENT STREAK", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = MaterialTheme.colorScheme.outline)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color(0xFFFF5722), modifier = Modifier.size(12.dp))
                        Text(text = "${metrics.currentStreak}d", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }

                // Best Streak
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "BEST STREAK", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = MaterialTheme.colorScheme.outline)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(12.dp))
                        Text(text = "${metrics.bestStreak}d", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }

                // Longest Missed Streak
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "LONGEST MISSED", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = MaterialTheme.colorScheme.outline)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(Icons.Default.HeartBroken, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(12.dp))
                        Text(text = "${metrics.longestMissedStreak}d", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// CARD: DISCOVER PRECISE TASK INSIGHT
// ==========================================
@Composable
fun InsightCard(
    title: String,
    taskName: String,
    scoreText: String,
    emoji: String,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Card(
        modifier = modifier
            .height(135.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(text = emoji, fontSize = 18.sp, modifier = Modifier.padding(start = 4.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = taskName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = scoreText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 10.sp
                )
            }
        }
    }
}

// ==========================================
// CARD: RECENT HISTORICAL DAY LOG WITH NOTES
// ==========================================
@Composable
fun HistoryDayCard(
    date: LocalDate,
    scheduledTasks: List<HabitTask>,
    logsMap: Map<Int, HabitLog>,
    streak: Int,
    noteValue: String,
    onEditNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateText = date.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy"))
    val isToday = date == LocalDate.now()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        border = BorderStroke(
            1.dp,
            if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Info: Date & Streak Count on Day
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (isToday) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "TODAY",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                // Row: STREAK VALUE BADGE
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = "Streak",
                        tint = Color(0xFFFF5722),
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "Streak: ${streak}d",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f))

            // Subtask states checklist
            if (scheduledTasks.isEmpty()) {
                Text(
                    text = "— No tasks scheduled on this day",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    scheduledTasks.forEach { task ->
                        val log = logsMap[task.id]
                        val status = log?.status

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = when {
                                    HabitAnalyticsService.isCompletedStatus(status) -> "✅"
                                    HabitAnalyticsService.isPartialStatus(status) -> "🔶"
                                    HabitAnalyticsService.isMissedStatus(status) -> "❌"
                                    else -> "❌" // Untouched is Incomplete!
                                },
                                fontSize = 12.sp
                            )

                            Text(
                                text = task.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )

                            Text(
                                text = when {
                                    HabitAnalyticsService.isCompletedStatus(status) -> "Completed"
                                    HabitAnalyticsService.isPartialStatus(status) -> "Partial"
                                    else -> "Incomplete"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = when {
                                    HabitAnalyticsService.isCompletedStatus(status) -> Color(0xFF2E7D32)
                                    HabitAnalyticsService.isPartialStatus(status) -> Color(0xFFE65100)
                                    else -> MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                }
            }

            // Daily Note Text Display & Action Edit Note Trigger
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.StickyNote2,
                        contentDescription = "Note icon",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (noteValue.isNotBlank()) noteValue else "No log notes recorded for date.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (noteValue.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                            color = if (noteValue.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                        )
                    }

                    IconButton(
                        onClick = onEditNote,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Log Note",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// COLUMN HELPER: INDIVIDUAL SCHEDULE METRIC
// ==========================================
@Composable
fun ScheduleColumnStat(
    label: String,
    value: String
) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

// Data class representation models for calculation results
data class TaskPerfMetrics(
    val task: HabitTask,
    val completionPercent: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val longestMissedStreak: Int
)

/**
 * Calculates Task Longest Missed Streak spanning from task's active registry till today.
 * Scans day-by-day and computes maximum consecutive non-completed scheduled counts.
 */
fun calculateTaskLongestMissedStreak(
    task: HabitTask,
    logs: List<HabitLog>,
    today: LocalDate = LocalDate.now()
): Int {
    val taskStartDate = try {
        LocalDate.parse(task.startDate)
    } catch (e: Exception) {
        today
    }
    val logMap = logs.filter { it.taskId == task.id }.associateBy { it.date }
    var maxMissed = 0
    var currentMissed = 0
    var current = taskStartDate

    while (!current.isAfter(today)) {
        if (Scheduler.isTaskScheduled(task, current)) {
            val logStatus = logMap[current.toString()]?.status
            val isCompleted = HabitAnalyticsService.isCompletedStatus(logStatus)
            if (isCompleted) {
                if (currentMissed > maxMissed) {
                    maxMissed = currentMissed
                }
                currentMissed = 0
            } else {
                currentMissed++
            }
        }
        current = current.plusDays(1)
    }

    if (currentMissed > maxMissed) {
        maxMissed = currentMissed
    }
    return maxMissed
}
