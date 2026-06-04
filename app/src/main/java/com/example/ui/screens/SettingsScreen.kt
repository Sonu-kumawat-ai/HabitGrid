@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.HabitViewModel

@Composable
fun SettingsScreen(
    viewModel: HabitViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Collect settings flows
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val subAlpha = if (notificationsEnabled) 1f else 0.4f
    val subEnabled = notificationsEnabled
    val dailyRemindersEnabled by viewModel.dailyRemindersEnabled.collectAsState()
    val dailyRemindersTime by viewModel.dailyRemindersTime.collectAsState()
    val missedRemindersEnabled by viewModel.missedRemindersEnabled.collectAsState()
    val missedRemindersFrequency by viewModel.missedRemindersFrequency.collectAsState()
    val dailySummaryEnabled by viewModel.dailySummaryEnabled.collectAsState()
    val dailySummaryTime by viewModel.dailySummaryTime.collectAsState()

    val reminderFrequency by viewModel.reminderFrequency.collectAsState()
    val reminderStartTime by viewModel.reminderStartTime.collectAsState()
    val reminderEndTime by viewModel.reminderEndTime.collectAsState()

    val wallpaperActive by viewModel.wallpaperActive.collectAsState()
    val wallpaperShowCurrentStreak by viewModel.wallpaperShowCurrentStreak.collectAsState()
    val wallpaperShowBestStreak by viewModel.wallpaperShowBestStreak.collectAsState()
    val wallpaperShowProductivity by viewModel.wallpaperShowProductivity.collectAsState()
    val wallpaperShowProgressBar by viewModel.wallpaperShowProgressBar.collectAsState()
    val wallpaperShowTasks by viewModel.wallpaperShowTasks.collectAsState()
    val wallpaperShowTotalDaysLeft by viewModel.wallpaperShowTotalDaysLeft.collectAsState()
    val wallpaperShowDateLabels by viewModel.wallpaperShowDateLabels.collectAsState()
    
    val wallpaperDataSource by viewModel.wallpaperDataSource.collectAsState()
    val wallpaperSelectedHabitSet by viewModel.wallpaperSelectedHabitSet.collectAsState()
    val wallpaperTheme by viewModel.wallpaperTheme.collectAsState()
    val wallpaperStatsPosition by viewModel.wallpaperStatsPosition.collectAsState()

    val allHabitSets by viewModel.unfilteredHabitSets.collectAsState()
    var showWallpaperConfirmDialog by remember { mutableStateOf(false) }

    val themeMode by viewModel.themeMode.collectAsState()
    val autoMarkMissed by viewModel.autoMarkMissed.collectAsState()
    val deleteConfirmation by viewModel.deleteConfirmation.collectAsState()

    // Dialog trigger states
    var showResetConfirmation by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }

    // Custom notification templates
    val sharedPrefs = remember { context.getSharedPreferences("habitgrid_settings", android.content.Context.MODE_PRIVATE) }
    var customDailyTitle by remember { mutableStateOf(sharedPrefs.getString("settings_custom_daily_reminder_title", "Daily Reminder! ☀️") ?: "Daily Reminder! ☀️") }
    var customDailyBody by remember { mutableStateOf(sharedPrefs.getString("settings_custom_daily_reminder_body", "Don't forget today's habits.") ?: "Don't forget today's habits.") }

    var customMissedTitle by remember { mutableStateOf(sharedPrefs.getString("settings_custom_missed_reminder_title", "Remaining Tasks! ⚠️") ?: "Remaining Tasks! ⚠️") }
    var customMissedBody by remember { mutableStateOf(sharedPrefs.getString("settings_custom_missed_reminder_body", "You still have {remaining} tasks remaining today.") ?: "You still have {remaining} tasks remaining today.") }

    var customSummaryTitle by remember { mutableStateOf(sharedPrefs.getString("settings_custom_daily_summary_title", "Daily Summary! ✨") ?: "Daily Summary! ✨") }
    var customSummaryBody by remember { mutableStateOf(sharedPrefs.getString("settings_custom_daily_summary_body", "Today's productivity: {productivity}%. Current streak: {streak} days.") ?: "Today's productivity: {productivity}%. Current streak: {streak} days.") }

    // Custom missed reminders schedules / schedules config
    var customMissedInterval by remember { mutableStateOf(sharedPrefs.getString("settings_custom_missed_reminder_interval", "Every 2 hours") ?: "Every 2 hours") }
    var customMissedTimesStr by remember { mutableStateOf(sharedPrefs.getString("settings_custom_missed_reminder_times", "09:00 AM,01:00 PM,06:00 PM") ?: "09:00 AM,01:00 PM,06:00 PM") }

    // Custom generic reminders schedules / schedules config
    var customReminderInterval by remember { mutableStateOf(sharedPrefs.getString("settings_custom_reminder_interval", "Every 2 hours") ?: "Every 2 hours") }
    var customReminderTimesStr by remember { mutableStateOf(sharedPrefs.getString("settings_custom_reminder_times", "10:00 AM,02:00 PM,08:00 PM") ?: "10:00 AM,02:00 PM,08:00 PM") }

    var notifPreviewTab by remember { mutableStateOf("Daily Reminder") }
    var customMissedHoursGap by remember { mutableStateOf(sharedPrefs.getString("settings_custom_hourly_hours_gap", "3") ?: "3") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.saveSetting("settings_notifications_enabled", true)
            Toast.makeText(context, "Notification permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permission denied. Real-time notifications cannot be shown.", Toast.LENGTH_LONG).show()
            viewModel.saveSetting("settings_notifications_enabled", false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back to home"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // SECTION 1: NOTIFICATIONS
            SettingsSectionHeader(title = "Notifications", icon = Icons.Default.Notifications)
            var showHourlyFrequencyDialog by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Master Toggle
                    RowSettingSwitch(
                        title = "Enable Notifications",
                        subtitle = "Master control for all app notifications",
                        checked = notificationsEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val isGranted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (isGranted) {
                                        viewModel.saveSetting("settings_notifications_enabled", true)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                } else {
                                    viewModel.saveSetting("settings_notifications_enabled", true)
                                }
                            } else {
                                viewModel.saveSetting("settings_notifications_enabled", false)
                            }
                        },
                        testTag = "master_notifications_toggle"
                    )

                    if (notificationsEnabled) {
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    text = "Daily Reminder",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface 
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Time: " + dailyRemindersTime,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        lineHeight = 16.sp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Edit",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clickable { showNativeTimePicker(context, dailyRemindersTime) { viewModel.saveSetting("settings_daily_reminders_time", it) } }
                                            .padding(vertical = 4.dp)
                                    )
                                }
                            }
                            Switch(
                                checked = dailyRemindersEnabled,
                                onCheckedChange = { viewModel.saveSetting("settings_daily_reminders_enabled", it) }
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    text = "Hourly Reminder",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val freqText = if (missedRemindersFrequency == "Custom") {
                                        "Every " + customMissedHoursGap + " Hours"
                                    } else {
                                        missedRemindersFrequency
                                    }
                                    Text(
                                        text = "Frequency: " + freqText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        lineHeight = 16.sp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Edit",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clickable { showHourlyFrequencyDialog = true }
                                            .padding(vertical = 4.dp)
                                    )
                                }
                            }
                            Switch(
                                checked = missedRemindersEnabled,
                                onCheckedChange = { viewModel.saveSetting("settings_missed_reminders_enabled", it) }
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    text = "Daily Summary",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Time: 10:00 PM",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                            Switch(
                                checked = dailySummaryEnabled,
                                onCheckedChange = { viewModel.saveSetting("settings_daily_summary_enabled", it) }
                            )
                        }
                    }
                }
            }

            if (notificationsEnabled) {
                if (showHourlyFrequencyDialog) {
                var tempFreq by remember { mutableStateOf(missedRemindersFrequency) }
                var tempNum by remember { mutableStateOf(customMissedHoursGap) }
                var customDropdownExpanded by remember { mutableStateOf(false) }

                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showHourlyFrequencyDialog = false },
                    title = { Text("Hourly Reminder Frequency") },
                    text = {
                        Column {
                            val freqOptions = listOf("Once Every Hour", "Once Every 2 Hours", "Custom")
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                freqOptions.forEach { option ->
                                    val isSelected = tempFreq == option
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { tempFreq = option },
                                        label = { Text(option) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                }
                            }
                            if (tempFreq == "Custom") {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Frequency:")
                                    Box {
                                        androidx.compose.material3.OutlinedButton(
                                            onClick = { customDropdownExpanded = true }
                                        ) {
                                            Text(text = "$tempNum ▼")
                                        }
                                        DropdownMenu(
                                            expanded = customDropdownExpanded,
                                            onDismissRequest = { customDropdownExpanded = false }
                                        ) {
                                            for (i in 1..24) {
                                                DropdownMenuItem(
                                                    text = { Text(i.toString()) },
                                                    onClick = {
                                                        tempNum = i.toString()
                                                        customDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    Text("Hours")
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.saveSetting("settings_missed_reminders_frequency", tempFreq)
                                if (tempFreq == "Custom") {
                                    viewModel.saveSetting("settings_custom_hourly_hours_gap", tempNum)
                                    customMissedHoursGap = tempNum
                                }
                                showHourlyFrequencyDialog = false
                            }
                        ) {
                            Text("Confirm")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showHourlyFrequencyDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
            }

            // SECTION 3: LIVE WALLPAPER INTEGRATION (MOCK SYSTEM)
            SettingsSectionHeader(title = "Live Wallpaper Integration", icon = Icons.Default.Wallpaper)
            
            val noHabitSets = allHabitSets.isEmpty()
            
            if (showWallpaperConfirmDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showWallpaperConfirmDialog = false },
                    title = { Text("Set Live Wallpaper") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Wallpaper Mode: $wallpaperDataSource", fontWeight = FontWeight.Bold)
                            if (wallpaperDataSource == "Selected Habit Set" && wallpaperSelectedHabitSet.isNotBlank()) {
                                Text("Selected Habit Set: $wallpaperSelectedHabitSet")
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Enabled Content Elements:", fontWeight = FontWeight.Bold)
                            if (wallpaperShowCurrentStreak) Text("• Current Streak")
                            if (wallpaperShowBestStreak) Text("• Best Streak")
                            if (wallpaperShowProductivity) Text("• Today's Productivity Score")
                            if (wallpaperShowProgressBar) Text("• Today's Progress Bar")
                            if (wallpaperShowTasks) Text("• Today's Tasks Completed & Remaining")
                            if (wallpaperShowTotalDaysLeft) Text("• Total Days Left")
                            if (wallpaperShowDateLabels) Text("• Show Date Labels")
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            showWallpaperConfirmDialog = false
                            viewModel.saveSetting("settings_wallpaper_active", true)
                            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(context, "com.example.wallpaper.HabitWallpaperService"))
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Live wallpaper picker not supported on this device.", Toast.LENGTH_LONG).show()
                            }
                        }) {
                            Text("Continue")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showWallpaperConfirmDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (noHabitSets) {
                        Text(
                            text = "Create at least one Habit Set to use the Live Wallpaper feature.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }

                    // Master Toggle
                    RowSettingSwitch(
                        title = "Enable Live Wallpaper Settings",
                        subtitle = "Show controls for Live Wallpaper features",
                        checked = wallpaperActive,
                        onCheckedChange = { enabled ->
                            viewModel.saveSetting("settings_wallpaper_active", enabled)
                            if (!enabled) {
                                try {
                                    android.app.WallpaperManager.getInstance(context).clear()
                                    Toast.makeText(context, "Wallpaper Cleared", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not completely clear wallpaper.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        testTag = "master_wallpaper_toggle",
                        enabled = !noHabitSets
                    )

                    if (wallpaperActive && !noHabitSets) {
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                        
                        Button(
                            onClick = { showWallpaperConfirmDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Set Live Wallpaper", fontWeight = FontWeight.Bold)
                        }

                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                        // Wallpaper Content Controls
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Wallpaper Content Controls",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            RowSettingSwitch(
                                title = "Show Current Streak",
                                subtitle = "🔥 21 Days",
                                checked = wallpaperShowCurrentStreak,
                                onCheckedChange = { viewModel.saveSetting("settings_wallpaper_show_current_streak", it) },
                                testTag = "wallpaper_current_streak_toggle",
                                enabled = !noHabitSets
                            )
                            RowSettingSwitch(
                                title = "Show Best Streak",
                                subtitle = "🏆 45 Days",
                                checked = wallpaperShowBestStreak,
                                onCheckedChange = { viewModel.saveSetting("settings_wallpaper_show_best_streak", it) },
                                testTag = "wallpaper_best_streak_toggle",
                                enabled = !noHabitSets
                            )
                            RowSettingSwitch(
                                title = "Show Today's Productivity Score",
                                subtitle = "Productivity: 82%",
                                checked = wallpaperShowProductivity,
                                onCheckedChange = { viewModel.saveSetting("settings_wallpaper_show_productivity", it) },
                                testTag = "wallpaper_productivity_toggle",
                                enabled = !noHabitSets
                            )
                            RowSettingSwitch(
                                title = "Show Today's Progress Bar",
                                subtitle = "Visual progress bar",
                                checked = wallpaperShowProgressBar,
                                onCheckedChange = { viewModel.saveSetting("settings_wallpaper_show_progress_bar", it) },
                                testTag = "wallpaper_progress_bar_toggle",
                                enabled = !noHabitSets
                            )
                            RowSettingSwitch(
                                title = "Show Today's Tasks Completed",
                                subtitle = "Task: 3/8",
                                checked = wallpaperShowTasks,
                                onCheckedChange = { viewModel.saveSetting("settings_wallpaper_show_tasks", it) },
                                testTag = "wallpaper_tasks_toggle",
                                enabled = !noHabitSets
                            )
                            RowSettingSwitch(
                                title = "Show Total Days Left",
                                subtitle = "Days remaining in tracking period",
                                checked = wallpaperShowTotalDaysLeft,
                                onCheckedChange = { viewModel.saveSetting("settings_wallpaper_show_total_days_left", it) },
                                testTag = "wallpaper_total_days_left_toggle",
                                enabled = !noHabitSets
                            )
                            RowSettingSwitch(
                                title = "Show Date Labels",
                                subtitle = "Displays calendar dates on heatmap cells",
                                checked = wallpaperShowDateLabels,
                                onCheckedChange = { viewModel.saveSetting("settings_wallpaper_show_date_labels", it) },
                                testTag = "wallpaper_date_labels_toggle",
                                enabled = !noHabitSets
                            )
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                    // Wallpaper Data Source
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Wallpaper Data Source",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Overall Habits", "Selected Habit Set").forEach { src ->
                                FilterChip(
                                    selected = wallpaperDataSource == src,
                                    onClick = { viewModel.saveSetting("settings_wallpaper_data_source", src) },
                                    label = { Text(src) },
                                    enabled = !noHabitSets
                                )
                            }
                        }
                        
                        if (wallpaperDataSource == "Selected Habit Set") {
                            var expanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = if (wallpaperSelectedHabitSet.isBlank()) "Select Habit Set" else wallpaperSelectedHabitSet,
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = !noHabitSets,
                                    modifier = Modifier.fillMaxWidth().clickable(enabled = !noHabitSets) { expanded = true },
                                    trailingIcon = { 
                                        androidx.compose.material3.IconButton(onClick = { if (!noHabitSets) expanded = !expanded }) {
                                            androidx.compose.material3.Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        }
                                    },
                                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                androidx.compose.material3.DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    allHabitSets.forEach { set ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text(set.habitSet.name) },
                                            onClick = {
                                                viewModel.saveSetting("settings_wallpaper_selected_habit_set", set.habitSet.name)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                    // Wallpaper Appearance
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Wallpaper Appearance",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Text("Theme Selection", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Light", "Dark", "Warm HabitGrid").forEach { t ->
                                FilterChip(
                                    selected = wallpaperTheme == t,
                                    onClick = { viewModel.saveSetting("settings_wallpaper_theme", t) },
                                    label = { Text(t) },
                                    enabled = !noHabitSets
                                )
                            }
                        }
                        
                        Text("Statistics Position", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Above Grid", "Below Grid").forEach { pos ->
                                FilterChip(
                                    selected = wallpaperStatsPosition == pos,
                                    onClick = { viewModel.saveSetting("settings_wallpaper_stats_position", pos) },
                                    label = { Text(pos) },
                                    enabled = !noHabitSets
                                )
                            }
                        }
                    }
                    }
                }
            }

            // SECTION 4: APPEARANCE & THEME Switching
            SettingsSectionHeader(title = "Appearance & Theme", icon = Icons.Default.Palette)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Theme Mode",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val themes = listOf("Light Theme", "Dark Theme", "System Default")
                        themes.forEach { t ->
                            val isSelected = themeMode == t
                            Card(
                                onClick = {
                                    viewModel.saveSetting("settings_theme_mode", t)
                                    Toast.makeText(context, "$t applied instantly!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("theme_btn_${t.lowercase().replace(" ", "_")}"),
                                border = BorderStroke(
                                    1.5.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (t == "System Default") "System" else t.replace(" Theme", ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 5: BASIC SETTINGS (GENERAL)
            SettingsSectionHeader(title = "Basic Settings", icon = Icons.Default.Settings)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    RowSettingSwitch(
                        title = "Auto Mark Missed Tasks",
                        subtitle = "Unmarked tasks automatically become Missed at day end.",
                        checked = autoMarkMissed,
                        onCheckedChange = { viewModel.saveSetting("settings_auto_mark_missed", it) },
                        testTag = "auto_mark_missed_toggle"
                    )

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                    RowSettingSwitch(
                        title = "Confirmation Dialogs",
                        subtitle = "Enable delete confirmation prompt alert overlays",
                        checked = deleteConfirmation,
                        onCheckedChange = { viewModel.saveSetting("settings_delete_confirmation", it) },
                        testTag = "delete_configs_toggle"
                    )

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                    // Reset Preferences Button
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Reset Options",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(
                            onClick = { showResetConfirmation = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reset_preferences_btn")
                        ) {
                            Text(
                                "Reset App Setting Preferences",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // SECTION 6: ABOUT HABITGRID
            SettingsSectionHeader(title = "About HabitGrid", icon = Icons.Default.Info)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfoLine(label = "App Name", value = "HabitGrid")
                    InfoLine(label = "App Version", value = "1.0.0")
                    InfoLine(label = "Developer Information", value = "SONU KUMAWAT")

                    Divider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    // Privacy Policy
                    Text(
                        text = "Privacy Policy",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPrivacyDialog = true }
                            .padding(vertical = 6.dp)
                    )

                    // Terms & Conditions
                    Text(
                        text = "Terms & Conditions",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTermsDialog = true }
                            .padding(vertical = 6.dp)
                    )
                }
            }
        }
    }

    // Reset Confirmation Dialog
    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("Confirm Reset Settings", fontWeight = FontWeight.Black) },
            text = { Text("Are you sure you want to reset all app settings and preferences back to their defaults? Confirmed configurations apply instantly.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetSettings()
                        showResetConfirmation = false
                        Toast.makeText(context, "All app preferences reset to defaults!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_reset_settings_btn")
                ) {
                    Text("Reset Preferences", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Privacy Policy Dialog
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy Policy", fontWeight = FontWeight.Black) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = """
                            At HabitGrid, we highly value your privacy. This application functions entirely on your local device.
                            
                            1. Data Collection: HabitGrid does NOT collect, upload, transmit, share, or store any personal information, habit schedules, tasks, or metrics to online servers or external third-party tools.
                            
                            2. Storage: All inputs, daily notes, streaks, and logging are securely maintained inside your device's isolated SQLite database using local Room architecture.
                            
                            3. Permissions: The parameters requested are utilized solely to facilitate system reminder channels and wallpaper renders locally.
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Terms & Conditions Dialog
    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = { Text("Terms & Conditions", fontWeight = FontWeight.Black) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = """
                            By downloading, installing, and utilizing the HabitGrid application, you agree to these Terms:
                            
                            1. Device Dependencies: Storage security, tracking alerts, and wallpaper refresh parameters depend directly on the host operating system's constraints and resource availability.
                            
                            2. Usage at Will: The execution stats, streak logs, data resetting, and schedules are handled strictly on-device. The developers do not hold liability for local data loss.
                            
                            3. Modification: Features are delivered as open source. You are welcome to customize notification schedules to suit your flow.
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTermsDialog = false }) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }


}

@Composable
fun SettingsSectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun RowSettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String = ""
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                lineHeight = 16.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
            modifier = Modifier.testTag(testTag)
        )
    }
}

fun showNativeTimePicker(
    context: android.content.Context,
    currentTimeStr: String,
    onTimeSelected: (String) -> Unit
) {
    val regex = """(\d+):(\d+)\s*(AM|PM)""".toRegex(RegexOption.IGNORE_CASE)
    val matchResult = regex.find(currentTimeStr)
    var initialHour = 9
    var initialMinute = 0
    if (matchResult != null) {
        val (hStr, mStr, amPmStr) = matchResult.destructured
        var h = hStr.toIntOrNull() ?: 9
        val m = mStr.toIntOrNull() ?: 0
        if (amPmStr.lowercase() == "pm" && h < 12) h += 12
        if (amPmStr.lowercase() == "am" && h == 12) h = 0
        initialHour = h
        initialMinute = m
    }
    
    android.app.TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            val amPm = if (hourOfDay < 12) "AM" else "PM"
            val displayHour = when {
                hourOfDay == 0 -> 12
                hourOfDay > 12 -> hourOfDay - 12
                else -> hourOfDay
            }
            val formattedTime = String.format("%02d:%02d %s", displayHour, minute, amPm)
            onTimeSelected(formattedTime)
        },
        initialHour,
        initialMinute,
        false
    ).show()
}

@Composable
fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 200.dp)
        )
    }
}
