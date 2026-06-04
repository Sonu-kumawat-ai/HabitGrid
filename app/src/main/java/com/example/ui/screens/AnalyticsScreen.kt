package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.HabitViewModel
import com.example.util.HabitAnalyticsService
import com.example.util.Scheduler
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class SetPerfData(
    val habitSet: com.example.data.HabitSet,
    val productivity: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val donePercent: Int,
    val missedPercent: Int
)

data class DashboardData(
    val totalSets: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val overallProductivity: Int,
    val totalTasksToday: Int,
    val completedTasksToday: Int,
    val remainingTasksToday: Int,
    val todayProgressPercent: Int,
    val setPerformanceList: List<SetPerfData>,
    val trackingTimeframe: String,
    val daysCompletedPercent: Int,
    val totalDaysCompleted: Int,
    val daysRemaining: Int,
    val totalDays: Int,
    val mostConsistentSet: String,
    val mostMissedSet: String,
    val mostConsistentTask: String,
    val mostMissedTask: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: HabitViewModel,
    modifier: Modifier = Modifier
) {
    val unfilteredSets by viewModel.unfilteredHabitSets.collectAsState()
    val allLogs by viewModel.allLogs.collectAsState()
    val selectedId by viewModel.selectedHabitSetId.collectAsState()

    if (selectedId != null) {
        TaskSetDashboardScreen(viewModel = viewModel, modifier = modifier)
        return
    }

    // Aggregate statistics
    val dashboardData = remember(unfilteredSets, allLogs) {
        val activeSets = unfilteredSets.filter { it.habitSet.status == "Active" }
        val today = LocalDate.now()

        if (activeSets.isEmpty()) {
            null
        } else {
            // Section 1: Overall Snapshot Calculations
            val totalSets = activeSets.size
            val currentOverallStreak = activeSets.maxOfOrNull { set ->
                HabitAnalyticsService.calculateCurrentStreak(set, allLogs, today)
            } ?: 0

            val bestOverallStreak = activeSets.maxOfOrNull { set ->
                HabitAnalyticsService.calculateBestStreak(set, allLogs, today)
            } ?: 0

            // Overall Productivity Score (Total Points Earned / Total Scheduled Tasks) * 100
            val earliestDates = activeSets.mapNotNull { set ->
                try { LocalDate.parse(set.habitSet.startDate) } catch (e: Exception) { null }
            }
            val minStartDate = earliestDates.minOrNull() ?: today

            val overallProductivity = HabitAnalyticsService.calculateOverallProductivity(
                activeSets,
                allLogs,
                minStartDate,
                today
            )

            // Section 2: Today's Overall Progress Calculations
            val scheduledTasksToday = activeSets.flatMap { set ->
                set.tasks.filter { Scheduler.isTaskScheduled(it, today) }
            }
            val totalTasksTodaySize = scheduledTasksToday.size
            val todayLogMap = allLogs.filter { it.date == today.toString() }.associateBy { it.taskId }
            val completedTasksTodaySize = scheduledTasksToday.count { task ->
                val log = todayLogMap[task.id]
                HabitAnalyticsService.isCompletedStatus(log?.status)
            }
            val remainingTasksTodaySize = totalTasksTodaySize - completedTasksTodaySize
            val todayProgressPercent = if (totalTasksTodaySize == 0) 0 else ((completedTasksTodaySize.toFloat() / totalTasksTodaySize) * 100).toInt()

            // Section 3: Task Set Performance Data
            val setPerformanceList = activeSets.map { setWithTasks ->
                val setStartDate = try { LocalDate.parse(setWithTasks.habitSet.startDate) } catch (e: Exception) { today }

                var totalScheduled = 0
                var completedCount = 0
                var missedCount = 0

                var current = setStartDate
                while (!current.isAfter(today)) {
                    val scheduled = setWithTasks.tasks.filter { Scheduler.isTaskScheduled(it, current) }
                    for (task in scheduled) {
                        totalScheduled++
                        val log = allLogs.find { it.taskId == task.id && it.date == current.toString() }
                        if (HabitAnalyticsService.isCompletedStatus(log?.status)) {
                            completedCount++
                        } else if (current.isBefore(today) || HabitAnalyticsService.isMissedStatus(log?.status)) {
                            missedCount++
                        }
                    }
                    current = current.plusDays(1)
                }

                val productivity = if (totalScheduled == 0) 0 else Math.round((completedCount.toDouble() / totalScheduled) * 100).toInt()
                val currentStreak = HabitAnalyticsService.calculateCurrentStreak(setWithTasks, allLogs, today)
                val bestStreak = HabitAnalyticsService.calculateBestStreak(setWithTasks, allLogs, today)
                val donePercent = if (totalScheduled == 0) 0 else ((completedCount.toFloat() / totalScheduled) * 100).toInt()
                val missedPercent = if (totalScheduled == 0) 0 else ((missedCount.toFloat() / totalScheduled) * 100).toInt()

                SetPerfData(
                    habitSet = setWithTasks.habitSet,
                    productivity = productivity,
                    currentStreak = currentStreak,
                    bestStreak = bestStreak,
                    donePercent = donePercent,
                    missedPercent = missedPercent
                )
            }

            // Section 4: Schedule Overview
            val latestEndDate = activeSets.mapNotNull { set ->
                set.habitSet.endDate?.let {
                    try { LocalDate.parse(it) } catch (e: Exception) { null }
                }
            }.maxOrNull()
            val finalEndDate = if (latestEndDate == null || latestEndDate.isBefore(today)) today else latestEndDate
            val totalCalendarDays = ChronoUnit.DAYS.between(minStartDate, finalEndDate).toInt() + 1

            var totalActiveDays = 0
            var totalDaysCompleted = 0

            var activeDayPtr = minStartDate
            while (!activeDayPtr.isAfter(today)) {
                val scheduledForDay = activeSets.flatMap { set ->
                    set.tasks.filter { Scheduler.isTaskScheduled(it, activeDayPtr) }
                }
                if (scheduledForDay.isNotEmpty()) {
                    totalActiveDays++
                    val dayLogs = allLogs.filter { it.date == activeDayPtr.toString() }.associateBy { it.taskId }
                    val allCompleted = scheduledForDay.all { task ->
                        val log = dayLogs[task.id]
                        HabitAnalyticsService.isCompletedStatus(log?.status)
                    }
                    if (allCompleted) {
                        totalDaysCompleted++
                    }
                }
                activeDayPtr = activeDayPtr.plusDays(1)
            }

            val daysCompletedPercent = if (totalActiveDays == 0) 0 else ((totalDaysCompleted.toFloat() / totalActiveDays) * 100).toInt()
            val daysRemaining = if (finalEndDate.isAfter(today)) ChronoUnit.DAYS.between(today, finalEndDate).toInt() else 0

            val trackingTimeframeText = "${minStartDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))} - ${finalEndDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}"

            // Section 5: Global Task Insights
            val sortedByProductivity = setPerformanceList.sortedByDescending { it.productivity }
            val mostConsistentSet = sortedByProductivity.firstOrNull()?.habitSet?.name ?: "N/A"
            val mostMissedSet = if (sortedByProductivity.isNotEmpty()) sortedByProductivity.last().habitSet.name else "N/A"

            val activeTasks = activeSets.flatMap { it.tasks }
            val taskStats = activeTasks.map { task ->
                val taskStartDate = try { LocalDate.parse(task.startDate) } catch (e: Exception) { today }
                var totalScheduledTask = 0
                var completedTaskCount = 0

                var current = taskStartDate
                while (!current.isAfter(today)) {
                    if (Scheduler.isTaskScheduled(task, current)) {
                        totalScheduledTask++
                        val log = allLogs.find { it.taskId == task.id && it.date == current.toString() }
                        if (HabitAnalyticsService.isCompletedStatus(log?.status)) {
                            completedTaskCount++
                        }
                    }
                    current = current.plusDays(1)
                }
                val score = if (totalScheduledTask == 0) 0 else ((completedTaskCount.toFloat() / totalScheduledTask) * 100).toInt()
                task to score
            }

            val mostConsistentTask = taskStats.maxByOrNull { it.second }?.first?.name ?: "N/A"
            val mostMissedTask = taskStats.filter { it.second > 0 || allLogs.any { log -> log.taskId == it.first.id } }.minByOrNull { it.second }?.first?.name ?: taskStats.minByOrNull { it.second }?.first?.name ?: "N/A"

            DashboardData(
                totalSets = totalSets,
                currentStreak = currentOverallStreak,
                bestStreak = bestOverallStreak,
                overallProductivity = overallProductivity,
                totalTasksToday = totalTasksTodaySize,
                completedTasksToday = completedTasksTodaySize,
                remainingTasksToday = remainingTasksTodaySize,
                todayProgressPercent = todayProgressPercent,
                setPerformanceList = setPerformanceList,
                trackingTimeframe = trackingTimeframeText,
                daysCompletedPercent = daysCompletedPercent,
                totalDaysCompleted = totalDaysCompleted,
                daysRemaining = daysRemaining,
                totalDays = totalCalendarDays,
                mostConsistentSet = mostConsistentSet,
                mostMissedSet = mostMissedSet,
                mostConsistentTask = mostConsistentTask,
                mostMissedTask = mostMissedTask
            )
        }
    }

    if (dashboardData == null) {
        // Empty State visual representation
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.InsertChart,
                    contentDescription = "Analytics inactive",
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "HabitGrid Analytics",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No active Habit Sets found. Create a set and add task routines in the 'Habit Sets' tab to activate your global dashboard analytics.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.selectedTab.value = "Habit Sets" },
                    modifier = Modifier.testTag("analytics_empty_add_set")
                ) {
                    Text("Go to Habit Sets")
                }
            }
        }
    } else {
        // Main Dashboard Screen Implementation
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Dashboard Header
            Column {
                Text(
                    text = "Analytics",
                    style = MaterialTheme.typography.displayMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your habits, your progress, your journey.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            // SECTION 1: Overall Snapshot (Grid of 4 beautifully designed cards in a 2x2 grid)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Snapshot Metrics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SnapshotCard(
                            emojiIcon = { Text("📂", fontSize = 26.sp) },
                            label = "Habit Sets",
                            value = "${dashboardData.totalSets}",
                            modifier = Modifier.weight(1f).testTag("stat_total_sets")
                        )
                        SnapshotCard(
                            emojiIcon = { Text("📈", fontSize = 26.sp) },
                            label = "Productivity",
                            value = "${dashboardData.overallProductivity}%",
                            modifier = Modifier.weight(1f).testTag("stat_productivity_score")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SnapshotCard(
                            emojiIcon = { Text("🔥", fontSize = 26.sp) },
                            label = "Current Streak",
                            value = "${dashboardData.currentStreak}",
                            suffix = "Days",
                            modifier = Modifier.weight(1f).testTag("stat_current_streak")
                        )
                        SnapshotCard(
                            emojiIcon = { Text("🏆", fontSize = 26.sp) },
                            label = "Best Streak",
                            value = "${dashboardData.bestStreak}",
                            suffix = "Days",
                            modifier = Modifier.weight(1f).testTag("stat_best_streak")
                        )
                    }
                }
            }

            // SECTION 2: Today's Overall Progress (Progress Visualization)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("section_today_progress"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Today's Overall Progress",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(72.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = dashboardData.todayProgressPercent / 100f,
                                strokeWidth = 7.dp,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxSize()
                            )
                            Text(
                                text = "${dashboardData.todayProgressPercent}%",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            ProgressStatsRow(
                                title = "Total Tasks",
                                value = "${dashboardData.totalTasksToday}",
                                dotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                            ProgressStatsRow(
                                title = "Completed Tasks",
                                value = "${dashboardData.completedTasksToday}",
                                dotColor = MaterialTheme.colorScheme.primary
                            )
                            ProgressStatsRow(
                                title = "Remaining Tasks",
                                value = "${dashboardData.remainingTasksToday}",
                                dotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }

            // SECTION 3: Task Set Performance (Mobile-Friendly list of performance)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Habit Set Performance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "View All",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            viewModel.selectedTab.value = "Habit Sets"
                        }
                    )
                }
                
                dashboardData.setPerformanceList.forEach { perf ->
                    Card(
                        onClick = {
                            viewModel.selectedHabitSetId.value = perf.habitSet.id
                            viewModel.selectedTab.value = "Analytics"
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("set_performance_card_${perf.habitSet.id}"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val isHighProc = perf.productivity >= 60
                            // Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = getLocalCategoryIcon(perf.habitSet.name),
                                        contentDescription = "Category icon",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = perf.habitSet.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                val badgeBgColor = if (isHighProc) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                                val badgeTextColor = if (isHighProc) Color(0xFF2E7D32) else Color(0xFFE65100)

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = badgeBgColor),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${perf.productivity}% Productive",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeTextColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Stats Columns Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Column 1: Streaks
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocalFireDepartment,
                                            contentDescription = "Streak",
                                            tint = Color(0xFFE65100),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Streak: ${perf.currentStreak} Days",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.EmojiEvents,
                                            contentDescription = "Best streak",
                                            tint = Color(0xFFFFB300),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Best: ${perf.bestStreak} Days",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Column 2: Percentages
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Success rate",
                                            tint = Color(0xFF2E7D32),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Done ${perf.donePercent}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.HighlightOff,
                                            contentDescription = "Miss rate",
                                            tint = Color(0xFFC62828),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Missed ${perf.missedPercent}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Clean thick linear bar
                            LinearProgressIndicator(
                                progress = perf.productivity / 100f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (isHighProc) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            )
                        }
                    }
                }
            }

            // SECTION 5: Global Task Insights (Now placed above Schedule Overview)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("section_global_insights"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Global Task Insights",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InsightSubCard(
                            label = "Most Consistent Habit Set",
                            value = dashboardData.mostConsistentSet,
                            icon = Icons.Default.ThumbUp,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        InsightSubCard(
                            label = "Most Missed Habit Set",
                            value = dashboardData.mostMissedSet,
                            icon = Icons.Default.TrendingDown,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InsightSubCard(
                            label = "Most Consistent Task",
                            value = dashboardData.mostConsistentTask,
                            icon = Icons.Default.TaskAlt,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        InsightSubCard(
                            label = "Most Missed Task",
                            value = dashboardData.mostMissedTask,
                            icon = Icons.Default.Warning,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // SECTION 4: Schedule Overview (Now placed below Task Insights)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Calendar Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Global Schedule Overview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("section_schedule_overview"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OverviewMetricLine(label = "Tracking Timeframe", value = dashboardData.trackingTimeframe)
                        OverviewMetricLine(label = "Days Completed %", value = "${dashboardData.daysCompletedPercent}%")
                        OverviewMetricLine(label = "Total Days", value = "${dashboardData.totalDays}")
                        OverviewMetricLine(label = "Days Completed", value = "${dashboardData.totalDaysCompleted}")
                    }
                }
            }
        }
    }
}

@Composable
fun SnapshotCard(
    emojiIcon: @Composable () -> Unit,
    label: String,
    value: String,
    suffix: String = "",
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(138.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
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
                        fontSize = 26.sp
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

@Composable
fun ProgressStatsRow(
    title: String,
    value: String,
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(dotColor)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun OverviewMetricLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun InsightSubCard(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun getLocalCategoryIcon(name: String): ImageVector {
    val lowercase = name.lowercase()
    return when {
         lowercase.contains("routine") || lowercase.contains("daily") || lowercase.contains("morning") || lowercase.contains("night") -> Icons.Default.WbSunny
         lowercase.contains("study") || lowercase.contains("learn") || lowercase.contains("math") || lowercase.contains("read") || lowercase.contains("book") -> Icons.Default.MenuBook
         lowercase.contains("exercise") || lowercase.contains("gym") || lowercase.contains("walk") || lowercase.contains("fitness") || lowercase.contains("sport") || lowercase.contains("run") -> Icons.Default.FitnessCenter
         lowercase.contains("health") || lowercase.contains("water") || lowercase.contains("meditate") || lowercase.contains("yoga") || lowercase.contains("spa") -> Icons.Default.Spa
         else -> Icons.Default.Assignment
    }
}
