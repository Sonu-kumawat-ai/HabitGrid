@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.HabitLog
import com.example.data.HabitSet
import com.example.data.HabitSetWithTasks
import com.example.data.HabitTask
import com.example.ui.HabitViewModel
import com.example.util.HabitAnalyticsService
import com.example.util.Scheduler
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

@Composable
fun CalendarScreen(
    viewModel: HabitViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val habitSetsWithTasks by viewModel.filteredHabitSets.collectAsState()
    val allLogs by viewModel.allLogs.collectAsState()

    // Preferences helper to access written notes
    val sharedPrefs = remember { context.getSharedPreferences("habitgrid_notes", Context.MODE_PRIVATE) }

    // Trigger state to reactively reload notes on delete
    var notesTrigger by remember { mutableStateOf(0) }

    // Selected Date & Visible Month State
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var currentMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }

    // Helper functions for tracked day navigation
    val totalScheduledForDate = { date: LocalDate ->
        var total = 0
        val activeSets = habitSetsWithTasks.filter { it.habitSet.status == "Active" }
        for (setWithTasks in activeSets) {
            total += setWithTasks.tasks.count { Scheduler.isTaskScheduled(it, date) }
        }
        total
    }

    val findPreviousTrackedDay = { current: LocalDate ->
        var check = current.minusDays(1)
        var result = current
        for (i in 1..365) {
            if (totalScheduledForDate(check) > 0) {
                result = check
                break
            }
            check = check.minusDays(1)
        }
        result
    }

    val findNextTrackedDay = { current: LocalDate ->
        var check = current.plusDays(1)
        var result = current
        for (i in 1..365) {
            if (totalScheduledForDate(check) > 0) {
                result = check
                break
            }
            check = check.plusDays(1)
        }
        result
    }

    // Dynamic list of notes written on selectedDate across all habit sets
    val notesForDay = remember(habitSetsWithTasks, selectedDate, notesTrigger) {
        val notes = habitSetsWithTasks.mapNotNull { setWithTasks ->
            val noteKey = "daily_note_${setWithTasks.habitSet.id}_$selectedDate"
            val noteValue = sharedPrefs.getString(noteKey, "") ?: ""
            if (noteValue.isNotBlank()) {
                setWithTasks.habitSet to noteValue
            } else {
                null
            }
        }.toMutableList()
        
        val globalPrefs = context.getSharedPreferences("habitgrid_settings", Context.MODE_PRIVATE)
        val globalNoteKey = "global_daily_note_$selectedDate"
        val globalNoteValue = globalPrefs.getString(globalNoteKey, "") ?: ""
        if (globalNoteValue.isNotBlank()) {
            val globalDummy = com.example.data.HabitSet(id = -1, name = "Daily Reflection", description = "", status = "", startDate = selectedDate.toString())
            notes.add(0, globalDummy to globalNoteValue)
        }
        
        notes
    }

    // Filter habit sets & tasks that are scheduled on the selected date
    val setsWithScheduledTasksOnDay = remember(habitSetsWithTasks, selectedDate) {
        habitSetsWithTasks
            .filter { it.habitSet.status == "Active" }
            .map { setWithTasks ->
                val tasksOnDay = setWithTasks.tasks.filter { Scheduler.isTaskScheduled(it, selectedDate) }
                setWithTasks.copy(tasks = tasksOnDay)
            }
            .filter { it.tasks.isNotEmpty() }
    }

    // Calculate aggregated metrics for selected day
    val totalTasksToday = setsWithScheduledTasksOnDay.sumOf { it.tasks.size }
    val logsOnDayMap = remember(allLogs, selectedDate) {
        allLogs.filter { it.date == selectedDate.toString() }.associateBy { it.taskId }
    }

    val tasksCompletedToday = remember(setsWithScheduledTasksOnDay, logsOnDayMap) {
        var count = 0
        for (set in setsWithScheduledTasksOnDay) {
            count += set.tasks.count { HabitAnalyticsService.isCompletedStatus(logsOnDayMap[it.id]?.status) }
        }
        count
    }

    val productivityScore = remember(habitSetsWithTasks, allLogs, selectedDate) {
        if (totalTasksToday == 0) 0 else HabitAnalyticsService.calculateDailyProductivity(habitSetsWithTasks, allLogs, selectedDate)
    }

    // Streak count representing selected sets or max active streak on selected day
    val streakCount = remember(habitSetsWithTasks, allLogs, selectedDate) {
        if (habitSetsWithTasks.isEmpty()) 0
        else {
            val activeSets = habitSetsWithTasks.filter { it.habitSet.status == "Active" }
            if (activeSets.isEmpty()) 0
            else {
                activeSets.maxOf { setWithTasks ->
                    HabitAnalyticsService.calculateCurrentStreak(setWithTasks, allLogs, selectedDate)
                }
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Main screen surface with comfortable scrolling
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // HEADER BAR
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "History Archive",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Browse past dates, habit logs & written notes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            selectedDate = LocalDate.now()
                            currentMonth = YearMonth.from(selectedDate)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("jump_to_today_btn"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Today",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Today", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                    }
                }
            }

            // MONTH NAVIGATION & MONTH TITLE BAR
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { currentMonth = currentMonth.minusMonths(1) },
                                modifier = Modifier.testTag("prev_month_btn")
                            ) {
                                Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Previous Month")
                            }

                            Text(
                                text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            IconButton(
                                onClick = { currentMonth = currentMonth.plusMonths(1) },
                                modifier = Modifier.testTag("next_month_btn")
                            ) {
                                Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Next Month")
                            }
                        }

                        // WEEKDAY HEADER ROW (Sun - Sat)
                        Row(modifier = Modifier.fillMaxWidth()) {
                            val weekdays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                            weekdays.forEach { dayName ->
                                Text(
                                    text = dayName,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        // MONTH DAYS GRID
                        val daysInMonth = currentMonth.lengthOfMonth()
                        val firstDayOfMonth = currentMonth.atDay(1)
                        val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value % 7 // Sunday = 0, Monday = 1 ...

                        val prevMonth = currentMonth.minusMonths(1)
                        val daysInPrevMonth = prevMonth.lengthOfMonth()

                        val totalCells = 42 // 6 rows of 7 columns
                        var cellIndex = 0

                        val chunkedDays = mutableListOf<List<LocalDate>>()
                        val rawDaysList = mutableListOf<LocalDate>()

                        // Previous Month Days
                        for (i in 0 until firstDayOfWeek) {
                            val day = daysInPrevMonth - firstDayOfWeek + i + 1
                            rawDaysList.add(prevMonth.atDay(day))
                            cellIndex++
                        }

                        // Current Month Days
                        for (i in 1..daysInMonth) {
                            rawDaysList.add(currentMonth.atDay(i))
                            cellIndex++
                        }

                        // Next Month Days
                        var nextMonthIndex = 1
                        while (cellIndex < totalCells) {
                            rawDaysList.add(currentMonth.plusMonths(1).atDay(nextMonthIndex))
                            nextMonthIndex++
                            cellIndex++
                        }

                        // Split into weekly rows of 7 days
                        for (row in 0 until 6) {
                            val weekList = rawDaysList.subList(row * 7, (row + 1) * 7)
                            chunkedDays.add(weekList)
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            chunkedDays.forEach { week ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    week.forEach { date ->
                                        val isSelected = date == selectedDate
                                        val isToday = date == LocalDate.now()
                                        val isCurrentMonth = YearMonth.from(date) == currentMonth

                                        val scheduledTasksCount = totalScheduledForDate(date)
                                        val dailyScore = if (scheduledTasksCount == 0) 0 else HabitAnalyticsService.calculateDailyProductivity(habitSetsWithTasks, allLogs, date)

                                        // Color calculation
                                        val hasTasks = scheduledTasksCount > 0
                                        val cellColor = when {
                                            date.isAfter(LocalDate.now()) -> if (isCurrentMonth) Color.White else Color.Transparent
                                            !hasTasks -> if (isCurrentMonth) Color.White else Color.Transparent
                                            dailyScore == 100 -> Color(0xFF2E7D32) // Complete
                                            dailyScore in 1..99 -> Color(0xFFE65100) // Partial
                                            else -> Color(0xFFEF5350) // Missed (0% with tasks)
                                        }

                                        val contentAlpha = if (isCurrentMonth) 1f else 0.35f
                                        val textColor = when {
                                            isSelected -> MaterialTheme.colorScheme.onPrimary
                                            isCurrentMonth -> {
                                                if (date.isAfter(LocalDate.now()) || !hasTasks) {
                                                    Color.Black
                                                } else {
                                                    Color.White
                                                }
                                            }
                                            !isCurrentMonth -> Color.White.copy(alpha = 0.35f)
                                            else -> Color.White.copy(alpha = 0.35f)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .padding(3.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else cellColor.copy(alpha = contentAlpha),
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .clickable {
                                                    selectedDate = date
                                                    if (YearMonth.from(date) != currentMonth) {
                                                        currentMonth = YearMonth.from(date)
                                                    }
                                                }
                                                .testTag("calendar_day_cell_${date}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = date.dayOfMonth.toString(),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected || isToday) FontWeight.Black else FontWeight.Bold,
                                                    color = textColor,
                                                    modifier = Modifier.clip(CircleShape)
                                                )

                                                if (isToday) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(4.dp)
                                                            .clip(CircleShape)
                                                            .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
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

            // COLOR PALETTE LEGEND SHOWING PRODUCTIVITY SCALE
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Legend:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LegendItem(label = "100% Complete", color = Color(0xFF2E7D32))
                        LegendItem(label = "1% - 99% Partial", color = Color(0xFFE65100))
                        LegendItem(label = "0% Missed", color = Color(0xFFEF5350))
                    }
                }
            }

            // SELECTED DAY ACTIONS (PREV / NEXT ACTIVE TRACKED DAYS QUICK NAVIGATION)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val prevDate = selectedDate.minusDays(1)
                    val nextDate = selectedDate.plusDays(1)

                    OutlinedButton(
                        onClick = {
                            selectedDate = prevDate
                            currentMonth = YearMonth.from(selectedDate)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("prev_tracked_day_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Prev Day", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Previous Date",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            selectedDate = nextDate
                            currentMonth = YearMonth.from(selectedDate)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("next_tracked_day_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Next Date",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Next Day", modifier = Modifier.size(16.dp))
                    }
                }
            }

            // DETAILS METRIC HEADER CARD
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (totalTasksToday == 0) "No scheduled execution logs" else "$totalTasksToday Scheduled Task${if (totalTasksToday == 1) "" else "s"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            // Streaks display badge
                            Row(
                                modifier = Modifier
                                    .background(Color(0xFFFFF3E0), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = "Streak",
                                    tint = Color(0xFFFF5722),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "${streakCount}d Streak",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFE65100)
                                )
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                        if (totalTasksToday > 0) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Daily Productivity Rate",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "$productivityScore%",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = productivityScore / 100f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = when {
                                        productivityScore == 100 -> Color(0xFF2E7D32)
                                        productivityScore > 0 -> Color(0xFFE65100)
                                        else -> Color(0xFFEF5350)
                                    },
                                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                                )

                                Text(
                                    text = "Completed $tasksCompletedToday of $totalTasksToday dynamic task units scheduled for today.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Rest Day",
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        text = "Rest Day! No active habit sets with schedulers targeted today.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // GROUPED HABIT LOGS BY HABIT SET
            item {
                Text(
                    text = "Habit Logs Grouped",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (setsWithScheduledTasksOnDay.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOff,
                            contentDescription = "Empty",
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No recorded log checklists for this date.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                items(setsWithScheduledTasksOnDay, key = { it.habitSet.id.toString() + "_" + selectedDate }) { setWithTasks ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Habit Set Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Text(
                                        text = setWithTasks.habitSet.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Text(
                                    text = setWithTasks.habitSet.status,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))

                            // List of tasks
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                setWithTasks.tasks.forEach { task ->
                                    val log = logsOnDayMap[task.id]

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = task.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (task.description.isNotBlank()) {
                                                Text(
                                                    text = task.description,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        // Render status icon badge
                                        StatusBadge(status = log?.status)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // DAILY NOTES SECTION FOR SELECTED DAY
            item {
                Text(
                    text = "Daily Notes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (notesForDay.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No daily log notes recorded for today.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                items(notesForDay, key = { it.first.id.toString() + "_" + selectedDate }) { pair ->
                    val hSet = pair.first
                    val rawNoteValue = pair.second

                    // Extract note mood and text
                    val mood = if (rawNoteValue.startsWith("[")) {
                        rawNoteValue.substringBefore("]").substringAfter("[")
                    } else "Productive 🎯"

                    val textValue = if (rawNoteValue.contains("] ")) {
                        rawNoteValue.substringAfter("] ")
                    } else rawNoteValue

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = textValue,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            
                            // Delete Note action
                            IconButton(
                                onClick = {
                                    if (hSet.id == -1) {
                                        val globalPrefs = context.getSharedPreferences("habitgrid_settings", Context.MODE_PRIVATE)
                                        globalPrefs.edit().remove("global_daily_note_$selectedDate").apply()
                                    } else {
                                        val noteKey = "daily_note_${hSet.id}_$selectedDate"
                                        sharedPrefs.edit().remove(noteKey).apply()
                                    }
                                    notesTrigger++
                                    Toast.makeText(context, "Deleted note", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(24.dp)
                                    .testTag("note_delete_btn_${hSet.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Note",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String?, modifier: Modifier = Modifier) {
    val isComplete = HabitAnalyticsService.isCompletedStatus(status)
    val isPartial = HabitAnalyticsService.isPartialStatus(status)
    val isMissed = HabitAnalyticsService.isMissedStatus(status)

    val badgeIcon = when {
        isComplete -> Icons.Default.CheckCircle
        isPartial -> Icons.Default.Adjust
        isMissed -> Icons.Default.Cancel
        else -> Icons.Default.RadioButtonUnchecked
    }

    val badgeColor = when {
        isComplete -> Color(0xFF2E7D32) // Complete (Deep green)
        isPartial -> Color(0xFFE65100) // Partial (Deep orange/amber)
        isMissed -> Color(0xFFEF5350) // Missed (Red/coral)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    val badgeLabel = when {
        isComplete -> "Completed"
        isPartial -> "Partial"
        isMissed -> "Missed"
        else -> "Unmarked"
    }

    Row(
        modifier = modifier
            .background(badgeColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = badgeIcon,
            contentDescription = badgeLabel,
            tint = badgeColor,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = badgeLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = badgeColor
        )
    }
}

@Composable
fun LegendItem(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}
